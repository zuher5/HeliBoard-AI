# ZeeBoard

> 🇮🇩 Bahasa Indonesia | [🇬🇧 English](README.md)

**Keyboard open-source yang menjaga privasi dan dapat dikustomisasi untuk Android — fork dari HeliBoard / AOSP, ditingkatkan dengan fitur cloud AI opsional dan terjemahan langsung di keyboard.**

[![Dapatkan APK dari GitHub](https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png)](https://github.com/zuher5/HeliBoard-AI/releases/latest)

[![Latest Release](https://img.shields.io/github/v/release/zuher5/HeliBoard-AI?style=flat-square&label=Release)](https://github.com/zuher5/HeliBoard-AI/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)

---

## Tentang ZeeBoard

ZeeBoard berawal sebagai fork pribadi dari [HeliBoard](https://github.com/HeliBorg/HeliBoard) dengan satu tujuan: menjaga keyboard tetap **ringan, privat, dan offline secara default** — sembari menambahkan fitur **cloud AI opsional** bagi pengguna yang membutuhkannya.

Filosofi utama:

- **Offline-first.** Tidak ada koneksi internet yang digunakan kecuali Anda secara eksplisit mengonfigurasi provider AI.
- **Kunci Anda, data Anda.** API key disimpan terenkripsi di perangkat (AES-256-GCM). Tidak ada data yang dikumpulkan oleh ZeeBoard.
- **DNA HeliBoard.** Seluruh fleksibilitas kustomisasi, tata letak (layout), kamus, dan tema dari HeliBoard — tetap utuh.
- **AI yang Praktis.** Terjemahkan, proofread, atau jalankan perintah prompt kustom melalui Google Gemini atau endpoint OpenAI-compatible apa pun (HuggingFace, Ollama, OpenRouter, dll.) — langsung dari toolbar keyboard.

---

## Fitur

### Fitur AI (Tambahan ZeeBoard)

- **AI Translate** — Terjemahkan teks yang dipilih atau seluruh kolom teks, langsung di keyboard, melalui Gemini atau provider OpenAI-compatible apa pun.
- **HF / AI Compatible** — Gunakan HuggingFace Inference, Ollama, OpenRouter, atau server LLM lokal lainnya (`http://192.168.x.x:11434/v1`).
- **Gemini (Google)** — Google Gemini melalui free tier Google AI Studio (API key berawalan `AIzaSy...`).
- **Custom AI Keys (1–10)** — Pasang prompt kustom ke hingga 10 tombol toolbar; gunakan tagar seperti `#proofread`, `#summarize`, `#editor`, `#paraphrase` untuk menentukan perilaku AI.
- **Penyimpanan Kunci Terenkripsi** — API key disimpan menggunakan `EncryptedSharedPreferences` (AES-256-GCM), tidak pernah dicatat (log) atau dikirim ke mana pun selain ke endpoint provider yang Anda atur.
- **Pemilih Bahasa di Keyboard** — Pilih bahasa target terjemahan langsung dari antarmuka keyboard.

### Fitur HeliBoard (Bawaan)

- Kustomisasi tema keyboard (gaya, warna, dan gambar latar belakang)
- Tambah kamus untuk saran kata dan pemeriksaan ejaan — [unduh di sini](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries)
- Pencarian emoji (memerlukan [kamus emoji](https://codeberg.org/Helium314/aosp-dictionaries))
- Kustomisasi [tata letak (layout)](layouts.md)
- Kustomisasi tata letak khusus (simbol, angka, tombol fungsi)
- Pengetikan multibahasa
- Glide typing / ketik geser *(pustaka pihak ketiga, opsional)*
- Riwayat papan klip (clipboard)
- Mode satu tangan (one-handed), keyboard terpisah (split), dan papan angka (number pad)
- Tema otomatis siang/malam (Android 10+) dan dynamic colors Material You (Android 12+)
- Cadangkan dan pulihkan setelan serta data kata yang dipelajari

---

## Unduh (Download)

**[Dapatkan APK terbaru dari GitHub Releases](https://github.com/zuher5/HeliBoard-AI/releases/latest)**

Dua varian build:
- `ZeeBoard_x.x.x-release.apk` — build rilis teroptimasi (minified)
- `ZeeBoard_x.x.x-nouserlib.apk` — sama dengan release, tanpa gesture library bawaan pengguna

---

## Panduan Pengaturan AI

### Gemini (Google)

1. Dapatkan API key gratis di [Google AI Studio](https://aistudio.google.com/apikey) (diawali `AIzaSy...`)
2. Di ZeeBoard: **Setelan → AI Integration**
3. Atur provider ke **Gemini (Google)**, tempelkan API key Anda
4. Anda dapat mengubah model jika diinginkan (default: `gemini-2.5-flash`)

### HF / AI Compatible (HuggingFace, Ollama, OpenRouter, …)

1. Dapatkan API key dari provider Anda:
   - HuggingFace: [hf.co/settings/tokens](https://huggingface.co/settings/tokens) (diawali `hf_...`)
   - OpenRouter: [openrouter.ai/keys](https://openrouter.ai/keys)
   - Ollama (lokal): tidak perlu key, atur endpoint ke `http://192.168.x.x:11434/v1`
2. Di ZeeBoard: **Setelan → AI Integration**
3. Atur provider ke **HF / AI Compatible**, masukkan API key dan URL endpoint dasar
4. Pilih model — untuk HuggingFace contohnya `Qwen/Qwen2.5-72B-Instruct` atau `meta-llama/Llama-3.1-8B-Instruct`

### Custom AI Keys

- Masuk ke **Setelan → AI Integration → Custom AI Keys**
- Masukkan prompt ke salah satu dari 10 tombol (misal `Terjemahkan ke bahasa Inggris`, `Perbaiki tata bahasa`, `Ringkas teks`)
- Gunakan tagar berikut pada prompt untuk mengatur format output:
  - `#proofread` — perbaiki tata bahasa & ejaan, hanya tampilkan hasil
  - `#editor` — edit teks tanpa basa-basi / teks pembuka
  - `#summarize` — ringkas teks dengan padat
  - `#paraphrase` — tulis ulang dengan makna yang sama
  - `#expand` — tambahkan detail pada teks
  - `#append` — tambahkan hasil ke akhir teks alih-alih menimpa
- Aktifkan tombol-tombol ini di toolbar Anda: **Setelan → Toolbar → Select toolbar keys**

---

## Privasi

ZeeBoard tanpa konfigurasi AI = **tanpa akses internet sama sekali**, persis seperti HeliBoard aslinya.

Saat AI dikonfigurasi:
- Hanya teks yang Anda picu secara sengaja yang akan dikirim (teks yang disorot, atau seluruh kolom jika tidak ada seleksi)
- Endpoint yang Anda pilih hanya menerima teks tersebut beserta API key Anda — tidak ada data yang masuk ke server ZeeBoard
- API key disimpan terenkripsi di perangkat, tidak pernah muncul di sistem log
- `network_security_config.xml` mengizinkan cleartext traffic (HTTP) hanya demi mendukung endpoint server lokal (seperti Ollama)

---

## Lisensi

ZeeBoard (sebagai fork dari HeliBoard / OpenBoard) dilisensikan di bawah **GNU General Public License v3.0**.

> Izin lisensi copyleft ini mewajibkan ketersediaan kode sumber lengkap dari karya berlisensi dan modifikasinya, termasuk karya lebih besar yang menggunakan karya berlisensi, di bawah lisensi yang sama. Pemberitahuan hak cipta dan lisensi harus dilestarikan.

Lihat file [LICENSE](LICENSE).

Karena aplikasi ini berbasis pada AOSP Keyboard berlisensi Apache 2.0, file lisensi [Apache 2.0](LICENSE-Apache-2.0) juga disediakan.
Ikon dilisensikan di bawah [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). File lisensi [Creative Commons](LICENSE-CC-BY-SA-4.0) juga disertakan.

---

## Kredit

- **[HeliBoard](https://github.com/HeliBorg/HeliBoard)** oleh Helium314 — keyboard luar biasa yang menjadi basis fork ini
- **[LeanType](https://github.com/LeanBitLab/LeanType)** oleh LeanBitLab — inspirasi arsitektur provider AI
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- Ikon oleh [Fabian OvrWrt](https://github.com/FabianOvrWrt) dengan kontribusi dari [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
