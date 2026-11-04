package br.com.sharpledger.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.core.Dates
import br.com.sharpledger.core.FileSharing
import br.com.sharpledger.core.MoneyFormat
import br.com.sharpledger.data.AttachmentRepository
import br.com.sharpledger.data.Category
import br.com.sharpledger.data.CategoryRepository
import br.com.sharpledger.data.CostCenter
import br.com.sharpledger.data.CostCenterRepository
import br.com.sharpledger.data.ExpenseAttachment
import br.com.sharpledger.data.MonthTransactions
import br.com.sharpledger.data.SaveTransactionRequest
import br.com.sharpledger.data.TransactionItem
import br.com.sharpledger.data.TransactionRepository
import br.com.sharpledger.data.TYPE_EXPENSE
import br.com.sharpledger.data.TYPE_INCOME
import br.com.sharpledger.data.UserProfile
import br.com.sharpledger.data.isIncome
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.ConfirmDialog
import br.com.sharpledger.ui.components.DateField
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.MonthSelector
import br.com.sharpledger.ui.components.SelectField
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth

data class TransactionsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: MonthTransactions? = null,
    val categories: List<Category> = emptyList(),
    val costCenters: List<CostCenter> = emptyList(),
    val saving: Boolean = false,
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val costCenterRepository: CostCenterRepository,
) : ViewModel() {
    var state by mutableStateOf(TransactionsUiState())
        private set

    var month by mutableStateOf(YearMonth.now())
        private set

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
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            val categoriesResult = categoryRepository.getCategories(includeInactive = true)
            val costCentersResult = costCenterRepository.getCostCenters(includeInactive = true)
            val monthResult = transactionRepository.getMonth(month.year, month.monthValue)

            val error = monthResult.exceptionOrNull()?.message
                ?: categoriesResult.exceptionOrNull()?.message

            state = state.copy(
                loading = false,
                error = error,
                data = monthResult.getOrNull(),
                categories = categoriesResult.getOrDefault(emptyList()),
                costCenters = costCentersResult.getOrDefault(emptyList()),
            )
        }
    }

    fun save(
        editingId: String?,
        request: SaveTransactionRequest,
        onSaved: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        state = state.copy(saving = true)

        viewModelScope.launch {
            val result = if (editingId == null) {
                transactionRepository.create(request)
            } else {
                transactionRepository.update(editingId, request)
            }

            result
                .onSuccess {
                    state = state.copy(saving = false)
                    onSaved()
                    load()
                }
                .onFailure {
                    state = state.copy(saving = false)
                    onError(it.message)
                }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            transactionRepository.delete(id)
                .onSuccess {
                    onDone()
                    load()
                }
                .onFailure { state = state.copy(error = it.message) }
        }
    }

    fun deleteInstallment(purchaseId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            transactionRepository.deleteInstallment(purchaseId)
                .onSuccess {
                    onDone()
                    load()
                }
                .onFailure { state = state.copy(error = it.message) }
        }
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                TransactionsViewModel(
                    container.transactionRepository,
                    container.categoryRepository,
                    container.costCenterRepository,
                )
            }
        }
    }
}

private enum class TransactionsMode { LIST, FORM, ATTACHMENTS }

