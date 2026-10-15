'use strict'

// Compatibility shims for the @wp2508/mineflayer package pinned by this app.
// Only patch packets after the negotiated protocol/version identifies Java 26.3.
function is26_3 (bot) {
  // The bot's protocolVersion is set by the fork only after version negotiation.
  // Never infer a wire protocol from options.version or client.version: those
  // fields can contain the requested version before the server answers.
  const protocol = bot && bot.protocolVersion
  return Number.isInteger(protocol) && protocol === 777
}

function finite (value, fallback = 0) {
  return Number.isFinite(value) ? value : fallback
}

function installMikaelCompatibility (bot) {
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
  // Every legacy action ID > 0 shifts up by one (abort 1→2, finish 2→3,
  // release-use-item 5→6, swap-hand 6→7).
  client.write = function (packetName, params, ...rest) {
    if (
      packetName === 'block_dig' &&
      is26_3(bot) &&
      params &&
      Number.isInteger(params.status) &&
      params.status >= 1 &&
      params.status <= 6
    ) {
      params = { ...params, status: params.status + 1 }
    }
    return originalWrite.call(this, packetName, params, ...rest)
  }

  // Convert the 26.3 packet shapes to the flat fields expected by the fork's
  // current entity listeners. Only the final absolute position is needed for
  // Mineflayer's current non-interpolating entity model.
  client.emit = function (eventName, ...args) {
    if (is26_3(bot) && args[0] && typeof args[0] === 'object') {
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
        const position = entity && entity.position
        const velocity = entity && entity.velocity
        const preservedVelocity = {
          dx: finite(velocity && velocity.x),
          dy: finite(velocity && velocity.y),
          dz: finite(velocity && velocity.z)
        }
        const fallbackPosition = {
          x: finite(packet.x, finite(position && position.x)),
          y: finite(packet.y, finite(position && position.y)),
          z: finite(packet.z, finite(position && position.z))
        }

        if (packet.positionType === 'stepped') {
          // Some edge cases may carry an empty/malformed path. Keep the last known
          // coordinates rather than feeding undefined values to Vec3.set().
          const steps = Array.isArray(packet.steps) ? packet.steps : []
          const finalStep = steps.length > 0 ? steps[steps.length - 1] : null
          args[0] = {
            ...packet,
            x: finite(finalStep && finalStep.x, fallbackPosition.x),
            y: finite(finalStep && finalStep.y, fallbackPosition.y),
            z: finite(finalStep && finalStep.z, fallbackPosition.z),
            ...preservedVelocity
          }
        } else if (packet.positionType === 'linear') {
          // 26.3 linear paths omit velocity; preserve the previous velocity.
          args[0] = {
            ...packet,
            ...fallbackPosition,
            ...preservedVelocity
          }
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
    scope: 'Only active after the negotiated client resolves to Minecraft 26.3 / protocol 777'
  }
  return { installed: true, patches: [...patches] }
}

module.exports = { is26_3, installMikaelCompatibility }
