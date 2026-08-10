package jp.masatolab.databottle.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset

data class OpenAiSpendSnapshot(
    val costUsd: Double,
    val limitUsd: Double,
    val limitFromApi: Boolean,
    val enforcementStatus: String?
)

sealed class OpenAiSpendResult {
    data class Success(val snapshot: OpenAiSpendSnapshot) : OpenAiSpendResult()
    data object MissingKey : OpenAiSpendResult()
    data object Unauthorized : OpenAiSpendResult()
    data class Failure(val message: String) : OpenAiSpendResult()
}

class OpenAiUsageClient {
    fun readCurrentMonth(adminKey: String?, fallbackLimitUsd: Double): OpenAiSpendResult {
        val key = adminKey?.trim().orEmpty()
        if (key.isBlank()) return OpenAiSpendResult.MissingKey

        val limitInfo = readOrganizationSpendLimit(key)
        if (limitInfo is EndpointResult.Unauthorized) return OpenAiSpendResult.Unauthorized

        val apiLimit = (limitInfo as? EndpointResult.Success)
            ?.json
            ?.optDouble("threshold_amount", 0.0)
            ?.takeIf { it > 0.0 }
            ?.div(100.0)
        val enforcement = (limitInfo as? EndpointResult.Success)
            ?.json
            ?.optJSONObject("enforcement")
            ?.optString("status", null)

        val limitUsd = apiLimit ?: fallbackLimitUsd.coerceAtLeast(0.01)
        val costResult = readCosts(key)
        return when (costResult) {
            is EndpointResult.Unauthorized -> OpenAiSpendResult.Unauthorized
            is EndpointResult.Failure -> OpenAiSpendResult.Failure(costResult.message)
            is EndpointResult.Success -> OpenAiSpendResult.Success(
                OpenAiSpendSnapshot(
                    costUsd = parseTotalCost(costResult.json),
                    limitUsd = limitUsd,
                    limitFromApi = apiLimit != null,
                    enforcementStatus = enforcement
                )
            )
        }
    }

    private fun readOrganizationSpendLimit(key: String): EndpointResult =
        getJson("$BASE_URL/organization/spend_limit", key)

    private fun readCosts(key: String): EndpointResult {
        val start = LocalDate.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
        val end = (System.currentTimeMillis() / 1000L) + 1L

        var nextPage: String? = null
        val merged = JSONObject().put("data", org.json.JSONArray())
        val mergedData = merged.getJSONArray("data")

        do {
            val pagePart = nextPage?.let {
                "&page=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name())
            }.orEmpty()
            val url = "$BASE_URL/organization/costs?start_time=$start&end_time=$end&bucket_width=1d&limit=31$pagePart"
            when (val result = getJson(url, key)) {
                is EndpointResult.Unauthorized -> return result
                is EndpointResult.Failure -> return result
                is EndpointResult.Success -> {
                    val data = result.json.optJSONArray("data")
                    if (data != null) {
                        for (i in 0 until data.length()) mergedData.put(data.get(i))
                    }
                    val hasMore = result.json.optBoolean("has_more", false)
                    nextPage = if (hasMore) result.json.optString("next_page", null) else null
                }
            }
        } while (!nextPage.isNullOrBlank())

        return EndpointResult.Success(merged)
    }

    private fun parseTotalCost(json: JSONObject): Double {
        val buckets = json.optJSONArray("data") ?: return 0.0
        var total = 0.0
        for (i in 0 until buckets.length()) {
            val bucket = buckets.optJSONObject(i) ?: continue
            val results = bucket.optJSONArray("results") ?: continue
            for (j in 0 until results.length()) {
                val result = results.optJSONObject(j) ?: continue
                val amount = result.optJSONObject("amount") ?: continue
                if (amount.optString("currency", "usd").equals("usd", ignoreCase = true)) {
                    total += amount.optDouble("value", 0.0)
                }
            }
        }
        return total.coerceAtLeast(0.0)
    }

    private fun getJson(url: String, key: String): EndpointResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            when {
                code in 200..299 -> EndpointResult.Success(JSONObject(body.ifBlank { "{}" }))
                code == 401 || code == 403 -> EndpointResult.Unauthorized
                code == 429 -> EndpointResult.Failure("RATE LIMITED")
                else -> EndpointResult.Failure("OPENAI HTTP $code")
            }
        } catch (_: Exception) {
            EndpointResult.Failure("NETWORK ERROR")
        } finally {
            connection?.disconnect()
        }
    }

    private sealed class EndpointResult {
        data class Success(val json: JSONObject) : EndpointResult()
        data object Unauthorized : EndpointResult()
        data class Failure(val message: String) : EndpointResult()
    }

    companion object {
        private const val BASE_URL = "https://api.openai.com/v1"
    }
}
