package br.com.sharpledger.core

import android.content.Context
import br.com.sharpledger.data.AttachmentRepository
import br.com.sharpledger.data.AuthRepository
import br.com.sharpledger.data.BudgetRepository
import br.com.sharpledger.data.CategoryRepository
import br.com.sharpledger.data.CostCenterRepository
import br.com.sharpledger.data.InvestmentRepository
import br.com.sharpledger.data.ProfileImageRepository
import br.com.sharpledger.data.ReportRepository
import br.com.sharpledger.data.TransactionRepository
import br.com.sharpledger.data.UsersRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val session = SessionManager(appContext)
    private val apiProvider = ApiProvider(session)
    private val runner = ApiRunner(apiProvider, session)

    val authRepository = AuthRepository(runner, session)
    val usersRepository = UsersRepository(runner)
    val profileImageRepository = ProfileImageRepository(runner, session, appContext)
    val categoryRepository = CategoryRepository(runner)
    val costCenterRepository = CostCenterRepository(runner)
    val budgetRepository = BudgetRepository(runner)
    val transactionRepository = TransactionRepository(runner)
    val attachmentRepository = AttachmentRepository(runner, appContext)
    val investmentRepository = InvestmentRepository(runner)
    val reportRepository = ReportRepository(runner, appContext)
}
