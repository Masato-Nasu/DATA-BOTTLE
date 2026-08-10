package jp.masatolab.databottle.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt


data class MetricResult(
    val type: BottleType,
    val ratio: Float,
    val headline: String,
    val detail: String,
    val available: Boolean = true,
    val needsUsageAccess: Boolean = false,
    val needsOpenAiKey: Boolean = false
)

class DataRepository(private val context: Context, private val settings: AppSettings) {
    private val openAiClient = OpenAiUsageClient()

    fun read(type: BottleType): MetricResult = when (type) {
        BottleType.BATTERY -> readBattery()
        BottleType.STORAGE -> readStorage()
        BottleType.MEMORY -> readMemory()
        BottleType.MOBILE_DATA -> readMobileData()
        BottleType.OPENAI_API -> readOpenAiApi()
        BottleType.BRIGHTNESS -> readBrightness()
        BottleType.VOLUME -> readVolume()
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun readBattery(): MetricResult {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val percent = if (raw in 0..100) raw else 0
        return MetricResult(
            type = BottleType.BATTERY,
            ratio = percent / 100f,
            headline = "$percent%",
            detail = "BATTERY LEVEL"
        )
    }

    private fun readStorage(): MetricResult {
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.totalBytes.coerceAtLeast(1L)
        val used = (total - stat.availableBytes).coerceIn(0L, total)
        val ratio = used.toFloat() / total.toFloat()
        return MetricResult(
            type = BottleType.STORAGE,
            ratio = ratio,
            headline = "${(ratio * 100).roundToInt()}%",
            detail = "${formatGiB(used)} / ${formatGiB(total)}"
        )
    }

    private fun readMemory(): MetricResult {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val total = info.totalMem.coerceAtLeast(1L)
        val used = (total - info.availMem).coerceIn(0L, total)
        val ratio = used.toFloat() / total.toFloat()
        return MetricResult(
            type = BottleType.MEMORY,
            ratio = ratio,
            headline = "${(ratio * 100).roundToInt()}%",
            detail = "${formatGiB(used)} / ${formatGiB(total)}"
        )
    }

    @Suppress("DEPRECATION")
    private fun readMobileData(): MetricResult {
        if (!hasUsageAccess()) {
            return MetricResult(
                type = BottleType.MOBILE_DATA,
                ratio = 0f,
                headline = "--",
                detail = "USAGE ACCESS REQUIRED",
                available = false,
                needsUsageAccess = true
            )
        }

        val limitGb = settings.mobileLimitGb().coerceAtLeast(0.1f)
        val limitBytes = (limitGb * GIB).toLong().coerceAtLeast(1L)
        val (startMillis, endMillis) = billingWindow(settings.cycleDay())

        return try {
            val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val bucket = manager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startMillis,
                endMillis
            )
            val used = ((bucket?.rxBytes ?: 0L) + (bucket?.txBytes ?: 0L)).coerceAtLeast(0L)
            val ratio = used.toDouble().div(limitBytes.toDouble()).toFloat().coerceAtLeast(0f)
            MetricResult(
                type = BottleType.MOBILE_DATA,
                ratio = ratio,
                headline = "${(ratio * 100).roundToInt()}%",
                detail = "${formatGiB(used)} / ${formatLimit(limitGb)}"
            )
        } catch (_: SecurityException) {
            MetricResult(
                type = BottleType.MOBILE_DATA,
                ratio = 0f,
                headline = "--",
                detail = "USAGE ACCESS REQUIRED",
                available = false,
                needsUsageAccess = true
            )
        } catch (_: Exception) {
            MetricResult(
                type = BottleType.MOBILE_DATA,
                ratio = 0f,
                headline = "--",
                detail = "DATA UNAVAILABLE",
                available = false
            )
        }
    }

    private fun readOpenAiApi(): MetricResult {
        val result = openAiClient.readCurrentMonth(
            adminKey = settings.openAiAdminKey(),
            fallbackLimitUsd = settings.openAiFallbackLimitUsd().toDouble()
        )

        return when (result) {
            OpenAiSpendResult.MissingKey -> MetricResult(
                type = BottleType.OPENAI_API,
                ratio = 0f,
                headline = "--",
                detail = "ADD ADMIN API KEY IN SETTINGS",
                available = false,
                needsOpenAiKey = true
            )
            OpenAiSpendResult.Unauthorized -> MetricResult(
                type = BottleType.OPENAI_API,
                ratio = 0f,
                headline = "--",
                detail = "ADMIN API KEY REQUIRED",
                available = false,
                needsOpenAiKey = true
            )
            is OpenAiSpendResult.Failure -> MetricResult(
                type = BottleType.OPENAI_API,
                ratio = 0f,
                headline = "--",
                detail = result.message,
                available = false
            )
            is OpenAiSpendResult.Success -> {
                val snapshot = result.snapshot
                val ratio = (snapshot.costUsd / snapshot.limitUsd)
                    .toFloat()
                    .coerceAtLeast(0f)
                val source = if (snapshot.limitFromApi) "OPENAI LIMIT" else "LOCAL LIMIT"
                MetricResult(
                    type = BottleType.OPENAI_API,
                    ratio = ratio,
                    headline = "${(ratio * 100).roundToInt()}%",
                    detail = String.format(
                        Locale.US,
                        "\$%.2f / \$%.2f · %s",
                        snapshot.costUsd,
                        snapshot.limitUsd,
                        source
                    )
                )
            }
        }
    }

    private fun readBrightness(): MetricResult {
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(0)
        val ratio = (raw / 255f).coerceIn(0f, 1f)
        return MetricResult(
            type = BottleType.BRIGHTNESS,
            ratio = ratio,
            headline = "${(ratio * 100).roundToInt()}%",
            detail = "SCREEN BRIGHTNESS"
        )
    }

    private fun readVolume(): MetricResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        val ratio = current.toFloat() / max.toFloat()
        return MetricResult(
            type = BottleType.VOLUME,
            ratio = ratio,
            headline = "${(ratio * 100).roundToInt()}%",
            detail = "MEDIA VOLUME"
        )
    }

    private fun billingWindow(cycleDay: Int): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        fun withSafeDay(date: LocalDate, day: Int): LocalDate =
            date.withDayOfMonth(min(day, date.lengthOfMonth()))

        val thisMonthStart = withSafeDay(today.withDayOfMonth(1), cycleDay)
        val startDate = if (!today.isBefore(thisMonthStart)) {
            thisMonthStart
        } else {
            withSafeDay(today.minusMonths(1).withDayOfMonth(1), cycleDay)
        }

        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        return start to System.currentTimeMillis()
    }

    private fun formatGiB(bytes: Long): String = String.format(
        Locale.US,
        "%.1f GB",
        bytes.toDouble() / GIB.toDouble()
    )

    private fun formatLimit(limitGb: Float): String = if (limitGb % 1f == 0f) {
        "${limitGb.toInt()} GB"
    } else {
        String.format(Locale.US, "%.1f GB", limitGb)
    }

    companion object {
        private const val GIB = 1024L * 1024L * 1024L
    }
}
