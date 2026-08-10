package jp.masatolab.databottle.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("data_bottle_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context.applicationContext)

    fun order(): List<BottleType> {
        val saved = prefs.getString(KEY_ORDER, null)
        val parsed = saved
            ?.split(',')
            ?.mapNotNull(BottleType::fromName)
            .orEmpty()

        val missing = BottleType.entries.filterNot(parsed::contains)
        return (parsed + missing).distinct()
    }

    fun enabled(): Set<BottleType> {
        val names = prefs.getStringSet(KEY_ENABLED, null)
            ?: return setOf(BottleType.BATTERY, BottleType.STORAGE, BottleType.MOBILE_DATA)
        return names.mapNotNull(BottleType::fromName).toSet()
    }

    fun saveOrder(order: List<BottleType>) {
        prefs.edit().putString(KEY_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun saveEnabled(enabled: Set<BottleType>) {
        prefs.edit().putStringSet(KEY_ENABLED, enabled.map { it.name }.toSet()).apply()
    }

    fun mobileLimitGb(): Float = prefs.getFloat(KEY_MOBILE_LIMIT_GB, 20f)

    fun saveMobileLimitGb(value: Float) {
        prefs.edit().putFloat(KEY_MOBILE_LIMIT_GB, value.coerceAtLeast(0.1f)).apply()
    }

    fun cycleDay(): Int = prefs.getInt(KEY_CYCLE_DAY, 1).coerceIn(1, 31)

    fun saveCycleDay(value: Int) {
        prefs.edit().putInt(KEY_CYCLE_DAY, value.coerceIn(1, 31)).apply()
    }

    fun openAiFallbackLimitUsd(): Float = prefs.getFloat(KEY_OPENAI_FALLBACK_LIMIT_USD, 20f)

    fun saveOpenAiFallbackLimitUsd(value: Float) {
        prefs.edit().putFloat(KEY_OPENAI_FALLBACK_LIMIT_USD, value.coerceAtLeast(0.01f)).apply()
    }

    fun hasOpenAiAdminKey(): Boolean = secrets.hasOpenAiAdminKey()

    fun openAiAdminKey(): String? = secrets.readOpenAiAdminKey()

    fun saveOpenAiAdminKey(value: String) = secrets.saveOpenAiAdminKey(value)

    fun clearOpenAiAdminKey() = secrets.clearOpenAiAdminKey()

    fun lastViewed(): BottleType = BottleType.fromName(prefs.getString(KEY_LAST_VIEWED, null))
        ?: BottleType.BATTERY

    fun saveLastViewed(type: BottleType) {
        prefs.edit().putString(KEY_LAST_VIEWED, type.name).apply()
    }

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MOBILE_LIMIT_GB = "mobile_limit_gb"
        private const val KEY_CYCLE_DAY = "cycle_day"
        private const val KEY_OPENAI_FALLBACK_LIMIT_USD = "openai_fallback_limit_usd"
        private const val KEY_LAST_VIEWED = "last_viewed"
    }
}
