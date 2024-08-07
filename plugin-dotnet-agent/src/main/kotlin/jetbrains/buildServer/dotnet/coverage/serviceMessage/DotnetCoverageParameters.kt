package jetbrains.buildServer.dotnet.coverage.serviceMessage

import jetbrains.buildServer.agent.BuildProgressLogger
import java.io.File

@Deprecated("Deprecated after task TW-85039. Needed for backward compatibility")
interface DotnetCoverageParameters {

    /**
     * @return selected coverage tool name
     */
    fun getCoverageToolName(): String?

    fun getCheckoutDirectory(): File

    fun getBuildName(): String

    fun getTempDirectory(): File

    fun resolvePath(path: String): File?

    fun resolvePathToTool(path: String, toolName: String): File?

    /**
     * Returns logger which can be used to log messages to the build log on server.
     *
     * @return logger for various build messages
     */
    fun getBuildLogger(): BuildProgressLogger

    fun getRunnerParameter(key: String): String?

    fun getConfigurationParameter(key: String): String?

    fun getConfigurationParameters(): Map<String, String>

    /**
     * @return environment variables of a build
     */
    fun getBuildEnvironmentVariables(): Map<String, String>

    /**
     * @return final copy of coverage parameters.
     */
    fun makeSnapshot(): DotnetCoverageParameters
}
