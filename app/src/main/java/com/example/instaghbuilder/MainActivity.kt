package com.example.instaghbuilder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFFC13584),
                    secondary = androidx.compose.ui.graphics.Color(0xFF833AB4),
                    background = androidx.compose.ui.graphics.Color(0xFFFFF7FB),
                    surface = androidx.compose.ui.graphics.Color.White
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GrowthAssistantScreen()
                }
            }
        }
    }
}

@Composable
private fun GrowthAssistantScreen() {
    val context = LocalContext.current
    var niche by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<GrowthPlan?>(null) }
    var showShareConfirmation by remember { mutableStateOf(false) }
    var showInstagramConfirmation by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "دستیار رشد اینستاگرام",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "ایده و پیش‌نویس محتوا بگیر؛ تصمیم و انتشار همیشه با خودت است.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("برای چه صفحه‌ای محتوا می‌سازی؟", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = niche,
                    onValueChange = { niche = it },
                    label = { Text("موضوع صفحه (مثلاً آشپزی)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = audience,
                    onValueChange = { audience = it },
                    label = { Text("مخاطب هدف (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("هدفت (مثلاً آموزش نکته‌های ساده)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        plan = makePlan(niche.trim(), audience.trim(), goal.trim())
                        notice = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پیشنهاد محتوا بساز")
                }
            }
        }

        plan?.let { contentPlan ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("پیشنهاد آمادهٔ بررسی", fontWeight = FontWeight.Bold)
                    Text("ایده: ${contentPlan.idea}")
                    Text("قالب پیشنهادی: ${contentPlan.format}")
                    Text("پیشنهاد: ${contentPlan.tip}")
                    Text("پیش‌نویس کپشن:", fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            contentPlan.caption,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = { showShareConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بررسی و اشتراک‌گذاری کپشن")
                    }
                    OutlinedButton(
                        onClick = { showInstagramConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("باز کردن اینستاگرام")
                    }
                }
            }
        }

        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("حریم خصوصی و کنترل دست توست", fontWeight = FontWeight.SemiBold)
                Text(
                    "این نسخه وارد حساب نمی‌شود، رمز عبور یا اطلاعات اینستاگرامت را نمی‌گیرد و هیچ فالو، آنفالو، پیام یا پستی را خودکار انجام نمی‌دهد. پیش از بازکردن اینستاگرام یا اشتراک‌گذاری، از تو تأیید می‌گیرد."
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showShareConfirmation && plan != null) {
        AlertDialog(
            onDismissRequest = { showShareConfirmation = false },
            title = { Text("تأیید اشتراک‌گذاری") },
            text = {
                Text("کپشن پیشنهادی در منوی اشتراک‌گذاری گوشی باز می‌شود. قبل از انتشار می‌توانی آن را ویرایش یا لغو کنی؛ برنامه خودش چیزی منتشر نمی‌کند.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showShareConfirmation = false
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, plan!!.caption)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "انتخاب برنامه برای ادامه"))
                }) { Text("تأیید و ادامه") }
            },
            dismissButton = {
                TextButton(onClick = { showShareConfirmation = false }) { Text("لغو") }
            }
        )
    }

    if (showInstagramConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstagramConfirmation = false },
            title = { Text("باز کردن اینستاگرام؟") },
            text = { Text("با تأیید تو، برنامهٔ اینستاگرام باز می‌شود. هیچ اقدامی در حساب انجام نخواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    showInstagramConfirmation = false
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
                        }
                    } catch (_: ActivityNotFoundException) {
                        notice = "اینستاگرام نصب نیست یا امکان بازکردن آن وجود ندارد."
                    }
                }) { Text("باز کن") }
            },
            dismissButton = {
                TextButton(onClick = { showInstagramConfirmation = false }) { Text("لغو") }
            }
        )
    }
}

private data class GrowthPlan(
    val idea: String,
    val format: String,
    val tip: String,
    val caption: String
)

private fun makePlan(niche: String, audience: String, goal: String): GrowthPlan {
    val topic = niche.ifBlank { "موضوع صفحه‌ات" }
    val target = audience.ifBlank { "دوست‌داران $topic" }
    val purpose = goal.ifBlank { "یک نکتهٔ کاربردی و ساده یاد بگیرند" }
    return GrowthPlan(
        idea = "یک نکتهٔ رایج دربارهٔ $topic را در چند مرحله توضیح بده و در پایان یک نمونهٔ واقعی نشان بده.",
        format = "ویدیوی کوتاه یا اسلاید آموزشی؛ هر بخش فقط یک پیام داشته باشد.",
        tip = "برای $target شروع ویدیو را با یک پرسش روشن جذاب کن. ��ر پایان، مخاطب را به گفت‌وگو دعوت کن؛ نتیجهٔ رشد تضمین‌شده نیست.",
        caption = "اگر به $topic علاقه داری، این نکته را ذخیره کن 🌱\n\nهدف: $purpose\n\nتو دوست داری دربارهٔ کدام بخش بیشتر بدانی؟ نظرت را بنویس.\n\n#${safeHashtag(niche)} #آموزش #ایده"
    )
}

private fun safeHashtag(value: String): String =
    value.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "اینستاگرام" }
