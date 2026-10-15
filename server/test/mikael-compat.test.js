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

test('compatibility gate follows negotiated protocol, not requested version', () => {
  const { bot, client, writes } = makeBot('26.2', 776)
  assert.equal(is26_3(bot), false)
  assert.equal(is26_3(bot, '26.3'), false)

  installMikaelCompatibility(bot, '26.3')
  client.write('block_dig', { status: 5 })
  assert.equal(writes.at(-1).params.status, 5)
})

test('compatibility never activates from a requested version without negotiated protocol', () => {
  const { bot, client, writes } = makeBot('26.3', null)
  assert.equal(is26_3(bot), false)
  installMikaelCompatibility(bot)
  client.write('block_dig', { status: 5 })
  assert.equal(writes.at(-1).params.status, 5)

  // Only the negotiated protocol enables the shim.
  bot.protocolVersion = 777
  assert.equal(is26_3(bot), true)
  client.write('block_dig', { status: 5 })
  assert.equal(writes.at(-1).params.status, 6)
})

test('26.3 shifts legacy block_dig status IDs without mutating caller payload', () => {
  const { bot, client, writes } = makeBot()
  installMikaelCompatibility(bot)
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
  installMikaelCompatibility(bot)
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
  installMikaelCompatibility(bot)
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

test('26.3 empty stepped path preserves the last known position and velocity', () => {
  const { bot, client } = makeBot()
  installMikaelCompatibility(bot)
  let received
  client.on('sync_entity_position', packet => { received = packet })
  client.emit('sync_entity_position', {
    entityId: 12, positionType: 'stepped', steps: [], yaw: 0, pitch: 0
  })
  assert.deepEqual([received.x, received.y, received.z], [1, 2, 3])
  assert.deepEqual([received.dx, received.dy, received.dz], [0.1, 0.2, 0.3])
})

test('26.3 linear position preserves velocity and falls back from invalid coordinates', () => {
  const { bot, client } = makeBot()
  installMikaelCompatibility(bot)
  let received
  client.on('sync_entity_position', packet => { received = packet })
  client.emit('sync_entity_position', {
    entityId: 12, positionType: 'linear', x: NaN, y: undefined, z: 6, yaw: 0, pitch: 0
  })
  assert.deepEqual([received.x, received.y, received.z], [1, 2, 6])
  assert.deepEqual([received.dx, received.dy, received.dz], [0.1, 0.2, 0.3])
})

test('compatibility shim leaves 26.2 packets unchanged', () => {
  const { bot, client, writes } = makeBot('26.2', 776)
  installMikaelCompatibility(bot)
  client.write('block_dig', { status: 5 })
  assert.equal(writes.at(-1).params.status, 5)
  let received
  client.on('rel_entity_move', packet => { received = packet })
  const original = { entityId: 12, dX: 32, dY: 0, dZ: 0 }
  client.emit('rel_entity_move', original)
  assert.equal(received, original)
})


test('26.3 inserts a tick_end boundary before every extra movement packet across 50 movements', () => {
  const { bot, client, writes } = makeBot()
  installMikaelCompatibility(bot)

  const expected = []
  for (let i = 0; i < 50; i++) {
    const packetName = i % 2 === 0 ? 'position' : 'position_look'
    client.write(packetName, { x: i + 0.5, y: 64, z: i + 0.5 })
    if (i > 0) expected.push('tick_end')
    expected.push(packetName)
  }

  assert.deepEqual(writes.map(packet => packet.name), expected)
  assert.equal(writes.filter(packet => packet.name === 'tick_end').length, 49)
})

test('native 26.3 tick_end prevents a duplicate fallback packet', () => {
  const { bot, client, writes } = makeBot()
  installMikaelCompatibility(bot)

  client.write('position', { x: 1, y: 64, z: 1 })
  client.write('tick_end', {})
  client.write('position_look', { x: 2, y: 64, z: 2 })

  assert.deepEqual(writes.map(packet => packet.name), ['position', 'tick_end', 'position_look'])
})

test('movement tick_end fallback does not change pre-26.3 movement packets', () => {
  const { bot, client, writes } = makeBot('26.2', 776)
  installMikaelCompatibility(bot)

  client.write('position', { x: 1, y: 64, z: 1 })
  client.write('position_look', { x: 2, y: 64, z: 2 })

  assert.deepEqual(writes.map(packet => packet.name), ['position', 'position_look'])
})
