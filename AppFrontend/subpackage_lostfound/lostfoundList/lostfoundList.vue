<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
      <!-- 列表页 -->
      <view v-if="currentPage === 'list'" class="page page-list">
        <nav-bar title="校园集市" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <view class="search">
            <text class="search-icon">⌕</text>
            <input v-model="searchKeyword" class="search-input" placeholder="搜索闲置好物" placeholder-class="search-placeholder" />
          </view>

          <view class="filter-section">
            <view class="type-tabs">
              <view
                v-for="tab in typeTabs"
                :key="tab.value"
                class="type-tab"
                :class="{ on: currentType === tab.value }"
                :data-v="tab.value"
                @click="currentType = tab.value"
              >
                {{ tab.label }}
              </view>
            </view>

            <scroll-view scroll-x class="cats-scroll" show-scrollbar="false">
              <view class="cats">
                <view
                  v-for="cat in categories"
                  :key="cat.key"
                  class="cat"
                  :class="{ on: currentCat === cat.key }"
                  @click="currentCat = cat.key"
                >
                  {{ cat.label }}
                </view>
              </view>
            </scroll-view>
          </view>

          <view class="grid">
            <view v-if="filteredItems.length === 0" class="empty-block">
              <view class="empty-icon">📭</view>
              <view class="empty-text">暂无相关内容</view>
            </view>

            <view
              v-for="item in filteredItems"
              :key="item.id"
              class="gcard"
              :class="item.type"
              @click="showDetail(item.id)"
            >
              <view class="type-badge" :class="item.type">{{ item.type === 'want' ? '求' : '售' }}</view>
              <view class="gimg">
                <image v-if="item.images && item.images.length" class="gimg-src" :src="item.images[0]" mode="aspectFill" />
                <text v-else class="gimg-emoji">{{ emoji(item.id) }}</text>
              </view>
              <view class="gbody">
                <view class="gname">{{ item.name }}</view>
                <view v-if="item.type === 'sell'" class="gprice">
                  <small>¥</small>{{ item.price }}
                </view>
                <view v-else class="gprice gprice-empty"></view>
                <view class="guser">
                  <view class="gava">{{ item.userName.slice(0,1) }}</view>
                  <view class="guname">{{ item.userName }}</view>
                </view>
              </view>
            </view>
          </view>

          <view class="bottom-bar">
            <view class="bar-item" @click="goToMyItems">
              <text class="bar-icon">📝</text>
              <span>发布</span>
            </view>
            <view class="bar-post-wrap">
              <view class="bar-post" @click="goToPublish">
                <text>＋</text>
              </view>
            </view>
            <view class="bar-item" @click="goToMessages">
              <text class="bar-icon">💬</text>
              <span>消息</span>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 详情页 -->
      <view v-else-if="currentPage === 'detail'" class="page page-detail">
        <nav-bar title="详情" :fixed="true" :placeholder="true" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body">
          <view class="dimg">
            <image v-if="curItem.images && curItem.images.length" :src="curItem.images[imgIdx]" mode="aspectFill" class="dimg-src" />
            <text v-else class="dimg-emoji">{{ curItem.emoji || emoji(curItem.id) }}</text>
            <view v-if="curItem.images && curItem.images.length > 1" class="counter">{{ imgIdx + 1 }}/{{ curItem.images.length }}</view>
          </view>

          <view class="dinfo">
            <view v-if="curItem.type === 'sell'" class="dprice">
              <small>¥</small>{{ curItem.price }}
            </view>
            <view class="dtitle">{{ curItem.name }}</view>
            <view class="ddesc">{{ curItem.desc }}</view>
          </view>

          <view class="seller" @click="openChat">
            <view class="sava">{{ curItem.userName.slice(0,1) }}</view>
            <view class="sinfo">
              <view class="sname">{{ curItem.userName }}</view>
              <view class="stime">{{ fmt(curItem.ctime) }}</view>
            </view>
            <view class="sarrow">›</view>
          </view>
        </scroll-view>

        <view class="abar">
          <button class="abtn" @click="openChat">{{ curItem.type === 'want' ? '我有' : '我想要' }}</button>
        </view>
      </view>

      <!-- 发布页 -->
      <view v-else-if="currentPage === 'publish'" class="page page-publish">
        <nav-bar :title="publishType === 'sell' ? '发布闲置' : '发布求物'" :fixed="true" :placeholder="true" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body pub-body">
          <view class="fg">
            <view class="fl">类型</view>
            <view class="opts">
              <view class="opt" :class="{ on: publishType === 'sell' }" @click="publishType = 'sell'">🏷️ 出售闲置</view>
              <view class="opt" :class="{ on: publishType === 'want' }" @click="publishType = 'want'">🔍 求物</view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">商品图片</view>
            <view class="upload-list">
              <view
                v-for="(img, index) in publishForm.images"
                :key="index"
                class="upload-item preview-item"
              >
                <image class="upload-preview" :src="img" mode="aspectFill" @click="previewImg(img)" />
                <view class="upload-delete" @click.stop="removeImg(index)">×</view>
              </view>
              <view
                v-if="publishForm.images.length < 9"
                class="upload-item upload-add"
                @click="choosePublishImage"
              >
                <text class="upload-icon">🖼️</text>
                <text class="upload-text">添加图片</text>
              </view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">{{ publishType === 'sell' ? '商品名称' : '求物名称' }}</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.name" :placeholder="publishType === 'sell' ? '起个名字' : '想要什么'" />
            </view>
          </view>

          <view class="fg" v-if="publishType === 'sell'">
            <view class="fl">售价（元）</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.price" type="number" placeholder="输入售价" />
            </view>
          </view>

          <view class="fg">
            <view class="fl">{{ publishType === 'sell' ? '商品描述' : '求物描述' }}</view>
            <view class="input-wrap">
              <textarea class="ft" v-model="publishForm.desc" :placeholder="publishType === 'sell' ? '描述一下情况...' : '描述一下需求...'" />
            </view>
          </view>

          <view class="fg">
            <view class="fl">分类</view>
            <view class="opts">
              <view
                v-for="cat in categories.filter(c => c.key !== 'all')"
                :key="cat.key"
                class="opt"
                :class="{ on: publishForm.cat === cat.key }"
                @click="publishForm.cat = cat.key"
              >
                {{ cat.label }}
              </view>
            </view>
          </view>

          <view class="fg">
            <view class="fl">微信号</view>
            <view class="input-wrap">
              <input class="fi" v-model="publishForm.phone" placeholder="你的微信号" maxlength="30" />
            </view>
          </view>

          <button class="pbtn" @click="publish">发布{{ publishType === 'sell' ? '闲置' : '求物' }}</button>
        </scroll-view>
      </view>

      <!-- 聊天页 -->
      <view v-else-if="currentPage === 'chat'" class="page page-chat">
        <nav-bar :title="curChat ? curChat.otherName : '聊天'" :fixed="true" :placeholder="true" @back="go('pgDetail')" />

        <scroll-view scroll-y class="chat-body" :scroll-into-view="scrollBottom" scroll-with-animation @scroll="onChatScroll">
          <view v-for="m in chatMessages" :key="m.id" :id="'msg-' + m.id">
            <view v-if="m.type === 'sys'" class="msys">{{ m.content }}</view>
            <view v-else class="msg" :class="m.type">
              <view v-if="m.type === 's'" class="msg-content-s">
                <view class="msg-bubble-group">
                  <view class="mbub mbub-s">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-s">{{ new Date(m.time).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) }}</view>
                </view>
                <view class="mava mava-s">我</view>
              </view>
              <view v-else class="msg-content-r">
                <view class="mava mava-r">{{ curChat ? curChat.otherName[0] : '' }}</view>
                <view class="msg-bubble-group">
                  <view class="mbub mbub-r">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-r">{{ new Date(m.time).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) }}</view>
                </view>
              </view>
            </view>
          </view>

          <view v-if="exchangeStatus.status === 'none'" class="excard-new">
            <view class="excard-new-title">想要进一步沟通？</view>
            <view class="excard-new-desc">交换微信后可以更方便联系</view>
            <button class="excard-new-btn" @click="reqExchange">申请交换微信</button>
          </view>
          <view v-else-if="exchangeStatus.status === 'pending'" class="excard-new">
            <view class="excard-new-title">等待对方同意</view>
            <view class="excard-new-desc">对方同意后互相显示微信号</view>
            <button class="excard-new-btn" disabled>等待中...</button>
          </view>

          <!-- 流内卡片：只有不在底部时显示 -->
          <view
            v-if="exchangeStatus.status === 'done' && !isNearBottom"
            class="revealed-new revealed-flow"
          >
            <view class="rev-row-new">
              <view class="rev-ava-new">我</view>
              <view class="rev-icon-new">⇄</view>
              <view class="rev-ava-new them">{{ curChat ? curChat.otherName[0] : '' }}</view>
            </view>
            <view class="rev-phone-new">{{ curChat ? curChat.otherPhone : 'wx_******' }}</view>
            <view class="rev-label-new">对方微信号</view>
          </view>
        </scroll-view>

        <!-- 吸底卡片：只有在底部时显示 -->
        <view
          v-if="exchangeStatus.status === 'done' && isNearBottom"
          class="revealed-new revealed-fixed"
        >
          <view class="rev-row-new">
            <view class="rev-ava-new">我</view>
            <view class="rev-icon-new">⇄</view>
            <view class="rev-ava-new them">{{ curChat ? curChat.otherName[0] : '' }}</view>
          </view>
          <view class="rev-phone-new">{{ curChat ? curChat.otherPhone : 'wx_******' }}</view>
          <view class="rev-label-new">对方微信号</view>
        </view>

        <view class="chat-footer-new">
          <view class="chat-ex-btn-new" :class="{ 'exchanged': exchangeStatus.status === 'done' }" :disabled="exchangeStatus.status !== 'none'" @click="reqExchange">
            {{ exchangeStatus.status === 'none' ? '交换微信' : (exchangeStatus.status === 'pending' ? '等待同意...' : '已交换') }}
          </view>
          <view class="chat-input-bar">
            <view class="chat-input-icon">🖼️</view>
            <input v-model="messageInput" class="chat-input-new" placeholder="输入消息..." @confirm="sendMsg" />
            <view class="chat-send-btn" @click="sendMsg">
              <text>➤</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 我的发布 -->
      <view v-else-if="currentPage === 'myitems'" class="page page-myitems">
        <nav-bar title="我发布的" :fixed="true" :placeholder="true" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body">
          <view v-if="myItems.length === 0" class="empty">
            <view class="empty-i">📦</view>
            <view class="empty-t">还没有发布过</view>
          </view>
          <view v-for="item in myItems" :key="item.id" class="micard">
            <view class="miimg" @click="showDetail(item.id)">
              <text v-if="item.type === 'want'">🔍</text>
              <image v-else-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" />
              <text v-else>{{ emoji(item.id) }}</text>
            </view>
            <view class="mibody">
              <view class="miname">{{ item.name }}</view>
              <view v-if="item.type === 'sell'" class="miprice">
                <small>¥</small>{{ item.price }}
              </view>
              <view class="mitime">{{ fmt(item.ctime) }}发布</view>
            </view>
            <button
              class="micard-btn"
              :style="{ borderColor: item.status === 'online' ? '#5C8AB8' : '#6FBF73', color: item.status === 'online' ? '#5C8AB8' : '#6FBF73' }"
              @click="toggleStatus(item.id)"
            >
              {{ item.status === 'online' ? '下架' : '上架' }}
            </button>
          </view>
        </scroll-view>
      </view>

      <!-- 我的消息 -->
      <view v-else-if="currentPage === 'mymessages'" class="page page-mymessages">
        <nav-bar title="我的消息" :fixed="true" :placeholder="true" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body">
          <view v-if="chats.length === 0" class="empty">
            <view class="empty-i">💬</view>
            <view class="empty-t">暂无消息</view>
          </view>
          <view v-for="c in chats" :key="c.id" class="mscard" @click="openChatFromList(c.id)">
            <view class="msava">{{ c.otherName[0] }}</view>
            <view class="msbody">
              <view class="msname">{{ c.otherName }}</view>
              <view class="msprev">{{ c.lastMsg }}</view>
            </view>
            <view class="msmeta">
              <view class="mstime">{{ fmt(c.lastTime) }}</view>
              <view v-if="c.unread" class="msbadge">{{ c.unread }}</view>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- Toast -->
      <view v-if="toastText" class="toast show">{{ toastText }}</view>
      </view>
    </view>
    <ai-float-assistant />
  </view>
