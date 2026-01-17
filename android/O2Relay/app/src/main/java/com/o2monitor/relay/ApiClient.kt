package com.o2monitor.relay

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with the Pi relay API.
 *
 * All methods are suspend functions for use with coroutines.
 * Network operations run on Dispatchers.IO.
 */
class ApiClient(
    private val baseUrl: String,
    private val deviceId: String = "android_relay"
) {
    companion object {
        private const val TAG = "ApiClient"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .create()

    /**
     * Check relay status from Pi.
     * GET /api/relay/status
     *
     * @return RelayStatus if successful, null on error
     */
    suspend fun getRelayStatus(): RelayStatus? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/relay/status"
        Log.d(TAG, "GET $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "getRelayStatus failed: ${response.code} ${response.message}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body == null) {
                    Log.w(TAG, "getRelayStatus: empty response body")
                    return@withContext null
                }

                Log.d(TAG, "getRelayStatus response: $body")
                return@withContext gson.fromJson(body, RelayStatus::class.java)
            }
        } catch (e: IOException) {
            Log.e(TAG, "getRelayStatus network error", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "getRelayStatus error", e)
            return@withContext null
        }
    }

    /**
     * Post a single reading to Pi.
     * POST /api/relay/reading
     *
     * @param reading The oximeter reading to send
     * @param queued Whether this reading was queued (sent later due to network issues)
     * @return true if successful, false on error
     */
    suspend fun postReading(reading: OxiReading, queued: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/relay/reading"
        Log.d(TAG, "POST $url")

        val requestData = ReadingRequest(
            spo2 = reading.spo2,
            heartRate = reading.heartRate,
            battery = reading.battery,
            timestamp = formatTimestamp(reading.timestamp),
            deviceId = deviceId,
            queued = queued
        )

        val jsonBody = gson.toJson(requestData)
        Log.d(TAG, "postReading body: $jsonBody")

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "postReading failed: ${response.code} ${response.message}")
                    return@withContext false
                }

                val body = response.body?.string()
                Log.d(TAG, "postReading response: $body")

                // Parse response to check status
                if (body != null) {
                    val apiResponse = gson.fromJson(body, ApiResponse::class.java)
                    return@withContext apiResponse.status == "ok"
                }

                return@withContext true
            }
        } catch (e: IOException) {
            Log.e(TAG, "postReading network error", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "postReading error", e)
            return@withContext false
        }
    }

    /**
     * Post a batch of readings to Pi.
     * POST /api/relay/batch
     *
     * @param readings List of readings to send
     * @return BatchResponse if successful, null on error
     */
    suspend fun postBatch(readings: List<OxiReading>): BatchResponse? = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) {
            Log.d(TAG, "postBatch: empty list, skipping")
            return@withContext BatchResponse(status = "ok", accepted = 0, rejected = 0)
        }

        val url = "$baseUrl/api/relay/batch"
        Log.d(TAG, "POST $url (${readings.size} readings)")

        val batchReadings = readings.map { reading ->
            BatchReading(
                spo2 = reading.spo2,
                heartRate = reading.heartRate,
                battery = reading.battery,
                timestamp = formatTimestamp(reading.timestamp),
                deviceId = deviceId
            )
        }

        val requestData = BatchRequest(readings = batchReadings)
        val jsonBody = gson.toJson(requestData)
        Log.d(TAG, "postBatch body: $jsonBody")

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "postBatch failed: ${response.code} ${response.message}")
                    return@withContext null
                }

                val body = response.body?.string()
                Log.d(TAG, "postBatch response: $body")

                if (body != null) {
                    return@withContext gson.fromJson(body, BatchResponse::class.java)
                }

                return@withContext null
            }
        } catch (e: IOException) {
            Log.e(TAG, "postBatch network error", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "postBatch error", e)
            return@withContext null
        }
    }

    /**
     * Check for app updates.
     * GET /api/relay/app-version
     *
     * @return AppVersion if successful, null on error
     */
    suspend fun getAppVersion(): AppVersion? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/relay/app-version"
        Log.d(TAG, "GET $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "getAppVersion failed: ${response.code} ${response.message}")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body != null) {
                    Log.d(TAG, "getAppVersion response: $body")
                    return@withContext gson.fromJson(body, AppVersion::class.java)
                }

                return@withContext null
            }
        } catch (e: IOException) {
            Log.e(TAG, "getAppVersion network error", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "getAppVersion error", e)
            return@withContext null
        }
    }

    /**
     * Simple connectivity check - try to reach the Pi.
     *
     * @return true if Pi is reachable, false otherwise
     */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val status = getRelayStatus()
            return@withContext status != null
        } catch (e: Exception) {
            return@withContext false
        }
    }

    private fun formatTimestamp(instant: Instant): String {
        return DateTimeFormatter.ISO_INSTANT.format(instant)
    }
}

// ==================== Data Classes ====================

/**
 * Response from GET /api/relay/status
 */
data class RelayStatus(
    val timestamp: String,
    @SerializedName("last_reading_age_seconds")
    val lastReadingAgeSeconds: Int,
    val source: String,
    @SerializedName("needs_relay")
    val needsRelay: Boolean,
    @SerializedName("pi_ble_connected")
    val piBleConnected: Boolean
)

/**
 * Request body for POST /api/relay/reading
 */
data class ReadingRequest(
    val spo2: Int,
    @SerializedName("heart_rate")
    val heartRate: Int,
    val battery: Int,
    val timestamp: String,
    @SerializedName("device_id")
    val deviceId: String,
    val queued: Boolean = false
)

/**
 * Generic API response with status and message
 */
data class ApiResponse(
    val status: String,
    val message: String? = null
)

/**
 * Single reading in a batch request
 */
data class BatchReading(
    val spo2: Int,
    @SerializedName("heart_rate")
    val heartRate: Int,
    val battery: Int,
    val timestamp: String,
    @SerializedName("device_id")
    val deviceId: String
)

/**
 * Request body for POST /api/relay/batch
 */
data class BatchRequest(
    val readings: List<BatchReading>
)

/**
 * Response from POST /api/relay/batch
 */
data class BatchResponse(
    val status: String,
    val accepted: Int,
    val rejected: Int
)

/**
 * Response from GET /api/relay/app-version
 */
data class AppVersion(
    val version: String,
    @SerializedName("version_code")
    val versionCode: Int,
    @SerializedName("apk_url")
    val apkUrl: String? = null,
    @SerializedName("release_notes")
    val releaseNotes: String? = null,
    @SerializedName("min_version_code")
    val minVersionCode: Int = 1
)
