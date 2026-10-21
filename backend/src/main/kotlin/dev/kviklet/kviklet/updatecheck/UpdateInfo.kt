package dev.kviklet.kviklet.updatecheck

import java.time.Instant

enum class UpdateChannel {
    /** A tagged release build; newer releases are offered. */
    RELEASE,

    /** An image built from a commit on main; the commits since then are offered. */
    MAIN,
}

data class ReleaseNote(
    val tag: String,
    val name: String?,
    /** Markdown, as written on GitHub. The frontend renders it without raw HTML. */
    val body: String,
    val publishedAt: Instant,
    val url: String,
)

data class CommitNote(val sha: String, val message: String, val committedAt: Instant, val url: String)

/** What this instance is missing compared to GitHub. Absent (null) whenever there is nothing to show. */
data class UpdateInfo(
    val channel: UpdateChannel,
    val latestVersion: String?,
    /** Every newer release, newest first. */
    val releases: List<ReleaseNote> = emptyList(),
    val behindBy: Int? = null,
    /** The newest commits on main this build is missing, newest first, capped at [UpdateChecker.MAX_COMMITS]. */
    val commits: List<CommitNote> = emptyList(),
    val url: String,
)
