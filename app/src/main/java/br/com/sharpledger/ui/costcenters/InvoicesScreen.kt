package br.com.sharpledger.ui.costcenters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.core.Dates
import br.com.sharpledger.core.MoneyFormat
import br.com.sharpledger.data.CostCenter
import br.com.sharpledger.data.InvoiceDetail
import br.com.sharpledger.data.InvoiceSummary
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun CardInvoicesScreen(
    container: AppContainer,
    costCenter: CostCenter,
    onBack: () -> Unit,
) {
    val repository = container.costCenterRepository
    val scope = rememberCoroutineScope()

    var loading by remember(costCenter.id) { mutableStateOf(true) }
    var error by remember(costCenter.id) { mutableStateOf<String?>(null) }
    var invoices by remember(costCenter.id) { mutableStateOf<List<InvoiceSummary>>(emptyList()) }
    var selected by remember(costCenter.id) { mutableStateOf<InvoiceSummary?>(null) }
    var detail by remember(costCenter.id) { mutableStateOf<InvoiceDetail?>(null) }
    var loadingDetail by remember(costCenter.id) { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            repository.getInvoices(costCenter.id)
                .onSuccess { invoices = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(costCenter.id) { reload() }

    LaunchedEffect(selected) {
        val target = selected
        if (target != null) {
            loadingDetail = true
            detail = null
            repository.getInvoice(costCenter.id, target.year, target.month)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            loadingDetail = false
        }
    }

    BackHandler(enabled = true) {
        if (selected != null) {
            selected = null
            detail = null
        } else {
            onBack()
        }
    }

    if (selected != null) {
        InvoiceDetailContent(
            summary = selected!!,
            detail = detail,
            loading = loadingDetail,
            error = error,
            onBack = {
                selected = null
                detail = null
            },
        )
    } else {
        InvoiceListContent(
            costCenter = costCenter,
            invoices = invoices,
            loading = loading,
            error = error,
            onBack = onBack,
            onSelect = { selected = it },
        )
    }
}

@Composable
private fun InvoiceListContent(
    costCenter: CostCenter,
    invoices: List<InvoiceSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSelect: (InvoiceSummary) -> Unit,
) {
    AppScaffold(title = costCenter.name, onBack = onBack) { padding ->
        if (loading && invoices.isEmpty()) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@AppScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.invoices_title)) {
                    Text(
                        text = billingDaysLabel(costCenter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { ErrorBanner(error) }

            if (invoices.isEmpty()) {
                item { EmptyState(stringResource(R.string.invoice_empty)) }
            }

            items(invoices, key = { "${it.year}-${it.month}" }) { invoice ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(invoice) },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = monthLabel(invoice.month, invoice.year) +
                                    if (invoice.isCurrent) {
                                        "  •  ${stringResource(R.string.invoice_current)}"
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (invoice.isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (invoice.isCurrent) FontWeight.SemiBold else null,
                            )
                            Text(
                                text = stringResource(
                                    R.string.invoice_due,
                                    Dates.formatIso(invoice.dueDate),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = MoneyFormat.format(invoice.total),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceDetailContent(
    summary: InvoiceSummary,
    detail: InvoiceDetail?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
) {
    AppScaffold(title = monthLabel(summary.month, summary.year), onBack = onBack) { padding ->
        if (loading && detail == null) {
            LoadingBox(modifier = Modifier.padding(padding))
            return@AppScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.invoice_total)) {
                    Text(
                        text = MoneyFormat.format(detail?.summary?.total ?: summary.total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.invoice_due, Dates.formatIso(summary.dueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.invoice_closing,
                            Dates.formatIso(summary.closingDate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { ErrorBanner(error) }

            val transactions = detail?.transactions.orEmpty()
            if (transactions.isEmpty() && !loading) {
                item { EmptyState(stringResource(R.string.no_transactions_month)) }
            }

            items(transactions, key = { it.id }) { transaction ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction.description,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${transaction.categoryName} • ${Dates.formatIso(transaction.date)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = MoneyFormat.format(transaction.amount),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun monthLabel(month: Int, year: Int): String = "%02d/%d".format(month, year)

@Composable
private fun billingDaysLabel(costCenter: CostCenter): String {
    val closing = costCenter.closingDay?.let {
        "${stringResource(R.string.closing_day_short)} $it"
    }
    val due = costCenter.dueDay?.let {
        "${stringResource(R.string.due_day_short)} $it"
    }

    return listOfNotNull(closing, due).joinToString(" • ")
}
