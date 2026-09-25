using System.Text.Json.Nodes;

namespace NestKidCommon;

/// <summary>
/// Firestore's REST API wraps every field as a typed-value envelope
/// ({"stringValue"|"integerValue"|"booleanValue"|"timestampValue"|"mapValue"|"nullValue": ...})
/// instead of plain JSON. One shared (de)serialization helper here instead of hand-writing this
/// shape at every call site.
/// </summary>
public static class FirestoreValue
{
    public static JsonObject ToValue(object? value) => value switch
    {
        null => new JsonObject { ["nullValue"] = null },
        string s => new JsonObject { ["stringValue"] = s },
        int i => new JsonObject { ["integerValue"] = i.ToString() },
        long l => new JsonObject { ["integerValue"] = l.ToString() },
        bool b => new JsonObject { ["booleanValue"] = b },
        DateTime dt => new JsonObject { ["timestampValue"] = dt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ") },
        Dictionary<string, object?> map => new JsonObject { ["mapValue"] = new JsonObject { ["fields"] = ToFields(map) } },
        _ => throw new NotSupportedException($"Unsupported Firestore value type: {value.GetType()}")
    };

    public static JsonObject ToFields(Dictionary<string, object?> map)
    {
        var obj = new JsonObject();
        foreach (var (key, val) in map) obj[key] = ToValue(val);
        return obj;
    }

    public static object? FromValue(JsonNode? node)
    {
        if (node is not JsonObject obj) return null;
        if (obj.ContainsKey("stringValue")) return obj["stringValue"]!.GetValue<string>();
        if (obj.ContainsKey("integerValue")) return long.Parse(obj["integerValue"]!.GetValue<string>());
        if (obj.ContainsKey("booleanValue")) return obj["booleanValue"]!.GetValue<bool>();
        if (obj.ContainsKey("timestampValue")) return DateTime.Parse(obj["timestampValue"]!.GetValue<string>()).ToUniversalTime();
        if (obj.ContainsKey("mapValue")) return FromFields(obj["mapValue"]?["fields"] as JsonObject);
        return null; // nullValue, or an unrecognized/absent shape
    }

    public static Dictionary<string, object?> FromFields(JsonObject? fields)
    {
        var result = new Dictionary<string, object?>();
        if (fields == null) return result;
        foreach (var (key, val) in fields) result[key] = FromValue(val);
        return result;
    }
}
