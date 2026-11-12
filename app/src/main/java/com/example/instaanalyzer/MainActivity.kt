package com.example.instaanalyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

private val Ink = Color(0xFF21162C)
private val Pink = Color(0xFFC13C86)
private val Canvas = Color(0xFFF7F4F8)

class MainActivity : ComponentActivity() {
    private var importedData by mutableStateOf(InstagramData())
    private var pendingCsv by mutableStateOf("")
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            importedData = runCatching { InstagramExportParser.parse(this, it) }
                .getOrElse { e -> InstagramData(error = e.message ?: "فایل قابل خواندن نیست") }
        }
    }
    private val csvPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF$pendingCsv") }
                ?: error("فایل خروجی ساخته نشد")
        }.onFailure { importedData = importedData.copy(error = "خطا در ذخیره خروجی: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Pink, background = Canvas, surface = Color.White)) {
                AnalyzerHome(
                    data = importedData,
                    onImport = { picker.launch(arrayOf("application/zip", "application/json", "text/*")) },
                    onExportCsv = { csv -> pendingCsv = csv; csvPicker.launch("insta-content-report.csv") }
                )
            }
        }
    }
}

data class InstagramData(
    val followers: Set<String> = emptySet(),
    val following: Set<String> = emptySet(),
    val error: String? = null,
    val newFollowers: Set<String> = emptySet(),
    val lostFollowers: Set<String> = emptySet(),
    val newFollowing: Set<String> = emptySet(),
    val removedFollowing: Set<String> = emptySet(),
    val hasPreviousSnapshot: Boolean = false
) {
    val notFollowingBack: List<String> get() = following.minus(followers).sorted()
    val fansNotFollowed: List<String> get() = followers.minus(following).sorted()
    val mutual: Set<String> get() = followers.intersect(following)
}

@Composable
private fun AnalyzerHome(data: InstagramData, onImport: () -> Unit, onExportCsv: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val tabs = listOf("نمای کلی", "ارتباطات", "رشد پیج", "محتوا", "تحلیل")
    Column(Modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("اینستا تحلیل‌گر", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("داشبورد حرفه‌ای رشد و تحلیل حساب", color = Color(0xFFE7D9EA), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("فالوورها", data.followers.size.toString())
                Metric("فالووینگ", data.following.size.toString())
                Metric("بدون فالو‌بک", data.notFollowingBack.size.toString())
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, title ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(title, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Panel("شروع تحلیل", "فایل ZIP یا JSON خروجی رسمی اطلاعات حساب اینستاگرام را وارد کن.") {
                        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("وارد کردن خروجی اینستاگرام") }
                    }
                }
                item {
                    Panel("شاخص‌های ارتباطی", "${data.mutual.size} ارتباط دوطرفه · ${data.fansNotFollowed.size} نفر تو را دنبال می‌کنند اما تو دنبالشان نیستی · ${data.notFollowingBack.size} نفر از فالووینگ تو، تو را دنبال نمی‌کنند") {}
                }
                if (data.hasPreviousSnapshot) item { Panel("تغییرات از خروجی قبلی", "فالوورهای جدید: ${data.newFollowers.size} · فالوورهای ازدست‌رفته: ${data.lostFollowers.size} · فالووینگ‌های جدید: ${data.newFollowing.size} · حذف‌شده از فالووینگ: ${data.removedFollowing.size}") {} }
                item { Panel("حریم خصوصی", "تحلیل خروجی رسمی روی دستگاه انجام می‌شود. برنامه رمز عبور، کوکی یا نشست ورود اینستاگرام را دریافت نمی‌کند.") {} }
                item { Panel("رشد مبتنی بر داده", "آمار دوره‌ای را در بخش رشد پیج ثبت کن و عملکرد هر پست و ریلز را در استودیوی محتوا وارد کن تا پیشنهادها بر اساس داده واقعی ساخته شوند.") {} }
                if (data.error != null) item { Panel("پیام", data.error) {} }
            }
            1 -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("فالووینگ تو که فالو‌بک نداده‌اند", fontWeight = FontWeight.Bold, color = Ink) }
                if (data.notFollowingBack.isEmpty()) item { Text("هنوز داده‌ای برای نمایش وجود ندارد. ابتدا خروجی حساب را وارد کن.") }
                items(data.notFollowingBack) { username ->
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("@$username", fontWeight = FontWeight.SemiBold)
                            Text("در فهرست فالووینگ هست، در فالوورها نیست", fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/")))
                        }) { Text("باز کردن") }
                    }
                }
            }
            2 -> GrowthTrackerPanel()
            3 -> ContentStudioPanel(onExportCsv)
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Panel("تحلیل رفتار مخاطبان", "تحلیل به‌صورت تجمیعی و بر اساس داده‌های مجاز انجام می‌شود: موضوعات پربازده، نرخ ذخیره و اشتراک‌گذاری، تبدیل Reach به فالوور و روند رشد. از داده‌های یک فرد برای استنباط شخصیت پنهان یا ویژگی‌های حساس استفاده نمی‌شود.") {} }
                item { Panel("افزایش فالوور پایدار", "روی محتواهایی تمرکز کن که علاوه بر Reach، ذخیره، اشتراک‌گذاری و فالوور جدید ایجاد می‌کنند. از خرید فالوور، ربات، ارسال انبوه پیام و تعامل مصنوعی پرهیز کن.") {} }
                item { Panel("اتصال رسمی Instagram", "اتصال خودکار Insights فقط با مجوز رسمی Meta و حساب حرفه‌ای مجاز خواهد بود. این نسخه از رمز عبور یا کوکی استفاده نمی‌کند و فعلاً اطلاعات را دستی یا از خروجی رسمی می‌گیرد.") {} }
            }
        }
        Text("نسخه 2.1.1 · استودیوی رشد و تحلیل محلی", Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun RowScope.Metric(title: String, value: String) {
    Column(Modifier.weight(1f).background(Color(0xFF35243F), RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Color(0xFFE5D7E9), fontSize = 10.sp)
    }
}

@Composable
private fun Panel(title: String, body: String, extra: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFF655B6B), fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        extra()
    }
}

