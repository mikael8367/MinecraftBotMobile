'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { normalizeCommand, parseCommand } = require('../command-parser')

test('normalizes accents and case without changing command meaning', () => {
  assert.equal(normalizeCommand('  AVANÇAR  '), 'avancar')
  assert.equal(parseCommand('avançar'), 'forward')
  assert.equal(parseCommand('vá para trás'), 'back')
  assert.equal(parseCommand('ficar parado'), 'stop')
})

test('recognizes documented and common direct commands', () => {
  const examples = {
    'parar': 'stop',
    'FICA PARADO': 'stop',
    'ficar parado': 'stop',
    'para de andar agora': 'stop',
    'pule': 'jump',
    'ataque': 'attack',
    'ataque nele': 'attack',
    'andar para frente': 'forward',
    'ande para frente': 'forward',
    'voltar': 'back',
    'esquerda': 'left',
    'vire à esquerda': 'left',
    'direita': 'right',
    'correr': 'sprint',
    'bot, avance': 'forward',
    'MikaelBot: pare': 'stop'
  }
  for (const [input, expected] of Object.entries(examples)) {
    assert.equal(parseCommand(input), expected, input)
  }
})

test('does not execute unknown, empty, negated, or incidental keyword phrases', () => {
  for (const phrase of [
    '', 'faz uma casa', 'não atacar', 'não pare', 'nunca pule', 'evite correr',
    'o caminho fica na minha frente', 'o jogador está à direita',
    'vou atacar depois', 'ele começou a pular', 're'
  ]) {
    assert.equal(parseCommand(phrase), null, phrase)
  }
})

test('caps command processing input to 200 characters', () => {
  assert.equal(normalizeCommand('a'.repeat(500)).length, 200)
})
