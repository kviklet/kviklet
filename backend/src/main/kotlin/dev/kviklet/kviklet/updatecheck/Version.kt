package dev.kviklet.kviklet.updatecheck

/**
 * A release tag such as `0.9.2` or `v0.6.0-pre2`. Build metadata (`+...`) is ignored, and a missing
 * patch number counts as 0.
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val preRelease: List<String>) :
    Comparable<Version> {

    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        .takeIf { it != 0 }
        ?: comparePreRelease(preRelease, other.preRelease)

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(tag: String): Version? {
            val match = PATTERN.matchEntire(tag.trim()) ?: return null
            val (major, minor, patch, preRelease) = match.destructured
            return Version(
                major.toInt(),
                minor.toInt(),
                patch.ifEmpty { "0" }.toInt(),
                preRelease.split('.').filter { it.isNotEmpty() },
            )
        }

        // Semver: a release outranks its pre-releases; numeric identifiers compare as numbers and
        // rank below alphanumeric ones; a longer list wins when all shared identifiers are equal.
        private fun comparePreRelease(a: List<String>, b: List<String>): Int {
            if (a.isEmpty() || b.isEmpty()) return b.size.compareTo(a.size).coerceIn(-1, 1)
            for ((x, y) in a.zip(b)) {
                val xNum = x.toIntOrNull()
                val yNum = y.toIntOrNull()
                val result = when {
                    xNum != null && yNum != null -> xNum.compareTo(yNum)
                    xNum != null -> -1
                    yNum != null -> 1
                    else -> x.compareTo(y)
                }
                if (result != 0) return result
            }
            return a.size.compareTo(b.size)
        }
    }
}
