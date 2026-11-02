package br.com.sharpledger.ui.more

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.sharpledger.R
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.core.AppPrefs
import br.com.sharpledger.core.AppVersion
import br.com.sharpledger.data.FamilyUsersResponse
import br.com.sharpledger.data.Permissions
import br.com.sharpledger.data.ROLE_ADMIN
import br.com.sharpledger.data.ROLE_MEMBER
import br.com.sharpledger.data.ROLE_VIEWER
import br.com.sharpledger.data.RoleSummary
import br.com.sharpledger.data.UserProfile
import br.com.sharpledger.data.UsersRepository
import br.com.sharpledger.ui.components.AppScaffold
import br.com.sharpledger.ui.components.ChoiceDialog
import br.com.sharpledger.ui.components.ConfirmDialog
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.LoadingBox
import br.com.sharpledger.ui.components.SectionCard
import kotlinx.coroutines.launch

private enum class MoreScreenMode { MAIN, USERS, PASSWORD, ABOUT }

@Composable
fun MoreScreen(
    container: AppContainer,
    user: UserProfile,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(MoreScreenMode.MAIN.name) }
    val currentMode = MoreScreenMode.valueOf(mode)

    BackHandler(enabled = currentMode != MoreScreenMode.MAIN) {
        mode = MoreScreenMode.MAIN.name
    }

    when (currentMode) {
        MoreScreenMode.MAIN -> MoreMain(
            container = container,
            user = user,
            onOpenUsers = { mode = MoreScreenMode.USERS.name },
            onOpenPassword = { mode = MoreScreenMode.PASSWORD.name },
            onOpenAbout = { mode = MoreScreenMode.ABOUT.name },
            onLoggedOut = onLoggedOut,
        )

        MoreScreenMode.USERS -> UsersScreen(
            container = container,
            currentUser = user,
            onBack = { mode = MoreScreenMode.MAIN.name },
        )

        MoreScreenMode.PASSWORD -> ChangePasswordScreen(
            container = container,
            onBack = { mode = MoreScreenMode.MAIN.name },
        )

        MoreScreenMode.ABOUT -> AboutScreen(
            container = container,
            onBack = { mode = MoreScreenMode.MAIN.name },
        )
    }
}

