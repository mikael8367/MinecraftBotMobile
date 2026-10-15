'use strict'

const officialMineflayer = require('mineflayer')
const officialVersionInfo = require('mineflayer/lib/version')
const mikael = require('./motors/mikael')

const officialVersions = Object.freeze([...(officialVersionInfo.testedVersions || [])])

const engines = Object.freeze({
  official: Object.freeze({
    id: 'official',
    label: 'Mineflayer oficial',
    protocolSource: 'PrismarineJS/mineflayer',
    testedVersions: officialVersions,
    createBot: (options) => officialMineflayer.createBot({
      ...options,
      hideErrors: false,
      logErrors: false
    })
  }),
  mikael
})

function capabilities () {
  const result = {}
  for (const [id, engine] of Object.entries(engines)) {
    result[id] = {
      id,
      label: engine.label,
      protocolSource: engine.protocolSource,
      testedVersions: [...engine.testedVersions]
    }
  }
  return {
    engines: result,
    notes: 'A lista vem do testedVersions de cada pacote. Ela não significa que este projeto testou cada versão ou que todo servidor aceitará a conexão.'
  }
}

module.exports = { engines, capabilities }
