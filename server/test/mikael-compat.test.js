'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { is26_3, installMikaelCompatibility } = require('../motors/mikael-compat')

function makeBot (version = '26.3', protocolVersion = 777) {
  const client = new EventEmitter()
  client.version = version
  client.protocolVersion = protocolVersion
  const writes = []
  client.write = function (name, params) {
    writes.push({ name, params })
    return true
  }
  const bot = {
    version,
    protocolVersion,
    _client: client,
    entities: {
      12: {
        position: { x: 1, y: 2, z: 3 },
        velocity: { x: 0.1, y: 0.2, z: 0.3 }
      }
    }
  }
  return { bot, client, writes }
}

test('26.3 compatibility gate does not turn on for older protocols', () => {
  const { bot } = makeBot('26.2', 776)
  assert.equal(is26_3(bot, 'auto'), false)
  assert.equal(is26_3(bot, '26.3'), true)
})

test('26.3 shifts legacy block_dig status IDs without mutating caller payload', () => {
  const { bot, client, writes } = makeBot()
  installMikaelCompatibility(bot, '26.3')
  for (const [before, after] of [[0, 0], [1, 2], [2, 3], [5, 6], [6, 7]]) {
    const body = { status: before, location: { x: 0, y: 0, z: 0 } }
    client.write('block_dig', body)
    assert.equal(writes.at(-1).params.status, after)
    assert.equal(body.status, before)
  }
  client.write('other_packet', { status: 2 })
  assert.equal(writes.at(-1).params.status, 2)
})

test('26.3 normalizes multi-step relative entity deltas for the existing listener', () => {
  const { bot, client } = makeBot()
  installMikaelCompatibility(bot, '26.3')
  let received
  client.on('rel_entity_move', packet => { received = packet })
  client.emit('rel_entity_move', {
    entityId: 12,
    move: { onGround: true, steps: [{ dX: 32, dY: 0, dZ: -32 }, { dX: 64, dY: 32, dZ: 0 }] }
  })
  assert.deepEqual([received.dX, received.dY, received.dZ], [96, 32, -32])
  assert.equal(received.move.steps.length, 2)
})

test('26.3 stepped position uses the final absolute position and preserves velocity', () => {
  const { bot, client } = makeBot()
  installMikaelCompatibility(bot, '26.3')
  let received
  client.on('sync_entity_position', packet => { received = packet })
  client.emit('sync_entity_position', {
    entityId: 12,
    positionType: 'stepped',
    steps: [{ x: 10, y: 11, z: 12 }, { x: 13, y: 14, z: 15 }],
    yaw: 0,
    pitch: 0
  })
  assert.deepEqual([received.x, received.y, received.z], [13, 14, 15])
  assert.deepEqual([received.dx, received.dy, received.dz], [0.1, 0.2, 0.3])
})

test('26.3 linear position preserves velocity when packet omits it', () => {
  const { bot, client } = makeBot()
  installMikaelCompatibility(bot, '26.3')
  let received
  client.on('sync_entity_position', packet => { received = packet })
  client.emit('sync_entity_position', {
    entityId: 12, positionType: 'linear', x: 4, y: 5, z: 6, yaw: 0, pitch: 0
  })
  assert.deepEqual([received.dx, received.dy, received.dz], [0.1, 0.2, 0.3])
})

test('compatibility shim leaves 26.2 packets unchanged', () => {
  const { bot, client, writes } = makeBot('26.2', 776)
  installMikaelCompatibility(bot, 'auto')
  client.write('block_dig', { status: 5 })
  assert.equal(writes.at(-1).params.status, 5)
  let received
  client.on('rel_entity_move', packet => { received = packet })
  const original = { entityId: 12, dX: 32, dY: 0, dZ: 0 }
  client.emit('rel_entity_move', original)
  assert.equal(received, original)
})
