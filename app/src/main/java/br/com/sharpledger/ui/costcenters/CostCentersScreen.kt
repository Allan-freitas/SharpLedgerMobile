package br.com.sharpledger.ui.costcenters

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.data.CostCenter
import br.com.sharpledger.data.CostCenterRepository
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch

data class CostCentersUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val costCenters: List<CostCenter> = emptyList(),
    val includeInactive: Boolean = false,
)

class CostCentersViewModel(private val repository: CostCenterRepository) : ViewModel() {
    var state by mutableStateOf(CostCentersUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            repository.getCostCenters(state.includeInactive)
                .onSuccess { state = state.copy(loading = false, costCenters = it) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun setIncludeInactive(value: Boolean) {
        state = state.copy(includeInactive = value)
        load()
    }

    fun save(
        editing: CostCenter?,
        name: String,
        isActive: Boolean,
        closingDay: Int?,
        dueDay: Int?,
        onSaved: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        if (name.isBlank()) {
            onError("Informe o nome do centro de custo.")
            return
        }

        if (name.length > 80) {
            onError("O nome do centro de custo deve ter no máximo 80 caracteres.")
            return
        }

        if (closingDay != null && closingDay !in 1..31) {
            onError("O dia de fechamento deve estar entre 1 e 31.")
            return
        }

        if (dueDay != null && dueDay !in 1..31) {
            onError("O dia de vencimento deve estar entre 1 e 31.")
            return
        }

        viewModelScope.launch {
            val result = if (editing == null) {
                repository.create(name, isActive, closingDay, dueDay)
            } else {
                repository.update(editing.id, name, isActive, closingDay, dueDay)
            }

            result
                .onSuccess {
                    onSaved()
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun setActive(costCenter: CostCenter, active: Boolean) {
        viewModelScope.launch {
            repository.setActive(costCenter.id, active)
                .onSuccess { load() }
                .onFailure { state = state.copy(error = it.message) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CostCentersViewModel(container.costCenterRepository) }
        }
    }
}

private enum class CostCentersMode { LIST, FORM }

@Composable
fun CostCentersScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CostCentersViewModel = viewModel(factory = CostCentersViewModel.factory(container))
    val context = LocalContext.current

    var mode by rememberSaveable { mutableStateOf(CostCentersMode.LIST.name) }
    var editing by remember { mutableStateOf<CostCenter?>(null) }
    var invoicesFor by remember { mutableStateOf<CostCenter?>(null) }

    val currentMode = CostCentersMode.valueOf(mode)

    BackHandler(enabled = currentMode == CostCentersMode.FORM) {
        mode = CostCentersMode.LIST.name
        editing = null
    }

    invoicesFor?.let { card ->
        CardInvoicesScreen(
            container = container,
            costCenter = card,
            onBack = { invoicesFor = null },
        )
        return
    }

    if (currentMode == CostCentersMode.FORM) {
        CostCenterForm(
            viewModel = viewModel,
            editing = editing,
            onBack = {
                mode = CostCentersMode.LIST.name
                editing = null
            },
            onSaved = {
                mode = CostCentersMode.LIST.name
                editing = null
            },
            onError = {
                android.widget.Toast.makeText(
                    context,
                    it ?: context.getString(R.string.error_generic),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            },
        )
        return
    }

    AppScaffold(
        title = stringResource(R.string.cost_centers_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                mode = CostCentersMode.FORM.name
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_cost_center))
            }
        },
    ) { padding ->
        if (viewModel.state.loading && viewModel.state.costCenters.isEmpty()) {
            LoadingBox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ErrorBanner(viewModel.state.error) }

                item {
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.show_inactive))
                            Switch(
                                checked = viewModel.state.includeInactive,
                                onCheckedChange = viewModel::setIncludeInactive,
                            )
                        }
                    }
                }

                if (viewModel.state.costCenters.isEmpty()) {
                    item { EmptyState(stringResource(R.string.no_cost_centers)) }
                }

                items(viewModel.state.costCenters, key = { it.id }) { costCenter ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = costCenter
                                mode = CostCentersMode.FORM.name
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = costCenter.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                val closingLabel = stringResource(R.string.closing_day_short)
                                val dueLabel = stringResource(R.string.due_day_short)
                                val inactiveLabel = stringResource(R.string.inactive)
                                val details = listOfNotNull(
                                    costCenter.closingDay?.let { "$closingLabel $it" },
                                    costCenter.dueDay?.let { "$dueLabel $it" },
                                    if (!costCenter.isActive) inactiveLabel else null,
                                ).joinToString(" • ")
                                if (details.isNotBlank()) {
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (costCenter.closingDay != null && costCenter.dueDay != null) {
                                TextButton(onClick = { invoicesFor = costCenter }) {
                                    Text(stringResource(R.string.invoices_title))
                                }
                            }
                            Switch(
                                checked = costCenter.isActive,
                                onCheckedChange = { viewModel.setActive(costCenter, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostCenterForm(
    viewModel: CostCentersViewModel,
    editing: CostCenter?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onError: (String?) -> Unit,
) {
    val key = editing?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: "") }
    var active by rememberSaveable(key) { mutableStateOf(editing?.isActive ?: true) }
    var closingDay by rememberSaveable(key) {
        mutableStateOf(editing?.closingDay?.toString() ?: "")
    }
    var dueDay by rememberSaveable(key) {
        mutableStateOf(editing?.dueDay?.toString() ?: "")
    }

    AppScaffold(
        title = stringResource(
            if (editing == null) R.string.add_cost_center else R.string.edit_cost_center,
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
            ErrorBanner(viewModel.state.error)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cost_center_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = closingDay,
                onValueChange = { closingDay = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text(stringResource(R.string.closing_day)) },
                supportingText = { Text(stringResource(R.string.billing_day_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = dueDay,
                onValueChange = { dueDay = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text(stringResource(R.string.due_day)) },
                supportingText = { Text(stringResource(R.string.billing_day_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.active))
                Switch(checked = active, onCheckedChange = { active = it })
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.save(
                        editing = editing,
                        name = name,
                        isActive = active,
                        closingDay = closingDay.toIntOrNull(),
                        dueDay = dueDay.toIntOrNull(),
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
}