private object InstagramExportParser {
    private val followerFile = Regex("(^|/)followers(_\\d+)?\\.json$", RegexOption.IGNORE_CASE)
    private val followingFile = Regex("(^|/)following(_\\d+)?\\.json$", RegexOption.IGNORE_CASE)

    fun parse(context: Context, uri: Uri): InstagramData {
        val source = context.contentResolver.openInputStream(uri)
            ?: error("فایل انتخاب‌شده قابل خواندن نیست")

        BufferedInputStream(source).use { buffered ->
            buffered.mark(4)
            val signature = ByteArray(4)
            val count = buffered.read(signature)
            buffered.reset()
            if (count <= 0) error("فایل خالی است")

            val isZip = count >= 2 &&
                signature[0] == 0x50.toByte() &&
                signature[1] == 0x4B.toByte()

            return if (isZip) parseZip(context, buffered) else parseJson(context, buffered)
        }
    }

    /**
     * Reads only the two relationship JSON entries from the archive.
     * Neither the complete ZIP nor a complete JSON document is copied into memory.
     */
    private fun parseZip(context: Context, input: java.io.InputStream): InstagramData {
        val followers = linkedSetOf<String>()
        val following = linkedSetOf<String>()
        var foundFollowers = false
        var foundFollowing = false

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val normalized = entry.name.replace('\\', '/')
                    when {
                        followerFile.containsMatchIn(normalized) -> {
                            foundFollowers = true
                            readNamesFromCurrentZipEntry(zip, followers)
                        }
                        followingFile.containsMatchIn(normalized) -> {
                            foundFollowing = true
                            readNamesFromCurrentZipEntry(zip, following)
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        if (!foundFollowers && !foundFollowing) {
            error("در ZIP فایل‌های followers_1.json یا following.json پیدا نشد.")
        }
        if (!foundFollowers || !foundFollowing) {
            error("فایل ZIP ناقص است: فهرست followers یا following پیدا نشد.")
        }
        if (followers.isEmpty() && following.isEmpty()) {
            error("فایل‌های ارتباطی پیدا شدند، اما نام کاربری قابل استخراج نبود.")
        }
        return recordSnapshot(context, followers, following)
    }

    private fun readNamesFromCurrentZipEntry(
        zip: ZipInputStream,
        output: MutableSet<String>
    ) {
        // Do not close this JsonReader: it wraps the ZipInputStream, which must
        // remain open so the archive can advance to its next entry.
        val reader = JsonReader(InputStreamReader(zip, Charsets.UTF_8))
        reader.isLenient = true
        collectNames(reader, output)
    }

    private fun parseJson(context: Context, input: java.io.InputStream): InstagramData {
        val followers = linkedSetOf<String>()
        val following = linkedSetOf<String>()
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true
        collectNamedLists(reader, followers, following)

        if (followers.isEmpty() && following.isEmpty()) {
            error("این فایل JSON شامل فهرست followers یا following قابل‌شناسایی نیست.")
        }
        return recordSnapshot(context, followers, following)
    }

    /**
     * Traverses JSON tokens incrementally. Only username strings are retained.
     */
    private fun collectNames(reader: JsonReader, output: MutableSet<String>) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == "string_list_data" && reader.peek() == JsonToken.BEGIN_ARRAY) {
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
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == "value" && reader.peek() == JsonToken.STRING) {
                        reader.nextString().trim().removePrefix("@")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let(output::add)
                    } else {
                        collectNames(reader, output)
                    }
                }
                reader.endObject()
            } else {
                collectNames(reader, output)
            }
        }
        reader.endArray()
    }

    private fun collectNamedLists(
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
                        name.equals("followers", true) || name.matches(Regex("followers_\\d+", RegexOption.IGNORE_CASE)) ->
                            collectNames(reader, followers)
                        name.equals("following", true) || name.matches(Regex("following_\\d+", RegexOption.IGNORE_CASE)) ->
                            collectNames(reader, following)
                        else -> collectNamedLists(reader, followers, following)
                    }
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                // Preserve compatibility with a JSON array containing one relationship list.
                val temporary = linkedSetOf<String>()
                collectNames(reader, temporary)
                followers.addAll(temporary)
                following.addAll(temporary)
            }
            else -> reader.skipValue()
        }
    }

    private fun recordSnapshot(
        context: Context,
        followers: Set<String>,
        following: Set<String>
    ): InstagramData {
        val prefs = context.getSharedPreferences("insta_relationship_history", Context.MODE_PRIVATE)
        val oldFollowers = prefs.getStringSet("followers", emptySet())?.toSet() ?: emptySet()
        val oldFollowing = prefs.getStringSet("following", emptySet())?.toSet() ?: emptySet()
        val hasPrevious = prefs.contains("snapshot_at")
        val diff = if (hasPrevious) {
            RelationshipAnalytics.compare(oldFollowers, followers, oldFollowing, following)
        } else {
            RelationshipChange()
        }
        prefs.edit()
            .putStringSet("followers", followers)
            .putStringSet("following", following)
            .putLong("snapshot_at", System.currentTimeMillis())
            .apply()

        return InstagramData(
            followers = followers,
            following = following,
            newFollowers = diff.newFollowers,
            lostFollowers = diff.lostFollowers,
            newFollowing = diff.newFollowing,
            removedFollowing = diff.removedFollowing,
            hasPreviousSnapshot = hasPrevious
        )
    }
}
