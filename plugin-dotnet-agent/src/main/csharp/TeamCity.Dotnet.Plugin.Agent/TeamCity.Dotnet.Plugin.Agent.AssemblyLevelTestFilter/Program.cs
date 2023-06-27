﻿using System.IO.Abstractions;
using System.Reflection;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.App;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Backup;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Patching;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Suppression;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Targeting;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.TestEngines;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.TestSelectors;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.CommandLine;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.CommandLine.Help;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.CommandLine.Validation;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.Configuration;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.DependencyInjection;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.DotnetAssembly;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.FileSystemExtensions;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.Logging;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.MsBuild;


namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter;

internal static class Program
{
    public static async Task Main(string[] args)
    {
        Console.WriteLine("TeamCity.Dotnet.Plugin.Agent – .NET Assembly Level Test Filter");
        Console.WriteLine($"Version: {Version}");
        Console.WriteLine($"Author: {Company}");
        Console.WriteLine();
        

        var configurationSource = new CommandLineConfigurationSource<MainCommand>(args);

        var host = await Host
            .CreateDefaultBuilder(args)
            .ConfigureAppConfiguration((_, config) => config.Add(configurationSource))
            .ConfigureServices((hostContext, services) =>
            {
                services.Configure<MainCommand>(hostContext.Configuration.GetSection(nameof(MainCommand)));

                // regular services
                services
                    .AddSingleton(configurationSource.ConfigurationParsingResult)
                    .AddSingleton<IFileSystem, FileSystem>()
                    .AddSingletonByInterface<IFileReader>()
                    .AddSingletonByInterface<IFileCopier>()
                    .AddSingletonByInterface<IMsBuildLocator>()
                    .AddSingletonByInterface<IDotnetAssemblyLoader>()
                    .AddSingletonByInterface<ITestSelectorParser>()
                    .AddSingletonByInterface<ITestEngine>()
                    .AddSingletonByImplementationType<ITestEngine>()
                    .AddSingletonByInterface<ITestEngineRecognizer>()
                    .AddSingletonByInterface<ITestClassDetector>()
                    .AddSingletonByInterface<ITestSuppressionDecider>()
                    .AddSingletonByInterface<ITestSuppressingStrategy>()
                    .AddSingletonByInterface<IAssemblyMutator>()
                    .AddSingletonByInterface<ITestsSuppressor>()
                    .AddSingletonByInterface<ITargetResolvingStrategy>()
                    .AddSingletonByInterface<ITargetResolver>()
                    .AddSingletonByInterface<IAssemblyPatcher>()
                    .AddSingletonByInterface<ITestSelectorsLoader>()
                    .AddSingletonByInterface<IBackupMetadataSaver>()
                    .AddSingletonByInterface<IBackupRestore>()
                    .AddSingletonByInterface<IHelpPrinter>()
                    .AddSingletonByInterface<ICommandHandler>()
                    .AddSingletonByInterface<ILoggerConfigurator>()
                    .AddSingletonByInterface<ICommandValidator>()
                    .AddSingleton<CommandRouter<MainCommand>>();
            })
            .ConfigureLogging((_, loggingBuilder) =>
            {
                loggingBuilder
                    .ClearProviders()
                    .AddFilter("Microsoft", LogLevel.Trace)
                    .SetMinimumLevel(LogLevel.Trace)
                    .Services.AddSingleton<ILoggerProvider, CustomLoggerProvider<MainCommand>>();
            })
            .UseConsoleLifetime()
            .StartAsync();

        var commandRouter = host.Services.GetRequiredService<CommandRouter<MainCommand>>();
        
        // entry point
        await commandRouter.Route();
    }
    
    private static string Version =>
        Assembly.GetExecutingAssembly().GetName().Version!.ToString();

    private static string Company =>
        GetAssemblyCustomAttribute<AssemblyCompanyAttribute>().Company;
    
    private static T GetAssemblyCustomAttribute<T>() where T : Attribute =>
        (T) Attribute.GetCustomAttribute(Assembly.GetExecutingAssembly(), typeof(T))!;
}