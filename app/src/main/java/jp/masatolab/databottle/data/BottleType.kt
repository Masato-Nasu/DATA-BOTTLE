package jp.masatolab.databottle.data

enum class BottleType(val label: String, val shortLabel: String) {
    BATTERY("BATTERY", "BATTERY"),
    STORAGE("STORAGE", "STORAGE"),
    MEMORY("MEMORY", "MEMORY"),
    MOBILE_DATA("MOBILE DATA", "MOBILE"),
    OPENAI_API("OPENAI API", "OPENAI"),
    BRIGHTNESS("BRIGHTNESS", "BRIGHT"),
    VOLUME("VOLUME", "VOLUME");

    val supportsOverflowLayers: Boolean
        get() = this == MOBILE_DATA || this == OPENAI_API

    companion object {
        fun fromName(name: String?): BottleType? = entries.firstOrNull { it.name == name }
    }
}
