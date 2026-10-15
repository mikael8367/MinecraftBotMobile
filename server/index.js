'use strict'

const express = require('express')
const crypto = require('node:crypto')
const { pathfinder, goals } = require('mineflayer-pathfinder')
const { engines, capabilities } = require('./engine-registry')
const { ValidationError, validateConnectionPayload } = require('./connection-validation')
const { parseCommand } = require('./command-parser')
const { applyMovementAction } = require('./movement-actions')

const app = express()
app.use(express.json({ limit: '32kb' }))

const rawPort = Number(process.env.PORT || 10000)
const PORT = Number.isInteger(rawPort) && rawPort > 0 && rawPort <= 65535 ? rawPort : 10000
const API_TOKEN = process.env.API_TOKEN
const rawConnectTimeout = Number(process.env.CONNECT_TIMEOUT_MS || 30000)
const CONNECT_TIMEOUT_MS = Number.isFinite(rawConnectTimeout)
  ? Math.max(10000, Math.min(rawConnectTimeout, 90000))
  : 30000
// Device-code login requires time for the user to switch to Microsoft and approve.
const rawMicrosoftConnectTimeout = Number(process.env.MICROSOFT_CONNECT_TIMEOUT_MS || 600000)
const MICROSOFT_CONNECT_TIMEOUT_MS = Number.isFinite(rawMicrosoftConnectTimeout)
  ? Math.max(60000, Math.min(rawMicrosoftConnectTimeout, 900000))
  : 600000
let bot = null
let connectionTimer = null
let reconnectTimer = null
let autoReconnectEnabled = false
let lastConnectionPayload = null
const controlTimers = new Map()

let botInfo = {
  connected: false,
  connectionState: 'disconnected',
  username: null,
  host: null,
  port: null,
  version: null,
  serverVersion: null,
  engine: null,
  auth: null,
  authCode: null,
  pathfindingAvailable: false,
  health: null,
  food: null,
  position: null,
  lastError: null,
  autoReconnect: false,
  updatedAt: new Date().toISOString()
}

function updateBotInfo (patch) {
  botInfo = { ...botInfo, ...patch, updatedAt: new Date().toISOString() }
}

function auth (req, res, next) {
  if (!API_TOKEN) {
    return res.status(503).json({ ok: false, error: 'Backend sem configuração: defina API_TOKEN nas Environment Variables do Render.' })
  }
  const header = req.get('authorization') || ''
  const supplied = Buffer.from(header.startsWith('Bearer ') ? header.slice(7) : '', 'utf8')
  const expected = Buffer.from(API_TOKEN, 'utf8')
  if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) {
    return res.status(401).json({ ok: false, error: 'Token da API inválido.' })
  }
  next()
}

function reasonText (reason) {
  if (reason == null) return ''
  if (typeof reason === 'string') return reason.slice(0, 500)
  try { return JSON.stringify(reason).slice(0, 500) } catch (_) { return String(reason).slice(0, 500) }
}

function microsoftCodeInfo (data) {
  const userCode = String((data && (data.user_code || data.userCode)) || '').trim().slice(0, 32)
  if (!userCode) return null

  const fallbackUri = 'https://microsoft.com/devicelogin'
  let verificationUri = fallbackUri
  const suppliedUri = String((data && (data.verification_uri || data.verificationUri)) || fallbackUri)
  try {
    const parsed = new URL(suppliedUri)
    const hostname = parsed.hostname.toLowerCase()
    const microsoftHost = hostname === 'microsoft.com' ||
      hostname.endsWith('.microsoft.com') ||
      hostname === 'microsoftonline.com' ||
      hostname.endsWith('.microsoftonline.com')
    if (parsed.protocol === 'https:' && microsoftHost) verificationUri = parsed.toString()
  } catch (_) {}

  const expiresIn = Number(data && (data.expires_in || data.expiresIn))
  return {
    userCode,
    verificationUri,
    expiresIn: Number.isFinite(expiresIn) ? Math.max(0, Math.min(expiresIn, 3600)) : null,
    createdAt: new Date().toISOString()
  }
}

function clearConnectionTimer () {
  if (connectionTimer) clearTimeout(connectionTimer)
  connectionTimer = null
}

function clearReconnectTimer () {
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = null
}

