package br.com.sharpledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.sharpledger.ResetLink
import br.com.sharpledger.core.AppContainer
import br.com.sharpledger.ui.auth.AuthFlow
import br.com.sharpledger.ui.main.MainShell

@Composable
fun SharpLedgerRoot(container: AppContainer, resetLink: ResetLink?) {
    val session by container.session.state.collectAsStateWithLifecycle()

    LaunchedEffect(resetLink) {
        if (!resetLink?.token.isNullOrBlank() && session.isLoggedIn) {
            container.session.logout()
        }
    }

    LaunchedEffect(session.token) {
        if (!session.token.isNullOrBlank()) {
            container.authRepository.me()
        }
    }

    val currentUser = session.user

    if (session.isLoggedIn && currentUser != null) {
        MainShell(container = container, user = currentUser)
    } else {
        AuthFlow(
            container = container,
            initialResetEmail = resetLink?.email,
            initialResetToken = resetLink?.token,
        )
    }
}
