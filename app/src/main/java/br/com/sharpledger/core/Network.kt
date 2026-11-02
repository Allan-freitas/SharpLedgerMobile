package br.com.sharpledger.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.sharpledger.data.ProblemDetails
import br.com.sharpledger.data.SharpLedgerApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val statusCode: Int = 0) : Exception(message)

class ApiProvider(private val session: SessionManager) {
    private val gson = Gson()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApi: SharpLedgerApi? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
            session.token()?.takeIf { it.isNotBlank() }?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    fun api(): SharpLedgerApi {
        val baseUrl = session.state.value.baseUrl
        val current = cachedApi
        if (current != null && cachedBaseUrl == baseUrl) {
            return current
        }

        val created = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SharpLedgerApi::class.java)

        cachedBaseUrl = baseUrl
        cachedApi = created

        return created
    }

    fun parseError(errorBody: String?, statusCode: Int): ApiException {
        val fallback = when {
            statusCode == 401 -> localized(
                "Sua sessão expirou. Entre novamente.",
                "Your session expired. Sign in again.",
            )
            statusCode == 403 -> localized(
                "Você não tem permissão para acessar este recurso.",
                "You do not have permission to access this resource.",
            )
            else -> localized(
                "Ocorreu um erro. Tente novamente.",
                "Something went wrong. Try again.",
            )
        }

        if (errorBody.isNullOrBlank()) {
            return ApiException(fallback, statusCode)
        }

        return runCatching {
            val problem = gson.fromJson(errorBody, ProblemDetails::class.java)
            val validation = problem.errors
                ?.mapNotNull { it.description }
                ?.filter { it.isNotBlank() }
                ?.joinToString(" ")

            val message = when {
                !problem.detail.isNullOrBlank() -> problem.detail
                !validation.isNullOrBlank() -> validation
                !problem.title.isNullOrBlank() -> problem.title
                else -> fallback
            }

            ApiException(message, statusCode)
        }.getOrElse { ApiException(fallback, statusCode) }
    }
}

class ApiRunner(
    private val apiProvider: ApiProvider,
    private val session: SessionManager,
) {
    suspend fun <T> call(block: suspend (SharpLedgerApi) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block(apiProvider.api()))
            } catch (exception: HttpException) {
                val statusCode = exception.code()
                val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
                if (statusCode == 401) {
                    session.expire()
                }
                Result.failure(apiProvider.parseError(body, statusCode))
            } catch (exception: IOException) {
                Result.failure(
                    ApiException(
                        localized(
                            "Não foi possível conectar ao servidor. Verifique o endereço da API.",
                            "Could not reach the server. Check the API address.",
                        ),
                    ),
                )
            } catch (exception: Exception) {
                Result.failure(
                    ApiException(
                        exception.message ?: localized(
                            "Ocorreu um erro. Tente novamente.",
                            "Something went wrong. Try again.",
                        ),
                    ),
                )
            }
        }
}

fun localized(pt: String, en: String): String =
    if (java.util.Locale.getDefault().language.equals("en", ignoreCase = true)) en else pt

class ContentUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open $uri")

        input.use { stream ->
            sink.writeAll(stream.source())
        }
    }
}

object FileSharing {
    fun saveToCache(
        context: Context,
        folder: String,
        fileName: String,
        body: ResponseBody,
    ): File {
        val directory = File(context.cacheDir, folder)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val safeName = sanitize(fileName)
        val file = File(directory, safeName)

        body.use { response ->
            response.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return file
    }

    fun share(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, file.name))
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isBlank()) "arquivo" else cleaned
    }

    fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }
}
