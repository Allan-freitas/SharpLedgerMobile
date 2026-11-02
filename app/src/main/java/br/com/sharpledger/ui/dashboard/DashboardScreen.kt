package br.com.sharpledger.ui.dashboard

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.core.FileSharing
import br.com.sharpledger.core.MoneyFormat
import br.com.sharpledger.data.BudgetRepository
import br.com.sharpledger.data.CategorySummary
import br.com.sharpledger.data.MonthlyBudgetSetup
import br.com.sharpledger.data.MonthlySummary
import br.com.sharpledger.data.Permissions
import br.com.sharpledger.data.ReportRepository
import br.com.sharpledger.data.UserProfile
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LabeledValue
import br.com.sharpledger.ui.components.MonthSelector
import br.com.sharpledger.ui.components.SectionCard
import br.com.sharpledger.ui.components.parseColor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val IncomeGreen = Color(0xFF10B981)
private val ExpensePink = Color(0xFFF43F5E)
private val SoftGray = Color(0xFF8A94A6)
private val LightPageBackground = Color(0xFFF3F6FC)
private val HeaderStart = Color(0xFF5B8DEF)
private val HeaderEnd = Color(0xFF8AB4FF)

data class MonthlyHistory(
    val month: YearMonth,
    val income: BigDecimal,
    val expense: BigDecimal,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val summary: MonthlySummary = MonthlySummary(),
    val history: List<MonthlyHistory> = emptyList(),
)

