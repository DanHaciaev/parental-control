using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json.Nodes;

namespace NestKidCommon;

/// <summary>
/// Plain REST against Firestore's public HTTP API, authenticated with a Firebase Auth ID token
/// (end-user identity, respects firestore.rules — not a service-account/Admin SDK client).
/// </summary>
public class FirestoreRestClient
{
    private static string BaseUrl =>
        $"https://firestore.googleapis.com/v1/projects/{FirebaseConfig.ProjectId}/databases/(default)/documents";

    private readonly HttpClient _http;

    public FirestoreRestClient(HttpClient http)
    {
        _http = http;
    }

    /// <summary>Returns null if the document doesn't exist (e.g. an already-used/expired pairing code lookup).</summary>
    public async Task<Dictionary<string, object?>?> GetDocumentAsync(string idToken, string path)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, $"{BaseUrl}/{path}");
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", idToken);
        using var resp = await _http.SendAsync(req);
        if (resp.StatusCode == HttpStatusCode.NotFound) return null;
        resp.EnsureSuccessStatusCode();
        var json = await resp.Content.ReadFromJsonAsync<JsonObject>();
        return FirestoreValue.FromFields(json?["fields"] as JsonObject);
    }

    public async Task PatchDocumentAsync(string idToken, string path, Dictionary<string, object?> fields)
    {
        var updateMask = string.Join("&", fields.Keys.Select(k => $"updateMask.fieldPaths={Uri.EscapeDataString(k)}"));
        var body = new JsonObject { ["fields"] = FirestoreValue.ToFields(fields) };
        using var req = new HttpRequestMessage(HttpMethod.Patch, $"{BaseUrl}/{path}?{updateMask}")
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json")
        };
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", idToken);
        using var resp = await _http.SendAsync(req);
        resp.EnsureSuccessStatusCode();
    }

    /// <summary>documentId query param makes this naturally fail if the doc already exists — no
    /// transaction needed for the device-claim flow (see PairingRepository.claimCodeForDevice's
    /// Kotlin sibling and the firestore.rules devices/{deviceId} create rule it mirrors).</summary>
    public async Task<bool> CreateDocumentAsync(string idToken, string collectionPath, string documentId, Dictionary<string, object?> fields)
    {
        var body = new JsonObject { ["fields"] = FirestoreValue.ToFields(fields) };
        using var req = new HttpRequestMessage(HttpMethod.Post, $"{BaseUrl}/{collectionPath}?documentId={Uri.EscapeDataString(documentId)}")
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json")
        };
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", idToken);
        using var resp = await _http.SendAsync(req);
        return resp.IsSuccessStatusCode;
    }
}
