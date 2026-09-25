using System.Runtime.InteropServices;

namespace NestKidCommon;

/// <summary>
/// Tracks whether the interactive console session is actually unlocked — not just "connected"
/// (WTSConnectState alone reports WTSActive even while locked, which would wrongly count
/// locked-but-logged-in idle time as usage). Needs a real Win32 message pump, which a Windows
/// Service doesn't have by default, so this spins up a dedicated thread with a hidden
/// message-only-style window purely to receive WM_WTSSESSION_CHANGE.
/// </summary>
public sealed class SessionLockWatcher : IDisposable
{
    private const uint WM_WTSSESSION_CHANGE = 0x02B1;
    private const int WTS_SESSION_LOCK = 0x7;
    private const int WTS_SESSION_UNLOCK = 0x8;
    private const int NOTIFY_FOR_ALL_SESSIONS = 1;

    [DllImport("wtsapi32.dll", SetLastError = true)]
    private static extern bool WTSRegisterSessionNotification(IntPtr hWnd, int dwFlags);

    [DllImport("wtsapi32.dll", SetLastError = true)]
    private static extern bool WTSUnRegisterSessionNotification(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr CreateWindowEx(int exStyle, string className, string windowName, int style,
        int x, int y, int width, int height, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr param);

    [DllImport("user32.dll")]
    private static extern IntPtr DefWindowProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern ushort RegisterClassEx(ref WNDCLASSEX lpwcx);

    [DllImport("user32.dll")]
    private static extern bool GetMessage(out MSG lpMsg, IntPtr hWnd, uint wMsgFilterMin, uint wMsgFilterMax);

    [DllImport("user32.dll")]
    private static extern bool TranslateMessage(ref MSG lpMsg);

    [DllImport("user32.dll")]
    private static extern IntPtr DispatchMessage(ref MSG lpMsg);

    [DllImport("user32.dll")]
    private static extern bool DestroyWindow(IntPtr hWnd);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string? lpModuleName);

    private delegate IntPtr WndProcDelegate(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct WNDCLASSEX
    {
        public uint cbSize;
        public uint style;
        public WndProcDelegate lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public IntPtr hInstance;
        public IntPtr hIcon;
        public IntPtr hCursor;
        public IntPtr hbrBackground;
        public string? lpszMenuName;
        public string lpszClassName;
        public IntPtr hIconSm;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MSG
    {
        public IntPtr hwnd;
        public uint message;
        public IntPtr wParam;
        public IntPtr lParam;
        public uint time;
        public POINT pt;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT { public int x; public int y; }

    private readonly WndProcDelegate _wndProcDelegate;
    private IntPtr _hwnd;

    // Assumes unlocked at startup — there's no cheap, reliable "is it currently locked" query to
    // seed this from, and a laptop is far more likely to be freshly booted/logged-in (unlocked)
    // than sitting at a lock screen when the service starts. Self-corrects on the next real
    // lock/unlock transition either way.
    private volatile bool _isUnlocked = true;

    public bool IsUnlocked => _isUnlocked;

    public SessionLockWatcher()
    {
        _wndProcDelegate = WndProc;
    }

    public void Start()
    {
        var thread = new Thread(RunMessageLoop) { IsBackground = true, Name = "NestKidSessionWatcher" };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
    }

    private void RunMessageLoop()
    {
        const string className = "NestKidSessionWatcherWnd";
        // GetModuleHandle(null), not Marshal.GetHINSTANCE(Module) — the latter returns -1 for a
        // module with no separate file on disk, which is exactly what a single-file-published
        // exe's main module is (confirmed via a build warning when actually publishing this way).
        var wc = new WNDCLASSEX
        {
            cbSize = (uint)Marshal.SizeOf<WNDCLASSEX>(),
            lpfnWndProc = _wndProcDelegate,
            hInstance = GetModuleHandle(null),
            lpszClassName = className
        };
        RegisterClassEx(ref wc);

        _hwnd = CreateWindowEx(0, className, "NestKidSessionWatcher", 0, 0, 0, 0, 0,
            IntPtr.Zero, IntPtr.Zero, wc.hInstance, IntPtr.Zero);

        if (_hwnd != IntPtr.Zero)
        {
            WTSRegisterSessionNotification(_hwnd, NOTIFY_FOR_ALL_SESSIONS);
        }

        while (GetMessage(out var msg, IntPtr.Zero, 0, 0))
        {
            TranslateMessage(ref msg);
            DispatchMessage(ref msg);
        }
    }

    private IntPtr WndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
    {
        if (msg == WM_WTSSESSION_CHANGE)
        {
            var reason = wParam.ToInt32();
            if (reason == WTS_SESSION_LOCK) _isUnlocked = false;
            else if (reason == WTS_SESSION_UNLOCK) _isUnlocked = true;
        }
        return DefWindowProc(hWnd, msg, wParam, lParam);
    }

    public void Dispose()
    {
        if (_hwnd != IntPtr.Zero)
        {
            WTSUnRegisterSessionNotification(_hwnd);
            DestroyWindow(_hwnd);
            _hwnd = IntPtr.Zero;
        }
    }
}
