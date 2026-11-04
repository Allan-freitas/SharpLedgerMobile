package br.com.sharpledger.data

import java.math.BigDecimal

const val ROLE_ADMIN = "Admin"
const val ROLE_MEMBER = "Member"
const val ROLE_VIEWER = "Viewer"

object Permissions {
    const val DASHBOARD_VIEW = "Dashboard.View"
    const val TRANSACTIONS_MANAGE = "Transactions.Manage"
    const val CATEGORIES_MANAGE = "Categories.Manage"
    const val BUDGETS_MANAGE = "Budgets.Manage"
    const val INVESTMENTS_MANAGE = "Investments.Manage"
    const val USERS_MANAGE = "Users.Manage"
    const val COST_CENTERS_MANAGE = "CostCenters.Manage"
}

const val TYPE_INCOME = "Income"
const val TYPE_EXPENSE = "Expense"

const val INVESTMENT_STOCK = "Stock"
const val INVESTMENT_FUND = "Fund"
const val INVESTMENT_CRYPTO = "Crypto"
const val INVESTMENT_OTHER = "Other"

data class UserProfile(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val role: String = "",
    val profileImageStorageName: String? = null,
    val permissions: List<String> = emptyList(),
)

data class AuthResponse(
    val token: String = "",
    val user: UserProfile = UserProfile(),
)

data class ProfileImageUploadResult(
    val storageName: String = "",
)

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val fullName: String,
    val password: String,
    val inviteCode: String?,
)

data class ForgotPasswordRequest(val email: String)

data class ForgotPasswordResponse(
    val message: String = "",
    val resetToken: String? = null,
)

