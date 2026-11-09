package vn.edu.vlu.trippocket.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Type

/**
 * An answer that arrived in full but could not be read: not JSON, JSON cut short, or an empty
 * body. A RuntimeException, so [toApiError] maps it to BAD_RESPONSE and never to OFFLINE.
 */
class UnreadableResponseException(cause: IOException) : RuntimeException(cause)

/**
 * Wraps [delegate] (Gson) so a lost connection and a broken answer stay two different errors.
 *
 * Gson reports broken JSON with IOExceptions (MalformedJsonException, EOFException), the same
 * type OkHttp uses when the connection drops, so every screen said "no connection" for a bad
 * answer. This reads the whole body from the network first: an IOException there is the
 * connection (OFFLINE or TIMEOUT). Then it parses the bytes: an IOException there is the answer,
 * and becomes [UnreadableResponseException]. The APIs the app calls answer a few kilobytes, so
 * holding one in memory costs nothing.
 */
class ReadFirstConverterFactory(private val delegate: Converter.Factory) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        val parse = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any?> { body ->
            val contentType = body.contentType()
            val bytes = body.use { it.bytes() }
            try {
                parse.convert(bytes.toResponseBody(contentType))
            } catch (e: IOException) {
                throw UnreadableResponseException(e)
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? =
        delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)

    companion object {
        /** The converter every Retrofit in AppContainer uses: Gson, read in full first. */
        fun gson(): ReadFirstConverterFactory =
            ReadFirstConverterFactory(GsonConverterFactory.create())
    }
}
