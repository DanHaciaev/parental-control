namespace NestKidCommon;

/// <summary>
/// Same project/key already shipped inside both Android apps' google-services.json — a client
/// identifier, not a secret. Confirmed live (see plan doc) that this key has no Android-only
/// application restriction, so a plain HttpClient call works unmodified.
/// </summary>
public static class FirebaseConfig
{
    public const string ApiKey = "AIzaSyC5PwJm8mF0Fl9BSDX_M-__ZDF8PHDwMio";
    public const string ProjectId = "parent-control-ttt";
}
