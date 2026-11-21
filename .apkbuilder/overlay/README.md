# Liquid Tabs

شريط سفلي زجاجي (LiquidBottomTabs) فوق مكتبة backdrop 2.0.0، مع انتقالات HyperSpring المستخرجة.

## الملفات
- `app/src/main/java/com/kyant/backdrop/catalog/**` : ملفات الشريط كما أرسلتها (بدون تعديل).
- `TabReveal.kt` : انتقال التبويبات (موجة زجاجية) — منقول من TabReveal.java بنفس الشيدرات الأصلية والأرقام.
- `PushMorph.kt` : فتح صفحة فرعية — منقول من PushMorph.java.
- `SplitTitle.kt`, `Motion.kt` : العنوان الذي يدخل حرفاً حرفاً + الدخول المتتالي للعناصر (Motion.cascade).
- `Screens.kt` : 3 تبويبات (Home / Explore / Profile) فيها عناصر كثيرة، وصفوف تفتح صفحة فرعية.

## التشغيل
- Android Studio: افتح المجلد → Sync → Run.
- أو ارفع المجلد إلى GitHub: ملف `.github/workflows/build.yml` يبني APK ويرفعه في Actions → Artifacts.

## ملاحظات
- موجة الزجاج الكاملة تحتاج أندرويد 13+، والتمويه أندرويد 12+. قبل ذلك تظهر الدائرة والحلقة فقط.
- README الأصلي للشريط يذكر backdrop 2.0.1؛ هنا 2.0.0 كما طلبت. إن ظهر خطأ API غيّر الرقم في app/build.gradle.kts.
