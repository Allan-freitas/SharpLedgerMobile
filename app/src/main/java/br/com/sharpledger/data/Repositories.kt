package br.com.sharpledger.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import br.com.sharpledger.core.ApiException
import br.com.sharpledger.core.ApiRunner
import br.com.sharpledger.core.ContentUriRequestBody
import br.com.sharpledger.core.FileSharing
import br.com.sharpledger.core.SessionManager
import br.com.sharpledger.core.localized
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.io.File

class AuthRepository(
    private val runner: ApiRunner,
    private val session: SessionManager,
) {
    suspend fun login(email: String, password: String): Result<UserProfile> = runner.call { api ->
        val response = api.login(LoginRequest(email.trim(), password))
        session.saveAuth(response.token, response.user)
        response.user
    }

    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        inviteCode: String?,
    ): Result<UserProfile> = runner.call { api ->
        val response = api.register(
            RegisterRequest(
                email = email.trim(),
                fullName = fullName.trim(),
                password = password,
                inviteCode = inviteCode?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        session.saveAuth(response.token, response.user)
        response.user
    }

    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> = runner.call { api ->
        api.forgotPassword(ForgotPasswordRequest(email.trim()))
    }

    suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Unit> =
        runner.call { api ->
            api.resetPassword(ResetPasswordRequest(email.trim(), token.trim(), newPassword))
        }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runner.call { api ->
            api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
        }

    suspend fun me(): Result<UserProfile> = runner.call { api ->
        val profile = api.me()
        session.updateUser(profile)
        profile
    }

    suspend fun version(): Result<String> = runner.call { api -> api.getVersion().version }
}

class UsersRepository(private val runner: ApiRunner) {
    suspend fun getUsers(): Result<FamilyUsersResponse> = runner.call { api -> api.getUsers() }

    suspend fun changeRole(userId: String, roleName: String): Result<Unit> = runner.call { api ->
        api.changeRole(userId, ChangeRoleRequest(roleName))
    }

    suspend fun regenerateInviteCode(): Result<String> = runner.call { api ->
        api.regenerateInviteCode().inviteCode
    }
}

class ProfileImageRepository(
    private val runner: ApiRunner,
    private val session: SessionManager,
    private val context: Context,
) {
    suspend fun upload(uri: Uri): Result<UserProfile> {
        val name = displayName(uri)
        val size = displaySize(uri)
        val extension = name.substringAfterLast('.', "").lowercase()

        if (extension !in ALLOWED_IMAGE_EXTENSIONS) {
            return Result.failure(
                ApiException(
                    localized(
                        "O arquivo selecionado não é uma imagem válida.",
                        "The selected file is not a valid image.",
                    ),
                ),
            )
        }

        if (size <= 0) {
            return Result.failure(
                ApiException(
                    localized("O arquivo está vazio.", "The file is empty."),
                ),
            )
        }

        if (size > MAX_IMAGE_SIZE) {
            return Result.failure(
                ApiException(
                    localized(
                        "A imagem excede o limite de 5 MB.",
                        "The image exceeds the 5 MB limit.",
                    ),
                ),
            )
        }

        val contentType = FileSharing.mimeTypeFor(name).toMediaTypeOrNull()
        val body = ContentUriRequestBody(context, uri, contentType, size)
        val part = MultipartBody.Part.createFormData("file", name, body)

        return runner.call { api ->
            val result = api.uploadProfileImage(part)
            val current = session.state.value.user ?: UserProfile()
            val updated = current.copy(profileImageStorageName = result.storageName)
            session.updateUser(updated)
            updated
        }
    }

    suspend fun download(): Result<File> = runner.call { api ->
        val body = api.downloadProfileImage()
        FileSharing.saveToCache(context, "profile", "profile_image", body)
    }

    suspend fun remove(): Result<UserProfile> = runner.call { api ->
        api.removeProfileImage()
        val current = session.state.value.user ?: UserProfile()
        val updated = current.copy(profileImageStorageName = null)
        session.updateUser(updated)
        updated
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val value = cursor.getString(index)
                        if (!value.isNullOrBlank()) return value
                    }
                }
            }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "profile.jpg"
    }

    private fun displaySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }

        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        const val MAX_IMAGE_SIZE = 5L * 1024L * 1024L
        private val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }
}

