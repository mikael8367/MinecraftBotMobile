'use strict'

// Projeto-owned adapter around the MIT-licensed @wp2508/mineflayer fork.
// Protocol implementation lives in that upstream fork; this file adds a stable
// project API and strict version validation instead of pretending to reimplement
// Minecraft's protocol from scratch.
const fork = require('mineflayer-mikael')
const versionInfo = require('mineflayer-mikael/lib/version')
const { installMikaelCompatibility } = require('./mikael-compat')

const testedVersions = Object.freeze([...(versionInfo.testedVersions || [])])

function createBot (options = {}) {
  const requested = options.version
  if (requested !== undefined && requested !== false && requested !== 'auto' && !testedVersions.includes(String(requested))) {
    throw new Error(
      'Versão ' + String(requested) + ' não consta em testedVersions do motor Mikael. ' +
      'Escolha Auto ou uma versão listada em /api/capabilities.'
    )
  }

  // Keep the fork's parser and protocol stack; normalize diagnostics for this app.
  const bot = fork.createBot({
    ...options,
    version: requested === 'auto' ? false : requested,
    hideErrors: false,
    logErrors: false
  })

  // Install compatibility adapters at client creation; each shim checks the
  // negotiated protocol/version at packet time, never only the requested version.
  installMikaelCompatibility(bot)
  return bot
}

module.exports = {
  id: 'mikael',
  label: 'Mineflayer Mikael (adaptador experimental)',
  protocolSource: '@wp2508/mineflayer',
  testedVersions,
  createBot
}
