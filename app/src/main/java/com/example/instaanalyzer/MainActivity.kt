package com.example.instaanalyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF21162C)
private val Pink = Color(0xFFC13C86)
private val Canvas = Color(0xFFF7F4F8)

class MainActivity : ComponentActivity() {
    private var importedData by mutableStateOf(InstagramData())
    private var pendingCsv by mutableStateOf("")
    private var isImporting by mutableStateOf(false)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selectedUri ->
            isImporting = true
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { RelationshipSnapshotStore.parse(this@MainActivity, selectedUri) }
                        .getOrElse { e -> InstagramData(error = e.message ?: "فایل قابل خواندن نیست") }
                }
                importedData = result
                isImporting = false
            }
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
        importedData = RelationshipSnapshotStore.loadLatest(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Pink, background = Canvas, surface = Color.White)) {
                AnalyzerHome(
                    data = importedData,
                    isImporting = isImporting,
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
    val hasPreviousSnapshot: Boolean = false,
    val relationshipDataLoaded: Boolean = false,
    val followerFiles: Int = 0,
    val followingFiles: Int = 0,
    val importedAt: Long = 0L
) {
    val notFollowingBack: List<String> get() = following.minus(followers).sorted()
    val fansNotFollowed: List<String> get() = followers.minus(following).sorted()
    val mutual: Set<String> get() = followers.intersect(following)
}

@Composable
private fun AnalyzerHome(data: InstagramData, isImporting: Boolean, onImport: () -> Unit, onExportCsv: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var relationshipQuery by remember { mutableStateOf("") }
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
        if (isImporting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        Button(onClick = onImport, enabled = !isImporting, modifier = Modifier.fillMaxWidth()) { Text(if (isImporting) "در حال خواندن فایل..." else "وارد کردن خروجی اینستاگرام") }
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
            1 -> {
                val filteredNotFollowingBack = data.notFollowingBack.filter {
                    it.contains(relationshipQuery.trim(), ignoreCase = true)
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            Text("فهرست فالو‌بک نداده‌ها", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "فالوورها: ${data.followers.size}  ·  فالووینگ: ${data.following.size}  ·  فالو‌بک نداده: ${data.notFollowingBack.size}",
                                fontSize = 12.sp, color = Color(0xFF655B6B), lineHeight = 18.sp
                            )
                            if (data.relationshipDataLoaded) Text(
                                "فایل‌های خوانده‌شده: ${data.followerFiles} بخش فالوور و ${data.followingFiles} بخش فالووینگ.",
                                fontSize = 11.sp, color = Color(0xFF655B6B)
                            )
                            Text(
                                "نام کاربری هر حساب را لمس کن تا پروفایل آن در اینستاگرام باز شود.",
                                fontSize = 12.sp, color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onImport, enabled = !isImporting, modifier = Modifier.fillMaxWidth()) {
                                Text(if (isImporting) "در حال خواندن فایل..." else if (data.relationshipDataLoaded) "وارد کردن خروجی جدید" else "انتخاب فایل خروجی اینستاگرام")
                            }
                            if (data.error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("خطای ورود اطلاعات: ${data.error}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = relationshipQuery,
                                onValueChange = { relationshipQuery = it.removePrefix("@").take(30) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("جست‌وجوی نام کاربری") },
                                placeholder = { Text("مثلاً username") }
                            )
                        }
                    }
                    if (!data.relationshipDataLoaded) {
                        item {
                            Panel("هنوز فهرست ارتباطات بارگذاری نشده", "از دکمه «انتخاب فایل خروجی اینستاگرام» در همین صفحه استفاده کن. فایل باید ZIP یا JSON خروجی رسمی و شامل followers و following باشد.") {}
                        }
                    } else if (data.following.isEmpty()) {
                        item {
                            Panel("فهرست فالووینگ خالی است", "برنامه ${data.followers.size} فالوور را خوانده، اما هیچ حسابی از فایل فالووینگ استخراج نکرده است. احتمالاً فایل یا قالب خروجی انتخاب‌شده کامل نیست؛ ZIP اصلی با قالب JSON را دوباره وارد کن.") {}
                        }
                    } else if (data.notFollowingBack.isEmpty()) {
                        item { Panel("موردی برای نمایش وجود ندارد", "از ${data.following.size} حسابی که دنبال می‌کنی، هیچ‌کدام در این خروجی از فهرست فالوورها جا نمانده‌اند. اگر این نتیجه با اینستاگرام مطابقت ندارد، ZIP اصلی را دوباره وارد کن و تعداد فالوورها و فالووینگ را بررسی کن.") {} }
                    } else if (filteredNotFollowingBack.isEmpty()) {
                        item { Text("با این عبارت، نام کاربری‌ای پیدا نشد.", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                    } else {
                        items(filteredNotFollowingBack, key = { it }) { username ->
                            Row(
                                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = { openInstagramProfile(context, username) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("@$username", fontWeight = FontWeight.Bold, color = Pink, fontSize = 15.sp)
                                    }
                                    Text("در فالووینگ هست · در فالوورها نیست", fontSize = 11.sp, color = Color.Gray)
                                }
                                TextButton(onClick = { openInstagramProfile(context, username) }) {
                                    Text("مشاهده پروفایل")
                                }
                            }
                        }
                    }
                }
            }
            2 -> GrowthTrackerPanel()
            3 -> ContentStudioPanel(onExportCsv)
            else -> AnalysisCenter(data)
        }
        Text("نسخه 2.2.0 · تحلیل ارتباطات و تاریخچه پایدار", Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 11.sp)
    }
}

