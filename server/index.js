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
let bot = null
let connectionTimer = null
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
  pathfindingAvailable: false,
  health: null,
  food: null,
  position: null,
  lastError: null,
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

function clearConnectionTimer () {
  if (connectionTimer) clearTimeout(connectionTimer)
  connectionTimer = null
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

app.post('/api/connect', auth, (req, res) => {
  let options
  try {
    options = validateConnectionPayload(req.body, engines)
  } catch (error) {
    if (error instanceof ValidationError) return res.status(error.statusCode).json({ ok: false, error: error.message })
    return res.status(400).json({ ok: false, error: 'Configuração de conexão inválida.' })
  }

  disconnectCurrentBot('Substituído por uma nova conexão')
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
      username: options.username,
      version: options.version === 'auto' ? false : options.version,
      auth: 'offline',
      hideErrors: false,
      logErrors: false
    })
    bot = activeBot

    // Resolve the server version first. Pathfinder uses a separate physics stack
    // and is intentionally not loaded for modern protocols until compatibility is verified.
    activeBot.once('spawn', () => {
      if (bot !== activeBot) return
      clearConnectionTimer()
      const detectedVersion = String(activeBot.version || '')
      const modernProtocol = detectedVersion.startsWith('26.')
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
        serverVersion: detectedVersion || null,
        pathfindingAvailable: canPathfind,
        lastError: null
      })
    })

        activeBot.on('kicked', (reason) => {
      if (bot !== activeBot) return
      clearConnectionTimer()
      const detail = reasonText(reason)
      stopMovement(activeBot)
      updateBotInfo({
        connected: false,
        connectionState: 'kicked',
        pathfindingAvailable: false,
        health: null,
        food: null,
        position: null,
        lastError: 'O servidor recusou a entrada: ' + (detail || 'motivo não informado')
      })
    })

    activeBot.on('error', (error) => {
      if (bot !== activeBot) return
      const message = String(error && (error.message || error) || 'Erro desconhecido').slice(0, 500)
      updateBotInfo({
        connectionState: botInfo.connected ? 'connected' : 'error',
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
        position: null,
        pathfindingAvailable: false,
        lastError: botInfo.lastError || (detail && detail !== 'socketClosed' ? 'Conexão encerrada: ' + detail : null)
      })
    })

    connectionTimer = setTimeout(() => {
      if (bot !== activeBot || botInfo.connected) return
      updateBotInfo({
        connected: false,
        connectionState: 'timeout',
        lastError: 'Tempo limite de conexão excedido. Confira o IP, a porta, a versão e se o servidor está ligado.'
      })
      try { activeBot.quit('Tempo limite de conexão') } catch (_) {}
    }, CONNECT_TIMEOUT_MS)
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