data class ResetPasswordRequest(
    val email: String,
    val token: String,
    val newPassword: String,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class FamilyUser(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val roleName: String = "",
    val isCurrentUser: Boolean = false,
)

data class RoleSummary(
    val name: String = "",
    val displayName: String = "",
    val permissions: List<String> = emptyList(),
)

data class FamilyUsersResponse(
    val familyName: String = "",
    val inviteCode: String? = null,
    val users: List<FamilyUser> = emptyList(),
    val roles: List<RoleSummary> = emptyList(),
)

data class ChangeRoleRequest(val roleName: String)

data class InviteCodeResponse(val inviteCode: String = "")

data class Category(
    val id: String = "",
    val name: String = "",
    val type: String = TYPE_EXPENSE,
    val isActive: Boolean = true,
)

data class SaveCategoryRequest(
    val name: String,
    val type: String,
    val isActive: Boolean,
)

data class SetActiveRequest(val isActive: Boolean)

data class CostCenter(
    val id: String = "",
    val name: String = "",
    val isActive: Boolean = true,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
)

data class SaveCostCenterRequest(
    val name: String,
    val isActive: Boolean,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
)

data class InvoiceSummary(
    val year: Int = 0,
    val month: Int = 0,
    val closingDate: String = "",
    val dueDate: String = "",
    val total: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0,
    val isCurrent: Boolean = false,
)

data class InvoiceTransaction(
    val id: String = "",
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: String = "",
    val categoryName: String = "",
)

data class InvoiceDetail(
    val summary: InvoiceSummary = InvoiceSummary(),
    val transactions: List<InvoiceTransaction> = emptyList(),
)

data class CategorySummary(
    val categoryId: String = "",
    val categoryName: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val percentage: BigDecimal = BigDecimal.ZERO,
    val color: String = "#0d6efd",
)

data class MonthlySummary(
    val familyId: String = "",
    val budgetId: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val availableBalance: BigDecimal = BigDecimal.ZERO,
    val sharedExpenseTotal: BigDecimal = BigDecimal.ZERO,
    val sharedPersonalShare: BigDecimal = BigDecimal.ZERO,
    val personalExpenseTotal: BigDecimal = BigDecimal.ZERO,
    val personalAvailableBalance: BigDecimal = BigDecimal.ZERO,
    val expenseBreakdown: List<CategorySummary> = emptyList(),
    val incomeBreakdown: List<CategorySummary> = emptyList(),
) {
    val sharedReimbursement: BigDecimal
        get() = sharedExpenseTotal.subtract(sharedPersonalShare)

    val hasTransactions: Boolean
        get() = totalIncome.signum() > 0 || totalExpense.signum() > 0
}

data class MonthlyBudgetSetup(
    val year: Int = 0,
    val month: Int = 0,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val exists: Boolean = false,
)

data class SetInitialBalanceRequest(
    val year: Int,
    val month: Int,
    val initialBalance: BigDecimal,
)

data class TransactionItem(
    val id: String = "",
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val type: String = TYPE_EXPENSE,
    val isShared: Boolean = false,
    val splitPercentage: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null,
    val installmentPurchaseId: String? = null,
    val installmentNumber: Int = 1,
    val installmentCount: Int = 1,
    val hasAttachments: Boolean = false,
    val costCenterId: String? = null,
    val costCenterName: String? = null,
) {
    val isInstallment: Boolean get() = !installmentPurchaseId.isNullOrBlank() && installmentCount > 1
}

data class MonthTransactions(
    val year: Int = 0,
    val month: Int = 0,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val availableBalance: BigDecimal = BigDecimal.ZERO,
    val transactions: List<TransactionItem> = emptyList(),
)

data class TransactionEdit(
    val id: String = "",
    val categoryId: String = "",
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: String = "",
    val isShared: Boolean = false,
    val splitPercentage: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null,
    val installmentPurchaseId: String? = null,
    val installmentNumber: Int = 1,
    val installmentCount: Int = 1,
    val costCenterId: String? = null,
)

data class SaveTransactionRequest(
    val categoryId: String,
    val description: String,
    val amount: BigDecimal,
    val date: String,
    val isShared: Boolean,
    val splitPercentage: BigDecimal,
    val notes: String?,
    val installmentCount: Int = 1,
    val costCenterId: String? = null,
)

data class TransactionSaveResult(
    val id: String = "",
    val year: Int = 0,
    val month: Int = 0,
)

data class ExpenseAttachment(
    val id: String = "",
    val fileName: String = "",
    val contentType: String = "",
    val sizeBytes: Long = 0,
    val createdAtUtc: String? = null,
)

data class InvestmentSummary(
    val id: String = "",
    val name: String = "",
    val ticker: String = "",
    val type: String = INVESTMENT_OTHER,
    val isActive: Boolean = true,
    val currentPrice: BigDecimal = BigDecimal.ZERO,
    val totalQuantity: BigDecimal = BigDecimal.ZERO,
    val averagePrice: BigDecimal = BigDecimal.ZERO,
    val totalInvested: BigDecimal = BigDecimal.ZERO,
    val currentValue: BigDecimal = BigDecimal.ZERO,
    val totalDividends: BigDecimal = BigDecimal.ZERO,
    val totalReturn: BigDecimal = BigDecimal.ZERO,
    val returnPercentage: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0,
    val dividendCount: Int = 0,
)

data class InvestmentTransaction(
    val id: String = "",
    val description: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val pricePerShare: BigDecimal = BigDecimal.ZERO,
    val fees: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal = BigDecimal.ZERO,
    val date: String = "",
    val notes: String? = null,
)

data class InvestmentDividend(
    val id: String = "",
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: String = "",
)

data class InvestmentDetails(
    val summary: InvestmentSummary = InvestmentSummary(),
    val transactions: List<InvestmentTransaction> = emptyList(),
    val dividends: List<InvestmentDividend> = emptyList(),
)

data class CreateInvestmentRequest(
    val name: String,
    val ticker: String,
    val type: String,
    val currentPrice: BigDecimal,
    val initialQuantity: BigDecimal = BigDecimal.ZERO,
    val initialInvestedAmount: BigDecimal = BigDecimal.ZERO,
)

data class UpdateInvestmentRequest(
    val name: String,
    val ticker: String,
    val type: String,
    val currentPrice: BigDecimal,
    val isActive: Boolean,
)

data class AddInvestmentTransactionRequest(
    val description: String,
    val quantity: BigDecimal,
    val pricePerShare: BigDecimal,
    val fees: BigDecimal,
    val date: String,
    val notes: String?,
)

data class AddInvestmentDividendRequest(
    val description: String,
    val amount: BigDecimal,
    val date: String,
)

data class QuoteRefreshResult(
    val updated: Int = 0,
    val requested: Int = 0,
    val missingSymbols: List<String> = emptyList(),
)

data class MonthlyReport(
    val familyName: String = "",
    val year: Int = 0,
    val month: Int = 0,
    val generatedAt: String = "",
    val summary: MonthlySummary = MonthlySummary(),
    val transactions: MonthTransactions = MonthTransactions(),
)

data class VersionResponse(val version: String = "")

data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: List<FieldError>? = null,
)

data class FieldError(
    val code: String? = null,
    val description: String? = null,
    val type: String? = null,
)

fun isIncome(type: String?): Boolean = type == TYPE_INCOME
