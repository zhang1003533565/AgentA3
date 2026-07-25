import { getRealtimeTicket } from '@/api/message'
import { BASE_URL } from '@/utils/config'
import { getToken } from '@/utils/storage'

const RECONNECT_DELAYS = [1000, 2000, 5000, 10000, 30000]

let socketTask = null
let reconnectTimer = null
let reconnectAttempt = 0
let opening = false
let stopped = true
let eventListener = null

function socketUrl(ticket) {
  const base = String(BASE_URL || '').replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
  return `${base}/api/realtime/messages?ticket=${encodeURIComponent(ticket)}`
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function scheduleReconnect() {
  if (stopped || reconnectTimer || !getToken()) return
  const delay = RECONNECT_DELAYS[Math.min(reconnectAttempt, RECONNECT_DELAYS.length - 1)]
  reconnectAttempt += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

function handleMessage(raw) {
  try {
    const event = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (event?.type === 'MESSAGE_STATE_CHANGED' && typeof eventListener === 'function') {
      eventListener(event)
    }
  } catch (error) {
    console.warn('message socket payload ignored', error)
  }
}

async function connect() {
  if (stopped || opening || socketTask || !getToken()) return
  opening = true
  try {
    const response = await getRealtimeTicket()
    const ticket = response?.data?.ticket
    if (!ticket || stopped || !getToken()) {
      opening = false
      return
    }
    const task = uni.connectSocket({ url: socketUrl(ticket), complete: () => {} })
    socketTask = task
    task.onOpen(() => {
      opening = false
      reconnectAttempt = 0
      clearReconnectTimer()
    })
    task.onMessage((event) => handleMessage(event?.data))
    task.onError(() => {
      opening = false
      if (socketTask === task) socketTask = null
      try {
        task.close({ code: 1011, reason: 'connection_error' })
      } catch (error) {
        console.warn('message socket error close failed', error)
      }
      scheduleReconnect()
    })
    task.onClose(() => {
      opening = false
      if (socketTask === task) socketTask = null
      scheduleReconnect()
    })
  } catch (error) {
    opening = false
    socketTask = null
    scheduleReconnect()
  }
}

export function startMessageSocket(listener) {
  eventListener = typeof listener === 'function' ? listener : eventListener
  stopped = false
  connect()
}

export function stopMessageSocket() {
  stopped = true
  opening = false
  reconnectAttempt = 0
  eventListener = null
  clearReconnectTimer()
  const task = socketTask
  socketTask = null
  if (task && typeof task.close === 'function') {
    try {
      task.close({ code: 1000, reason: 'logout' })
    } catch (error) {
      console.warn('message socket close failed', error)
    }
  }
}
