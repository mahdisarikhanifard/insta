package com.example.instaanalyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstagramExportParserTest {
    private fun relation(username: String): String =
        """{"title":"$username","string_list_data":[{"href":"https://www.instagram.com/$username/","value":"$username","timestamp":1700000000}]}"""

    private fun relationHrefOnly(username: String): String =
        """{"title":"$username","string_list_data":[{"href":"https://www.instagram.com/$username/","timestamp":1700000000}]}"""

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            entries.forEach { (name, content) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(content.toByteArray(Charsets.UTF_8))
                archive.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun parsesMultipartFollowersAndFollowingWithoutMixingLists() {
        val bytes = zip(
            "connections/followers_and_following/followers_1.json" to "[${relation("Alice")}]",
            "connections/followers_and_following/followers_2.json" to "[${relation("bob")}]",
            "connections/followers_and_following/following.json" to """{"relationships_following":[${relationHrefOnly("Bob")},${relationHrefOnly("carol")}]}"""
        )
        val parsed = InstagramExportParser.parse(ByteArrayInputStream(bytes), "instagram-export.zip")
        assertEquals(setOf("alice", "bob"), parsed.followers)
        assertEquals(setOf("bob", "carol"), parsed.following)
        assertEquals(2, parsed.followerFiles)
        assertEquals(1, parsed.followingFiles)
    }

    @Test fun parsesStandaloneFollowersJsonUsingItsFilename() {
        val json = "[${relation("One")},${relation("Two")}]"
        val parsed = InstagramExportParser.parse(ByteArrayInputStream(json.toByteArray()), "followers_1.json")
        assertEquals(setOf("one", "two"), parsed.followers)
        assertTrue(parsed.following.isEmpty())
    }

    @Test fun parsesStandaloneFollowingJsonUsingInstagramWrapperKey() {
        val json = """{"relationships_following":[${relationHrefOnly("Friend")}]}"""
        val parsed = InstagramExportParser.parse(ByteArrayInputStream(json.toByteArray()), "following.json")
        assertTrue(parsed.followers.isEmpty())
        assertEquals(setOf("friend"), parsed.following)
    }

    @Test fun rejectsAmbiguousUnnamedJsonInsteadOfCopyingItIntoBothLists() {
        val json = "[${relation("Someone")}]"
        val error = runCatching {
            InstagramExportParser.parse(ByteArrayInputStream(json.toByteArray()), "data.json")
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("قابل تشخیص نیست") == true)
    }

    @Test fun parsesACombinedJsonWithExplicitRelationshipKeys() {
        val json = """{"relationships_followers":[${relation("Fan")}],"relationships_following":[${relationHrefOnly("Followed")}]}"""
        val parsed = InstagramExportParser.parse(ByteArrayInputStream(json.toByteArray()), "relationships.json")
        assertEquals(setOf("fan"), parsed.followers)
        assertEquals(setOf("followed"), parsed.following)
    }

    @Test fun permitsAValidEmptyFollowerListWhenFollowingExists() {
        val bytes = zip(
            "followers_1.json" to "[]",
            "following.json" to """{"relationships_following":[${relationHrefOnly("onlyfollowing")}]}"""
        )
        val parsed = InstagramExportParser.parse(ByteArrayInputStream(bytes), "export.zip")
        assertTrue(parsed.followers.isEmpty())
        assertEquals(setOf("onlyfollowing"), parsed.following)
    }
}
