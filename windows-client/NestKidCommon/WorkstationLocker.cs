using System.Runtime.InteropServices;

namespace NestKidCommon;

/// <summary>
/// Locks the real interactive console session from a Session-0 LocalSystem service. Needs
/// SeTcbPrivilege (LocalSystem has it by default, but this enables it explicitly at each call as
/// a defensive step) to query the logged-on user's token and launch a process in their session.
///
/// Important limitation, not a bug: LockWorkStation only returns the OS to the lock screen, it
/// does NOT prevent the user from logging back in with their password. Callers that need
/// enforcement to actually stick must call TryLock() repeatedly whenever the session comes back
/// unlocked (see NestKidService's Worker) — there's no persistent "stay locked" state on Windows
/// short of a much larger undertaking (custom Credential Provider / kiosk shell), which is out of
/// scope here.
/// </summary>
public static class WorkstationLocker
{
    public static bool TryLock()
    {
        try
        {
            TryEnableSeTcbPrivilege();

            var sessionId = WTSGetActiveConsoleSessionId();
            if (sessionId == 0xFFFFFFFF) return false; // nobody logged in at the console right now

            if (!WTSQueryUserToken(sessionId, out var userToken)) return false;
            try
            {
                if (!DuplicateTokenEx(userToken, 0x02000000 /* MAXIMUM_ALLOWED */, IntPtr.Zero,
                        SECURITY_IMPERSONATION_LEVEL.SecurityIdentification, TOKEN_TYPE.TokenPrimary, out var primaryToken))
                {
                    return false;
                }
                try
                {
                    var envCreated = CreateEnvironmentBlock(out var envBlock, primaryToken, false);
                    try
                    {
                        var startupInfo = new STARTUPINFO
                        {
                            cb = Marshal.SizeOf<STARTUPINFO>(),
                            lpDesktop = "winsta0\\default"
                        };
                        const int CREATE_UNICODE_ENVIRONMENT = 0x00000400;
                        const int CREATE_NEW_CONSOLE = 0x00000010;
                        var commandLine = Environment.ExpandEnvironmentVariables(
                            @"%SystemRoot%\System32\rundll32.exe user32.dll,LockWorkStation");

                        var success = CreateProcessAsUser(
                            primaryToken, null, commandLine, IntPtr.Zero, IntPtr.Zero, false,
                            CREATE_UNICODE_ENVIRONMENT | CREATE_NEW_CONSOLE,
                            envCreated ? envBlock : IntPtr.Zero, null, ref startupInfo, out var processInfo);

                        if (success)
                        {
                            CloseHandle(processInfo.hProcess);
                            CloseHandle(processInfo.hThread);
                        }
                        return success;
                    }
                    finally
                    {
                        if (envCreated) DestroyEnvironmentBlock(envBlock);
                    }
                }
                finally
                {
                    CloseHandle(primaryToken);
                }
            }
            finally
            {
                CloseHandle(userToken);
            }
        }
        catch
        {
            return false;
        }
    }

    private static void TryEnableSeTcbPrivilege()
    {
        try
        {
            if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, out var hToken)) return;
            try
            {
                if (!LookupPrivilegeValue(null, "SeTcbPrivilege", out var luid)) return;
                var tp = new TOKEN_PRIVILEGES
                {
                    PrivilegeCount = 1,
                    Luid = luid,
                    Attributes = SE_PRIVILEGE_ENABLED
                };
                AdjustTokenPrivileges(hToken, false, ref tp, 0, IntPtr.Zero, IntPtr.Zero);
            }
            finally
            {
                CloseHandle(hToken);
            }
        }
        catch
        {
            // Best-effort defensive step — LocalSystem already has this privilege by default.
        }
    }

    private enum SECURITY_IMPERSONATION_LEVEL { SecurityAnonymous, SecurityIdentification, SecurityImpersonation, SecurityDelegation }
    private enum TOKEN_TYPE { TokenPrimary = 1, TokenImpersonation }

    private const uint TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private const uint TOKEN_QUERY = 0x0008;
    private const uint SE_PRIVILEGE_ENABLED = 0x00000002;

    [StructLayout(LayoutKind.Sequential)]
    private struct LUID { public uint LowPart; public int HighPart; }

    [StructLayout(LayoutKind.Sequential)]
    private struct TOKEN_PRIVILEGES { public uint PrivilegeCount; public LUID Luid; public uint Attributes; }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public int cb;
        public string? lpReserved;
        public string? lpDesktop;
        public string? lpTitle;
        public int dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
        public short wShowWindow, cbReserved2;
        public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION { public IntPtr hProcess, hThread; public int dwProcessId, dwThreadId; }

    [DllImport("kernel32.dll")]
    private static extern uint WTSGetActiveConsoleSessionId();

    [DllImport("wtsapi32.dll", SetLastError = true)]
    private static extern bool WTSQueryUserToken(uint sessionId, out IntPtr token);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool DuplicateTokenEx(IntPtr hExistingToken, uint dwDesiredAccess, IntPtr lpTokenAttributes,
        SECURITY_IMPERSONATION_LEVEL impersonationLevel, TOKEN_TYPE tokenType, out IntPtr phNewToken);

    [DllImport("userenv.dll", SetLastError = true)]
    private static extern bool CreateEnvironmentBlock(out IntPtr lpEnvironment, IntPtr hToken, bool bInherit);

    [DllImport("userenv.dll", SetLastError = true)]
    private static extern bool DestroyEnvironmentBlock(IntPtr lpEnvironment);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool CreateProcessAsUser(IntPtr hToken, string? lpApplicationName, string lpCommandLine,
        IntPtr lpProcessAttributes, IntPtr lpThreadAttributes, bool bInheritHandles, int dwCreationFlags,
        IntPtr lpEnvironment, string? lpCurrentDirectory, ref STARTUPINFO lpStartupInfo, out PROCESS_INFORMATION lpProcessInformation);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool OpenProcessToken(IntPtr processHandle, uint desiredAccess, out IntPtr tokenHandle);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool LookupPrivilegeValue(string? lpSystemName, string lpName, out LUID lpLuid);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool AdjustTokenPrivileges(IntPtr tokenHandle, bool disableAllPrivileges,
        ref TOKEN_PRIVILEGES newState, int bufferLength, IntPtr previousState, IntPtr returnLength);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetCurrentProcess();

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);
}
