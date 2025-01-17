package jetbrains.buildServer.dotnet.test.dotcover

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.*
import jetbrains.buildServer.agent.runner.serviceMessages.ImportDataServiceMessage
import jetbrains.buildServer.dotcover.*
import jetbrains.buildServer.dotcover.DotCoverProject.CoverCommandData
import jetbrains.buildServer.dotcover.command.DotCoverCommandType
import jetbrains.buildServer.dotcover.command.DotCoverCoverCommandLineBuilder
import jetbrains.buildServer.dotcover.command.DotCoverMergeCommandLineBuilder
import jetbrains.buildServer.dotcover.command.DotCoverReportCommandLineBuilder
import jetbrains.buildServer.dotnet.CoverageConstants
import jetbrains.buildServer.dotnet.Verbosity
import jetbrains.buildServer.dotnet.test.agent.VirtualFileSystemService
import jetbrains.buildServer.dotnet.test.agent.runner.WorkflowContextStub
import jetbrains.buildServer.mono.MonoToolProvider
import jetbrains.buildServer.rx.Disposable
import jetbrains.buildServer.util.OSType
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class DotCoverWorkflowComposerTest {
    @MockK private lateinit var _pathService: PathsService
    @MockK private lateinit var _parametersService: ParametersService
    @MockK private lateinit var _argumentsService: ArgumentsService
    @MockK private lateinit var _dotCoverProjectSerializer: DotCoverProjectSerializer
    @MockK private lateinit var _loggerService: LoggerService
    @MockK private lateinit var _coverageFilterProvider: CoverageFilterProvider
    @MockK private lateinit var _virtualContext: VirtualContext
    @MockK private lateinit var _environmentVariables: EnvironmentVariables
    @MockK private lateinit var _entryPointSelector: DotCoverEntryPointSelector
    @MockK private lateinit var _blockToken: Disposable
    @MockK private lateinit var _dotCoverSettings: DotCoverSettings
    @MockK private lateinit var _monoToolProvider: MonoToolProvider
    @MockK private lateinit var _buildStepContext: BuildStepContext
    private val _defaultVariables = sequenceOf(CommandLineEnvironmentVariable("Abc", "C"))

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clearAllMocks()
        every { _blockToken.dispose() } returns Unit
        every { _argumentsService.combine(any()) } answers { arg<Sequence<String>>(0).joinToString(arg<String>(1)) }
        every { _argumentsService.split(any()) } answers { arg<String>(0).split(" ").asSequence() }
        every { _pathService.getPath(PathType.Checkout) } returns File("checkoutDir")
        every { _pathService.getPath(PathType.AgentTemp) } returns File("agentTmp")
        every { _virtualContext.resolvePath(File("agentTmp").canonicalPath) } returns "v_agentTmp"
        every { _dotCoverSettings.coveragePostProcessingEnabled } returns true
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Disabled
        every { _loggerService.writeDebug(any()) } returns Unit
        every { _loggerService.writeStandardOutput(text = any(), any()) } returns Unit
    }

    @Test
    fun shouldBeProfilerOfCodeCoverage() {
        // Given
        val composer = createInstance(VirtualFileSystemService())

        // When

        // Then
        Assert.assertEquals(composer.target, TargetType.CodeCoverageProfiler)
    }

    @DataProvider(name = "composeCases")
    fun getComposeCases(): Array<Array<Any>> {
        return arrayOf(
            arrayOf(
                CoverageConstants.PARAM_DOTCOVER,
                "dotCover",
                VirtualFileSystemService()
                    .addFile(File("dotCover", "dotCover.exe"))
                    .addFile(File("snapshot000"))))
    }

    @Test(dataProvider = "composeCases")
    fun shouldCompose(
        coverageType: String?,
        dotCoverPath: String?,
        fileSystemService: FileSystemService) {
        // Given
        val dotCoverProjectUniqueName = Path("proj000")
        val dotCoverSnapshotUniqueName = Path("snapshot000")
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path(File("wd").path)
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)
        val dotCoverExecutableFile = File(dotCoverPath, "dotCover.exe")
        val dotCoverProject = DotCoverProject(
            DotCoverCommandType.Cover,
            CoverCommandData(
                CommandLine(
                    commandLine,
                    TargetType.Tool,
                    executableFile,
                    Path("v_wd"),
                    args,
                    envVars
                ),
                Path("v_proj"),
                Path("v_snap")
            )
        )

        val expectedWorkflow = Workflow(
            sequenceOf(
                CommandLine(
                    commandLine,
                    TargetType.CodeCoverageProfiler,
                    Path("v_dotCover"),
                    Path("wd"),
                    listOf(
                        CommandLineArgument("cover", CommandLineArgumentType.Mandatory),
                        CommandLineArgument("v_proj", CommandLineArgumentType.Target),
                        CommandLineArgument("/ReturnTargetExitCode"),
                        CommandLineArgument("/AnalyzeTargetArguments=false"),
                        CommandLineArgument("--ProcessFilters=-:process1;-:process2", CommandLineArgumentType.Custom)
                    ),
                    envVars + _defaultVariables)))
        val composer = createInstance(fileSystemService)

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns coverageType
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns dotCoverPath
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns null
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns "--ProcessFilters=-:process1;-:process2"
        every { _parametersService.tryGetParameter(ParameterType.Configuration, CoverageConstants.PARAM_DOTCOVER_LOG_PATH) } returns null
        every { _pathService.getTempFileName(DotCoverWorkflowComposer.DOTCOVER_CONFIG_EXTENSION) } returns File(dotCoverProjectUniqueName.path)
        every { _pathService.getTempFileName(".${DotCoverWorkflowComposer.DOTCOVER_SNAPSHOT_EXTENSION}") } returns File(dotCoverSnapshotUniqueName.path)
        every { _dotCoverProjectSerializer.serialize(dotCoverProject, any()) } returns Unit
        every { _loggerService.writeMessage(DotCoverServiceMessage(Path(dotCoverPath!!))) } returns Unit
        every { _loggerService.importData(DotCoverWorkflowComposer.DOTCOVER_DATA_PROCESSOR_TYPE, Path("v_snap"), DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME) } returns Unit
        every { _virtualContext.resolvePath(dotCoverExecutableFile.path) } returns "v_dotCover"
        every { _virtualContext.resolvePath(dotCoverProjectUniqueName.path) } returns "v_proj"
        every { _virtualContext.resolvePath(dotCoverSnapshotUniqueName.path) } returns "v_snap"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _environmentVariables.getVariables() } returns _defaultVariables
        every { _coverageFilterProvider.attributeFilters } returns emptySequence()
        every { _coverageFilterProvider.filters } returns emptySequence()
        every { _loggerService.writeTraceBlock(any()) } returns _blockToken
        every { _loggerService.writeTrace(any()) } returns Unit
        every { _loggerService.writeWarning(any()) } returns Unit
        every { _entryPointSelector.select() } answers { Result.success(File(dotCoverExecutableFile.path)) }
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
        every { _dotCoverSettings.dotCoverHomePath } returns "dotCover"

        val actualCommandLines = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()

        // Then
        verify { _blockToken.dispose() }
        verify { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) }
        verify { _loggerService.importData(DotCoverWorkflowComposer.DOTCOVER_DATA_PROCESSOR_TYPE, Path("v_snap"), DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME) }
        Assert.assertEquals(actualCommandLines, expectedWorkflow.commandLines.toList())
    }

    @DataProvider(name = "notComposeCases")
    fun getNotComposeCases(): Array<Array<Any?>> {
        return arrayOf(
            arrayOf("other", "dotCover" as Any?),
            arrayOf("", "dotCover" as Any?),
            arrayOf("   ", "dotCover" as Any?),
            arrayOf(null, "dotCover" as Any?),
            arrayOf(CoverageConstants.PARAM_DOTCOVER, null as Any?),
            arrayOf(CoverageConstants.PARAM_DOTCOVER, "" as Any?),
            arrayOf(CoverageConstants.PARAM_DOTCOVER, "   " as Any?))
    }

    @Test(dataProvider = "notComposeCases")
    fun shouldReturnBaseWorkflowWhenCoverageDisabled(
        coverageType: String?,
        dotCoverPath: String?) {
        // Given
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)

        val composer = createInstance(VirtualFileSystemService())
        val baseWorkflow = Workflow(sequenceOf(commandLine))

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns coverageType
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns dotCoverPath
        every { _parametersService.tryGetParameter(ParameterType.Runner, "dotNetCoverage.dotCover.enabled") } returns null
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Disabled

        val actualWorkflow = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, baseWorkflow).commandLines.toList()

        // Then
        Assert.assertEquals(actualWorkflow, baseWorkflow.commandLines.toList())
    }

    @Test
    fun shouldNotWrapNonToolTargetsByDotCover() {
        // Given
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.SystemDiagnostics,
            executableFile,
            workingDirectory,
            args,
            envVars)

        val composer = createInstance(VirtualFileSystemService().addFile(File("dotCover", "dotCover.exe")))
        val baseWorkflow = Workflow(sequenceOf(commandLine))

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
        every { _parametersService.tryGetParameter(ParameterType.Runner, "dotNetCoverage.dotCover.enabled") } returns null
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _entryPointSelector.select() } answers { Result.success(File("")) }
        every { _virtualContext.resolvePath("") } returns ""

        val actualWorkflow = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, baseWorkflow).commandLines.toList()

        // Then
        Assert.assertEquals(actualWorkflow, baseWorkflow.commandLines.toList())
    }

    @DataProvider(name = "showDiagnosticCases")
    fun getShowDiagnosticCases(): Array<Array<Verbosity>> {
        return arrayOf(
            arrayOf(Verbosity.Detailed),
            arrayOf(Verbosity.Diagnostic))
    }

    @Test
    fun shouldShowDiagnostic() {
        // Given
        val dotCoverProjectUniqueName = Path("proj000")
        val dotCoverSnapshotUniqueName = Path("snapshot000")
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)
        val dotCoverExecutableFile = File("dotCover", "dotCover.exe")
        val dotCoverProject = DotCoverProject(
            DotCoverCommandType.Cover,
            CoverCommandData(CommandLine(
                commandLine,
                TargetType.Tool,
                executableFile,
                Path("v_wd"),
                args,
                envVars),
            Path("v_proj"),
            Path ("v_snap")))
        val expectedWorkflow = Workflow(
            sequenceOf(
                CommandLine(
                    commandLine,
                    TargetType.CodeCoverageProfiler,
                    Path("v_dotCover"),
                    Path("wd"),
                    listOf(
                        CommandLineArgument("cover", CommandLineArgumentType.Mandatory),
                        CommandLineArgument("v_proj", CommandLineArgumentType.Target),
                        CommandLineArgument("/ReturnTargetExitCode"),
                        CommandLineArgument("/AnalyzeTargetArguments=false")
                    ),
                    envVars + _defaultVariables)))
        val fileSystemService = VirtualFileSystemService().addFile(File("dotCover", "dotCover.exe"))
        val composer = createInstance(fileSystemService)

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns null
        every { _parametersService.tryGetParameter(ParameterType.Configuration, CoverageConstants.PARAM_DOTCOVER_LOG_PATH) } returns null
        every { _pathService.getTempFileName(DotCoverWorkflowComposer.DOTCOVER_CONFIG_EXTENSION) } returns File(dotCoverProjectUniqueName.path)
        every { _pathService.getTempFileName(".${DotCoverWorkflowComposer.DOTCOVER_SNAPSHOT_EXTENSION}") } returns File(dotCoverSnapshotUniqueName.path)
        every { _dotCoverProjectSerializer.serialize(dotCoverProject, any()) } returns Unit
        every { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) } returns Unit
        every { _loggerService.importData(DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME, Path("v_snap")) } returns Unit
        every { _virtualContext.resolvePath(dotCoverExecutableFile.path) } returns "v_dotCover"
        every { _virtualContext.resolvePath(dotCoverProjectUniqueName.path) } returns "v_proj"
        every { _virtualContext.resolvePath(dotCoverSnapshotUniqueName.path) } returns "v_snap"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _entryPointSelector.select() } answers { Result.success(File(dotCoverExecutableFile.path)) }

        every { _loggerService.writeTraceBlock("dotCover settings") } returns _blockToken
        every { _loggerService.writeTrace("Command line:") } returns Unit
        every { _loggerService.writeTrace("  \"${File("dotnet", "dotnet.exe").path}\" arg1") } returns Unit
        every { _loggerService.writeTrace("Filters:") } returns Unit
        val filter1 = CoverageFilter(CoverageFilter.CoverageFilterType.Exclude, CoverageFilter.Any, "abc")
        val filter2 = CoverageFilter(CoverageFilter.CoverageFilterType.Exclude, CoverageFilter.Any, CoverageFilter.Any, "qwerty")
        every { _coverageFilterProvider.filters } returns sequenceOf(filter1, filter2)
        every { _loggerService.writeTrace("  $filter1") } returns Unit
        every { _loggerService.writeTrace("  $filter2") } returns Unit
        every { _loggerService.writeTrace("Attribute Filters:") } returns Unit
        val attributeFilter = CoverageFilter(CoverageFilter.CoverageFilterType.Exclude, CoverageFilter.Any, "xyz")
        every { _coverageFilterProvider.attributeFilters } returns sequenceOf(attributeFilter)
        every { _loggerService.writeTrace("  $attributeFilter") } returns Unit
        every { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) } returns Unit
        every { _loggerService.writeMessage(ImportDataServiceMessage(DotCoverWorkflowComposer.DOTCOVER_DATA_PROCESSOR_TYPE, Path("v_snap"), DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME)) } returns Unit
        every { _environmentVariables.getVariables() } returns _defaultVariables
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
        every { _dotCoverSettings.dotCoverHomePath } returns "dotCover"

        val actualCommandLines = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()

        // Then
        Assert.assertEquals(actualCommandLines, expectedWorkflow.commandLines.toList())
    }

    @Test
    fun shouldNotPublishServiceMessageWhenWorkflowFailed() {
        // Given
        val dotCoverProjectUniqueName = Path("proj000")
        val dotCoverSnapshotUniqueName = Path("snapshot000")
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1", CommandLineArgumentType.Secondary))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)
        val dotCoverExecutableFile = File("dotCover", "dotCover.exe")
        val dotCoverProject = DotCoverProject(
            DotCoverCommandType.Cover,
            CoverCommandData(CommandLine(
                commandLine,
                TargetType.Tool,
                executableFile,
                Path("v_wd"),
                args,
                envVars),
            Path("v_proj"),
            Path ("v_snap")))
        val expectedWorkflow = Workflow(
            sequenceOf(
                CommandLine(
                    commandLine,
                    TargetType.CodeCoverageProfiler,
                    Path("v_dotCover"),
                    Path("wd"),
                    listOf(
                        CommandLineArgument("cover", CommandLineArgumentType.Mandatory),
                        CommandLineArgument("v_proj", CommandLineArgumentType.Target),
                        CommandLineArgument("/ReturnTargetExitCode"),
                        CommandLineArgument("/AnalyzeTargetArguments=false")
                    ),
                    envVars + _defaultVariables)))
        val fileSystemService = VirtualFileSystemService().addFile(File("dotCover", "dotCover.exe"))
        val composer = createInstance(fileSystemService)

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns null
        every { _parametersService.tryGetParameter(ParameterType.Configuration, CoverageConstants.PARAM_DOTCOVER_LOG_PATH) } returns null
        every { _pathService.getTempFileName(DotCoverWorkflowComposer.DOTCOVER_CONFIG_EXTENSION) } returns File(dotCoverProjectUniqueName.path)
        every { _pathService.getTempFileName(".${DotCoverWorkflowComposer.DOTCOVER_SNAPSHOT_EXTENSION}") } returns File(dotCoverSnapshotUniqueName.path)
        every { _dotCoverProjectSerializer.serialize(dotCoverProject, any()) } returns Unit
        every { _virtualContext.resolvePath(dotCoverExecutableFile.path) } returns "v_dotCover"
        every { _virtualContext.resolvePath(dotCoverProjectUniqueName.path) } returns "v_proj"
        every { _virtualContext.resolvePath(dotCoverSnapshotUniqueName.path) } returns "v_snap"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _entryPointSelector.select() } answers { Result.success(File(dotCoverExecutableFile.path)) }
        every { _environmentVariables.getVariables() } returns _defaultVariables
        every { _coverageFilterProvider.attributeFilters } returns emptySequence()
        every { _coverageFilterProvider.filters } returns emptySequence()
        every { _loggerService.writeTraceBlock(any()) } returns _blockToken
        every { _loggerService.writeTrace(any()) } returns Unit
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
        every { _dotCoverSettings.dotCoverHomePath } returns "dotCover"

        val actualCommandLines = composer.compose(WorkflowContextStub(WorkflowStatus.Failed, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()

        // Then
        verify(exactly = 0) { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) }
        verify(exactly = 0) { _loggerService.writeMessage(ImportDataServiceMessage(DotCoverWorkflowComposer.DOTCOVER_DATA_PROCESSOR_TYPE, Path("v_snap"), DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME)) }

        Assert.assertEquals(actualCommandLines, expectedWorkflow.commandLines.toList())
    }

    @Test
    fun shouldTakeInAccountDotCoverArguments() {
        // Given
        val dotCoverProjectUniqueName = Path("proj000")
        val dotCoverSnapshotUniqueName = Path("snapshot000")
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)
        val dotCoverExecutableFile = File("dotCover", "dotCover.exe")
        val dotCoverProject = DotCoverProject(
            DotCoverCommandType.Cover,
            CoverCommandData(CommandLine(
                commandLine,
                TargetType.Tool,
                executableFile,
                Path("v_wd"),
                args,
                envVars),
            Path("v_proj"),
            Path ("v_snap")))
        val expectedWorkflow = Workflow(
            sequenceOf(
                CommandLine(
                    commandLine,
                    TargetType.CodeCoverageProfiler,
                    Path("v_dotCover"),
                    Path("wd"),
                    listOf(
                        CommandLineArgument("cover", CommandLineArgumentType.Mandatory),
                        CommandLineArgument("v_proj", CommandLineArgumentType.Target),
                        CommandLineArgument("/ReturnTargetExitCode"),
                        CommandLineArgument("/AnalyzeTargetArguments=false"),
                        CommandLineArgument("/ProcessFilters=-:sqlservr.exe", CommandLineArgumentType.Custom),
                        CommandLineArgument("/arg", CommandLineArgumentType.Custom)
                    ),
                    envVars + _defaultVariables)))

        val fileSystemService = VirtualFileSystemService().addFile(File("dotCover", "dotCover.exe"))
        val composer = createInstance(fileSystemService)

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns "/ProcessFilters=-:sqlservr.exe /arg"
        every { _parametersService.tryGetParameter(ParameterType.Configuration, CoverageConstants.PARAM_DOTCOVER_LOG_PATH) } returns null
        every { _pathService.getTempFileName(DotCoverWorkflowComposer.DOTCOVER_CONFIG_EXTENSION) } returns File(dotCoverProjectUniqueName.path)
        every { _pathService.getTempFileName(".${DotCoverWorkflowComposer.DOTCOVER_SNAPSHOT_EXTENSION}") } returns File(dotCoverSnapshotUniqueName.path)
        every { _dotCoverProjectSerializer.serialize(dotCoverProject, any()) } returns Unit
        every { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) } returns Unit
        every { _loggerService.importData(DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME, Path("v_snap")) } returns Unit
        every { _virtualContext.resolvePath(dotCoverExecutableFile.path) } returns "v_dotCover"
        every { _virtualContext.resolvePath(dotCoverProjectUniqueName.path) } returns "v_proj"
        every { _virtualContext.resolvePath(dotCoverSnapshotUniqueName.path) } returns "v_snap"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _entryPointSelector.select() } answers { Result.success(File(dotCoverExecutableFile.path)) }
        every { _environmentVariables.getVariables() } returns _defaultVariables
        every { _coverageFilterProvider.attributeFilters } returns emptySequence()
        every { _coverageFilterProvider.filters } returns emptySequence()
        every { _loggerService.writeTraceBlock(any()) } returns _blockToken
        every { _loggerService.writeTrace(any()) } returns Unit
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
        every { _dotCoverSettings.dotCoverHomePath } returns "dotCover"

        val actualCommandLines = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()

        // Then
        Assert.assertEquals(actualCommandLines, expectedWorkflow.commandLines.toList())
    }

    @Test
    fun shouldSupportLogFileConfigParamArguments() {
        // Given
        val dotCoverProjectUniqueName = Path("proj000")
        val dotCoverSnapshotUniqueName = Path("snapshot000")
        val executableFile = Path(File("dotnet", "dotnet.exe").path)
        val workingDirectory = Path("wd")
        val args = listOf(CommandLineArgument("arg1"))
        val envVars = listOf(CommandLineEnvironmentVariable("var1", "val1"))
        val commandLine = CommandLine(
            null,
            TargetType.Tool,
            executableFile,
            workingDirectory,
            args,
            envVars)
        val dotCoverExecutableFile = File("dotCover", "dotCover.exe")
        val dotCoverProject = DotCoverProject(
            DotCoverCommandType.Cover,
            CoverCommandData(CommandLine(
                commandLine,
                TargetType.Tool,
                executableFile,
                Path("v_wd"),
                args,
                envVars),
            Path("v_proj"),
            Path ("v_snap")))
        val expectedWorkflow = Workflow(
            sequenceOf(
                CommandLine(
                    commandLine,
                    TargetType.CodeCoverageProfiler,
                    Path("v_dotCover"),
                    Path("wd"),
                    listOf(
                        CommandLineArgument("cover", CommandLineArgumentType.Mandatory),
                        CommandLineArgument("v_proj", CommandLineArgumentType.Target),
                        CommandLineArgument("/ReturnTargetExitCode"),
                        CommandLineArgument("/AnalyzeTargetArguments=false"),
                        CommandLineArgument("/LogFile=v_log", CommandLineArgumentType.Infrastructural)
                    ),
                    envVars + _defaultVariables)))

        val fileSystemService = VirtualFileSystemService().addFile(File("dotCover", "dotCover.exe"))
        val composer = createInstance(fileSystemService)

        // When
        every { _virtualContext.targetOSType } returns OSType.WINDOWS
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_ARGUMENTS) } returns null
        every { _parametersService.tryGetParameter(ParameterType.Configuration, CoverageConstants.PARAM_DOTCOVER_LOG_PATH) } returns "logPath"
        every { _pathService.getTempFileName(DotCoverWorkflowComposer.DOTCOVER_CONFIG_EXTENSION) } returns File(dotCoverProjectUniqueName.path)
        every { _pathService.getTempFileName(".${DotCoverWorkflowComposer.DOTCOVER_SNAPSHOT_EXTENSION}") } returns File(dotCoverSnapshotUniqueName.path)
        every { _dotCoverProjectSerializer.serialize(dotCoverProject, any()) } returns Unit
        every { _loggerService.writeMessage(DotCoverServiceMessage(Path("dotCover"))) } returns Unit
        every { _loggerService.importData(DotCoverWorkflowComposer.DOTCOVER_TOOL_NAME, Path("v_snap")) } returns Unit
        every { _virtualContext.resolvePath(dotCoverExecutableFile.path) } returns "v_dotCover"
        every { _virtualContext.resolvePath(dotCoverProjectUniqueName.path) } returns "v_proj"
        every { _virtualContext.resolvePath(dotCoverSnapshotUniqueName.path) } returns "v_snap"
        every { _virtualContext.resolvePath(File("logPath", "dotCover99.log").canonicalPath) } returns "v_log"
        every { _virtualContext.resolvePath("wd") } returns "v_wd"
        every { _entryPointSelector.select() } answers { Result.success(File(dotCoverExecutableFile.path)) }
        every { _environmentVariables.getVariables() } returns _defaultVariables
        every { _coverageFilterProvider.attributeFilters } returns emptySequence()
        every { _coverageFilterProvider.filters } returns emptySequence()
        every { _loggerService.writeTraceBlock(any()) } returns _blockToken
        every { _loggerService.writeTrace(any()) } returns Unit
        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
        every { _dotCoverSettings.dotCoverHomePath } returns "dotCover"

        val actualCommandLines = composer.compose(WorkflowContextStub(WorkflowStatus.Running, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()

        // Then
        Assert.assertEquals(actualCommandLines, expectedWorkflow.commandLines.toList())
    }

//    @Test
//    fun shouldThrowExceptionWhenRequiredCrossPlatformDotCoverButCannotFindIt() {
//        // Given
//        val commandLine = CommandLine(
//            null,
//            TargetType.Tool,
//            Path(File("dotnet", "dotnet").path),
//            Path("wd"),
//            listOf(CommandLineArgument("arg1", CommandLineArgumentType.Secondary)),
//            listOf(CommandLineEnvironmentVariable("var1", "val1")))
//        val fileSystemService = VirtualFileSystemService()
//        val composer = createInstance(fileSystemService)
//
//        // When
//        every { _virtualContext.targetOSType } returns OSType.UNIX
//        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_TYPE) } returns CoverageConstants.PARAM_DOTCOVER
//        every { _parametersService.tryGetParameter(ParameterType.Runner, CoverageConstants.PARAM_DOTCOVER_HOME) } returns "dotCover"
//        every { _entryPointSelector.select() } returns Result.failure(ToolCannotBeFoundException("TOOL CAN NOT BE FOUND"))
//        every { _dotCoverSettings.dotCoverMode } returns DotCoverMode.Wrapper
//        every { _dotCoverSettings.dotCoverHomePath } returns ""
//        every { _loggerService.writeWarning(any()) } returns Unit
//
//        // Then
//        try {
//            composer.compose(WorkflowContextStub(WorkflowStatus.Failed, CommandResultExitCode(0)), Unit, Workflow(sequenceOf(commandLine))).commandLines.toList()
//            Assert.fail("Exception is required")
//        }
//        catch (ex: RunBuildException) {
//            Assert.assertEquals(ex.message, "dotCover run failed: TOOL CAN NOT BE FOUND")
//        }
//    }

    private fun createInstance(fileSystemService: FileSystemService): SimpleWorkflowComposer {
        return DotCoverWorkflowComposer(
            _pathService,
            fileSystemService,
            _dotCoverProjectSerializer,
            _loggerService,
            _argumentsService,
            _coverageFilterProvider,
            _virtualContext,
            _environmentVariables,
            _entryPointSelector,
            _dotCoverSettings,
            listOf(
                DotCoverCoverCommandLineBuilder(_pathService, _virtualContext, _parametersService, fileSystemService, _argumentsService, _buildStepContext, _monoToolProvider),
                DotCoverMergeCommandLineBuilder(_pathService, _virtualContext, _parametersService, fileSystemService),
                DotCoverReportCommandLineBuilder(_pathService, _virtualContext, _parametersService, fileSystemService)
            ))
    }
}
