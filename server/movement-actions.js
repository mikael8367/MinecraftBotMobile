'use strict'

// Deterministic movement policy kept separate so actions can be regression-tested.
function applyMovementAction (actionValue, pressControl) {
  const action = String(actionValue || '').trim().toLowerCase()
  const timedControls = new Set(['forward', 'back', 'left', 'right', 'jump'])

  if (timedControls.has(action)) {
    pressControl(action, 700)
    return { ok: true, message: 'Ação executada: ' + action }
  }

  if (action === 'sprint') {
    // Sprint toggles speed but does not move the bot by itself.
    pressControl('forward', 1200)
    pressControl('sprint', 1200)
    return { ok: true, message: 'Corrida iniciada por 1,2 segundo.' }
  }

  return null
}

module.exports = { applyMovementAction }
