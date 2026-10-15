'use strict'

class ValidationError extends Error {
  constructor (message, statusCode = 400) {
    super(message)
    this.name = 'ValidationError'
    this.statusCode = statusCode
  }
}

function validateConnectionPayload (body, engines) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new ValidationError('Envie as configurações do bot em um objeto JSON.')
  }

  const host = String(body.host ?? '').trim()
  if (!host || host.length > 253 || /[\s/?#\\\u0000-\u001f]/.test(host) || host.includes('://')) {
    throw new ValidationError('Informe somente o IP ou domínio do servidor, sem espaços nem URL.')
  }

  const rawPort = body.port === undefined || body.port === null || body.port === '' ? '25565' : String(body.port)
  if (!/^\d{1,5}$/.test(rawPort)) throw new ValidationError('A porta deve ser um número entre 1 e 65535.')
  const port = Number(rawPort)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ValidationError('A porta deve ser um número entre 1 e 65535.')
  }

  const username = String(body.username ?? 'MikaelBot').trim()
  if (!/^[A-Za-z0-9_]{1,16}$/.test(username)) {
    throw new ValidationError('O nome/identificador offline deve ter de 1 a 16 caracteres: letras, números ou _.')
  }

  const auth = String(body.auth ?? 'offline').trim().toLowerCase()
  if (!['offline', 'microsoft'].includes(auth)) {
    throw new ValidationError('Autenticação inválida. Escolha Offline ou Microsoft.')
  }

  let accountId = null
  if (auth === 'microsoft') {
    accountId = String(body.accountId ?? '').trim()
    if (!/^[A-Za-z0-9][A-Za-z0-9._@+-]{0,127}$/.test(accountId)) {
      throw new ValidationError('Informe um identificador da conta Microsoft (e-mail ou apelido, sem espaços).')
    }
  }

  const requestedEngine = String(body.engine ?? 'mikael').trim().toLowerCase()
  const engine = engines[requestedEngine]
  if (!engine) throw new ValidationError('Motor inválido. Escolha Mineflayer Mikael ou Mineflayer oficial.')

  const version = String(body.version ?? 'auto').trim() || 'auto'
  if (version !== 'auto' && !engine.testedVersions.includes(version)) {
    throw new ValidationError(
      'A versão ' + version + ' não consta na lista testedVersions do motor ' + engine.label +
      '. Consulte /api/capabilities ou escolha Auto.'
    )
  }

  return {
    host,
    port,
    username,
    accountId,
    auth,
    version,
    engine: requestedEngine
  }
}

module.exports = { ValidationError, validateConnectionPayload }
