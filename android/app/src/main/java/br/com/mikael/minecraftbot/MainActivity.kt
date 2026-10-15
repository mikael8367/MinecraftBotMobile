package br.com.mikael.minecraftbot

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
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
    private var availableVersions: List<String> = listOf("auto", "26.3", "26.1", "1.21.11", "1.21.8", "1.20.1", "1.12.2", "1.8.9")

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
        logs = text("Aguardando tentativa de conexão...",11,white,false).apply { setTextIsSelectable(true); setPadding(dp(8),dp(8),dp(8),dp(8)) }
        statusCard.addView(logs)
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

        val tabs = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; background=rounded(panel,14); setPadding(4,4,4,4) }
        val names = listOf("🤖  Meus Bots","✣  Controle","✦  IA")
        tabButtons = names.mapIndexed { i, name ->
            val t = text(name,12,muted,true).apply { gravity=Gravity.CENTER; setPadding(3,13,3,13); background=rounded(if(i==0) green else panel,11); setOnClickListener { showTab(i) } }
            tabs.addView(t,LinearLayout.LayoutParams(0,-2,1f)); t
        }
        root.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })
        pages = FrameLayout(this)
        root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
        buildBotsInfoPage(); buildControlPage(); buildAiPage()
        showTab(0)
        loadMinecraftVersions()
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

    private fun loadMinecraftVersions() {
        thread {
            try {
                val connection = (URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val versionsJson = JSONObject(json).getJSONArray("versions")
                val releases = mutableListOf<String>()
                val snapshots = mutableListOf<String>()
                val betas = mutableListOf<String>()
                val alphas = mutableListOf<String>()
                for (i in 0 until versionsJson.length()) {
                    val item = versionsJson.getJSONObject(i)
                    val id = item.optString("id")
                    when (item.optString("type")) {
                        "release" -> releases.add(id)
                        "snapshot" -> snapshots.add(id)
                        "old_beta" -> betas.add(id)
                        "old_alpha" -> alphas.add(id)
                    }
                }
                val all = mutableListOf("auto", "26.3")
                all.addAll(releases.filter { it != "26.3" })
                if (snapshots.isNotEmpty()) { all.add("── Snapshots ──"); all.addAll(snapshots) }
                if (betas.isNotEmpty()) { all.add("── Betas antigas ──"); all.addAll(betas) }
                if (alphas.isNotEmpty()) { all.add("── Alphas antigas ──"); all.addAll(alphas) }
                runOnUiThread {
                    availableVersions = all.filter { !it.startsWith("──") }

                }
            } catch (_: Exception) {
                runOnUiThread {
                    val fallback = listOf("auto","26.3","26.1","1.21.11","1.21.8","1.21.7","1.21.6","1.21.5","1.21.4","1.21.3","1.21.2","1.21.1","1.21","1.20.6","1.20.4","1.20.1","1.19.4","1.18.2","1.16.5","1.12.2","1.8.9")
                    availableVersions = fallback

                }
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
            card.addView(text(item.optString("host") + ":" + item.optString("port") + "  •  Minecraft " + item.optString("version","auto") + "\\nToque no nome para abrir as configurações",11,muted,false).apply { setPadding(0,4,0,8) })
            card.setOnClickListener { showBotSettingsDialog(i, item) }
            val actions = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            val use = Button(this).apply { text="LIGAR"; isAllCaps=false; textSize=10f; setTextColor(bg); background=rounded(green,9) }
            use.setOnClickListener {
                val wanted = item.optString("version","auto")
                appendLog("Bot selecionado: " + item.optString("name"))
                request("POST","/api/connect",JSONObject().put("host",item.optString("host")).put("port",item.optInt("port",25565)).put("username",item.optString("name")).put("version",wanted).put("engine",item.optString("engine","mikael")))
            }
            actions.addView(use,LinearLayout.LayoutParams(0,dp(42),1f).apply { leftMargin=dp(5) })
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
        val form = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        val botName = EditText(this).apply { setSingleLine(true); hint="Nome do bot"; setText(item.optString("name")) }
        val serverIp = EditText(this).apply { setSingleLine(true); hint="IP ou domínio"; setText(item.optString("host")) }
        val serverPort = EditText(this).apply { setSingleLine(true); hint="Porta"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText(item.optString("port","25565")) }
        val mcVersion = Spinner(this)
        val versions = (listOf(item.optString("version","auto")) + availableVersions).distinct()
        mcVersion.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,versions)
        val engine = Spinner(this)
        engine.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Mineflayer Mikael (experimental)","Mineflayer normal"))
        engine.setSelection(if (item.optString("engine","mikael") == "official") 1 else 0)
        form.addView(text("NOME DO BOT",10,muted,true)); form.addView(botName)
        form.addView(text("IP / DOMÍNIO",10,muted,true)); form.addView(serverIp)
        form.addView(text("PORTA",10,muted,true)); form.addView(serverPort)
        form.addView(text("VERSÃO DO MINECRAFT",10,muted,true)); form.addView(mcVersion)
        form.addView(text("MOTOR DE CONEXÃO",10,muted,true)); form.addView(engine)
        form.addView(text("Mineflayer Mikael é experimental; suporte real depende de testes com o servidor.",11,muted,false))
        val dialog = AlertDialog.Builder(this).setTitle("Configurações do bot").setView(form)
            .setNegativeButton("Cancelar",null).setPositiveButton("SALVAR ALTERAÇÕES",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = botName.text.toString().trim()
                val ip = serverIp.text.toString().trim()
                val p = serverPort.text.toString().toIntOrNull()
                val v = mcVersion.selectedItem?.toString() ?: "auto"
                val engineId = if (engine.selectedItemPosition == 1) "official" else "mikael"
                if (name.isBlank() || ip.isBlank() || p == null || p !in 1..65535) {
                    Toast.makeText(this,"Confira nome, IP e porta.",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val items = savedBots()
                if (index !in 0 until items.length()) return@setOnClickListener
                items.put(index, JSONObject().put("name",name).put("host",ip).put("port",p).put("version",v).put("engine",engineId))
                saveBots(items)
                Toast.makeText(this,"Configurações salvas.",Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showCreateBotDialog() {
        val form = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        val botName = EditText(this).apply { hint="Nome do bot (ex.: MikaelBot)"; setSingleLine(true) }
        val serverIp = EditText(this).apply { hint="IP ou domínio (ex.: servidor.aternos.me)"; setSingleLine(true) }
        val serverPort = EditText(this).apply { hint="Porta (ex.: 25565)"; setSingleLine(true); inputType=android.text.InputType.TYPE_CLASS_NUMBER }
        val mcVersion = Spinner(this)
        mcVersion.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,availableVersions)
        val engine = Spinner(this)
        engine.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Mineflayer Mikael (experimental)","Mineflayer normal"))
        form.addView(text("NOME DO BOT",10,muted,true)); form.addView(botName)
        form.addView(text("IP / DOMÍNIO",10,muted,true)); form.addView(serverIp)
        form.addView(text("PORTA",10,muted,true)); form.addView(serverPort)
        form.addView(text("VERSÃO DO MINECRAFT",10,muted,true)); form.addView(mcVersion)
        form.addView(text("MOTOR DE CONEXÃO",10,muted,true)); form.addView(engine)
        form.addView(text("Mineflayer Mikael é experimental; versões não testadas podem falhar.",11,muted,false))
        val dialog = AlertDialog.Builder(this).setTitle("Criar bot").setView(form)
            .setNegativeButton("Cancelar",null).setPositiveButton("SALVAR BOT",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = botName.text.toString().trim()
                val ip = serverIp.text.toString().trim()
                val p = serverPort.text.toString().toIntOrNull()
                val v = mcVersion.selectedItem?.toString() ?: "auto"
                val engineId = if (engine.selectedItemPosition == 1) "official" else "mikael"
                if (name.isBlank() || ip.isBlank() || p == null || p !in 1..65535 || v.startsWith("──")) {
                    Toast.makeText(this,"Preencha nome, IP e uma porta válida.",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val items = savedBots()
                items.put(JSONObject().put("name",name).put("host",ip).put("port",p).put("version",v).put("engine",engineId))
                saveBots(items)
                Toast.makeText(this,"Bot salvo!",Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
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

    private fun appendLog(message:String) {
        if (!::logs.isInitialized) return
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logs.text = (logs.text.toString() + "\n[" + stamp + "] " + message).takeLast(12000)
    }

    private fun request(method:String,path:String,body:JSONObject) {
        val base=BuildConfig.API_BASE_URL
        val secret=getSharedPreferences("minecraftbot", MODE_PRIVATE).getString("api_access", "")?.trim().orEmpty()
        if(base.isBlank()||(!base.startsWith("https://")&&!base.startsWith("http://"))) { status.text="●  URL do servidor inválida"; return }
        if(secret.isBlank()) {
            val input = EditText(this).apply { hint = "Senha de acesso da API"; setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
            AlertDialog.Builder(this).setTitle("Acesso ao bot").setMessage("Digite o API_TOKEN configurado no backend. Ele não fica embutido no APK.").setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Salvar e conectar") { _, _ ->
                val entered = input.text.toString().trim()
                if (entered.isBlank()) { status.text = "● Senha não informada"; return@setPositiveButton }
                getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putString("api_access", entered).apply()
                request(method, path, body)
            }.show()
            return
        }
        busy=true; status.text="●  Enviando pedido..."
        thread {
            var conn:HttpURLConnection?=null
            try {
                conn=(URL(base+path).openConnection() as HttpURLConnection).apply {
                    requestMethod=method; connectTimeout=8000; readTimeout=10000
                    setRequestProperty("Authorization","Bearer $secret"); setRequestProperty("Content-Type","application/json")
                    if(method!="GET") { doOutput=true; outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
                }
                val code=conn.responseCode
                val stream=if(code in 200..299) conn.inputStream else conn.errorStream
                val response=stream.bufferedReader().use { it.readText() }
                runOnUiThread {
                    if (code == 401 || code == 403) {
                        getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().remove("api_access").apply()
                        status.text = "● Token inválido — informe o token novo"
                        appendLog("A API recusou o token (HTTP $code). O token salvo foi apagado; toque em uma ação para informar o novo.")
                        busy = false
                        conn?.disconnect()
                        return@runOnUiThread
                    }
                    val parsed = try { JSONObject(response) } catch (_: Exception) { JSONObject() }
                    val state = parsed.optJSONObject("status")
                    val connected = parsed.optBoolean("connected", state?.optBoolean("connected", false) ?: false)
                    val serverError = parsed.optString("error").ifBlank { state?.optString("lastError", "") ?: "" }
                    status.text = when {
                        code !in 200..299 -> "● Servidor/API com erro (HTTP $code)"
                        (path == "/api/status" || path == "/api/connect") && connected -> "● BOT ON — conectado ao servidor"
                        (path == "/api/status" || path == "/api/connect") && serverError.isNotBlank() -> "● BOT OFF — $serverError"
                        path == "/api/status" || path == "/api/connect" -> "● Tentando conectar / ainda offline"
                        else -> "● Resposta recebida"
                    }
                    appendLog("HTTP $code $path\n$response")
                    if (serverError.isNotBlank()) appendLog("ERRO: $serverError")
                    if (path == "/api/connect" && code in 200..299) appendLog("Tentativa iniciada. Verifique o status em seguida.")
                }
            } catch(e:Exception) { runOnUiThread {
                status.text="● FALHA DE CONEXÃO"
                appendLog("ERRO DE REDE: ${e.message ?: "sem conexão"}")
            } }
            finally { conn?.disconnect(); runOnUiThread { busy=false } }
        }
    }
}