function scheduleReconnect (reason) {
  if (!autoReconnectEnabled || !lastConnectionPayload || reconnectTimer) return
  console.log('[bot:reconnect] Tentando reconectar em 5 segundos. Motivo:', reason || 'conexão encerrada')
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null
    if (!autoReconnectEnabled || !lastConnectionPayload || botInfo.connectionState === 'kicked') return
    try {
      const response = await fetch('http://127.0.0.1:' + PORT + '/api/connect', {
        method: 'POST',
        headers: { 'content-type': 'application/json', authorization: 'Bearer ' + API_TOKEN },
        body: JSON.stringify(lastConnectionPayload)
      })
      if (!response.ok) console.warn('[bot:reconnect] A API recusou a tentativa (HTTP ' + response.status + ').')
    } catch (error) {
      console.warn('[bot:reconnect] Falha ao iniciar tentativa:', String(error && (error.message || error)).slice(0, 250))
      scheduleReconnect('falha ao iniciar tentativa')
    }
  }, 5000)
  if (typeof reconnectTimer.unref === 'function') reconnectTimer.unref()
}

function clearControlTimers () {
  for (const timer of controlTimers.values()) clearTimeout(timer)
  controlTimers.clear()
}

function stopMovement (activeBot) {
  clearControlTimers()
  if (!activeBot) return
  try { activeBot.pathfinder?.setGoal(null) } catch (_) {}
  for (const control of ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak']) {
    try { activeBot.setControlState(control, false) } catch (_) {}
  }
}

function disconnectCurrentBot (reason = 'Desconectado pelo aplicativo') {
  const oldBot = bot
  bot = null
  clearConnectionTimer()
  stopMovement(oldBot)
  if (oldBot) {
    try { oldBot.quit(reason) } catch (_) {
      try { oldBot.end(reason) } catch (_) {}
    }
  }
  updateBotInfo({
    connected: false,
    connectionState: 'disconnected',
    serverVersion: null,
    authCode: null,
    pathfindingAvailable: false,
    health: null,
    food: null,
    position: null,
    lastError: null
  })
}

function getStatus () {
  let health = null
  let food = null
  let position = null
  if (bot && botInfo.connected && bot.entity) {
    health = Number.isFinite(bot.health) ? bot.health : null
    food = Number.isFinite(bot.food) ? bot.food : null
    const p = bot.entity.position
    if (p && Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z)) {
      position = {
        x: Math.round(p.x * 100) / 100,
        y: Math.round(p.y * 100) / 100,
        z: Math.round(p.z * 100) / 100
      }
    }
  }
  return { ...botInfo, health, food, position }
}

function requireConnectedBot () {
  if (!bot || !botInfo.connected || !bot.entity) {
    throw new ValidationError('O bot ainda não está conectado ao mundo.', 409)
  }
  return bot
}

function pressControl (activeBot, action, durationMs) {
  const opposite = ({ forward: 'back', back: 'forward', left: 'right', right: 'left' })[action]
  if (opposite) {
    const oppositeTimer = controlTimers.get(opposite)
    if (oppositeTimer) clearTimeout(oppositeTimer)
    controlTimers.delete(opposite)
    try { activeBot.setControlState(opposite, false) } catch (_) {}
  }

  const previousTimer = controlTimers.get(action)
  if (previousTimer) clearTimeout(previousTimer)
  activeBot.setControlState(action, true)
  const timer = setTimeout(() => {
    controlTimers.delete(action)
    if (bot === activeBot) {
      try { activeBot.setControlState(action, false) } catch (_) {}
    }
  }, durationMs)
  if (typeof timer.unref === 'function') timer.unref()
  controlTimers.set(action, timer)
}
function executeAction (actionValue, payload = {}) {
  const activeBot = requireConnectedBot()
  const action = String(actionValue || '').trim().toLowerCase()
  const movementResult = applyMovementAction(action, (control, duration) => {
    // Manual movement takes priority over a pathfinder goal set by "follow".
    try {
      if (activeBot.pathfinder && activeBot.pathfinder.goal) activeBot.pathfinder.setGoal(null)
    } catch (_) {}
    pressControl(activeBot, control, duration)
  })
  if (movementResult) return movementResult

  if (action === 'stop') {
    stopMovement(activeBot)
    return { ok: true, message: 'Movimento e caminho interrompidos.' }
  }

  if (action === 'attack') {
    const position = activeBot.entity.position
    const target = activeBot.nearestEntity((entity) =>
      entity &&
      entity.type === 'player' &&
      entity.username !== activeBot.username &&
      entity.position &&
      entity.position.distanceTo(position) < 4
    )
    if (!target) throw new ValidationError('Nenhum jogador visível por perto para atacar.', 404)
    activeBot.attack(target)
    return { ok: true, message: 'Ataque enviado.' }
  }

  if (action === 'follow') {
    if (!botInfo.pathfindingAvailable || !activeBot.pathfinder) {
      throw new ValidationError('Seguir jogador está indisponível neste modo experimental: o pathfinder não foi validado para esta versão.', 501)
    }
    const name = String(payload.target || '').trim()
    if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) {
      throw new ValidationError('Informe o nome exato do jogador (1 a 16 caracteres).')
    }
    const player = activeBot.players && activeBot.players[name] && activeBot.players[name].entity
    if (!player || !player.position) throw new ValidationError('Jogador não encontrado ou não está visível.', 404)
    activeBot.pathfinder.setGoal(new goals.GoalFollow(player, 2), true)
    return { ok: true, message: 'Seguindo ' + name }
  }

  throw new ValidationError('Ação desconhecida.')
}

