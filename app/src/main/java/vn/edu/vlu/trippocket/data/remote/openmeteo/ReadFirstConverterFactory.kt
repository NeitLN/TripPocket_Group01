package vn.edu.vlu.trippocket.data.remote.openmeteo

import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Type

/**
 * An answer from Open-Meteo that arrived in full but could not be read: not JSON, or JSON cut
 * short. A RuntimeException, so it is never mistaken for a lost connection (an IOException).
 */
class UnreadableResponseException(cause: IOException) : RuntimeException(cause)

/**
 * Wraps [delegate] (Gson) so a lost connection and a broken answer stay two different errors.
 *
 * Gson reports broken JSON with IOExceptions (MalformedJsonException, EOFException), the same
 * type OkHttp uses when the connection drops, so the screen said "no connection" for a bad
 * answer. This reads the whole body from the network first: an IOException there is the
 * connection. Then it parses the bytes: an IOException there is the answer, and becomes
 * [UnreadableResponseException]. Open-Meteo answers are a few kilobytes, so holding one in
 * memory costs nothing.
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
        /** The converter the app uses for Open-Meteo: Gson, read in full first. */
        fun gson(): ReadFirstConverterFactory =
            ReadFirstConverterFactory(GsonConverterFactory.create())
    }
}