class CategoryRepository(private val runner: ApiRunner) {
    suspend fun getCategories(includeInactive: Boolean): Result<List<Category>> = runner.call { api ->
        api.getCategories(includeInactive)
    }

    suspend fun create(name: String, type: String, isActive: Boolean): Result<String> =
        runner.call { api ->
            api.createCategory(SaveCategoryRequest(name.trim(), type, isActive))
        }

    suspend fun update(
        id: String,
        name: String,
        type: String,
        isActive: Boolean,
    ): Result<Unit> = runner.call { api ->
        api.updateCategory(id, SaveCategoryRequest(name.trim(), type, isActive))
    }

    suspend fun setActive(id: String, isActive: Boolean): Result<Unit> = runner.call { api ->
        api.setCategoryActive(id, SetActiveRequest(isActive))
    }
}

class CostCenterRepository(private val runner: ApiRunner) {
    suspend fun getCostCenters(includeInactive: Boolean): Result<List<CostCenter>> = runner.call { api ->
        api.getCostCenters(includeInactive)
    }

    suspend fun create(name: String, isActive: Boolean, closingDay: Int?, dueDay: Int?): Result<String> =
        runner.call { api ->
            api.createCostCenter(SaveCostCenterRequest(name.trim(), isActive, closingDay, dueDay))
        }

    suspend fun update(
        id: String,
        name: String,
        isActive: Boolean,
        closingDay: Int?,
        dueDay: Int?,
    ): Result<Unit> = runner.call { api ->
        api.updateCostCenter(id, SaveCostCenterRequest(name.trim(), isActive, closingDay, dueDay))
    }

    suspend fun setActive(id: String, isActive: Boolean): Result<Unit> = runner.call { api ->
        api.setCostCenterActive(id, SetActiveRequest(isActive))
    }
}

class BudgetRepository(private val runner: ApiRunner) {
    suspend fun getSummary(year: Int, month: Int): Result<MonthlySummary> = runner.call { api ->
        api.getMonthlySummary(year, month)
    }

    suspend fun getSetup(year: Int, month: Int): Result<MonthlyBudgetSetup> = runner.call { api ->
        api.getBudgetSetup(year, month)
    }

    suspend fun setInitialBalance(year: Int, month: Int, value: java.math.BigDecimal): Result<Unit> =
        runner.call { api ->
            api.setInitialBalance(SetInitialBalanceRequest(year, month, value))
        }
}

class TransactionRepository(private val runner: ApiRunner) {
    suspend fun getMonth(year: Int, month: Int): Result<MonthTransactions> = runner.call { api ->
        api.getMonthTransactions(year, month)
    }

    suspend fun getById(id: String): Result<TransactionEdit> = runner.call { api ->
        api.getTransaction(id)
    }

    suspend fun create(request: SaveTransactionRequest): Result<TransactionSaveResult> =
        runner.call { api -> api.createTransaction(request) }

    suspend fun update(id: String, request: SaveTransactionRequest): Result<TransactionSaveResult> =
        runner.call { api -> api.updateTransaction(id, request) }

    suspend fun delete(id: String): Result<Unit> = runner.call { api -> api.deleteTransaction(id) }

    suspend fun deleteInstallment(purchaseId: String): Result<Unit> = runner.call { api ->
        api.deleteInstallmentPurchase(purchaseId)
    }
}

