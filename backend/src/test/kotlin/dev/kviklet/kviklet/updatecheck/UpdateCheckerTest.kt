package dev.kviklet.kviklet.updatecheck

import dev.kviklet.kviklet.ApplicationProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateCheckerTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()

    private fun checker(version: String, gitCommit: String = "abc1234") = UpdateChecker(
        ApplicationProperties().apply {
            this.version = version
            this.gitCommit = gitCommit
        },
        JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(),
        restTemplate,
        Clock.fixed(now, ZoneOffset.UTC),
        apiUrl = "https://github.test",
    )

    private fun release(
        tag: String,
        publishedAt: String = "2026-09-01T00:00:00Z",
        draft: Boolean = false,
        prerelease: Boolean = false,
        body: String = "Notes for $tag",
    ) = """
        {
          "tag_name": "$tag",
          "name": "$tag",
          "body": "$body",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "$publishedAt",
          "html_url": "https://github.com/kviklet/kviklet/releases/tag/$tag",
          "author": {"login": "someone"}
        }
    """.trimIndent()

    private fun commit(sha: String, message: String, date: String) = """
        {
          "sha": "$sha",
          "html_url": "https://github.com/kviklet/kviklet/commit/$sha",
          "commit": {"message": "$message", "committer": {"name": "x", "date": "$date"}}
        }
    """.trimIndent()

    private fun expectReleases(vararg releases: String) {
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"))
            .andRespond(withSuccess(releases.joinToString(",", "[", "]"), MediaType.APPLICATION_JSON))
    }

    private fun expectComparison(behindBy: Int, vararg commits: String) {
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/compare/abc1234...main"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "ahead",
                      "ahead_by": $behindBy,
                      "behind_by": 0,
                      "html_url": "https://github.com/kviklet/kviklet/compare/abc1234...main",
                      "commits": ${commits.joinToString(",", "[", "]")}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    @Test
    fun `an older release build gets every newer release, newest first`() {
        expectReleases(
            release("0.9.2"),
            release("0.9.1"),
            release("0.9.0"),
            release("0.8.0"),
            release("v0.6.0-pre2"),
        )
        val checker = checker("0.9.0")

        checker.check()

        val update = checker.current()!!
        assertThat(update.channel).isEqualTo(UpdateChannel.RELEASE)
        assertThat(update.latestVersion).isEqualTo("0.9.2")
        assertThat(update.releases.map { it.tag }).containsExactly("0.9.2", "0.9.1")
        assertThat(update.releases.first().body).isEqualTo("Notes for 0.9.2")
        assertThat(update.url).isEqualTo("https://github.com/kviklet/kviklet/releases/tag/0.9.2")
        server.verify()
    }

    @Test
    fun `drafts, pre-releases and releases still being built are skipped`() {
        expectReleases(
            release("1.0.0", draft = true),
            release("0.10.0-rc1", prerelease = true),
            release("0.9.3", publishedAt = "2026-10-21T11:45:00Z"),
            release("0.9.2"),
        )
        val checker = checker("0.9.1")

        checker.check()

        assertThat(checker.current()!!.releases.map { it.tag }).containsExactly("0.9.2")
    }

    @Test
    fun `the latest release build shows nothing`() {
        expectReleases(release("0.9.2"), release("0.9.1"))
        val checker = checker("0.9.2")

        checker.check()

        assertThat(checker.current()).isNull()
    }

    @Test
    fun `a main build behind main gets the commit count and the newest messages first`() {
        expectComparison(
            2,
            commit("1111111aaa", "Fix the login page (#600)\\n\\nLonger description", "2026-10-20T10:00:00Z"),
            commit("2222222bbb", "Show the license in force (#601)", "2026-10-20T11:00:00Z"),
        )
        val checker = checker("dev")

        checker.check()

        val update = checker.current()!!
        assertThat(update.channel).isEqualTo(UpdateChannel.MAIN)
        assertThat(update.behindBy).isEqualTo(2)
        assertThat(update.commits.map { it.message })
            .containsExactly("Show the license in force (#601)", "Fix the login page (#600)")
        assertThat(update.url).isEqualTo("https://github.com/kviklet/kviklet/compare/abc1234...main")
    }

    @Test
    fun `commits pushed within the CI lag do not count yet`() {
        expectComparison(
            2,
            commit("1111111aaa", "Older", "2026-10-21T11:00:00Z"),
            commit("2222222bbb", "Just merged", "2026-10-21T11:50:00Z"),
        )
        val checker = checker("dev")

        checker.check()

        assertThat(checker.current()!!.behindBy).isEqualTo(1)
        assertThat(checker.current()!!.commits.map { it.message }).containsExactly("Older")
    }

    @Test
    fun `a main build only behind by commits within the CI lag shows nothing`() {
        expectComparison(1, commit("2222222bbb", "Just merged", "2026-10-21T11:50:00Z"))
        val checker = checker("dev")

        checker.check()

        assertThat(checker.current()).isNull()
    }

    @Test
    fun `an up-to-date main build shows nothing`() {
        expectComparison(0)
        val checker = checker("dev")

        checker.check()

        assertThat(checker.current()).isNull()
    }

    @Test
    fun `local builds without a known commit never call GitHub`() {
        val checker = checker("dev", gitCommit = "unknown")

        checker.check()

        assertThat(checker.current()).isNull()
        server.verify()
    }

    @Test
    fun `rate limits, unknown commits and network errors are swallowed and keep the last answer`() {
        server.expect(
            ExpectedCount.times(1),
            requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"),
        )
            .andRespond(withSuccess("[${release("0.9.2")}]", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("""{"message": "API rate limit exceeded"}"""))
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"))
            .andRespond(withException(IOException("Network is unreachable")))
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/releases?per_page=20"))
            .andRespond(withSuccess("<html>captive portal</html>", MediaType.TEXT_HTML))
        val checker = checker("0.9.1")

        repeat(5) { checker.check() }

        assertThat(checker.current()!!.latestVersion).isEqualTo("0.9.2")
        server.verify()
    }

    @Test
    fun `a commit GitHub does not know is swallowed`() {
        server.expect(requestTo("https://github.test/repos/kviklet/kviklet/compare/abc1234...main"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        val checker = checker("dev")

        checker.check()

        assertThat(checker.current()).isNull()
    }
}
