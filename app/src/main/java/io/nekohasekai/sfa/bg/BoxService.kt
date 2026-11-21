package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import go.Seq
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class BoxService(private val service: Service, private val platformInterface: PlatformInterface) : CommandServerHandler {
    companion object {
        private const val PROFILE_UPDATE_INTERVAL = 15L * 60 * 1000 // 15 minutes in milliseconds
        private const val TAG = "BoxService"

        @OptIn(DelicateCoroutinesApi::class)
        fun start() = GlobalScope.launch(Dispatchers.Main.immediate) {
            Settings.dataStore.initialize()
            withContext(Dispatchers.IO) {
                Settings.rebuildServiceMode()
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            ContextCompat.startForegroundService(Application.application, intent)
        }

        fun stop() {
            Application.application.sendBroadcast(
                Intent(Action.SERVICE_CLOSE).setPackage(
                    Application.application.packageName,
                ),
            )
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null

    private val status = MutableLiveData(Status.Stopped)
    private val binder = ServiceBinder(status)
    private val notification = ServiceNotification(status, service)
    private lateinit var commandServer: CommandServer
    private val idleModeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleModeUpdates = Channel<Boolean>(Channel.UNLIMITED)
    private var lifecycleJob: Job? = null
    private var backgroundStopJob: Job? = null
    private var reconnectJob: Job? = null
    /** Serialise settings/network reloads; libbox does not support concurrent reload RPCs. */
    private val reloadMutex = Mutex()

    init {
        idleModeScope.launch {
            for (idle in idleModeUpdates) {
                if (!::commandServer.isInitialized) continue
                if (idle) {
                    commandServer.pause()
                } else {
                    commandServer.wake()
                }
            }
        }
    }

    /**
     * Applique le comportement en arrière-plan choisi par l'utilisateur.
     * « optimized » suspend les connexions inactives quand l'écran est éteint
     * et que l'application n'est plus visible ; « stop » arrête le tunnel après
     * un court délai de grâce. « always » ne change rien.
     */
    private fun startLifecycleObserver() {
        lifecycleJob?.cancel()
        lifecycleJob =
            idleModeScope.launch(Dispatchers.Default) {
                var hadForeground = AppLifecycleObserver.isForeground.value
                combine(
                    AppLifecycleObserver.isForeground,
                    AppLifecycleObserver.isScreenOn,
                ) { foreground, screenOn -> foreground to screenOn }
                    .collect { (foreground, screenOn) ->
                        if (foreground) hadForeground = true
                        updatePauseState()
                        if (Settings.backgroundBehavior == "stop" &&
                            hadForeground &&
                            !foreground
                        ) {
                            scheduleBackgroundStop()
                        } else {
                            backgroundStopJob?.cancel()
                        }
                    }
            }
    }

    private fun updatePauseState() {
        if (!::commandServer.isInitialized) return
        val deviceIdle =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Application.powerManager.isDeviceIdleMode
        val backgroundPause =
            Settings.backgroundBehavior == "optimized" &&
                !AppLifecycleObserver.isForeground.value &&
                !AppLifecycleObserver.isScreenOn.value
        idleModeUpdates.trySend(deviceIdle || backgroundPause)
    }

    private fun scheduleBackgroundStop() {
        if (backgroundStopJob?.isActive == true) return
        backgroundStopJob =
            idleModeScope.launch(Dispatchers.IO) {
                delay(5_000)
                if (status.value == Status.Started &&
                    Settings.backgroundBehavior == "stop" &&
                    !AppLifecycleObserver.isForeground.value
                ) {
                    stopService()
                }
            }
    }

    private fun refreshBackgroundPolicy() {
        updatePauseState()
        // L'arrêt ne se déclenche que sur une vraie transition premier plan → arrière-plan,
        // gérée par startLifecycleObserver(). Un démarrage depuis l'arrière-plan
        // (raccourci, automatisation) ne doit pas couper le tunnel immédiatement.
        if (Settings.backgroundBehavior != "stop" ||
            AppLifecycleObserver.isForeground.value
        ) {
            backgroundStopJob?.cancel()
        }
    }

    private fun cancelBackgroundJobs() {
        lifecycleJob?.cancel()
        lifecycleJob = null
        backgroundStopJob?.cancel()
        backgroundStopJob = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * Après un changement de réseau, attend le délai choisi puis recharge le
     * tunnel. Les changements rapprochés annulent le rechargement précédent.
     */
    private fun scheduleReconnect() {
        if (status.value != Status.Started) return
        val delaySec = Settings.reconnectDelaySec.coerceIn(1, 120)
        reconnectJob?.cancel()
        reconnectJob =
            idleModeScope.launch(Dispatchers.IO) {
                delay(delaySec * 1_000L)
                if (status.value != Status.Started) return@launch
                runCatching { serviceReload0() }
                    .onFailure {
                        Log.w(TAG, "Reconnect after network change failed: ${it.message}")
                    }
            }
    }

    private var receiverRegistered = false
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Action.SERVICE_CLOSE -> {
                        stopService()
                    }

                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            serviceUpdateIdleMode()
                        }
                    }
                }
            }
        }

    private fun startCommandServer() {
        Libbox.promoteOOMDraft()
        Libbox.discardPowerReportDraft()
        val commandServer = CommandServer(this, platformInterface)
        commandServer.start()
        this.commandServer = commandServer
    }

    private var lastProfileName = ""

    private suspend fun startService() {
        try {
            val selectedProfileId = Settings.selectedProfile
            if (selectedProfileId == -1L) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            val profile = ProfileManager.get(selectedProfileId)
            if (profile == null) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            val content = File(profile.typed.path).readText()
            if (content.isBlank()) {
                stopAndAlert(Alert.EmptyConfiguration)
                return
            }

            // Décoration A2M (bloqueur de pubs) appliquée à la config réelle.
            var effectiveContent = effectiveContent(content)

            lastProfileName = profile.name
            withContext(Dispatchers.Main) {
                notification.show(lastProfileName, R.string.status_starting)
            }

            DefaultNetworkMonitor.setNetworkChangedListener { scheduleReconnect() }
            DefaultNetworkMonitor.start()

            // Validation préalable de la configuration (échec propre avant tout démarrage).
            try {
                Libbox.checkConfig(effectiveContent)
            } catch (e: Exception) {
                // A remote ad rule-set must never make the VPN unusable.
                // Retry validation without the optional decoration and keep
                // the service available; the UI can report the degraded mode.
                if (!Settings.adBlockEnabled) {
                    stopAndAlert(Alert.CreateService, e.message)
                    return
                }
                effectiveContent = effectiveContent(content, adBlock = false)
                Log.w(TAG, "Adblock configuration rejected, starting without adblock", e)
            }

            val serviceOverride =
                OverrideOptions().apply {
                    autoRedirect = Settings.autoRedirect
                    if (Vendor.isPerAppProxyAvailable() && Settings.perAppProxyEnabled) {
                        val appList = Settings.getEffectivePerAppProxyList()
                        if (Settings.getEffectivePerAppProxyMode() == Settings.PER_APP_PROXY_INCLUDE) {
                            includePackage =
                                PlatformInterfaceWrapper.StringArray((appList + Application.application.packageName).iterator())
                        } else {
                            excludePackage =
                                PlatformInterfaceWrapper.StringArray((appList - Application.application.packageName).iterator())
                        }
                    }
                }

            try {
                commandServer.startOrReloadService(effectiveContent, serviceOverride)
            } catch (e: Exception) {
                // Filet de sécurité : noyau compilé sans gVisor + profil demandant gVisor/mixed
                // => réécriture de la pile TUN en "system" et nouvelle tentative unique.
                val fallbackContent = buildStackFallbackContent(effectiveContent, e.message)
                if (fallbackContent == null) {
                    if (Settings.adBlockEnabled) {
                        val withoutAdBlock = effectiveContent(content, adBlock = false)
                        try {
                            commandServer.startOrReloadService(withoutAdBlock, serviceOverride)
                            Log.w(TAG, "Tunnel started without adblock after rule-set failure")
                        } catch (retryError: Exception) {
                            stopAndAlert(Alert.CreateService, retryError.message)
                            return
                        }
                    } else {
                        stopAndAlert(Alert.CreateService, e.message)
                        return
                    }
                    // The optional feature was rejected; continue with the VPN.
                    if (Settings.adBlockEnabled) {
                        Log.w(TAG, "Adblock disabled for this session after startup failure")
                    }
                } else {
                    Log.w(TAG, "gVisor stack unavailable, retrying with system stack")
                    try {
                        commandServer.startOrReloadService(fallbackContent, serviceOverride)
                        // The fallback is expected on Android builds without the
                        // optional gVisor implementation. Keep it out of the error
                        // channel: the tunnel is healthy and a modal dialog here
                        // incorrectly suggests that connection failed.
                        Log.i(TAG, "Tunnel started with system stack after gVisor fallback")
                    } catch (retryError: Exception) {
                        stopAndAlert(Alert.CreateService, retryError.message)
                        return
                    }
                }
            }

            if (commandServer.needWIFIState()) {
                val wifiPermission =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    } else {
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    }
                if (!service.hasPermission(wifiPermission)) {
                    stopAndAlert(Alert.RequestLocationPermission)
                    return
                }
            }

            status.postValue(Status.Started)
            withContext(Dispatchers.Main) {
                notification.show(lastProfileName, R.string.status_started)
            }
            notification.start()
            refreshBackgroundPolicy()
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)
            return
        }
    }

    /**
     * Filet de sécurité pour un noyau compilé sans `with_gvisor` :
     * si l'erreur indique une fonctionnalité absente du build et que la configuration
     * demande la pile gVisor/mixed pour l'inbound TUN, renvoie une copie de la
     * configuration avec `"stack": "system"`, sinon null.
     */
    /** Contenu effectivement chargé : config du profil + décorations A2M (LAN, DNS, moteur, pub). */
    private fun effectiveContent(raw: String, adBlock: Boolean = Settings.adBlockEnabled): String {
        val dnsOverride =
            if (Settings.dnsOverrideEnabled) {
                A2MConfigDecorations.DnsOverride(
                    type = Settings.dnsRemoteType,
                    remoteServer = Settings.dnsRemoteServer.trim(),
                    bootstrapServer = Settings.dnsBootstrapServer.trim(),
                )
            } else {
                null
            }
        val engineNeedsApplying =
            Settings.engineMtuMode != "auto" ||
                Settings.engineStackMode != "auto" ||
                Settings.engineMuxEnabled ||
                Settings.engineLogLevel != "auto"
        val engine =
            if (engineNeedsApplying) {
                A2MConfigDecorations.EngineOptions(
                    mtuMode = Settings.engineMtuMode,
                    mtuCustom = Settings.engineMtuCustom,
                    stackMode = Settings.engineStackMode,
                    muxEnabled = Settings.engineMuxEnabled,
                    logLevel = Settings.engineLogLevel,
                )
            } else {
                null
            }
        val tls =
            if (Settings.tlsFragment || !Settings.sniEnabled) {
                A2MConfigDecorations.TlsOptions(
                    fragment = Settings.tlsFragment,
                    sniEnabled = Settings.sniEnabled,
                )
            } else {
                null
            }
        return A2MConfigDecorations.decorate(
            raw = raw,
            bypassPrivate = Settings.bypassPrivateNetworks,
            dns = dnsOverride,
            engine = engine,
            adBlock = adBlock,
            hotspotPort = if (Settings.hotspotProxyEnabled) Settings.hotspotProxyPort else null,
            tls = tls,
            gaming = Settings.gamingMode,
        )
    }

    private fun buildStackFallbackContent(content: String, cause: String?): String? {
        if (cause == null || !cause.contains("not included in this build", ignoreCase = true)) {
            return null
        }
        return runCatching {
            val root = org.json.JSONObject(content)
            val inbounds = root.optJSONArray("inbounds") ?: return@runCatching null
            var changed = false
            for (i in 0 until inbounds.length()) {
                val inbound = inbounds.optJSONObject(i) ?: continue
                if (inbound.optString("type") != "tun") continue
                when (inbound.optString("stack")) {
                    "gvisor", "mixed" -> {
                        inbound.put("stack", "system")
                        changed = true
                    }
                }
            }
            if (changed) root.toString() else null
        }.getOrNull()
    }

    override fun serviceStop() {
        notification.close()
        status.postValue(Status.Starting)
        val pfd = fileDescriptor
        if (pfd != null) {
            pfd.close()
            fileDescriptor = null
        }
        closeService()
    }

    override fun serviceReload() {
        runBlocking {
            serviceReload0()
        }
    }

    suspend fun serviceReload0() {
        reloadMutex.withLock { serviceReloadLocked() }
    }

    private suspend fun serviceReloadLocked() {
        val selectedProfileId = Settings.selectedProfile
        if (selectedProfileId == -1L) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }

        val profile = ProfileManager.get(selectedProfileId)
        if (profile == null) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }

        val content = File(profile.typed.path).readText()
        if (content.isBlank()) {
            stopAndAlert(Alert.EmptyConfiguration)
            return
        }
        // Décoration A2M (bloqueur de pubs) appliquée aussi au rechargement.
        val effectiveContent = effectiveContent(content)
        lastProfileName = profile.name
        val serviceOverride =
            OverrideOptions().apply {
                autoRedirect = Settings.autoRedirect
                if (Vendor.isPerAppProxyAvailable() && Settings.perAppProxyEnabled) {
                    val appList = Settings.getEffectivePerAppProxyList()
                    if (Settings.getEffectivePerAppProxyMode() == Settings.PER_APP_PROXY_INCLUDE) {
                        includePackage = PlatformInterfaceWrapper.StringArray((appList + Application.application.packageName).iterator())
                    } else {
                        excludePackage = PlatformInterfaceWrapper.StringArray((appList - Application.application.packageName).iterator())
                    }
                }
            }
        try {
            commandServer.startOrReloadService(effectiveContent, serviceOverride)
        } catch (e: Exception) {
            // Reloads (DNS/ad-block toggles) must use the same gVisor fallback
            // as the initial start. Otherwise the old service is torn down and
            // the UI reports a misleading RPC EOF while the tunnel is stopped.
            val fallbackContent = buildStackFallbackContent(effectiveContent, e.message)
            if (fallbackContent == null) {
                if (Settings.adBlockEnabled) {
                    try {
                        commandServer.startOrReloadService(effectiveContent(content, adBlock = false), serviceOverride)
                        Log.w(TAG, "Tunnel reloaded without adblock after rule-set failure")
                    } catch (retryError: Exception) {
                        // Keep the current service alive when possible. A failed
                        // optional reload must not turn a connected VPN off.
                        Log.e(TAG, "Optional adblock reload failed", retryError)
                    }
                } else {
                    Log.e(TAG, "Service reload failed; keeping current tunnel", e)
                }
                return
            }
            Log.w(TAG, "gVisor stack unavailable during reload, retrying with system stack")
            try {
                commandServer.startOrReloadService(fallbackContent, serviceOverride)
                Log.i(TAG, "Tunnel reloaded with system stack after gVisor fallback")
            } catch (retryError: Exception) {
                stopAndAlert(Alert.CreateService, retryError.message)
                return
            }
        }

        if (commandServer.needWIFIState()) {
            val wifiPermission =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                } else {
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }
            if (!service.hasPermission(wifiPermission)) {
                stopAndAlert(Alert.RequestLocationPermission)
                return
            }
        }
        refreshBackgroundPolicy()
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? {
        val status = SystemProxyStatus()
        if (service is VPNService) {
            status.available = service.systemProxyAvailable
            status.enabled = service.systemProxyEnabled
        }
        return status
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {
        serviceReload()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun serviceUpdateIdleMode() {
        updatePauseState()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun stopService() {
        if (status.value != Status.Started) return
        status.value = Status.Stopping
        cancelBackgroundJobs()
        DefaultNetworkMonitor.setNetworkChangedListener(null)
        if (receiverRegistered) {
            service.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        notification.close()
        GlobalScope.launch(Dispatchers.IO) {
            val pfd = fileDescriptor
            if (pfd != null) {
                pfd.close()
                fileDescriptor = null
            }
            DefaultNetworkMonitor.stop()
            closeService()
            commandServer.apply {
                close()
//                Seq.destroyRef(refnum)
            }
            Libbox.discardPowerReportDraft()
            PowerReportManager.refresh()
            Settings.startedByUser = false
            Settings.dataStore.flush()
            withContext(Dispatchers.Main) {
                status.value = Status.Stopped
                service.stopSelf()
            }
        }
    }

    private fun closeService() {
        runCatching {
            commandServer.closeService()
        }.onFailure {
            commandServer.setError("android: close service: ${it.message}")
        }
    }

    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        Settings.startedByUser = false
        Settings.dataStore.flush()
        cancelBackgroundJobs()
        DefaultNetworkMonitor.setNetworkChangedListener(null)
        val pfd = fileDescriptor
        if (pfd != null) {
            pfd.close()
            fileDescriptor = null
        }
        DefaultNetworkMonitor.stop()
        if (::commandServer.isInitialized) {
            closeService()
            commandServer.close()
        }
        withContext(Dispatchers.Main) {
            if (receiverRegistered) {
                service.unregisterReceiver(receiver)
                receiverRegistered = false
            }
            notification.close()
            binder.broadcast { callback ->
                callback.onServiceAlert(type.ordinal, message)
            }
            status.value = Status.Stopped
            service.stopSelf()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("SameReturnValue")
    internal fun onStartCommand(): Int {
        if (status.value != Status.Stopped) return Service.START_NOT_STICKY
        status.value = Status.Starting
        notification.show(lastProfileName, R.string.status_starting)

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                service,
                receiver,
                IntentFilter().apply {
                    addAction(Action.SERVICE_CLOSE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        startLifecycleObserver()

        GlobalScope.launch(Dispatchers.IO) {
            Settings.startedByUser = true
            try {
                Application.application.setupLibbox()
                startCommandServer()
            } catch (e: Exception) {
                stopAndAlert(Alert.StartCommandServer, e.message)
                return@launch
            }
            startService()
        }
        return Service.START_NOT_STICKY
    }

    internal fun onBind(): IBinder = binder

    internal fun onDestroy() {
        cancelBackgroundJobs()
        idleModeUpdates.cancel()
        idleModeScope.cancel()
        binder.close()
    }

    internal fun onRevoke() {
        stopService()
    }

    internal fun sendNotification(notification: Notification) {
        val channel = "notification-${notification.typeID}"
        val builder =
            NotificationCompat.Builder(service, channel).setShowWhen(false)
                .setContentTitle(notification.title).setContentText(notification.body)
                .setOnlyAlertOnce(true).setSmallIcon(R.drawable.ic_menu)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
        if (!notification.subtitle.isNullOrBlank()) {
            builder.setContentInfo(notification.subtitle)
        }
        if (!notification.openURL.isNullOrBlank()) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(
                        service,
                        MainActivity::class.java,
                    ).apply {
                        setAction(Action.OPEN_URL).setData(Uri.parse(notification.openURL))
                        setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    },
                    ServiceNotification.flags,
                ),
            )
        }
        GlobalScope.launch(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Application.notification.createNotificationChannel(
                    NotificationChannel(
                        channel,
                        notification.typeName,
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
            Application.notification.notify(notification.identifier, notification.typeID, builder.build())
        }
    }

    internal fun cancelNotification(identifier: String, typeID: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            Application.notification.cancel(identifier, typeID)
        }
    }

    override fun triggerNativeCrash() {
        Thread {
            Thread.sleep(200)
            throw RuntimeException("debug native crash")
        }.start()
    }

    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message!!)
    }

    override fun connectSSHAgent(): Int = -1
}
