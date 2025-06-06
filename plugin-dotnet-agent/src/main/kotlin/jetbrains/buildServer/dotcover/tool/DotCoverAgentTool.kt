package jetbrains.buildServer.dotcover.tool

import jetbrains.buildServer.agent.FileSystemService
import jetbrains.buildServer.agent.Version
import jetbrains.buildServer.agent.runner.ParameterType
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.dotnet.CoverageConstants
import jetbrains.buildServer.dotnet.DotnetConstants
import jetbrains.buildServer.dotnet.coverage.serviceMessage.DotnetCoverageParametersHolder
import jetbrains.buildServer.util.OSType
import java.io.File

class DotCoverAgentTool(
    private val _parametersService: ParametersService,
    private val _fileSystemService: FileSystemService,
    private val _coverageParametersHolder: DotnetCoverageParametersHolder,
) {
    val dotCoverExeFile get() = EntryPointType.WindowsExecutable.getEntryPointFile(dotCoverHomePath)

    val dotCoverDllFile get() = EntryPointType.UsingAgentDotnetRuntime.getEntryPointFile(dotCoverHomePath)

    val dotCoverShFile get() = EntryPointType.UsingBundledDotnetRuntime.getEntryPointFile(dotCoverHomePath)

    val dotCoverHomePath
        get() = _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME)
            ?.takeUnless { it.isBlank() }
            ?: tryGetDotCoverHomePathFromParameterHolder()
            ?: ""

    val type get() = when {
        // cross-platform version using bundled runtime
        _fileSystemService.isExists(dotCoverShFile) ->
            DotCoverToolType.DeprecatedCrossPlatform

        // Windows-only version using agent requirements mechanism – no build-time requirements checking needed
        _fileSystemService.isExists(dotCoverExeFile) && !_fileSystemService.isExists(dotCoverDllFile) ->
            DotCoverToolType.WindowsOnly

        // cross-platform version using agent runtime
        _fileSystemService.isExists(dotCoverDllFile) && !_fileSystemService.isExists(dotCoverShFile) ->
            DotCoverToolType.CrossPlatform

        else -> DotCoverToolType.Unknown
    }

    val canUseDotNetRuntime get() =
        satisfiedRequirements.contains(MinRequirement.DotnetCore31) && _fileSystemService.isExists(dotCoverDllFile)

    val canUseDotNetFrameworkRuntime get() =
        satisfiedRequirements.contains(MinRequirement.DotnetFramework472) && _fileSystemService.isExists(dotCoverExeFile)

    fun getCrossPlatformVersionMinRequirement(os: OSType) = when (os) {
        OSType.WINDOWS -> sequenceOf(
            MinRequirement.DotnetCore31.requirementName,
            MinRequirement.DotnetFramework472.requirementName
        )

        else -> sequenceOf(MinRequirement.DotnetCore31.requirementName)
    }

    private val satisfiedRequirements : List<MinRequirement> get() {
        val buildParametersNames = _parametersService.getParameterNames(ParameterType.Configuration)
        return MinRequirement.values().filter { req -> buildParametersNames.any { req.isSatisfiedBy(it) } }
    }

    // When the dotCover path is set via the "##teamcity[dotNetCoverageDotnetRunner dotcover_home='/path/to/dotcover']"
    // from outside the runner, the path is saved as "dotNetCoverage.dotCover.home.path" in "DotnetCoverageParametersHolder",
    // so we check the holder here when the "dotNetCoverage.dotCover.home.path" is not available in runner parameters
    private fun tryGetDotCoverHomePathFromParameterHolder(): String? {
        if (!isParameterHolderFallbackEnabled) {
            return null
        }
        return runCatching {
            _coverageParametersHolder
                .getCoverageParameters()
                .getRunnerParameter(CoverageConstants.PARAM_DOTCOVER_HOME)
        }.getOrNull()
    }

    private val isParameterHolderFallbackEnabled: Boolean
        get() = _parametersService
            .tryGetParameter(
                ParameterType.Configuration,
                DotnetConstants.PARAM_DOTCOVER_PARAMETER_HOLDER_FALLBACK_ENABLED,
            )
            ?.trim()
            ?.equals("false", ignoreCase = true)
            ?.not()
            ?: true

    private enum class EntryPointType(private val entryPointFileName: String) {
        // dotCover.exe ... – simple run of Windows executable file
        WindowsExecutable("dotCover.exe"),

        // dotCover.sh ... – dotCover will select the proper bundled runtime in its own package
        UsingBundledDotnetRuntime("dotCover.sh"),

        // dotnet dotCover.dll ... – will use detected dotnet on agent
        UsingAgentDotnetRuntime("dotCover.dll");

        fun getEntryPointFile(basePath: String): File = File(basePath, this.entryPointFileName)
    }

    private enum class MinRequirement(
        prefix: String,
        private val minVersion: Version,
        suffix: String,
        val requirementName: String
    ) {
        DotnetFramework472(
            DotnetConstants.CONFIG_PREFIX_DOTNET_FRAMEWORK,
            Version.MinDotNetFrameworkVersionForDotCover,
            DotnetConstants.CONFIG_SUFFIX_PATH,
            ".NET Framework 4.7.2+"
        ),

        DotnetCore31(
            DotnetConstants.CONFIG_PREFIX_CORE_RUNTIME,
            Version.MinDotNetSdkVersionForDotCover,
            DotnetConstants.CONFIG_SUFFIX_PATH,
            ".NET Core 3.1+"
        );

        private val regexPattern = "^$prefix(.+)$suffix$".toRegex()

        fun isSatisfiedBy(parameter: String) =
            when (val extractedVersion = regexPattern.find(parameter)?.groupValues?.get(1)) {
                null -> false
                else -> when {
                    Version.isValid(extractedVersion) -> Version.parse(extractedVersion) >= minVersion
                    else -> false
                }
            }
    }
}

