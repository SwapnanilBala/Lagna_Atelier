package site.lagnaatelier.app.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/*
 * The app's language: the seven the website reads in, chosen in Account ->
 * Preferences or left to follow the phone.
 *
 * On Android 13 and later the choice is the system's per-app language
 * (LocaleManager): Android remembers it, recreates the activity, and lists it
 * under Settings -> Apps -> Language too (res/xml/locales_config.xml). On 9
 * to 12 there is no such setting, so the choice is kept here and the activity
 * wraps its own context in it (MainActivity.attachBaseContext).
 */
object AppLanguage {
    /** BCP 47 tags, in the order the picker lists them. English is the source; the rest are translations. */
    val SUPPORTED = listOf("en", "hi", "bn", "es", "fr", "it", "de")

    private const val FILE = "language"
    private const val KEY = "tag"

    /** The tag the reader chose, or null when the app follows the phone. */
    fun chosen(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY, null)
        }
        return tag?.takeIf { it in SUPPORTED }
    }

    /** Switches the app to [tag], or back to the phone's language for null. Recreates the activity. */
    fun choose(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            /* The system recreates every activity in the new language. */
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().apply { if (tag == null) remove(KEY) else putString(KEY, tag) }.commit()
            activity.recreate()
        }
    }

    /** Before Android 13: [base] in the chosen language, or [base] itself when following the phone. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = chosen(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /**
     * The language the app's text came out in, for formatting dates to match:
     * the first of the configuration's locales the app has strings for. A
     * phone set to Japanese gets English text, so it gets English dates too.
     */
    fun resolve(locales: List<Locale>): Locale =
        locales.firstOrNull { it.language in SUPPORTED } ?: Locale.ENGLISH

    fun resolve(configuration: Configuration): Locale =
        resolve((0 until configuration.locales.size()).map { configuration.locales[it] })

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/*
 * Dates in the app's language. English keeps the patterns the screens were
 * written with ("Tue 3 Nov"); another language gets the same fields in its
 * own order and words from Android's pattern for it ("3 नव॰, मंगल").
 */
object AppDates {
    /** Set from the activity's configuration as it starts. English until then, and in unit tests. */
    @Volatile
    var locale: Locale = Locale.ENGLISH

    private val cache = ConcurrentHashMap<Triple<String, String, Locale>, DateTimeFormatter>()

    /**
     * A formatter for [english] (a java.time pattern) in English, or for
     * [skeleton] (the same fields, as Android's DateFormat skeleton) in any
     * other language.
     */
    fun formatter(english: String, skeleton: String, locale: Locale = this.locale): DateTimeFormatter =
        cache.getOrPut(Triple(english, skeleton, locale)) {
            val local = if (locale.language == "en") null else localPattern(locale, skeleton)
            /* ICU and java.time share nearly all pattern letters; a pattern java.time refuses falls back. */
            local?.let { runCatching { DateTimeFormatter.ofPattern(it, locale) }.getOrNull() }
                ?: DateTimeFormatter.ofPattern(english, locale)
        }

    /* Android's best pattern; null on the JVM (unit tests), where android.text is a stub. */
    private fun localPattern(locale: Locale, skeleton: String): String? = try {
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    } catch (_: Throwable) {
        null
    }
}