</template>

<script>
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'

const STORAGE_KEYS = {
  items: 'items',
  chats: 'chats',
  msgs: 'msgs',
  exStatus: 'exStatus'
}

const CATEGORIES = [
  { key: 'all', label: '全部' },
  { key: 'digital', label: '数码' },
  { key: 'book', label: '教材图书' },
  { key: 'daily', label: '生活日用' },
  { key: 'other', label: '其他' }
]

const TYPE_TABS = [
  { value: 'all', label: '全部' },
  { value: 'sell', label: '出售' },
  { value: 'want', label: '求物' }
]

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

function defaultItems() {
  return [
    { id: 1, name: 'iPhone 12 Pro 256G', desc: '用了两年，一直带壳贴膜，保护得很好。电池健康度85%，功能全部正常。', price: 2800, type: 'sell', status: 'online', cat: 'digital', images: [], userId: 'u1', userName: '小明', userPhone: 'wx_xiaoming2024', userAva: null, ctime: Date.now() - 7200000 },
    { id: 2, name: '《高等数学》同济第七版', desc: '上下册全套，有笔记但很整洁，考研必备。', price: 35, type: 'sell', status: 'online', cat: 'book', images: [], userId: 'me', userName: '我', userPhone: 'wx_mine_123', userAva: null, ctime: Date.now() - 172800000 },
    { id: 3, name: '求：二手自行车', desc: '校内代步用，能骑就行，预算200以内。', price: 200, type: 'want', status: 'online', cat: 'other', images: [], userId: 'u3', userName: '大一新生', userPhone: 'wx_freshman', userAva: null, ctime: Date.now() - 1800000 },
    { id: 4, name: '求：考研英语资料', desc: '需要26考研英语一全套资料，真题+解析。', price: 100, type: 'want', status: 'online', cat: 'book', images: [], userId: 'me', userName: '我', userPhone: 'wx_mine_123', userAva: null, ctime: Date.now() - 3600000 },
    { id: 5, name: '求：宿舍床上桌', desc: '考研需要，求一个稳固的床上桌，可自提。', price: 50, type: 'want', status: 'online', cat: 'other', images: [], userId: 'u5', userName: '躺平选手', userPhone: 'wx_tangping', userAva: null, ctime: Date.now() - 5400000 },
    { id: 6, name: '机械键盘 Cherry轴', desc: 'Cherry MX Red轴，87键，RGB背光，用了半年。', price: 320, type: 'sell', status: 'online', cat: 'digital', images: [], userId: 'u6', userName: '键盘侠', userPhone: 'wx_keyboard', userAva: null, ctime: Date.now() - 86400000 },
    { id: 7, name: '求：四级英语真题', desc: '需要近几年四级真题，带听力音频的。', price: 30, type: 'want', status: 'online', cat: 'book', images: [], userId: 'u7', userName: '四六级战士', userPhone: 'wx_cet4', userAva: null, ctime: Date.now() - 10800000 },
    { id: 8, name: '小米台灯1S', desc: '护眼台灯，三档调光，宿舍神器。', price: 65, type: 'sell', status: 'online', cat: 'digital', images: [], userId: 'u8', userName: '毕业清仓', userPhone: 'wx_grad2024', userAva: null, ctime: Date.now() - 14400000 },
    { id: 9, name: '求：健身卡转让', desc: '校内健身房月卡，还剩20天，价格好商量。', price: 80, type: 'want', status: 'online', cat: 'other', images: [], userId: 'u9', userName: '运动达人', userPhone: 'wx_fitness', userAva: null, ctime: Date.now() - 21600000 },
    { id: 10, name: '《计算机网络》谢希仁', desc: '第七版，有少量笔记，不影响阅读。', price: 25, type: 'sell', status: 'online', cat: 'book', images: [], userId: 'u10', userName: '学霸笔记', userPhone: 'wx_study', userAva: null, ctime: Date.now() - 28800000 }
  ]
}

