// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.encoder.EncoderBase
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.RolloverFailure
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.rolling.helper.RenameUtil
import ch.qos.logback.core.status.Status
import ch.qos.logback.core.util.FileSize
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** A private Logback context: event payloads never propagate to the application's console logger. */
class EventFileWriter(private val settings: EventStreamingSettings, activateImmediately: Boolean = true) :
    AutoCloseable {
    private val context = LoggerContext()
    private val appender = object : RollingFileAppender<String>() {
        override fun openFile(fileName: String) {
            val path = Path.of(fileName)
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(
                    path,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----")),
                )
            }
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Event output must be a regular file" }
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"))
            super.openFile(fileName)
        }
    }
    private val policy = object : SizeAndTimeBasedRollingPolicy<String>() {
        override fun rollover() {
            try {
                super.rollover()
                cleanupRequired.set(true)
            } catch (e: RolloverFailure) {
                // RollingFileAppender otherwise swallows this as a WARN and keeps growing the active file.
                failed.set(true)
                throw e
            }
        }
    }
    private var cleanup: Future<*>? = null
    private var cleanedAt = Instant.EPOCH
    private val failed = AtomicBoolean(false)
    private val cleanupRequired = AtomicBoolean(true)
    private val lockChannel: FileChannel
    private val lock: FileLock
    private val directory = Path.of(settings.directory).toAbsolutePath().normalize()
    private val retention = EventArchiveRetention(directory, settings.retentionDays, settings.maxArchiveSizeMiB)

    init {
        require(settings.maxArchiveSizeMiB > settings.maxFileSizeMiB) {
            "Archive budget must be at least 1 MiB larger than the file-size threshold"
        }
        prepareDirectory(directory)
        lockChannel = FileChannel.open(
            directory.resolve("events.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        )
        try {
            lock = lockChannel.tryLock() ?: throw IllegalArgumentException("The event directory already has a writer")
        } catch (e: Exception) {
            lockChannel.close()
            throw e
        }
        try {
            // Opening without truncation validates access while reserving an uncommitted directory.
            FileChannel.open(
                directory.resolve("events.jsonl"),
                setOf(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                ),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----")),
            ).use { }
            if (activateImmediately) activate()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /** Activate only after the settings commit; reservations must not repair tails or delete archives. */
    @Synchronized
    fun activate() {
        if (appender.isStarted) return
        check(lock.isValid) { "Event writer reservation is closed" }
        try {
            discardIncompleteTail()
            context.name = "kviklet-event-stream"
            context.statusManager.add { status ->
                if (status.level == Status.ERROR ||
                    (status.level == Status.WARN && status.origin is RenameUtil)
                ) {
                    failed.set(true)
                }
            }
            context.start()
            appender.context = context
            appender.name = "EVENT_STREAM"
            appender.file = directory.resolve("events.jsonl").toString()
            appender.isAppend = true
            appender.isImmediateFlush = true
            val encoder = object : EncoderBase<String>() {
                override fun headerBytes(): ByteArray? = null
                override fun footerBytes(): ByteArray? = null
                override fun encode(event: String): ByteArray = (event + "\n").toByteArray(Charsets.UTF_8)
            }
            encoder.context = context
            encoder.start()
            appender.encoder = encoder

            policy.context = context
            policy.setParent(appender)
            policy.fileNamePattern = directory.resolve("events.%d{yyyy-MM-dd,UTC}.%i.jsonl").toString()
            policy.setMaxFileSize(FileSize.valueOf("${settings.maxFileSizeMiB}MB"))
            // Own the complete retention scan; Logback's remover skips archives beyond its startup window.
            policy.maxHistory = 0
            policy.start()
            appender.rollingPolicy = policy
            appender.start()
            checkHealthy()
            secureFiles()
            maintain()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    @Synchronized
    fun write(json: String) {
        checkHealthy()
        require(!Files.isSymbolicLink(directory.resolve("events.jsonl"))) { "Event output must not be a symbolic link" }
        appender.doAppend(json)
        checkHealthy()
        maintain()
    }

    /** Keep archive retention working even when there are no new events to trigger rotation. */
    @Synchronized
    fun maintain() {
        checkHealthy()
        if ((cleanupRequired.get() || Instant.now().isAfter(cleanedAt.plusSeconds(60))) && cleanup?.isDone != false) {
            cleanupRequired.set(false)
            cleanedAt = Instant.now()
            cleanup = context.alternateExecutorService.submit {
                try {
                    retention.clean()
                } catch (e: Exception) {
                    failed.set(true)
                }
            }
        }
    }

    /** A crash can leave an unterminated final line; never concatenate a new event onto it. */
    private fun discardIncompleteTail() {
        val active = directory.resolve("events.jsonl")
        if (!Files.exists(active, LinkOption.NOFOLLOW_LINKS)) return
        FileChannel.open(
            active,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { file ->
            var position = file.size()
            val buffer = ByteBuffer.allocate(8192)
            while (position > 0) {
                val start = (position - buffer.capacity()).coerceAtLeast(0)
                buffer.clear().limit((position - start).toInt())
                file.position(start)
                while (buffer.hasRemaining() && file.read(buffer) >= 0) { }
                for (index in buffer.position() - 1 downTo 0) {
                    if (buffer[index] == '\n'.code.toByte()) {
                        file.truncate(start + index + 1)
                        return
                    }
                }
                position = start
            }
            file.truncate(0)
        }
    }

    private fun checkHealthy() {
        check(appender.isStarted && !failed.get()) { "Event file output is unavailable" }
    }

    private fun secureFiles() {
        Files.list(directory).use { files ->
            files.filter { managed(it) }.forEach {
                try {
                    Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rw-r-----"))
                } catch (_: NoSuchFileException) {
                    // Retention cleanup may remove an archive while startup is securing files.
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        appender.stop()
        context.stop()
        if (lock.isValid) lock.release()
        lockChannel.close()
    }

    companion object {
        const val MAX_RECORD_BYTES = 1024 * 1024

        private fun managed(path: Path): Boolean = path.fileName.toString().let {
            it == "events.jsonl" || it == "events.lock" ||
                Regex("events\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+\\.jsonl").matches(it)
        }

        private fun rejectSymlinks(directory: Path) {
            Files.list(directory).use { files ->
                require(files.noneMatch { managed(it) && !Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }) {
                    "Event output files must be regular files, not symbolic links"
                }
            }
        }

        fun prepareDirectory(directory: Path) {
            require(directory.isAbsolute) { "Event output directory must be absolute" }
            require(!Files.isSymbolicLink(directory)) { "Event output directory must not be a symbolic link" }
            Files.createDirectories(
                directory,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
            require(Files.isDirectory(directory) && Files.isWritable(directory)) {
                "Event output directory must be writable"
            }
            val permissions = Files.getPosixFilePermissions(directory)
            require(
                !permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE) &&
                    !permissions.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE),
            ) {
                "Event output directory must only be writable by its owner"
            }
            rejectSymlinks(directory)
        }
    }
}
