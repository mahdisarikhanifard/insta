package com.example.instaanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private val Ink = Color(0xFF21162C)
private val Pink = Color(0xFFC13C86)
private val Canvas = Color(0xFFF7F4F8)

class MainActivity : ComponentActivity() {
    private var importedData by mutableStateOf(InstagramData())
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            importedData = runCatching { InstagramExportParser.parse(contentResolver, it) }
                .getOrElse { e -> InstagramData(error = e.message ?: "فایل قابل خواندن نیست") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Pink, background = Canvas, surface = Color.White)) {
                AnalyzerHome(
                    data = importedData,
                    onImport = { picker.launch(arrayOf("application/zip", "application/json", "text/*")) }
                )
            }
        }
    }
}

data class InstagramData(
    val followers: Set<String> = emptySet(),
    val following: Set<String> = emptySet(),
    val error: String? = null
) {
    val notFollowingBack: List<String> get() = following.minus(followers).sorted()
    val fansNotFollowed: List<String> get() = followers.minus(following).sorted()
    val mutual: Set<String> get() = followers.intersect(following)
}

@Composable
private fun AnalyzerHome(data: InstagramData, onImport: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text("اینستا تحلیل‌گر", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("مرکز تحلیل ارتباطات حساب", color = Color(0xFFE7D9EA), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("فالوورها", data.followers.size.toString())
                Metric("فالووینگ", data.following.size.toString())
                Metric("فالو‌بک نداده‌اند", data.notFollowingBack.size.toString())
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("نمای کلی", "ارتباطات", "تحلیل").forEachIndexed { i, title ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(title) })
            }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Panel("شروع تحلیل", "فایل ZIP یا JSON خروجی رسمی اطلاعات حساب اینستاگرام را وارد کن.") {
                        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Text("وارد کردن خروجی اینستاگرام")
                        }
                    }
                }
                item {
                    Panel(
                        "شاخص‌های ارتباطی",
                        "${data.mutual.size} ارتباط دوطرفه · ${data.fansNotFollowed.size} نفر تو را دنبال می‌کنند اما تو دنبالشان نیستی · ${data.notFollowingBack.size} نفر از فالووینگ تو، تو را دنبال نمی‌کنند"
                    ) {}
                }
                item {
                    Panel(
                        "حریم خصوصی",
                        "تحلیل روی دستگاه انجام می‌شود. برنامه رمز عبور یا نشست ورود اینستاگرام را دریافت یا ذخیره نمی‌کند."
                    ) {}
                }
                if (data.error != null) item { Panel("خطا در واردسازی", data.error) {} }
            }
            1 -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("فالووینگ تو که فالو‌بک نداده‌اند", fontWeight = FontWeight.Bold, color = Ink) }
                if (data.notFollowingBack.isEmpty()) {
                    item { Text("هنوز داده‌ای برای نمایش وجود ندارد. ابتدا خروجی حساب را وارد کن.") }
                }
                items(data.notFollowingBack) { username ->
                    Row(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("@$username", fontWeight = FontWeight.SemiBold)
                            Text("در فهرست فالووینگ هست، در فالوورها نیست", fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/"))
                            context.startActivity(intent)
                        }) { Text("باز کردن") }
                    }
                }
            }
            else -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Panel(
                        "تحلیل داده",
                        "این نسخه تعداد ارتباطات دوطرفه، فالوورهای یک‌طرفه و فالووینگ‌های بدون فالو‌بک را محاسبه می‌کند. تحلیل تعامل و Insights در نسخه فعلی پیاده‌سازی نشده است."
                    ) {}
                }
                item {
                    Panel(
                        "کنترل اقدامات",
                        "هیچ آنفالو خودکاری انجام نمی‌شود. برای هر اقدام باید صفحه حساب را باز کنی و تصمیم را در برنامه رسمی Instagram بگیری."
                    ) {}
                }
            }
        }
        Text(
            "نسخه 1.0.1 · تحلیل محلی داده‌های صاحب حساب",
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RowScope.Metric(title: String, value: String) {
    Column(Modifier.weight(1f).background(Color(0xFF35243F), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Color(0xFFE5D7E9), fontSize = 11.sp)
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
    fun parse(resolver: android.content.ContentResolver, uri: Uri): InstagramData {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("فایل انتخاب‌شده قابل خواندن نیست")
        if (bytes.isEmpty()) error("فایل خالی است")

        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!isZip) {
            val root = org.json.JSONTokener(bytes.toString(Charsets.UTF_8)).nextValue()
            val parsed = InstagramData(
                followers = namesFromObject(root, "followers"),
                following = namesFromObject(root, "following")
            )
            if (parsed.followers.isEmpty() && parsed.following.isEmpty()) {
                error("این فایل JSON شامل فهرست followers یا following قابل‌شناسایی نیست")
            }
            return parsed
        }

        var followers: Set<String> = emptySet()
        var following: Set<String> = emptySet()
        var foundFollowers = false
        var foundFollowing = false

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".json")) {
                    val normalized = entry.name.lowercase().replace('\\', '/')
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    val root = runCatching { org.json.JSONTokener(content).nextValue() }.getOrNull()
                    if (root != null) {
                        when {
                            Regex("(^|/)followers(_\\d+)?\\.json$").containsMatchIn(normalized) -> {
                                followers = followers + extractNames(root)
                                foundFollowers = true
                            }
                            Regex("(^|/)following(_\\d+)?\\.json$").containsMatchIn(normalized) -> {
                                following = following + extractNames(root)
                                foundFollowing = true
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        if (!foundFollowers && !foundFollowing) {
            error("در ZIP فایل‌های followers_1.json یا following.json پیدا نشد. خروجی JSON اینستاگرام را انتخاب کن.")
        }
        if (!foundFollowers || !foundFollowing) {
            error("فایل ZIP ناقص است: ${if (!foundFollowers) "فهرست دنبال‌کنندگان (followers) " else ""}${if (!foundFollowing) "فهرست دنبال‌شوندگان (following)" else ""} پیدا نشد.")
        }
        if (followers.isEmpty() && following.isEmpty()) {
            error("فایل‌های JSON پیدا شدند، اما نام کاربری قابل استخراج نبود.")
        }
        return InstagramData(followers = followers, following = following)
    }

    private fun namesFromObject(root: Any?, key: String): Set<String> {
        if (root is JSONArray) return extractNames(root)
        val obj = root as? JSONObject ?: return emptySet()
        val direct = obj.opt(key)
        if (direct != null && direct !== JSONObject.NULL) return extractNames(direct)
        val matching = obj.keys().asSequence()
            .filter { it.equals(key, ignoreCase = true) || it.startsWith("${key}_", ignoreCase = true) }
            .flatMap { extractNames(obj.opt(it)).asSequence() }
            .toSet()
        return if (matching.isNotEmpty()) matching else extractNames(obj)
    }

    private fun extractNames(root: Any?): Set<String> {
        val out = linkedSetOf<String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val list = value.optJSONArray("string_list_data")
                    if (list != null) {
                        for (i in 0 until list.length()) {
                            val item = list.optJSONObject(i)
                            val username = item?.optString("value")?.trim()?.removePrefix("@")
                            if (!username.isNullOrBlank() && username != "null") out += username
                        }
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
            }
        }
        visit(root)
        return out
    }
}
