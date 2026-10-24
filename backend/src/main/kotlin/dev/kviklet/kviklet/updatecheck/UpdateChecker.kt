package dev.kviklet.kviklet.updatecheck

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import dev.kviklet.kviklet.ApplicationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Asks GitHub whether a newer Kviklet is out: newer releases for a tagged build, the commits on main
 * for an image built from main. The answer is kept in memory and served with the public config, so
 * every page load reads it for free.
 *
 * Requests are unauthenticated and every failure (rate limit, unknown commit, no network in an
 * air-gapped setup) is logged at debug level and otherwise ignored; the last good answer stays.
 */
@Component
@Lazy(false)
class UpdateChecker(
    private val applicationProperties: ApplicationProperties,
    private val jsonMapper: JsonMapper,
    private val restTemplate: RestTemplate,
    private val clock: Clock = Clock.systemUTC(),
    private val apiUrl: String = API_URL,
) {

    // Not the shared RestTemplate bean: a slow or unreachable GitHub must not hold the scheduler thread.
    @Autowired
    constructor(applicationProperties: ApplicationProperties, jsonMapper: JsonMapper) : this(
        applicationProperties,
        jsonMapper,
        RestTemplate(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(10))
            },
        ),
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val latest = AtomicReference<UpdateInfo?>(null)

    fun current(): UpdateInfo? = latest.get()

    // Runs once right after startup (on the scheduler thread, so it never delays boot), then every 2h.
    @Scheduled(fixedDelayString = "PT2H")
    fun check() {
        try {
            latest.set(fetch())
        } catch (e: Exception) {
            logger.debug("Update check against GitHub failed: {}", e.toString())
        }
    }

    private fun fetch(): UpdateInfo? {
        Version.parse(applicationProperties.version)?.let { return checkReleases(it) }
        // Images built from main carry the version "dev" and the short commit SHA. Anything else
        // (a local build, a fork with its own tags) has nothing to compare against.
        val commit = applicationProperties.gitCommit
        if (applicationProperties.version == "dev" && COMMIT_SHA.matches(commit)) {
            return checkMain(commit)
        }
        return null
    }

    private fun checkReleases(current: Version): UpdateInfo? {
        val cutoff = cutoff()
        val newer = get<List<GitHubRelease>>("/releases?per_page=20")
            .filter { !it.draft && !it.prerelease && it.publishedAt != null && it.publishedAt.isBefore(cutoff) }
            .mapNotNull { release -> Version.parse(release.tagName)?.let { it to release } }
            .filter { (version, _) -> version > current }
            .sortedByDescending { (version, _) -> version }
            .map { (_, release) -> release }
        val newest = newer.firstOrNull() ?: return null
        return UpdateInfo(
            channel = UpdateChannel.RELEASE,
            latestVersion = newest.tagName,
            releases = newer.map {
                ReleaseNote(
                    tag = it.tagName,
                    name = it.name?.takeIf { name -> name.isNotBlank() },
                    body = it.body.orEmpty(),
                    publishedAt = it.publishedAt!!,
                    url = it.htmlUrl,
                )
            },
            url = newest.htmlUrl,
        )
    }

    private fun checkMain(commit: String): UpdateInfo? {
        val comparison = get<GitHubComparison>("/compare/$commit...$BRANCH")
        // Commits younger than the cutoff may not have an image yet, so they don't count.
        val fresh = comparison.commits.count { !it.commit.committer.date.isBefore(cutoff()) }
        val behindBy = comparison.aheadBy - fresh
        if (behindBy <= 0) return null
        return UpdateInfo(
            channel = UpdateChannel.MAIN,
            latestVersion = null,
            behindBy = behindBy,
            commits = comparison.commits
                .dropLast(fresh)
                .takeLast(MAX_COMMITS)
                .reversed()
                .map {
                    CommitNote(
                        sha = it.sha,
                        message = it.commit.message.lineSequence().first(),
                        committedAt = it.commit.committer.date,
                        url = it.htmlUrl,
                    )
                },
            url = comparison.htmlUrl,
        )
    }

    private fun cutoff(): Instant = clock.instant().minus(CI_LAG)

    private inline fun <reified T> get(path: String): T {
        val body = restTemplate.getForObject(URI.create("$apiUrl/repos/$REPOSITORY$path"), String::class.java)
            ?: error("Empty response from GitHub for $path")
        return jsonMapper.readValue(body)
    }

    companion object {
        const val API_URL = "https://api.github.com"
        const val REPOSITORY = "kviklet/kviklet"
        const val BRANCH = "main"
        const val MAX_COMMITS = 20

        /** How long CI takes to publish an image after a release or a push to main. */
        val CI_LAG: Duration = Duration.ofMinutes(30)

        private val COMMIT_SHA = Regex("^[0-9a-f]{7,40}$")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubRelease(
    @JsonProperty("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    @JsonProperty("published_at") val publishedAt: Instant?,
    @JsonProperty("html_url") val htmlUrl: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubComparison(
    // Comparing this build's commit (base) to main (head): how far main is ahead is how far this build is behind.
    @JsonProperty("ahead_by") val aheadBy: Int,
    @JsonProperty("html_url") val htmlUrl: String,
    /** Oldest first, at most 250. */
    val commits: List<GitHubCommit>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubCommit(
    val sha: String,
    @JsonProperty("html_url") val htmlUrl: String,
    val commit: GitHubCommitDetails,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubCommitDetails(val message: String, val committer: GitHubCommitter)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GitHubCommitter(val date: Instant)
