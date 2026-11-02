package br.com.sharpledger

import android.app.Application
import br.com.sharpledger.core.AppContainer

class SharpLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
