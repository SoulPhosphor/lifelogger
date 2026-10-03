package com.datadragon.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateReleaseTest {

    @Test
    fun `newest eligible release includes prereleases`() {
        val result = findNewestUpdate(
            releasesJson = """
                [
                  {
                    "tag_name": "build-11",
                    "draft": false,
                    "prerelease": true,
                    "assets": [
                      {"name": "data-dragon-release-build-11.apk", "browser_download_url": "https://example.com/11.apk"}
                    ]
                  },
                  {
                    "tag_name": "build-10",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {"name": "data-dragon-release-build-10.apk", "browser_download_url": "https://example.com/10.apk"}
                    ]
                  }
                ]
            """.trimIndent(),
            currentBuild = 9,
        )

        assertEquals(11, result?.buildNumber)
        assertEquals("https://example.com/11.apk", result?.apkUrl)
    }

    @Test
    fun `drafts releases without apks and installed builds are ignored`() {
        val result = findNewestUpdate(
            releasesJson = """
                [
                  {"tag_name": "build-14", "draft": true, "assets": [{"name": "app.apk", "browser_download_url": "https://example.com/14.apk"}]},
                  {"tag_name": "build-13", "draft": false, "assets": [{"name": "notes.txt", "browser_download_url": "https://example.com/notes"}]},
                  {"tag_name": "build-12", "draft": false, "assets": [{"name": "app.apk", "browser_download_url": "https://example.com/12.apk"}]}
                ]
            """.trimIndent(),
            currentBuild = 12,
        )

        assertNull(result)
    }
}
