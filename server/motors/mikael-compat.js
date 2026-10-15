'use strict'

// Compatibility shims for the current @wp2508/mineflayer package used by this app.
// Keep these narrowly gated to Java 26.3 (protocol 777); do not change packet
// handling for older versions or for 26.1/26.2.
function is26_3 (bot, requestedVersion) {
  if (String(requestedVersion || '') === '26.3') return true
  const client = bot && bot._client
  const candidates = [
    bot && bot.version,
    client && client.version
  ]
  if (candidates.some(version => String(version || '') === '26.3')) return true
  return Number((bot && bot.protocolVersion) ?? (client && client.protocolVersion)) === 777
}

function finite (value, fallback = 0) {
  return Number.isFinite(value) ? value : fallback
}

function installMikaelCompatibility (bot, requestedVersion) {
  const client = bot && bot._client
  if (!client || typeof client.emit !== 'function' || typeof client.write !== 'function') {
    return { installed: false, reason: 'Mineflayer client does not expose emit/write' }
  }

  const marker = Symbol.for('minecraftbotmobile.mikael.compatibility.v1')
  if (client[marker]) return { installed: true, alreadyInstalled: true, patches: client[marker] }
  const patches = ['26.3-player-action-ids', '26.3-entity-movement-shapes']
  const originalEmit = client.emit
  const originalWrite = client.write

  // In 26.3 the enum inserted CHANGE_DESTROY_DIRECTION at id 1.
  // The installed fork still writes legacy numeric block_dig statuses, so every
  // old action ID > 0 must move up by one: abort 1→2, finish 2→3, release 5→6.
  client.write = function (packetName, params, ...rest) {
    if (
      packetName === 'block_dig' &&
      is26_3(bot, requestedVersion) &&
      params &&
      Number.isInteger(params.status) &&
      params.status >= 1 &&
      params.status <= 6
    ) {
      params = { ...params, status: params.status + 1 }
    }
    return originalWrite.call(this, packetName, params, ...rest)
  }

  // The 26.3 wire schema uses chained relative movement steps and new absolute
  // position-path shapes. Existing fork listeners still expect the pre-26.3
  // flat fields. Normalize the packet just before the client's listeners run.
  client.emit = function (eventName, ...args) {
    if (is26_3(bot, requestedVersion) && args[0] && typeof args[0] === 'object') {
      const packet = args[0]

      if (
        (eventName === 'rel_entity_move' || eventName === 'entity_move_look') &&
        packet.move &&
        Array.isArray(packet.move.steps)
      ) {
        const totals = packet.move.steps.reduce((sum, step) => ({
          dX: sum.dX + finite(step && step.dX),
          dY: sum.dY + finite(step && step.dY),
          dZ: sum.dZ + finite(step && step.dZ)
        }), { dX: 0, dY: 0, dZ: 0 })
        args[0] = { ...packet, ...totals }
      } else if (eventName === 'sync_entity_position' && packet.positionType) {
        const entity = bot.entities && bot.entities[packet.entityId]
        const velocity = entity && entity.velocity
        const preservedVelocity = {
          dx: finite(velocity && velocity.x),
          dy: finite(velocity && velocity.y),
          dz: finite(velocity && velocity.z)
        }

        if (packet.positionType === 'stepped' && Array.isArray(packet.steps) && packet.steps.length > 0) {
          // Each step is an absolute position; the last is the final server position.
          const finalStep = packet.steps[packet.steps.length - 1]
          args[0] = {
            ...packet,
            x: finite(finalStep.x, packet.x),
            y: finite(finalStep.y, packet.y),
            z: finite(finalStep.z, packet.z),
            ...preservedVelocity
          }
        } else if (packet.positionType === 'linear') {
          // 26.3 linear paths omit velocity. Preserve the prior entity velocity rather
          // than allowing the old listener to overwrite it with undefined values.
          args[0] = { ...packet, ...preservedVelocity }
        }
      }
    }
    return originalEmit.call(this, eventName, ...args)
  }

  Object.defineProperty(client, marker, { value: patches, configurable: false })
  bot._mikaelCompatibility = {
    id: 'mikael-26.3-compat',
    applied: true,
    patches: [...patches],
    scope: 'Only active after this client resolves to Minecraft 26.3 / protocol 777'
  }
  return { installed: true, patches: [...patches] }
}

module.exports = { is26_3, installMikaelCompatibility }
