package jetbrains.buildServer.dotnet.test.script

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.FileSystemService
import jetbrains.buildServer.agent.Version
import jetbrains.buildServer.agent.VirtualContext
import jetbrains.buildServer.dotnet.discovery.dotnetRuntime.DotnetRuntime
import jetbrains.buildServer.dotnet.discovery.dotnetRuntime.DotnetRuntimesProvider
import jetbrains.buildServer.dotnet.test.agent.VirtualFileSystemService
import jetbrains.buildServer.script.ToolVersionResolverImpl
import jetbrains.buildServer.script.CsiTool
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class ToolVersionResolverTest {
    @MockK
    private lateinit var _runtimesProvider: DotnetRuntimesProvider
    @MockK
    private lateinit var _virtualContext: VirtualContext
    private val DefaultToolsPath = File("tools")
    private val DotnetPath = "dotnet"

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @DataProvider(name = "testCases")
    fun getCases(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(5, 0), "")),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net5.0")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "net5.0"), Version(5, 0))
            ),

            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(3, 1), "")),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "netcoreapp3.1")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "netcoreapp3.1"), Version(3, 1))
            ),

            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(5, 0), "")),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net6.0")),
                DefaultToolsPath,
                false,
                null
            ),

            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(5, 0, 0, "-beta"), "")),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net5.0")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "net5.0"), Version(5, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(6, 0, 0, "beta"), ""),
                    DotnetRuntime(File("."), Version(3, 1), "")
                ),
                VirtualFileSystemService()
                    .addDirectory(File(DefaultToolsPath, "net5.0"))
                    .addDirectory(File(DefaultToolsPath, "net6.0"))
                    .addDirectory(File(DefaultToolsPath, "netcoreapp3.1")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "net6.0"), Version(6, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(8, 0), ""),
                    DotnetRuntime(File("."), Version(9, 0), "")
                ),
                VirtualFileSystemService()
                    .addDirectory(File(DefaultToolsPath, "net5.0"))
                    .addDirectory(File(DefaultToolsPath, "net6.0")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "net6.0"), Version(6, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(3, 1), "")
                ),
                VirtualFileSystemService()
                    .addDirectory(File(DefaultToolsPath, "net5.0"))
                    .addDirectory(File(DefaultToolsPath, "netcoreapp3.1")),
                DefaultToolsPath,
                false,
                CsiTool(File(DefaultToolsPath, "net5.0"), Version(5, 0))
            ),

            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(5, 0), "")),
                VirtualFileSystemService().addFile(File(DefaultToolsPath, "net5.0")),
                DefaultToolsPath,
                false,
                null
            ),

            arrayOf(
                sequenceOf(DotnetRuntime(File("."), Version(5, 0), "")),
                VirtualFileSystemService(),
                DefaultToolsPath,
                false,
                null
            ),

            arrayOf(
                emptySequence<DotnetRuntime>(),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net5.0")),
                DefaultToolsPath,
                false,
                null
            ),

            arrayOf(
                emptySequence<DotnetRuntime>(),
                VirtualFileSystemService(),
                DefaultToolsPath,
                false,
                null
            ),

            // In docker
            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(6, 0), "")
                ),
                VirtualFileSystemService()
                    .addDirectory(File(DefaultToolsPath, "net5.0"))
                    .addDirectory(File(DefaultToolsPath, "net6.0")),
                DefaultToolsPath,
                true,
                CsiTool(File(DefaultToolsPath, "net6.0"), Version(6, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(6, 0), ""),
                    DotnetRuntime(File("."), Version(7, 0), "")
                ),
                VirtualFileSystemService()
                    .addDirectory(File(DefaultToolsPath, "net5.0"))
                    .addDirectory(File(DefaultToolsPath, "net6.0"))
                    .addDirectory(File(DefaultToolsPath, "net7.0")),
                DefaultToolsPath,
                true,
                CsiTool(File(DefaultToolsPath, "net6.0"), Version(6, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(6, 0), "")
                ),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net5.0")),
                DefaultToolsPath,
                true,
                CsiTool(File(DefaultToolsPath, "net5.0"), Version(5, 0))
            ),

            arrayOf(
                emptySequence<DotnetRuntime>(),
                VirtualFileSystemService().addDirectory(File(DefaultToolsPath, "net6.0")),
                DefaultToolsPath,
                true,
                CsiTool(File(DefaultToolsPath, "net6.0"), Version(6, 0))
            ),

            arrayOf(
                sequenceOf(
                    DotnetRuntime(File("."), Version(5, 0), ""),
                    DotnetRuntime(File("."), Version(6, 0), "")
                ),
                VirtualFileSystemService(),
                DefaultToolsPath,
                true,
                null
            ),

            arrayOf(
                emptySequence<DotnetRuntime>(),
                VirtualFileSystemService(),
                DefaultToolsPath,
                true,
                null
            )
        )
    }

    @Test(dataProvider = "testCases")
    fun shouldResolve(
        runtimes: Sequence<DotnetRuntime>,
        fileSystemService: FileSystemService,
        toolsPath: File,
        isVurtual: Boolean,
        expectedTool: CsiTool?
    ) {
        // arrange
        every { _runtimesProvider.getRuntimes() }.returns(runtimes)
        every { _virtualContext.isVirtual } returns isVurtual
        val resolver = createInstance(fileSystemService)

        // act
        var actualTool: CsiTool? = null
        try {
            actualTool = resolver.resolve(toolsPath)
        } catch (ignored: Exception) {
        }

        // assert
        Assert.assertEquals(actualTool, expectedTool)
    }

    private fun createInstance(fileSystemService: FileSystemService) =
        ToolVersionResolverImpl(fileSystemService, _runtimesProvider, _virtualContext)
}