export default {
  components: {
    AiFloatAssistant,
    NavBar
  },
  data() {
    return {
      categories: CATEGORIES,
      typeTabs: TYPE_TABS,
      currentPage: 'list',
      currentCat: 'all',
      currentType: 'all',
      searchKeyword: '',
      items: [],
      chats: [],
      msgs: {},
      exStatus: {},
      curItem: {},
      imgIdx: 0,
      curChat: null,
      messageInput: '',
      scrollBottom: '',
      toastText: '',
      publishType: 'sell',
      publishForm: {
        name: '',
        price: '',
        desc: '',
        cat: '',
        phone: '',
        images: []
      },
      isNearBottom: false
    }
  },
  computed: {
    filteredItems() {
      const keyword = this.searchKeyword.trim().toLowerCase()
      return this.items.filter(item => {
        if (item.status !== 'online') return false
        if (this.currentCat !== 'all' && item.cat !== this.currentCat) return false
        if (this.currentType !== 'all' && item.type !== this.currentType) return false
        if (!keyword) return true
        return item.name.toLowerCase().includes(keyword) || item.desc.toLowerCase().includes(keyword)
      })
    },
    myItems() {
      return this.items.filter(item => item.userId === 'me')
    },
    chatMessages() {
      if (!this.curChat) return []
      const list = this.msgs[this.curChat.id] || []
      return list
    },
    exchangeStatus() {
      if (!this.curChat) return { status: 'none' }
      return this.exStatus[this.curChat.id] || { status: 'none' }
    }
  },
  onLoad() {
    this.loadFromStorage()
    if (this.items.length === 0) {
      this.items = defaultItems()
      this.saveToStorage()
    }
  },
  methods: {
    onChatScroll(e) {
      const { scrollTop, scrollHeight, clientHeight } = e.detail
      const distanceToBottom = scrollHeight - scrollTop - clientHeight
      // 小于等于160认为在底部区域
      this.isNearBottom = distanceToBottom <= 160
    },
    updateCardPosition() {
      this.$nextTick(() => {
        uni.createSelectorQuery()
          .in(this)
          .select('.chat-body')
          .boundingClientRect()
          .select('.chat-body >>> .uni-scroll-view-content')
          .boundingClientRect()
          .exec((res) => {
            if (!res || res.length < 2 || !res[0] || !res[1]) return

            const bodyRect = res[0]
            const contentRect = res[1]

            // 内容没撑满：卡片应该跟随消息流
            if (contentRect.height <= bodyRect.height) {
              this.isNearBottom = false
            } else {
              // 内容撑满了，默认先按到底部处理
              this.isNearBottom = true
            }
          })
      })
    },
    choosePublishImage() {
      uni.chooseImage({
        count: 9 - this.publishForm.images.length,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          this.publishForm.images = [...this.publishForm.images, ...res.tempFilePaths]
        },
        fail: (err) => {
          console.log('选择图片失败', err)
        }
      })
    },
    removeImg(index) {
      this.publishForm.images.splice(index, 1)
    },
    previewImg(img) {
      uni.previewImage({
        urls: this.publishForm.images,
        current: img
      })
    },
    emoji(id) {
      return EMOJIS[id % EMOJIS.length]
    },
    fmt(ts) {
      if (!ts) return ''
      const diff = Date.now() - ts
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      const d = new Date(ts)
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    loadFromStorage() {
      try {
        this.items = uni.getStorageSync(STORAGE_KEYS.items) || []
        this.chats = uni.getStorageSync(STORAGE_KEYS.chats) || []
        this.msgs = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
        this.exStatus = uni.getStorageSync(STORAGE_KEYS.exStatus) || {}
      } catch (e) {
        console.error('加载数据失败', e)
      }
    },
    saveToStorage() {
      try {
        uni.setStorageSync(STORAGE_KEYS.items, this.items)
        uni.setStorageSync(STORAGE_KEYS.chats, this.chats)
        uni.setStorageSync(STORAGE_KEYS.msgs, this.msgs)
        uni.setStorageSync(STORAGE_KEYS.exStatus, this.exStatus)
      } catch (e) {
        console.error('保存数据失败', e)
      }
    },
    go(page) {
      const pageMap = {
        'pgList': 'list',
        'pgDetail': 'detail',
        'pgPub': 'publish',
        'pgChat': 'chat',
        'pgMyItems': 'myitems',
        'pgMyMsg': 'mymessages'
      }
      this.currentPage = pageMap[page] || 'list'
    },
    goToPublish() {
      uni.navigateTo({
        url: '/subpackage_lostfound/lostfoundPublish/lostfoundPublish'
      })
    },
    goToMyItems() {
      uni.navigateTo({
        url: '/subpackage_lostfound/myItems/myItems'
      })
    },
    goToMessages() {
      uni.navigateTo({
        url: '/subpackage_lostfound/myMessages/myMessages'
      })
    },
    showDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    },
    publish() {
      if (!this.publishForm.name.trim()) {
        this.showToast('请输入名称')
        return
      }
      const newItem = {
        id: Date.now(),
        name: this.publishForm.name,
        desc: this.publishForm.desc,
        price: this.publishType === 'sell' ? parseFloat(this.publishForm.price) || 0 : 0,
        type: this.publishType,
        status: 'online',
        cat: this.publishForm.cat || 'other',
        images: this.publishForm.images,
        userId: 'me',
        userName: '我',
        userPhone: this.publishForm.phone,
        userAva: null,
        ctime: Date.now()
      }
      this.items.unshift(newItem)
      this.saveToStorage()
      this.showToast('发布成功！')
      // 清空表单
      this.publishForm = {
        name: '',
        price: '',
        desc: '',
        cat: '',
        phone: '',
        images: []
      }
      setTimeout(() => this.go('pgList'), 800)
    },
    openChat() {
      if (!this.curItem) return
      let c = this.chats.find(x => x.itemId === this.curItem.id)

      if (!c) {
        const firstMsgId = Date.now()
        c = {
          id: 'c_' + firstMsgId,
          itemId: this.curItem.id,
          itemName: this.curItem.name,
          otherId: this.curItem.userId,
          otherName: this.curItem.userName,
          otherPhone: this.curItem.userPhone,
          otherAva: this.curItem.userAva,
          lastMsg: '你好',
          lastTime: firstMsgId,
          unread: 0
        }

        this.chats.unshift(c)
        this.msgs[c.id] = [{
          id: firstMsgId,
          type: 's',
          content: '你好',
          time: firstMsgId
        }]
        this.exStatus[c.id] = { status: 'none' }
        this.saveToStorage()
      }

      this.curChat = c
      this.go('pgChat')

      this.$nextTick(() => {
        const list = this.msgs[this.curChat.id] || []
        if (list.length) {
          this.scrollBottom = 'msg-' + list[list.length - 1].id
        }
        this.updateCardPosition()
      })
    },
    sendMsg() {
      const c = this.messageInput.trim()
      if (!c || !this.curChat) return

      const msgId = Date.now()

      this.msgs[this.curChat.id].push({
        id: msgId,
        type: 's',
        content: c,
        time: msgId
      })

      this.curChat.lastMsg = c
      this.curChat.lastTime = msgId
      this.messageInput = ''
      this.saveToStorage()
      this.scrollBottom = 'msg-' + msgId

      this.$nextTick(() => {
        this.updateCardPosition()
      })

      setTimeout(() => {
        const replyId = Date.now()

        this.msgs[this.curChat.id].push({
          id: replyId,
          type: 'r',
          content: '在的，可以聊聊～',
          time: replyId
        })

        this.curChat.lastMsg = '在的，可以聊聊～'
        this.curChat.lastTime = replyId
        this.saveToStorage()
        this.scrollBottom = 'msg-' + replyId

        this.$nextTick(() => {
          this.updateCardPosition()
        })
      }, 1500)
    },
    reqExchange() {
      if (!this.curChat) return
      const ex = this.exStatus[this.curChat.id] || { status: 'none' }
      if (ex.status !== 'none') return

      ex.status = 'pending'
      this.exStatus[this.curChat.id] = ex

      const sysId1 = Date.now()
      this.msgs[this.curChat.id].push({
        id: sysId1,
        type: 'sys',
        content: '你申请交换微信',
        time: sysId1
      })

      this.saveToStorage()
      this.scrollBottom = 'msg-' + sysId1
      this.showToast('已发送请求')

      setTimeout(() => {
        ex.status = 'done'
        this.exStatus[this.curChat.id] = ex

        const sysId2 = Date.now()
        this.msgs[this.curChat.id].push({
          id: sysId2,
          type: 'sys',
          content: '对方同意交换微信',
          time: sysId2
        })

        this.saveToStorage()
        this.scrollBottom = 'msg-' + sysId2
        this.showToast('已交换微信')

        this.$nextTick(() => {
          this.updateCardPosition()
        })
      }, 2500)
    },
    toggleStatus(id) {
      const item = this.items.find(i => i.id === id)
      if (item) {
        item.status = item.status === 'online' ? 'offline' : 'online'
        this.saveToStorage()
        this.showToast(item.status === 'online' ? '已上架' : '已下架')
      }
    },
    openChatFromList(id) {
      this.curChat = this.chats.find(c => c.id === id)
      if (!this.curChat) return
      this.curChat.unread = 0
      this.saveToStorage()
      this.go('pgChat')

      this.$nextTick(() => {
        const list = this.msgs[this.curChat.id] || []
        if (list.length) {
          this.scrollBottom = 'msg-' + list[list.length - 1].id
        }
        this.updateCardPosition()
      })
    },
    previewImg(src) {
      uni.previewImage({
        urls: [src]
      })
    },
    showToast(text) {
      this.toastText = text
      setTimeout(() => {
        this.toastText = ''
      }, 2000)
    }
  }
}
</script>

