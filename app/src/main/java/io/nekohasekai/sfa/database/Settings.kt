package io.nekohasekai.sfa.database

import android.os.Build
import androidx.room.Room
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.ProxyService
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.database.preference.KeyValueDatabase
import io.nekohasekai.sfa.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.boolean
import io.nekohasekai.sfa.ktx.int
import io.nekohasekai.sfa.ktx.long
import io.nekohasekai.sfa.ktx.map
import io.nekohasekai.sfa.ktx.string
import io.nekohasekai.sfa.ktx.stringSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

object Settings {
    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH,
        ).fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore { instance.keyValuePairDao() }
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    var updateSource by dataStore.string(SettingsKey.UPDATE_SOURCE) { "github" }
    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { false }
    var updateCheckPrompted by dataStore.boolean(SettingsKey.UPDATE_CHECK_PROMPTED) { false }
    var updateTrack by dataStore.string(SettingsKey.UPDATE_TRACK) {
        val versionName = BuildConfig.VERSION_NAME.lowercase()
        if (versionName.contains("-alpha") ||
            versionName.contains("-beta") ||
            versionName.contains("-rc")
        ) {
            "beta"
        } else {
            "stable"
        }
    }
    var githubToken by dataStore.string(SettingsKey.GITHUB_TOKEN) { "" }
    var silentInstallEnabled by dataStore.boolean(SettingsKey.SILENT_INSTALL_ENABLED) { false }
    var silentInstallMethod by dataStore.string(SettingsKey.SILENT_INSTALL_METHOD) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "PACKAGE_INSTALLER"
        } else {
            "SHIZUKU"
        }
    }
    var fdroidMirrorUrl by dataStore.string(SettingsKey.FDROID_MIRROR_URL) { "https://f-droid.org/repo" }
    var fdroidCustomMirrors by dataStore.stringSet(SettingsKey.FDROID_CUSTOM_MIRRORS) { emptySet() }
    var autoUpdateEnabled by dataStore.boolean(SettingsKey.AUTO_UPDATE_ENABLED) { false }
    var dynamicNotification by dataStore.boolean(SettingsKey.DYNAMIC_NOTIFICATION) { true }
    var disableDeprecatedWarnings by dataStore.boolean(SettingsKey.DISABLE_DEPRECATED_WARNINGS) { false }

    // ── Apparence : palette + mode, appliqués à toute l'UI ──
    var themeKey by dataStore.string(SettingsKey.THEME_KEY) { "a2m" }
    var themeMode by dataStore.string(SettingsKey.THEME_MODE) { "light" }

    /** Thème personnalisé actif, encodé en `.a2mtheme` compact (vide = aucun). */
    var customTheme by dataStore.string(SettingsKey.CUSTOM_THEME) { "" }

    // ── Options persistées dont l'effet moteur n'est pas encore raccordé ──
    // (notice d'honnêteté affichée dans l'interface ; aucun effet simulé).
    var tlsFragment by dataStore.boolean(SettingsKey.TLS_FRAGMENT) { false }
    var sniEnabled by dataStore.boolean(SettingsKey.SNI_ENABLED) { true }
    var gamingMode by dataStore.boolean(SettingsKey.GAMING_MODE) { false }
    var connectionTimeoutSec by dataStore.int(SettingsKey.CONNECTION_TIMEOUT_SEC) { 30 }
    var reconnectDelaySec by dataStore.int(SettingsKey.RECONNECT_DELAY_SEC) { 5 }
    var backgroundBehavior by dataStore.string(SettingsKey.BACKGROUND_BEHAVIOR) { "always" }

    // ── IP Finder : ciblage de l'IP du réseau mobile ──
    // Règles « ^préfixe », « $suffixe », « =exacte » séparées par ;; / , / retour
    // à la ligne ; historique « timestamp|ip » (une ligne par observation).
    var ipFinderRules by dataStore.string(SettingsKey.IP_FINDER_RULES) { "" }
    var ipFinderAutoConnect by dataStore.boolean(SettingsKey.IP_FINDER_AUTO_CONNECT) { true }
    var ipFinderHistory by dataStore.string(SettingsKey.IP_FINDER_HISTORY) { "" }
    var ipFinderCycles by dataStore.int(SettingsKey.IP_FINDER_CYCLES) { 100 }
    var ipFinderPauseSec by dataStore.int(SettingsKey.IP_FINDER_PAUSE_SEC) { 3 }

    // ── Consommation journalière (90 jours) : deltas du trafic du tunnel ──
    var dailyTraffic by dataStore.string(SettingsKey.DAILY_TRAFFIC) { "" }

    // ── Retour tactile dans les réglages : sound / vibrate / silent ──
    var clickFeedback by dataStore.string(SettingsKey.CLICK_FEEDBACK) { "silent" }

    const val PER_APP_PROXY_DISABLED = 0
    const val PER_APP_PROXY_EXCLUDE = 1
    const val PER_APP_PROXY_INCLUDE = 2

    var autoRedirect by dataStore.boolean(SettingsKey.AUTO_REDIRECT) { false }
    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }
    var perAppProxyMode by dataStore.int(SettingsKey.PER_APP_PROXY_MODE) { PER_APP_PROXY_EXCLUDE }
    var perAppProxyList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_LIST) { emptySet() }
    var perAppProxyManagedMode by dataStore.boolean(SettingsKey.PER_APP_PROXY_MANAGED_MODE) { false }
    var perAppProxyManagedList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_MANAGED_LIST) { emptySet() }

    const val PACKAGE_QUERY_MODE_SHIZUKU = "SHIZUKU"
    const val PACKAGE_QUERY_MODE_ROOT = "ROOT"
    var perAppProxyPackageQueryMode by dataStore.string(SettingsKey.PER_APP_PROXY_PACKAGE_QUERY_MODE) { PACKAGE_QUERY_MODE_SHIZUKU }

    fun getEffectivePerAppProxyMode(): Int = if (perAppProxyManagedMode) {
        PER_APP_PROXY_EXCLUDE
    } else {
        perAppProxyMode
    }

    fun getEffectivePerAppProxyList(): Set<String> = if (perAppProxyManagedMode) {
        perAppProxyManagedList
    } else {
        perAppProxyList
    }

    var allowBypass by dataStore.boolean(SettingsKey.ALLOW_BYPASS) { false }

    /** Bloqueur de publicités : injecte des règles de routage « block » au démarrage du tunnel. */
    var adBlockEnabled by dataStore.boolean(SettingsKey.AD_BLOCK_ENABLED) { false }

    /** Contournement des réseaux privés (LAN) : trafic local hors du tunnel. */
    var bypassPrivateNetworks by dataStore.boolean(SettingsKey.BYPASS_PRIVATE_NETWORKS) { true }

    /** DNS personnalisé : remplace le DNS du profil par celui choisi ici. */
    var dnsOverrideEnabled by dataStore.boolean(SettingsKey.DNS_OVERRIDE_ENABLED) { false }
    var dnsRemoteType by dataStore.string(SettingsKey.DNS_REMOTE_TYPE) { "https" }
    var dnsRemoteServer by dataStore.string(SettingsKey.DNS_REMOTE_SERVER) { "1.1.1.1" }
    var dnsBootstrapServer by dataStore.string(SettingsKey.DNS_BOOTSTRAP_SERVER) { "1.1.1.1" }

    /** Moteur réseau : MTU, pile TUN, multiplexage, niveau de journal. */
    var engineMtuMode by dataStore.string(SettingsKey.ENGINE_MTU_MODE) { "auto" }
    var engineMtuCustom by dataStore.int(SettingsKey.ENGINE_MTU_CUSTOM) { 1500 }
    var engineStackMode by dataStore.string(SettingsKey.ENGINE_STACK_MODE) { "auto" }
    var engineMuxEnabled by dataStore.boolean(SettingsKey.ENGINE_MUX_ENABLED) { false }
    var engineLogLevel by dataStore.string(SettingsKey.ENGINE_LOG_LEVEL) { "auto" }

    /** Partage VPN par point d'accès : inbound mixte (HTTP+SOCKS5) écoutant sur le LAN. */
    var hotspotProxyEnabled by dataStore.boolean(SettingsKey.HOTSPOT_PROXY_ENABLED) { false }
    var hotspotProxyPort by dataStore.int(SettingsKey.HOTSPOT_PROXY_PORT) { 2080 }

    var systemProxyEnabled by dataStore.boolean(SettingsKey.SYSTEM_PROXY_ENABLED) { true }

    var privilegeSettingsEnabled by dataStore.boolean(SettingsKey.PRIVILEGE_SETTINGS_ENABLED) { false }
    var privilegeSettingsList by dataStore.stringSet(SettingsKey.PRIVILEGE_SETTINGS_LIST) { emptySet() }
    var privilegeSettingsInterfaceRenameEnabled by dataStore.boolean(
        SettingsKey.PRIVILEGE_SETTINGS_INTERFACE_RENAME_ENABLED,
    ) { false }
    var privilegeSettingsInterfacePrefix by dataStore.string(SettingsKey.PRIVILEGE_SETTINGS_INTERFACE_PREFIX) { "wlan" }

    var oomKillerEnabled by dataStore.boolean(SettingsKey.OOM_KILLER_ENABLED) { false }
    var oomKillerDisabled by dataStore.boolean(SettingsKey.OOM_KILLER_DISABLED) { true }
    var oomMemoryLimitMB by dataStore.int(SettingsKey.OOM_MEMORY_LIMIT_MB) { 50 }

    var powerReportEnabled by dataStore.boolean(SettingsKey.POWER_REPORT_ENABLED) { false }

    var dashboardItemOrder by dataStore.string(SettingsKey.DASHBOARD_ITEM_ORDER) { "" }
    var dashboardDisabledItems by dataStore.stringSet(SettingsKey.DASHBOARD_DISABLED_ITEMS) { emptySet() }

    var serversSort by dataStore.string(SettingsKey.SERVERS_SORT) { "name" }
    var serversView by dataStore.string(SettingsKey.SERVERS_VIEW) { "list" }
    var serversSize by dataStore.string(SettingsKey.SERVERS_SIZE) { "m" }

    var activeRemoteServerId by dataStore.long(SettingsKey.ACTIVE_REMOTE_SERVER_ID) { 0L }

    // Tailscale SSH
    var tailscaleSSHRememberedUsernames by dataStore.map(SettingsKey.TAILSCALE_SSH_REMEMBERED_USERNAMES)
    var tailscaleSSHRememberedTerminalTypes by dataStore.map(SettingsKey.TAILSCALE_SSH_REMEMBERED_TERMINAL_TYPES)
    var tailscaleSSHQuickConnectPeers by dataStore.stringSet(SettingsKey.TAILSCALE_SSH_QUICK_CONNECT_PEERS)
    var tailscaleSSHLightTheme by dataStore.string(SettingsKey.TAILSCALE_SSH_LIGHT_THEME) { "Alabaster" }
    var tailscaleSSHDarkTheme by dataStore.string(SettingsKey.TAILSCALE_SSH_DARK_THEME) { "Afterglow" }
    var tailscaleSSHFontFamily by dataStore.string(SettingsKey.TAILSCALE_SSH_FONT_FAMILY)
    var tailscaleSSHFontSize by dataStore.int(SettingsKey.TAILSCALE_SSH_FONT_SIZE) { 14 }
    var tailscaleSSHCustomFontPath by dataStore.string(SettingsKey.TAILSCALE_SSH_CUSTOM_FONT_PATH)
    var tailscaleSSHLightConfig by dataStore.string(SettingsKey.TAILSCALE_SSH_LIGHT_CONFIG)
    var tailscaleSSHDarkConfig by dataStore.string(SettingsKey.TAILSCALE_SSH_DARK_CONFIG)
    var tailscaleSSHFontFollowTheme by dataStore.boolean(SettingsKey.TAILSCALE_SSH_FONT_FOLLOW_THEME) { true }

    var cachedUpdateInfo by dataStore.string(SettingsKey.CACHED_UPDATE_INFO) { "" }
    var cachedApkPath by dataStore.string(SettingsKey.CACHED_APK_PATH) { "" }
    var lastShownUpdateVersion by dataStore.int(SettingsKey.LAST_SHOWN_UPDATE_VERSION) { 0 }

    fun serviceClass(): Class<*> = when (serviceMode) {
        ServiceMode.VPN -> VPNService::class.java
        else -> ProxyService::class.java
    }

    suspend fun rebuildServiceMode() {
        val newMode = detectServiceMode()
        if (serviceMode != newMode) {
            serviceMode = newMode
        }
    }

    suspend fun detectServiceMode(): String {
        try {
            if (needVPNService()) {
                return ServiceMode.VPN
            }
        } catch (_: Exception) {
        }
        return ServiceMode.NORMAL
    }

    private suspend fun needVPNService(): Boolean {
        val selectedProfileId = selectedProfile
        if (selectedProfileId == -1L) return false
        val profile = ProfileManager.get(selectedProfileId) ?: return false
        return Libbox.hasTunInbound(File(profile.typed.path).readText())
    }
}
