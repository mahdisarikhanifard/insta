package com.example.instaanalyzer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GrowthSnapshot(val recordedAt: Long, val followers: Int, val following: Int, val reach: Int, val profileVisits: Int, val interactions: Int)

private object GrowthStore {
    private const val PREFS = "insta_analyzer_local_metrics"
    private const val KEY = "growth_snapshots"
    fun load(context: Context): List<GrowthSnapshot> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            GrowthSnapshot(o.optLong("recordedAt"), o.optInt("followers"), o.optInt("following"), o.optInt("reach"), o.optInt("profileVisits"), o.optInt("interactions"))
        }.sortedByDescending { it.recordedAt }
    }.getOrDefault(emptyList())
    fun save(context: Context, snapshots: List<GrowthSnapshot>) {
        val array = JSONArray()
        snapshots.sortedBy { it.recordedAt }.forEach { s ->
            array.put(JSONObject().apply {
                put("recordedAt", s.recordedAt); put("followers", s.followers); put("following", s.following)
                put("reach", s.reach); put("profileVisits", s.profileVisits); put("interactions", s.interactions)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

@Composable
internal fun GrowthTrackerPanel() {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf(GrowthStore.load(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var followers by remember { mutableStateOf("") }
    var following by remember { mutableStateOf("") }
    var reach by remember { mutableStateOf("") }
    var profileVisits by remember { mutableStateOf("") }
    var interactions by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val latest = snapshots.firstOrNull()
    val previous = snapshots.getOrNull(1)
    val delta = if (latest != null && previous != null) latest.followers - previous.followers else null
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("fa", "IR")) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("دفتر ثبت آمار پیج", color = Color(0xFF21162C), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("آمار را از Instagram Insights یا Meta Business Suite خودت وارد کن. داده‌ها فقط روی همین گوشی ذخیره می‌شوند و این بخش به اتصال Meta نیاز ندارد.", color = Color(0xFF655B6B), fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    followers = latest?.followers?.toString() ?: ""; following = latest?.following?.toString() ?: ""
                    reach = ""; profileVisits = ""; interactions = ""; validationError = null; showDialog = true
                }, modifier = Modifier.fillMaxWidth()) { Text("ثبت آمار جدید") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrowthMetricCard("فالوور فعلی", latest?.followers?.toString() ?: "—", Modifier.weight(1f))
                GrowthMetricCard("تغییر نسبت به ثبت قبل", when { delta == null -> "—"; delta > 0 -> "+$delta"; else -> delta.toString() }, Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("پیشنهادهای مبتنی بر داده", color = Color(0xFF21162C), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                val advice = when {
                    latest == null -> "بعد از ثبت اولین آمار، پیشنهادهای اولیه اینجا نمایش داده می‌شوند."
                    latest.reach == 0 && latest.interactions == 0 -> "برای تحلیل عملکرد محتوا، در ثبت بعدی Reach و تعاملات را هم از Insights وارد کن."
                    latest.reach > 0 && latest.interactions == 0 -> "دسترسی ثبت شده اما تعاملی ثبت نشده است؛ دعوت روشن به ذخیره، نظر یا اشتراک‌گذاری را در محتوای بعدی آزمایش کن."
                    latest.reach > 0 && latest.interactions > latest.reach -> "تعامل ثبت‌شده از Reach بیشتر است؛ واحد و بازه زمانی آمار را بررسی کن."
                    latest.reach > 0 -> "نسبت تعامل به Reach در آخرین ثبت حدود " + String.format(Locale.US, "%.1f", latest.interactions.toDouble() / latest.reach.toDouble() * 100.0) + "٪ است. این شاخص را در ثبت‌های بعدی مقایسه کن؛ یک ثبت برای نتیجه‌گیری کافی نیست."
                    else -> "آمار بیشتری ثبت کن تا بتوان روند را بررسی کرد."
                }
                Text(advice, color = Color(0xFF655B6B), fontSize = 13.sp, lineHeight = 20.sp)
                if (latest != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("آخرین ثبت: " + dateFormat.format(Date(latest.recordedAt)), color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
        item { Text("تاریخچه ثبت‌ها", color = Color(0xFF21162C), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (snapshots.isEmpty()) item { Text("هنوز آماری ثبت نشده است. برای شروع، تعداد فالوورها را وارد کن؛ سایر شاخص‌ها اختیاری‌اند.", color = Color.Gray, fontSize = 13.sp) }
        items(snapshots) { snapshot ->
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text(dateFormat.format(Date(snapshot.recordedAt)), color = Color(0xFF21162C), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("فالوور: " + snapshot.followers + "  ·  فالووینگ: " + snapshot.following, color = Color(0xFF655B6B), fontSize = 12.sp)
                Text("Reach: " + snapshot.reach + "  ·  بازدید پروفایل: " + snapshot.profileVisits + "  ·  تعامل: " + snapshot.interactions, color = Color(0xFF655B6B), fontSize = 12.sp)
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("ثبت آمار پیج") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricInput("تعداد فالوورها (ضروری)", followers) { followers = it }
                    MetricInput("تعداد فالووینگ", following) { following = it }
                    MetricInput("Reach در بازه گزارش", reach) { reach = it }
                    MetricInput("بازدید پروفایل", profileVisits) { profileVisits = it }
                    MetricInput("تعاملات محتوا", interactions) { interactions = it }
                    if (validationError != null) Text(validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = followers.toIntOrNull()
                    val optional = listOf(following, reach, profileVisits, interactions)
                    val invalid = optional.any { it.isNotBlank() && (it.toIntOrNull() == null || it.toIntOrNull()!! < 0) }
                    if (f == null || f < 0 || invalid) validationError = "تعداد فالوورها الزامی است و همه اعداد باید صحیح و نامنفی باشند."
                    else {
                        val entry = GrowthSnapshot(System.currentTimeMillis(), f, following.toIntOrNull() ?: 0, reach.toIntOrNull() ?: 0, profileVisits.toIntOrNull() ?: 0, interactions.toIntOrNull() ?: 0)
                        snapshots = (listOf(entry) + snapshots).sortedByDescending { it.recordedAt }
                        GrowthStore.save(context, snapshots); showDialog = false
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun GrowthMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF35243F), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFE5D7E9), fontSize = 11.sp)
    }
}

@Composable
private fun MetricInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(9)) },
        label = { Text(label, fontSize = 12.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
