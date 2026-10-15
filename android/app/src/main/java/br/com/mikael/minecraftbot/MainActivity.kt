package br.com.mikael.minecraftbot

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_MONITOR_NOTIFICATION_PERMISSION = 7301
    }

    private val bg = Color.rgb(10, 17, 14)
    private val panel = Color.rgb(21, 32, 26)
    private val green = Color.rgb(89, 231, 126)
    private val white = Color.rgb(238, 245, 240)
    private val muted = Color.rgb(155, 176, 162)
    private lateinit var root: LinearLayout
    private lateinit var pages: FrameLayout
    private lateinit var target: EditText
    private lateinit var command: EditText
    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var tabButtons: List<TextView>
    private var busy = false
    private var selectedTab = 0
    private lateinit var botListContainer: LinearLayout
    private lateinit var monitoringSwitch: Switch
    private lateinit var autoReconnectSwitch: Switch
    private var pendingMonitoringPermission = false
    private var lastShownAuthCode = ""

    // Fallback metadata from the exact Mineflayer package testedVersions lists.
    // On startup these are refreshed from /api/capabilities, so UI and backend agree.
    private var officialTestedVersions = listOf(
        "1.8.8", "1.9.4", "1.10.2", "1.11.2", "1.12.2", "1.13.2", "1.14.4",
        "1.15.2", "1.16.5", "1.17.1", "1.18.2", "1.19", "1.19.2", "1.19.3",
        "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3",
        "1.21.4", "1.21.5", "1.21.6", "1.21.8", "1.21.9", "1.21.11", "26.1"
    )
    private var mikaelTestedVersions = listOf(
        "1.8.8", "1.9.4", "1.10.2", "1.11.2", "1.12.2", "1.13.2", "1.14.4",
        "1.15.2", "1.16.5", "1.17.1", "1.18.2", "1.19", "1.19.2", "1.19.3",
        "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3",
        "1.21.4", "1.21.5", "1.21.6", "1.21.8", "1.21.9", "1.21.11",
        "26.1.2", "26.2", "26.3"
    )
    private val statusHandler = Handler(Looper.getMainLooper())
    private var pollingStatus = false
    private var lastPolledState = ""
    private var lastPolledError = ""
    private val statusPoller = object : Runnable {
        override fun run() {
            if (!pollingStatus) return
            if (!busy) request("GET", "/api/status", JSONObject(), quiet = true)
            statusHandler.postDelayed(this, 3000L)
        }
    }

    private fun supportedVersions(engineId: String): List<String> =
        listOf("auto") + if (engineId == "official") officialTestedVersions else mikaelTestedVersions

    private fun configureVersionSpinner(spinner: Spinner, engineId: String, preferred: String) {
        val options = supportedVersions(engineId)
        spinner.adapter = styledSpinnerAdapter(options)
        val preferredIndex = options.indexOf(preferred).let { if (it >= 0) it else 0 }
        spinner.setSelection(preferredIndex)
    }

    private fun watchEngineSelection(engine: Spinner, version: Spinner) {
        engine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preferred = version.selectedItem?.toString() ?: "auto"
                configureVersionSpinner(version, if (position == 1) "official" else "mikael", preferred)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun startStatusPolling() {
        pollingStatus = true
        lastPolledState = ""
        lastPolledError = ""
        statusHandler.removeCallbacks(statusPoller)
        statusHandler.postDelayed(statusPoller, 700L)
    }

    private fun stopStatusPolling() {
        pollingStatus = false
        statusHandler.removeCallbacks(statusPoller)
    }

    override fun onDestroy() {
        pendingMonitoringPermission = false
        stopStatusPolling()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(10),dp(16),dp(12)); setBackgroundColor(Color.rgb(5,16,12)); minimumHeight = resources.displayMetrics.heightPixels }
        scroll.addView(root); setContentView(scroll)

        val brand = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(2,4,2,14) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.ic_launcher_minecraft); scaleType=ImageView.ScaleType.FIT_CENTER }
        brand.addView(logo, LinearLayout.LayoutParams(dp(62),dp(62)))
        val brandText = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,0,0,0) }
        val titleRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        titleRow.addView(text("Minecraft",23,white,true))
        titleRow.addView(text("BotMobile",23,Color.rgb(0,238,91),true))
        brandText.addView(titleRow)
        brandText.addView(text("Seu bot de Minecraft no celular",13,muted,false))
        brand.addView(brandText,LinearLayout.LayoutParams(0,-2,1f))
        brand.addView(TextView(this).apply { text="⚙"; textSize=28f; setTextColor(green); gravity=Gravity.CENTER; setOnClickListener { showTab(2) } },LinearLayout.LayoutParams(dp(40),dp(52)))

        val hero = ImageView(this).apply { setImageResource(R.drawable.bot_hero); scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(panel,18); clipToOutline=true; contentDescription="Ilustração do bot voxel verde" }
        root.addView(hero, LinearLayout.LayoutParams(-1,dp(202)).apply { topMargin=dp(8); bottomMargin=dp(12) })

        val statusCard = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,16); setPadding(15,12,15,12) }
        statusCard.addView(text("STATUS DO BOT",10,muted,true))
        status = text("●  Desconectado",15,white,true); statusCard.addView(status)
        logs = text("Aguardando tentativa de conexão...",11,white,false).apply {
            setTextIsSelectable(true)
            setPadding(dp(8),dp(8),dp(8),dp(8))
            visibility = View.GONE
            background = rounded(Color.rgb(10,22,15),10)
        }
        statusCard.addView(logs)
        val logsToggle = Button(this).apply {
            text = "MOSTRAR LOGS"
            isAllCaps = false
            textSize = 11f
            setTextColor(white)
            background = rounded(Color.rgb(43,61,49),9)
            setOnClickListener {
                val showLogs = logs.visibility != View.VISIBLE
                logs.visibility = if (showLogs) View.VISIBLE else View.GONE
                text = if (showLogs) "OCULTAR LOGS" else "MOSTRAR LOGS"
            }
        }
        statusCard.addView(logsToggle, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })

        val monitoringRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(2), dp(2))
        }
        val monitoringLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        monitoringLabels.addView(text("Monitoramento em primeiro plano", 13, white, true))
        monitoringLabels.addView(text("Mantém a notificação ON/OFF ao fechar o app. O bot roda no backend; quedas do servidor ainda podem desconectar.", 10, muted, false).apply {
            setPadding(0, dp(3), dp(8), 0)
        })
        monitoringRow.addView(monitoringLabels, LinearLayout.LayoutParams(0, -2, 1f))
        monitoringSwitch = Switch(this).apply {
            contentDescription = "Ativar monitoramento em primeiro plano"
            isChecked = getSharedPreferences(BotMonitoringService.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(BotMonitoringService.KEY_MONITORING, false)
        }
        monitoringRow.addView(monitoringSwitch)
        monitoringSwitch.setOnCheckedChangeListener { _, enabled -> setForegroundMonitoring(enabled) }
        statusCard.addView(monitoringRow)

        val reconnectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(2), dp(2))
        }
        val reconnectLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reconnectLabels.addView(text("Reconectar automaticamente", 13, white, true))
        reconnectLabels.addView(text("Tenta conectar de novo se a conexão cair. Não reconecta se o servidor expulsar o bot ou se você desativar.", 10, muted, false).apply {
            setPadding(0, dp(3), dp(8), 0)
        })
        reconnectRow.addView(reconnectLabels, LinearLayout.LayoutParams(0, -2, 1f))
        autoReconnectSwitch = Switch(this).apply {
            contentDescription = "Ativar reconexão automática"
            isChecked = getSharedPreferences("minecraftbot", MODE_PRIVATE).getBoolean("auto_reconnect", false)
        }
        reconnectRow.addView(autoReconnectSwitch)
        autoReconnectSwitch.setOnCheckedChangeListener { _, enabled ->
            getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putBoolean("auto_reconnect", enabled).apply()
            request("POST", "/api/settings/auto-reconnect", JSONObject().put("enabled", enabled))
        }
        statusCard.addView(reconnectRow)

        val disableBotButton = Button(this).apply {
            text = "DESATIVAR BOT"
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            background = rounded(Color.rgb(117, 47, 47), 11)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Desativar bot?")
                    .setMessage("O bot será desconectado do servidor Minecraft.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("DESATIVAR") { _, _ ->
                        request("POST", "/api/disconnect", JSONObject())
                    }
                    .show()
            }
        }
        statusCard.addView(disableBotButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        root.addView(statusCard, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })

        val botHome = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,16); setPadding(dp(14),dp(14),dp(14),dp(14)) }
        val botHomeHeader = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        botHomeHeader.addView(text("MEUS BOTS",17,white,true), LinearLayout.LayoutParams(0,-2,1f))
        val createBotButton = Button(this).apply { text="+ CRIAR BOT"; isAllCaps=false; textSize=11f; setTextColor(bg); background=rounded(green,10) }
        createBotButton.setOnClickListener { showCreateBotDialog() }
        botHomeHeader.addView(createBotButton)
        botHome.addView(botHomeHeader)
        botHome.addView(text("Salve configurações diferentes para servidores e nomes de bot.",11,muted,false).apply { setPadding(0,4,0,10) })
        botListContainer = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        botHome.addView(botListContainer)
        root.addView(botHome, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
        refreshBotList()

        // Controles e comandos de IA ficam dentro das configurações de cada bot.
        loadEngineCapabilities()
        request("POST", "/api/settings/auto-reconnect", JSONObject().put(
            "enabled",
            getSharedPreferences("minecraftbot", MODE_PRIVATE).getBoolean("auto_reconnect", false)
        ), quiet = true)
        if (monitoringSwitch.isChecked) setForegroundMonitoring(true)
    }

    private fun setForegroundMonitoring(enabled: Boolean) {
        val prefs = getSharedPreferences(BotMonitoringService.PREFS_NAME, MODE_PRIVATE)
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingMonitoringPermission = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_MONITOR_NOTIFICATION_PERMISSION)
            return
        }

        prefs.edit().putBoolean(BotMonitoringService.KEY_MONITORING, enabled).apply()
        val serviceIntent = Intent(this, BotMonitoringService::class.java)
        if (enabled) {
            serviceIntent.action = BotMonitoringService.ACTION_START
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                else startService(serviceIntent)
                appendLog("Monitoramento em primeiro plano ativado. A notificação acompanha o estado retornado pelo backend.")
            } catch (error: Exception) {
                prefs.edit().putBoolean(BotMonitoringService.KEY_MONITORING, false).apply()
                monitoringSwitch.setOnCheckedChangeListener(null)
                monitoringSwitch.isChecked = false
                monitoringSwitch.setOnCheckedChangeListener { _, checked -> setForegroundMonitoring(checked) }
                Toast.makeText(this, "Não foi possível iniciar o monitoramento: ${error.message ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
            }
        } else {
            stopService(serviceIntent)
            appendLog("Monitoramento em primeiro plano desativado.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MONITOR_NOTIFICATION_PERMISSION) return
        val wasGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        pendingMonitoringPermission = false
        if (wasGranted) {
            setForegroundMonitoring(true)
        } else {
            getSharedPreferences(BotMonitoringService.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(BotMonitoringService.KEY_MONITORING, false).apply()
            monitoringSwitch.setOnCheckedChangeListener(null)
            monitoringSwitch.isChecked = false
            monitoringSwitch.setOnCheckedChangeListener { _, checked -> setForegroundMonitoring(checked) }
            Toast.makeText(this, "Permita notificações para usar o monitoramento em primeiro plano.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::monitoringSwitch.isInitialized) {
            val enabled = getSharedPreferences(BotMonitoringService.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(BotMonitoringService.KEY_MONITORING, false)
            if (monitoringSwitch.isChecked != enabled) monitoringSwitch.isChecked = enabled
        }
    }

    private val pageViews = mutableListOf<View>()
    private fun page(title:String, subtitle:String): LinearLayout {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,18); setPadding(16,16,16,16) }
        box.addView(text(title,18,white,true)); box.addView(text(subtitle,12,muted,false).apply { setPadding(0,4,0,12) })
        pageViews.add(box); return box
    }
    private fun buildBotsInfoPage() {
        val p=page("Seus bots salvos","Crie e gerencie bots na seção MEUS BOTS acima. Os bots criados aparecem logo abaixo do título.")
        p.addView(text("Toque em ABRIR / LIGAR no bot desejado para iniciar a conexão. Não é necessário cadastrar o mesmo bot novamente aqui.",13,white,false))
        button(p,"COPIAR LOG DE CONEXÃO",false) {
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MinecraftBotMobile log",logs.text.toString()))
            Toast.makeText(this,"Log copiado",Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEngineCapabilities() {
        thread {
            var connection: HttpURLConnection? = null
            try {
                val base = BuildConfig.API_BASE_URL.trimEnd('/')
                connection = (URL("$base/api/capabilities").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    requestMethod = "GET"
                }
                if (connection.responseCode !in 200..299) return@thread
                val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val engines = data.optJSONObject("engines") ?: return@thread
                val official = engines.optJSONObject("official")?.optJSONArray("testedVersions") ?: return@thread
                val mikael = engines.optJSONObject("mikael")?.optJSONArray("testedVersions") ?: return@thread
                val officialList = mutableListOf<String>()
                val mikaelList = mutableListOf<String>()
                for (i in 0 until official.length()) officialList.add(official.optString(i))
                for (i in 0 until mikael.length()) mikaelList.add(mikael.optString(i))
                if (officialList.isEmpty() || mikaelList.isEmpty()) return@thread
                runOnUiThread {
                    officialTestedVersions = officialList
                    mikaelTestedVersions = mikaelList
                    appendLog("Lista de versões sincronizada com o backend.")
                }
            } catch (_: Exception) {
                // Keep the built-in list so the app remains usable if capabilities is offline.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun savedBots(): org.json.JSONArray {
        val raw = getSharedPreferences("minecraftbot", MODE_PRIVATE).getString("saved_bots", "[]") ?: "[]"
        return try { org.json.JSONArray(raw) } catch (_: Exception) { org.json.JSONArray() }
    }

    private fun saveBots(items: org.json.JSONArray) {
        getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putString("saved_bots", items.toString()).apply()
        refreshBotList()
    }

    private fun refreshBotList() {
        if (!::botListContainer.isInitialized) return
        botListContainer.removeAllViews()
        val items = savedBots()
        if (items.length() == 0) {
            botListContainer.addView(text("Nenhum bot criado ainda. Toque em + CRIAR BOT.",12,muted,false).apply { setPadding(0,8,0,8) })
            return
        }
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(Color.rgb(10,22,15),12); setPadding(dp(12),dp(10),dp(12),dp(10)) }
            val botTitle = text("🤖  " + item.optString("name", "Bot") + "  ✎",15,white,true).apply {
                isClickable = true
                setOnClickListener { showBotSettingsDialog(i, item) }
            }
            card.addView(botTitle)
            card.addView(text(item.optString("host") + ":" + item.optString("port") + "  •  Minecraft " + item.optString("version","auto") + "  •  " + (if (item.optString("engine","mikael") == "official") "Mineflayer oficial" else "Mikael experimental") + " • " + (if (item.optString("auth","offline") == "microsoft") "Microsoft" else "Offline") + "\nToque no nome para abrir as configurações",11,muted,false).apply { setPadding(0,4,0,8) })
            card.setOnClickListener { showBotSettingsDialog(i, item) }
            val actions = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            val use = Button(this).apply { text="LIGAR"; isAllCaps=false; textSize=10f; setTextColor(bg); background=rounded(green,9) }
            use.setOnClickListener {
                val wanted = item.optString("version","auto")
                appendLog("Bot selecionado: " + item.optString("name"))
                lastShownAuthCode = ""
                request("POST","/api/connect",JSONObject()
                    .put("host",item.optString("host"))
                    .put("port",item.optInt("port",25565))
                    .put("username",item.optString("name"))
                    .put("accountId",item.optString("accountId",""))
                    .put("auth",item.optString("auth","offline"))
                    .put("version",wanted)
                    .put("engine",item.optString("engine","mikael")))
            }
            actions.addView(use,LinearLayout.LayoutParams(0,dp(42),1f).apply { leftMargin=dp(5) })
            val check = Button(this).apply { text="VERIFICAR"; isAllCaps=false; textSize=10f; setTextColor(white); background=rounded(Color.rgb(43,61,49),9) }
            check.setOnClickListener {
                appendLog("Verificando servidor: " + item.optString("host") + ":" + item.optInt("port",25565))
                request("POST", "/api/server-check", JSONObject()
                    .put("host", item.optString("host"))
                    .put("port", item.optInt("port",25565)))
            }
            actions.addView(check,LinearLayout.LayoutParams(0,dp(42),1f).apply { leftMargin=dp(5) })
            val delete = Button(this).apply { text="EXCLUIR"; isAllCaps=false; textSize=10f; setTextColor(white); background=rounded(Color.rgb(65,43,43),9) }
            delete.setOnClickListener {
                val next = org.json.JSONArray()
                for (j in 0 until items.length()) if (j != i) next.put(items.getJSONObject(j))
                saveBots(next)
            }
            actions.addView(delete,LinearLayout.LayoutParams(0,dp(42),1f).apply { leftMargin=dp(6) })
            card.addView(actions)
            botListContainer.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
    }

    private fun showBotSettingsDialog(index: Int, item: JSONObject) {
        val dialog = android.app.Dialog(this)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(7, 17, 12), 22)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("MIKAEL BOTMOBILE", 10, green, true))
        titleBox.addView(text(item.optString("name", "Meu bot"), 20, white, true).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(white)
            background = rounded(Color.rgb(25, 39, 30), 12)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        shell.addView(header)
        shell.addView(View(this).apply { setBackgroundColor(Color.rgb(35, 61, 43)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) })

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(Color.rgb(12, 24, 17), 13); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val tabConfig = Button(this).apply { text = "⚙  CONFIGURAÇÃO"; isAllCaps = false; textSize = 11f }
        val tabControl = Button(this).apply { text = "🎮  CONTROLES"; isAllCaps = false; textSize = 11f }
        fun styleTab(button: Button, selected: Boolean) {
            button.setTextColor(if (selected) bg else muted)
            button.background = rounded(if (selected) green else Color.TRANSPARENT, 10)
        }
        styleTab(tabConfig, true); styleTab(tabControl, false)
        tabs.addView(tabConfig, LinearLayout.LayoutParams(0, dp(44), 1f))
        tabs.addView(tabControl, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        shell.addView(tabs)

        val body = FrameLayout(this)
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val configScroll = ScrollView(this).apply { isFillViewport = true }
        val config = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(8)) }
        configScroll.addView(config)
        fun section(title: String, subtitle: String? = null): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.rgb(13, 27, 18), 14)
                setPadding(dp(12), dp(10), dp(12), dp(12))
            }
            card.addView(text(title, 11, green, true).apply { setPadding(0, 0, 0, dp(3)) })
            if (subtitle != null) card.addView(text(subtitle, 10, muted, false).apply { setPadding(0, 0, 0, dp(7)) })
            config.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            return card
        }
        fun fieldLabel(parent: LinearLayout, value: String) {
            parent.addView(text(value.uppercase(), 9, muted, true).apply { setPadding(0, dp(7), 0, dp(3)) })
        }
        fun addField(parent: LinearLayout, caption: String, view: View) {
            fieldLabel(parent, caption)
            parent.addView(view, LinearLayout.LayoutParams(-1, dp(46)))
        }
        val botName = EditText(this).apply { hint = "Nome do bot"; setSingleLine(true); setText(item.optString("name")); styleBotInput(this) }
        val serverIp = EditText(this).apply { hint = "IP ou domínio"; setSingleLine(true); setText(item.optString("host")); styleBotInput(this) }
        val serverPort = EditText(this).apply { hint = "Porta"; setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(item.optString("port", "25565")); styleBotInput(this) }
        val skinName = EditText(this).apply { hint = "Nome de jogador (opcional)"; setSingleLine(true); setText(item.optString("skinName", "")); styleBotInput(this) }
        val mcVersion = Spinner(this).apply { styleBotSpinner(this) }
        val engine = Spinner(this).apply { styleBotSpinner(this) }
        val authMode = Spinner(this).apply { styleBotSpinner(this) }
        authMode.adapter = styledSpinnerAdapter(listOf("Offline (servidor offline)", "Microsoft (conta Java válida)"))
        authMode.setSelection(if (item.optString("auth", "offline") == "microsoft") 1 else 0)
        val accountId = EditText(this).apply { hint = "E-mail ou identificador Microsoft"; setSingleLine(true); setText(item.optString("accountId", "")); styleBotInput(this) }
        engine.adapter = styledSpinnerAdapter(listOf("Mineflayer Mikael", "Mineflayer"))
        val selectedEngine = item.optString("engine", "mikael")
        engine.setSelection(if (selectedEngine == "official") 1 else 0)
        configureVersionSpinner(mcVersion, selectedEngine, item.optString("version", "auto"))
        watchEngineSelection(engine, mcVersion)

        val identityCard = section("IDENTIDADE", "Como o bot será identificado")
        addField(identityCard, "Nome do bot", botName)
        addField(identityCard, "Skin • nome de referência", skinName)
        identityCard.addView(text("A aparência real depende da autenticação e do suporte do servidor.", 10, muted, false).apply { setPadding(0, dp(5), 0, 0) })

        val serverCard = section("SERVIDOR", "Endereço de conexão")
        addField(serverCard, "IP / domínio", serverIp)
        fieldLabel(serverCard, "PORTA")
        serverCard.addView(serverPort, LinearLayout.LayoutParams(-1, dp(46)))

        val gameCard = section("JOGO", "Compatibilidade e conexão")
        addField(gameCard, "Versão do Minecraft", mcVersion)
        addField(gameCard, "Motor de conexão", engine)

        val accountCard = section("CONTA", "Escolha como o bot autentica")
        addField(accountCard, "Método de autenticação", authMode)
        addField(accountCard, "Identificador Microsoft", accountId)
        accountCard.addView(text("Não coloque sua senha aqui. A autorização Microsoft deve ser concluída pelo fluxo oficial.", 10, muted, false).apply { setPadding(0, dp(5), 0, 0) })

        val reconnectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(13, 27, 18), 14)
            setPadding(dp(12), dp(10), dp(8), dp(10))
        }
        val reconnectTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reconnectTexts.addView(text("Reconexão automática", 13, white, true))
        reconnectTexts.addView(text("Tentar conectar novamente se cair.", 10, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        reconnectRow.addView(reconnectTexts, LinearLayout.LayoutParams(0, -2, 1f))
        val reconnect = Switch(this).apply { isChecked = getSharedPreferences("minecraftbot", MODE_PRIVATE).getBoolean("auto_reconnect", false) }
        reconnectRow.addView(reconnect)
        reconnect.setOnCheckedChangeListener { _, enabled ->
            getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putBoolean("auto_reconnect", enabled).apply()
            request("POST", "/api/settings/auto-reconnect", JSONObject().put("enabled", enabled))
        }
        config.addView(reconnectRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val manage = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBot = Button(this).apply { text = "▶ INICIAR BOT"; isAllCaps = false; textSize = 11f; setTextColor(bg); background = rounded(green, 10) }
        startBot.setOnClickListener {
            val name = botName.text.toString().trim()
            val host = serverIp.text.toString().trim()
            val port = serverPort.text.toString().toIntOrNull()
            if (name.isBlank() || host.isBlank() || port == null || port !in 1..65535) {
                Toast.makeText(this, "Confira nome, IP e porta antes de iniciar.", Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            val v = mcVersion.selectedItem?.toString() ?: "auto"
            val engineId = if (engine.selectedItemPosition == 1) "official" else "mikael"
            val authValue = if (authMode.selectedItemPosition == 1) "microsoft" else "offline"
            val accountValue = accountId.text.toString().trim()
            if (authValue == "microsoft" && !accountValue.matches(Regex("[A-Za-z0-9][A-Za-z0-9._@+-]{0,127}"))) {
                Toast.makeText(this, "Informe um identificador Microsoft válido, sem espaços.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val saved = savedBots()
            if (index in 0 until saved.length()) {
                saved.put(index, JSONObject().put("name", name).put("host", host).put("port", port)
                    .put("skinName", skinName.text.toString().trim()).put("version", v).put("engine", engineId)
                    .put("auth", authValue).put("accountId", accountValue))
                saveBots(saved)
            }
            request("POST", "/api/connect", JSONObject().put("host", host).put("port", port).put("username", name)
                .put("accountId", accountValue).put("auth", authValue).put("version", v).put("engine", engineId))
            Toast.makeText(this, "Pedido para iniciar enviado.", Toast.LENGTH_SHORT).show()
        }
        val stopBot = Button(this).apply { text = "■ DESLIGAR"; isAllCaps = false; textSize = 11f; setTextColor(white); background = rounded(Color.rgb(111, 48, 48), 10) }
        stopBot.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Desligar bot?").setMessage("O bot conectado será desconectado.")
                .setNegativeButton("Cancelar", null).setPositiveButton("DESLIGAR") { _, _ -> request("POST", "/api/disconnect", JSONObject()); reconnect.isChecked = false }.show()
        }
        manage.addView(startBot, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        manage.addView(stopBot, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        config.addView(manage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val delete = Button(this).apply { text = "EXCLUIR BOT SALVO"; isAllCaps = false; textSize = 11f; setTextColor(white); background = rounded(Color.rgb(76, 39, 39), 10) }
        delete.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Excluir este bot?").setMessage("Isso remove a configuração salva deste celular. Não apaga sua conta nem o servidor.")
                .setNegativeButton("Cancelar", null).setPositiveButton("EXCLUIR") { _, _ ->
                    val items = savedBots(); val next = org.json.JSONArray()
                    for (j in 0 until items.length()) if (j != index) next.put(items.getJSONObject(j))
                    saveBots(next); dialog.dismiss()
                }.show()
        }
        config.addView(delete, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })

        val controlScroll = ScrollView(this).apply { isFillViewport = true }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(8)) }
        fun controlSection(title: String, subtitle: String? = null): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.rgb(13, 27, 18), 14)
                setPadding(dp(11), dp(10), dp(11), dp(11))
            }
            card.addView(text(title, 11, green, true).apply { setPadding(0, 0, 0, dp(4)) })
            if (subtitle != null) card.addView(text(subtitle, 10, muted, false).apply { setPadding(0, 0, 0, dp(7)) })
            controls.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            return card
        }
        val movementCard = controlSection("MOVIMENTO", "Toque para executar uma ação")
        val grid = GridLayout(this).apply { columnCount = 3 }
        movementCard.addView(grid)
        fun controlButton(labelText: String, action: String) {
            val b = Button(this).apply {
                text = labelText; isAllCaps = false; textSize = 11f
                setTextColor(white); background = rounded(Color.rgb(34, 57, 41), 11)
                minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(2), dp(4), dp(2), dp(4))
            }
            b.setOnClickListener { request("POST", "/api/action", JSONObject().put("action", action)) }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0; height = dp(52)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        controlButton("↑ Frente", "forward"); controlButton("↓ Trás", "back"); controlButton("→ Direita", "right")
        controlButton("← Esquerda", "left"); controlButton("⤒ Pular", "jump"); controlButton("■ Parar", "stop")
        controlButton("⚡ Correr", "sprint"); controlButton("⌁ Agachar", "sneak"); controlButton("⚔ Atacar", "attack")

        val chatCard = controlSection("MENSAGEM NO CHAT", "Enviar uma mensagem no servidor")
        val chatField = EditText(this).apply { hint = "Digite sua mensagem"; setSingleLine(true); styleBotInput(this) }
        chatCard.addView(chatField, LinearLayout.LayoutParams(-1, dp(46)))
        val sendChat = Button(this).apply { text = "ENVIAR MENSAGEM"; isAllCaps = false; setTextColor(bg); background = rounded(green, 10) }
        sendChat.setOnClickListener {
            val message = chatField.text.toString().trim()
            if (message.isBlank()) chatField.error = "Digite uma mensagem"
            else request("POST", "/api/chat/send", JSONObject().put("message", message))
        }
        chatCard.addView(sendChat, LinearLayout.LayoutParams(-1, dp(43)).apply { topMargin = dp(6) })

        val followCard = controlSection("SEGUIR JOGADOR", "Informe o nome exato de quem o bot deve seguir")
        val followField = EditText(this).apply { hint = "Nome do jogador"; setSingleLine(true); styleBotInput(this) }
        followCard.addView(followField, LinearLayout.LayoutParams(-1, dp(46)))
        val follow = Button(this).apply { text = "COMEÇAR A SEGUIR"; isAllCaps = false; setTextColor(bg); background = rounded(green, 10) }
        follow.setOnClickListener {
            val name = followField.text.toString().trim()
            if (name.isBlank()) followField.error = "Digite o nome do jogador"
            else request("POST", "/api/action", JSONObject().put("action", "follow").put("target", name))
        }
        followCard.addView(follow, LinearLayout.LayoutParams(-1, dp(43)).apply { topMargin = dp(6) })

        val coordinateCard = controlSection("IR PARA COORDENADAS", "Digite as posições X, Y e Z do mundo")
        val coords = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val coordX = EditText(this).apply { hint = "X"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; styleBotInput(this) }
        val coordY = EditText(this).apply { hint = "Y"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; styleBotInput(this) }
        val coordZ = EditText(this).apply { hint = "Z"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; styleBotInput(this) }
        coords.addView(coordX, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
        coords.addView(coordY, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
        coords.addView(coordZ, LinearLayout.LayoutParams(0, dp(46), 1f))
        coordinateCard.addView(coords)
        val goto = Button(this).apply { text = "IR ATÉ X / Y / Z"; isAllCaps = false; setTextColor(bg); background = rounded(green, 10) }
        goto.setOnClickListener {
            val x = coordX.text.toString().toDoubleOrNull()
            val y = coordY.text.toString().toDoubleOrNull()
            val z = coordZ.text.toString().toDoubleOrNull()
            if (x == null || y == null || z == null) Toast.makeText(this, "Informe X, Y e Z válidos.", Toast.LENGTH_LONG).show()
            else request("POST", "/api/action", JSONObject().put("action", "goto").put("x", x).put("y", y).put("z", z))
        }
        coordinateCard.addView(goto, LinearLayout.LayoutParams(-1, dp(43)).apply { topMargin = dp(6) })
        controls.addView(text("O bot precisa estar conectado. Seguir jogador e navegar por coordenadas dependem do suporte do pathfinder nesta versão.", 10, muted, false).apply { setPadding(dp(2), dp(2), dp(2), dp(8) })
        controlScroll.addView(controls)

        fun selectTab(showControls: Boolean) {
            body.removeAllViews()
            body.addView(if (showControls) controlScroll else configScroll)
            styleTab(tabConfig, !showControls); styleTab(tabControl, showControls)
        }
        tabConfig.setOnClickListener { selectTab(false) }
        tabControl.setOnClickListener { selectTab(true) }
        selectTab(false)

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        val cancel = Button(this).apply { text = "FECHAR"; isAllCaps = false; setTextColor(white); background = rounded(Color.rgb(34, 49, 39), 11); setOnClickListener { dialog.dismiss() } }
        val save = Button(this).apply { text = "SALVAR ALTERAÇÕES"; isAllCaps = false; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(bg); background = rounded(green, 11) }
        save.setOnClickListener {
            val name = botName.text.toString().trim(); val host = serverIp.text.toString().trim(); val port = serverPort.text.toString().toIntOrNull()
            val authValue = if (authMode.selectedItemPosition == 1) "microsoft" else "offline"
            val accountValue = accountId.text.toString().trim()
            if (name.isBlank() || host.isBlank() || port == null || port !in 1..65535) { Toast.makeText(this, "Confira nome, IP e porta.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            if (authValue == "microsoft" && !accountValue.matches(Regex("[A-Za-z0-9][A-Za-z0-9._@+-]{0,127}"))) { Toast.makeText(this, "Informe um identificador Microsoft válido, sem espaços.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val items = savedBots(); if (index !in 0 until items.length()) return@setOnClickListener
            items.put(index, JSONObject().put("name", name).put("host", host).put("port", port).put("skinName", skinName.text.toString().trim())
                .put("version", mcVersion.selectedItem?.toString() ?: "auto").put("engine", if (engine.selectedItemPosition == 1) "official" else "mikael")
                .put("auth", authValue).put("accountId", accountValue))
            saveBots(items); Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        footer.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 0.8f).apply { rightMargin = dp(5) })
        footer.addView(save, LinearLayout.LayoutParams(0, dp(46), 1.2f).apply { leftMargin = dp(5) })
        shell.addView(footer)
        dialog.setContentView(shell)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
    }

    private fun showBotControlsDialog(item: JSONObject) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        content.addView(text("Controlando: " + item.optString("name", "Bot"), 14, green, true))
        content.addView(text("As ações vão para o bot atualmente conectado no servidor.", 11, muted, false).apply { setPadding(0, dp(4), 0, dp(10)) })
        val grid = GridLayout(this).apply { columnCount = 3 }
        content.addView(grid)
        fun action(label: String, command: String) {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                setTextColor(white)
                background = rounded(Color.rgb(38, 62, 46), 11)
                setOnClickListener {
                    request("POST", "/api/action", JSONObject().put("action", command))
                }
            }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(54)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        action("↑ Frente", "forward")
        action("⤒ Pular", "jump")
        action("⚡ Sprint", "sprint")
        action("← Esquerda", "left")
        action("■ Parar", "stop")
        action("Direita →", "right")
        action("↓ Ré", "back")
        action("⚔ Atacar", "attack")
        val targetField = EditText(this).apply {
            hint = "Nome exato do jogador"
            setSingleLine(true)
            styleBotInput(this)
        }
        content.addView(text("SEGUIR JOGADOR", 10, muted, true).apply { setPadding(0, dp(12), 0, dp(3)) })
        content.addView(targetField, LinearLayout.LayoutParams(-1, dp(48)))
        val follow = Button(this).apply {
            text = "SEGUIR"
            isAllCaps = false
            setTextColor(bg)
            background = rounded(green, 11)
            setOnClickListener {
                val name = targetField.text.toString().trim()
                if (name.isBlank()) {
                    targetField.error = "Digite o nome do jogador"
                } else {
                    request("POST", "/api/action", JSONObject().put("action", "follow").put("target", name))
                }
            }
        }
        content.addView(follow, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
        AlertDialog.Builder(this)
            .setTitle("🎮 Controles do bot")
            .setView(ScrollView(this).apply { isFillViewport = true; addView(content) })
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showBotAiDialog(item: JSONObject) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(bg)
        }
        content.addView(text("Comandos para: " + item.optString("name", "Bot"), 14, green, true))
        content.addView(text("Exemplos: andar, pular, atacar ou parar. A IA online ainda não está conectada.", 11, muted, false).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val commandField = EditText(this).apply {
            hint = "O que o bot deve fazer?"
            setSingleLine(true)
            styleBotInput(this)
        }
        content.addView(commandField, LinearLayout.LayoutParams(-1, dp(48)))
        val send = Button(this).apply {
            text = "ENVIAR COMANDO"
            isAllCaps = false
            setTextColor(bg)
            background = rounded(green, 11)
            setOnClickListener {
                val message = commandField.text.toString().trim()
                if (message.isBlank()) {
                    commandField.error = "Digite um comando"
                } else {
                    request("POST", "/api/chat", JSONObject().put("message", message))
                }
            }
        }
        content.addView(send, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(9) })
        AlertDialog.Builder(this)
            .setTitle("✦ Comandos IA")
            .setView(content)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showCreateBotDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundColor(bg)
        }
        form.addView(text("CONFIGURE SUA CONEXÃO", 11, green, true).apply { setPadding(0, 0, 0, dp(4)) })
        form.addView(text("Preencha os dados e escolha como o bot vai entrar no servidor.", 12, muted, false).apply { setPadding(0, 0, 0, dp(14)) })
        val botName = EditText(this).apply { hint="Nome do bot (ex.: MikaelBot)"; setSingleLine(true); styleBotInput(this) }
        val serverIp = EditText(this).apply { hint="IP ou domínio (ex.: servidor.aternos.me)"; setSingleLine(true); styleBotInput(this) }
        val serverPort = EditText(this).apply { hint="Porta (ex.: 25565)"; setSingleLine(true); inputType=android.text.InputType.TYPE_CLASS_NUMBER; styleBotInput(this) }
        val mcVersion = Spinner(this).apply { styleBotSpinner(this) }
        val engine = Spinner(this).apply { styleBotSpinner(this) }
        val authMode = Spinner(this).apply { styleBotSpinner(this) }
        authMode.adapter = styledSpinnerAdapter(listOf("Offline (servidor offline)","Microsoft (conta Java válida)"))
        val accountId = EditText(this).apply {
            setSingleLine(true)
            hint = "E-mail ou identificador da conta Microsoft"
            styleBotInput(this)
        }
        engine.adapter = styledSpinnerAdapter(listOf("Mineflayer Mikael","Mineflayer"))
        configureVersionSpinner(mcVersion, "mikael", "26.3")
        watchEngineSelection(engine, mcVersion)

        form.addView(text("NOME DO BOT",10,muted,true)); form.addView(botName)
        form.addView(text("IP / DOMÍNIO",10,muted,true)); form.addView(serverIp)
        form.addView(text("PORTA",10,muted,true)); form.addView(serverPort)
        form.addView(text("AUTENTICAÇÃO",10,muted,true)); form.addView(authMode)
        form.addView(text("IDENTIFICADOR MICROSOFT (não é a senha)",10,muted,true)); form.addView(accountId)
        form.addView(text("No modo Microsoft, o app mostra um código oficial de autorização. Não informe nem salve sua senha aqui.",11,muted,false))
        form.addView(text("VERSÃO DO MINECRAFT",10,muted,true)); form.addView(mcVersion)
        form.addView(text("MOTOR DE CONEXÃO",10,muted,true)); form.addView(engine)
        form.addView(text("A seleção inicial é 26.3 + Mikael experimental. A lista vem do motor, mas o servidor real precisa ser testado.",11,muted,false))
        createStyledFormDialog("CRIAR NOVO BOT", form, "SALVAR BOT") { dialog ->
            val name = botName.text.toString().trim()
            val ip = serverIp.text.toString().trim()
            val p = serverPort.text.toString().toIntOrNull()
            val v = mcVersion.selectedItem?.toString() ?: "auto"
            val engineId = if (engine.selectedItemPosition == 1) "official" else "mikael"
            val authValue = if (authMode.selectedItemPosition == 1) "microsoft" else "offline"
            val accountValue = accountId.text.toString().trim()
            if (name.isBlank() || ip.isBlank() || p == null || p !in 1..65535) {
                Toast.makeText(this,"Preencha nome, IP e uma porta válida.",Toast.LENGTH_LONG).show()
                return@createStyledFormDialog
            }
            if (authValue == "microsoft" && !accountValue.matches(Regex("[A-Za-z0-9][A-Za-z0-9._@+-]{0,127}"))) {
                Toast.makeText(this,"Informe um identificador Microsoft válido, sem espaços.",Toast.LENGTH_LONG).show()
                return@createStyledFormDialog
            }
            val items = savedBots()
            items.put(JSONObject().put("name",name).put("host",ip).put("port",p).put("version",v).put("engine",engineId).put("auth",authValue).put("accountId",accountValue))
            saveBots(items)
            Toast.makeText(this,"Bot salvo!",Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }


    private fun createStyledFormDialog(
        heading: String,
        form: View,
        saveLabel: String,
        onSave: (android.app.Dialog) -> Unit
    ) {
        val dialog = android.app.Dialog(this)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(7, 17, 12), 22)
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(2), dp(14))
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("MIKAEL BOTMOBILE", 10, green, true))
        titleBox.addView(text(heading, 19, white, true).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(Color.rgb(25, 39, 30), 12)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        shell.addView(header)
        val separator = View(this).apply { setBackgroundColor(Color.rgb(35, 61, 43)) }
        shell.addView(separator, LinearLayout.LayoutParams(-1, dp(1)))
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(10))
            addView(form)
        }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val cancel = Button(this).apply {
            text = "CANCELAR"
            isAllCaps = false
            textSize = 12f
            setTextColor(white)
            background = rounded(Color.rgb(34, 49, 39), 12)
            setOnClickListener { dialog.dismiss() }
        }
        val save = Button(this).apply {
            text = saveLabel
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            background = rounded(green, 12)
            setOnClickListener { onSave(dialog) }
        }
        footer.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        footer.addView(save, LinearLayout.LayoutParams(0, dp(48), 1.25f).apply { leftMargin = dp(6) })
        shell.addView(footer)
        dialog.setContentView(shell)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(-1, -1)
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(-1, -1)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
    }

    private fun styleBotInput(field: EditText) {
        field.textSize = 14f
        field.setTextColor(white)
        field.setHintTextColor(muted)
        field.setPadding(dp(13), dp(11), dp(13), dp(11))
        field.background = rounded(Color.rgb(12, 24, 17), 11)
        field.minimumHeight = dp(48)
    }

    private fun styledSpinnerAdapter(options: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            private fun makeRow(value: String, recycled: View?, dropdown: Boolean): TextView {
                val row = (recycled as? TextView) ?: TextView(this@MainActivity)
                row.text = value
                row.textSize = 14f
                row.setTextColor(if (dropdown) white else green)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(12), dp(if (dropdown) 12 else 9), dp(12), dp(if (dropdown) 12 else 9))
                row.background = if (dropdown) rounded(Color.rgb(16, 31, 21), 8) else rounded(Color.rgb(12, 24, 17), 10)
                return row
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                makeRow(getItem(position).orEmpty(), convertView, false)
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                makeRow(getItem(position).orEmpty(), convertView, true)
        }.also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun styleBotSpinner(spinner: Spinner) {
        spinner.setPadding(dp(8), dp(5), dp(8), dp(5))
        spinner.background = rounded(Color.rgb(12, 24, 17), 11)
    }

    private fun buildControlPage() {
        val p=page("Controle rápido","Cada toque envia uma ação curta ao bot.")
        val grid=GridLayout(this).apply { columnCount=3; rowCount=3 }
        p.addView(grid)
        fun move(label:String,action:String) {
            val b=Button(this).apply { text=label; isAllCaps=false; textSize=12f; setTextColor(white); background=rounded(Color.rgb(38,62,46),12) }
            b.setOnClickListener { request("POST","/api/action",JSONObject().put("action",action)) }
            grid.addView(b,GridLayout.LayoutParams().apply { width=0; height=dp(58); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(3,3,3,3) })
        }
        move("↑ Frente","forward"); move("⤒ Pular","jump"); move("⚡ Sprint","sprint")
        move("← Esquerda","left"); move("■ Parar","stop"); move("Direita →","right")
        move("↓ Ré","back"); move("⚔ Atacar","attack")
        p.addView(text("SEGUIR JOGADOR",10,muted,true).apply { setPadding(0,16,0,4) })
        target=field(p,"Nome exato do jogador","")
        button(p,"SEGUIR",true) { request("POST","/api/action",JSONObject().put("action","follow").put("target",target.text.toString().trim())) }
        button(p,"DESCONECTAR",false) { request("POST","/api/disconnect",JSONObject()) }
    }
    private fun buildAiPage() {
        val p=page("Assistente","Comandos em linguagem natural simplificada.")
        p.addView(text("Experimente: “andar”, “pular”, “atacar” ou “parar”.",13,muted,false).apply { setPadding(0,0,0,12) })
        command=field(p,"O que o bot deve fazer?","")
        button(p,"ENVIAR COMANDO",true) { request("POST","/api/chat",JSONObject().put("message",command.text.toString())) }
        p.addView(text("Esta versão interpreta comandos locais básicos. IA online ainda não está conectada.",11,muted,false).apply { setPadding(0,12,0,0) })
    }
    private fun showTab(index:Int) {
        selectedTab=index
        pages.removeAllViews()
        pageViews.getOrNull(index)?.let { pages.addView(it) }
        tabButtons.forEachIndexed { i,t -> t.background=rounded(if(i==index) green else panel,11); t.setTextColor(if(i==index) bg else muted) }
    }
    private fun field(parent:LinearLayout,hint:String,value:String):EditText {
        parent.addView(text(hint,10,muted,true).apply { setPadding(0,8,0,3) })
        val e=EditText(this).apply {
            setSingleLine(true); setText(value); this.hint=hint; textSize=14f
            setTextColor(white); setHintTextColor(muted); setPadding(dp(12),dp(9),dp(12),dp(9))
            background=rounded(Color.rgb(12,22,16),10)
        }
        parent.addView(e,LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=4 })
        return e
    }
    private fun button(parent:LinearLayout,label:String,primary:Boolean,onClick:()->Unit) {
        val b=Button(this).apply { text=label; isAllCaps=false; textSize=12f; typeface=Typeface.DEFAULT_BOLD; setTextColor(if(primary) bg else white); background=rounded(if(primary) green else Color.rgb(43,61,49),12); setOnClickListener { if(!busy) onClick() } }
        parent.addView(b,LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=8 })
    }
    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun outline(color:Int,radius:Int)=GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=dp(radius).toFloat(); setStroke(dp(1),color) }
    private fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()

    private fun showMicrosoftLoginDialog(verificationUri: String, code: String) {
        val candidate = android.net.Uri.parse(verificationUri)
        val host = candidate.host?.lowercase().orEmpty()
        val allowedHost = host == "microsoft.com" || host.endsWith(".microsoft.com") ||
            host == "microsoftonline.com" || host.endsWith(".microsoftonline.com")
        val safeUri = if (candidate.scheme == "https" && allowedHost) {
            candidate.toString()
        } else {
            "https://microsoft.com/devicelogin"
        }

        AlertDialog.Builder(this)
            .setTitle("Autorizar conta Microsoft")
            .setMessage("Para servidores que exigem uma conta Java válida, abra a página oficial e digite este código:\n\n$code\n\nA senha não é solicitada pelo MinecraftBotMobile. Após autorizar, volte ao app e aguarde a conexão.")
            .setPositiveButton("Abrir Microsoft") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(safeUri)))
                } catch (error: Exception) {
                    Toast.makeText(this, "Não foi possível abrir o navegador: ${error.message ?: "erro"}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun appendLog(message:String) {
        if (!::logs.isInitialized) return
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logs.text = (logs.text.toString() + "\n[" + stamp + "] " + message).takeLast(12000)
    }

    private fun request(method:String,path:String,body:JSONObject,quiet:Boolean=false) {
        if (busy) {
            if (!quiet) appendLog("Aguarde a solicitação atual terminar.")
            return
        }
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val secret = getSharedPreferences("minecraftbot", MODE_PRIVATE).getString("api_access", "")?.trim().orEmpty()
        if (base.isBlank() || (!base.startsWith("https://") && !base.startsWith("http://"))) {
            status.text = "● URL do servidor inválida"
            return
        }
        if (secret.isBlank()) {
            if (quiet) { stopStatusPolling(); return }
            val input = EditText(this).apply {
                hint = "Senha de acesso da API"
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Acesso ao bot")
                .setMessage("Digite o API_TOKEN configurado no backend. Ele não fica embutido no APK.")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar e conectar") { _, _ ->
                    val entered = input.text.toString().trim()
                    if (entered.isBlank()) {
                        status.text = "● Senha não informada"
                        return@setPositiveButton
                    }
                    getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putString("api_access", entered).apply()
                    request(method, path, body, quiet)
                }.show()
            return
        }

        busy = true
        if (!quiet) status.text = "● Enviando pedido..."
        thread {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Content-Type", "application/json")
                    if (method != "GET") {
                        doOutput = true
                        outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                runOnUiThread {
                    if (code == 401 || code == 403) {
                        getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().remove("api_access").apply()
                        status.text = "● Token inválido — informe o token novo"
                        if (!quiet) appendLog("A API recusou o token (HTTP $code); o token salvo foi apagado.")
                        stopStatusPolling()
                        busy = false
                        conn?.disconnect()
                        return@runOnUiThread
                    }

                    val parsed = try { JSONObject(response) } catch (_: Exception) { JSONObject() }
                    val state = parsed.optJSONObject("status") ?: parsed
                    val connected = state.optBoolean("connected", parsed.optBoolean("connected", false))
                    val apiError = parsed.optString("error", "").takeUnless { it.isBlank() || it == "null" }.orEmpty()
                    val stateError = state.optString("lastError", "").takeUnless { it.isBlank() || it == "null" }.orEmpty()
                    val serverError = apiError.ifBlank { stateError }
                    val connectionState = state.optString("connectionState", if (connected) "connected" else "disconnected")
                    if (path == "/api/status" && state.has("autoReconnect") && ::autoReconnectSwitch.isInitialized) {
                        val enabled = state.optBoolean("autoReconnect", false)
                        if (autoReconnectSwitch.isChecked != enabled) {
                            autoReconnectSwitch.setOnCheckedChangeListener(null)
                            autoReconnectSwitch.isChecked = enabled
                            autoReconnectSwitch.setOnCheckedChangeListener { _, checked ->
                                getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putBoolean("auto_reconnect", checked).apply()
                                request("POST", "/api/settings/auto-reconnect", JSONObject().put("enabled", checked))
                            }
                        }
                    }
                    if (path == "/api/disconnect" && code in 200..299 && ::autoReconnectSwitch.isInitialized) {
                        autoReconnectSwitch.setOnCheckedChangeListener(null)
                        autoReconnectSwitch.isChecked = false
                        autoReconnectSwitch.setOnCheckedChangeListener { _, checked ->
                            getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putBoolean("auto_reconnect", checked).apply()
                            request("POST", "/api/settings/auto-reconnect", JSONObject().put("enabled", checked))
                        }
                        getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putBoolean("auto_reconnect", false).apply()
                    }
                    val authState = state.optJSONObject("authCode")
                    val deviceCode = authState?.optString("userCode", "").orEmpty()
                    val verificationUri = authState?.optString("verificationUri", "https://microsoft.com/devicelogin").orEmpty()

                    if (deviceCode.isNotBlank() && deviceCode != lastShownAuthCode) {
                        lastShownAuthCode = deviceCode
                        appendLog("AUTENTICAÇÃO MICROSOFT: abra $verificationUri e informe o código $deviceCode")
                        showMicrosoftLoginDialog(verificationUri, deviceCode)
                    }

                    status.text = when {
                        path == "/api/server-check" && code in 200..299 -> "● SERVIDOR: " + parsed.optString("message", "Verificação concluída")
                        path == "/api/disconnect" && code in 200..299 -> "● BOT OFF — desconectado pelo aplicativo"
                        code !in 200..299 -> "● API com erro (HTTP $code)"
                        (path == "/api/status" || path == "/api/connect") && connected -> "● BOT ON — conectado ao mundo"
                        (path == "/api/status" || path == "/api/connect") && deviceCode.isNotBlank() -> "● AUTENTICAÇÃO MICROSOFT — código $deviceCode"
                        (path == "/api/status" || path == "/api/connect") && serverError.isNotBlank() -> "● BOT OFF — $serverError"
                        path == "/api/status" || path == "/api/connect" -> "● BOT — $connectionState"
                        else -> "● Resposta recebida"
                    }

                    if (!quiet) appendLog("HTTP $code $path\n$response")
                    if (serverError.isNotBlank() && (!quiet || serverError != lastPolledError)) appendLog("ERRO: $serverError")
                    if (quiet && (connectionState != lastPolledState || serverError != lastPolledError)) {
                        val serverVersion = state.optString("serverVersion", "")
                        val engineId = state.optString("engine", "")
                        val pathfindingAvailable = state.optBoolean("pathfindingAvailable", false)
                        appendLog("Estado do bot: $connectionState" +
                            (if (serverVersion.isNotBlank()) " • Minecraft $serverVersion" else "") +
                            (if (engineId.isNotBlank()) " • Motor $engineId" else "") +
                            (if (connectionState == "connected") " • Seguir: " + if (pathfindingAvailable) "disponível" else "desativado" else "") +
                            (if (serverError.isNotBlank()) " — $serverError" else ""))
                    }
                    if (quiet) {
                        lastPolledState = connectionState
                        lastPolledError = serverError
                    }
                    if (path == "/api/connect" && code in 200..299) {
                        appendLog("Tentativa iniciada; acompanhando o estado real...")
                        startStatusPolling()
                    }
                    if (path == "/api/disconnect" && code in 200..299) stopStatusPolling()
                    if (quiet && state.has("connectionState") && connectionState in setOf("disconnected", "kicked", "timeout", "error")) {
                        stopStatusPolling()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "● FALHA DE CONEXÃO"
                    if (!quiet) appendLog("ERRO DE REDE: ${e.message ?: "sem conexão"}")
                }
            } finally {
                conn?.disconnect()
                runOnUiThread { busy = false }
            }
        }
    }
}
