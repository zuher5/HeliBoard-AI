/*
 * Copyright (C) 2026 LeanBitLab
 * adapted for HeliBoard (AI translate & proofread)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.latin.translation.TranslationModelImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Helper to run AI & Plugin translate/proofread operations asynchronously, with callbacks
 * executed on the main thread (used from Java code in InputLogic).
 */
object ProofreadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentJob: Job? = null

    val isOperationInProgress: Boolean
        get() = currentJob?.isActive == true

    interface AiCallback {
        fun onSuccess(result: String)
        fun onError(message: String)
    }

    private fun performAsyncOperation(
        context: Context,
        text: String,
        noTextErrorResId: Int,
        allowEmptyInput: Boolean = false,
        skipApiKeyCheck: Boolean = false,
        apiCall: suspend (ProofreadService) -> Result<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val service = ProofreadService(context)
        if (!skipApiKeyCheck && !service.hasApiKey()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(R.string.ai_no_api_key), true)
            }
            return
        }
        if (!allowEmptyInput && text.isBlank()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(noTextErrorResId), true)
            }
            return
        }
        if (isOperationInProgress) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(context.getString(R.string.ai_operation_in_progress), true)
            }
            return
        }

        currentJob = scope.launch {
            val result = apiCall(service)
            mainHandler.post {
                currentJob = null
                result.fold(
                    onSuccess = { onSuccess(it) },
                    onFailure = { error ->
                        onError(error.message ?: "Unknown error")
                        KeyboardSwitcher.getInstance().showToast(error.message ?: "Error", false)
                    }
                )
            }
        }
    }

    @JvmStatic
    fun translateAsync(context: Context, text: String, callback: AiCallback) {
        val prefs = context.prefs()
        val translationEngine = prefs.getString("translation_engine", "plugin") ?: "plugin"
        val isOfflineOnly = translationEngine == "plugin"
        val isOnlineOnly = translationEngine == "ai"

        val hasPlugin = TranslationLoader.hasPlugin(context)
        val usePlugin = !isOnlineOnly && hasPlugin

        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.translate_no_text,
            skipApiKeyCheck = usePlugin || isOfflineOnly,
            apiCall = { service ->
                val pluginProvider = if (usePlugin) TranslationLoader.getProvider(context) else null
                val targetLang = service.getTargetLanguage()
                val targetLangCode = getLangCode(targetLang)
                val configuredSourceLang = service.getSourceLanguage()
                val sourceLangCode = if (configuredSourceLang.isNotBlank() && !configuredSourceLang.equals("auto", ignoreCase = true)) {
                    getLangCode(configuredSourceLang)
                } else {
                    LanguageDetector.detect(context, text, targetLangCode)
                }

                val hasAiConfigured = service.hasApiKey()

                if (pluginProvider != null && pluginProvider.isAvailable()) {
                    val missingModels = mutableListOf<String>()
                    if (sourceLangCode != "auto" && sourceLangCode != "en") {
                        val isDownloaded = try {
                            pluginProvider.isModelDownloaded(sourceLangCode)
                        } catch (_: Throwable) {
                            false
                        } || TranslationModelImporter.isModelInstalled(context, sourceLangCode)
                        if (!isDownloaded) {
                            missingModels.add(sourceLangCode)
                        }
                    }
                    if (targetLangCode != "en" && !missingModels.contains(targetLangCode)) {
                        val isDownloaded = try {
                            pluginProvider.isModelDownloaded(targetLangCode)
                        } catch (_: Throwable) {
                            false
                        } || TranslationModelImporter.isModelInstalled(context, targetLangCode)
                        if (!isDownloaded) {
                            missingModels.add(targetLangCode)
                        }
                    }

                    if (missingModels.isNotEmpty()) {
                        val missingNames = missingModels.joinToString(", ") { getLanguageDisplayName(context, it) }
                        val errorMsg = context.getString(R.string.translation_specific_model_not_downloaded, missingNames)
                        if (isOfflineOnly || !hasAiConfigured) {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(errorMsg, true)
                            }
                            return@performAsyncOperation Result.failure(Exception(errorMsg))
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_switching_to_ai, missingNames),
                                    false
                                )
                            }
                            Log.i(TAG, "Plugin model for $missingNames not downloaded, falling back to built-in AI")
                            return@performAsyncOperation service.translate(text)
                        }
                    }

                    try {
                        Log.i(TAG, "Translating via Translation Plugin (source: $sourceLangCode, target: $targetLangCode)")
                        val result = pluginProvider.translate(text, targetLangCode, sourceLangCode)
                        if (result.isNotBlank()) {
                            Result.success(result)
                        } else if (isOfflineOnly || !hasAiConfigured) {
                            Result.failure(Exception("Plugin translation returned empty result"))
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_plugin_fallback_to_ai),
                                    false
                                )
                            }
                            service.translate(text)
                        }
                    } catch (e: Throwable) {
                        val errorMsg = if (e.message?.contains("not installed for en", ignoreCase = true) == true) {
                            val missingCode = if (sourceLangCode != "en") sourceLangCode else targetLangCode
                            val missingName = getLanguageDisplayName(context, missingCode)
                            context.getString(R.string.translation_specific_model_not_downloaded, missingName)
                        } else {
                            e.message ?: "Translation error"
                        }
                        if (isOfflineOnly || !hasAiConfigured) {
                            Result.failure(Exception(errorMsg, e))
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_plugin_fallback_to_ai),
                                    false
                                )
                            }
                            service.translate(text)
                        }
                    }
                } else if (isOfflineOnly || !hasAiConfigured) {
                    mainHandler.post {
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(R.string.translation_model_not_downloaded),
                            true
                        )
                    }
                    Result.failure(Exception("Translation plugin not available"))
                } else {
                    if (!isOnlineOnly) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.translation_plugin_fallback_to_ai),
                                false
                            )
                        }
                    }
                    service.translate(text)
                }
            },
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    @JvmStatic
    fun customAsync(context: Context, text: String, prompt: String, callback: AiCallback) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.translate_no_text,
            allowEmptyInput = true,
            apiCall = { it.customAi(text, prompt) },
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    /** cancel the current operation, if any */
    @JvmStatic
    fun cancelCurrentOperation() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            currentJob = null
        }
    }

    fun getLanguageDisplayName(context: Context, code: String): String {
        if (code.equals("keyboard", ignoreCase = true) || code.equals("default", ignoreCase = true)) {
            return context.getString(R.string.translate_language_keyboard)
        }
        val names = context.resources.getStringArray(R.array.translate_language_names)
        val codes = context.resources.getStringArray(R.array.translate_language_codes)
        val index = codes.indexOfFirst { it.equals(code, ignoreCase = true) }
        if (index != -1 && index < names.size) {
            return names[index]
        }
        val localeName = java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
        return if (localeName.isNotBlank()) localeName else code.uppercase()
    }

    fun getLangCode(rawNameOrCode: String): String {
        val trimmed = rawNameOrCode.trim()
        if (trimmed.isEmpty() || trimmed.equals("keyboard", ignoreCase = true) || trimmed.equals("default", ignoreCase = true)) {
            return "en"
        }
        if (trimmed.length in 2..3 && trimmed.all { it.isLetter() }) return trimmed.lowercase()
        if (trimmed.contains("-") || trimmed.contains("_")) {
            val prefix = trimmed.split('-', '_')[0].trim().lowercase()
            if (prefix.length in 2..3 && prefix.all { it.isLetter() }) return prefix
        }
        val lower = trimmed.lowercase()
        return when (lower) {
            "english", "anglais", "englisch", "inglés", "inglese", "inglês", "английский", "انگریزی", "الإنجليزية", "ഇംഗ്ലീഷ്", "αγγλικά", "İngilizce" -> "en"
            "spanish", "espagnol", "spanisch", "español", "spagnolo", "espanhol", "испанский", "ہسپانوی", "الإسبانية", "സ്പാനിഷ്", "ισπανικά", "İspanyolca" -> "es"
            "french", "français", "französisch", "francés", "francese", "francês", "французский", "فرانسیسی", "الفرنسية", "ഫ്രഞ്ച്", "γαλλικά", "Fransızca" -> "fr"
            "german", "allemand", "deutsch", "alemán", "tedesco", "alemão", "немецкий", "جرمن", "الألمانية", "ജർമ്മൻ", "γερμανικά", "Almanca" -> "de"
            "italian", "italien", "italienisch", "italiano", "итальянский", "اطالوی", "الإيطالية", "ഇറ്റാലിയൻ", "ιταλικά", "İtalyanca" -> "it"
            "portuguese", "portugais", "portugiesisch", "portugués", "portoghese", "português", "португальский", "پرتگالی", "البرتغالية", "പോർച്ചുഗീസ്", "πορτογαλικά", "Portekizce" -> "pt"
            "chinese", "chinese (simplified)", "chinese (traditional)", "chinois", "chinois (simplifié)", "chinois (traditionnel)", "chinesisch", "chino", "cinese", "chinês", "китайский", "چینی", "الصينية", "ചൈനീസ്", "κινεζικά", "Çince" -> "zh"
            "japanese", "japonais", "japanisch", "japonés", "giapponese", "japonês", "японский", "جاپانی", "اليابانية", "ജാപ്പനീസ്", "ιαπωνικά", "Japonca" -> "ja"
            "korean", "coréen", "koreanisch", "coreano", "корейский", "کوریائی", "الكورية", "കൊറിയൻ", "κορεατικά", "Korece" -> "ko"
            "arabic", "arabe", "arabisch", "árabe", "arabo", "арабский", "عربی", "العربية", "അറബിക്", "αραβικά", "Arapça" -> "ar"
            "russian", "russe", "russisch", "ruso", "russo", "русский", "روسی", "الروسية", "റഷ്യൻ", "ρωσικά", "Rusça" -> "ru"
            "hindi", "indien", "индийский", "хинди", "ہندی", "الهندية", "ഹിന്ദി", "χίντι", "Hintçe" -> "hi"
            "bengali", "bengalí", "бенгальский", "بنگالی", "البنغالية", "ബംഗാളി", "μπενγκάλι", "Bengalce" -> "bn"
            "indonesian", "indonésien", "indonesisch", "indonesio", "indonesiano", "индонезийский", "انڈونیشیائی", "الإندونيسية", "ഇന്തോനേഷ്യൻ", "ινδονησιακά", "Endonezce" -> "id"
            "dutch", "néerlandais", "niederländisch", "holandés", "olandese", "holandês", "нидерландский", "голландский", "ولندیزی", "الهولندية", "ഡച്ച്", "ολλανδικά", "Felemenkçe" -> "nl"
            "turkish", "turc", "türkisch", "turco", "турецкий", "ترکی", "التركية", "ടർക്കിഷ്", "τουρκικά", "Türkçe" -> "tr"
            "polish", "polonais", "polnisch", "polaco", "polacco", "польский", "پولش", "البولندية", "പോളിഷ്", "πολωνικά", "Lehçe" -> "pl"
            "ukrainian", "ukrainien", "ukrainisch", "ucraniano", "ucraino", "украинский", "یوکرائنی", "الأوكرانية", "ഉക്രേനിയൻ", "ουκρανικά", "Ukraynaca" -> "uk"
            "swedish", "suédois", "schwedisch", "sueco", "svedese", "шведский", "سویڈش", "السويدية", "സ്വീഡിഷ്", "σουηδικά", "İsveççe" -> "sv"
            "danish", "danois", "dänisch", "danés", "danese", "dinamarquês", "датский", "ڈینش", "الدنماركية", "ഡാനിഷ്", "δανικά", "Danca" -> "da"
            "norwegian", "norvégien", "norwegisch", "noruego", "norvegese", "norueguês", "норвежский", "نارویجن", "النرويجية", "നോർവീജിയൻ", "νορβηγικά", "Norveççe" -> "no"
            "finnish", "finnois", "finnisch", "finlandés", "finlandese", "finlandês", "финский", "فنش", "الفنلندية", "ഫിന്നിഷ്", "φινλανδικά", "Fince" -> "fi"
            "greek", "grec", "griechisch", "griego", "greco", "grego", "греческий", "یونانی", "اليونانية", "ഗ്രീക്ക്", "ελληνικά", "Yunanca" -> "el"
            "hebrew", "hébreu", "hebräisch", "hebreo", "ebraico", "hebraico", "иврит", "عبرانی", "العبرية", "ഹീബ്രു", "εβραϊκά", "İbranice" -> "he"
            "thai", "thaï", "thailändisch", "tailandés", "thailandese", "tailandês", "тайский", "تھائی", "التايلاندية", "തായ്", "ταϊλανδικά", "Tayca" -> "th"
            "vietnamese", "vietnamien", "vietnamesisch", "vietnamita", "вьетнамский", "ویتنامی", "الفيتنامية", "വിയറ്റ്നാമീസ്", "βιετναμέζικα", "Vietnamca" -> "vi"
            "tamil", "tamoul", "тамильский", "تامل", "التاميلية", "തമിഴ്", "ταμίλ", "Tamilce" -> "ta"
            "telugu", "télougou", "телугу", "تیلگو", "التيلوغوية", "തെലുങ്ക്", "τελούγκου", "Teluguca" -> "te"
            "marathi", "marathe", "маратхи", "مراٹھی", "الماراثية", "മറാത്തി", "μαράθι", "Marathice" -> "mr"
            "gujarati", "goudjarati", "гуджарати", "گجراتی", "الغوجاراتية", "ഗുജറാത്തി", "γκουτζαράτι", "Guceratça" -> "gu"
            "kannada", "каннада", "کنڑ", "الكانادية", "കന്നഡ", "κανάντα", "Kannadaca" -> "kn"
            "malayalam", "малаялам", "млиалм", "المالايالامية", "മലയാളം", "μαλαγιαλάμ", "Malayalamca" -> "ml"
            "urdu", "ourdou", "урду", "اردو", "الأردية", "ഉർദു", "ούρντου", "Urduca" -> "ur"
            "persian (farsi)", "persian", "farsi", "persan (farsi)", "persan", "персидский", "فارسی", "الفارسية", "പേർഷ്യൻ", "περσικά", "Farsça" -> "fa"
            "swahili", "souahéli", "суахили", "سواحلی", "السواحيلية", "സ്വാഹിലി", "σουαχίλι", "Svahilice" -> "sw"
            "romanian", "roumain", "rumänisch", "rumano", "rumeno", "romeno", "румынский", "رومانیہ", "الرومانية", "റൊമാനിയൻ", "ρουμανικά", "Romence" -> "ro"
            "czech", "tchèque", "tschechisch", "checo", "ceco", "чешский", "چیک", "التشيكية", "ചെക്ക്", "τσέχικα", "Çekçe" -> "cs"
            "hungarian", "hongrois", "ungarisch", "húngaro", "ungherese", "венгерский", "ہنگری", "المجرية", "ഹംഗേറിയൻ", "ουγγρικά", "Macarca" -> "hu"
            "filipino (tagalog)", "tagalog", "filipino", "philippin (tagalog)", "тагальский", "فلپائنی", "الفلبينية", "ഫിലിപ്പിനോ", "φιλιππινέζικα", "Filipince" -> "tl"
            "malay", "malais", "malaiisch", "malayo", "malese", "малайский", "ملائی", "الملايوية", "മലായ്", "μαλαισιανά", "Malayca" -> "ms"
            "serbian", "serbe", "serbisch", "serbio", "сербский", "سربین", "الصربية", "സെർബിയൻ", "σερβικά", "Sırpça" -> "sr"
            "croatian", "croate", "kroatisch", "croata", "хорватский", "کروشین", "الكرواتية", "ക്രൊയേഷ്യൻ", "κροατικά", "Hırvatça" -> "hr"
            "bulgarian", "bulgare", "bulgarisch", "búlgaro", "болгарский", "بلغاریائی", "البلغارية", "ബൾഗേറിയൻ", "βουλγαρικά", "Bulgarca" -> "bg"
            "slovak", "slovaque", "slowakisch", "eslovaco", "словацкий", "سلوواک", "السلوفاكية", "സ്ലോവാക്", "σλοβακικά", "Slovakça" -> "sk"
            "slovenian", "slovène", "slowenisch", "esloveno", "словенский", "سلووین", "السلوفينية", "സ്ലൊവേനിയൻ", "σλοβενικά", "Slovence" -> "sl"
            "lithuanian", "lituanien", "litauisch", "lituano", "литовский", "لتھواینین", "الليتوانية", "ലിത്വാനിയൻ", "λιθουανικά", "Litvanca" -> "lt"
            "latvian", "letton", "lettisch", "letón", "латышский", "لاطویائی", "اللاتفية", "ലാത്വിയൻ", "λετονικά", "Letonca" -> "lv"
            "estonian", "estonien", "estnisch", "estonio", "эстонский", "اسٹونین", "الإستونية", "എസ്റ്റോണിയൻ", "εσθονικά", "Estonca" -> "et"
            "catalan", "catalán", "katalanisch", "каталанский", "کیٹالان", "الكتالانية", "കറ്റാലൻ", "καταλανικά", "Katalanca" -> "ca"
            "basque", "baskisch", "vasco", "euskera", "баскский", "باسکی", "الباسكية", "ബാസ്ക്", "βασκικά", "Baskça" -> "eu"
            "afrikaans" -> "af"
            "albanian", "albanais", "albanisch", "albanés", "албанский" -> "sq"
            "belarusian", "biélorusse", "belarussisch", "bielorruso", "белорусский" -> "be"
            "esperanto" -> "eo"
            "galician", "galicien", "galizisch", "gallego", "галисийский" -> "gl"
            "georgian", "géorgien", "georgisch", "georgiano", "грузинский" -> "ka"
            "haitian creole", "haitian", "haïtien" -> "ht"
            "icelandic", "islandais", "isländisch", "islandés", "исландский" -> "is"
            "irish", "irlandais", "irisch", "irlandés", "ирландский" -> "ga"
            "macedonian", "macédonien", "mazedonisch", "macedonio", "македонский" -> "mk"
            "maltese", "maltais", "maltesisch", "maltés", "мальтийский" -> "mt"
            "welsh", "gallois", "walisisch", "galés", "валлийский" -> "cy"
            else -> {
                try {
                    val matched = java.util.Locale.getAvailableLocales().firstOrNull {
                        it.getDisplayLanguage(it).equals(lower, ignoreCase = true) ||
                        it.getDisplayLanguage(java.util.Locale.ENGLISH).equals(lower, ignoreCase = true) ||
                        it.getDisplayLanguage(java.util.Locale.getDefault()).equals(lower, ignoreCase = true)
                    }
                    if (matched != null && matched.language.isNotBlank()) {
                        matched.language.lowercase()
                    } else {
                        val parsed = java.util.Locale.forLanguageTag(lower).language
                        if (parsed.isNotBlank() && parsed.length in 2..3) parsed.lowercase() else "en"
                    }
                } catch (_: Throwable) {
                    "en"
                }
            }
        }
    }

    private const val TAG = "ProofreadHelper"
}
