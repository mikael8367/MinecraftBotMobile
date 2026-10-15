'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { normalizeCommand, parseCommand } = require('../command-parser')

test('normalizes accents and case without changing command meaning', () => {
  assert.equal(normalizeCommand('  AVANÇAR  '), 'avancar')
  assert.equal(parseCommand('avançar'), 'forward')
  assert.equal(parseCommand('vá para trás'), 'back')
  assert.equal(parseCommand('ficar parado'), null)
})

test('recognizes all documented commands', () => {
  const examples = {
    'parar': 'stop',
    'FICA PARADO': 'stop',
    'pule': 'jump',
    'ataque': 'attack',
    'andar para frente': 'forward',
    'voltar': 'back',
    'esquerda': 'left',
    'direita': 'right',
    'correr': 'sprint'
  }
  for (const [input, expected] of Object.entries(examples)) {
    assert.equal(parseCommand(input), expected, input)
  }
})

test('does not execute unknown, empty, or negated phrases', () => {
  for (const phrase of ['', 'faz uma casa', 'não atacar', 'não pare', 'nunca pule', 'evite correr']) {
    assert.equal(parseCommand(phrase), null, phrase)
  }
  assert.equal(parseCommand('re'), null)
})

test('caps command processing input to 200 characters', () => {
  assert.equal(normalizeCommand('a'.repeat(500)).length, 200)
})
