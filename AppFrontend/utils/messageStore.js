import {
  getChatSessions,
  getChatUnreadCount,
  getTradeNotificationUnreadCount,
  getTradeNotifications
} from '@/api/secondhand'
import { getEnabledAnnouncements } from '@/api/notice'
import { getAppMessageUnreadCount } from '@/api/message'
import { getToken } from '@/utils/storage'
import { startMessageSocket, stopMessageSocket } from '@/utils/messageSocket'

const state = {
  unreadChatCount: 0,
  unreadTradeCount: 0,
  unreadAppCount: 0,
  unreadLostFoundAppCount: 0,
  totalUnreadCount: 0,
  sessions: [],
  tradeNotifications: [],
  activeChatSessionId: null,
  lastSyncAt: 0,
  syncing: false,
  started: false
}

let realtimeRefreshTimer = null
let realtimeRefreshPending = false
const listeners = new Set()
let lastSignature = ''

function numberValue(value) {
  const next = Number(value)
  return Number.isFinite(next) ? next : 0
}

function getRecords(res) {
  if (Array.isArray(res?.data?.records)) return res.data.records
  if (Array.isArray(res?.data)) return res.data
  return []
}

function getAnnouncementUnreadCount(res) {
  const list = getRecords(res)
  const lastSeenId = Number(uni.getStorageSync('marketLastSeenAnnounceId') || 0)
  return list.filter((item) => Number(item.id || 0) > lastSeenId).length
}

function buildSignature(nextState) {
  const sessionPart = (nextState.sessions || [])
    .map((item) => [
      item.sessionId,
      item.lastMessage,
      item.lastTime,
      item.unreadCount,
      item.tradeStatus,
      item.contactExchangeStatus
    ].join(':'))
    .join('|')
  const tradePart = (nextState.tradeNotifications || [])
    .map((item) => [item.id, item.isRead, item.tradeStatus, item.createTime].join(':'))
    .join('|')
  return [
    nextState.unreadChatCount,
    nextState.unreadTradeCount,
    nextState.unreadAppCount,
    nextState.unreadLostFoundAppCount,
    sessionPart,
    tradePart
  ].join('::')
}

function notify(reason = 'sync') {
  const snapshot = getMessageState()
  listeners.forEach((listener) => {
    try {
      listener(snapshot, reason)
    } catch (error) {
      console.warn('messageStore listener failed', error)
    }
  })
  if (typeof uni !== 'undefined' && uni.$emit) {
    uni.$emit('message-store:change', { state: snapshot, reason })
  }
}

export function getMessageState() {
  return {
    unreadChatCount: state.unreadChatCount,
    unreadTradeCount: state.unreadTradeCount,
    unreadAppCount: state.unreadAppCount,
    unreadLostFoundAppCount: state.unreadLostFoundAppCount,
    totalUnreadCount: state.totalUnreadCount,
    sessions: [...state.sessions],
    tradeNotifications: [...state.tradeNotifications],
    activeChatSessionId: state.activeChatSessionId,
    lastSyncAt: state.lastSyncAt,
    syncing: state.syncing,
    started: state.started
  }
}

export function subscribeMessageStore(listener) {
  if (typeof listener !== 'function') return () => {}
  listeners.add(listener)
  listener(getMessageState(), 'subscribe')
  return () => {
    listeners.delete(listener)
  }
}

export async function refreshMessageState(reason = 'manual') {
  if (!getToken()) return getMessageState()
  if (state.syncing) return getMessageState()
  state.syncing = true
  try {
    const [chatUnreadRes, tradeUnreadRes, announceRes, appMessageUnreadRes, sessionsRes, tradeRes] = await Promise.all([
      getChatUnreadCount(),
      getTradeNotificationUnreadCount(),
      getEnabledAnnouncements().catch(() => ({ data: [] })),
      getAppMessageUnreadCount({ showError: false }).catch(() => ({ data: { lostFound: 0 } })),
      getChatSessions({ current: 1, size: 100 }),
      getTradeNotifications({ current: 1, size: 100 })
    ])

    state.unreadChatCount = numberValue(chatUnreadRes?.data)
    state.unreadTradeCount = numberValue(tradeUnreadRes?.data)
    state.unreadAppCount = getAnnouncementUnreadCount(announceRes)
    state.unreadLostFoundAppCount = numberValue(appMessageUnreadRes?.data?.lostFound)
    state.totalUnreadCount = state.unreadChatCount + state.unreadTradeCount + state.unreadAppCount
    state.sessions = getRecords(sessionsRes)
    state.tradeNotifications = getRecords(tradeRes)
    state.lastSyncAt = Date.now()

    const signature = buildSignature(state)
    if (signature !== lastSignature || reason !== 'realtime') {
      lastSignature = signature
      notify(reason)
    }
  } catch (error) {
    console.warn('messageStore refresh failed', error)
  } finally {
    state.syncing = false
    if (realtimeRefreshPending) scheduleRealtimeRefresh()
  }
  return getMessageState()
}

function scheduleRealtimeRefresh() {
  if (realtimeRefreshTimer) return
  realtimeRefreshTimer = setTimeout(() => {
    realtimeRefreshTimer = null
    if (state.syncing) return
    realtimeRefreshPending = false
    refreshMessageState('realtime')
  }, 120)
}

function handleRealtimeEvent(event) {
  if (event?.type !== 'MESSAGE_STATE_CHANGED') return
  realtimeRefreshPending = true
  scheduleRealtimeRefresh()
}

export function startMessageSync() {
  state.started = true
  startMessageSocket(handleRealtimeEvent)
}

export function stopMessageSync() {
  if (realtimeRefreshTimer) {
    clearTimeout(realtimeRefreshTimer)
    realtimeRefreshTimer = null
  }
  realtimeRefreshPending = false
  stopMessageSocket()
  state.started = false
}

export function setActiveChatSession(sessionId) {
  state.activeChatSessionId = sessionId ? Number(sessionId) : null
  notify('active-chat')
}

export function clearActiveChatSession(sessionId) {
  if (!sessionId || Number(sessionId) === Number(state.activeChatSessionId)) {
    state.activeChatSessionId = null
    notify('active-chat')
  }
}

export default {
  state,
  getState: getMessageState,
  subscribe: subscribeMessageStore,
  refresh: refreshMessageState,
  start: startMessageSync,
  stop: stopMessageSync,
  setActiveChatSession,
  clearActiveChatSession
}
