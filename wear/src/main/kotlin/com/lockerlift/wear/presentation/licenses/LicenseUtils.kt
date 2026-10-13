package com.lockerlift.wear.presentation.licenses

import com.mikepenz.aboutlibraries.entity.License

data class LicenseItem(
    val libraryName: String,
    val artifactVersion: String?,
    val licenses: List<License>
)

object LicenseUtils {

    fun toLicenseItems(
        libraries: List<com.mikepenz.aboutlibraries.entity.Library>
    ): List<LicenseItem> =
        libraries
            .sortedBy { it.name.lowercase() }
            .map { lib ->
                LicenseItem(
                    libraryName = lib.name,
                    artifactVersion = lib.artifactVersion,
                    licenses = lib.licenses.toList().sortedBy { it.name.lowercase() }
                )
            }

    fun formatLicenseNames(licenses: List<License>): String =
        licenses.joinToString(", ") { it.name }

    fun formatLicenseContent(licenses: List<License>): String =
        licenses.mapNotNull { license ->
            license.licenseContent ?: license.url
        }.joinToString("\n\n")
}