@Composable
fun TransactionsScreen(container: AppContainer, user: UserProfile) {
    val viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.factory(container))
    val context = LocalContext.current

    var mode by rememberSaveable { mutableStateOf(TransactionsMode.LIST.name) }
    var editing by remember { mutableStateOf<TransactionItem?>(null) }
    var attachmentsFor by remember { mutableStateOf<TransactionItem?>(null) }

    val currentMode = TransactionsMode.valueOf(mode)

    BackHandler(enabled = currentMode != TransactionsMode.LIST) {
        mode = TransactionsMode.LIST.name
        editing = null
        attachmentsFor = null
        viewModel.clearError()
    }

    when (currentMode) {
        TransactionsMode.LIST -> TransactionsList(
            viewModel = viewModel,
            onAdd = {
                editing = null
                mode = TransactionsMode.FORM.name
            },
            onEdit = {
                editing = it
                mode = TransactionsMode.FORM.name
            },
            onAttachments = {
                attachmentsFor = it
                mode = TransactionsMode.ATTACHMENTS.name
            },
            onDelete = { item ->
                if (item.isInstallment) {
                    viewModel.deleteInstallment(item.installmentPurchaseId.orEmpty()) {}
                } else {
                    viewModel.delete(item.id) {}
                }
            },
            context = context,
        )

        TransactionsMode.FORM -> TransactionForm(
            viewModel = viewModel,
            editing = editing,
            onBack = {
                mode = TransactionsMode.LIST.name
                editing = null
                viewModel.clearError()
            },
            onSaved = {
                mode = TransactionsMode.LIST.name
                editing = null
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.transaction_saved),
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

        TransactionsMode.ATTACHMENTS -> {
            val item = attachmentsFor
            if (item != null) {
                AttachmentsScreen(
                    container = container,
                    transaction = item,
                    onBack = {
                        mode = TransactionsMode.LIST.name
                        attachmentsFor = null
                    },
                )
            } else {
                mode = TransactionsMode.LIST.name
            }
        }
    }
}

