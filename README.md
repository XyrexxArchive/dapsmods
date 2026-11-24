# DapsMods

**DapsMods** adalah aplikasi Android all-in-one yang dibuat dengan Kotlin.

- **Versi awal:** 0.1.0
- **Developer:** xyrcodex
- **Minimum Android:** Android 6.0 (API 23)
- **Build JDK:** 17

## Fitur

- Music search, streaming, dan lyrics.
- Facebook video downloader.
- Tema Terang, Gelap, Liquid Glass, dan Brutalism.
- UI Jetpack Compose yang ringan.

## Build lokal

```bash
gradle assembleDebug
```

APK debug tersedia di:

`app/build/outputs/apk/debug/app-debug.apk`

## Automatic GitHub Releases

GitHub Actions akan membuat Release dan mengunggah APK secara otomatis ketika tag versi baru dengan format SemVer dibuat dan dipush.

Contoh:

```bash
git tag v0.1.1
git push origin v0.1.1
```

Setelah workflow selesai, buka tab **Releases** di repository. File akan tersedia dengan nama `DapsMods-0.1.1.apk`.

- Push biasa ke branch `main` tetap menjalankan debug build dan menyimpan APK sebagai Actions artifact.
- Push tag `vX.Y.Z` menjalankan release build, memberi versionName dari tag, menghitung versionCode, menandatangani APK menggunakan debug keystore bawaan Gradle, lalu menerbitkan GitHub Release.
- Tidak perlu membuat Release secara manual.

> Untuk distribusi Play Store atau update jangka panjang, ganti debug signing dengan private release keystore yang disimpan sebagai GitHub Actions secrets. APK dari workflow ini sudah dapat dipasang untuk pengujian dan sideload, tetapi debug key bukan kunci signing produksi permanen.