package vn.edu.vlu.trippocket.data.remote.exchangerate

import com.google.gson.annotations.SerializedName

/**
 * DTO for https://open.er-api.com/v6/latest/{base}.
 * Every field is nullable to withstand missing or unexpected fields.
 *
 * The API returns HTTP 200 with result = "error" and error-type when an invalid base is provided.
 */
data class ExchangeRateResponse(
    val result: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("documentation") val documentation: String? = null,
    @SerializedName("terms_of_use") val termsOfUse: String? = null,
    @SerializedName("time_last_update_unix") val timeLastUpdateUnix: Long? = null,
    @SerializedName("time_last_update_utc") val timeLastUpdateUtc: String? = null,
    @SerializedName("time_next_update_unix") val timeNextUpdateUnix: Long? = null,
    @SerializedName("time_next_update_utc") val timeNextUpdateUtc: String? = null,
    @SerializedName("base_code") val baseCode: String? = null,
    val rates: Map<String, Double>? = null,
    @SerializedName("error-type") val errorType: String? = null,
)
