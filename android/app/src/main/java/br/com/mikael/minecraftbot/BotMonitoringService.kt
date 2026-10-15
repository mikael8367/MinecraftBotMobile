package br.com.mikael.minecraftbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Keeps status monitoring alive after the Activity is closed.
 * The actual Minecraft bot runs on the remote backend; this service monitors it.
 */
class BotMonitoringService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var pollInFlight = false
    @Volatile private var latestTitle = "Monitoramento ativo"
    @Volatile private var latestText = "Consultando o estado do bot..."
    @Volatile private var started = false

    private val pollTask = object : Runnable {
        override fun run() {
            if (!started) return
            pollStatus()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.edit().putBoolean(KEY_MONITORING, false).apply()
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_START -> prefs.edit().putBoolean(KEY_MONITORING, true).apply()
            else -> {
                if (!prefs.getBoolean(KEY_MONITORING, false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        if (!started) {
            latestTitle = "Monitoramento ativo"
            latestText = "Conferindo a conexão do bot..."
            startForeground(NOTIFICATION_ID, buildNotification(latestTitle, latestText))
            started = true
            handler.removeCallbacks(pollTask)
            handler.post(pollTask)
        }
        return START_STICKY
    }

    private fun pollStatus() {
        if (pollInFlight) return
        pollInFlight = true
        thread(name = "MinecraftBotStatusMonitor") {
            var connection: HttpURLConnection? = null
            try {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val baseUrl = BuildConfig.API_BASE_URL.trim().trimEnd('/')
                val token = prefs.getString(KEY_API_TOKEN, "")?.trim().orEmpty()
                if (baseUrl.isBlank() || token.isBlank()) {
                    setNotificationState("Monitoramento ativo", "Configure o endereço da API e o API_TOKEN no app.")
                    return@thread
                }

                connection = (URL("${'$'}baseUrl/api/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Authorization", "Bearer ${'$'}token")
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code == 401 || code == 403) {
                    setNotificationState("Monitoramento ativo", "Token da API inválido. Abra o app para corrigir.")
                    return@thread
                }
                if (code !in 200..299) {
                    setNotificationState("Monitoramento ativo", "Backend indisponível (HTTP ${'$'}code). Tentando novamente.")
                    return@thread
                }

                val root = JSONObject(response)
                val info = root.optJSONObject("status") ?: root
                val connected = info.optBoolean("connected", false)
                val state = info.optString("connectionState", if (connected) "connected" else "disconnected")
                val username = info.optString("username", "Bot").ifBlank { "Bot" }
                val host = info.optString("host", "").trim()
                val port = info.optInt("port", 25565)
                val version = info.optString("serverVersion", info.optString("version", "")).trim()
                val engine = info.optString("engine", "").trim()
                val error = info.optString("lastError", "").trim()

                if (connected) {
                    val server = if (host.isNotBlank()) "${'$'}host:${'$'}port" else "servidor conectado"
                    val detail = buildString {
                        append("${'$'}username • ${'$'}server")
                        if (version.isNotBlank()) append(" • Minecraft ${'$'}version")
                        if (engine.isNotBlank()) append(" • ${'$'}engine")
                    }
                    setNotificationState("1 bot ON", detail)
                } else {
                    val detail = when (state) {
                        "connecting" -> "Conectando ${'$'}username..."
                        "kicked" -> "${'$'}username foi expulso pelo servidor."
                        "timeout" -> "Tempo limite ao conectar ${'$'}username."
                        "error" -> "Falha no bot ${'$'}username."
                        else -> "Nenhum bot conectado no momento."
                    } + if (error.isNotBlank()) " ${'$'}error" else ""
                    setNotificationState("Bot OFF • monitoramento ativo", detail.take(MAX_NOTIFICATION_TEXT))
                }
            } catch (_: Exception) {
                setNotificationState("Monitoramento ativo", "Sem resposta da API; nova tentativa automática.")
            } finally {
                connection?.disconnect()
                pollInFlight = false
            }
        }
    }

    private fun setNotificationState(title: String, text: String) {
        val safeTitle = title.take(80)
        val safeText = text.take(MAX_NOTIFICATION_TEXT)
        // All notification updates are serialized on the service's main thread.
        // This prevents an in-flight HTTP poll from recreating the notification after
        // the user has disabled monitoring and the foreground notification was removed.
        handler.post {
            if (!started) return@post
            latestTitle = safeTitle
            latestText = safeText
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(latestTitle, latestText))
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, BotMonitoringService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, REQUEST_STOP, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_minecraft)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_launcher_minecraft, "Desativar", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado do Minecraft Bot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra se o bot do Minecraft está conectado e monitora a API."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopMonitoring() {
        started = false
        handler.removeCallbacks(pollTask)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        started = false
        handler.removeCallbacks(pollTask)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "br.com.mikael.minecraftbot.action.START_MONITORING"
        const val ACTION_STOP = "br.com.mikael.minecraftbot.action.STOP_MONITORING"
        const val PREFS_NAME = "minecraftbot"
        const val KEY_MONITORING = "foreground_monitoring"
        const val KEY_API_TOKEN = "api_access"
        const val CHANNEL_ID = "minecraft_bot_monitor"
        const val NOTIFICATION_ID = 26031
        private const val REQUEST_OPEN_APP = 26032
        private const val REQUEST_STOP = 26033
        private const val POLL_INTERVAL_MS = 5000L
        private const val MAX_NOTIFICATION_TEXT = 240
    }
}
