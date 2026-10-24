package dev.kviklet.kviklet.updatecheck

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionTest {

    private fun v(tag: String) = Version.parse(tag)!!

    @Test
    fun `tags with and without a v prefix parse`() {
        assertThat(Version.parse("0.9.2")).isEqualTo(Version(0, 9, 2, emptyList()))
        assertThat(Version.parse("v0.6.0-pre2")).isEqualTo(Version(0, 6, 0, listOf("pre2")))
        assertThat(Version.parse("1.2")).isEqualTo(Version(1, 2, 0, emptyList()))
        assertThat(Version.parse("1.2.3+build.5")).isEqualTo(Version(1, 2, 3, emptyList()))
    }

    @Test
    fun `non-version strings do not parse`() {
        assertThat(Version.parse("dev")).isNull()
        assertThat(Version.parse("main")).isNull()
        assertThat(Version.parse("")).isNull()
        assertThat(Version.parse("v1")).isNull()
    }

    @Test
    fun `numeric parts compare as numbers`() {
        assertThat(v("0.10.0")).isGreaterThan(v("0.9.2"))
        assertThat(v("1.0.0")).isGreaterThan(v("0.99.99"))
        assertThat(v("0.9.10")).isGreaterThan(v("0.9.9"))
        assertThat(v("v0.9.2")).isEqualByComparingTo(v("0.9.2"))
    }

    @Test
    fun `a release outranks its pre-releases`() {
        assertThat(v("0.6.0")).isGreaterThan(v("v0.6.0-pre2"))
        assertThat(v("v0.6.0-pre2")).isGreaterThan(v("v0.6.0-pre"))
        assertThat(v("1.0.0-rc.10")).isGreaterThan(v("1.0.0-rc.2"))
        assertThat(v("1.0.0-alpha.beta")).isGreaterThan(v("1.0.0-alpha.1"))
        assertThat(v("0.5.9")).isLessThan(v("0.6.0-pre"))
    }
}
