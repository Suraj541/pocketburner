package com.example

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsStore
import com.example.data.local.TaxonomyRepository
import com.example.engine.FirmwareCatalogEngine
import com.example.engine.FlashProtocolEngine
import com.example.engine.ScriptRepository
import com.example.engine.TransportManager

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val taxonomyRepository: TaxonomyRepository = TaxonomyRepository(database.taxonomyDao())
    val transportManager: TransportManager = TransportManager(appContext, settingsStore)
    val firmwareCatalogEngine: FirmwareCatalogEngine = FirmwareCatalogEngine(
        context = appContext,
        firmwareDao = database.firmwareDao(),
        repoDao = database.repoSourceDao(),
        taxonomyRepository = taxonomyRepository,
    )
    val flashProtocolEngine: FlashProtocolEngine = FlashProtocolEngine(
        context = appContext,
        transport = transportManager,
    )
    val scriptRepository: ScriptRepository = ScriptRepository(
        context = appContext,
        scriptDao = database.scriptDao(),
        transport = transportManager,
    )
    val multiAgentFirmwareEngine: com.example.engine.MultiAgentFirmwareEngine =
        com.example.engine.MultiAgentFirmwareEngine()

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
