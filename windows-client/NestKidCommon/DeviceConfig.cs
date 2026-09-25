using System.Text.Json;

namespace NestKidCommon;

/// <summary>
/// Written once by NestKidSetup after a successful pairing, read every time NestKidService
/// starts. Deliberately flat/plain JSON (no encryption) — the refresh token only grants access
/// to this one family's `devices/{deviceId}` doc per firestore.rules, and the folder itself is
/// ACL-restricted to Administrators+SYSTEM by setup (best-effort; does nothing if the child
/// already has admin rights on the machine).
/// </summary>
public class DeviceConfig
{
    public string FamilyId { get; set; } = "";
    public string DeviceId { get; set; } = "";
    public string RefreshToken { get; set; } = "";

    public static readonly string ConfigDir = @"C:\ProgramData\NestKid";
    private static readonly string ConfigPath = Path.Combine(ConfigDir, "config.json");

    public static DeviceConfig? Load()
    {
        if (!File.Exists(ConfigPath)) return null;
        var json = File.ReadAllText(ConfigPath);
        return JsonSerializer.Deserialize<DeviceConfig>(json);
    }

    public void Save()
    {
        Directory.CreateDirectory(ConfigDir);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(this));
    }
}

/// <summary>
/// Cached last-known state so enforcement keeps working (fail-closed on quota) through a brief
/// Firestore outage — disabling Wi-Fi shouldn't be a bypass. Not a source of truth: overwritten
/// from the server on every successful sync.
/// </summary>
public class DeviceState
{
    public int? DailyLimitMinutes { get; set; }
    public string TodayDateKey { get; set; } = "";
    public int TodayMinutesUsed { get; set; }

    private static readonly string StatePath = Path.Combine(DeviceConfig.ConfigDir, "state.json");

    public static DeviceState? Load()
    {
        if (!File.Exists(StatePath)) return null;
        var json = File.ReadAllText(StatePath);
        return JsonSerializer.Deserialize<DeviceState>(json);
    }

    public void Save()
    {
        Directory.CreateDirectory(DeviceConfig.ConfigDir);
        File.WriteAllText(StatePath, JsonSerializer.Serialize(this));
    }
}
