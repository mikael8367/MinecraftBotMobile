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
    private lateinit var token: EditText
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
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,16,18,22); setBackgroundColor(bg) }
        scroll.addView(root); setContentView(scroll)

        val brand = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(2,4,2,14) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.bot_hero); scaleType=ImageView.ScaleType.CENTER_CROP }
        brand.addView(logo, LinearLayout.LayoutParams(dp(62),dp(62)))
        val brandText = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,0,0,0) }
        brandText.addView(text("MINECRAFT BOT",20,green,true))
        brandText.addView(text("MOBILE CONTROL CENTER",10,muted,true))
        brand.addView(brandText); root.addView(brand)

        val hero = ImageView(this).apply { setImageResource(R.drawable.bot_hero); scaleType=ImageView.ScaleType.CENTER_CROP; background=rounded(panel,18); clipToOutline=true; contentDescription="Ilustração do bot voxel verde" }
        root.addView(hero, LinearLayout.LayoutParams(-1,dp(150)).apply { bottomMargin=12 })

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
        root.addView(pages,LinearLayout.LayoutParams(-1,-2))
        buildConnectPage(); buildControlPage(); buildAiPage()
        showTab(0)
    }

    private val pageViews = mutableListOf<View>()
    private fun page(title:String, subtitle:String): LinearLayout {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,18); setPadding(16,16,16,16) }
        box.addView(text(title,18,white,true)); box.addView(text(subtitle,12,muted,false).apply { setPadding(0,4,0,12) })
        pageViews.add(box); return box
    }
    private fun buildConnectPage() {
        val p=page("Conexão do servidor","Backend configurado automaticamente. Informe o token e os dados do servidor Java.")
        token=field(p,"Token da API",getSharedPreferences("minecraftbot", MODE_PRIVATE).getString("api_token", BuildConfig.API_TOKEN) ?: BuildConfig.API_TOKEN)
        host=field(p,"IP ou domínio do servidor","play.exemplo.com")
        port=field(p,"Porta","25565")
        username=field(p,"Nome do bot","MikaelBot")
        p.addView(text("VERSÃO DO MINECRAFT",10,muted,true).apply { setPadding(0,10,0,4) })
        version=Spinner(this)
        version.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("auto","1.8","1.8.8","1.8.9","1.9","1.9.2","1.9.4","1.10","1.10.2","1.11","1.11.2","1.12","1.12.1","1.12.2","1.13","1.13.1","1.13.2","1.14","1.14.1","1.14.2","1.14.3","1.14.4","1.15","1.15.1","1.15.2","1.16","1.16.1","1.16.2","1.16.3","1.16.4","1.16.5","1.17","1.17.1","1.18","1.18.1","1.18.2","1.19","1.19.1","1.19.2","1.19.3","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2","1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8"))
        p.addView(version)
        button(p,"CONECTAR BOT",true) { request("POST","/api/connect",JSONObject().put("host",host.text.toString().trim()).put("port",port.text.toString().toIntOrNull()?:25565).put("username",username.text.toString().trim()).put("version",version.selectedItem.toString())) }
        button(p,"ATUALIZAR STATUS",false) { request("GET","/api/status",JSONObject()) }
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
        val base=BuildConfig.API_BASE_URL; val secret=token.text.toString().trim().ifBlank { BuildConfig.API_TOKEN }
        if (secret.isNotBlank()) getSharedPreferences("minecraftbot", MODE_PRIVATE).edit().putString("api_token", secret).apply()
        if(base.isBlank()||secret.isBlank()) { status.text="●  Token da API ausente; configure API_TOKEN no GitHub Actions ou informe no app"; return }
        if(!base.startsWith("https://")&&!base.startsWith("http://")) { status.text="●  URL inválida"; return }
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
