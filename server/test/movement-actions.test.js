'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { applyMovementAction } = require('../movement-actions')

test('sprint presses forward and sprint for a bounded duration', () => {
  const pressed = []
  const result = applyMovementAction('sprint', (control, duration) => pressed.push({ control, duration }))
  assert.deepEqual(pressed, [
    { control: 'forward', duration: 1200 },
    { control: 'sprint', duration: 1200 }
  ])
  assert.equal(result.ok, true)
})

test('single movement controls have a finite duration', () => {
  const pressed = []
  const result = applyMovementAction('forward', (control, duration) => pressed.push({ control, duration }))
  assert.deepEqual(pressed, [{ control: 'forward', duration: 700 }])
  assert.equal(result.ok, true)
})

test('unknown actions are left for the server action handler', () => {
  assert.equal(applyMovementAction('follow', () => assert.fail('must not press a movement control')), null)
  assert.equal(applyMovementAction('attack', () => assert.fail('must not press a movement control')), null)
})
