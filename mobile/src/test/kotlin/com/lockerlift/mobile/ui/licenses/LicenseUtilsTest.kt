package com.lockerlift.mobile.ui.licenses

import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Funding
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Organization
import com.mikepenz.aboutlibraries.entity.Scm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseUtilsTest {

    private fun license(
        name: String,
        content: String? = null,
        hash: String = name
    ) = License(name = name, url = null, year = null, spdxId = null, licenseContent = content, hash = hash)

    private fun library(
        uniqueId: String,
        name: String,
        version: String? = null,
        licenses: Set<License> = emptySet()
    ) = Library(
        uniqueId = uniqueId,
        artifactVersion = version,
        name = name,
        description = null,
        website = null,
        developers = emptyList<Developer>(),
        organization = Organization("", null),
        scm = Scm(null, null, null),
        licenses = licenses,
        funding = emptySet<Funding>(),
        tag = null
    )

    @Test
    fun `toLicenseItems sorts libraries alphabetically case-insensitive`() {
        val libs = listOf(
            library("com.example:zeta", "Zeta Lib"),
            library("com.example:alpha", "alpha lib"),
            library("com.example:Beta", "Beta Lib")
        )

        val items = LicenseUtils.toLicenseItems(libs)

        assertEquals(listOf("alpha lib", "Beta Lib", "Zeta Lib"), items.map { it.libraryName })
    }

    @Test
    fun `toLicenseItems keeps artifact version and sorts multiple licenses by name`() {
        val libs = listOf(
            library(
                "com.example:room",
                "Room",
                version = "2.8.5",
                licenses = setOf(license("Apache-2.0"), license("Android SDK"))
            )
        )

        val items = LicenseUtils.toLicenseItems(libs)

        assertEquals("2.8.5", items.single().artifactVersion)
        assertEquals(listOf("Android SDK", "Apache-2.0"), items.single().licenses.map { it.name })
    }

    @Test
    fun `formatLicenseNames joins license names with comma`() {
        val formatted = LicenseUtils.formatLicenseNames(listOf(license("MIT"), license("Apache-2.0")))

        assertEquals("MIT, Apache-2.0", formatted)
    }

    @Test
    fun `formatLicenseContent concatenates embedded license texts`() {
        val formatted = LicenseUtils.formatLicenseContent(
            listOf(
                license("MIT", content = "MIT text"),
                license("Apache-2.0", content = "Apache text")
            )
        )

        assertEquals("MIT text\n\nApache text", formatted)
    }

    @Test
    fun `formatLicenseContent returns blank when no content embedded`() {
        val formatted = LicenseUtils.formatLicenseContent(listOf(license("MIT")))

        assertTrue(formatted.isBlank())
    }
}