<style scoped>
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F0F5FA;
}

.screen {
  width: 100%;
  background: #F0F5FA;
  min-height: 100vh;
}

.container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: #E8F0F8;
  min-height: 100vh;
  position: relative;
}

.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-body {
  flex: 1;
  overflow-y: auto;
}

/* search */
.search {
  margin: 20rpx 24rpx;
  padding: 16rpx 24rpx;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
}

.search-icon {
  font-size: 36rpx;
  color: rgba(0, 0, 0, 0.4);
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  font-size: 26rpx;
}

.search-placeholder {
  color: rgba(0, 0, 0, 0.4);
}

/* filter */
.filter-section {
  padding: 0 24rpx 20rpx;
}

.type-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 20rpx;
  background: rgba(255, 255, 255, 0.4);
  padding: 8rpx;
  border-radius: 24rpx;
}

.type-tab {
  flex: 1;
  padding: 16rpx 0;
  border-radius: 18rpx;
  background: transparent;
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.4);
  text-align: center;
}

.type-tab.on {
  background: #fff;
  color: #5C7A99;
  box-shadow: 0 4rpx 12rpx rgba(92, 122, 153, 0.15);
}

.cats-scroll {
  white-space: nowrap;
}

.cats {
  display: flex;
  gap: 12rpx;
  padding: 4rpx 0;
}