private fun openInstagramProfile(context: Context, rawUsername: String) {
    val username = rawUsername.trim().removePrefix("@")
    if (!username.matches(Regex("[A-Za-z0-9._]{1,30}"))) return
    val profileUri = Uri.parse("https://www.instagram.com/$username/")
    context.startActivity(Intent(Intent.ACTION_VIEW, profileUri))
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


private data class AnalysisCheck(val title: String, val status: String, val detail: String)

@Composable
private fun AnalysisCenter(data: InstagramData) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<AnalysisCheck>>(emptyList()) }
    var started by remember { mutableStateOf(false) }
    val success = checks.count { it.status == "موفق" }
    val missing = checks.count { it.status == "نیازمند داده" }
    val failed = checks.count { it.status == "ناموفق" }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Panel("مرکز تحلیل کامل",
                "با زدن دکمه، بررسی‌های قابل انجام با اطلاعات ذخیره‌شده روی گوشی اجرا می‌شوند. نتیجه و علت انجام‌نشدن هر بررسی جداگانه نمایش داده خواهد شد.") {
                Button(onClick = {
                    checks = runCatching { performFullAnalysis(context, data) }.getOrElse {
                        listOf(AnalysisCheck("اجرای تحلیل", "ناموفق", "خطای غیرمنتظره: " + (it.message ?: "علت نامشخص")))
                    }
                    started = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (started) "اجرای دوباره تحلیل" else "شروع تحلیل کامل")
                }
            }
        }
        if (!started) {
            item { Panel("مواردی که بررسی می‌شوند",
                "فهرست ارتباطات و فالووبک، تغییرات نسبت به خروجی قبلی، روند رشد، عملکرد محتوا و نرخ تعامل، مقایسه قالب‌ها، تقویم انتشار، کفایت داده برای پیشنهاد ساعت انتشار و وضعیت اتصال رسمی Instagram.") {} }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("موفق", success.toString())
                    Metric("نیازمند داده", missing.toString())
                    Metric("ناموفق", failed.toString())
                }
            }
            item { Panel("خلاصه اجرای تحلیل",
                "موفق: " + success + " · نیازمند داده: " + missing + " · ناموفق: " + failed +
                    "\n«نیازمند داده» به معنی خطای برنامه نیست؛ یعنی اطلاعات لازم هنوز وارد نشده یا اتصال مربوطه برقرار نیست.") {} }
            items(checks) { check ->
                val color = when (check.status) {
                    "موفق" -> Color(0xFF237A4B)
                    "ناموفق" -> Color(0xFFB3261E)
                    else -> Color(0xFF946200)
                }
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(check.title, modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        Text(check.status, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(check.detail, color = Color(0xFF655B6B), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        item { Panel("محدودیت داده‌ها",
            "فایل روابط به‌تنهایی سن، جنسیت، علایق یا ساعات فعالیت مخاطبان را مشخص نمی‌کند. این اطلاعات فقط با گزارش معتبر Instagram Insights قابل تحلیل است. برنامه از نام کاربری، شخصیت یا ویژگی حساس افراد را حدس نمی‌زند.") {} }
    }
}

private fun performFullAnalysis(context: Context, data: InstagramData): List<AnalysisCheck> {
    val result = mutableListOf<AnalysisCheck>()
    if (data.relationshipDataLoaded) {
        result += AnalysisCheck("فهرست فالوورها و فالووینگ", "موفق",
            "تعداد " + data.followers.size + " فالوور و " + data.following.size + " فالووینگ خوانده شد.")
        result += AnalysisCheck("تفکیک ارتباطات", "موفق",
            "ارتباط دوطرفه: " + data.mutual.size + " · فالووبک نداده‌اند: " +
                data.notFollowingBack.size + " · تو دنبالشان نمی‌کنی: " + data.fansNotFollowed.size)
    } else {
        result += AnalysisCheck("فهرست فالوورها و فالووینگ", "نیازمند داده",
            "فهرست روابط در حافظه گوشی پیدا نشد. فایل ZIP/JSON خروجی رسمی را وارد کن؛ اگر قبلاً وارد کرده‌ای، این نسخه پس از راه‌اندازی مجدد هم آن را بازیابی می‌کند.")
    }

    if (data.hasPreviousSnapshot) {
        result += AnalysisCheck("مقایسه با خروجی قبلی", "موفق",
            "فالوور جدید: " + data.newFollowers.size + " · ازدست‌رفته: " + data.lostFollowers.size +
                " · فالووینگ جدید: " + data.newFollowing.size + " · حذف‌شده: " + data.removedFollowing.size)
    } else {
        result += AnalysisCheck("مقایسه با خروجی قبلی", "نیازمند داده",
            "خروجی قبلی برای مقایسه وجود ندارد. خروجی بعدی را هم وارد کن تا تغییرات مشخص شوند.")
    }

    runCatching {
        val raw = context.getSharedPreferences("insta_analyzer_local_metrics", Context.MODE_PRIVATE)
            .getString("growth_snapshots", "[]") ?: "[]"
        val array = org.json.JSONArray(raw)
        val ordered = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .sortedBy { it.optLong("recordedAt") }
        if (ordered.size >= 2) {
            val delta = ordered.last().optInt("followers") - ordered.first().optInt("followers")
            result += AnalysisCheck("روند رشد فالوورها", "موفق",
                ordered.size.toString() + " ثبت بررسی شد؛ تغییر خالص: " + delta + " فالوور.")
        } else {
            result += AnalysisCheck("روند رشد فالوورها", "نیازمند داده",
                "فقط " + ordered.size + " ثبت وجود دارد؛ برای روند حداقل دو ثبت در بخش رشد پیج لازم است.")
        }
    }.onFailure {
        result += AnalysisCheck("روند رشد فالوورها", "ناموفق",
            "خواندن تاریخچه رشد با خطا مواجه شد: " + (it.message ?: "ساختار داده نامعتبر است"))
    }

    runCatching {
        val prefs = context.getSharedPreferences("insta_analyzer_studio_v2", Context.MODE_PRIVATE)
        val records = org.json.JSONArray(prefs.getString("content_records", "[]") ?: "[]")
        var reach = 0L
        var interactions = 0L
        val formats = linkedMapOf<String, MutableList<Double>>()
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            val itemReach = item.optLong("reach")
            val itemInteractions = item.optLong("likes") + item.optLong("comments") +
                item.optLong("shares") + item.optLong("saves")
            reach += itemReach
            interactions += itemInteractions
            if (itemReach > 0) {
                val format = item.optString("format", "نامشخص")
                formats.getOrPut(format) { mutableListOf() }.add(itemInteractions * 100.0 / itemReach)
            }
        }
        if (records.length() > 0 && reach > 0) {
            val rate = interactions * 100.0 / reach
            result += AnalysisCheck("عملکرد محتوا و نرخ تعامل", "موفق",
                records.length().toString() + " محتوا بررسی شد؛ Reach: " + reach +
                    " · تعامل: " + interactions + " · نرخ تعامل وزنی: " +
                    String.format(java.util.Locale.US, "%.2f", rate) + "٪")
        } else {
            result += AnalysisCheck("عملکرد محتوا و نرخ تعامل", "نیازمند داده",
                "محتوای دارای Reach معتبر ثبت نشده است؛ آمار ریلز، پست یا استوری را در بخش محتوا وارد کن.")
        }
        if (formats.size >= 2) {
            val best = formats.mapValues { it.value.average() }.maxByOrNull { it.value }
            result += AnalysisCheck("مقایسه قالب‌های محتوا", "موفق",
                "قالب دارای میانگین نرخ تعامل بالاتر: " + (best?.key ?: "نامشخص") +
                    "؛ نتیجه فقط بر اساس محتواهای ثبت‌شده است.")
        } else {
            result += AnalysisCheck("مقایسه قالب‌های محتوا", "نیازمند داده",
                "برای مقایسه ریلز، پست و استوری، داده معتبر از حداقل دو قالب لازم است.")
        }
    }.onFailure {
        result += AnalysisCheck("عملکرد محتوا", "ناموفق",
            "خواندن اطلاعات محتوا ناموفق بود: " + (it.message ?: "ساختار داده نامعتبر است"))
    }

    runCatching {
        val prefs = context.getSharedPreferences("insta_analyzer_studio_v2", Context.MODE_PRIVATE)
        val plans = org.json.JSONArray(prefs.getString("calendar_entries", "[]") ?: "[]")
        if (plans.length() > 0) result += AnalysisCheck("تقویم انتشار", "موفق",
            plans.length().toString() + " برنامه انتشار بررسی شد.")
        else result += AnalysisCheck("تقویم انتشار", "نیازمند داده", "هنوز برنامه‌ای در تقویم انتشار ثبت نشده است.")
    }.onFailure {
        result += AnalysisCheck("تقویم انتشار", "ناموفق",
            "خواندن تقویم ناموفق بود: " + (it.message ?: "ساختار داده نامعتبر است"))
    }

    result += AnalysisCheck("پیشنهاد بهترین ساعت انتشار", "نیازمند داده",
        "زمان دقیق انتشار هر محتوا ذخیره نمی‌شود؛ بنابراین پیشنهاد ساعت قابل اعتماد ممکن نیست.")
    result += AnalysisCheck("تحلیل جمعیتی و علایق مخاطبان", "نیازمند داده",
        "فهرست فالوورها سن، جنسیت، علایق یا ساعات فعالیت را مشخص نمی‌کند؛ گزارش معتبر Insights لازم است.")
    result += AnalysisCheck("دریافت خودکار Instagram Insights", "نیازمند داده",
        "اتصال رسمی Meta هنوز در برنامه تنظیم نشده است؛ این قابلیت به مجوز رسمی حساب حرفه‌ای نیاز دارد.")
    return result
}


private object RelationshipSnapshotStore {
    private const val PREFS = "insta_relationship_history"

    fun parse(context: Context, uri: Uri): InstagramData {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

        val input = context.contentResolver.openInputStream(uri)
            ?: error("فایل انتخاب‌شده قابل خواندن نیست.")
        val parsed = InstagramExportParser.parse(input, displayName)
        return recordSnapshot(context, parsed)
    }

    fun loadLatest(context: Context): InstagramData {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("snapshot_at")) return InstagramData()
        return InstagramData(
            followers = prefs.getStringSet("followers", emptySet())?.toSet() ?: emptySet(),
            following = prefs.getStringSet("following", emptySet())?.toSet() ?: emptySet(),
            newFollowers = prefs.getStringSet("last_new_followers", emptySet())?.toSet() ?: emptySet(),
            lostFollowers = prefs.getStringSet("last_lost_followers", emptySet())?.toSet() ?: emptySet(),
            newFollowing = prefs.getStringSet("last_new_following", emptySet())?.toSet() ?: emptySet(),
            removedFollowing = prefs.getStringSet("last_removed_following", emptySet())?.toSet() ?: emptySet(),
            hasPreviousSnapshot = prefs.getBoolean("has_previous_snapshot", false),
            relationshipDataLoaded = true,
            followerFiles = prefs.getInt("follower_files", 0),
            followingFiles = prefs.getInt("following_files", 0),
            importedAt = prefs.getLong("snapshot_at", 0L)
        )
    }

    private fun recordSnapshot(context: Context, parsed: ParsedRelationships): InstagramData {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldFollowers = prefs.getStringSet("followers", emptySet())?.toSet() ?: emptySet()
        val oldFollowing = prefs.getStringSet("following", emptySet())?.toSet() ?: emptySet()
        val hasPrevious = prefs.contains("snapshot_at")
        val diff = if (hasPrevious) {
            RelationshipAnalytics.compare(oldFollowers, parsed.followers, oldFollowing, parsed.following)
        } else {
            RelationshipChange()
        }
        val now = System.currentTimeMillis()
        prefs.edit()
            .putStringSet("followers", parsed.followers.toSet())
            .putStringSet("following", parsed.following.toSet())
            .putStringSet("last_new_followers", diff.newFollowers.toSet())
            .putStringSet("last_lost_followers", diff.lostFollowers.toSet())
            .putStringSet("last_new_following", diff.newFollowing.toSet())
            .putStringSet("last_removed_following", diff.removedFollowing.toSet())
            .putBoolean("has_previous_snapshot", hasPrevious)
            .putInt("follower_files", parsed.followerFiles)
            .putInt("following_files", parsed.followingFiles)
            .putLong("snapshot_at", now)
            .apply()

        return InstagramData(
            followers = parsed.followers,
            following = parsed.following,
            newFollowers = diff.newFollowers,
            lostFollowers = diff.lostFollowers,
            newFollowing = diff.newFollowing,
            removedFollowing = diff.removedFollowing,
            hasPreviousSnapshot = hasPrevious,
            relationshipDataLoaded = true,
            followerFiles = parsed.followerFiles,
            followingFiles = parsed.followingFiles,
            importedAt = now
        )
    }
}
