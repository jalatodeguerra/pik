package com.pik.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pik.app.data.db.AccountEntity
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.AccountRepository
import com.pik.app.data.repo.ImportRepository
import com.pik.app.domain.AccountType
import com.pik.app.domain.importing.AccountRef
import com.pik.app.domain.importing.ImportAppConfig
import com.pik.app.domain.importing.ImportMatcher
import com.pik.app.domain.importing.ImportSource
import com.pik.app.domain.importing.KnownApps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** An app with a launcher icon. [source] is set for the payment apps Pik knows (found by name). */
data class LauncherApp(val packageName: String, val label: String, val source: ImportSource?)

data class ImportAppUi(val app: LauncherApp, val enabled: Boolean, val accountId: Long?)

data class ImportSettingsState(
    /** Pik has notification access in the system settings. */
    val accessGranted: Boolean = false,
    val appsLoaded: Boolean = false,
    val apps: List<ImportAppUi> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val pendingCount: Int = 0,
)

@HiltViewModel
class ImportSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PikPreferences,
    private val accountRepository: AccountRepository,
    importRepository: ImportRepository,
) : ViewModel() {

    private val accessGranted = MutableStateFlow(readAccess())
    private val launcherApps = MutableStateFlow<List<LauncherApp>?>(null)

    init {
        viewModelScope.launch {
            launcherApps.value = withContext(Dispatchers.IO) { loadLauncherApps() }
        }
    }

    val state: StateFlow<ImportSettingsState> = combine(
        launcherApps,
        prefs.importApps,
        accountRepository.observeAccounts(),
        accessGranted,
        importRepository.observePendingCount(),
    ) { apps, configs, accounts, access, pending ->
        val byPackage = configs.associateBy { it.packageName }
        ImportSettingsState(
            accessGranted = access,
            appsLoaded = apps != null,
            apps = (apps ?: emptyList()).map { app ->
                val config = byPackage[app.packageName]
                ImportAppUi(app, config != null, config?.accountId)
            },
            accounts = accounts,
            pendingCount = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportSettingsState())

    /** Call when the screen resumes: the user may have changed the notification access in the system settings. */
    fun refreshAccess() {
        accessGranted.value = readAccess()
    }

    private fun readAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun setEnabled(app: LauncherApp, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                prefs.removeImportApp(app.packageName)
                return@launch
            }
            val source = app.source ?: ImportSource.OTHER_APP
            val refs = accountRepository.getAccounts().filter { !it.archived }
                .map { AccountRef(it.id, it.name, it.type == AccountType.BANK) }
            prefs.setImportApp(app.packageName, source, ImportMatcher.defaultAccountFor(source, refs))
        }
    }

    fun setAccount(app: LauncherApp, accountId: Long) {
        viewModelScope.launch {
            val config: ImportAppConfig? = prefs.importApps.first().firstOrNull { it.packageName == app.packageName }
            prefs.setImportApp(app.packageName, config?.source ?: app.source ?: ImportSource.OTHER_APP, accountId)
        }
    }

    private fun loadLauncherApps(): List<LauncherApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                queryLegacy(pm, intent)
            }
        } catch (e: Exception) {
            emptyList()
        }
        return infos.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm).toString()
            LauncherApp(pkg, label, KnownApps.sourceFor(pkg, label))
        }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<LauncherApp>({ it.source == null }, { it.label.lowercase() }))
    }

    @Suppress("DEPRECATION")
    private fun queryLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
}