@Composable
private fun TransactionsList(
    viewModel: TransactionsViewModel,
    onAdd: () -> Unit,
    onEdit: (TransactionItem) -> Unit,
    onAttachments: (TransactionItem) -> Unit,
    onDelete: (TransactionItem) -> Unit,
    context: android.content.Context,
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var actionsFor by remember { mutableStateOf<TransactionItem?>(null) }
    var confirmDelete by remember { mutableStateOf<TransactionItem?>(null) }

    val data = viewModel.state.data

    AppScaffold(
        title = stringResource(R.string.transactions_title),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_transaction))
            }
        },
    ) { padding ->
        if (viewModel.state.loading && data == null) {
            LoadingBox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthSelector(
                        month = viewModel.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }

                item { ErrorBanner(viewModel.state.error) }

                if (data != null) {
                    item {
                        SectionCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.income),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        MoneyFormat.format(data.totalIncome),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF198754),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.expenses),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        MoneyFormat.format(data.totalExpense),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFDC3545),
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.initial_balance),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        MoneyFormat.format(data.initialBalance),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.available_balance),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        MoneyFormat.format(data.availableBalance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filter == "all",
                                onClick = { filter = "all" },
                                label = { Text(stringResource(R.string.all)) },
                            )
                            FilterChip(
                                selected = filter == TYPE_INCOME,
                                onClick = { filter = TYPE_INCOME },
                                label = { Text(stringResource(R.string.income)) },
                            )
                            FilterChip(
                                selected = filter == TYPE_EXPENSE,
                                onClick = { filter = TYPE_EXPENSE },
                                label = { Text(stringResource(R.string.expenses)) },
                            )
                        }
                    }

                    val filtered = data.transactions.filter {
                        filter == "all" || it.type == filter
                    }

                    if (filtered.isEmpty()) {
                        item { EmptyState(stringResource(R.string.no_transactions_month)) }
                    } else {
                        items(filtered, key = { it.id }) { item ->
                            TransactionCard(
                                item = item,
                                onClick = { actionsFor = item },
                            )
                        }
                    }
                }
            }
        }
    }

    val actionItem = actionsFor
    if (actionItem != null) {
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(actionItem.description) },
            text = {
                Column {
                    Text(
                        text = "${MoneyFormat.format(actionItem.amount)} • ${Dates.formatIso(actionItem.date)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = actionItem.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            actionsFor = null
                            onEdit(actionItem)
                        },
                        enabled = !actionItem.isInstallment,
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_edit))
                    }
                    TextButton(
                        onClick = {
                            actionsFor = null
                            onAttachments(actionItem)
                        },
                        enabled = !isIncome(actionItem.type),
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.attachments))
                    }
                    TextButton(
                        onClick = {
                            actionsFor = null
                            confirmDelete = actionItem
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (actionItem.isInstallment) {
                                stringResource(R.string.delete_installment)
                            } else {
                                stringResource(R.string.action_delete)
                            },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }

    val deleteTarget = confirmDelete
    if (deleteTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = if (deleteTarget.isInstallment) {
                stringResource(R.string.delete_installment_confirm)
            } else {
                stringResource(R.string.delete_transaction_confirm)
            },
            onConfirm = {
                onDelete(deleteTarget)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun TransactionCard(item: TransactionItem, onClick: () -> Unit) {
    val isIncomeItem = isIncome(item.type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = buildString {
                        append(item.categoryName)
                        if (!item.costCenterName.isNullOrBlank()) {
                            append(" • ").append(item.costCenterName)
                        }
                        append(" • ").append(Dates.formatIso(item.date))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isShared && !isIncomeItem) {
                    Text(
                        text = stringResource(
                            R.string.share_of,
                            item.splitPercentage.stripTrailingZeros().toPlainString(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (item.isInstallment) {
                    Text(
                        text = stringResource(
                            R.string.installment_badge,
                            item.installmentNumber,
                            item.installmentCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncomeItem) "+ " else "- ") + MoneyFormat.format(item.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncomeItem) Color(0xFF198754) else Color(0xFFDC3545),
                )
                if (item.hasAttachments) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionForm(
    viewModel: TransactionsViewModel,
    editing: TransactionItem?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onError: (String?) -> Unit,
) {
    val key = editing?.id ?: "new"
    val categories = viewModel.state.categories
        .filter { editing != null || it.isActive }
    val costCenters = viewModel.state.costCenters
        .filter { editing != null || it.isActive }

    var description by rememberSaveable(key) { mutableStateOf(editing?.description ?: "") }
    var amount by rememberSaveable(key) {
        mutableStateOf(editing?.amount?.let { MoneyFormat.formatPlain(it) } ?: "")
    }
    var date by rememberSaveable(key) { mutableStateOf(editing?.date ?: Dates.today()) }
    var categoryId by rememberSaveable(key) { mutableStateOf(editing?.categoryId ?: "") }
    var costCenterId by rememberSaveable(key) { mutableStateOf(editing?.costCenterId ?: "") }
    var isShared by rememberSaveable(key) { mutableStateOf(editing?.isShared ?: false) }
    var split by rememberSaveable(key) {
        mutableStateOf(
            editing?.splitPercentage?.takeIf { it.signum() > 0 }
                ?.let { MoneyFormat.formatPlain(it) }
                ?: "50",
        )
    }
    var notes by rememberSaveable(key) { mutableStateOf(editing?.notes ?: "") }
    var installments by rememberSaveable(key) { mutableStateOf("1") }
    var localError by remember { mutableStateOf<String?>(null) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val isExpense = selectedCategory == null || selectedCategory.type == TYPE_EXPENSE
    val isEditInstallment = editing?.isInstallment == true

    AppScaffold(
        title = stringResource(
            if (editing == null) R.string.add_transaction else R.string.edit_transaction,
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
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            DateField(label = stringResource(R.string.date), value = date, onValueChange = { date = it })

            SelectField(
                label = stringResource(R.string.category),
                selectedText = selectedCategory?.let {
                    it.name + if (!it.isActive) " (${stringResource(R.string.inactive)})" else ""
                },
                options = categories.map { it.id to it.name },
                onSelect = { categoryId = it },
            )

            val selectedCostCenter = costCenters.firstOrNull { it.id == costCenterId }
            SelectField(
                label = stringResource(R.string.cost_center),
                selectedText = selectedCostCenter?.name
                    ?: stringResource(R.string.cost_center_none),
                options = listOf("" to stringResource(R.string.cost_center_none)) +
                    costCenters.map { it.id to it.name },
                onSelect = { costCenterId = it },
            )

            if (isExpense && selectedCostCenter?.closingDay != null &&
                selectedCostCenter.dueDay != null
            ) {
                Text(
                    text = stringResource(R.string.card_competence_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpense) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.shared_expense))
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }

                if (isShared) {
                    OutlinedTextField(
                        value = split,
                        onValueChange = { split = it },
                        label = { Text(stringResource(R.string.split_percentage)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                if (editing == null && !isEditInstallment) {
                    OutlinedTextField(
                        value = installments,
                        onValueChange = { installments = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.installments)) },
                        supportingText = { Text(stringResource(R.string.installments_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (isEditInstallment) {
                Text(
                    text = stringResource(R.string.installments_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    localError = null
                    val parsedAmount = MoneyFormat.parse(amount)
                    val parsedSplit = MoneyFormat.parse(split) ?: BigDecimal("100")
                    val parsedInstallments = installments.toIntOrNull() ?: 1

                    if (description.isBlank()) {
                        localError = "Informe a descrição do lançamento."
                        return@Button
                    }

                    if (parsedAmount == null || parsedAmount.signum() <= 0) {
                        localError = "O valor deve ser maior que zero."
                        return@Button
                    }

                    if (Dates.parseIso(date) == null) {
                        localError = "Informe uma data válida."
                        return@Button
                    }

                    val category = categories.firstOrNull { it.id == categoryId }
                    if (category == null) {
                        localError = "Selecione uma categoria válida."
                        return@Button
                    }

                    if (isShared && (parsedSplit.signum() < 0 || parsedSplit > BigDecimal("100"))) {
                        localError = "O percentual de divisão deve estar entre 0 e 100."
                        return@Button
                    }

                    if (editing == null && category.type == TYPE_EXPENSE &&
                        (parsedInstallments < 1 || parsedInstallments > 120)
                    ) {
                        localError = "O número de parcelas deve estar entre 1 e 120."
                        return@Button
                    }

                    val request = SaveTransactionRequest(
                        categoryId = category.id,
                        description = description.trim(),
                        amount = parsedAmount,
                        date = date,
                        isShared = isShared && category.type == TYPE_EXPENSE,
                        splitPercentage = if (isShared && category.type == TYPE_EXPENSE) {
                            parsedSplit
                        } else {
                            BigDecimal("100")
                        },
                        notes = notes.trim().ifEmpty { null },
                        installmentCount = if (editing == null && category.type == TYPE_EXPENSE) {
                            parsedInstallments
                        } else {
                            1
                        },
                        costCenterId = costCenterId.ifBlank { null },
                    )

                    viewModel.save(
                        editingId = editing?.id,
                        request = request,
                        onSaved = onSaved,
                        onError = onError,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.saving,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun AttachmentsScreen(
    container: AppContainer,
    transaction: TransactionItem,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: AttachmentRepository = container.attachmentRepository

    var attachments by remember { mutableStateOf<List<ExpenseAttachment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            repository.getForTransaction(transaction.id)
                .onSuccess {
                    attachments = it
                    error = null
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(transaction.id) { reload() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uploading = true
                repository.upload(transaction.id, uris)
                    .onSuccess {
                        error = null
                        reload()
                    }
                    .onFailure { error = it.message }
                uploading = false
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.attachments),
        onBack = onBack,
        floatingActionButton = {
            if (!isIncome(transaction.type)) {
                FloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    content = {
                        if (uploading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.padding(12.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = stringResource(R.string.add_attachment),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = transaction.description,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ErrorBanner(error)

            if (isIncome(transaction.type)) {
                Text(
                    text = stringResource(R.string.attachments_only_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.attachment_limit, AttachmentRepository.MAX_FILES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (loading) {
                LoadingBox()
            } else if (attachments.isEmpty()) {
                EmptyState(stringResource(R.string.no_attachments))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(attachments, key = { it.id }) { attachment ->
                        Card {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${attachment.sizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        repository.download(attachment.id, attachment.fileName)
                                            .onSuccess {
                                                FileSharing.share(
                                                    context,
                                                    it,
                                                    FileSharing.mimeTypeFor(attachment.fileName),
                                                )
                                            }
                                            .onFailure { error = it.message }
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = stringResource(R.string.action_download),
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        repository.delete(attachment.id)
                                            .onSuccess { reload() }
                                            .onFailure { error = it.message }
                                    }
                                }) {
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