class DashboardViewModel(
    private val budgetRepository: BudgetRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {
    var state by mutableStateOf(DashboardUiState())
        private set

    var month by mutableStateOf(YearMonth.now())
        private set

    var setup by mutableStateOf<MonthlyBudgetSetup?>(null)
        private set

    var exporting by mutableStateOf(false)
        private set

    private var loadToken = 0

    init {
        load()
    }

    fun previousMonth() {
        month = month.minusMonths(1)
        load()
    }

    fun nextMonth() {
        month = month.plusMonths(1)
        load()
    }

    fun load() {
        val token = ++loadToken
        val currentMonth = month
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            val currentResult = budgetRepository.getSummary(
                currentMonth.year,
                currentMonth.monthValue,
            )

            val previousMonths = (5 downTo 1).map { currentMonth.minusMonths(it.toLong()) }
            val previousResults = coroutineScope {
                previousMonths
                    .map { previous ->
                        async {
                            previous to budgetRepository.getSummary(
                                previous.year,
                                previous.monthValue,
                            )
                        }
                    }
                    .awaitAll()
            }

            if (token != loadToken) {
                return@launch
            }

            val history = buildList {
                previousResults.forEach { (historyMonth, result) ->
                    result.getOrNull()?.let { summary ->
                        add(MonthlyHistory(historyMonth, summary.totalIncome, summary.totalExpense))
                    }
                }
                currentResult.getOrNull()?.let { summary ->
                    add(MonthlyHistory(currentMonth, summary.totalIncome, summary.totalExpense))
                }
            }.sortedBy { it.month }

            state = DashboardUiState(
                loading = false,
                error = currentResult.exceptionOrNull()?.message,
                summary = currentResult.getOrElse {
                    MonthlySummary(year = currentMonth.year, month = currentMonth.monthValue)
                },
                history = history,
            )
        }
    }

    fun loadSetup() {
        viewModelScope.launch {
            budgetRepository.getSetup(month.year, month.monthValue)
                .onSuccess { setup = it }
                .onFailure { setup = null }
        }
    }

    fun saveInitialBalance(
        value: BigDecimal,
        onSaved: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            budgetRepository.setInitialBalance(month.year, month.monthValue, value)
                .onSuccess {
                    onSaved()
                    load()
                    loadSetup()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun exportPdf(onFile: (java.io.File) -> Unit, onError: (String?) -> Unit) {
        if (exporting) return
        exporting = true

        viewModelScope.launch {
            reportRepository.downloadMonthlyPdf(month.year, month.monthValue)
                .onSuccess {
                    exporting = false
                    onFile(it)
                }
                .onFailure {
                    exporting = false
                    onError(it.message)
                }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                DashboardViewModel(
                    container.budgetRepository,
                    container.reportRepository,
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(container: AppContainer, user: UserProfile) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val context = LocalContext.current
    val activity = context as? Activity
    val canManageBudget = user.permissions.contains(Permissions.BUDGETS_MANAGE)
    val pageBackground = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.background
    } else {
        LightPageBackground
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false

        onDispose {
            if (previous != null) {
                controller?.isAppearanceLightStatusBars = previous
            }
        }
    }

    var showBalanceDialog by rememberSaveable { mutableStateOf(false) }

    val summary = viewModel.state.summary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                DashboardHeader(
                    user = user,
                    month = viewModel.month,
                    summary = summary,
                    loading = viewModel.state.loading,
                    exporting = viewModel.exporting,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onRefresh = viewModel::load,
                    onExport = {
                        viewModel.exportPdf(
                            onFile = { file ->
                                FileSharing.share(context, file, "application/pdf")
                            },
                            onError = {
                                android.widget.Toast.makeText(
                                    context,
                                    it ?: context.getString(R.string.export_failed),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            },
                        )
                    },
                )
            }

            if (!viewModel.state.error.isNullOrBlank()) {
                item(key = "error") {
                    ErrorBanner(
                        message = viewModel.state.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item(key = "balance") {
                BalanceCard(
                    summary = summary,
                    canManageBudget = canManageBudget,
                    onSetInitialBalance = {
                        showBalanceDialog = true
                        viewModel.loadSetup()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "flow") {
                MonthlyFlowCard(
                    history = viewModel.state.history,
                    currentMonth = viewModel.month,
                    loading = viewModel.state.loading,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "categories") {
                CategoryBreakdownCard(
                    month = viewModel.month,
                    breakdown = summary.expenseBreakdown,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (summary.sharedExpenseTotal.signum() > 0) {
                item(key = "shared") {
                    SharedExpensesCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (!summary.hasTransactions && !viewModel.state.loading) {
                item(key = "empty") {
                    EmptyState(
                        text = stringResource(R.string.no_transactions_month),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showBalanceDialog) {
        InitialBalanceDialog(
            setup = viewModel.setup,
            onDismiss = { showBalanceDialog = false },
            onSave = { value ->
                viewModel.saveInitialBalance(
                    value,
                    onSaved = { showBalanceDialog = false },
                    onError = {
                        android.widget.Toast.makeText(
                            context,
                            it ?: context.getString(R.string.error_generic),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            },
        )
    }
}

@Composable
private fun DashboardHeader(
    user: UserProfile,
    month: YearMonth,
    summary: MonthlySummary,
    loading: Boolean,
    exporting: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(
                    Brush.linearGradient(listOf(HeaderStart, HeaderEnd)),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(name = user.fullName)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onRefresh, enabled = !loading) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = onExport, enabled = !exporting) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = stringResource(R.string.export_pdf),
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.dashboard_overview).uppercase(),
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                MonthSelector(
                    month = month,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    contentColor = Color.White,
                )
            }
        }

        IncomeExpenseCard(
            income = summary.totalIncome,
            expense = summary.totalExpense,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun UserAvatar(name: String) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "SL" }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun IncomeExpenseCard(
    income: BigDecimal,
    expense: BigDecimal,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric(
                label = stringResource(R.string.income),
                value = income,
                color = IncomeGreen,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            SummaryMetric(
                label = stringResource(R.string.expenses),
                value = expense,
                color = ExpensePink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: BigDecimal,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = SoftGray,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MoneyFormat.format(value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BalanceCard(
    summary: MonthlySummary,
    canManageBudget: Boolean,
    onSetInitialBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.balance_summary), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BalanceMetric(
                label = stringResource(R.string.initial_balance),
                value = summary.initialBalance,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            BalanceDivider()
            BalanceMetric(
                label = stringResource(R.string.net_balance),
                value = summary.netBalance,
                color = if (summary.netBalance.signum() < 0) ExpensePink else IncomeGreen,
                modifier = Modifier.weight(1f),
            )
            BalanceDivider()
            BalanceMetric(
                label = stringResource(R.string.available_balance),
                value = summary.availableBalance,
                color = if (summary.availableBalance.signum() < 0) ExpensePink else IncomeGreen,
                modifier = Modifier.weight(1f),
            )
        }

        if (canManageBudget) {
            TextButton(onClick = onSetInitialBalance) {
                Text(stringResource(R.string.set_initial_balance))
            }
        }
    }
}

@Composable
private fun BalanceDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun BalanceMetric(
    label: String,
    value: BigDecimal,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = MoneyFormat.format(value),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MonthlyFlowCard(
    history: List<MonthlyHistory>,
    currentMonth: YearMonth,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.cash_flow), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = IncomeGreen, label = stringResource(R.string.income))
            LegendDot(color = ExpensePink, label = stringResource(R.string.expenses))
        }

        Spacer(Modifier.height(16.dp))

        when {
            history.isEmpty() && loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            history.isEmpty() -> EmptyState(stringResource(R.string.no_transactions_month))

            else -> MonthlyBarChart(history = history, currentMonth = currentMonth)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthlyBarChart(history: List<MonthlyHistory>, currentMonth: YearMonth) {
    val maxValue = history.maxOfOrNull { entry ->
        maxOf(entry.income, entry.expense)
    } ?: BigDecimal.ZERO

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        history.forEach { entry ->
            val incomeFraction = fraction(entry.income, maxValue)
            val expenseFraction = fraction(entry.expense, maxValue)
            val isCurrent = entry.month == currentMonth

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(
                    modifier = Modifier.height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    ChartBar(fraction = incomeFraction, color = IncomeGreen)
                    ChartBar(fraction = expenseFraction, color = ExpensePink)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = shortMonth(entry.month),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ChartBar(fraction: Float, color: Color) {
    val barHeight = if (fraction > 0f) {
        (120f * fraction).coerceAtLeast(3f).dp
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .width(7.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

private fun fraction(value: BigDecimal, maxValue: BigDecimal): Float {
    if (maxValue.signum() <= 0) return 0f
    return (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
}

private fun shortMonth(month: YearMonth): String =
    month.month
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .removeSuffix(".")
        .uppercase(Locale.getDefault())

@Composable
private fun CategoryBreakdownCard(
    month: YearMonth,
    breakdown: List<CategorySummary>,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.top_categories), modifier = modifier) {
        if (breakdown.isEmpty()) {
            EmptyState(stringResource(R.string.no_expenses_month))
        } else {
            CategoryDonut(month = month, breakdown = breakdown)
        }
    }
}

@Composable
private fun CategoryDonut(month: YearMonth, breakdown: List<CategorySummary>) {
    val top = breakdown.take(5)
    val others = breakdown.drop(5).fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
    val total = breakdown.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
    val otherLabel = stringResource(R.string.other)
    val items = buildList {
        addAll(top)
        if (others.signum() > 0) {
            add(
                CategorySummary(
                    categoryId = "others",
                    categoryName = otherLabel,
                    amount = others,
                    color = "#94A3B8",
                ),
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2f

                if (total.signum() <= 0) {
                    drawArc(
                        color = Color(0xFFE3E8F2),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                    )
                } else {
                    var start = -90f
                    items.forEach { item ->
                        val sweep = (
                            item.amount.toFloat() / total.toFloat() * 360f
                            ).coerceIn(0f, 360f)

                        drawArc(
                            color = parseColor(item.color),
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = stroke),
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                        )
                        start += sweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = shortMonth(month),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = month.year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(parseColor(item.color)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.categoryName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val percentage = if (total.signum() <= 0) {
                        0f
                    } else {
                        item.amount.toFloat() / total.toFloat() * 100f
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%.0f%%", percentage),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedExpensesCard(summary: MonthlySummary, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.shared_expenses), modifier = modifier) {
        LabeledValue(
            label = stringResource(R.string.shared_expenses),
            value = MoneyFormat.format(summary.sharedExpenseTotal),
        )
        Spacer(Modifier.height(8.dp))
        LabeledValue(
            label = stringResource(R.string.shared_share),
            value = MoneyFormat.format(summary.sharedPersonalShare),
        )
        Spacer(Modifier.height(8.dp))
        LabeledValue(
            label = stringResource(R.string.reimbursement),
            value = MoneyFormat.format(summary.sharedReimbursement),
            valueColor = IncomeGreen,
        )
        Spacer(Modifier.height(8.dp))
        LabeledValue(
            label = stringResource(R.string.personal_expenses),
            value = MoneyFormat.format(summary.personalExpenseTotal),
        )
        Spacer(Modifier.height(8.dp))
        LabeledValue(
            label = stringResource(R.string.personal_available),
            value = MoneyFormat.format(summary.personalAvailableBalance),
        )
    }
}

@Composable
private fun InitialBalanceDialog(
    setup: MonthlyBudgetSetup?,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
) {
    var value by rememberSaveable {
        mutableStateOf(setup?.initialBalance?.let { MoneyFormat.formatPlain(it) } ?: "")
    }

    LaunchedEffect(setup) {
        if (setup != null) {
            value = MoneyFormat.formatPlain(setup.initialBalance)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.set_initial_balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.initial_balance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(onClick = {
                        val parsed = MoneyFormat.parse(value)
                        if (parsed != null) {
                            onSave(parsed)
                        }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
