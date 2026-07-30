<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import {
  createOrGetChatSession, createSecondhandItem, favoriteSecondhandItem,
  getChatSessions, getMySecondhandFavorites, getMySecondhandItems,
  getSecondhandCategories, getSecondhandItemDetail, getSecondhandItemList,
  getTradeNotifications, getTradeRecords, reserveSecondhandItem, unfavoriteSecondhandItem,
  confirmTradeRecord, completeTradeRecord, cancelTradeRecord,
} from '../api/secondhand'

const router = useRouter()
const tab = ref('market')
const loading = ref(true)
const error = ref('')
const items = ref([])
const categories = ref([])
const selectedCategory = ref('')
const keyword = ref('')
const selected = ref(null)
const sessions = ref([])
const trades = ref([])
const notifications = ref([])
const historyItems = ref([])
const showPublish = ref(false)
const publishing = ref(false)
const form = ref({ title:'', description:'', price:'', categoryId:'', condition:'GOOD', tradeLocation:'', images:[] })
const tabs = [['market','逛市集'],['mine','我的发布'],['favorites','我的收藏'],['history','浏览记录'],['chats','我的消息'],['trades','交易记录'],['notifications','交易通知']]

const content = (value) => Array.isArray(value) ? value : value?.content || value?.records || value?.list || []
const parseImages = (value) => {
  if (Array.isArray(value)) return value
  try { return JSON.parse(value || '[]') } catch { return String(value || '').split(',').filter(Boolean) }
}
const shownItems = computed(() => items.value.filter((item) => {
  const query = keyword.value.trim().toLowerCase()
  return !query || `${item.title} ${item.description || ''}`.toLowerCase().includes(query)
}))
const price = (value) => Number.isFinite(Number(value)) ? `¥${Number(value).toFixed(2)}` : '价格待确认'

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'market') items.value = content(await getSecondhandItemList({ page:0, size:40, categoryId:selectedCategory.value || undefined, keyword:keyword.value || undefined }))
    if (tab.value === 'mine') items.value = content(await getMySecondhandItems({ page:0, size:40 }))
    if (tab.value === 'favorites') items.value = content(await getMySecondhandFavorites({ page:0, size:40 }))
    if (tab.value === 'chats') sessions.value = content(await getChatSessions({ page:0, size:50 }))
    if (tab.value === 'trades') trades.value = content(await getTradeRecords({ page:0, size:50 }))
    if (tab.value === 'notifications') notifications.value = content(await getTradeNotifications({ page:0, size:50 }))
    if (tab.value === 'history') historyItems.value = JSON.parse(localStorage.getItem('marketBrowseHistory') || '[]')
  } catch (cause) { error.value = cause.message } finally { loading.value = false }
}
async function openItem(id) {
  try {
    selected.value = await getSecondhandItemDetail(id)
    const next = [
      { id:selected.value.id,title:selected.value.title,price:selected.value.price,images:selected.value.images,description:selected.value.description,viewedAt:new Date().toISOString() },
      ...JSON.parse(localStorage.getItem('marketBrowseHistory') || '[]').filter((item) => String(item.id) !== String(id)),
    ].slice(0, 50)
    localStorage.setItem('marketBrowseHistory', JSON.stringify(next))
  } catch (cause) { error.value = cause.message }
}
async function toggleFavorite(item) {
  try {
    if (item.isFavorited) await unfavoriteSecondhandItem(item.id)
    else await favoriteSecondhandItem(item.id)
    item.isFavorited = !item.isFavorited
  } catch (cause) { error.value = cause.message }
}
async function contact(item) {
  try {
    const session = await createOrGetChatSession(item.id, item.sellerId)
    router.push({ path:'/marketplace/chat', query:{ sessionId:session.sessionId || session.id, itemId:item.id } })
  } catch (cause) { error.value = cause.message }
}
async function reserve(item) {
  try { await reserveSecondhandItem(item.id); await openItem(item.id) } catch (cause) { error.value = cause.message }
}
async function publish() {
  publishing.value = true
  try {
    await createSecondhandItem({ ...form.value, price:Number(form.value.price), categoryId:Number(form.value.categoryId) })
    showPublish.value = false
    form.value = { title:'',description:'',price:'',categoryId:'',condition:'GOOD',tradeLocation:'',images:[] }
    tab.value = 'mine'
    await load()
  } catch (cause) { error.value = cause.message } finally { publishing.value = false }
}
async function tradeAction(record, action) {
  try {
    if (action === 'confirm') await confirmTradeRecord(record.id)
    if (action === 'complete') await completeTradeRecord(record.id)
    if (action === 'cancel') await cancelTradeRecord(record.id)
    await load()
  } catch (cause) { error.value = cause.message }
}
watch([tab,selectedCategory], load)
onMounted(async () => {
  try { categories.value = content(await getSecondhandCategories()) } catch { categories.value = [] }
  await load()
})
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <main class="feature-container">
      <header class="feature-heading">
        <div><h1>校园二手市集</h1><p>发现闲置、沟通确认并完成线下交易</p></div>
        <div class="feature-actions"><button class="feature-button" @click="router.push('/messages')">消息中心</button><button class="feature-button feature-button--primary" @click="showPublish=true">发布闲置</button></div>
      </header>
      <div class="market-toolbar feature-card">
        <div class="feature-tabs"><button v-for="[value,label] in tabs" :key="value" :class="{active:tab===value}" @click="tab=value">{{ label }}</button></div>
        <input v-if="tab==='market'" v-model="keyword" class="feature-input" placeholder="搜索商品" @keyup.enter="load" />
      </div>
      <div v-if="error" class="feature-error">{{ error }}</div>
      <div v-if="loading" class="feature-empty">正在加载市集数据…</div>
      <template v-else>
        <div v-if="['market','mine','favorites','history'].includes(tab)">
          <div v-if="tab==='market'" class="feature-chip-row categories"><button class="feature-chip" :class="{'feature-chip--active':!selectedCategory}" @click="selectedCategory=''">全部</button><button v-for="category in categories" :key="category.id" class="feature-chip" :class="{'feature-chip--active':String(selectedCategory)===String(category.id)}" @click="selectedCategory=category.id">{{ category.name }}</button></div>
          <div v-if="!(tab==='history'?historyItems:shownItems).length" class="feature-empty">暂无符合条件的商品</div>
          <div v-else class="market-grid">
            <article v-for="item in tab==='history'?historyItems:shownItems" :key="item.id" class="feature-card product-card" @click="openItem(item.id)">
              <div class="product-image"><img v-if="parseImages(item.images)[0]" :src="parseImages(item.images)[0]" alt="" /><span v-else></span><em>{{ item.statusText || '在售' }}</em></div>
              <div class="product-copy"><strong>{{ item.title }}</strong><p>{{ item.description || '卖家暂未填写描述' }}</p><div><b>{{ price(item.price) }}</b><span>{{ item.campusName || item.tradeLocation || '校内交易' }}</span></div></div>
            </article>
          </div>
        </div>
        <div v-if="tab==='chats'" class="feature-card feature-section">
          <div v-if="!sessions.length" class="feature-empty">暂无市集会话</div>
          <div class="feature-list"><button v-for="session in sessions" :key="session.sessionId" class="feature-row chat-row" @click="router.push({path:'/marketplace/chat',query:{sessionId:session.sessionId,itemId:session.itemId}})"><div class="avatar">{{ (session.otherUsername || '用').slice(0,1) }}</div><div class="feature-row__copy"><strong>{{ session.otherUsername || '校园用户' }} · {{ session.itemTitle }}</strong><span>{{ session.lastMessage || '暂无消息' }}</span></div><time>{{ session.lastTime }}</time></button></div>
        </div>
        <div v-if="tab==='trades'" class="feature-card feature-section">
          <div v-if="!trades.length" class="feature-empty">暂无交易记录</div>
          <div class="feature-list"><div v-for="record in trades" :key="record.id" class="feature-row"><div class="feature-row__copy"><strong>{{ record.itemTitle || `交易 ${record.id}` }}</strong><span>{{ record.buyerName }} · {{ record.sellerName }} · {{ record.createTime }}</span></div><span class="feature-status feature-status--learning">{{ record.statusText || record.status }}</span><div class="trade-actions"><button v-if="record.status==='WAIT_CONFIRM'" @click="tradeAction(record,'confirm')">确认</button><button v-if="record.status==='TRADING'" @click="tradeAction(record,'complete')">完成</button><button v-if="!['COMPLETED','CANCELLED'].includes(record.status)" @click="tradeAction(record,'cancel')">取消</button></div></div></div>
        </div>
        <div v-if="tab==='notifications'" class="feature-card feature-section">
          <div v-if="!notifications.length" class="feature-empty">暂无交易通知</div>
          <div class="feature-list"><div v-for="notice in notifications" :key="notice.id" class="feature-row"><div class="feature-row__copy"><strong>{{ notice.title || notice.eventType || '交易通知' }}</strong><span>{{ notice.content || notice.message }} · {{ notice.createTime }}</span></div><span v-if="!notice.isRead" class="feature-status feature-status--learning">未读</span></div></div>
        </div>
      </template>
    </main>
    <div v-if="selected" class="detail-mask" @click.self="selected=null">
      <aside class="market-detail">
        <button class="feature-modal__close" @click="selected=null">×</button>
        <div class="detail-gallery"><img v-if="parseImages(selected.images)[0]" :src="parseImages(selected.images)[0]" alt="" /><span v-else></span></div>
        <div class="detail-copy"><div class="detail-price">{{ price(selected.price) }}</div><h2>{{ selected.title }}</h2><p>{{ selected.description || '卖家暂未填写详细描述' }}</p><dl><div><dt>成色</dt><dd>{{ selected.conditionText || selected.condition || '待确认' }}</dd></div><div><dt>交易地点</dt><dd>{{ selected.tradeLocation || selected.pickupPoint || '待确认' }}</dd></div><div><dt>卖家</dt><dd>{{ selected.sellerName || '校园用户' }}</dd></div></dl><div class="detail-buttons"><button class="feature-button" @click="toggleFavorite(selected)">{{ selected.isFavorited ? '取消收藏' : '收藏' }}</button><button class="feature-button" @click="contact(selected)">联系卖家</button><button class="feature-button feature-button--primary" @click="reserve(selected)">预订商品</button></div></div>
      </aside>
    </div>
    <div v-if="showPublish" class="feature-modal-mask" @click.self="showPublish=false">
      <form class="feature-modal feature-form" @submit.prevent="publish">
        <div class="feature-modal__head"><h2>发布闲置</h2><button type="button" class="feature-modal__close" @click="showPublish=false">×</button></div>
        <label>商品标题<input v-model="form.title" class="feature-input" required maxlength="80" /></label>
        <label>商品描述<textarea v-model="form.description" class="feature-textarea" required maxlength="1000"></textarea></label>
        <label>价格<input v-model="form.price" class="feature-input" required type="number" min="0" step="0.01" /></label>
        <label>分类<select v-model="form.categoryId" class="feature-select" required><option value="">请选择分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <label>交易地点<input v-model="form.tradeLocation" class="feature-input" maxlength="100" /></label>
        <label>商品图片地址<input class="feature-input" placeholder="可填写已上传图片 URL" @change="form.images=$event.target.value?[String($event.target.value)]:[]" /></label>
        <button class="feature-button feature-button--primary" :disabled="publishing">{{ publishing?'发布中…':'确认发布' }}</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.market-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:12px;margin-bottom:18px}.market-toolbar .feature-input{width:280px}.categories{margin:0 0 18px}.market-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.product-card{overflow:hidden;cursor:pointer;transition:transform .16s}.product-card:hover{transform:translateY(-2px)}.product-image{position:relative;height:180px;background:#edf1f4}.product-image img{width:100%;height:100%;object-fit:cover}.product-image>span{display:block;width:45px;height:54px;margin:auto;transform:translateY(60px);border:2px solid #9caab8;border-radius:8px}.product-image em{position:absolute;top:10px;right:10px;padding:4px 8px;border-radius:5px;color:#52677c;background:rgba(255,255,255,.92);font-size:11px;font-style:normal}.product-copy{padding:16px}.product-copy>strong{display:block;color:#26384d;font-size:16px}.product-copy>p{height:40px;margin:7px 0 15px;overflow:hidden;color:#718096;font-size:13px;line-height:1.55}.product-copy>div{display:flex;align-items:end;justify-content:space-between}.product-copy b{color:#b45249;font-size:18px}.product-copy span{color:#8593a2;font-size:11px}.chat-row{width:100%;text-align:left}.avatar{display:grid;place-items:center;width:40px;height:40px;border-radius:50%;color:#315f8c;background:#e9f0f6;font-weight:800}.chat-row .feature-row__copy{flex:1}.chat-row time{color:#8996a3;font-size:11px}.detail-mask{position:fixed;inset:0;z-index:1100;background:rgba(15,23,42,.4)}.market-detail{position:absolute;inset:0 0 0 auto;width:min(620px,100%);padding:28px;overflow:auto;background:#fff}.market-detail>.feature-modal__close{position:absolute;z-index:2;top:18px;right:18px}.detail-gallery{height:330px;border-radius:10px;background:#eef2f5;overflow:hidden}.detail-gallery img{width:100%;height:100%;object-fit:cover}.detail-gallery span{display:block;width:70px;height:82px;margin:auto;transform:translateY(110px);border:3px solid #9eabb7;border-radius:10px}.detail-copy{padding:24px 4px}.detail-price{color:#b45249;font-size:25px;font-weight:800}.detail-copy h2{margin:8px 0}.detail-copy>p{color:#65758a;line-height:1.8}.detail-copy dl{margin:22px 0;border-block:1px solid #e7ecf1}.detail-copy dl div{display:flex;padding:13px 0}.detail-copy dt{width:90px;color:#8794a1}.detail-copy dd{margin:0;color:#344a60}.detail-buttons{display:flex;gap:10px}.detail-buttons .feature-button--primary{flex:1}@media(max-width:1100px){.market-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.market-grid{grid-template-columns:repeat(2,1fr)}.market-toolbar{align-items:stretch;flex-direction:column}.market-toolbar .feature-input{width:100%}}@media(max-width:500px){.market-grid{grid-template-columns:1fr}}
</style>
<style scoped>
.trade-actions{display:flex;gap:6px}
.trade-actions button{padding:6px 9px;border-radius:5px;color:#4b6379;background:#edf2f6}
</style>
