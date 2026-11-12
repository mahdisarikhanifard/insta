package com.example.instaanalyzer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val StudioInk = Color(0xFF21162C)
private val StudioPink = Color(0xFFC13C86)

private data class ContentRecord(
    val id: Long,
    val title: String,
    val format: String,
    val date: String,
    val reach: Int,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val saves: Int,
    val follows: Int
) {
    val interactions: Int get() = likes + comments + shares + saves
    val engagementRate: Double? get() = if (reach > 0) interactions * 100.0 / reach else null
}

private data class CalendarEntry(
    val id: Long,
    val date: String,
    val topic: String,
    val format: String,
    val status: String
)

private object StudioStore {
    private const val PREFS = "insta_analyzer_studio_v2"
    private const val CONTENT = "content_records"
    private const val CALENDAR = "calendar_entries"

    fun loadContent(context: Context): List<ContentRecord> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CONTENT, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ContentRecord(
                o.optLong("id"), o.optString("title"), o.optString("format"), o.optString("date"),
                o.optInt("reach"), o.optInt("likes"), o.optInt("comments"), o.optInt("shares"),
                o.optInt("saves"), o.optInt("follows")
            )
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    fun saveContent(context: Context, records: List<ContentRecord>) {
        val a = JSONArray()
        records.forEach { r -> a.put(JSONObject().apply {
            put("id", r.id); put("title", r.title); put("format", r.format); put("date", r.date)
            put("reach", r.reach); put("likes", r.likes); put("comments", r.comments)
            put("shares", r.shares); put("saves", r.saves); put("follows", r.follows)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CONTENT, a.toString()).apply()
    }

    fun loadCalendar(context: Context): List<CalendarEntry> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CALENDAR, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            CalendarEntry(o.optLong("id"), o.optString("date"), o.optString("topic"), o.optString("format"), o.optString("status"))
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    fun saveCalendar(context: Context, entries: List<CalendarEntry>) {
        val a = JSONArray()
        entries.forEach { e -> a.put(JSONObject().apply {
            put("id", e.id); put("date", e.date); put("topic", e.topic); put("format", e.format); put("status", e.status)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CALENDAR, a.toString()).apply()
    }
}

@Composable
internal fun ContentStudioPanel(onExportCsv: (String) -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(StudioStore.loadContent(context)) }
    var plans by remember { mutableStateOf(StudioStore.loadCalendar(context)) }
    var section by remember { mutableStateOf(0) }
    var showContentDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("ریلز") }
    var date by remember { mutableStateOf("") }
    var reach by remember { mutableStateOf("") }
    var likes by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var saves by remember { mutableStateOf("") }
    var follows by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var planDate by remember { mutableStateOf("") }
    var planFormat by remember { mutableStateOf("ریلز") }
    var error by remember { mutableStateOf<String?>(null) }

    val totalReach = records.sumOf { it.reach.toLong() }
    val totalInteractions = records.sumOf { it.interactions.toLong() }
    val weightedRate = if (totalReach > 0) totalInteractions * 100.0 / totalReach else null
    val best = records.filter { it.engagementRate != null }.groupBy { it.format }
        .mapValues { (_, list) -> list.mapNotNull { it.engagementRate }.average() }
        .maxByOrNull { it.value }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("استودیوی رشد و محتوا", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = StudioInk)
                Text("ثبت عملکرد محتوا، تقویم انتشار و پیشنهادهای مبتنی بر آمار واقعی پیج", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("عملکرد محتوا", "تقویم انتشار", "راهبرد رشد").forEachIndexed { i, label ->
                        FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label) })
                    }
                }
            }
        }
        when (section) {
            0 -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioMetric("مجموع Reach", totalReach.toString(), Modifier.weight(1f))
                        StudioMetric("تعامل کل", totalInteractions.toString(), Modifier.weight(1f))
                        StudioMetric("نرخ تعامل", weightedRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—", Modifier.weight(1f))
                    }
                }
                item {
                    Button(onClick = {
                        title = ""; date = ""; reach = ""; likes = ""; comments = ""; shares = ""; saves = ""; follows = ""; format = "ریلز"; error = null
                        showContentDialog = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("ثبت عملکرد پست یا ریلز") }
                }
                if (records.isEmpty()) item { StudioPanel("هنوز محتوایی ثبت نشده", "برای شروع، آمار یک پست یا ریلز را از Insights وارد کن. مقایسه‌ها فقط بر اساس داده‌های ثبت‌شده انجام می‌شوند.") }
                items(records, key = { it.id }) { record ->
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(record.title.ifBlank { "محتوای بدون عنوان" }, fontWeight = FontWeight.Bold, color = StudioInk)
                            Text(record.format, color = StudioPink, fontSize = 12.sp)
                        }
                        Text(record.date, color = Color.Gray, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Reach: ${record.reach}  ·  تعامل: ${record.interactions}  ·  فالوور جذب‌شده: ${record.follows}", fontSize = 12.sp)
                        Text("نرخ تعامل بر اساس Reach: ${record.engagementRate?.let { String.format(Locale.US, "%.2f%%", it) } ?: "قابل محاسبه نیست"}", fontSize = 12.sp, color = StudioInk)
                        TextButton(onClick = {
                            records = records.filterNot { it.id == record.id }
                            StudioStore.saveContent(context, records)
                        }) { Text("حذف رکورد") }
                    }
                }
                if (records.isNotEmpty()) item {
                    OutlinedButton(onClick = { onExportCsv(contentCsv(records)) }, modifier = Modifier.fillMaxWidth()) { Text("خروجی CSV عملکرد محتوا") }
                }
            }
            1 -> {
                item {
                    Button(onClick = {
                        topic = ""; planDate = ""; planFormat = "ریلز"; error = null; showPlanDialog = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("افزودن برنامه انتشار") }
                }
                if (plans.isEmpty()) item { StudioPanel("تقویم هنوز خالی است", "موضوع، قالب و تاریخ انتشار محتوای بعدی را ثبت کن تا برنامه تولید محتوا منظم شود.") }
                items(plans, key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text(entry.topic, fontWeight = FontWeight.Bold, color = StudioInk)
                        Text("${entry.date}  ·  ${entry.format}  ·  ${entry.status}", color = Color.Gray, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                plans = plans.map { if (it.id == entry.id) it.copy(status = if (it.status == "منتشر شد") "برنامه‌ریزی‌شده" else "منتشر شد") else it }
                                StudioStore.saveCalendar(context, plans)
                            }) { Text(if (entry.status == "منتشر شد") "بازگرداندن به برنامه" else "علامت‌گذاری انتشار") }
                            TextButton(onClick = {
                                plans = plans.filterNot { it.id == entry.id }; StudioStore.saveCalendar(context, plans)
                            }) { Text("حذف") }
                        }
                    }
                }
            }
            else -> {
                item { StudioPanel("تحلیل عملکرد محتوا", "نرخ تعامل = (پسندیدن + نظرها + اشتراک‌گذاری + ذخیره) تقسیم بر Reach. این شاخص برای مقایسه محتوا مفید است؛ با نرخ تعامل مبتنی بر تعداد فالوورها یکسان نیست.") }
                item {
                    StudioPanel(
                        "بهترین قالب ثبت‌شده",
                        when {
                            records.size < 3 -> "برای پیشنهاد قابل اتکا، حداقل سه محتوای دارای Reach و تعامل ثبت کن. فعلاً داده کافی برای انتخاب قالب برتر نداریم."
                            best == null -> "Reach برای محتواها ثبت نشده است؛ نرخ تعامل قابل محاسبه نیست."
                            else -> "در داده‌های فعلی، قالب «${best.key}» با میانگین نرخ تعامل ${String.format(Locale.US, "%.2f%%", best.value)} بهترین عملکرد را داشته است. این نتیجه توصیفی است، نه تضمین عملکرد آینده."
                        }
                    )
                }
                item {
                    StudioPanel(
                        "جذب فالوور",
                        if (records.isEmpty()) "پس از ثبت آمار محتوا، نسبت فالوور جذب‌شده به Reach و عملکرد قالب‌ها بررسی می‌شود. خرید فالوور، ربات و تعامل مصنوعی پیشنهاد نمی‌شود."
                        else {
                            val totalFollows = records.sumOf { it.follows }
                            val conversion = if (totalReach > 0) totalFollows * 100.0 / totalReach else null
                            "فالوور جذب‌شده در رکوردهای ثبت‌شده: $totalFollows. تبدیل Reach به فالوور: ${conversion?.let { String.format(Locale.US, "%.2f%%", it) } ?: "داده کافی نیست"}. برای رشد پایدار، محتواهای با تبدیل بالاتر را با موضوع و فراخوان اقدام روشن تکرار و آزمایش کن."
                        }
                    )
                }
                item { StudioPanel("مخاطب‌شناسی مسئولانه", "این نسخه الگوی تعامل مخاطبان را به‌صورت تجمیعی تحلیل می‌کند؛ مثلاً کدام موضوع یا قالب بیشتر ذخیره و به‌اشتراک گذاشته شده است. از روی پروفایل افراد، شخصیت پنهان، سلامت روان یا باورهای حساس آن‌ها استنباط نمی‌شود.") }
                item { StudioPanel("زمان انتشار", "برای پیشنهاد ساعت انتشار، باید زمان دقیق انتشار و عملکرد هر محتوا ثبت شود. تا وقتی داده کافی جمع نشده، ساعت برتر حدس زده نمی‌شود.") }
            }
        }
    }

    if (showContentDialog) {
        AlertDialog(
            onDismissRequest = { showContentDialog = false },
            title = { Text("ثبت عملکرد محتوا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان یا موضوع") }, singleLine = true)
                    OutlinedTextField(date, { date = it }, label = { Text("تاریخ انتشار (YYYY-MM-DD)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ریلز", "پست", "استوری").forEach { FilterChip(selected = format == it, onClick = { format = it }, label = { Text(it) }) }
                    }
                    NumberField("Reach", reach) { reach = it }
                    NumberField("پسندیدن", likes) { likes = it }
                    NumberField("نظرها", comments) { comments = it }
                    NumberField("اشتراک‌گذاری", shares) { shares = it }
                    NumberField("ذخیره", saves) { saves = it }
                    NumberField("فالوور جذب‌شده", follows) { follows = it }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nums = listOf(reach, likes, comments, shares, saves, follows)
                    val parsed = nums.map { it.toIntOrNull() }
                    when {
                        title.isBlank() -> error = "عنوان یا موضوع محتوا را وارد کن."
                        !date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> error = "تاریخ را با قالب YYYY-MM-DD وارد کن."
                        parsed.any { it == null || it < 0 } -> error = "همه شاخص‌ها باید عدد صحیح نامنفی باشند."
                        else -> {
                            val v = parsed.map { it!! }
                            records = (records + ContentRecord(System.currentTimeMillis(), title.trim(), format, date, v[0], v[1], v[2], v[3], v[4], v[5])).sortedByDescending { it.date }
                            StudioStore.saveContent(context, records); showContentDialog = false
                        }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showContentDialog = false }) { Text("انصراف") } }
        )
    }

    if (showPlanDialog) {
        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text("برنامه انتشار") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(topic, { topic = it }, label = { Text("موضوع محتوا") }, singleLine = true)
                    OutlinedTextField(planDate, { planDate = it }, label = { Text("تاریخ هدف (YYYY-MM-DD)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ریلز", "پست", "استوری").forEach { FilterChip(selected = planFormat == it, onClick = { planFormat = it }, label = { Text(it) }) }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        topic.isBlank() -> error = "موضوع محتوا را وارد کن."
                        !planDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> error = "تاریخ را با قالب YYYY-MM-DD وارد کن."
                        else -> {
                            plans = (plans + CalendarEntry(System.currentTimeMillis(), planDate, topic.trim(), planFormat, "برنامه‌ریزی‌شده")).sortedBy { it.date }
                            StudioStore.saveCalendar(context, plans); showPlanDialog = false
                        }
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showPlanDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun StudioMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StudioInk)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun StudioPanel(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = StudioInk)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, color = Color(0xFF655B6B), lineHeight = 19.sp)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(9)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun contentCsv(records: List<ContentRecord>): String {
    val header = "title,format,published_date,reach,likes,comments,shares,saves,interactions,engagement_rate_percent,new_followers"
    val rows = records.map { r ->
        val title = "\"${r.title.replace("\"", "\"\"")}\""
        listOf(title, r.format, r.date, r.reach, r.likes, r.comments, r.shares, r.saves, r.interactions,
            r.engagementRate?.let { String.format(Locale.US, "%.3f", it) } ?: "", r.follows).joinToString(",")
    }
    return (listOf(header) + rows).joinToString("\n")
}