.cat {
  padding: 12rpx 24rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.5);
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}

.cat.on {
  background: #5C7A99;
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(92, 122, 153, 0.25);
}

/* grid */
.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding: 0 24rpx 200rpx;
}

.gcard {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  position: relative;
}

.gcard.want {
  border: 3rpx solid rgba(90, 158, 111, 0.2);
}

.type-badge {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  padding: 6rpx 12rpx;
  border-radius: 12rpx;
  font-size: 20rpx;
  font-weight: 800;
  z-index: 2;
  color: #fff;
}

.type-badge.sell {
  background: rgba(92, 138, 184, 0.9);
}

.type-badge.want {
  background: rgba(90, 158, 111, 0.9);
}

.gimg {
  width: 100%;
  aspect-ratio: 1;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 96rpx;
  overflow: hidden;
}

.gimg-src {
  width: 100%;
  height: 100%;
}

.gbody {
  padding: 16rpx;
}

.gname {
  font-size: 26rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12rpx;
}

.gprice {
  font-size: 32rpx;
  font-weight: 800;
  color: #5C8AB8;
}

.gprice small {
  font-size: 22rpx;
  font-weight: 600;
}

.gprice-empty {
  height: 32rpx;
}

.guser {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 12rpx;
}

.gava {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16rpx;
  font-weight: 700;
}

