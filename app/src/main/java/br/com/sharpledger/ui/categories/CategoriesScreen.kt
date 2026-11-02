package br.com.sharpledger.ui.categories

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.data.Category
import br.com.sharpledger.data.CategoryRepository
import br.com.sharpledger.data.TYPE_EXPENSE
import br.com.sharpledger.data.TYPE_INCOME
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.EmptyState
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val includeInactive: Boolean = false,
)

class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {
    var state by mutableStateOf(CategoriesUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            repository.getCategories(state.includeInactive)
                .onSuccess { state = state.copy(loading = false, categories = it) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun setIncludeInactive(value: Boolean) {
        state = state.copy(includeInactive = value)
        load()
    }

    fun save(
        editing: Category?,
        name: String,
        type: String,
        isActive: Boolean,
        onSaved: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        if (name.isBlank()) {
            onError("Informe o nome da categoria.")
            return
        }

        if (name.length > 80) {
            onError("O nome da categoria deve ter no máximo 80 caracteres.")
            return
        }

        viewModelScope.launch {
            val result = if (editing == null) {
                repository.create(name, type, isActive)
            } else {
                repository.update(editing.id, name, type, isActive)
            }

            result
                .onSuccess {
                    onSaved()
                    load()
                }
                .onFailure { onError(it.message) }
        }
    }

    fun setActive(category: Category, active: Boolean) {
        viewModelScope.launch {
            repository.setActive(category.id, active)
                .onSuccess { load() }
                .onFailure { state = state.copy(error = it.message) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CategoriesViewModel(container.categoryRepository) }
        }
    }
}

private enum class CategoriesMode { LIST, FORM }

@Composable
fun CategoriesScreen(container: AppContainer) {
    val viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.factory(container))
    val context = LocalContext.current

    var mode by rememberSaveable { mutableStateOf(CategoriesMode.LIST.name) }
    var editing by remember { mutableStateOf<Category?>(null) }

    val currentMode = CategoriesMode.valueOf(mode)

    BackHandler(enabled = currentMode == CategoriesMode.FORM) {
        mode = CategoriesMode.LIST.name
        editing = null
    }

    if (currentMode == CategoriesMode.FORM) {
        CategoryForm(
            viewModel = viewModel,
            editing = editing,
            onBack = {
                mode = CategoriesMode.LIST.name
                editing = null
            },
            onSaved = {
                mode = CategoriesMode.LIST.name
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
        title = stringResource(R.string.categories_title),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                mode = CategoriesMode.FORM.name
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_category))
            }
        },
    ) { padding ->
        if (viewModel.state.loading && viewModel.state.categories.isEmpty()) {
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

                if (viewModel.state.categories.isEmpty()) {
                    item { EmptyState(stringResource(R.string.empty)) }
                }

                items(viewModel.state.categories, key = { it.id }) { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = category
                                mode = CategoriesMode.FORM.name
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                val typeLabel = if (category.type == TYPE_INCOME) {
                                    stringResource(R.string.category_income)
                                } else {
                                    stringResource(R.string.category_expense)
                                }
                                val suffix = if (!category.isActive) {
                                    " • ${stringResource(R.string.inactive)}"
                                } else {
                                    ""
                                }
                                Text(
                                    text = typeLabel + suffix,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = category.isActive,
                                onCheckedChange = { viewModel.setActive(category, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryForm(
    viewModel: CategoriesViewModel,
    editing: Category?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onError: (String?) -> Unit,
) {
    val key = editing?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: "") }
    var type by rememberSaveable(key) { mutableStateOf(editing?.type ?: TYPE_EXPENSE) }
    var active by rememberSaveable(key) { mutableStateOf(editing?.isActive ?: true) }
    var typeMenu by remember { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(
            if (editing == null) R.string.add_category else R.string.edit_category,
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
                label = { Text(stringResource(R.string.category_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Column {
                TextButton(onClick = { typeMenu = true }) {
                    Text(
                        text = if (type == TYPE_INCOME) {
                            stringResource(R.string.category_income)
                        } else {
                            stringResource(R.string.category_expense)
                        },
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_income)) },
                        onClick = {
                            type = TYPE_INCOME
                            typeMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_expense)) },
                        onClick = {
                            type = TYPE_EXPENSE
                            typeMenu = false
                        },
                    )
                }
            }

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
                        type = type,
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
}
