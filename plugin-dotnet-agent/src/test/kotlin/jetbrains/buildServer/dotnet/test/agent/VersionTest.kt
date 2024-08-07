

package jetbrains.buildServer.dotnet.test.agent

import jetbrains.buildServer.agent.Version
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class VersionTest {
    @DataProvider
    fun testDataComparable(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf(Version(2), Version(1), 1),
            arrayOf(Version(2, 0), Version(1, 0), 1),
            arrayOf(Version(2), Version(2), 0),
            arrayOf(Version(0, 2), Version(0, 2), 0),
            arrayOf(Version(2), Version(2, 0), 0),
            arrayOf(Version(2, 0, 1), Version(2, 0, 1), 0),
            arrayOf(Version(2, 0, 1, 3), Version(2, 0, 1, 3), 0),
            arrayOf(Version(2, 0, 1, 3, 7), Version(2, 0, 1, 3, 7), 0),
            arrayOf(Version(2, 0, 1, 3, 8), Version(2, 0, 1, 3, 7), 1),
            arrayOf(Version(2, 1), Version(2, 1, 0), 0),
            arrayOf(Version(2, 1), Version(2, 1, 0), 0),
            arrayOf(Version(2, 0, 1), Version(2, 0, 1), 0),
            arrayOf(Version(1), Version(2), -1),
            arrayOf(Version(1, 2), Version(1, 1), 1),
            arrayOf(Version(0), Version(1), -1),
            arrayOf(Version(3, 3), Version(0), 1),
            arrayOf(Version(0, 3), Version(5), -1),
            arrayOf(Version.parse("1.0.0"), Version.parse("1.0.0-beta"), 1),
            arrayOf(Version.parse("1.0.0-beta1"), Version.parse("1.0.0-beta2"), -1),
            arrayOf(Version.parse("1.0.0-beta+meta1"), Version.parse("1.0.0-beta+meta2"), 0)
        )
    }

    @Test(dataProvider = "testDataComparable")
    fun shouldBeComparable(version1: Version, version2: Version, expectedCompareResult: Int) {
        // Given

        // When
        val actualCompareResult = version1.compareTo(version2)

        // Then
        Assert.assertEquals(actualCompareResult, expectedCompareResult)
    }

    @DataProvider
    fun testDataToString(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf(Version(2), "2.0.0"),
            arrayOf(Version(99, 3, 10), "99.3.10"),
            arrayOf(Version(0, 2), "0.2.0"),
            arrayOf(Version(2, 0, 0), "2.0.0"),
            arrayOf(Version(0, 0, 2), "0.0.2"),
            arrayOf(Version(99, 3, 10, 33), "99.3.10.33"),
            arrayOf(Version(99, 3, 10, 33, 765), "99.3.10.33.765"),
            arrayOf(Version.parse("0.1.2-beta+meta"), "0.1.2-beta+meta"),
            arrayOf(Version.parse("03.001.02"), "03.001.02"),
            arrayOf(Version.parse("v0.1.2-beta+meta"), "0.1.2-beta+meta"),
            arrayOf(Version.parse("vv03.001.02"), "03.001.02")
        )
    }

    @Test(dataProvider = "testDataToString")
    fun shouldSupportToString(version: Version, expectedString: String) {
        // Given

        // When
        val actualString = version.toString()

        // Then
        Assert.assertEquals(actualString, expectedString)
    }

    @DataProvider
    fun testDataEquitable(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf(Version(1), Version(1), true),
            arrayOf(Version(0, 1), Version(0, 1), true),
            arrayOf(Version(1), Version(1, 0), true),
            arrayOf(Version(1), Version(1, 0, 0), true),
            arrayOf(Version(1), Version(2), false),
            arrayOf(Version(1, 0), Version(2, 0), false),
            arrayOf(Version(0, 1, 0), Version(0, 2, 0), false),
            arrayOf(Version(0), Version(2), false),
            arrayOf(Version(1, 2), Version(1, 2), true),
            arrayOf(Version(1, 2, 3), Version(1, 2, 3), true),
            arrayOf(Version(1, 2, 3, 4), Version(1, 2, 3, 4), true),
            arrayOf(Version(1, 2, 3, 4, 5), Version(1, 2, 3, 4, 5), true),
            arrayOf(Version(1, 2), Version(1), false),
            arrayOf(Version(1, 2), Version(1, 3), false),
            arrayOf(Version(1, 2), Version(2, 2), false),
            arrayOf(Version.parse("1.0.0-beta+meta1"), Version.parse("1.0.0-beta+meta2"), true),
            arrayOf(Version.parse("1.0.0-beta1"), Version.parse("1.0.0-beta2"), false)
        )
    }

    @Test(dataProvider = "testDataEquitable")
    fun shouldBeEquitable(version1: Version, version2: Version, expectedEqualsResult: Boolean) {
        // Given

        // When
        val actualEqualsResult1 = version1 == version2
        val actualEqualsResult2 = version1 == version2
        val actualEqualsResult3 = version2 == version1
        val hashCode1 = version1.hashCode()
        val hashCode2 = version1.hashCode()

        // Then
        Assert.assertEquals(actualEqualsResult1, expectedEqualsResult)
        Assert.assertEquals(actualEqualsResult2, expectedEqualsResult)
        Assert.assertEquals(actualEqualsResult3, expectedEqualsResult)
        if (expectedEqualsResult) {
            Assert.assertTrue(hashCode1 == hashCode2)
        }
    }

    @Test
    fun `should return true when equal to empty`() {
        // Given

        // When

        // Then
        Assert.assertTrue(Version.Empty.isEmpty())
        Assert.assertFalse(Version.FirstInspectCodeWithExtensionsOptionVersion.isEmpty())
        Assert.assertFalse(Version.parse("1.1.1").isEmpty())
    }

    @DataProvider
    fun testDataParse(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf("", Version.Empty),
            arrayOf("1", Version(1)),
            arrayOf("10.0-A", Version(10, 0, 0, "A")),
            arrayOf("1.23.99", Version(1, 23, 99)),
            arrayOf("1.23.99.88", Version(1, 23, 99, 88)),
            arrayOf("7.0.7600.16385.40715", Version(7, 0, 7600, 16385, 40715)),
            arrayOf("abc", Version.Empty),
            arrayOf("abc.xyz", Version.Empty),
            arrayOf("abc.", Version.Empty),
            arrayOf("1.", Version.Empty),
            arrayOf(".xyz", Version.Empty),
            arrayOf(".1", Version.Empty),
            arrayOf("abc.1", Version.Empty),
            arrayOf("1.abc", Version.Empty),
            arrayOf(" Version:     1.0.0-beta-001598", Version.parse("1.0.0-beta-001598"))
        )
    }

    @Test(dataProvider = "testDataParse")
    fun shouldParse(text: String, expectedVersion: Version) {
        // Given

        // When
        val actualVersion = Version.parse(text)

        // Then
        Assert.assertEquals(actualVersion, expectedVersion)
    }

    @DataProvider
    fun parseSimplifiedData(): Array<Array<out Any?>> = arrayOf(
        arrayOf("", Version.Empty),
        arrayOf("1", Version(1)),
        arrayOf("1.1", Version(1, 1)),
        arrayOf("1.1.1", Version(1, 1, 1)),
        arrayOf("1.1.1.1", Version(1, 1, 1)),
        arrayOf("1.1.1.1.1", Version(1, 1, 1)),
        arrayOf("1 minor", Version(1)),
        arrayOf("1.1 patch", Version(1, 1)),
        arrayOf("2023.3 EAP 1 D", Version(2023, 3)),
        arrayOf("2023.3.3 EAP 1 D", Version(2023, 3, 3)),
        arrayOf("2023.3.3.3 EAP 1 D", Version(2023, 3, 3)),
        arrayOf("2023.3EAP1D", Version(2023, 3)),
        arrayOf("2023.3.3EAP1D", Version(2023, 3, 3)),
        arrayOf("2023.3.3.3EAP1D", Version(2023, 3, 3)),
        arrayOf("abc", Version.Empty),
        arrayOf(".1", Version.Empty),
        arrayOf(" Version:     1.1.1-beta-001598", Version(1, 1, 1))
    )

    @Test(dataProvider = "parseSimplifiedData")
    fun `should parse simplified version`(versionStr: String, expectedVersion: Version) {
        // Given

        // When
        val actualVersion = Version.parseSimplified(versionStr)

        // Then
        Assert.assertEquals(actualVersion, expectedVersion)
    }

    @DataProvider
    fun testDataIsValid(): Array<Array<out Any?>> {
        return arrayOf(
            arrayOf("", false),
            arrayOf("1", true),
            arrayOf("10.0-A", true),
            arrayOf("1.23.99", true),
            arrayOf("1.23.99.88", true),
            arrayOf("7.0.7600.16385.40715", true),
            arrayOf(" Version:     1.0.0-beta-001598", true),
            arrayOf("abc", false),
            arrayOf("abc.xyz", false),
            arrayOf("abc.", false),
            arrayOf("1.", false),
            arrayOf(".xyz", false),
            arrayOf(".1", false),
            arrayOf("abc.1", false),
            arrayOf("1.abc", false),
        )
    }

    @Test(dataProvider = "testDataIsValid")
    fun testDataIsValid(text: String, isValid: Boolean) {
        // Then
        Assert.assertEquals(Version.isValid(text), isValid)
    }
}