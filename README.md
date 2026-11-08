# GitHub ساز حرفه‌ای — Android 1.0.0

اپ اندروید برای انتقال سورس پروژه ZIP به مخزن GitHub، به‌روزرسانی فایل‌ها روی یک شاخه و اجرای GitHub Actions workflow.

## امکانات
- ذخیره امن PAT با Android EncryptedSharedPreferences
- بررسی اعتبار توکن
- دریافت فهرست مخازن و شاخه‌ها
- انتخاب ZIP سورس پروژه
- نوشتن فایل‌ها از طریق GitHub Contents API (به‌روزرسانی فایل موجود با SHA یا ایجاد فایل تازه)
- اجرای Workflow از طریق workflow_dispatch
- مشاهده وضعیت Runهای اخیر
- پیام خطای GitHub API در رابط

## مجوزهای توکن
برای GitHub fine-grained PAT، مخزن انتخابی را محدود کنید. معمولاً `Contents: Read and write` و `Actions: Read and write` برای ارسال سورس و dispatch لازم است. برای مخازن سازمانی ممکن است تأیید سازمان لازم باشد.

## نکته مهم
این نسخه هر فایل ZIP را به‌صورت عملیات مستقل Contents API commit می‌کند؛ برای پروژه‌های بزرگ، نرخ درخواست و محدودیت API می‌تواند اهمیت داشته باشد. فعلاً نمایش Artifact و دانلود مستقیم APK از رابط هنوز در نسخه پایه نیست؛ می‌توان APK را از صفحه Actions دریافت کرد. پیش از استفاده روی مخزن اصلی، ابتدا با یک مخزن آزمایشی کار کنید.

## مشخصات
applicationId: `com.example.instaghbuilder`; compileSdk 35; minSdk 26; Java 17; AGP 8.7.3; Gradle 8.9.
