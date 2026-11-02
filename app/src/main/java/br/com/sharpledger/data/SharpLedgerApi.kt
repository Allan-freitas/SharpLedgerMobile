package br.com.sharpledger.data

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SharpLedgerApi {

    @POST("users/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("users/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("users/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("users/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest)

    @POST("users/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    @GET("users/me")
    suspend fun me(): UserProfile

    @GET("users")
    suspend fun getUsers(): FamilyUsersResponse

    @PUT("users/{id}/role")
    suspend fun changeRole(@Path("id") userId: String, @Body request: ChangeRoleRequest)

    @POST("users/invite-code/regenerate")
    suspend fun regenerateInviteCode(): InviteCodeResponse

    @GET("categories")
    suspend fun getCategories(@Query("includeInactive") includeInactive: Boolean): List<Category>

    @GET("categories/{id}")
    suspend fun getCategory(@Path("id") id: String): Category

    @POST("categories")
    suspend fun createCategory(@Body request: SaveCategoryRequest): String

    @PUT("categories/{id}")
    suspend fun updateCategory(@Path("id") id: String, @Body request: SaveCategoryRequest)

    @PATCH("categories/{id}/active")
    suspend fun setCategoryActive(@Path("id") id: String, @Body request: SetActiveRequest)

    @GET("budgets/summary")
    suspend fun getMonthlySummary(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): MonthlySummary

    @GET("budgets/setup")
    suspend fun getBudgetSetup(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): MonthlyBudgetSetup

    @PUT("budgets/initial-balance")
    suspend fun setInitialBalance(@Body request: SetInitialBalanceRequest)

    @GET("transactions")
    suspend fun getMonthTransactions(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): MonthTransactions

    @GET("transactions/{id}")
    suspend fun getTransaction(@Path("id") id: String): TransactionEdit

    @POST("transactions")
    suspend fun createTransaction(@Body request: SaveTransactionRequest): TransactionSaveResult

    @PUT("transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: String,
        @Body request: SaveTransactionRequest,
    ): TransactionSaveResult

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String)

    @DELETE("installments/{purchaseId}")
    suspend fun deleteInstallmentPurchase(@Path("purchaseId") purchaseId: String)

    @GET("transactions/{id}/attachments")
    suspend fun getAttachments(@Path("id") transactionId: String): List<ExpenseAttachment>

    @Multipart
    @POST("transactions/{id}/attachments")
    suspend fun uploadAttachments(
        @Path("id") transactionId: String,
        @Part files: List<MultipartBody.Part>,
    ): List<ExpenseAttachment>

    @Streaming
    @GET("attachments/{id}")
    suspend fun downloadAttachment(@Path("id") attachmentId: String): ResponseBody

    @DELETE("attachments/{id}")
    suspend fun deleteAttachment(@Path("id") attachmentId: String)

    @GET("investments")
    suspend fun getPortfolio(): List<InvestmentSummary>

    @GET("investments/{id}")
    suspend fun getInvestment(@Path("id") id: String): InvestmentDetails

    @POST("investments")
    suspend fun createInvestment(@Body request: CreateInvestmentRequest): String

    @PUT("investments/{id}")
    suspend fun updateInvestment(
        @Path("id") id: String,
        @Body request: UpdateInvestmentRequest,
    )

    @DELETE("investments/{id}")
    suspend fun deleteInvestment(@Path("id") id: String)

    @POST("investments/{id}/transactions")
    suspend fun addInvestmentTransaction(
        @Path("id") id: String,
        @Body request: AddInvestmentTransactionRequest,
    )

    @DELETE("investments/{id}/transactions/{transactionId}")
    suspend fun deleteInvestmentTransaction(
        @Path("id") id: String,
        @Path("transactionId") transactionId: String,
    )

    @POST("investments/{id}/dividends")
    suspend fun addInvestmentDividend(
        @Path("id") id: String,
        @Body request: AddInvestmentDividendRequest,
    )

    @DELETE("investments/{id}/dividends/{dividendId}")
    suspend fun deleteInvestmentDividend(
        @Path("id") id: String,
        @Path("dividendId") dividendId: String,
    )

    @POST("investments/quotes/refresh")
    suspend fun refreshQuotes(
        @Query("investmentId") investmentId: String? = null,
    ): QuoteRefreshResult

    @GET("reports/monthly")
    suspend fun getMonthlyReport(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): MonthlyReport

    @Streaming
    @GET("reports/monthly/pdf")
    suspend fun downloadMonthlyReportPdf(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): ResponseBody

    @GET("version")
    suspend fun getVersion(): VersionResponse
}
