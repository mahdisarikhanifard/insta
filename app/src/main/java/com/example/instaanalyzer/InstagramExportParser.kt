package com.example.instaanalyzer

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Streaming parser for the official Instagram followers/following export.
 * It never loads the ZIP or a complete JSON document into memory.
 */
data class ParsedRelationships(
    val followers: Set<String>,
    val following: Set<String>,
    val followerFiles: Int,
    val followingFiles: Int
)

object InstagramExportParser {
    private val followerFile = Regex("^followers(?:_\\d+)?\\.json$", RegexOption.IGNORE_CASE)
    private val followingFile = Regex("^following(?:_\\d+)?\\.json$", RegexOption.IGNORE_CASE)
    private val usernamePattern = Regex("[A-Za-z0-9._]{1,30}")

    fun parse(input: InputStream, displayName: String): ParsedRelationships {
        BufferedInputStream(input).use { buffered ->
            buffered.mark(4)
            val signature = ByteArray(4)
            val count = buffered.read(signature)
            buffered.reset()
            require(count > 0) { "فایل انتخاب‌شده خالی است." }
            val isZip = count >= 2 &&
                signature[0] == 0x50.toByte() &&
                signature[1] == 0x4B.toByte()
            return if (isZip) parseZip(buffered) else parseSingleJson(buffered, displayName)
        }
    }

    private fun parseZip(input: InputStream): ParsedRelationships {
        val followers = linkedSetOf<String>()
        val following = linkedSetOf<String>()
        var followerFiles = 0
        var followingFiles = 0
        var jsonFiles = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    jsonFiles++
                    val basename = entry.name.replace('\\', '/').substringAfterLast('/')
                    when {
                        followerFile.matches(basename) -> {
                            followerFiles++
                            collectNames(JsonReader(InputStreamReader(zip, Charsets.UTF_8)), followers)
                        }
                        followingFile.matches(basename) -> {
                            followingFiles++
                            collectNames(JsonReader(InputStreamReader(zip, Charsets.UTF_8)), following)
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        require(followerFiles > 0 || followingFiles > 0) {
            if (jsonFiles == 0) "در ZIP هیچ فایل JSON پیدا نشد."
            else "فایل‌های followers_1.json و following.json در ZIP پیدا نشدند. هنگام دریافت خروجی، بخش Followers and Following و قالب JSON را انتخاب کن."
        }
        require(followerFiles > 0) { "فهرست فالوورها در ZIP پیدا نشد؛ فایل followers_1.json وجود ندارد." }
        require(followingFiles > 0) { "فهرست فالووینگ در ZIP پیدا نشد؛ فایل following.json وجود ندارد." }

        return ParsedRelationships(
            followers = followers,
            following = following,
            followerFiles = followerFiles,
            followingFiles = followingFiles
        )
    }

    private fun parseSingleJson(input: InputStream, displayName: String): ParsedRelationships {
        val basename = displayName.replace('\\', '/').substringAfterLast('/').substringBefore('?')
        val followers = linkedSetOf<String>()
        val following = linkedSetOf<String>()
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8)).apply { isLenient = false }

        when {
            followerFile.matches(basename) -> collectNames(reader, followers)
            followingFile.matches(basename) -> collectNames(reader, following)
            else -> collectNamedRelationships(reader, followers, following)
        }

        require(followers.isNotEmpty() || following.isNotEmpty()) {
            "این JSON قابل تشخیص نیست. برای ورود مطمئن، ZIP اصلی خروجی اینستاگرام را انتخاب کن؛ یا فایل followers_1.json یا following.json را جداگانه انتخاب کن."
        }
        return ParsedRelationships(
            followers = followers,
            following = following,
            followerFiles = if (followers.isNotEmpty()) 1 else 0,
            followingFiles = if (following.isNotEmpty()) 1 else 0
        )
    }

    private fun collectNames(reader: JsonReader, output: MutableSet<String>) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name.equals("string_list_data", ignoreCase = true) &&
                        reader.peek() == JsonToken.BEGIN_ARRAY
                    ) {
                        readStringListData(reader, output)
                    } else {
                        collectNames(reader, output)
                    }
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) collectNames(reader, output)
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }

    private fun readStringListData(reader: JsonReader, output: MutableSet<String>) {
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                var exportedValue: String? = null
                var profileHref: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    when {
                        key.equals("value", ignoreCase = true) && reader.peek() == JsonToken.STRING ->
                            exportedValue = reader.nextString()
                        key.equals("href", ignoreCase = true) && reader.peek() == JsonToken.STRING ->
                            profileHref = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val rawUsername = exportedValue ?: usernameFromHref(profileHref)
                val username = rawUsername?.let(::normalizeUsername)
                if (username != null) output.add(username)
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
    }

    private fun collectNamedRelationships(
        reader: JsonReader,
        followers: MutableSet<String>,
        following: MutableSet<String>
    ) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when {
                        name.equals("relationships_followers", true) ||
                            name.equals("followers", true) ||
                            name.matches(Regex("followers_\\d+", RegexOption.IGNORE_CASE)) ->
                            collectNames(reader, followers)
                        name.equals("relationships_following", true) ||
                            name.equals("following", true) ||
                            name.matches(Regex("following_\\d+", RegexOption.IGNORE_CASE)) ->
                            collectNames(reader, following)
                        else -> collectNamedRelationships(reader, followers, following)
                    }
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                // An unnamed array has no reliable follower/following identity.
                reader.beginArray()
                while (reader.hasNext()) reader.skipValue()
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }

    private fun usernameFromHref(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val match = Regex(
            "^https?://(?:www\\.)?instagram\\.com/([A-Za-z0-9._]{1,30})/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE
        ).matchEntire(href.trim()) ?: return null
        return match.groupValues[1]
    }

    private fun normalizeUsername(raw: String): String? {
        val value = raw.trim().removePrefix("@")
        if (!usernamePattern.matches(value)) return null
        // Instagram handles are case-insensitive; canonicalization avoids false diffs.
        return value.lowercase(Locale.ROOT)
    }
}
