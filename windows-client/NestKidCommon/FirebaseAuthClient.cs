using System.Net.Http.Json;
using System.Text;
using System.Text.Json.Nodes;

namespace NestKidCommon;

public record AuthResult(string IdToken, string RefreshToken, string LocalId);
public record RefreshResult(string IdToken, string RefreshToken);

/// <summary>
/// Talks to the public Firebase Auth REST API directly (no Admin SDK — this client authenticates
/// as an end-user/anonymous identity, the same one firestore.rules already checks, not a
/// service-account identity Admin SDK expects). See windows-client's plan doc for why.
/// </summary>
public class FirebaseAuthClient
{
    private readonly HttpClient _http;
    private readonly string _apiKey;

    public FirebaseAuthClient(HttpClient http, string apiKey)
    {
        _http = http;
        _apiKey = apiKey;
    }

    public async Task<AuthResult> SignUpAnonymouslyAsync()
    {
        var url = $"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={_apiKey}";
        using var content = new StringContent("{\"returnSecureToken\":true}", Encoding.UTF8, "application/json");
        using var resp = await _http.PostAsync(url, content);
        resp.EnsureSuccessStatusCode();
        var json = await resp.Content.ReadFromJsonAsync<JsonObject>()
            ?? throw new InvalidOperationException("Empty response from accounts:signUp");
        return new AuthResult(
            json["idToken"]!.GetValue<string>(),
            json["refreshToken"]!.GetValue<string>(),
            json["localId"]!.GetValue<string>());
    }

    public async Task<RefreshResult> RefreshTokenAsync(string refreshToken)
    {
        var url = $"https://securetoken.googleapis.com/v1/token?key={_apiKey}";
        var form = new Dictionary<string, string>
        {
            ["grant_type"] = "refresh_token",
            ["refresh_token"] = refreshToken
        };
        using var content = new FormUrlEncodedContent(form);
        using var resp = await _http.PostAsync(url, content);
        resp.EnsureSuccessStatusCode();
        var json = await resp.Content.ReadFromJsonAsync<JsonObject>()
            ?? throw new InvalidOperationException("Empty response from securetoken refresh");
        return new RefreshResult(
            json["access_token"]!.GetValue<string>(),
            json["refresh_token"]!.GetValue<string>());
    }
}
