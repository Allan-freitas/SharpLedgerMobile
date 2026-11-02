package br.com.sharpledger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.sharpledger.core.AppPrefs
import br.com.sharpledger.core.LocaleHelper
import br.com.sharpledger.ui.SharpLedgerRoot
import br.com.sharpledger.ui.theme.SharpLedgerTheme

data class ResetLink(val email: String?, val token: String?)

class MainActivity : ComponentActivity() {
    private var resetLink by mutableStateOf<ResetLink?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.apply(newBase, AppPrefs.locale(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        resetLink = parseResetLink(intent)

        val container = (application as SharpLedgerApp).container

        setContent {
            SharpLedgerTheme(dynamicColor = false) {
                SharpLedgerRoot(container = container, resetLink = resetLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetLink = parseResetLink(intent)
    }

    private fun parseResetLink(intent: Intent?): ResetLink? {
        val data = intent?.data ?: return null
        if (!data.scheme.equals("sharpledger", ignoreCase = true)) return null
        if (!data.host.equals("reset-password", ignoreCase = true)) return null

        return ResetLink(
            email = data.getQueryParameter("email"),
            token = data.getQueryParameter("token"),
        )
    }
}