app.get('/', (_req, res) => res.json({ service: 'MinecraftBotMobile backend', ok: true }))
app.get('/health', (_req, res) => res.json({ ok: true }))
app.get('/api/capabilities', (_req, res) => res.json({ ok: true, ...capabilities() }))
app.get('/api/status', auth, (_req, res) => res.json({ ok: true, status: getStatus() }))

app.post('/api/server-check', auth, async (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {}
  const host = String(body.host ?? '').trim()
  const rawPort = body.port === undefined || body.port === null || body.port === '' ? '25565' : String(body.port)
  if (!host || host.length > 253 || /[\s/?#\\\u0000-\u001f]/.test(host) || host.includes('://')) {
    return res.status(400).json({ ok: false, result: 'invalid', message: 'Informe um IP ou domínio válido, sem http:// ou https://.' })
  }
  if (!/^\d{1,5}$/.test(rawPort) || Number(rawPort) < 1 || Number(rawPort) > 65535) {
    return res.status(400).json({ ok: false, result: 'invalid', message: 'A porta deve estar entre 1 e 65535.' })
  }
  const port = Number(rawPort)
  const dns = require('node:dns').promises
  const net = require('node:net')
  let address
  try {
    const resolved = await dns.lookup(host)
    address = resolved.address
  } catch (_) {
    return res.status(200).json({
      ok: true, result: 'not_found', domainResolves: false, portOpen: false,
      host, port, message: 'O domínio não foi encontrado no DNS. Confira o endereço.'
    })
  }

  const probe = await new Promise((resolve) => {
    const socket = new net.Socket()
    let finished = false
    const finish = (result) => {
      if (finished) return
      finished = true
      socket.destroy()
      resolve(result)
    }
    socket.setTimeout(5000)
    socket.once('connect', () => finish({ open: true }))
    socket.once('timeout', () => finish({ open: false, reason: 'timeout' }))
    socket.once('error', (error) => finish({ open: false, reason: error.code || 'error' }))
    socket.connect(port, address)
  })
  if (probe.open) {
    return res.json({
      ok: true, result: 'online', domainResolves: true, portOpen: true,
      host, port, address, message: 'O endereço existe no DNS e a porta está aceitando conexões. Isso indica que o servidor pode estar ligado.'
    })
  }
  const timedOut = probe.reason === 'timeout'
  return res.json({
    ok: true, result: timedOut ? 'unknown' : 'offline', domainResolves: true, portOpen: false,
    host, port, address, reason: probe.reason,
    message: timedOut
      ? 'O domínio existe, mas a verificação expirou. O servidor pode estar desligado ou bloqueando conexões.'
      : 'O domínio existe, mas a porta não está aceitando conexões. O servidor pode estar desligado, ou o IP/porta podem estar incorretos.'
  })
})


app.get('/api/settings/auto-reconnect', auth, (_req, res) => res.json({ ok: true, autoReconnect: autoReconnectEnabled }))

app.post('/api/settings/auto-reconnect', auth, (req, res) => {
  autoReconnectEnabled = Boolean(req.body && req.body.enabled)
  updateBotInfo({ autoReconnect: autoReconnectEnabled })
  if (!autoReconnectEnabled) clearReconnectTimer()
  return res.json({ ok: true, autoReconnect: autoReconnectEnabled })
})

app.post('/api/connect', auth, (req, res) => {
  clearReconnectTimer()
  let options
  try {
    options = validateConnectionPayload(req.body, engines)
  } catch (error) {
    if (error instanceof ValidationError) return res.status(error.statusCode).json({ ok: false, error: error.message })
    return res.status(400).json({ ok: false, error: 'Configuração de conexão inválida.' })
  }

  disconnectCurrentBot('Substituído por uma nova conexão')
  lastConnectionPayload = JSON.parse(JSON.stringify(req.body))
  const engine = engines[options.engine]
  updateBotInfo({
    connected: false,
    connectionState: 'connecting',
    username: options.username,
    host: options.host,
    port: options.port,
    version: options.version,
    serverVersion: null,
    engine: options.engine,
    auth: options.auth,
    authCode: null,
    pathfindingAvailable: false,
    health: null,
    food: null,
    position: null,
    lastError: null
  })

  let activeBot = null
  try {
    activeBot = engine.createBot({
      host: options.host,
      port: options.port,
      username: options.auth === 'microsoft' ? options.accountId : options.username,
      version: options.version === 'auto' ? false : options.version,
      auth: options.auth,
      onMsaCode: options.auth === 'microsoft' ? (data) => {
        if (bot && bot !== activeBot) return
        const authCode = microsoftCodeInfo(data)
        if (!authCode) {
          console.warn('[auth:microsoft] device-code callback did not include a user code')
          return
        }
        updateBotInfo({
          connectionState: 'authenticating',
          authCode,
          lastError: null
        })
        console.log('[auth:microsoft] Device authorization required; code is available through authenticated /api/status.')
      } : undefined,
      hideErrors: false,
      logErrors: false
    })
    bot = activeBot

    // Resolve the server version first. Pathfinder uses a separate physics stack
    // and is intentionally not loaded for modern protocols until compatibility is verified.
    activeBot.once('spawn', () => {
      // A late spawn must never overwrite a kick, timeout, or fatal connection
      // error already reported for this same bot instance.
      if (bot !== activeBot || ['kicked', 'timeout', 'disconnected', 'error'].includes(botInfo.connectionState)) return
      clearConnectionTimer()
      const detectedVersion = String(activeBot.version || '')
      const negotiatedProtocol = Number(activeBot.protocolVersion ?? (activeBot._client && activeBot._client.protocolVersion))
      // 26.1 starts at protocol 775. Keep pathfinder disabled for this and all
      // later protocol IDs until its independent physics support is verified.
      const modernProtocol = detectedVersion.startsWith('26.') ||
        (Number.isInteger(negotiatedProtocol) && negotiatedProtocol >= 775)
      let canPathfind = false
      if (!modernProtocol) {
        try {
          if (!activeBot.pathfinder) activeBot.loadPlugin(pathfinder)
          canPathfind = Boolean(activeBot.pathfinder)
        } catch (error) {
          console.warn('[pathfinder:disabled]', String(error && (error.message || error)).slice(0, 300))
        }
      }
      updateBotInfo({
        connected: true,
        connectionState: 'connected',
        username: activeBot.username || options.username,
        serverVersion: detectedVersion || null,
        authCode: null,
        pathfindingAvailable: canPathfind,
        lastError: null
      })
    })

        // Renasce automaticamente após morrer, sem reconectar nem alterar o controle manual.
    activeBot.on('death', () => {
      if (bot !== activeBot || !botInfo.connected) return
      console.log('[bot:death] Morte detectada; tentando renascer automaticamente.')
      const respawnTimer = setTimeout(() => {
        if (bot !== activeBot || !botInfo.connected) return
        try {
          activeBot.respawn()
          console.log('[bot:respawn] Pedido de renascimento enviado.')
        } catch (error) {
          console.warn('[bot:respawn] Não foi possível renascer:', String(error && (error.message || error)).slice(0, 300))
        }
      }, 1000)
      if (typeof respawnTimer.unref === 'function') respawnTimer.unref()
    })

    activeBot.on('kicked', (reason) => {
      if (bot !== activeBot) return
      clearConnectionTimer()
      const detail = reasonText(reason)
      stopMovement(activeBot)
      updateBotInfo({
        connected: false,
        connectionState: 'kicked',
        authCode: null,
        pathfindingAvailable: false,
        health: null,
        food: null,
        position: null,
        lastError: 'O servidor recusou a entrada: ' + (detail || 'motivo não informado')
      })
    })

    activeBot.on('error', (error) => {
      if (bot !== activeBot) return
      // A socket error can arrive after kicked/end/timeout. Preserve the first
      // terminal state instead of making the UI report a different failure.
      if (['kicked', 'timeout', 'disconnected'].includes(botInfo.connectionState)) return

      const message = String(error && (error.message || error) || 'Erro desconhecido').slice(0, 500)
      updateBotInfo({
        connectionState: botInfo.connected ? 'connected' : 'error',
        authCode: botInfo.connected ? botInfo.authCode : null,
        lastError: message
      })
      console.error('[bot:error]', JSON.stringify({
        engine: options.engine,
        version: options.version,
        host: options.host,
        message
      }))
    })

    activeBot.on('end', (reason) => {
      // A disconnected old bot can emit "end" after a new bot has been assigned.
      // Never let that stale event clear the new bot's timeout/control timers.
      if (bot !== activeBot) return
      clearConnectionTimer()
      clearControlTimers()
      const endedState = botInfo.connectionState
      const detail = reasonText(reason)
      let nextState = endedState
      if (!['timeout', 'kicked'].includes(endedState)) {
        nextState = botInfo.lastError ? 'error' : 'disconnected'
      }
      updateBotInfo({
        connected: false,
        connectionState: nextState,
        authCode: null,
        position: null,
        pathfindingAvailable: false,
        lastError: botInfo.lastError || (detail && detail !== 'socketClosed' ? 'Conexão encerrada: ' + detail : null)
      })
      if (nextState !== 'kicked' && nextState !== 'disconnected') scheduleReconnect(detail || nextState)
    })

    connectionTimer = setTimeout(() => {
      if (bot !== activeBot || botInfo.connected) return
      updateBotInfo({
        connected: false,
        connectionState: 'timeout',
        authCode: null,
        lastError: 'Tempo limite de conexão excedido. Confira o IP, a porta, a versão e, no modo Microsoft, conclua a autorização antes do prazo.'
      })
      try { activeBot.quit('Tempo limite de conexão') } catch (_) {}
    }, options.auth === 'microsoft' ? MICROSOFT_CONNECT_TIMEOUT_MS : CONNECT_TIMEOUT_MS)
    if (typeof connectionTimer.unref === 'function') connectionTimer.unref()

    return res.status(202).json({
      ok: true,
      message: 'Tentativa iniciada. O estado real será atualizado em /api/status.',
      status: getStatus()
    })
  } catch (error) {
    clearConnectionTimer()
    if (bot === activeBot) bot = null
    if (activeBot) {
      try { activeBot.end('Falha ao iniciar a conexão') } catch (_) {}
    }
    const message = String(error && (error.message || error) || 'Falha ao iniciar conexão').slice(0, 500)
    updateBotInfo({
      connected: false,
      connectionState: 'error',
      pathfindingAvailable: false,
      lastError: message
    })
    console.error('[connect:error]', JSON.stringify({ engine: options.engine, version: options.version, message }))
    return res.status(500).json({ ok: false, error: message, status: getStatus() })
  }
})

app.post('/api/action', auth, (req, res) => {
  try {
    const body = req.body && typeof req.body === 'object' ? req.body : {}
    return res.json(executeAction(body.action, body))
  } catch (error) {
    const statusCode = error instanceof ValidationError ? error.statusCode : 500
    const message = String(error && (error.message || error) || 'Falha ao executar ação').slice(0, 500)
    if (statusCode >= 500) console.error('[action:error]', message)
    return res.status(statusCode).json({ ok: false, error: message })
  }
})

app.post('/api/chat', auth, (req, res) => {
  const body = req.body && typeof req.body === 'object' ? req.body : {}
  const action = parseCommand(body.message)
  if (!action) {
    return res.status(400).json({
      ok: false,
      error: 'Ainda não entendi esse comando. Tente: andar, pular, correr, esquerda, direita, atacar ou parar.'
    })
  }

  try {
    const result = executeAction(action, body)
    return res.json({ ...result, interpretedAs: action })
  } catch (error) {
    const statusCode = error instanceof ValidationError ? error.statusCode : 500
    const errorMessage = String(error && (error.message || error) || 'Falha ao executar comando').slice(0, 500)
    if (statusCode >= 500) console.error('[chat:error]', errorMessage)
    return res.status(statusCode).json({ ok: false, error: errorMessage, interpretedAs: action })
  }
})

app.post('/api/disconnect', auth, (_req, res) => {
  autoReconnectEnabled = false
  updateBotInfo({ autoReconnect: false })
  clearReconnectTimer()
  disconnectCurrentBot('Desconectado pelo aplicativo')
  return res.json({ ok: true, message: 'Bot desconectado.', status: getStatus() })
})

app.use((error, _req, res, _next) => {
  const statusCode = error && error.status === 413 ? 413 : 400
  return res.status(statusCode).json({
    ok: false,
    error: statusCode === 413 ? 'Pedido grande demais (máximo 32 KB).' : 'JSON inválido.'
  })
})

app.listen(PORT, '0.0.0.0', () => {
  console.log('MinecraftBotMobile backend ouvindo na porta ' + PORT)
  console.log('Motores disponíveis: ' + Object.keys(engines).join(', '))
})
