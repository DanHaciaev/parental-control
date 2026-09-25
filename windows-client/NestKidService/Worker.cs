using NestKidCommon;

namespace NestKidService;

/// <summary>
/// The enforcement loop. One base tick interval handles everything — date rollover, usage
/// accrual, and (when over budget) the re-lock loop — with a heavier Firestore sync only every
/// ~60s, rather than juggling several independent timers.
/// </summary>
public class Worker : BackgroundService
{
    private static readonly TimeSpan TickInterval = TimeSpan.FromSeconds(5);
    private static readonly TimeSpan OverBudgetTickInterval = TimeSpan.FromSeconds(3);
    private static readonly TimeSpan SyncInterval = TimeSpan.FromSeconds(60);
    private static readonly TimeSpan LockRetryInterval = TimeSpan.FromSeconds(3);

    private readonly ILogger<Worker> _logger;
    private readonly FirebaseAuthClient _authClient;
    private readonly FirestoreRestClient _firestore;
    private readonly SessionLockWatcher _sessionWatcher;

    private DeviceConfig? _config;
    private string _idToken = "";
    private DateTime _idTokenExpiresAt = DateTime.MinValue;

    private string _todayDateKey = "";
    private int _todayMinutesUsed;
    private double _secondsAccumulator;
    private int? _dailyLimitMinutes;

    private DateTime _lastSyncAt = DateTime.MinValue;
    private DateTime _lastLockAttemptAt = DateTime.MinValue;

    public Worker(
        ILogger<Worker> logger,
        FirebaseAuthClient authClient,
        FirestoreRestClient firestore,
        SessionLockWatcher sessionWatcher)
    {
        _logger = logger;
        _authClient = authClient;
        _firestore = firestore;
        _sessionWatcher = sessionWatcher;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _config = DeviceConfig.Load();
        if (_config == null)
        {
            _logger.LogError("No config found under {Dir} — run NestKidSetup first.", DeviceConfig.ConfigDir);
            return;
        }

        _sessionWatcher.Start();

        // Seed from the last cached state immediately, so enforcement (fail-closed on quota)
        // keeps working even before the first successful Firestore round-trip (e.g. no network
        // yet at boot) — see DeviceState's kdoc.
        var cached = DeviceState.Load();
        if (cached != null)
        {
            _todayDateKey = cached.TodayDateKey;
            _todayMinutesUsed = cached.TodayMinutesUsed;
            _dailyLimitMinutes = cached.DailyLimitMinutes;
        }
        RolloverIfNeeded();

        await RefreshAuthAsync();
        await SyncAsync();

        while (!stoppingToken.IsCancellationRequested)
        {
            var overBudget = false;
            try
            {
                RolloverIfNeeded();

                if (_sessionWatcher.IsUnlocked)
                {
                    _secondsAccumulator += TickInterval.TotalSeconds;
                    while (_secondsAccumulator >= 60)
                    {
                        _secondsAccumulator -= 60;
                        _todayMinutesUsed++;
                    }
                }

                overBudget = _dailyLimitMinutes is int limit && _todayMinutesUsed >= limit;
                if (overBudget && _sessionWatcher.IsUnlocked && DateTime.UtcNow - _lastLockAttemptAt > LockRetryInterval)
                {
                    _lastLockAttemptAt = DateTime.UtcNow;
                    // LockWorkStation only returns to the lock screen, it doesn't block re-login —
                    // this loop is what makes it stick, by re-locking every time the session comes
                    // back unlocked while still over budget (see WorkstationLocker's kdoc).
                    WorkstationLocker.TryLock();
                }

                if (DateTime.UtcNow - _lastSyncAt >= SyncInterval)
                {
                    await SyncAsync();
                }

                SaveStateCache();
            }
            catch (Exception e)
            {
                _logger.LogWarning(e, "Tick failed");
            }

            await Task.Delay(overBudget ? OverBudgetTickInterval : TickInterval, stoppingToken);
        }
    }

    private void RolloverIfNeeded()
    {
        var today = DateTime.Now.ToString("yyyy-MM-dd");
        if (_todayDateKey != today)
        {
            _todayDateKey = today;
            _todayMinutesUsed = 0;
            _secondsAccumulator = 0;
        }
    }

    private async Task RefreshAuthAsync()
    {
        if (_config == null) return;
        try
        {
            var result = await _authClient.RefreshTokenAsync(_config.RefreshToken);
            _idToken = result.IdToken;
            // Firebase ID tokens last 1h; refresh proactively well before that instead of waiting
            // for a 401 to discover it expired.
            _idTokenExpiresAt = DateTime.UtcNow.AddMinutes(50);
            if (result.RefreshToken != _config.RefreshToken)
            {
                _config.RefreshToken = result.RefreshToken;
                _config.Save();
            }
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Token refresh failed — will retry next sync");
        }
    }

    /// <summary>Always re-reads dailyLimitMinutes + pendingCommand before pushing usage, so a
    /// parent's live limit change or "Заблокировать сейчас" lands within about one sync cycle.</summary>
    private async Task SyncAsync()
    {
        if (_config == null) return;
        _lastSyncAt = DateTime.UtcNow;

        if (DateTime.UtcNow >= _idTokenExpiresAt) await RefreshAuthAsync();
        if (string.IsNullOrEmpty(_idToken)) return;

        var path = $"families/{_config.FamilyId}/devices/{_config.DeviceId}";
        try
        {
            var doc = await _firestore.GetDocumentAsync(_idToken, path);
            if (doc == null)
            {
                // The parent removed/unpaired this device — stop enforcing rather than locking
                // forever on a stale cached limit.
                _logger.LogWarning("Device doc not found (unpaired?) — pausing enforcement.");
                _dailyLimitMinutes = null;
                return;
            }

            _dailyLimitMinutes = doc.TryGetValue("dailyLimitMinutes", out var limitVal) && limitVal is long l ? (int)l : null;

            if (doc.TryGetValue("pendingCommand", out var cmdVal) && cmdVal is Dictionary<string, object?> cmd)
            {
                var type = cmd.TryGetValue("type", out var t) ? t as string : null;
                var status = cmd.TryGetValue("status", out var s) ? s as string : null;
                if (type == "LOCK_NOW" && status == "PENDING")
                {
                    WorkstationLocker.TryLock();
                    await _firestore.PatchDocumentAsync(_idToken, path, new Dictionary<string, object?>
                    {
                        ["pendingCommand"] = new Dictionary<string, object?> { ["type"] = "LOCK_NOW", ["status"] = "DONE" }
                    });
                }
            }

            await _firestore.PatchDocumentAsync(_idToken, path, new Dictionary<string, object?>
            {
                ["todayDateKey"] = _todayDateKey,
                ["todayMinutesUsed"] = _todayMinutesUsed,
                ["lastSeenAt"] = DateTime.UtcNow
            });
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Sync failed — continuing with cached limit until next sync");
        }
    }

    private void SaveStateCache()
    {
        new DeviceState
        {
            DailyLimitMinutes = _dailyLimitMinutes,
            TodayDateKey = _todayDateKey,
            TodayMinutesUsed = _todayMinutesUsed
        }.Save();
    }
}