.guname {
  font-size: 20rpx;
  color: rgba(0, 0, 0, 0.5);
}

/* bottom bar */
.bottom-bar {
  position: fixed;
  bottom: 60rpx;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(40rpx);
  border-radius: 999rpx;
  padding: 12rpx 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(0, 0, 0, 0.08);
  z-index: 60;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 8rpx 12rpx;
  border-radius: 12rpx;
  color: rgba(0, 0, 0, 0.5);
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.3;
  text-align: center;
  min-width: 80rpx;
}

.bar-icon {
  font-size: 36rpx;
  margin-bottom: 4rpx;
  opacity: 0.6;
}

.bar-post-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 8rpx;
}

.bar-post {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 20rpx rgba(123, 168, 212, 0.45);
  font-size: 44rpx;
  font-weight: 700;
}

/* detail */
.dimg {
  width: 100%;
  aspect-ratio: 3 / 2;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 160rpx;
  overflow: hidden;
  position: relative;
}

.dimg-src {
  width: 100%;
  height: 100%;
}

.counter {
  position: absolute;
  bottom: 24rpx;
  right: 24rpx;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.dinfo {
  padding: 32rpx;
}

.dprice {
  font-size: 56rpx;
  font-weight: 900;
  color: #5C8AB8;
  margin-bottom: 12rpx;
}

.dprice small {
  font-size: 32rpx;
  font-weight: 600;
}

.dtitle {
  font-size: 36rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 24rpx;
  line-height: 1.4;
}

.ddesc {
  font-size: 30rpx;
  color: rgba(0, 0, 0, 0.5);
  line-height: 1.7;
  margin-bottom: 32rpx;
}

.seller {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  margin: 0 32rpx 32rpx;
}

.sava {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.sinfo {
  flex: 1;
}

.sname {
  font-size: 30rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.stime {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
  margin-top: 4rpx;
}

.sarrow {
  font-size: 28rpx;
  color: rgba(0, 0, 0, 0.2);
}

.abar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 32rpx 56rpx;
  background: linear-gradient(to top, rgba(232, 240, 248, 1) 0%, rgba(232, 240, 248, 0.98) 100%);
  border-top: 2rpx solid rgba(0, 0, 0, 0.06);
  z-index: 50;
}

.abtn {
  height: 88rpx;
  border-radius: 24rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(123, 168, 212, 0.6);
}

/* publish */
.pub-body {
  padding: 32rpx 0;
}

.fg {
  margin-bottom: 32rpx;
}

.fl {
  font-size: 26rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.7);
  margin-bottom: 12rpx;
}

.opts {
  display: flex;
  gap: 12rpx;
  flex-wrap: nowrap;
  justify-content: flex-start;
}

.opt {
  padding: 14rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.6);
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.5);
  text-align: center;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.opt.on {
  background: #7ba8d4;
  color: #fff;
}

