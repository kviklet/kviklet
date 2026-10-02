// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneOffset

/** A single directory scan covers all archives, including long downtime and shortened retention. */
internal class EventArchiveRetention(
    private val directory: Path,
    private val retentionDays: Int,
    maxArchiveSizeMiB: Int,
) {
    private val budget = maxArchiveSizeMiB.toLong() * 1024 * 1024

    fun clean() {
        val cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays.toLong())
        val archives = Files.list(directory).use { files ->
            files.iterator().asSequence().mapNotNull(::archive)
                .sortedWith(compareByDescending<Archive> { it.date }.thenByDescending { it.index }).toList()
        }
        var retainedBytes = 0L
        var overBudget = false
        for (archive in archives) {
            if (archive.date.isBefore(cutoff)) {
                delete(archive.path)
            } else {
                overBudget = overBudget || archive.bytes > budget - retainedBytes
                if (overBudget) delete(archive.path) else retainedBytes += archive.bytes
            }
        }
    }

    private fun archive(path: Path): Archive? {
        val match = ARCHIVE_NAME.matchEntire(path.fileName.toString()) ?: return null
        val date = try {
            LocalDate.parse(match.groupValues[1])
        } catch (_: DateTimeException) {
            return null
        }
        val index = match.groupValues[2].toIntOrNull() ?: return null
        val attributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return null // A concurrent rollover or shutdown cleanup can remove an archive.
        }
        require(attributes.isRegularFile) { "Event archives must be regular files" }
        return Archive(path, date, index, attributes.size())
    }

    private fun delete(path: Path) {
        try {
            Files.delete(path)
        } catch (_: NoSuchFileException) {
            // Another cleanup can finish deleting the same archive during a writer replacement.
        }
    }

    private data class Archive(val path: Path, val date: LocalDate, val index: Int, val bytes: Long)

    companion object {
        private val ARCHIVE_NAME = Regex("events\\.(\\d{4}-\\d{2}-\\d{2})\\.(\\d+)\\.jsonl")
    }
}
