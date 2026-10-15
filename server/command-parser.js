'use strict'

function normalizeCommand (input) {
  return String(input ?? '')
    .trim()
    .slice(0, 200)
    .toLocaleLowerCase('pt-BR')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

function parseCommand (input) {
  const message = normalizeCommand(input)
  if (!message) return null

  // Avoid acting when a phrase explicitly negates the command.
  if (/\b(nao|nunca|jamais|evite|evitar)\b/.test(message)) return null

  if (/\b(parar|pare|stop|fica parado)\b/.test(message)) return 'stop'
  if (/\b(pular|pule|jump)\b/.test(message)) return 'jump'
  if (/\b(atacar|ataque|bater)\b/.test(message)) return 'attack'
  if (/\b(frente|andar|avancar|andar para frente)\b/.test(message)) return 'forward'
  if (/\b(voltar|tras)\b/.test(message)) return 'back'
  if (/\b(esquerda)\b/.test(message)) return 'left'
  if (/\b(direita)\b/.test(message)) return 'right'
  if (/\b(correr|sprint)\b/.test(message)) return 'sprint'
  return null
}

module.exports = { normalizeCommand, parseCommand }
