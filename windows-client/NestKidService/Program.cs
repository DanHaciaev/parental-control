using NestKidCommon;
using NestKidService;

IHost host = Host.CreateDefaultBuilder(args)
    .UseWindowsService(options => { options.ServiceName = "NestKidService"; })
    .ConfigureServices(services =>
    {
        services.AddSingleton<HttpClient>();
        services.AddSingleton(sp => new FirebaseAuthClient(sp.GetRequiredService<HttpClient>(), FirebaseConfig.ApiKey));
        services.AddSingleton(sp => new FirestoreRestClient(sp.GetRequiredService<HttpClient>()));
        services.AddSingleton<SessionLockWatcher>();
        services.AddHostedService<Worker>();
    })
    .Build();

host.Run();
