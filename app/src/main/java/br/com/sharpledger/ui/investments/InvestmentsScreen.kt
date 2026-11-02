package br.com.sharpledger.ui.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.core.Dates
import br.com.sharpledger.core.MoneyFormat
import br.com.sharpledger.data.AddInvestmentDividendRequest
import br.com.sharpledger.data.AddInvestmentTransactionRequest
import br.com.sharpledger.data.CreateInvestmentRequest
import br.com.sharpledger.data.INVESTMENT_CRYPTO
import br.com.sharpledger.data.INVESTMENT_FUND
import br.com.sharpledger.data.INVESTMENT_OTHER
import br.com.sharpledger.data.INVESTMENT_STOCK
import br.com.sharpledger.data.InvestmentDetails
import br.com.sharpledger.data.InvestmentRepository
import br.com.sharpledger.data.InvestmentSummary
import br.com.sharpledger.data.UpdateInvestmentRequest
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.ChoiceDialog
import br.com.sharpledger.ui.components.ConfirmDialog
import br.com.sharpledger.ui.components.DateField
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LabeledValue
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class InvestmentsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val portfolio: List<InvestmentSummary> = emptyList(),
    val refreshing: Boolean = false,
)

class InvestmentsViewModel(private val repository: InvestmentRepository) : ViewModel() {
    var state by mutableStateOf(InvestmentsUiState())
        private set

    var details by mutableStateOf<InvestmentDetails?>(null)
        private set

    var detailsLoading by mutableStateOf(false)
        private set

