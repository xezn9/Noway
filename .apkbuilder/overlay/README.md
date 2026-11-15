# ApkBuilderApp

تطبيق أندرويد فيه زرين:
1. اختيار ملف مشروع مضغوط (.zip)
2. حفظ ملف الـ APK الناتج

مع خطوة وسطى تلقائية: يحلل المشروع (ProjectRequirementsDetector)، يكتشف نسخة Java المطلوبة
وcompileSdk/minSdk/buildTools، وينزّل أي أداة ناقصة (JDK أو Android SDK) ويخزنها داخل
filesDir عشان ما يعيد التنزيل كل مرة (ToolchainManager)، ثم يبني APK فعلي (ProjectBuilder).

## الملفات
- `MainActivity.kt`            — الواجهة والأزرار وتسلسل العملية
- `ProjectUnzipper.kt`         — فك ضغط المشروع بأمان (حماية Zip Slip)
- `ProjectRequirementsDetector.kt` — يقرأ build.gradle(.kts) ويستخرج النسخ المطلوبة فعلياً
- `ToolchainManager.kt`        — يتحقق/ينزّل JDK (Eclipse Temurin) و Android SDK (Google) حسب الحاجة
- `ProjectBuilder.kt`          — يبني: إما عبر gradlew لو موجود، أو يدوياً (javac→d8→aapt2→zipalign→apksigner)

## قيود واقعية يفترض تعرفها قبل التشغيل على جهاز حقيقي

1. **الحجم**: أول تشغيل ينزل JDK (~200 م.ب) + Android SDK tools + build-tools + platform
   (مئات الميجا إضافية) — يحتاج إنترنت جيد ومساحة تخزين كافية. التنزيل يصير مرة وحدة فقط،
   تم تخزينه بـ `filesDir/toolchains`.

2. **تنفيذ ملفات ثنائية منزّلة**: أندرويد يسمح بتشغيل binary وضعته بنفسك داخل `filesDir` الخاص
   بتطبيقك بعد `setExecutable(true)` — هذا شغال حالياً، لكن قابل للتضييق مستقبلاً من جوجل
   لأسباب أمنية. جرّب على جهازك المستهدف قبل الاعتماد الكامل.

3. **المعالج (ABI)**: الروابط المستخدمة بـ `ToolchainManager` تفترض معالج **aarch64** (الشائع
   بأغلب أجهزة أندرويد الحديثة). لو تستهدف أجهزة `armeabi-v7a` قديمة لازم تبدّل الرابط.

4. **مشروع Gradle كامل vs مشروع بسيط**: لو المشروع المرفوع فيه `gradlew`، التطبيق يشغله مباشرة
   (الأقوى والأضمن، يدعم أي إعداد Gradle حقيقي). لو المشروع بسيط (مجرد `src/`, `res/`,
   `AndroidManifest.xml` بدون Gradle) يستخدم المسار اليدوي المطابق لسكربت `build_apk.py` السابق.

5. **رخصة Android SDK**: `sdkmanager` يتطلب الموافقة على ترخيص الاستخدام أول مرة — الكود يرسل
   "y" تلقائياً، لكن تأكد انك فعلاً موافق على شروط Google قبل الاستخدام الفعلي/التوزيع.

## التبعيات (في app/build.gradle.kts)
- `org.apache.commons:commons-compress` لفك ضغط tar.gz (JDK) وzip (SDK/المشروع)
