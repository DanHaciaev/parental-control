using System.Diagnostics;
using NestKidCommon;

Console.OutputEncoding = System.Text.Encoding.UTF8;

Console.WriteLine("=== Nest Kid — настройка ноутбука ===");
Console.WriteLine();

if (DeviceConfig.Load() != null)
{
    Console.WriteLine("Этот ноутбук уже привязан. Чтобы привязать заново, сначала удалите");
    Console.WriteLine($"папку {DeviceConfig.ConfigDir} и остановите/удалите службу NestKidService.");
    Console.WriteLine();
    Console.WriteLine("Нажмите Enter для выхода...");
    Console.ReadLine();
    return;
}

Console.Write("Введите код с телефона мамы (6 цифр): ");
var rawCode = Console.ReadLine() ?? "";
var code = new string(rawCode.Where(char.IsDigit).ToArray());
if (code.Length != 6)
{
    Console.WriteLine("Код должен состоять из 6 цифр.");
    return;
}

using var http = new HttpClient();
var authClient = new FirebaseAuthClient(http, FirebaseConfig.ApiKey);
var firestore = new FirestoreRestClient(http);

Console.WriteLine();
Console.WriteLine("Подключаемся...");

AuthResult auth;
try
{
    auth = await authClient.SignUpAnonymouslyAsync();
}
catch (Exception e)
{
    Console.WriteLine($"Не удалось подключиться: {e.Message}");
    return;
}

var deviceUid = auth.LocalId;

var codeDoc = await firestore.GetDocumentAsync(auth.IdToken, $"pairingCodes/{code}");
if (codeDoc == null)
{
    Console.WriteLine("Код не найден. Проверьте, что ввели его правильно.");
    return;
}
if (codeDoc.TryGetValue("used", out var usedVal) && usedVal is true)
{
    Console.WriteLine("Этот код уже использован. Сгенерируйте новый код на телефоне мамы.");
    return;
}
if (codeDoc.TryGetValue("expiresAt", out var expiresVal) && expiresVal is DateTime expiresAt && expiresAt < DateTime.UtcNow)
{
    Console.WriteLine("Код истёк. Сгенерируйте новый код на телефоне мамы.");
    return;
}
if (!codeDoc.TryGetValue("familyId", out var familyIdVal) || familyIdVal is not string familyId || familyId.Length == 0)
{
    Console.WriteLine("Некорректный код.");
    return;
}

Console.WriteLine("Код принят, привязываем устройство...");

var created = await firestore.CreateDocumentAsync(auth.IdToken, $"families/{familyId}/devices", deviceUid, new Dictionary<string, object?>
{
    ["type"] = "windows",
    ["label"] = "Ноутбук",
    ["createdAt"] = DateTime.UtcNow
});
if (!created)
{
    Console.WriteLine("Не удалось привязать устройство. Возможно, оно уже привязано, или");
    Console.WriteLine("правила Firestore ещё не опубликованы — обратитесь к тому, кто настраивал приложение.");
    return;
}

try
{
    await firestore.PatchDocumentAsync(auth.IdToken, $"pairingCodes/{code}", new Dictionary<string, object?> { ["used"] = true });
}
catch
{
    // Best-effort — the device is already paired at this point either way (see claimCodeForDevice's
    // Kotlin sibling for the same tradeoff: the code just lingers reusable until its 15-min TTL).
}

var config = new DeviceConfig { FamilyId = familyId, DeviceId = deviceUid, RefreshToken = auth.RefreshToken };
config.Save();

Console.WriteLine("Устройство привязано. Настраиваем защиту от случайного отключения...");

try
{
    RunProcess("icacls", $"\"{DeviceConfig.ConfigDir}\" /inheritance:r /grant:r \"SYSTEM:(OI)(CI)F\" \"BUILTIN\\Administrators:(OI)(CI)F\"");
}
catch (Exception e)
{
    Console.WriteLine($"Не удалось ограничить доступ к папке настроек: {e.Message}");
}

var serviceExePath = Path.Combine(AppContext.BaseDirectory, "..", "NestKidService", "NestKidService.exe");
serviceExePath = Path.GetFullPath(serviceExePath);
if (!File.Exists(serviceExePath))
{
    // Fall back to assuming NestKidService.exe sits next to this exe — the expected layout once
    // both are published side-by-side into one install folder.
    serviceExePath = Path.Combine(AppContext.BaseDirectory, "NestKidService.exe");
}

try
{
    RunProcess("sc.exe", "stop NestKidService", ignoreFailure: true);
    RunProcess("sc.exe", "delete NestKidService", ignoreFailure: true);
    RunProcess("sc.exe", $"create NestKidService binPath= \"{serviceExePath}\" start= delayed-auto obj= LocalSystem DisplayName= \"Nest Kid\"");
    RunProcess("sc.exe", "failure NestKidService reset= 86400 actions= restart/5000/restart/5000/restart/5000");
    RunProcess("sc.exe", "start NestKidService");
}
catch (Exception e)
{
    Console.WriteLine($"Не удалось установить службу: {e.Message}");
    Console.WriteLine($"Путь к NestKidService.exe: {serviceExePath}");
    Console.WriteLine("Нажмите Enter для выхода...");
    Console.ReadLine();
    return;
}

Console.WriteLine();
Console.WriteLine("Готово! Лимит времени теперь можно ставить с телефона мамы.");
Console.WriteLine();
Console.WriteLine("ВАЖНО: чтобы это действительно работало, учётная запись ребёнка на этом");
Console.WriteLine("ноутбуке должна быть ОБЫЧНОЙ (не администратором). Если ребёнок — администратор,");
Console.WriteLine("он сможет отключить эту защиту через Службы Windows. Пароль от учётной записи");
Console.WriteLine("администратора должен знать только родитель.");
Console.WriteLine();
Console.WriteLine("Нажмите Enter для выхода...");
Console.ReadLine();

static void RunProcess(string fileName, string arguments, bool ignoreFailure = false)
{
    var psi = new ProcessStartInfo(fileName, arguments)
    {
        UseShellExecute = false,
        RedirectStandardOutput = true,
        RedirectStandardError = true,
        CreateNoWindow = true
    };
    using var process = Process.Start(psi) ?? throw new InvalidOperationException($"Failed to start {fileName}");
    process.WaitForExit();
    if (process.ExitCode != 0 && !ignoreFailure)
    {
        var stderr = process.StandardError.ReadToEnd();
        throw new InvalidOperationException($"{fileName} {arguments} exited {process.ExitCode}: {stderr}");
    }
}
