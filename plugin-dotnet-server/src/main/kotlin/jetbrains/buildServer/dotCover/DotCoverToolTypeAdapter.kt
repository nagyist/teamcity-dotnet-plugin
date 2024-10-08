

package jetbrains.buildServer.dotCover

import jetbrains.buildServer.dotnet.CoverageConstants
import jetbrains.buildServer.tools.ToolTypeAdapter

class DotCoverToolTypeAdapter : ToolTypeAdapter() {

    override fun getType() = CoverageConstants.DOTCOVER_PACKAGE_ID

    override fun getDisplayName() = CoverageConstants.DOT_COVER_TOOL_TYPE_NAME

    override fun getDescription(): String = "Is used in JetBrains dotCover-specific build steps to get code coverage."

    override fun getShortDisplayName() = CoverageConstants.DOT_COVER_SHORT_TOOL_TYPE_NAME

    override fun getTargetFileDisplayName() = CoverageConstants.DOT_COVER_TARGET_FILE_DISPLAY_NAME

    override fun isSupportDownload() = true

    override fun getToolSiteUrl() = "https://www.jetbrains.com/dotcover/download/#section=commandline"

    override fun getToolLicenseUrl() = "https://www.jetbrains.com/legal/docs/dotcover/dotcover_clt_license"

    override fun getTeamCityHelpFile() = "JetBrains+dotCover"

    override fun getValidPackageDescription() = "Specify the path to a " + displayName +  " (.nupkg, .tar.gz, or .zip).\n" +
            "<br/><br/>Supported tools:" +
            "<br/><a href=\"https://www.jetbrains.com/dotcover/download/#section=commandline\" target=\"_blank\" rel=\"noreferrer\">JetBrains.dotCover.CommandLineTools.&lt;VERSION&gt;.tar.gz</a>" +
            "<br/><a href=\"https://www.nuget.org/packages/JetBrains.dotCover.CommandLineTools/\" target=\"_blank\" rel=\"noreferrer\">JetBrains.dotCover.CommandLineTools.&lt;VERSION&gt;.nupkg</a>"
}