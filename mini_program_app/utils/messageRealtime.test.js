const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function source(file) {
  return readFileSync(join(__dirname, file), 'utf8')
}

test('message state uses event-driven socket refresh without business polling', () => {
  const store = source('messageStore.js')
  const socket = source('messageSocket.js')

  assert.doesNotMatch(store, /POLL_INTERVAL|setInterval\s*\(/)
  assert.match(store, /startMessageSocket\(handleRealtimeEvent\)/)
  assert.match(store, /event\?\.type !== 'MESSAGE_STATE_CHANGED'/)
  assert.match(store, /if \(realtimeRefreshPending\) scheduleRealtimeRefresh\(\)/)
  assert.match(socket, /uni\.connectSocket/)
  assert.match(socket, /getRealtimeTicket\(\)/)
  assert.doesNotMatch(socket, /setInterval\s*\(|heartbeat|ping/i)
})

test('socket reconnects with bounded backoff and never places the JWT in its URL', () => {
  const socket = source('messageSocket.js')

  assert.match(socket, /\[1000, 2000, 5000, 10000, 30000\]/)
  assert.match(socket, /\?ticket=\$\{encodeURIComponent\(ticket\)\}/)
  assert.doesNotMatch(socket, /\?token=|Authorization/)
})
