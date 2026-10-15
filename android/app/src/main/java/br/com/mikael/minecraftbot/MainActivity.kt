package br.com.mikael.minecraftbot

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var username: EditText
    private lateinit var version: Spinner
    private lateinit var target: EditText
    private lateinit var command: EditText
    private lateinit var status: TextView
    private lateinit var tabButtons: List<TextView>
    private var busy = false
    private var selectedTab = 0

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
        root.addView(statusCard, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })

        val tabs = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; background=rounded(panel,14); setPadding(4,4,4,4) }
        val names = listOf("⌁  Conectar","✣  Controle","✦  IA")
        tabButtons = names.mapIndexed { i, name ->
            val t = text(name,12,muted,true).apply { gravity=Gravity.CENTER; setPadding(3,13,3,13); background=rounded(if(i==0) green else panel,11); setOnClickListener { showTab(i) } }
            tabs.addView(t,LinearLayout.LayoutParams(0,-2,1f)); t
        }
        root.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })
        pages = FrameLayout(this)
        root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
        buildConnectPage(); buildControlPage(); buildAiPage()
        showTab(0)
        loadMinecraftVersions()
    }

    private val pageViews = mutableListOf<View>()
    private fun page(title:String, subtitle:String): LinearLayout {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,18); setPadding(16,16,16,16) }
        box.addView(text(title,18,white,true)); box.addView(text(subtitle,12,muted,false).apply { setPadding(0,4,0,12) })
        pageViews.add(box); return box
    }
    private fun buildConnectPage() {
        val p=page("Conexão do servidor","Backend configurado automaticamente. Informe os dados do servidor Java.")
        host=field(p,"IP ou domínio do servidor","play.exemplo.com")
        port=field(p,"Porta","25565")
        username=field(p,"Nome do bot","MikaelBot")
        p.addView(text("VERSÃO DO MINECRAFT",10,muted,true).apply { setPadding(0,10,0,4) })
        version=Spinner(this)
        version.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item, mutableListOf("auto","Carregando versões..."))
        p.addView(version)
        button(p,"CONECTAR BOT",true) { request("POST","/api/connect",JSONObject().put("host",host.text.toString().trim()).put("port",port.text.toString().toIntOrNull()?:25565).put("username",username.text.toString().trim()).put("version",version.selectedItem.toString())) }
        button(p,"ATUALIZAR STATUS",false) { request("GET","/api/status",JSONObject()) }
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
                val all = mutableListOf("auto")
                all.addAll(releases)
                if (snapshots.isNotEmpty()) { all.add("── Snapshots ──"); all.addAll(snapshots) }
                if (betas.isNotEmpty()) { all.add("── Betas antigas ──"); all.addAll(betas) }
                if (alphas.isNotEmpty()) { all.add("── Alphas antigas ──"); all.addAll(alphas) }
                runOnUiThread {
                    version.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, all)
                    val saved = getSharedPreferences("minecraftbot", MODE_PRIVATE).getString("mc_version", "auto") ?: "auto"
                    val position = all.indexOf(saved)
                    if (position >= 0) version.setSelection(position)
                    version.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val selected = all[position]
                            if (!selected.startsWith("──")) getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putString("mc_version", selected).apply()
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val fallback = listOf("auto","1.21.8","1.21.7","1.21.6","1.21.5","1.21.4","1.21.3","1.21.2","1.21.1","1.21","1.20.6","1.20.4","1.20.1","1.19.4","1.18.2","1.16.5","1.12.2","1.8.9")
                    version.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fallback)
                }
            }
        }
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

    private fun request(method:String,path:String,body:JSONObject) {
        val base=BuildConfig.API_BASE_URL
        val secret=BuildConfig.API_TOKEN.trim()
        if(base.isBlank()||(!base.startsWith("https://")&&!base.startsWith("http://"))) { status.text="●  URL do servidor inválida"; return }
        if(secret.isBlank()) { status.text="●  Token não incluído no APK. Configure o segredo API_TOKEN no GitHub."; return }
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
                runOnUiThread { status.text=(if(code in 200..299) "●  Resposta recebida" else "●  Erro HTTP $code")+"\n"+response.take(240) }
            } catch(e:Exception) { runOnUiThread { status.text="●  Falha: ${e.message?: "sem conexão"}" } }
            finally { conn?.disconnect(); runOnUiThread { busy=false } }
        }
    }
}
