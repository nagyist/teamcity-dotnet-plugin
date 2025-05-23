

package jetbrains.buildServer.inspect

import jetbrains.buildServer.RequirementsProvider
import jetbrains.buildServer.inspect.DupFinderConstants.DEFAULT_DISCARD_COST
import jetbrains.buildServer.inspect.DupFinderConstants.DEFAULT_INCLUDE_FILES
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor

class DupFinderRunType(
    runTypeRegistry: RunTypeRegistry,
    private val _pluginDescriptor: PluginDescriptor,
    private val _requirementsProvider: RequirementsProvider,
    private val _propertiesProcessor: PropertiesProcessor)
    : RunType(), Deprecation {
    init {
        runTypeRegistry.registerRunType(this)
    }

    override fun getRunnerPropertiesProcessor() = _propertiesProcessor

    override fun getDescription() = DupFinderConstants.RUNNER_DESCRIPTION

    override fun getEditRunnerParamsJspFilePath() =
        _pluginDescriptor.getPluginResourcesPath("editDupFinderRunParams.jsp")

    override fun getViewRunnerParamsJspFilePath() =
        _pluginDescriptor.getPluginResourcesPath("viewDupFinderRunParams.jsp")

    override fun getDefaultRunnerProperties() = mapOf(
            DupFinderConstants.SETTINGS_INCLUDE_FILES to DEFAULT_INCLUDE_FILES,
            DupFinderConstants.SETTINGS_DISCARD_COST to DEFAULT_DISCARD_COST,
            DupFinderConstants.SETTINGS_DISCARD_LITERALS to true.toString(),
            CltConstants.RUNNER_SETTING_CLT_PLATFORM to InspectionToolPlatform.WindowsX64.id)

    override fun getType() = DupFinderConstants.RUNNER_TYPE

    override fun getDisplayName() = DupFinderConstants.RUNNER_DISPLAY_NAME

    override fun getTags(): MutableSet<String> {
        return mutableSetOf(".NET", "dotnet", "ReSharper", "code analysis")
    }

    override fun describeParameters(parameters: Map<String, String>): String {
        val includes = parameters[DupFinderConstants.SETTINGS_INCLUDE_FILES]
        val excludes = parameters[DupFinderConstants.SETTINGS_EXCLUDE_FILES]
        val sb = StringBuilder()
        if (!StringUtil.isEmptyOrSpaces(includes)) {
            sb.append("Include sources: ").append(includes).append("\n")
        }

        if (!StringUtil.isEmptyOrSpaces(excludes)) {
            sb.append("Exclude sources: : ").append(excludes).append("\n")
        }

        return sb.toString()
    }

    override fun getRunnerSpecificRequirements(runParameters: Map<String, String>) =
        _requirementsProvider.getRequirements(runParameters).toList()

    override fun getIconUrl(): String {
        return _pluginDescriptor.getPluginResourcesPath("resharper.svg")
    }

    override fun getDeprecationType(): DeprecationType = DeprecationType.DEPRECATED_ALLOWED

    override fun getDeprecationReason(): String =
        "The Duplicates finder (ReSharper) runner is now deprecated. To continue using it, install JetBrains ReSharper Command Line Tools version 2021.2.3 and select this version under advanced options."

    override fun getDeprecationReference(): String = "duplicates-finder-resharper"
}