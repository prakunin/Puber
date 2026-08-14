package com.kino.puber.data.repository

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        return compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        fun parse(raw: String): AppVersion? {
            val normalized = raw
                .trim()
                .let { version -> if (version.startsWith("v", ignoreCase = true)) version.drop(1) else version }
                .substringBefore('-')

            val parts = normalized.split('.')
            if (parts.size != VERSION_PARTS_COUNT) {
                return null
            }

            // toIntOrNull() alone would accept a signed part such as "-1", which is not a version.
            val versionParts = parts.map { part ->
                part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() ?: return null
            }

            return AppVersion(
                major = versionParts[0],
                minor = versionParts[1],
                patch = versionParts[2],
            )
        }

        private const val VERSION_PARTS_COUNT = 3
    }
}
