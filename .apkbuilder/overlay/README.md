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

## ⚠️ قيد حرج: تنفيذ أدوات منزّلة وقت التشغيل غير مسموح على أندرويد 10+

من أندرويد 10، مجلد `filesDir` الخاص بالتطبيق مركّب بخاصية **noexec** — يعني ما تقدر
تنفذ أي ملف نزّلته وقت التشغيل حتى لو سويت `chmod +x`. هذا يفشّل `sdkmanager`,
`javac`, `aapt2`, `d8`, `zipalign`, `apksigner` كلها بخطأ `Permission denied (error=13)`
لما تحاول تشغلها من `ToolchainManager`/`ProjectBuilder` بصيغتهم الحالية.

**الكود الوحيد المسموح تنفيذه على أندرويد:**
1. مكتبات `.so` مرفقة أصلاً داخل الـ APK بمجلد `jniLibs/` (تاخذ صلاحية تنفيذ وقت التثبيت).
2. كود يتحمّل ديناميكياً عبر `System.loadLibrary()`.

**الحلول الممكنة (تحتاج إعادة تصميم جزء من `ToolchainManager`/`ProjectBuilder`):**
- **تفويض Termux**: يرسل التطبيق أمر البناء لتطبيق Termux (عبر `RUN_COMMAND` Intent) بدل
  تنفيذ الأدوات بنفسه؛ Termux أصلاً حل مشكلة noexec بتغليف أدواته كـ `.so` وهمية.
- **تضمين الأدوات مسبقاً**: حزم نسخة/نسختين ثابتة من JDK/SDK كملفات `.so` داخل `jniLibs/`
  وقت بناء هذا التطبيق نفسه، بدل تنزيلها وقت التشغيل — يفقد مرونة "أي نسخة تلقائياً"
  لكن يحل مشكلة التنفيذ نهائياً.

## التوقيع ورفع رقم الإصدار (لنشر تحديثات بدل تثبيت منفصل)

- `versionCode` لازم يزيد **بكل إصدار جديد** (الحين 2) حتى يتعرف أندرويد عليه كـ"تحديث".
- لازم تبقى كل النسخ موقعة **بنفس المفتاح** — لو تغير المفتاح، التثبيت كتحديث يفشل
  برسالة `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

### توليد مفتاح توقيع (مرة وحدة، واحتفظ فيه للأبد)
```bash
keytool -genkeypair -v -keystore release.jks -alias release \
  -keyalg RSA -keysize 2048 -validity 10000
```

### الاستخدام محلياً
ضيف بـ `gradle.properties` (لا ترفعه لـ Git):
```
RELEASE_KEYSTORE_PATH=/absolute/path/release.jks
RELEASE_KEYSTORE_PASSWORD=xxxx
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=xxxx
```

### الاستخدام بـ GitHub Actions
1. حوّل الـ keystore لـ base64: `base64 -w0 release.jks > release.b64`
2. ضيف أسرار بالمستودع (Settings → Secrets → Actions):
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. بخطوة قبل البناء بملف الـ workflow:
   ```yaml
   - name: Decode keystore
     run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > release.jks

   - name: Build signed release APK
     run: ./gradlew assembleRelease
     env:
       KEYSTORE_PATH: ${{ github.workspace }}/release.jks
       KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
       KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
       KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
   ```
4. ارفع `app/build/outputs/apk/release/app-release.apk` كـ artifact، وزيد
   `versionCode` بكل مرة قبل الإصدار الجديد.
