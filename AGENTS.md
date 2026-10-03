# HeliBoard-AI Developer & Agent Guide

Dokumen panduan arsitektur, flow integrasi, konvensi, dan workflow untuk developer / agent yang memelihara proyek **HeliBoard-AI**.

---

## 📌 Ringkasan Proyek

- **Repositori**: `zuher5/HeliBoard-AI`
- **Cabang Utama**: `main`
- **Dasar Fork**: Fork dari HeliBoard / OpenBoard / AOSP LatinIME.
- **Tujuan Utama**: Keyboard Android yang privat, offline-first, dengan integrasi modern:
  - Multi-provider Cloud AI (Gemini, Groq, OpenAI-compatible).
  - Online Voice Input berbasis AI (Groq Whisper `whisper-large-v3-turbo`) dengan fallback ke Input Method bawaan sistem (Gboard voice).
  - Translation Engine hybrid (Offline via LeanBitLab Translation Plugin APK & Google MT Model Packets + Online fallback via Cloud AI).

---

## 🏛️ Arsitektur Komponen

### 1. Voice Input Subsystem
- **Package**: `helium314.keyboard.latin.voice` & `helium314.keyboard.latin.suggestions`
- **Arsitektur**:
  - `VoiceInputManager.kt`: Mengelola state machine perekaman suara (`IDLE`, `RECORDING`, `PROCESSING_FINAL`, `ERROR`). Menangkap buffer PCM 16kHz mono, mengonversi ke file WAV via `AudioUtils.kt`, dan mengirim multipart request ke Groq `/openai/v1/audio/transcriptions`.
  - `VoiceConstants.kt`: Menyimpan preference keys (`voice_provider`, `voice_language`, `voice_smart_punctuation`, `voice_max_duration_seconds`).
  - `VoiceVisualizerView.kt`: View animasi visualizer gelombang suara pada bar saran (`suggestions_strip.xml`).
  - `LatinIME.java`: Menangkap input tombol mic/voice (`KeyCode.VOICE_INPUT`). Jika provider `"system"`, memanggil `mRichImm.switchToShortcutIme(this)`. Jika `"online"`, memanggil `VoiceInputManager`.
  - `VoiceSettingsScreen.kt`: Tampilan setting voice input (dibungkus dalam `Card` Material 3 surfaceContainer dengan konsistensi styling).

### 2. Translation Subsystem
- **Package**: `helium314.keyboard.latin.translation`
- **Arsitektur**:
  - `ITranslationProvider.kt`: Kontrak interface modular versi 2.
  - `TranslationLoader.kt`: Dynamic class loading file APK plugin (`translation_plugin-$abi.apk`) menggunakan `DexClassLoader` dari rilis LeanBitLab Translation Plugin.
  - `TranslationModelImporter.kt` & `TranslationModelUrls.kt`: Pengunduh dan penata paket model bahasa offline dari Google Translate CDN (`dl.google.com/translate/offline/v5`).
  - `ProofreadHelper.kt`: Pengarah logika terjemahan (`translateAsync`). Jika plugin dan model lokal tersedia, terjemahkan secara offline. Jika model belum ada atau plugin mati, lakukan fallback otomatis ke model Cloud AI aktif.
  - `LanguageDetector.kt`: Pendeteksi bahasa sumber cerdas di perangkat (script Unicode analysis + Android TextClassifier).
  - `TranslationSettingsScreen.kt` & `LoadTranslationPluginPreference.kt`: Layar preferensi unduh plugin, pemilihan engine (Plugin vs AI), dan unduh model per-bahasa.

### 3. Cloud AI Proofreading & Custom Keys
- **Package**: `helium314.keyboard.latin.utils`
- **Komponen**:
  - `ProofreadService.kt`: Menangani panggilan API ke Gemini, Groq, dan OpenAI endpoints. Menggunakan `EncryptedSharedPreferences` untuk keamanan token API.
  - `CustomAIKeysScreen.kt`: Mengatur prompt khusus untuk tombol toolbar custom AI 1, 2, dan 3.

---

## 🎨 Standar UI & Styling Settings

1. **Card Container**:
   - Seluruh grup preferensi pada screen kustom wajib dibungkus `Card` dengan `containerColor = MaterialTheme.colorScheme.surfaceContainer` dan margin `padding(horizontal = 16.dp, vertical = 8.dp)`.
2. **PreferenceCategory**:
   - Saat berada di dalam `Card`, panggil `PreferenceCategory(title, showDivider = false)` untuk menghindari garis `HorizontalDivider` yang bertabrakan dengan background card.
3. **Penyusunan Bahasa / Auto Detect**:
   - Jangan menambahkan item auto-detect ganda di daftar dropdown / dialog. Filter item `"auto"` dari array sebelum menambahkan entry auto default.

---

## 🛠️ CI / CD & Build Commands

- **Local Build Requirement**:
  - JDK 21 / 17.
  - Android SDK (minSdk 21, compileSdk 35).
- **Gradle Tasks**:
  ```bash
  # Compile Kotlin debug
  ./gradlew compileDebugKotlin

  # Compile Java debug
  ./gradlew compileDebugJavaWithJavac

  # Assemble APK Debug
  ./gradlew assembleDebug
  ```
- **GitHub Actions**:
  - Workflow file: `.github/workflows/build-debug-apk.yml` dan `build-release-apk.yml`.
  - Berjalan otomatis setiap push ke branch `main`.

---

## ⚠️ Aturan untuk AI Agents

1. **Gaya Komunikasi**: Terse / caveman technical saat memberi penjelasan singkat kepada user.
2. **Prinsip Lazy Senior**:
   - Utamakan stdlib dan dependensi yang sudah ada.
   - Buat diff sekecil dan sebersih mungkin.
   - Hindari abstraksi tak berdasar.
3. **Java-Kotlin Interop**:
   - Saat mendefinisikan callback di class Kotlin yang dipanggil dari `LatinIME.java`, gunakan interface functional Java-friendly seperti `Runnable` atau interface SAM eksplisit agar tidak terjadi error lambda return type di `javac`.
