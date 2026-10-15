'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { validateConnectionPayload, ValidationError } = require('../connection-validation')

const engines = {
  official: { label: 'Mineflayer oficial', testedVersions: ['1.12.2', '1.21.11', '26.1'] },
  mikael: { label: 'Mineflayer Mikael', testedVersions: ['1.12.2', '1.21.11', '26.1.2', '26.2', '26.3'] }
}

function invalid (fn, phrase) {
  assert.throws(fn, (error) => {
    assert.ok(error instanceof ValidationError)
    assert.match(error.message, phrase)
    return true
  })
}

test('accepts Auto, defaults to offline authentication, and normalizes a valid target', () => {
  assert.deepEqual(
    validateConnectionPayload({ host: ' play.example.net ', username: 'MikaelBot', port: '25565' }, engines),
    { host: 'play.example.net', port: 25565, username: 'MikaelBot', accountId: null, auth: 'offline', version: 'auto', engine: 'mikael' }
  )
})

test('accepts Microsoft device authentication only with a safe account identifier', () => {
  assert.deepEqual(
    validateConnectionPayload({
      host: 'mc.example.net', username: 'MikaelBot', accountId: 'mikael@example.com',
      auth: 'microsoft', version: '26.3', engine: 'mikael'
    }, engines),
    {
      host: 'mc.example.net', port: 25565, username: 'MikaelBot', accountId: 'mikael@example.com',
      auth: 'microsoft', version: '26.3', engine: 'mikael'
    }
  )
  invalid(() => validateConnectionPayload({
    host: 'mc.example.net', username: 'Bot', auth: 'microsoft', accountId: '../token'
  }, engines), /identificador da conta Microsoft/)
  invalid(() => validateConnectionPayload({
    host: 'mc.example.net', username: 'Bot', auth: 'microsoft'
  }, engines), /identificador da conta Microsoft/)
})

test('rejects unknown authentication modes', () => {
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', auth: 'password' }, engines), /Autenticação inválida/)
})

test('accepts 26.3 only when selected engine metadata lists it', () => {
  assert.equal(validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', version: '26.3', engine: 'mikael' }, engines).version, '26.3')
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', version: '26.3', engine: 'official' }, engines), /testedVersions/)
})

test('rejects a version outside the selected engine list', () => {
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', version: '26.2', engine: 'official' }, engines), /testedVersions/)
})

test('rejects invalid or missing host and malformed request body', () => {
  invalid(() => validateConnectionPayload(null, engines), /JSON/)
  invalid(() => validateConnectionPayload({ host: '', username: 'Bot' }, engines), /IP ou domínio/)
  invalid(() => validateConnectionPayload({ host: 'mc example.net', username: 'Bot' }, engines), /IP ou domínio/)
  invalid(() => validateConnectionPayload({ host: 'https://example.net', username: 'Bot' }, engines), /IP ou domínio/)
})

test('rejects invalid ports without silently parsing partial numbers', () => {
  for (const port of ['25565abc', '0', '65536', '-1', '1.5']) {
    invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', port }, engines), /porta/)
  }
})

test('rejects invalid offline usernames instead of silently truncating them', () => {
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'name with spaces' }, engines), /nome\/identificador offline/)
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'abcdefghijklmnopq' }, engines), /nome\/identificador offline/)
})

test('rejects unknown engine identifiers', () => {
  invalid(() => validateConnectionPayload({ host: 'mc.example.net', username: 'Bot', engine: 'unknown' }, engines), /Motor inválido/)
})
