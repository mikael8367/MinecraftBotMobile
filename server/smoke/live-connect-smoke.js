'use strict'

const assert = require('node:assert/strict')

const baseUrl = process.env.MINECRAFTBOT_BASE_URL || 'http://127.0.0.1:10000'
const apiToken = process.env.API_TOKEN
const host = process.env.MC_SMOKE_HOST || '127.0.0.1'
const port = Number(process.env.MC_SMOKE_PORT || 25565)
const version = process.env.MC_SMOKE_VERSION || '26.3'
const engine = process.env.MC_SMOKE_ENGINE || 'mikael'
const connectTimeoutMs = Number(process.env.MC_SMOKE_WAIT_MS || 80000)

if (!apiToken) throw new Error('API_TOKEN is required for the live-connect smoke test.')

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

async function request (path, method = 'GET', body) {
  const response = await fetch(baseUrl + path, {
    method,
    headers: {
      authorization: 'Bearer ' + apiToken,
      ...(body ? { 'content-type': 'application/json' } : {})
    },
    ...(body ? { body: JSON.stringify(body) } : {})
  })
  const raw = await response.text()
  let json
  try { json = JSON.parse(raw) } catch (_) { json = { raw } }
  return { response, json }
}

async function readStatus () {
  const { response, json } = await request('/api/status')
  assert.equal(response.status, 200, 'status endpoint should return HTTP 200')
  assert.equal(json.ok, true, 'status API should report ok=true')
  return json.status
}

function distance (a, b) {
  if (!a || !b) return 0
  return Math.sqrt(
    ((a.x || 0) - (b.x || 0)) ** 2 +
    ((a.y || 0) - (b.y || 0)) ** 2 +
    ((a.z || 0) - (b.z || 0)) ** 2
  )
}

async function main () {
  console.log('[smoke] Target server: ' + host + ':' + port + ' / Java ' + version + ' / engine ' + engine)

  const connect = await request('/api/connect', 'POST', {
    host,
    port,
    username: 'MikaelSmoke',
    accountId: '',
    auth: 'offline',
    version,
    engine
  })
  assert.equal(connect.response.status, 202, 'connect endpoint should accept the connection attempt: ' + JSON.stringify(connect.json))
  assert.equal(connect.json.ok, true, 'connect endpoint should return ok=true')

  let status = null
  const deadline = Date.now() + connectTimeoutMs
  while (Date.now() < deadline) {
    status = await readStatus()
    if (status.connected && status.connectionState === 'connected') break
    if (['error', 'kicked', 'timeout'].includes(status.connectionState)) {
      throw new Error('Bot failed before spawn: ' + JSON.stringify(status))
    }
    await sleep(1000)
  }

  assert.ok(status && status.connected, 'bot must reach connected/spawn before timeout: ' + JSON.stringify(status))
  assert.equal(status.connectionState, 'connected', 'connection state should be connected')
  assert.ok(status.position && [status.position.x, status.position.y, status.position.z].every(Number.isFinite),
    'bot must report finite world coordinates after spawn: ' + JSON.stringify(status.position))
  assert.equal(status.serverVersion, version, 'server version should be detected as ' + version)
  console.log('[smoke] SPAWN PASS: ' + JSON.stringify({ username: status.username, version: status.serverVersion, position: status.position }))

  const before = status.position
  const action = await request('/api/action', 'POST', { action: 'forward' })
  assert.equal(action.response.status, 200, 'forward action should return HTTP 200: ' + JSON.stringify(action.json))
  assert.equal(action.json.ok, true, 'forward action should be accepted: ' + JSON.stringify(action.json))

  const movementDeadline = Date.now() + 7000
  let moved = false
  while (Date.now() < movementDeadline) {
    await sleep(500)
    status = await readStatus()
    if (!status.connected || status.connectionState !== 'connected') {
      throw new Error('Bot disconnected after movement command: ' + JSON.stringify(status))
    }
    if (distance(before, status.position) > 0.08) {
      moved = true
      break
    }
  }
  assert.ok(moved, 'bot stayed connected but did not move after forward; before=' +
    JSON.stringify(before) + ', after=' + JSON.stringify(status && status.position))
  console.log('[smoke] MOVEMENT PASS: ' + JSON.stringify({ before, after: status.position }))

  const disconnect = await request('/api/disconnect', 'POST', {})
  assert.equal(disconnect.response.status, 200, 'disconnect endpoint should return HTTP 200')
  assert.equal(disconnect.json.ok, true, 'disconnect endpoint should return ok=true')
  console.log('[smoke] DISCONNECT PASS')
}

main().catch(async (error) => {
  console.error('[smoke] FAILURE:', error && (error.stack || error))
  try {
    await request('/api/disconnect', 'POST', {})
  } catch (_) {}
  process.exitCode = 1
})
