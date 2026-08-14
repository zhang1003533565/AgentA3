<script setup>
import { computed, onMounted, ref } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import { getMessages, getUnreadCount, markAllMessagesRead, markCategoryRead, markMessageRead } from '../api/messageCenter'

const category = ref('ALL')
const messages = ref([])
const unread = ref(0)
const loading = ref(true)
const error = ref('')
const categories = [['ALL','全部'],['SYSTEM','系统'],['ACTIVITY','活动'],['FORUM','论坛'],['MARKET','市集'],['LEARNING','学习']]
const rows = (value) => Array.isArray(value) ? value : value?.content || value?.records || value?.list || []
const shown = computed(() => category.value === 'ALL' ? messages.value : messages.value.filter((item) => String(item.moduleType || item.category).toUpperCase() === category.value))
async function load(){loading.value=true;try{const [data,count]=await Promise.all([getMessages({page:0,size:100}),getUnreadCount()]);messages.value=rows(data);unread.value=Number(count?.count??count??0)}catch(cause){error.value=cause.message}finally{loading.value=false}}
async function read(item){if(item.isRead)return;try{await markMessageRead(item.id);item.isRead=true;unread.value=Math.max(0,unread.value-1)}catch(cause){error.value=cause.message}}
async function readAll(){try{if(category.value==='ALL')await markAllMessagesRead();else await markCategoryRead({category:category.value,moduleType:category.value});await load()}catch(cause){error.value=cause.message}}
onMounted(load)
</script>
<template><div class="feature-page"><AppTabBar/><main class="feature-container"><header class="feature-heading"><div><h1>消息中心</h1><p>共 {{unread}} 条未读消息</p></div><button class="feature-button" @click="readAll">全部标为已读</button></header><div class="feature-card message-layout"><aside><button v-for="[value,label] in categories" :key="value" :class="{active:category===value}" @click="category=value">{{label}}</button></aside><section><div v-if="error" class="feature-error">{{error}}</div><div v-if="loading" class="feature-empty">正在加载消息…</div><div v-else-if="!shown.length" class="feature-empty">暂无消息</div><button v-for="item in shown" :key="item.id" class="message-row" :class="{unread:!item.isRead}" @click="read(item)"><i></i><div><header><strong>{{item.title}}</strong><time>{{item.createTime}}</time></header><p>{{item.content}}</p><small>{{item.moduleType||item.category||'系统消息'}}</small></div></button></section></div></main></div></template>
<style scoped>
.message-layout{display:grid;grid-template-columns:190px 1fr;min-height:580px;overflow:hidden}.message-layout>aside{padding:14px;border-right:1px solid #e4e9ee;background:#fafbfd}.message-layout>aside button{display:block;width:100%;height:42px;padding:0 13px;border-radius:7px;color:#607286;background:transparent;text-align:left}.message-layout>aside button.active{color:#294b67;background:#e8f0f6;font-weight:750}.message-layout>section{padding:20px}.message-row{display:flex;gap:13px;width:100%;padding:17px;border-bottom:1px solid #e8edf1;color:#53677a;background:#fff;text-align:left}.message-row>i{flex:0 0 8px;width:8px;height:8px;margin-top:7px;border-radius:50%;background:#c6cfd8}.message-row.unread{background:#f7fbfe}.message-row.unread>i{background:#527797}.message-row>div{flex:1}.message-row header{display:flex;justify-content:space-between;gap:16px}.message-row strong{color:#2c4055}.message-row time{color:#8c98a4;font-size:11px}.message-row p{margin:7px 0;color:#66788a;line-height:1.6}.message-row small{color:#8293a3}@media(max-width:650px){.message-layout{grid-template-columns:1fr}.message-layout>aside{display:flex;overflow:auto;border-right:0;border-bottom:1px solid #e4e9ee}.message-layout>aside button{width:auto;white-space:nowrap}}
</style>