class AttachmentRepository(
    private val runner: ApiRunner,
    private val context: Context,
) {
    suspend fun getForTransaction(transactionId: String): Result<List<ExpenseAttachment>> =
        runner.call { api -> api.getAttachments(transactionId) }

    suspend fun upload(transactionId: String, uris: List<Uri>): Result<List<ExpenseAttachment>> {
        if (uris.isEmpty()) {
            return Result.failure(
                ApiException(
                    localized(
                        "Selecione pelo menos um arquivo.",
                        "Select at least one file.",
                    ),
                ),
            )
        }

        if (uris.size > MAX_FILES) {
            return Result.failure(
                ApiException(
                    localized(
                        "Envie no máximo $MAX_FILES arquivos de até 10 MB.",
                        "Upload up to $MAX_FILES files of 10 MB each.",
                    ),
                ),
            )
        }

        val parts = mutableListOf<MultipartBody.Part>()

        for (uri in uris) {
            val name = displayName(uri)
            val size = displaySize(uri)
            val extension = name.substringAfterLast('.', "").lowercase()

            if (extension !in ALLOWED_EXTENSIONS) {
                return Result.failure(
                    ApiException(
                        localized(
                            "O arquivo '$name' não é permitido.",
                            "The file '$name' is not allowed.",
                        ),
                    ),
                )
            }

            if (size <= 0) {
                return Result.failure(
                    ApiException(
                        localized("O arquivo '$name' está vazio.", "The file '$name' is empty."),
                    ),
                )
            }

            if (size > MAX_FILE_SIZE) {
                return Result.failure(
                    ApiException(
                        localized(
                            "O arquivo '$name' excede o limite de 10 MB.",
                            "The file '$name' exceeds the 10 MB limit.",
                        ),
                    ),
                )
            }

            val contentType = FileSharing.mimeTypeFor(name).toMediaTypeOrNull()
            val body = ContentUriRequestBody(context, uri, contentType, size)
            parts.add(MultipartBody.Part.createFormData("files", name, body))
        }

        return runner.call { api -> api.uploadAttachments(transactionId, parts) }
    }

    suspend fun download(attachmentId: String, fileName: String): Result<File> =
        runner.call { api ->
            val body = api.downloadAttachment(attachmentId)
            FileSharing.saveToCache(context, "attachments", fileName, body)
        }

    suspend fun delete(attachmentId: String): Result<Unit> = runner.call { api ->
        api.deleteAttachment(attachmentId)
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val value = cursor.getString(index)
                        if (!value.isNullOrBlank()) return value
                    }
                }
            }

        return uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
    }

    private fun displaySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }

        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        const val MAX_FILES = 5
        const val MAX_FILE_SIZE = 10L * 1024L * 1024L
        private val ALLOWED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "txt", "csv",
        )
    }
}

class InvestmentRepository(private val runner: ApiRunner) {
    suspend fun getPortfolio(): Result<List<InvestmentSummary>> = runner.call { api ->
        api.getPortfolio()
    }

    suspend fun getDetails(id: String): Result<InvestmentDetails> = runner.call { api ->
        api.getInvestment(id)
    }

    suspend fun create(request: CreateInvestmentRequest): Result<String> = runner.call { api ->
        api.createInvestment(request)
    }

    suspend fun update(id: String, request: UpdateInvestmentRequest): Result<Unit> =
        runner.call { api -> api.updateInvestment(id, request) }

    suspend fun delete(id: String): Result<Unit> = runner.call { api -> api.deleteInvestment(id) }

    suspend fun addTransaction(
        id: String,
        request: AddInvestmentTransactionRequest,
    ): Result<Unit> = runner.call { api -> api.addInvestmentTransaction(id, request) }

    suspend fun deleteTransaction(id: String, transactionId: String): Result<Unit> =
        runner.call { api -> api.deleteInvestmentTransaction(id, transactionId) }

    suspend fun addDividend(
        id: String,
        request: AddInvestmentDividendRequest,
    ): Result<Unit> = runner.call { api -> api.addInvestmentDividend(id, request) }

    suspend fun deleteDividend(id: String, dividendId: String): Result<Unit> =
        runner.call { api -> api.deleteInvestmentDividend(id, dividendId) }

    suspend fun refreshQuotes(investmentId: String? = null): Result<QuoteRefreshResult> =
        runner.call { api -> api.refreshQuotes(investmentId) }
}

class ReportRepository(
    private val runner: ApiRunner,
    private val context: Context,
) {
    suspend fun getMonthly(year: Int, month: Int): Result<MonthlyReport> = runner.call { api ->
        api.getMonthlyReport(year, month)
    }

    suspend fun downloadMonthlyPdf(year: Int, month: Int): Result<File> = runner.call { api ->
        val body = api.downloadMonthlyReportPdf(year, month)
        val fileName = "sharpledger-%04d-%02d.pdf".format(year, month)
        FileSharing.saveToCache(context, "exports", fileName, body)
    }
}
