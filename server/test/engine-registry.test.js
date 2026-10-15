'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { engines, capabilities } = require('../engine-registry')
const mikaelAdapter = require('../motors/mikael')

test('both engines expose real installed testedVersions metadata', () => {
  assert.ok(engines.official.testedVersions.length > 0)
  assert.ok(engines.mikael.testedVersions.length > 0)
  assert.ok(engines.official.testedVersions.includes('1.12.2'))
  assert.ok(engines.mikael.testedVersions.includes('1.12.2'))
})

test('26.3 is exposed only by the Mikael fork metadata', () => {
  assert.ok(engines.mikael.testedVersions.includes('26.3'))
  assert.ok(!engines.official.testedVersions.includes('26.3'))
})

test('capabilities returns copy arrays, not mutable internal version arrays', () => {
  const response = capabilities()
  response.engines.mikael.testedVersions.push('fake-version')
  assert.ok(!engines.mikael.testedVersions.includes('fake-version'))
})

test('Mikael adapter rejects an unlisted version before creating a connection', () => {
  assert.throws(
    () => mikaelAdapter.createBot({ host: 'invalid.example', username: 'Bot', version: '0.0-not-real' }),
    /testedVersions/
  )
})