@Composable
private fun MoreMain(
    container: AppContainer,
    user: UserProfile,
    onOpenUsers: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenAbout: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val session = container.session
    val sessionState by session.state.collectAsStateWithLifecycle()

    var languageDialog by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }

    AppScaffold(title = stringResource(R.string.tab_more)) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = user.fullName) {
                    Text(user.email, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${user.familyName} • ${roleDisplayName(user.role)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (user.permissions.contains(Permissions.USERS_MANAGE)) {
                item {
                    MenuRow(
                        icon = Icons.Default.People,
                        title = stringResource(R.string.users_title),
                        subtitle = stringResource(R.string.family),
                        onClick = onOpenUsers,
                    )
                }
            }

            item {
                MenuRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.change_password_title),
                    onClick = onOpenPassword,
                )
            }

            item {
                MenuRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.language),
                    subtitle = if (sessionState.locale == "en-US") {
                        stringResource(R.string.english)
                    } else {
                        stringResource(R.string.portuguese)
                    },
                    onClick = { languageDialog = true },
                )
            }

            item {
                MenuRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.server_url),
                    subtitle = sessionState.baseUrl,
                    onClick = { serverDialog = true },
                )
            }

            item {
                MenuRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = "${stringResource(R.string.version)} ${AppVersion.VERSION_NAME}",
                    onClick = onOpenAbout,
                )
            }

            item {
                MenuRow(
                    icon = Icons.Default.Logout,
                    title = stringResource(R.string.action_logout),
                    onClick = { logoutDialog = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (languageDialog) {
        LanguageDialog(
            current = sessionState.locale,
            onDismiss = { languageDialog = false },
            onSelect = {
                session.saveLocale(it)
                languageDialog = false
                (context as? Activity)?.recreate()
            },
        )
    }

    if (serverDialog) {
        ServerDialog(
            current = sessionState.baseUrl,
            onDismiss = { serverDialog = false },
            onSave = {
                session.saveBaseUrl(AppPrefs.normalizeBaseUrl(it))
                session.logout()
                onLoggedOut()
            },
        )
    }

    if (logoutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.action_logout),
            message = stringResource(R.string.logout_confirm),
            confirmText = stringResource(R.string.action_logout),
            onConfirm = {
                logoutDialog = false
                session.logout()
                onLoggedOut()
            },
            onDismiss = { logoutDialog = false },
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var locale by remember { mutableStateOf(current) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = locale == "pt-BR", onClick = { locale = "pt-BR" })
                    Text(stringResource(R.string.portuguese))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = locale == "en-US", onClick = { locale = "en-US" })
                    Text(stringResource(R.string.english))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(onClick = { onSelect(locale) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(current) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.server_url),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.server_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ex.: https://agfapp.com/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(onClick = { onSave(value) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun roleDisplayName(role: String): String = when (role) {
    ROLE_ADMIN -> stringResource(R.string.admin)
    ROLE_MEMBER -> stringResource(R.string.member)
    ROLE_VIEWER -> stringResource(R.string.viewer)
    else -> role
}

@Composable
private fun permissionDisplayName(permission: String): String = when (permission) {
    Permissions.DASHBOARD_VIEW -> stringResource(R.string.perm_dashboard_view)
    Permissions.TRANSACTIONS_MANAGE -> stringResource(R.string.perm_transactions_manage)
    Permissions.CATEGORIES_MANAGE -> stringResource(R.string.perm_categories_manage)
    Permissions.BUDGETS_MANAGE -> stringResource(R.string.perm_budgets_manage)
    Permissions.INVESTMENTS_MANAGE -> stringResource(R.string.perm_investments_manage)
    Permissions.USERS_MANAGE -> stringResource(R.string.perm_users_manage)
    else -> permission
}

data class UsersUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: FamilyUsersResponse? = null,
)

class UsersViewModel(private val repository: UsersRepository) : ViewModel() {
    var state by mutableStateOf(UsersUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(loading = true, error = null)

        viewModelScope.launch {
            repository.getUsers()
                .onSuccess { state = state.copy(loading = false, data = it) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun changeRole(userId: String, roleName: String, onError: (String?) -> Unit) {
        viewModelScope.launch {
            repository.changeRole(userId, roleName)
                .onSuccess { load() }
                .onFailure { onError(it.message) }
        }
    }

    fun regenerateInviteCode(onError: (String?) -> Unit) {
        viewModelScope.launch {
            repository.regenerateInviteCode()
                .onSuccess { load() }
                .onFailure { onError(it.message) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { UsersViewModel(container.usersRepository) }
        }
    }
}

@Composable
private fun UsersScreen(
    container: AppContainer,
    currentUser: UserProfile,
    onBack: () -> Unit,
) {
    val viewModel: UsersViewModel = viewModel(factory = UsersViewModel.factory(container))
    val context = LocalContext.current

    var roleUser by remember { mutableStateOf<Pair<String, String>?>(null) }

    val data = viewModel.state.data

    AppScaffold(title = stringResource(R.string.users_title), onBack = onBack) { padding ->
        if (viewModel.state.loading && data == null) {
            LoadingBox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ErrorBanner(viewModel.state.error) }

                if (data != null) {
                    item {
                        SectionCard(title = data.familyName) {
                            Text(
                                stringResource(R.string.invite_code_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = data.inviteCode ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row {
                                    TextButton(onClick = {
                                        val code = data.inviteCode ?: return@TextButton
                                        val clipboard = context.getSystemService(
                                            Context.CLIPBOARD_SERVICE,
                                        ) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("invite", code),
                                        )
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.copied),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }) {
                                        Text(stringResource(R.string.action_copy))
                                    }
                                    TextButton(onClick = {
                                        viewModel.regenerateInviteCode {
                                            android.widget.Toast.makeText(
                                                context,
                                                it ?: context.getString(R.string.error_generic),
                                                android.widget.Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }) {
                                        Text(stringResource(R.string.regenerate_invite_code))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.family),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(data.users, key = { it.id }) { member ->
                        Card {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.fullName + if (member.isCurrentUser) {
                                            " (${stringResource(R.string.you)})"
                                        } else {
                                            ""
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "${member.email} • ${roleDisplayName(member.roleName)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!member.isCurrentUser) {
                                    TextButton(onClick = {
                                        roleUser = member.id to member.roleName
                                    }) {
                                        Text(stringResource(R.string.change_role))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.role_permissions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    items(data.roles, key = { it.name }) { role ->
                        RoleCard(role)
                    }
                }
            }
        }
    }

    val roleSelection = roleUser
    if (roleSelection != null && data != null) {
        ChoiceDialog(
            title = stringResource(R.string.change_role),
            options = data.roles.map { it.name to roleDisplayName(it.name) },
            selected = roleSelection.second,
            onSelect = { roleName ->
                viewModel.changeRole(roleSelection.first, roleName) {
                    android.widget.Toast.makeText(
                        context,
                        it ?: context.getString(R.string.error_generic),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { roleUser = null },
        )
    }
}

@Composable
private fun RoleCard(role: RoleSummary) {
    SectionCard(title = roleDisplayName(role.name)) {
        role.permissions.forEach { permission ->
            Text(
                text = "• ${permissionDisplayName(permission)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ChangePasswordScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var current by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AppScaffold(title = stringResource(R.string.change_password_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorBanner(error)

            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text(stringResource(R.string.current_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.confirm_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    error = null

                    if (current.isBlank() || newPassword.isBlank()) {
                        error = context.getString(R.string.fill_required_fields)
                        return@Button
                    }
                    if (newPassword.length < 8) {
                        error = context.getString(R.string.password_min_length)
                        return@Button
                    }
                    if (newPassword != confirm) {
                        error = context.getString(R.string.passwords_do_not_match)
                        return@Button
                    }

                    saving = true
                    scope.launch {
                        container.authRepository.changePassword(current, newPassword)
                            .onSuccess {
                                saving = false
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.action_save),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                onBack()
                            }
                            .onFailure {
                                saving = false
                                error = it.message
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun AboutScreen(container: AppContainer, onBack: () -> Unit) {
    val sessionState by container.session.state.collectAsStateWithLifecycle()
    var version by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.authRepository.version()
            .onSuccess { version = it }
            .onFailure { error = it.message }
    }

    AppScaffold(title = stringResource(R.string.about), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorBanner(error)
            SectionCard(title = stringResource(R.string.app_name)) {
                Text(
                    text = "${stringResource(R.string.version)} ${AppVersion.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "API: ${version ?: stringResource(R.string.loading)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = sessionState.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
