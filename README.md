# ZeeBoard

> [🇮🇩 Bahasa Indonesia](README_ID.md) | 🇬🇧 English

**A privacy-conscious, customizable open-source keyboard for Android — forked from HeliBoard / AOSP, enhanced with optional cloud AI and on-device translation.**

[![Get APK from GitHub](https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png)](https://github.com/zuher5/HeliBoard-AI/releases/latest)

[![Latest Release](https://img.shields.io/github/v/release/zuher5/HeliBoard-AI?style=flat-square&label=Release)](https://github.com/zuher5/HeliBoard-AI/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)

---

## About ZeeBoard

ZeeBoard started as a personal fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) with one goal: keep the keyboard **lightweight, private, and offline by default** — while adding **optional cloud AI** for those who want it.

The core philosophy:

- **Offline-first.** No internet is used unless you explicitly configure an AI provider.
- **Your key, your data.** API keys are stored encrypted on-device (AES-256-GCM). Nothing is collected by ZeeBoard.
- **HeliBoard DNA.** All the customization, layouts, dictionaries, and themes from HeliBoard — untouched.
- **Practical AI.** Translate, proofread, or run custom prompts via Google Gemini or any OpenAI-compatible endpoint (HuggingFace, Ollama, OpenRouter, etc.) — right from the keyboard toolbar.

---

## Features

### AI Features (ZeeBoard additions)

- **AI Translate** — translate selected text or entire field, inline, via Gemini or any OpenAI-compatible provider
- **HF / AI Compatible** — use HuggingFace Inference, Ollama, OpenRouter, or any local LLM server (`http://192.168.x.x:11434/v1`)
- **Gemini (Google)** — Google Gemini via AI Studio free tier (`AIzaSy...` key)
- **Custom AI Keys (1–10)** — assign custom prompts to up to 10 toolbar keys; use hashtags like `#proofread`, `#summarize`, `#editor`, `#paraphrase` to define AI behavior
- **Encrypted key storage** — API keys stored in `EncryptedSharedPreferences` (AES-256-GCM), never logged or sent anywhere except the provider endpoint you configured
- **In-keyboard language picker** — choose translation target language directly from the keyboard

### HeliBoard Features (inherited)

- Customizable keyboard themes (style, colors, background image)
- Add dictionaries for suggestions and spell check — [get them here](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries)
- Emoji search (requires [emoji dictionary](https://codeberg.org/Helium314/aosp-dictionaries))
- Customize keyboard [layouts](layouts.md)
- Customize special layouts (symbols, number, functional keys)
- Multilingual typing
- Glide typing *(closed source library, optional)*
- Clipboard history
- One-handed mode, Split keyboard, Number pad
- Day/night auto theme (Android 10+), dynamic colors (Android 12+)
- Backup and restore settings and learned word data

---

## Download

**[Get the latest APK from GitHub Releases](https://github.com/zuher5/HeliBoard-AI/releases/latest)**

Two build variants:
- `ZeeBoard_x.x.x-release.apk` — minified release build
- `ZeeBoard_x.x.x-nouserlib.apk` — same as release, without user-provided gesture library

---

## AI Setup Guide

### Gemini (Google)

1. Get a free API key at [Google AI Studio](https://aistudio.google.com/apikey) (starts with `AIzaSy...`)
2. In ZeeBoard: **Settings → AI Integration**
3. Set provider to **Gemini (Google)**, paste your API key
4. Optionally change the model (default: `gemini-2.5-flash`)

### HF / AI Compatible (HuggingFace, Ollama, OpenRouter, …)

1. Get an API key from your provider:
   - HuggingFace: [hf.co/settings/tokens](https://huggingface.co/settings/tokens) (starts with `hf_...`)
   - OpenRouter: [openrouter.ai/keys](https://openrouter.ai/keys)
   - Ollama (local): no key needed, set endpoint to `http://192.168.x.x:11434/v1`
2. In ZeeBoard: **Settings → AI Integration**
3. Set provider to **HF / AI Compatible**, enter API key and base URL endpoint
4. Choose model — for HuggingFace try `Qwen/Qwen2.5-72B-Instruct` or `meta-llama/Llama-3.1-8B-Instruct`

### Custom AI Keys

- Go to **Settings → AI Integration → Custom AI Keys**
- Assign a prompt to any of the 10 keys (e.g. `Translate to English`, `Fix grammar`, `Summarize`)
- Use hashtags in prompts to set behavior:
  - `#proofread` — fix grammar/spelling, output only result
  - `#editor` — edit text, no filler
  - `#summarize` — concise summary
  - `#paraphrase` — rephrase with same meaning
  - `#expand` — add detail
  - `#append` — append result instead of replacing
- Add these keys to your toolbar: **Settings → Toolbar → Select toolbar keys**

---

## Privacy

ZeeBoard with no AI configured = **zero internet usage**, same as upstream HeliBoard.

When AI is configured:
- Only the text you explicitly trigger AI on is sent (selected text, or whole field if no selection)
- The endpoint you chose receives the text + your API key — nothing goes to ZeeBoard
- API keys are stored encrypted on-device, never appear in logs
- `network_security_config.xml` allows cleartext only for local endpoints (Ollama)

---

## License

ZeeBoard (as a fork of HeliBoard / OpenBoard) is licensed under **GNU General Public License v3.0**.

> Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved.

See [LICENSE](LICENSE).

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided.
The icon is licensed under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). A [license file](LICENSE-CC-BY-SA-4.0) is also included.

---

## Credits

- **[HeliBoard](https://github.com/HeliBorg/HeliBoard)** by Helium314 — the excellent keyboard this fork is built on
- **[LeanType](https://github.com/LeanBitLab/LeanType)** by LeanBitLab — inspiration for the AI provider architecture
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
