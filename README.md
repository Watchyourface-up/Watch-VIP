# DevVault

دفتر محلي لروابط المطورين، مبني باستخدام Expo وReact Native وTypeScript لأجهزة Android وiOS.

## التشغيل
يتطلب Node.js وnpm. Android يحتاج Android SDK، وiOS يحتاج macOS وXcode.

    npm install
    npm run typecheck
    npm test
    npm run lint
    npx expo prebuild --platform android
    npx expo run:android

على macOS يمكن بناء iOS عبر `npx expo prebuild --platform ios && npx expo run:ios`.

## الخصوصية والأمان
- لا حسابات ولا مزامنة سحابية، ولا تُرسل الروابط إلى خادم من التطبيق.
- يقبل التطبيق HTTP وHTTPS فقط، ويفتح الروابط عبر تطبيق النظام الخارجي.
- يستخدم المشروع expo-sqlite مع خيار SQLCipher؛ يفحص `PRAGMA cipher_version` ويرفض إنشاء قاعدة بيانات إذا لم يتوفر التشفير.
- المفتاح عشوائي بطول 256 بت ويُحفظ في SecureStore/Keychain ولا يُكتب في المستودع أو السجلات.
- SQLCipher غير متاح في Expo Go؛ يجب اختبار بناء أصلي. لا يعتبر الإعداد وحده دليلًا على نجاح الحماية.
- اكتشاف الروابط الحساسة استدلالي وقد يفوّت حالات؛ لا تحفظ كلمات مرور أو رموز وصول في الروابط أو الوصف.
- لا يمكن ضمان منع جميع لقطات الشاشة على iOS. حظرها على Android يتطلب تكاملًا أصليًا إضافيًا.
- لم يُعلن نجاح أي بناء أصلي أو اختبار على جهاز ما لم يتم تشغيله فعليًا.

TypeScript هي اللغة الأساسية. SQL للمخطط، وPython/Bash لأدوات الفحص والأتمتة. لا نضيف Kotlin أو Swift أو Rust شكليًا.