.fi {
  width: 100%;
  padding: 20rpx 24rpx;
  border-radius: 0;
  background: transparent;
  font-size: 26rpx;
}

.ft {
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  border-radius: 0;
  background: transparent;
  font-size: 26rpx;
}

.input-wrap {
  border-radius: 24rpx;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.8);
}

.pbtn {
  width: 100%;
  height: 88rpx;
  border-radius: 24rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  border: none;
  margin-top: 40rpx;
}

/* 图片上传 */
.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  width: 144rpx;
  height: 144rpx;
  border-radius: 24rpx;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

.upload-add {
  border: 2rpx dashed rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.42);
}

.upload-icon {
  font-size: 44rpx;
  margin-bottom: 8rpx;
}

.upload-text {
  font-size: 24rpx;
}

.upload-preview {
  width: 100%;
  height: 100%;
  display: block;
}

.upload-delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 24rpx;
  line-height: 36rpx;
  text-align: center;
  z-index: 2;
}

/* chat */
.chat-body {
  height: calc(100vh - 240rpx);
  padding: 32rpx 0 0rpx;
  box-sizing: border-box;
}

.msg {
  margin-bottom: 32rpx;
  display: flex;
}

.msg-content-s {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 16rpx;
  margin-left: auto;
  justify-content: flex-end;
  padding-right: 64rpx;
}

.msg-content-r {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-right: auto;
}

.msg-bubble-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 520rpx;
}

.msg-content-s .msg-bubble-group {
  align-items: flex-end;
}

