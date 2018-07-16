package jetbrains.buildServer.dotnet

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.rx.Disposable
import jetbrains.buildServer.rx.subscribe

class BuildServerShutdownMonitor(
        agentLifeCycleEventSources: AgentLifeCycleEventSources,
        private val _commandLineExecutor: CommandLineExecutor,
        private val _dotnetToolResolver: DotnetToolResolver)
    : CommandRegistry {

    private var _subscriptionToken: Disposable
    private var _contexts = mutableListOf<DotnetBuildContext>()

    init {
        _subscriptionToken = agentLifeCycleEventSources.buildFinishedSource.subscribe {
            try {
                if (_contexts.size > 0) {
                    LOG.debug("Has a build command")
                    val sdks = _contexts
                            .flatMap { it.sdks }
                            .filter { it.version > Version.LastVersionWithoutSharedCompilation }
                            .distinctBy { it.path }

                    val executableFile = _dotnetToolResolver.executableFile
                    for ((path, version) in sdks) {
                        LOG.debug("$version is greater then ${Version.LastVersionWithoutSharedCompilation} in the \"$path\"")
                        _commandLineExecutor.tryExecute(
                                CommandLine(
                                        TargetType.Tool,
                                        executableFile,
                                        path,
                                        shutdownArgs,
                                        emptyList())
                        )
                    }
                }
            } finally {
                _contexts.clear()
            }
        }
    }

    override fun register(context: DotnetBuildContext) {
        if (buildCommands.contains(context.command.commandType)) {
            _contexts.add(context)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(BuildServerShutdownMonitor::class.java.name)
        internal val shutdownArgs = listOf(CommandLineArgument("build-server"), CommandLineArgument("shutdown"))
        private val buildCommands = setOf(
                DotnetCommandType.Build,
                DotnetCommandType.Pack,
                DotnetCommandType.Publish,
                DotnetCommandType.Test,
                DotnetCommandType.Run,
                DotnetCommandType.MSBuild)
    }
}