/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.dotnet.test.dotnet

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.PathType
import jetbrains.buildServer.agent.runner.PathsService
import jetbrains.buildServer.dotnet.*
import jetbrains.buildServer.rx.subjectOf
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class MSBuildRegistryAgentPropertiesProviderTest {
    @MockK private lateinit var _windowsRegistry: WindowsRegistry
    @MockK private lateinit var _msuildValidator: MSBuildValidator

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    fun shouldProvideConfigParams() {
        // Given
        val rootKey = WindowsRegistryKey.create(
                WindowsRegistryBitness.Bitness64,
                WindowsRegistryHive.LOCAL_MACHINE,
                "SOFTWARE",
                "Microsoft",
                "MSBuild",
                "ToolsVersions")

        val rootKey32 = WindowsRegistryKey.create(
                WindowsRegistryBitness.Bitness32,
                WindowsRegistryHive.LOCAL_MACHINE,
                "SOFTWARE",
                "Microsoft",
                "MSBuild",
                "ToolsVersions")

        // When
        val regItems = mutableListOf<Any>(
                rootKey,
                rootKey + "12.0",
                WindowsRegistryValue(rootKey + "12.0" + "MSBuildToolsPath", WindowsRegistryValueType.Str, "msbuild12"),
                rootKey + "13.0",
                WindowsRegistryValue(rootKey + "13.0" + "MSBuildToolsPath", WindowsRegistryValueType.Str, "msbuild13"),
                rootKey + "14.0",
                WindowsRegistryValue(rootKey + "13.0" + "MSBuildToolsPath", WindowsRegistryValueType.Long, "msbuild14"),
                WindowsRegistryValue(rootKey + "15.0" + "MSBuildToolsPath", WindowsRegistryValueType.Text, "msbuild15"),
                rootKey + "16.0",
                WindowsRegistryValue(rootKey + "16.0" + "MSBuildToolsPathAaa", WindowsRegistryValueType.Str, "msbuild16"),
                WindowsRegistryValue(rootKey32 + "17.0" + "MSBuildToolsPath", WindowsRegistryValueType.Str, "msbuild17")
        )

        every { _windowsRegistry.get(any<WindowsRegistryKey>(), any<WindowsRegistryVisitor>()) } answers  {
            val visitor = arg<WindowsRegistryVisitor>(1)
            for (item in regItems) {
                when (item) {
                    is WindowsRegistryValue -> visitor.accept(item)
                    is WindowsRegistryKey -> visitor.accept(item)
                }
            }
            regItems.clear()
            value
        }

        every { _msuildValidator.isValide(File("msbuild12")) } returns true
        every { _msuildValidator.isValide(File("msbuild13")) } returns false
        every { _msuildValidator.isValide(File("msbuild17")) } returns true

        val propertiesProvider = createInstance()

        // Then
        Assert.assertEquals(propertiesProvider.desription, "MSBuild in registry")
        Assert.assertEquals(
                propertiesProvider.properties.toList(),
                listOf(
                        AgentProperty("MSBuildTools12.0_x64_Path", "msbuild12"),
                        AgentProperty("MSBuildTools17.0_x86_Path", "msbuild17")))
    }

    private fun createInstance() = MSBuildRegistryAgentPropertiesProvider(
                _windowsRegistry,
                _msuildValidator)
}