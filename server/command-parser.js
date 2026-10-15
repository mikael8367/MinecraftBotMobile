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
  let message = normalizeCommand(input)
  if (!message) return null
  message = message.replace(/[.!?,;]+$/g, '').trim()
  message = message.replace(/^(?:bot|mikaelbot)[\s,:-]+/i, '').trim()
  if (!message) return null

  // Never move/attack on a phrase that explicitly negates an action.
  if (/\b(nao|nunca|jamais|evite|evitar)\b/.test(message)) return null

  // Match complete commands or known command phrases; keyword mentions in
  // unrelated sentences (e.g. "o jogador está na minha frente") are not orders.
  const rules = [
    [/^(?:parar|pare|stop|fica(?:r)? parado|fique parado|para de andar)(?: agora)?$/, 'stop'],
    [/^(?:pular|pule|pula|jump)(?: agora)?$/, 'jump'],
    [/^(?:atacar|ataque|bater)(?: agora| nele| no jogador)?$/, 'attack'],
    [/^(?:frente|andar|ande|avancar|avanca|andar para frente|ande para frente|va para frente|vai para frente|siga em frente|segue em frente)$/, 'forward'],
    [/^(?:voltar|volte|tras|va para tras|vai para tras|ande para tras|andar para tras|recue)$/, 'back'],
    [/^(?:esquerda|va para esquerda|vai para esquerda|ande para esquerda|vire a esquerda|vira a esquerda)$/, 'left'],
    [/^(?:direita|va para direita|vai para direita|ande para direita|vire a direita|vira a direita)$/, 'right'],
    [/^(?:correr|corra|sprint)$/, 'sprint']
  ]

  for (const [pattern, action] of rules) {
    if (pattern.test(message)) return action
  }
  return null
}

module.exports = { normalizeCommand, parseCommand }