    var detailsError by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            repository.getPortfolio()
                .onSuccess { state = state.copy(loading = false, portfolio = it) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun loadDetails(id: String) {
        if (details?.summary?.id != id) {
            details = null
        }
        detailsLoading = true
        detailsError = null

        viewModelScope.launch {
            repository.getDetails(id)
                .onSuccess {
                    details = it
                    detailsLoading = false
                }
                .onFailure {
                    detailsLoading = false
                    detailsError = it.message
                }
        }
    }

    fun refreshQuotes(
        investmentId: String?,
        onResult: (Int, Int) -> Unit,
        onError: (String?) -> Unit,
    ) {
        if (state.refreshing) return
        state = state.copy(refreshing = true)

        viewModelScope.launch {
            repository.refreshQuotes(investmentId)
                .onSuccess {
                    state = state.copy(refreshing = false)
                    onResult(it.updated, it.requested)
                    load()
                    investmentId?.let { loadDetails(it) }
                }
                .onFailure {
                    state = state.copy(refreshing = false, error = it.message)
                    onError(it.message)
                }
        }
    }

    fun saveInvestment(
        editing: InvestmentSummary?,
        name: String,
        ticker: String,
        type: String,
        currentPrice: BigDecimal,
        initialQuantity: BigDecimal,
        initialInvested: BigDecimal,
        isActive: Boolean,
        onSaved: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = if (editing == null) {
                repository.create(
                    CreateInvestmentRequest(
                        name = name,
                        ticker = ticker,
                        type = type,
                        currentPrice = currentPrice,
                        initialQuantity = initialQuantity,
                        initialInvestedAmount = initialInvested,
                    ),
                )
            } else {
                repository.update(
                    editing.id,
                    UpdateInvestmentRequest(
                        name = name,
                        ticker = ticker,
                        type = type,
                        currentPrice = currentPrice,
                        isActive = isActive,
                    ),
                )
            }

            result
                .onSuccess {
                    onSaved()
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun deleteInvestment(id: String, onDone: () -> Unit, onError: (String?) -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
                .onSuccess {
                    onDone()
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun addTransaction(
        id: String,
        request: AddInvestmentTransactionRequest,
        onDone: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.addTransaction(id, request)
                .onSuccess {
                    onDone()
                    loadDetails(id)
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun deleteTransaction(id: String, transactionId: String, onError: (String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteTransaction(id, transactionId)
                .onSuccess {
                    loadDetails(id)
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun addDividend(
        id: String,
        request: AddInvestmentDividendRequest,
        onDone: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.addDividend(id, request)
                .onSuccess {
                    onDone()
                    loadDetails(id)
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun deleteDividend(id: String, dividendId: String, onError: (String?) -> Unit) {
        viewModelScope.launch {
            repository.deleteDividend(id, dividendId)
                .onSuccess {
                    loadDetails(id)
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { InvestmentsViewModel(container.investmentRepository) }
        }
    }
}

private enum class InvestmentsMode { LIST, FORM, DETAILS }

@Composable
private fun investmentTypeText(type: String): String = when (type) {
    INVESTMENT_STOCK -> stringResource(R.string.stock)
    INVESTMENT_FUND -> stringResource(R.string.fund)
    INVESTMENT_CRYPTO -> stringResource(R.string.crypto)
    else -> stringResource(R.string.other)
}

@Composable
fun InvestmentsScreen(container: AppContainer) {
    val viewModel: InvestmentsViewModel = viewModel(factory = InvestmentsViewModel.factory(container))
    val context = LocalContext.current

    var mode by rememberSaveable { mutableStateOf(InvestmentsMode.LIST.name) }
    var editing by remember { mutableStateOf<InvestmentSummary?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentMode = InvestmentsMode.valueOf(mode)

    BackHandler(enabled = currentMode != InvestmentsMode.LIST) {
        mode = InvestmentsMode.LIST.name
        editing = null
        selectedId = null
    }

    when (currentMode) {
        InvestmentsMode.LIST -> InvestmentsList(
            viewModel = viewModel,
            onAdd = {
                editing = null
                mode = InvestmentsMode.FORM.name
            },
            onOpen = {
                selectedId = it.id
                mode = InvestmentsMode.DETAILS.name
            },
            onMessage = { message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            },
        )

        InvestmentsMode.FORM -> InvestmentForm(
            viewModel = viewModel,
            editing = editing,
            onBack = {
                mode = if (editing == null) InvestmentsMode.LIST.name else InvestmentsMode.DETAILS.name
                editing = null
            },
            onSaved = {
                mode = InvestmentsMode.LIST.name
                editing = null
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.action_save),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onError = {
                android.widget.Toast.makeText(
                    context,
                    it ?: context.getString(R.string.error_generic),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            },
        )

        InvestmentsMode.DETAILS -> {
            val id = selectedId
            if (id == null) {
                mode = InvestmentsMode.LIST.name
            } else {
                InvestmentDetailsScreen(
                    viewModel = viewModel,
                    investmentId = id,
                    onBack = {
                        mode = InvestmentsMode.LIST.name
                        selectedId = null
                    },
                    onEdit = { summary ->
                        editing = summary
                        mode = InvestmentsMode.FORM.name
                    },
                    onError = {
                        android.widget.Toast.makeText(
                            context,
                            it ?: context.getString(R.string.error_generic),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    },
                    onMessage = {
                        android.widget.Toast.makeText(
                            context,
                            it,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun InvestmentsList(
    viewModel: InvestmentsViewModel,
    onAdd: () -> Unit,
    onOpen: (InvestmentSummary) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    AppScaffold(
        title = stringResource(R.string.investments_title),
        actions = {
            IconButton(
                onClick = {
                    viewModel.refreshQuotes(
                        investmentId = null,
                        onResult = { updated, requested ->
                            onMessage(
                                context.getString(R.string.quotes_updated, updated, requested),
                            )
                        },
                        onError = {},
                    )
                },
                enabled = !viewModel.state.refreshing,
            ) {
                if (viewModel.state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.padding(6.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_quotes))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_investment))
            }
        },
    ) { padding ->
        if (viewModel.state.loading && viewModel.state.portfolio.isEmpty()) {
            LoadingBox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ErrorBanner(viewModel.state.error) }

                val totalValue = viewModel.state.portfolio.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(item.currentValue)
                }
                val totalInvested = viewModel.state.portfolio.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(item.totalInvested)
                }
                val totalReturn = totalValue.subtract(totalInvested)

                if (viewModel.state.portfolio.isNotEmpty()) {
                    item {
                        SectionCard(title = stringResource(R.string.portfolio_total)) {
                            LabeledValue(
                                label = stringResource(R.string.current_value),
                                value = MoneyFormat.format(totalValue),
                            )
                            Spacer(Modifier.height(8.dp))
                            LabeledValue(
                                label = stringResource(R.string.invested),
                                value = MoneyFormat.format(totalInvested),
                            )
                            Spacer(Modifier.height(8.dp))
                            LabeledValue(
                                label = stringResource(R.string.total_return),
                                value = MoneyFormat.format(totalReturn),
                                valueColor = if (totalReturn.signum() < 0) {
                                    Color(0xFFDC3545)
                                } else {
                                    Color(0xFF198754)
                                },
                            )
                        }
                    }
                }

                if (viewModel.state.portfolio.isEmpty()) {
                    item { EmptyState(stringResource(R.string.no_investments)) }
                }

                items(viewModel.state.portfolio, key = { it.id }) { investment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(investment) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${investment.ticker} • ${investment.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = investmentTypeText(investment.type) +
                                            if (!investment.isActive) {
                                                " • ${stringResource(R.string.inactive_investment)}"
                                            } else {
                                                ""
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = MoneyFormat.format(investment.currentValue),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${investment.returnPercentage.stripTrailingZeros().toPlainString()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (investment.returnPercentage.signum() < 0) {
                                            Color(0xFFDC3545)
                                        } else {
                                            Color(0xFF198754)
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${stringResource(R.string.quantity)}: ${
                                    MoneyFormat.formatQuantity(investment.totalQuantity)
                                } • ${stringResource(R.string.current_price)}: ${
                                    MoneyFormat.format(investment.currentPrice)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentForm(
    viewModel: InvestmentsViewModel,
    editing: InvestmentSummary?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onError: (String?) -> Unit,
) {
    val key = editing?.id ?: "new"

    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: "") }
    var ticker by rememberSaveable(key) { mutableStateOf(editing?.ticker ?: "") }
    var type by rememberSaveable(key) { mutableStateOf(editing?.type ?: INVESTMENT_STOCK) }
    var price by rememberSaveable(key) {
        mutableStateOf(editing?.currentPrice?.let { MoneyFormat.formatPlain(it) } ?: "")
    }
    var quantity by rememberSaveable(key) { mutableStateOf("") }
    var invested by rememberSaveable(key) { mutableStateOf("") }
    var active by rememberSaveable(key) { mutableStateOf(editing?.isActive ?: true) }
    var typeDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AppScaffold(
        title = stringResource(
            if (editing == null) R.string.add_investment else R.string.edit_investment,
        ),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorBanner(localError ?: viewModel.state.error)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.investment_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = it.uppercase() },
                label = { Text(stringResource(R.string.ticker)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            TextButton(onClick = { typeDialog = true }) {
                Text("${stringResource(R.string.type)}: ${investmentTypeText(type)}")
            }

            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(stringResource(R.string.current_price)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (editing == null) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.initial_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = invested,
                    onValueChange = { invested = it },
                    label = { Text(stringResource(R.string.initial_invested)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.active))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    localError = null

                    val parsedPrice = MoneyFormat.parse(price)
                    val parsedQuantity = MoneyFormat.parse(quantity) ?: BigDecimal.ZERO
                    val parsedInvested = MoneyFormat.parse(invested) ?: BigDecimal.ZERO

                    if (name.isBlank()) {
                        localError = "Informe o nome do investimento."
                        return@Button
                    }

                    if (ticker.isBlank()) {
                        localError = "Informe o ticker do investimento."
                        return@Button
                    }

                    if (parsedPrice == null || parsedPrice.signum() <= 0) {
                        localError = "A cotação atual deve ser maior que zero."
                        return@Button
                    }

                    if (editing == null) {
                        if (parsedQuantity.signum() > 0 && parsedInvested.signum() <= 0) {
                            localError = "Informe o valor investido da posição inicial."
                            return@Button
                        }
                        if (parsedQuantity.signum() == 0 && parsedInvested.signum() > 0) {
                            localError = "Informe a quantidade da posição inicial."
                            return@Button
                        }
                    }

                    viewModel.saveInvestment(
                        editing = editing,
                        name = name.trim(),
                        ticker = ticker.trim(),
                        type = type,
                        currentPrice = parsedPrice,
                        initialQuantity = parsedQuantity,
                        initialInvested = parsedInvested,
                        isActive = active,
                        onSaved = onSaved,
                        onError = onError,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (typeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.type),
            options = listOf(
                INVESTMENT_STOCK to stringResource(R.string.stock),
                INVESTMENT_FUND to stringResource(R.string.fund),
                INVESTMENT_CRYPTO to stringResource(R.string.crypto),
                INVESTMENT_OTHER to stringResource(R.string.other),
            ),
            selected = type,
            onSelect = { type = it },
            onDismiss = { typeDialog = false },
        )
    }
}

@Composable
private fun InvestmentDetailsScreen(
    viewModel: InvestmentsViewModel,
    investmentId: String,
    onBack: () -> Unit,
    onEdit: (InvestmentSummary) -> Unit,
    onError: (String?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var showContribution by remember { mutableStateOf(false) }
    var showDividend by remember { mutableStateOf(false) }
    var confirmDeleteInvestment by remember { mutableStateOf(false) }
    var deleteTransactionId by remember { mutableStateOf<String?>(null) }
    var deleteDividendId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(investmentId) { viewModel.loadDetails(investmentId) }

    val details = viewModel.details

    AppScaffold(
        title = details?.summary?.ticker ?: stringResource(R.string.investments_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                viewModel.refreshQuotes(
                    investmentId = investmentId,
                    onResult = { updated, requested ->
                        onMessage(context.getString(R.string.quotes_updated, updated, requested))
                    },
                    onError = onError,
                )
            }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_quotes))
            }
            if (details != null) {
                IconButton(onClick = { onEdit(details.summary) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { confirmDeleteInvestment = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) { padding ->
        if (viewModel.detailsLoading && details == null) {
            LoadingBox(modifier = Modifier.padding(padding))
        } else if (details == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                ErrorBanner(viewModel.detailsError)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ErrorBanner(viewModel.detailsError) }

                item {
                    SectionCard(title = details.summary.name) {
                        LabeledValue(
                            label = stringResource(R.string.current_price),
                            value = MoneyFormat.format(details.summary.currentPrice),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.quantity),
                            value = MoneyFormat.formatQuantity(details.summary.totalQuantity),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.average_price),
                            value = MoneyFormat.format(details.summary.averagePrice),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.invested),
                            value = MoneyFormat.format(details.summary.totalInvested),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.current_value),
                            value = MoneyFormat.format(details.summary.currentValue),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.dividends),
                            value = MoneyFormat.format(details.summary.totalDividends),
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.total_return),
                            value = MoneyFormat.format(details.summary.totalReturn),
                            valueColor = if (details.summary.totalReturn.signum() < 0) {
                                Color(0xFFDC3545)
                            } else {
                                Color(0xFF198754)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledValue(
                            label = stringResource(R.string.return_percentage),
                            value = "${details.summary.returnPercentage.stripTrailingZeros().toPlainString()}%",
                            valueColor = if (details.summary.returnPercentage.signum() < 0) {
                                Color(0xFFDC3545)
                            } else {
                                Color(0xFF198754)
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showContribution = true }) {
                                Text(stringResource(R.string.add_contribution))
                            }
                            Button(onClick = { showDividend = true }) {
                                Text(stringResource(R.string.add_dividend))
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.contributions)) {
                        if (details.transactions.isEmpty()) {
                            EmptyState(stringResource(R.string.no_contributions))
                        } else {
                            details.transactions.forEach { transaction ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(transaction.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${Dates.formatIso(transaction.date)} • ${
                                                MoneyFormat.formatQuantity(transaction.quantity)
                                            } x ${MoneyFormat.format(transaction.pricePerShare)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        MoneyFormat.format(transaction.total),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    IconButton(onClick = { deleteTransactionId = transaction.id }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.dividends)) {
                        if (details.dividends.isEmpty()) {
                            EmptyState(stringResource(R.string.no_dividends))
                        } else {
                            details.dividends.forEach { dividend ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dividend.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            Dates.formatIso(dividend.date),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        MoneyFormat.format(dividend.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    IconButton(onClick = { deleteDividendId = dividend.id }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContribution) {
        ContributionDialog(
            onDismiss = { showContribution = false },
            onSave = { description, quantity, price, fees, date, notes ->
                viewModel.addTransaction(
                    id = investmentId,
                    request = AddInvestmentTransactionRequest(
                        description = description,
                        quantity = quantity,
                        pricePerShare = price,
                        fees = fees,
                        date = date,
                        notes = notes,
                    ),
                    onDone = { showContribution = false },
                    onError = onError,
                )
            },
        )
    }

    if (showDividend) {
        DividendDialog(
            onDismiss = { showDividend = false },
            onSave = { description, amount, date ->
                viewModel.addDividend(
                    id = investmentId,
                    request = AddInvestmentDividendRequest(description, amount, date),
                    onDone = { showDividend = false },
                    onError = onError,
                )
            },
        )
    }

    if (confirmDeleteInvestment) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.delete_investment_confirm),
            onConfirm = {
                confirmDeleteInvestment = false
                viewModel.deleteInvestment(
                    id = investmentId,
                    onDone = { onBack() },
                    onError = onError,
                )
            },
            onDismiss = { confirmDeleteInvestment = false },
        )
    }

    val transactionId = deleteTransactionId
    if (transactionId != null) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.delete_contribution_confirm),
            onConfirm = {
                deleteTransactionId = null
                viewModel.deleteTransaction(investmentId, transactionId, onError)
            },
            onDismiss = { deleteTransactionId = null },
        )
    }

    val dividendId = deleteDividendId
    if (dividendId != null) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.delete_dividend_confirm),
            onConfirm = {
                deleteDividendId = null
                viewModel.deleteDividend(investmentId, dividendId, onError)
            },
            onDismiss = { deleteDividendId = null },
        )
    }
}

@Composable
private fun ContributionDialog(
    onDismiss: () -> Unit,
    onSave: (String, BigDecimal, BigDecimal, BigDecimal, String, String?) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var fees by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.add_contribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                ErrorBanner(error)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.price_per_share)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fees,
                    onValueChange = { fees = it },
                    label = { Text(stringResource(R.string.fees)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                DateField(
                    label = stringResource(R.string.date),
                    value = date,
                    onValueChange = { date = it },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
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
                        error = null
                        val parsedQuantity = MoneyFormat.parse(quantity)
                        val parsedPrice = MoneyFormat.parse(price)
                        val parsedFees = MoneyFormat.parse(fees) ?: BigDecimal.ZERO

                        if (description.isBlank()) {
                            error = "Informe a descrição do aporte."
                            return@Button
                        }
                        if (parsedQuantity == null || parsedQuantity.signum() <= 0) {
                            error = "A quantidade deve ser maior que zero."
                            return@Button
                        }
                        if (parsedPrice == null || parsedPrice.signum() <= 0) {
                            error = "O preço por cota deve ser maior que zero."
                            return@Button
                        }
                        if (parsedFees.signum() < 0) {
                            error = "As taxas não podem ser negativas."
                            return@Button
                        }
                        if (Dates.parseIso(date) == null) {
                            error = "Informe uma data válida."
                            return@Button
                        }

                        onSave(
                            description.trim(),
                            parsedQuantity,
                            parsedPrice,
                            parsedFees,
                            date,
                            notes.trim().ifEmpty { null },
                        )
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun DividendDialog(
    onDismiss: () -> Unit,
    onSave: (String, BigDecimal, String) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.add_dividend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                ErrorBanner(error)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                DateField(
                    label = stringResource(R.string.date),
                    value = date,
                    onValueChange = { date = it },
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
                        error = null
                        val parsedAmount = MoneyFormat.parse(amount)

                        if (description.isBlank()) {
                            error = "Informe a descrição do provento."
                            return@Button
                        }
                        if (parsedAmount == null || parsedAmount.signum() <= 0) {
                            error = "O valor do provento deve ser maior que zero."
                            return@Button
                        }
                        if (Dates.parseIso(date) == null) {
                            error = "Informe uma data válida."
                            return@Button
                        }

                        onSave(description.trim(), parsedAmount, date)
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