.mava {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.mava-r {
  background: #7ba8d4;
}

.mava-s {
  background: #7ba8d4;
}

.mbub {
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  max-width: 100%;
  word-break: break-all;
  line-height: 1.5;
}

.mbub-s {
  background: #7ba8d4;
  color: #fff;
}

.mbub-r {
  background: #fff;
  color: rgba(0, 0, 0, 0.85);
}

.mtime {
  font-size: 20rpx;
  color: rgba(0, 0, 0, 0.35);
  margin-top: 8rpx;
  padding: 0 4rpx;
}

.msys {
  text-align: center;
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.4);
  margin: 32rpx 0;
}

.excard-new {
  margin: 32rpx 0;
  padding: 40rpx;
  background: rgba(123, 168, 212, 0.1);
  border-radius: 24rpx;
  text-align: center;
}

.excard-new-title {
  font-size: 30rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 12rpx;
}

.excard-new-desc {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
  margin-bottom: 24rpx;
}

.excard-new-btn {
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
}

.revealed-new {
  padding: 40rpx 32rpx;
  background: #fff;
  border-radius: 24rpx;
  text-align: center;
  width: calc(100% - 96rpx);
  max-width: 500rpx;
  box-sizing: border-box;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);
}

.revealed-flow {
  position: relative;
  margin: 20rpx auto 0;
}

.revealed-fixed {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 24rpx;
  z-index: 9;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.08);
}

.rev-row-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  margin-bottom: 28rpx;
}

.rev-ava-new {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}

.rev-ava-new.them {
  background: rgba(0, 0, 0, 0.5);
}

.rev-icon-new {
  font-size: 36rpx;
  color: #7ba8d4;
}

.rev-phone-new {
  font-size: 36rpx;
  font-weight: 800;
  color: #5c8ab8;
  margin-bottom: 8rpx;
}

.rev-label-new {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
}

.chat-footer-new {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(232, 240, 248, 0.98);
  padding: 16rpx 32rpx 24rpx;
  border-top: none;
  z-index: 10;
}

.chat-ex-btn-new {
  position: absolute;
  top: -68rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 12rpx 32rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.95);
  font-size: 24rpx;
  font-weight: 700;
  color: #7ba8d4;
  border: 2rpx solid rgba(123, 168, 212, 0.3);
  text-align: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10rpx);
  z-index: 10;
  white-space: nowrap;
}

.chat-ex-btn-new.exchanged {
  color: rgba(123, 168, 212, 0.5);
  border-color: rgba(123, 168, 212, 0.15);
  background: rgba(255, 255, 255, 0.6);
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 999rpx;
  padding: 8rpx 8rpx 8rpx 24rpx;
}

.chat-input-icon {
  font-size: 32rpx;
  opacity: 0.5;
}

.chat-input-new {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
  background: transparent;
}

.chat-send-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  flex-shrink: 0;
}

/* my items */
.micard {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  margin: 24rpx 32rpx;
  background: #fff;
  border-radius: 20rpx;
  position: relative;
}

.miimg {
  width: 120rpx;
  height: 120rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  overflow: hidden;
  flex-shrink: 0;
}

.miimg image {
  width: 100%;
  height: 100%;
}

.mibody {
  flex: 1;
}

.miname {
  font-size: 28rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 8rpx;
}

.miprice {
  font-size: 32rpx;
  font-weight: 800;
  color: #5C8AB8;
}

.miprice small {
  font-size: 22rpx;
}

.mitime {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.5);
  margin-top: 8rpx;
}

.micard-btn {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  border: 2rpx solid;
  background: transparent;
  font-size: 22rpx;
  font-weight: 700;
  white-space: nowrap;
  min-width: 110rpx;
  text-align: center;
  line-height: 1.2;
}

/* my messages */
.mscard {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  margin: 24rpx 32rpx;
  background: #fff;
  border-radius: 20rpx;
}

.msava {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.msbody {
  flex: 1;
}

.msname {
  font-size: 30rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 8rpx;
}

.msprev {
  font-size: 26rpx;
  color: rgba(0, 0, 0, 0.5);
}

.msmeta {
  text-align: right;
}

.mstime {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.4);
  margin-bottom: 8rpx;
}

.msbadge {
  display: inline-block;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #5C8AB8;
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
}

/* empty */
.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-i {
  font-size: 120rpx;
  margin-bottom: 24rpx;
  opacity: 0.3;
}

.empty-t {
  font-size: 28rpx;
  color: rgba(0, 0, 0, 0.4);
}

/* toast */
.toast {
  position: fixed;
  top: 120rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 20rpx 44rpx;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 600;
  z-index: 9999;
  opacity: 0;
  transition: opacity 0.3s;
}

.toast.show {
  opacity: 1;
}

.empty-block {
  grid-column: 1 / -1;
  padding: 100rpx 0;
  text-align: center;
}

.empty-block .empty-icon {
  font-size: 120rpx;
  margin-bottom: 24rpx;
  opacity: 0.3;
}

.empty-block .empty-text {
  font-size: 28rpx;
  color: rgba(0, 0, 0, 0.4);
}
</style>
