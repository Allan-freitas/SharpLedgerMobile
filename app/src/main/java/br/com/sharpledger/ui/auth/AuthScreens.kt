package br.com.sharpledger.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.ImeAction
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
import br.com.sharpledger.data.AuthRepository
import br.com.sharpledger.ui.components.ErrorBanner
import br.com.sharpledger.ui.components.InfoBanner
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val resetToken: String? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    var state by mutableStateOf(AuthUiState())
        private set

    fun clear() {
        state = AuthUiState()
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = state.copy(error = "Preencha e-mail e senha.")
            return
        }

        state = state.copy(loading = true, error = null, message = null)

        viewModelScope.launch {
            repository.login(email, password)
                .onSuccess { state = state.copy(loading = false) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun register(fullName: String, email: String, password: String, confirm: String, inviteCode: String) {
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            state = state.copy(error = "Preencha os campos obrigatórios.")
            return
        }

        if (password.length < 8) {
            state = state.copy(error = "A senha deve ter pelo menos 8 caracteres.")
            return
        }

        if (password != confirm) {
            state = state.copy(error = "As senhas não coincidem.")
            return
        }

        state = state.copy(loading = true, error = null, message = null)

        viewModelScope.launch {
            repository.register(fullName, email, password, inviteCode)
                .onSuccess { state = state.copy(loading = false) }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            state = state.copy(error = "Informe o e-mail.")
            return
        }

        state = state.copy(loading = true, error = null, message = null, resetToken = null)

        viewModelScope.launch {
            repository.forgotPassword(email)
                .onSuccess { response ->
                    state = state.copy(
                        loading = false,
                        message = response.message,
                        resetToken = response.resetToken,
                    )
                }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    fun resetPassword(email: String, token: String, password: String, confirm: String, onDone: () -> Unit) {
        if (email.isBlank() || token.isBlank() || password.isBlank()) {
            state = state.copy(error = "Preencha os campos obrigatórios.")
            return
        }

        if (password.length < 8) {
            state = state.copy(error = "A senha deve ter pelo menos 8 caracteres.")
            return
        }

        if (password != confirm) {
            state = state.copy(error = "As senhas não coincidem.")
            return
        }

        state = state.copy(loading = true, error = null, message = null)

        viewModelScope.launch {
            repository.resetPassword(email, token, password)
                .onSuccess {
                    state = state.copy(
                        loading = false,
                        message = "Senha redefinida com sucesso. Faça login.",
                    )
                    onDone()
                }
                .onFailure { state = state.copy(loading = false, error = it.message) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AuthViewModel(container.authRepository) }
        }
    }
}

private enum class AuthScreen { LOGIN, REGISTER, FORGOT, RESET }

@Composable
fun AuthFlow(
    container: AppContainer,
    initialResetEmail: String? = null,
    initialResetToken: String? = null,
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container))
    val context = LocalContext.current

    var screen by rememberSaveable {
        mutableStateOf(if (!initialResetToken.isNullOrBlank()) AuthScreen.RESET else AuthScreen.LOGIN)
    }
    var resetEmail by rememberSaveable { mutableStateOf(initialResetEmail.orEmpty()) }
    var resetToken by rememberSaveable { mutableStateOf(initialResetToken.orEmpty()) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        when (screen) {
            AuthScreen.LOGIN -> LoginScreen(
                state = viewModel.state,
                onLogin = viewModel::login,
                onRegister = {
                    viewModel.clear()
                    screen = AuthScreen.REGISTER
                },
                onForgot = {
                    viewModel.clear()
                    screen = AuthScreen.FORGOT
                },
            )

            AuthScreen.REGISTER -> RegisterScreen(
                state = viewModel.state,
                onRegister = viewModel::register,
                onBack = {
                    viewModel.clear()
                    screen = AuthScreen.LOGIN
                },
            )

            AuthScreen.FORGOT -> ForgotPasswordScreen(
                state = viewModel.state,
                onSend = viewModel::forgotPassword,
                onGoToReset = { email, token ->
                    resetEmail = email
                    resetToken = token
                    viewModel.clear()
                    screen = AuthScreen.RESET
                },
                onBack = {
                    viewModel.clear()
                    screen = AuthScreen.LOGIN
                },
            )

            AuthScreen.RESET -> ResetPasswordScreen(
                state = viewModel.state,
                initialEmail = resetEmail,
                initialToken = resetToken,
                onReset = { email, token, password, confirm ->
                    viewModel.resetPassword(email, token, password, confirm) {
                        screen = AuthScreen.LOGIN
                    }
                },
                onBack = {
                    viewModel.clear()
                    screen = AuthScreen.LOGIN
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { showSettings = true }) {
            Text(stringResource(R.string.server_url))
        }
    }

    if (showSettings) {
        ApiSettingsDialog(container) {
            showSettings = false
            (context as? Activity)?.recreate()
        }
    }
}

@Composable
private fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        ErrorBanner(state.error)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.login))
            }
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.forgot_password))
        }
        TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.no_account))
        }
    }
}

@Composable
private fun RegisterScreen(
    state: AuthUiState,
    onRegister: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.register_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        ErrorBanner(state.error)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text(stringResource(R.string.full_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.confirm_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase() },
            label = { Text(stringResource(R.string.invite_code)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onRegister(fullName, email, password, confirm, inviteCode) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.register))
            }
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.already_have_account))
        }
    }
}

@Composable
private fun ForgotPasswordScreen(
    state: AuthUiState,
    onSend: (String) -> Unit,
    onGoToReset: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.forgot_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        ErrorBanner(state.error)
        InfoBanner(state.message)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onSend(email) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Text(stringResource(R.string.send_reset_link))
        }
        Spacer(Modifier.height(8.dp))

        if (!state.resetToken.isNullOrBlank()) {
            InfoBanner(stringResource(R.string.reset_token_dev, state.resetToken))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onGoToReset(email, state.resetToken.orEmpty()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reset_password))
            }
            Spacer(Modifier.height(8.dp))
        }

        TextButton(
            onClick = { onGoToReset(email, "") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reset_token))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun ResetPasswordScreen(
    state: AuthUiState,
    initialEmail: String,
    initialToken: String,
    onReset: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var token by rememberSaveable { mutableStateOf(initialToken) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reset_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        ErrorBanner(state.error)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.reset_token)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.new_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.confirm_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onReset(email, token, password, confirm) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Text(stringResource(R.string.reset_password))
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
fun ApiSettingsDialog(
    container: AppContainer,
    onApplied: () -> Unit,
) {
    val session = container.session
    val sessionState by session.state.collectAsStateWithLifecycle()

    var baseUrl by remember { mutableStateOf(sessionState.baseUrl) }
    var locale by remember { mutableStateOf(sessionState.locale) }

    Dialog(onDismissRequest = onApplied) {
        androidx.compose.material3.Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.server_url),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.server_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleSmall,
                )
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
                    TextButton(onClick = onApplied) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.height(0.dp))
                    Button(onClick = {
                        session.saveBaseUrl(AppPrefs.normalizeBaseUrl(baseUrl))
                        session.saveLocale(locale)
                        onApplied()
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
