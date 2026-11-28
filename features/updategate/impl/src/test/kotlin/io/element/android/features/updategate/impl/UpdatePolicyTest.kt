/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.updategate.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.leveragers.agents"

class UpdatePolicyTest {
    private fun updateFile(
        v: String = "1",
        minBuild: String = "261100130",
        recommendedBuild: String = "261100150",
        updateUrl: String = "\"$PLAY_URL\"",
    ) = """
        {"v":$v,
         "ios":{"min_build":5,"recommended_build":6,"update_url":"https://testflight.apple.com/join/zMWdSWBu"},
         "android":{"min_build":$minBuild,"recommended_build":$recommendedBuild,"update_url":$updateUrl}}
    """.trimIndent()

    @Test
    fun `a valid file gives its Android part`() {
        assertThat(UpdatePolicy.fromUpdateFile(updateFile())).isEqualTo(
            UpdatePolicy(minBuild = 261100130, recommendedBuild = 261100150, updateUrl = PLAY_URL)
        )
    }

    @Test
    fun `thresholds at zero are valid`() {
        assertThat(UpdatePolicy.fromUpdateFile(updateFile(minBuild = "0", recommendedBuild = "0"))).isEqualTo(
            UpdatePolicy(minBuild = 0, recommendedBuild = 0, updateUrl = PLAY_URL)
        )
    }

    @Test
    fun `a file of another version is ignored`() {
        listOf("2", "0", "\"1\"", "1.0", "null").forEach { v ->
            assertThat(UpdatePolicy.fromUpdateFile(updateFile(v = v))).isNull()
        }
        assertThat(UpdatePolicy.fromUpdateFile("""{"android":{"min_build":0,"recommended_build":0,"update_url":"$PLAY_URL"}}""")).isNull()
    }

    @Test
    fun `thresholds that are not non-negative integers are ignored`() {
        listOf("-1", "\"261100130\"", "261100130.5", "1e9", "null", "true", "{}").forEach { value ->
            assertThat(UpdatePolicy.fromUpdateFile(updateFile(minBuild = value))).isNull()
            assertThat(UpdatePolicy.fromUpdateFile(updateFile(recommendedBuild = value))).isNull()
        }
        assertThat(UpdatePolicy.fromUpdateFile("""{"v":1,"android":{"recommended_build":0,"update_url":"$PLAY_URL"}}""")).isNull()
        assertThat(UpdatePolicy.fromUpdateFile("""{"v":1,"android":{"min_build":0,"update_url":"$PLAY_URL"}}""")).isNull()
    }

    @Test
    fun `an address that is not https is ignored`() {
        listOf(
            "\"http://play.google.com/store/apps/details?id=com.leveragers.agents\"",
            "\"market://details?id=com.leveragers.agents\"",
            "\"play.google.com/store/apps/details?id=com.leveragers.agents\"",
            "\"https://\"",
            "\"HTTPS://play.google.com/\"",
            "\"\"",
            "42",
            "null",
        ).forEach { url ->
            assertThat(UpdatePolicy.fromUpdateFile(updateFile(updateUrl = url))).isNull()
        }
        assertThat(UpdatePolicy.fromUpdateFile("""{"v":1,"android":{"min_build":0,"recommended_build":0}}""")).isNull()
    }

    @Test
    fun `a file without an Android part is ignored`() {
        assertThat(
            UpdatePolicy.fromUpdateFile("""{"v":1,"ios":{"min_build":0,"recommended_build":0,"update_url":"$PLAY_URL"}}""")
        ).isNull()
        assertThat(UpdatePolicy.fromUpdateFile("""{"v":1,"android":[]}""")).isNull()
    }

    @Test
    fun `a file that is not a JSON object is ignored`() {
        listOf("", "<html><body>Not found</body></html>", "[]", "1", "{\"v\":1,").forEach { body ->
            assertThat(UpdatePolicy.fromUpdateFile(body)).isNull()
        }
    }
}
