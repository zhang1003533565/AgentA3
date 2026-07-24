<template>
  <view class="page-root" :class="{ 'page-root--market-list': currentPage === 'list' }">
    <view class="screen">
      <view class="container">
      <!-- 列表页 -->
      <view v-if="currentPage === 'list'" class="page page-list">
        <view class="page-content">
          <common-page-header title="校园集市" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="onBackToApp" />

          <view class="market-hero">
            <view class="market-list-search-row" :class="{ 'market-list-search-row--transitioning': searchTransitioning }">
              <view class="market-list-search" @click="goToSearch">
                <view class="market-search-pill">
                  <image class="market-search-pill-icon" src="/static/icons/search.svg" mode="aspectFit" />
                  <input class="market-search-pill-input" value="搜索商品、书籍、用品等" disabled />
                </view>
              </view>
              <view class="search-filter-btn" @click.stop="openFilter">
                <image class="search-filter-icon" src="/static/icons/mage-filter-fill.svg" mode="aspectFit" />
                <view v-if="hasActiveFilter" class="search-filter-dot"></view>
              </view>
            </view>
          </view>

          <view class="category-shell" :class="{ 'category-shell--expanded': categoryExpanded }">
            <view class="category-summary" @click="toggleCategoryPanel">
              <view class="category-summary-main">
                <text class="category-summary-title">分类浏览</text>
              </view>
              <view class="category-summary-action">
                <text>{{ categoryExpanded ? '收起' : '展开' }}</text>
                <view
                  class="category-summary-arrow"
                  :class="{ 'category-summary-arrow--expanded': categoryExpanded }"
                ></view>
              </view>
            </view>
            <view class="category-collapse" :class="{ 'category-collapse--expanded': categoryExpanded }">
              <view class="category-collapse-inner">
                <view class="cat-grid">
                  <view
                    v-for="cat in marketCategoryTabs"
                    :key="cat.key"
                    class="cat-item"
                    :class="{ on: currentCat === cat.key || (cat.activeKey && currentCat === cat.activeKey) }"
                    @click="selectMarketCategory(cat)"
                  >
                    <view class="cat-icon-wrap">
                      <image v-if="cat.icon" class="cat-icon-img" :src="cat.icon" mode="aspectFit" />
                      <text v-else class="cat-icon-text">{{ cat.label.slice(0, 1) }}</text>
                    </view>
                    <text class="cat-label">{{ cat.label }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>

          <view class="sort-bar">
            <view
              v-for="s in sortOptions"
              :key="s.value"
              class="sort-tab"
              :class="{ on: sortBy === s.value }"
              @click="sortBy = s.value"
            >
              {{ s.label }}
            </view>
            <view class="sort-spacer"></view>
            <view class="sort-filter" :class="{ on: hasActiveFilter }" @click="openFilter">
              <text>{{ hasActiveFilter ? '已筛选' : '筛选' }}</text>
              <image class="sort-filter-icon" src="/static/icons/mage-filter-fill.svg" mode="aspectFit" />
            </view>
          </view>

          <scroll-view scroll-y class="page-body market-list-scroll">
            <view class="product-grid">
              <view v-if="filteredItems.length === 0" class="empty-block">
                <view class="empty-icon"></view>
                <text class="empty-title">暂无商品</text>
                <text class="empty-sub">发布第一个闲置吧</text>
              </view>

              <view
                v-for="item in filteredItems"
                :key="item.id"
                class="product-card"
                @click="showDetail(item.id)"
              >
                <view class="product-img">
                  <image v-if="item.images && item.images.length" class="product-img-src" :src="item.images[0]" mode="aspectFill" />
                  <view v-else class="product-img-placeholder">
                    <text class="product-img-emoji">{{ emoji(item.id) }}</text>
                    <text class="product-img-placeholder-text">{{ itemCategoryLabel(item) }}</text>
                  </view>
                  <text class="product-status-badge" :class="'product-status-badge--' + item.status">{{ item.statusText }}</text>
                </view>
                <view class="product-body">
                  <text class="product-name">{{ item.name }}</text>
                  <text class="product-desc">{{ item.categoryLevel2Name || item.categoryName || itemCategoryLabel(item) }}</text>
                  <view class="product-price-line">
                    <view class="product-price-row">
                      <text v-if="priceDisplay(item).prefix" class="product-price-symbol">{{ priceDisplay(item).prefix }}</text>
                      <text class="product-price-num" :class="{ 'product-price-text': !priceDisplay(item).prefix }">{{ priceDisplay(item).text }}</text>
                    </view>
                    <text class="product-info-chip">{{ itemConditionLabel(item) }}</text>
                  </view>
                  <view class="product-location-row">
                    <image class="product-location-icon" src="/static/icons/mi-location.svg" mode="aspectFit" />
                    <text class="product-location">{{ itemLocationLabel(item) }}</text>
                  </view>
                  <view class="product-user">
                    <view class="product-ava">{{ item.userName ? item.userName.slice(0,1) : '同' }}</view>
                    <text class="product-uname">{{ item.userName }}</text>
                    <text class="product-time">{{ fmt(item.ctime) }}</text>
                  </view>
                </view>
              </view>
            </view>
          </scroll-view>

          <view v-if="filterVisible" class="filter-overlay" @click="closeFilter">
            <view class="filter-panel" @click.stop>
              <view class="filter-handle"></view>
              <view class="filter-header">
                <text class="filter-title">筛选</text>
                <text class="filter-close" @click="closeFilter">✕</text>
              </view>

              <view v-if="selectedFilterSummaries.length" class="selected-filter-strip">
                <view
                  v-for="item in selectedFilterSummaries"
                  :key="item.key"
                  class="selected-filter-chip"
                >{{ item.label }}</view>
              </view>

              <scroll-view scroll-y class="filter-body">
                <view class="filter-group">
                  <view class="filter-group-title">商品分类</view>
                  <view class="filter-options">
                    <view
                      v-for="cat in categories"
                      :key="'filter-cat-' + cat.key"
                      class="filter-opt"
                      :class="{ on: filterForm.categoryLevel1Id === cat.key }"
                      @click="selectFilterCategoryLevel1(cat.key)"
                    >{{ cat.label }}</view>
                  </view>
                </view>

                <view v-if="currentFilterCategoryChildren.length" class="filter-group">
                  <view class="filter-group-title">细分分类</view>
                  <view class="filter-options">
                    <view
                      class="filter-opt"
                      :class="{ on: !filterForm.categoryLevel2Id }"
                      @click="filterForm.categoryLevel2Id = ''"
                    >全部</view>
                    <view
                      v-for="cat in currentFilterCategoryChildren"
                      :key="'filter-sub-cat-' + cat.key"
                      class="filter-opt"
                      :class="{ on: filterForm.categoryLevel2Id === cat.key }"
                      @click="filterForm.categoryLevel2Id = cat.key"
                    >{{ cat.label }}</view>
                  </view>
                </view>

                <view class="filter-group">
                  <view class="filter-group-title">价格区间</view>
                  <view class="filter-options">
                    <view
                      v-for="o in FILTER_PRICE_OPTIONS"
                      :key="o.value"
                      class="filter-opt"
                      :class="{ on: filterForm.priceRange === o.value }"
                      @click="selectFilterPrice(o.value)"
                    >{{ o.label }}</view>
                  </view>
                  <view class="filter-price-custom">
                    <input
                      v-model="filterForm.customPriceMin"
                      class="filter-price-input"
                      type="number"
                      placeholder="￥ 最低价"
                      placeholder-class="filter-price-placeholder"
                    />
                    <text class="filter-price-separator">-</text>
                    <input
                      v-model="filterForm.customPriceMax"
                      class="filter-price-input"
                      type="number"
                      placeholder="￥ 最高价"
                      placeholder-class="filter-price-placeholder"
                    />
                  </view>
                </view>

                <view class="filter-group">
                  <view class="filter-group-title">发布时间</view>
                  <view class="filter-options">
                    <view
                      v-for="o in FILTER_TIME_OPTIONS"
                      :key="o.value"
                      class="filter-opt"
                      :class="{ on: filterForm.publishTime === o.value }"
                      @click="filterForm.publishTime = o.value"
                    >{{ o.label }}</view>
                  </view>
                </view>

                <view class="filter-group">
                  <view class="filter-group-title">商品状态</view>
                  <view class="filter-options">
                    <view
                      v-for="o in FILTER_CONDITION_OPTIONS"
                      :key="o.value"
                      class="filter-opt"
                      :class="{ on: filterForm.condition === o.value }"
                      @click="filterForm.condition = o.value"
                    >{{ o.label }}</view>
                  </view>
                </view>

                <view class="filter-group">
                  <view class="filter-group-title">交易位置</view>
                  <view class="filter-options">
                    <view
                      v-for="o in FILTER_LOCATION_OPTIONS"
                      :key="o.value"
                      class="filter-opt"
                      :class="{ on: filterForm.location === o.value }"
                      @click="filterForm.location = o.value"
                    >{{ o.label }}</view>
                  </view>
                </view>

                <view v-if="currentAttributeFilters.length" class="filter-group filter-attribute-group">
                  <view class="filter-group-title">{{ currentAttributeFilterTitle }}</view>
                  <view
                    v-for="group in currentAttributeFilters"
                    :key="group.key"
                    class="attribute-row"
                  >
                    <view class="attribute-title">{{ group.label }}</view>
                    <view class="filter-options">
                      <view
                        v-for="o in group.options"
                        :key="group.key + '-' + String(o.value)"
                        class="filter-opt"
                        :class="{ on: isAttributeSelected(group.key, o.value) }"
                        @click="toggleAttributeFilter(group.key, o.value)"
                      >{{ o.label }}</view>
                    </view>
                  </view>
                </view>
              </scroll-view>

              <view class="filter-footer">
                <view class="filter-btn reset" @click="resetFilter">重置</view>
                <view class="filter-btn confirm" @click="confirmFilter">确认筛选</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 详情页 -->
      <view v-else-if="currentPage === 'detail'" class="page page-detail">
        <common-page-header title="详情" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="go('pgList')" />

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
            <view class="sava">{{ curItem.userName ? curItem.userName.slice(0,1) : '同' }}</view>
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

      <!-- 聊天 -->
      <view v-else-if="currentPage === 'chat'" class="page page-chat">
        <common-page-header :title="curChat ? curChat.otherName : '聊天'" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="go('pgDetail')" />

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
        <common-page-header title="我发布的" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body">
          <view v-if="myItems.length === 0" class="empty">
            <view class="empty-i"></view>
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
              :style="{ borderColor: item.status === 'online' ? '#6F98D0' : '#6FBF73', color: item.status === 'online' ? '#6F98D0' : '#6FBF73' }"
              @click="toggleStatus(item.id)"
            >
              {{ item.status === 'online' ? '下架' : '上架' }}
            </button>
          </view>
        </scroll-view>
      </view>

      <!-- 我的消息 -->
      <view v-else-if="currentPage === 'mymessages'" class="page page-mymessages">
        <common-page-header title="我的消息" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="go('pgList')" />

        <scroll-view scroll-y class="page-body">
          <view v-if="chats.length === 0" class="empty">
            <image class="empty-icon-msg" src="/static/icons/message-empty.svg" mode="aspectFit" />
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

      <view
        class="search-transition-mask"
        :class="{ 'search-transition-mask--active': searchTransitioning }"
        :style="searchTransitionStyle"
      >
        <view class="search-transition-top-panel"></view>
        <view class="search-transition-surface" @transitionend="onSearchTransitionEnd"></view>
        <view class="search-transition-bar">
          <image class="search-transition-icon" src="/static/icons/search.svg" mode="aspectFit" />
          <input class="search-transition-input" value="搜索商品、书籍、用品等" disabled />
        </view>
      </view>

      <market-bottom-bar activeTab="market" v-show="currentPage === 'list'" />
      </view>
    </view>
    <ai-float-assistant />
  </view>
</template>

<script>
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getSecondhandItemList } from '@/api/secondhand'
import { createDefaultMarketFilter, filterMarketItems } from '@/subpackage_lostfound/utils/marketFilter.js'
import { formatLocationText } from '@/subpackage_lostfound/utils/campusLocation.js'
import { createMarketCategoryOptions, getMarketCategoryChildren, getMarketCategoryLabel } from '@/subpackage_lostfound/utils/marketCategories.js'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

const TYPE_TABS = [
  { value: 'all', label: '全部' },
  { value: 'sell', label: '出售' }
]

const CATEGORIES = createMarketCategoryOptions()

const SORT_OPTIONS = [
  { value: 'latest', label: '最新发布' },
  { value: 'hot',    label: '热门' }
]

const SCENE_TAGS = [
  { key: 'graduate', label: '毕业急出', sub: '低价好物', keyword: '急出' },
  { key: 'free', label: '免费低价', sub: '50元内', filter: { priceRange: '0-50' } },
  { key: 'book', label: '考研教材', sub: '资料真题', category: 'textbook' },
  { key: 'dorm', label: '同楼自提', sub: '宿舍区', filter: { location: 'dorm' } }
]

const FILTER_PRICE_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: '0-50', label: '0-50元' },
  { value: '50-200', label: '50-200元' },
  { value: '200+', label: '200元以上' }
]

const FILTER_TIME_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'today', label: '今天' },
  { value: '3days', label: '最近三天' },
  { value: 'week', label: '最近一周' }
]

const FILTER_CONDITION_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'new', label: '全新' },
  { value: 'like-new', label: '九成新' },
  { value: 'used', label: '二手' }
]

const FILTER_LOCATION_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'campus', label: '校内' },
  { value: 'dorm', label: '宿舍区' },
  { value: 'nearby', label: '附近' }
]

const ATTRIBUTE_FILTERS = {
  digital: [
    { key: 'brand', label: '品牌', options: [{ value: 'Apple', label: 'Apple' }, { value: 'Lenovo', label: 'Lenovo' }, { value: 'Keychron', label: 'Keychron' }, { value: 'Sony', label: 'Sony' }] },
    { key: 'model', label: '型号', options: [{ value: 'iPad Air 5', label: 'iPad Air 5' }, { value: 'K2', label: 'K2' }, { value: 'ThinkPad', label: 'ThinkPad' }, { value: 'AirPods', label: 'AirPods' }] },
    { key: 'storage', label: '存储', options: [{ value: '64G', label: '64G' }, { value: '128G', label: '128G' }, { value: '256G', label: '256G' }, { value: '512G', label: '512G' }] },
    { key: 'color', label: '颜色', options: [{ value: '黑色', label: '黑色' }, { value: '白色', label: '白色' }, { value: '蓝色', label: '蓝色' }, { value: '灰色', label: '灰色' }] }
  ],
  textbook: [
    { key: 'subject', label: '科目', options: [{ value: '数学', label: '数学' }, { value: '英语', label: '英语' }, { value: '计算机', label: '计算机' }, { value: '专业课', label: '专业课' }] },
    { key: 'grade', label: '年级', options: [{ value: '大一', label: '大一' }, { value: '大二', label: '大二' }, { value: '大三', label: '大三' }, { value: '考研', label: '考研' }] },
    { key: 'edition', label: '版本', options: [{ value: '第七版', label: '第七版' }, { value: '2025新版', label: '2025新版' }, { value: '最新版', label: '最新版' }] },
    { key: 'hasNotes', label: '笔记', options: [{ value: true, label: '有笔记' }, { value: false, label: '无笔记' }] }
  ],
  dorm: [
    { key: 'type', label: '类型', options: [{ value: '小电器', label: '小电器' }, { value: '收纳', label: '收纳' }, { value: '床品', label: '床品' }, { value: '桌椅', label: '桌椅' }] },
    { key: 'condition', label: '状态', options: [{ value: 'new', label: '全新' }, { value: 'like_new', label: '九成新' }, { value: 'used', label: '正常使用' }] }
  ],
  clothing: [
    { key: 'gender', label: '性别', options: [{ value: '女款', label: '女款' }, { value: '男款', label: '男款' }, { value: '通用', label: '通用' }] },
    { key: 'size', label: '尺码', options: [{ value: 'S', label: 'S' }, { value: 'M', label: 'M' }, { value: 'L', label: 'L' }, { value: 'XL', label: 'XL' }] },
    { key: 'style', label: '风格', options: [{ value: '学院风', label: '学院风' }, { value: '运动', label: '运动' }, { value: '通勤', label: '通勤' }, { value: '休闲', label: '休闲' }] }
  ],
  game: [
    { key: 'platform', label: '平台', options: [{ value: 'Switch', label: 'Switch' }, { value: 'PS5', label: 'PS5' }, { value: 'Xbox', label: 'Xbox' }, { value: 'PC', label: 'PC' }] },
    { key: 'type', label: '类型', options: [{ value: '主机', label: '主机' }, { value: '手柄', label: '手柄' }, { value: '卡带', label: '卡带' }, { value: '配件', label: '配件' }] }
  ]
}



function formatTimestamp(value) {
  if (!value) return ''
  return value.replace('T', ' ')
}

function normalizeItem(item) {
  const seller = item.seller || {}
  const categoryId = item.categoryId ?? item.categoryLevel2Id ?? item.categoryLevel1Id ?? 'other'
  const categoryName = item.categoryName || item.categoryLevel2Name || item.categoryLevel1Name || ''
  const condition = item.condition || item.itemCondition || ''
  const location = item.location || item.tradeLocationText || ''
  const schoolName = item.schoolName || seller.schoolName || ''

  return {
    id: item.id,
    name: item.title,
    desc: item.description || '',
    price: item.price,
    originalPrice: item.originalPrice || item.original_price || null,
    type: 'sell',
    status: item.status === 4 ? 'offline' : item.status === 3 ? 'sold' : 'online',
    statusText: item.statusText || (item.status === 3 ? '已售出' : item.status === 4 ? '已下架' : '在售'),
    cat: String(categoryId),
    images: Array.isArray(item.images) ? item.images : [],
    userId: item.userId,
    userName: seller.username || '用户',
    userPhone: seller.phone || '',
    userAva: seller.avatar || '',
    ctime: formatTimestamp(item.createTime),
    rawStatus: item.status,
    categoryId: item.categoryId || categoryId,
    categoryName,
    categoryLevel1Id: item.categoryLevel1Id || item.categoryParentId || item.categoryId || '',
    categoryLevel1Name: item.categoryLevel1Name || item.categoryParentName || item.categoryName || '',
    categoryLevel2Id: item.categoryLevel2Id || item.categoryId || '',
    categoryLevel2Name: item.categoryLevel2Name || item.categoryName || '',
    condition,
    conditionText: item.conditionText || item.conditionName || '',
    location,
    tradeLocation: item.tradeLocation || item.trade_location || location,
    campusId: item.campusId || '',
    campusName: item.campusName || '',
    schoolId: item.schoolId || seller.schoolId || '',
    schoolName,
    college: seller.college || item.college || '',
    dormitoryArea: item.dormitoryArea || '',
    allowBargain: Boolean(item.allowBargain ?? item.allow_bargain ?? false),
    deliveryMethod: item.deliveryMethod || item.delivery_method || 'pickup',
    isFree: Boolean(item.isFree ?? Number(item.price) === 0),
    urgency: item.urgency || 'normal',
    viewCount: Number(item.viewCount || item.view_count || 0),
    favoriteCount: Number(item.favoriteCount || item.favorite_count || 0),
    distanceText: item.distanceText || '',
    distanceValue: item.distanceValue || null,
    pickupPoint: item.pickupPoint || item.pickup_point || '',
    attributes: item.attributes || {}
  }
}

export default {
  components: {
    AiFloatAssistant,
    CommonPageHeader,
    MarketBottomBar
  },
  data() {
    return {
      categories: CATEGORIES,
      typeTabs: TYPE_TABS,
      sortOptions: SORT_OPTIONS,
      sceneTags: SCENE_TAGS,
      currentPage: 'list',
      currentTab: 'market',
      currentCat: 'all',
      categoryExpanded: false,
      currentType: 'all',
      currentSceneTag: '',
      sortBy: 'latest',
      searchKeyword: '',
      searchTransitioning: false,
      searchTransitionNavigating: false,
      searchTransitionRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      },
      items: [],
      curItem: {},
      imgIdx: 0,
      curChat: null,
      messageInput: '',
      scrollBottom: '',
      toastText: '',
      isNearBottom: false,
      pageLoading: false,
      filterVisible: false,
      filterForm: {
        categoryLevel1Id: 'all',
        categoryLevel2Id: '',
        priceRange: 'all',
        customPriceMin: '',
        customPriceMax: '',
        publishTime: 'all',
        condition: 'all',
        location: 'all',
        attributes: {}
      },
      activeFilterForm: {
        categoryLevel2Id: '',
        priceRange: 'all',
        publishTime: 'all',
        condition: 'all',
        location: 'all',
        attributes: {}
      },
      filterQuery: createDefaultMarketFilter(),
      FILTER_PRICE_OPTIONS,
      FILTER_TIME_OPTIONS,
      FILTER_CONDITION_OPTIONS,
      FILTER_LOCATION_OPTIONS
    }
  },
  computed: {
    marketCategoryTabs() {
      const findCategory = (key) => this.categories.find((cat) => String(cat.key) === String(key)) || {}
      return [
        { key: 'all', label: '全部', icon: '/static/icons/cat-all.svg' },
        { ...findCategory('2'), key: '2', label: '书籍教材', icon: '/static/icons/cat-book.svg' },
        { ...findCategory('1'), key: '1', label: '数码电子', icon: '/static/icons/cat-digital.svg' },
        { ...findCategory('4'), key: '4', label: '生活用品', icon: '/static/icons/cat-dorm.svg' },
        { ...findCategory('5'), key: '5', label: '运动户外', icon: '/static/icons/cat-transport.svg' },
        { key: 'more', activeKey: '', label: '更多', icon: '/static/icons/cat-more.svg', action: 'filter' }
      ]
    },
    filteredItems() {
      return filterMarketItems(this.items, this.normalizedFilterQuery)
    },
    normalizedFilterQuery() {
      return createDefaultMarketFilter({
        ...this.filterQuery,
        keyword: this.searchKeyword,
        categoryLevel1Id: this.currentCat === 'all' ? 'all' : this.currentCat,
        categoryLevel2Id: this.activeFilterForm.categoryLevel2Id,
        priceRange: this.activeFilterForm.priceRange,
        publishTime: this.activeFilterForm.publishTime,
        condition: this.activeFilterForm.condition,
        tradeLocation: this.activeFilterForm.location,
        attributes: this.activeFilterForm.attributes,
        sortBy: this.sortBy
      })
    },
    hasActiveFilter() {
      const f = this.activeFilterForm
      return this.currentCat !== 'all' || Boolean(f.categoryLevel2Id) || f.priceRange !== 'all' || f.publishTime !== 'all' || f.condition !== 'all' || f.location !== 'all' || Object.keys(f.attributes || {}).length > 0
    },
    searchTransitionStyle() {
      const rect = this.searchTransitionRect
      if (!rect.width || !rect.height) return {}
      return {
        '--search-transition-start-left': `${rect.left}px`,
        '--search-transition-start-top': `${rect.top}px`,
        '--search-transition-start-width': `${rect.width}px`,
        '--search-transition-start-height': `${rect.height}px`,
        '--search-transition-surface-top': `${rect.surfaceTop || rect.top + rect.height}px`
      }
    },
    selectedAttributeFilterKey() {
      const key = String(this.currentCat || '')
      if (ATTRIBUTE_FILTERS[key]) return key
      const matched = this.categories.find((cat) => String(cat.key) === key)
      const label = matched ? matched.label : ''
      if (label.includes('数码')) return 'digital'
      if (label.includes('教材') || label.includes('资料')) return 'textbook'
      if (label.includes('宿舍')) return 'dorm'
      if (label.includes('服饰') || label.includes('衣')) return 'clothing'
      if (label.includes('游戏')) return 'game'
      return ''
    },
    currentAttributeFilters() {
      return ATTRIBUTE_FILTERS[this.selectedAttributeFilterKey] || []
    },
    currentFilterCategoryChildren() {
      return getMarketCategoryChildren(this.categories, this.filterForm.categoryLevel1Id)
    },
    currentAttributeFilterTitle() {
      const titles = {
        digital: '数码设备筛选',
        textbook: '教材资料筛选',
        dorm: '宿舍用品筛选',
        clothing: '闲置服饰筛选',
        game: '游戏设备筛选'
      }
      return titles[this.selectedAttributeFilterKey] || '分类属性'
    },
    selectedFilterSummaries() {
      const form = this.filterForm || {}
      const items = []
      const optionLabel = (options, value) => {
        const matched = options.find((item) => String(item.value) === String(value))
        return matched ? matched.label : ''
      }

      if (form.categoryLevel1Id && form.categoryLevel1Id !== 'all') {
        const matched = this.categories.find((cat) => String(cat.key) === String(form.categoryLevel1Id))
        if (matched) items.push({ key: 'categoryLevel1Id', label: matched.label })
      }
      if (form.categoryLevel2Id) {
        const matched = getMarketCategoryChildren(this.categories, form.categoryLevel1Id)
          .find((cat) => String(cat.key) === String(form.categoryLevel2Id))
        if (matched) items.push({ key: 'categoryLevel2Id', label: matched.label })
      }
      if (form.priceRange && form.priceRange !== 'all') {
        items.push({ key: 'priceRange', label: optionLabel(FILTER_PRICE_OPTIONS, form.priceRange) || `${form.priceRange}元` })
      }
      if (form.publishTime && form.publishTime !== 'all') {
        items.push({ key: 'publishTime', label: optionLabel(FILTER_TIME_OPTIONS, form.publishTime) })
      }
      if (form.condition && form.condition !== 'all') {
        items.push({ key: 'condition', label: optionLabel(FILTER_CONDITION_OPTIONS, form.condition) })
      }
      if (form.location && form.location !== 'all') {
        items.push({ key: 'location', label: optionLabel(FILTER_LOCATION_OPTIONS, form.location) })
      }

      const attrs = form.attributes || {}
      this.currentAttributeFilters.forEach((group) => {
        const value = attrs[group.key]
        if (value === undefined || value === null || value === '') return
        const label = optionLabel(group.options, value)
        if (label) {
          items.push({ key: `attr-${group.key}`, label })
        }
      })

      return items.filter((item) => item.label)
    },
    exchangeStatus() {
      return { status: 'none' }
    }
  },
  watch: {
    searchKeyword(newVal) {
      if (this.currentSceneTag) {
        const tag = this.sceneTags.find(t => t.key === this.currentSceneTag)
        if (tag && tag.keyword && newVal !== tag.keyword) {
          this.currentSceneTag = ''
        }
      }
      this.updateFilterQuery({ keyword: newVal })
    },
    currentCat(newVal) {
      this.updateFilterQuery({
        categoryLevel1Id: newVal === 'all' ? 'all' : newVal,
        categoryLevel2Id: '',
        attributes: {}
      })
      if (Object.keys(this.activeFilterForm.attributes || {}).length) {
        this.activeFilterForm = { ...this.activeFilterForm, attributes: {} }
      }
      if (this.activeFilterForm.categoryLevel2Id) {
        this.activeFilterForm = { ...this.activeFilterForm, categoryLevel2Id: '' }
      }
      if (this.filterVisible && Object.keys(this.filterForm.attributes || {}).length) {
        this.filterForm = { ...this.filterForm, attributes: {} }
      }
    },
    sortBy(newVal) {
      this.updateFilterQuery({ sortBy: newVal })
    },
    activeFilterForm: {
      deep: true,
      immediate: true,
      handler(next) {
        this.updateFilterQuery({
          categoryLevel2Id: next.categoryLevel2Id || '',
          priceRange: next.priceRange,
          publishTime: next.publishTime,
          condition: next.condition,
          tradeLocation: next.location,
          attributes: next.attributes || {}
        })
      }
    }
  },
  async onLoad() {
    await this.loadItems()
  },
  async onShow() {
    this.searchTransitioning = false
    this.searchTransitionNavigating = false
    await this.loadItems()
  },
  methods: {
    goToSearch() {
      if (this.searchTransitioning || this.searchTransitionNavigating) return
      uni.createSelectorQuery()
        .in(this)
        .select('.market-search-pill')
        .boundingClientRect()
        .select('.market-hero')
        .boundingClientRect()
        .exec((res) => {
          const rect = res && res[0]
          const heroRect = res && res[1]
          if (!rect) {
            uni.navigateTo({
              url: '/subpackage_lostfound/marketSearch/marketSearch?source=marketplace',
              animationType: 'none',
              animationDuration: 0
            })
            return
          }
          this.searchTransitionRect = {
            left: rect.left || 0,
            top: rect.top || 0,
            width: rect.width || 0,
            height: rect.height || 0,
            surfaceTop: heroRect && heroRect.bottom ? heroRect.bottom : (rect.top || 0) + (rect.height || 0)
          }
          this.$nextTick(() => {
            this.searchTransitioning = true
          })
        })
    },
    onSearchTransitionEnd() {
      if (!this.searchTransitioning || this.searchTransitionNavigating) return
      this.searchTransitionNavigating = true
      uni.navigateTo({
        url: '/subpackage_lostfound/marketSearch/marketSearch?source=marketplace',
        animationType: 'none',
        animationDuration: 0,
        complete: () => {
          this.searchTransitioning = false
          this.searchTransitionNavigating = false
        }
      })
    },
    updateFilterQuery(patch = {}) {
      this.filterQuery = createDefaultMarketFilter({
        ...this.filterQuery,
        ...patch,
        attributes: {
          ...(this.filterQuery.attributes || {}),
          ...(patch.attributes || {})
        }
      })
    },
    async loadItems() {
      try {
        this.pageLoading = true
        const res = await getSecondhandItemList({ current: 1, size: 100, sort: 'latest' })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalizeItem)
      } catch (error) {
        console.error('加载集市列表失败', error)
        this.items = []
      } finally {
        this.pageLoading = false
      }
    },
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
    emoji(id) {
      return EMOJIS[id % EMOJIS.length]
    },
    itemCategoryLabel(item) {
      return getMarketCategoryLabel(item)
    },
    itemConditionLabel(item) {
      if (item.conditionText) return item.conditionText
      const text = `${item.name || ''} ${item.desc || ''}`
      if (text.includes('全新') || text.includes('未拆')) return '全新'
      if (text.includes('九成新') || text.includes('很少用')) return '九成新'
      return '二手好物'
    },
    itemLocationLabel(item) {
      return item.pickupPoint || item.location || '校内自提'
    },
    isNegotiable(item) {
      return Boolean(item.allowBargain || String(item.desc || '').includes('价格可议'))
    },
    priceDisplay(item) {
      const desc = String(item.desc || '')
      if (desc.includes('免费赠送')) {
        return { prefix: '', text: '免费' }
      }
      if (desc.includes('价格面议')) {
        return { prefix: '', text: '面议' }
      }
      const price = Number(item.price)
      if (!price) {
        return { prefix: '', text: '免费' }
      }
      return { prefix: '¥', text: String(item.price) }
    },
    itemSceneTags(item) {
      const tags = []
      const price = Number(item.price) || 0
      const text = `${item.name || ''} ${item.desc || ''}`
      if (price > 0 && price <= 50) tags.push('低价')
      if (text.includes('可小刀') || text.includes('小刀')) tags.push('可小刀')
      if (text.includes('全新') || text.includes('未拆')) tags.push('全新')
      if (this.itemCategoryLabel(item)) tags.push(this.itemCategoryLabel(item))
      return tags.slice(0, 2)
    },
    applySceneTag(tag) {
      // Toggle off if clicking the already-selected tag
      if (this.currentSceneTag === tag.key) {
        this.currentSceneTag = ''
        this.searchKeyword = ''
        this.currentCat = 'all'
        this.activeFilterForm = { categoryLevel2Id: '', priceRange: 'all', publishTime: 'all', condition: 'all', location: 'all', attributes: {} }
        return
      }
      // Apply tag
      this.currentSceneTag = tag.key
      this.searchKeyword = tag.keyword || ''
      this.currentCat = tag.category || 'all'
      if (tag.filter) {
        this.activeFilterForm = { categoryLevel2Id: '', priceRange: 'all', publishTime: 'all', condition: 'all', location: 'all', attributes: {}, ...tag.filter }
      } else {
        this.activeFilterForm = { categoryLevel2Id: '', priceRange: 'all', publishTime: 'all', condition: 'all', location: 'all', attributes: {} }
      }
    },
    fmt(ts) {
      if (!ts) return ''
      const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
      const diff = Date.now() - time
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      const d = new Date(time)
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    go(page) {
      const pageMap = {
        'pgList': 'list',
        'pgDetail': 'detail',
        'pgChat': 'chat',
        'pgMyItems': 'myitems',
        'pgMyMsg': 'mymessages'
      }
      this.currentPage = pageMap[page] || 'list'
    },
    onBackToApp() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
    goToMyItems() {
      uni.navigateTo({
        url: '/subpackage_lostfound/myItems/myItems'
      })
    },
    goToMessages() {
      this.currentTab = 'messages'
      uni.navigateTo({
        url: '/pages/market/message/message'
      })
    },
    goToHome() {
      this.currentTab = 'home'
      uni.navigateTo({ url: '/subpackage_lostfound/marketplaceHome/marketplaceHome' })
    },
    goToMarket() {
      this.currentTab = 'market'
      this.currentPage = 'list'
      this.categoryExpanded = false
    },
    toggleCategoryPanel() {
      this.categoryExpanded = !this.categoryExpanded
    },
    goToMine() {
      this.currentTab = 'mine'
      uni.navigateTo({ url: '/subpackage_lostfound/marketplaceProfile/marketplaceProfile' })
    },
    showDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    },
    openChat() {
      if (!this.curItem?.id) return
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?itemId=${this.curItem.id}`
      })
    },
    sendMsg() {},
    reqExchange() {
      uni.showToast({ title: '暂未开放微信交换', icon: 'none' })
    },
    toggleStatus() {},
    openChatFromList(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?sessionId=${id}`
      })
    },
    previewImg(src) {
      uni.previewImage({
        urls: [src]
      })
    },
    openFilter() {
      this.filterVisible = true
      this.filterForm = {
        categoryLevel1Id: this.currentCat,
        categoryLevel2Id: this.activeFilterForm.categoryLevel2Id || '',
        priceRange: this.activeFilterForm.priceRange,
        customPriceMin: '',
        customPriceMax: '',
        publishTime: this.activeFilterForm.publishTime,
        condition: this.activeFilterForm.condition,
        location: this.activeFilterForm.location,
        attributes: { ...(this.activeFilterForm.attributes || {}) }
      }
    },
    closeFilter() {
      this.filterVisible = false
    },
    selectMarketCategory(cat) {
      if (cat?.action === 'filter' || cat?.key === 'more') {
        this.categoryExpanded = false
        this.openFilter()
        return
      }
      this.currentCat = cat?.key || 'all'
      this.categoryExpanded = false
    },
    resetFilter() {
      this.filterForm = {
        categoryLevel1Id: 'all',
        categoryLevel2Id: '',
        priceRange: 'all',
        customPriceMin: '',
        customPriceMax: '',
        publishTime: 'all',
        condition: 'all',
        location: 'all',
        attributes: {}
      }
    },
    confirmFilter() {
      const priceRange = this.normalizeCustomPriceRange(this.filterForm)
      this.currentCat = this.filterForm.categoryLevel1Id || 'all'
      this.activeFilterForm = {
        categoryLevel2Id: this.filterForm.categoryLevel2Id || '',
        priceRange,
        publishTime: this.filterForm.publishTime,
        condition: this.filterForm.condition,
        location: this.filterForm.location,
        attributes: { ...(this.filterForm.attributes || {}) }
      }
      this.filterVisible = false
    },
    selectFilterCategoryLevel1(value) {
      this.filterForm = {
        ...this.filterForm,
        categoryLevel1Id: value,
        categoryLevel2Id: '',
        attributes: {}
      }
    },
    selectFilterPrice(value) {
      this.filterForm = {
        ...this.filterForm,
        priceRange: value,
        customPriceMin: '',
        customPriceMax: ''
      }
    },
    normalizeCustomPriceRange(form = {}) {
      const minText = String(form.customPriceMin ?? '').trim()
      const maxText = String(form.customPriceMax ?? '').trim()
      if (!minText && !maxText) return form.priceRange || 'all'
      const min = minText ? Math.max(0, Number(minText)) : 0
      const max = maxText ? Math.max(0, Number(maxText)) : ''
      if (Number.isNaN(min) || Number.isNaN(max)) return form.priceRange || 'all'
      return max === '' ? `${min}-` : `${min}-${max}`
    },
    isAttributeSelected(key, value) {
      return String((this.filterForm.attributes || {})[key]) === String(value)
    },
    toggleAttributeFilter(key, value) {
      const next = { ...(this.filterForm.attributes || {}) }
      if (String(next[key]) === String(value)) {
        delete next[key]
      } else {
        next[key] = value
      }
      this.filterForm = {
        ...this.filterForm,
        attributes: next
      }
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
  background: #F7F7F9;
  padding-bottom: 120rpx;
}

.page-root--market-list {
  height: 100vh;
  min-height: 100vh;
  padding-bottom: 0;
  overflow: hidden;
}

.page-root--market-list .screen,
.page-root--market-list .container,
.page-root--market-list .page-list {
  height: 100vh;
  min-height: 0;
  overflow: hidden;
}

.page-root--market-list .page {
  min-height: 0;
}

.page-content {
  /* 入场动画已移除 */
}

.page-list {
  height: 100vh;
  overflow: hidden;
}

.page-list .page-content {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.screen {
  width: 100%;
  background: #F7F7F9;
  min-height: 100vh;
}

.container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0;
  background: #F7F7F9;
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

.market-list-scroll {
  min-height: 0;
  height: 0;
  overflow: hidden;
}

.market-hero {
  z-index: 20;
  padding: 28rpx 0 24rpx;
  background: #F7F7F9;
  border-bottom: 0;
}

/* ===== School header ===== */
.list-school-bar {
  padding: 10rpx 28rpx 6rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.school-chip {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8rpx;
}

.school-chip-icon {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}

.list-school-name {
  font-size: 32rpx;
  font-weight: 800;
  color: #1D1D1F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-message {
  width: 58rpx;
  height: 58rpx;
  border-radius: 50%;
  background: #F7F7F9;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-message-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.72;
}

.market-list-search-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin: 0 28rpx;
}

.market-list-search {
  flex: 1;
  min-width: 0;
}

.market-search-pill {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  height: 82rpx;
  border-radius: 42rpx;
  background: #FFFFFF;
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-sizing: border-box;
}

.market-search-pill-input {
  flex: 1;
  min-width: 0;
  height: 82rpx;
  line-height: 82rpx;
  font-size: 27rpx;
  font-weight: 500;
  color: #8C929A;
  -webkit-text-fill-color: #8C929A;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  pointer-events: none;
}

.market-search-pill-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  opacity: 0.58;
}

.search-transition-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 99999;
  pointer-events: none;
  overflow: hidden;
}

.search-transition-top-panel {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  height: var(--search-transition-surface-top, 180rpx);
  background: transparent;
  opacity: 0;
  z-index: 2;
  transition: none;
}

.search-transition-surface {
  position: fixed;
  left: 0;
  right: 0;
  top: var(--search-transition-surface-top, 180rpx);
  bottom: 0;
  width: 100%;
  height: auto;
  border-radius: 0;
  background: #F7F7F9;
  overflow: hidden;
  box-shadow: none;
  transform-origin: left top;
  opacity: 0;
  transform: translate3d(0, 0, 0) scale3d(1, 0.01, 1);
  transition:
    transform 340ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 220ms ease-out;
  will-change: transform, opacity;
}

.search-transition-mask--active .search-transition-surface {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale3d(1, 1, 1);
}

.search-transition-bar {
  position: fixed;
  left: var(--search-transition-start-left, 28rpx);
  top: var(--search-transition-start-top, 120rpx);
  width: var(--search-transition-start-width, calc(100vw - 132rpx));
  height: var(--search-transition-start-height, 82rpx);
  z-index: 100000;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 42rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  box-sizing: border-box;
  opacity: 0;
  transform: translate3d(0, 8rpx, 0) scale3d(0.985, 0.985, 1);
  transform-origin: center center;
  transition:
    opacity 180ms ease-out,
    transform 340ms cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 340ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 340ms ease-out;
  will-change: transform, opacity;
}

.search-transition-mask--active .search-transition-bar {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale3d(1, 1, 1);
}

.search-transition-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  opacity: 0.58;
}

.search-transition-input {
  flex: 1;
  min-width: 0;
  height: 82rpx;
  line-height: 82rpx;
  font-size: 27rpx;
  font-weight: 500;
  color: #8C929A;
  -webkit-text-fill-color: #8C929A;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  pointer-events: none;
}

.search-filter-btn {
  width: 82rpx;
  height: 82rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
  box-sizing: border-box;
}

.search-filter-icon {
  width: 36rpx;
  height: 36rpx;
  opacity: 0.82;
}

.search-filter-dot {
  position: absolute;
  top: 8rpx;
  right: 6rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #E85D75;
  border: 2rpx solid #fff;
}

.scene-strip {
  display: flex;
  gap: 14rpx;
  padding: 18rpx 28rpx 0;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.scene-strip::-webkit-scrollbar {
  display: none;
}

.scene-strip--in-category {
  padding: 0 0 22rpx;
  margin-bottom: 22rpx;
  border-bottom: 1rpx solid #F0F1F3;
}

.scene-chip {
  min-width: 142rpx;
  padding: 14rpx 18rpx;
  border-radius: 18rpx;
  background: #F7F7F9;
  border: 1rpx solid #EEEEEE;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  flex-shrink: 0;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.scene-chip:active {
  transform: scale(0.96);
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.scene-chip.on {
  background: #FFFFFF;
  border-color: #6F98D0;
  box-shadow: 0 0 0 2rpx rgba(111, 152, 208, 0.25);
}

.scene-chip-title {
  font-size: 24rpx;
  line-height: 1.2;
  font-weight: 800;
  color: #1D1D1F;
}

.scene-chip-sub {
  font-size: 20rpx;
  color: #8E8E93;
  line-height: 1.2;
}

.section-headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22rpx;
  transition: margin-bottom 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.section-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1;
}

.section-note {
  font-size: 22rpx;
  color: #8E8E93;
}

.category-toggle {
  width: 56rpx;
  height: 56rpx;
  border-radius: 0;
  background: transparent;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.category-toggle:active {
  background: transparent;
}

.category-toggle-icon {
  width: 30rpx;
  height: 30rpx;
  position: relative;
  color: #1D1D1F;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-toggle-icon::before {
  content: '';
  width: 14rpx;
  height: 14rpx;
  border-right: 2rpx solid currentColor;
  border-bottom: 2rpx solid currentColor;
  transform: rotate(45deg);
  transform-origin: center;
  transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.category-toggle-icon--expanded::before {
  transform: rotate(225deg);
}

/* ===== Categories ===== */
.category-shell {
  background: #fff;
  border-radius: 22rpx;
  margin: 4rpx 28rpx 0;
  padding: 0 22rpx;
  border: 1rpx solid rgba(228, 232, 238, 0.9);
  box-shadow: 0 12rpx 32rpx rgba(92, 122, 153, 0.08);
  overflow: hidden;
  transition: padding 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.category-shell--expanded {
  padding-bottom: 24rpx;
}

.category-summary {
  min-height: 70rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.category-summary-main {
  min-width: 0;
  display: flex;
  align-items: center;
}

.category-summary-title {
  min-width: 0;
  font-size: 26rpx;
  color: #1D1D1F;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-summary-action {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
  font-size: 22rpx;
  color: #5C7A99;
  font-weight: 800;
}

.category-summary-arrow {
  width: 18rpx;
  height: 18rpx;
  position: relative;
  color: #5C7A99;
}

.category-summary-arrow::before {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 1rpx;
  width: 10rpx;
  height: 10rpx;
  border-right: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: rotate(45deg);
  transform-origin: center;
  transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.category-summary-arrow--expanded::before {
  transform: rotate(225deg);
}

.category-collapse {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  transform: translateY(-8rpx);
  overflow: hidden;
  transition:
    grid-template-rows 260ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease-out,
    transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
}

.category-collapse--expanded {
  grid-template-rows: 1fr;
  opacity: 1;
  transform: translateY(0);
}

.category-collapse-inner {
  min-height: 0;
  overflow: hidden;
}

.cat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 0;
}

.cat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  min-width: 0;
}

.cat-item.on .cat-icon-wrap {
  background: rgba(86, 149, 230, 0.14);
  border-color: rgba(86, 149, 230, 0.28);
}

.cat-item.on .cat-label {
  color: #2F7FE5;
  font-weight: 800;
}

.cat-icon-wrap {
  width: 74rpx;
  height: 74rpx;
  border-radius: 50%;
  background: #F5F6F8;
  border: 1rpx solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s ease;
}

.cat-icon-img {
  width: 40rpx;
  height: 40rpx;
}

.cat-icon-text {
  font-size: 26rpx;
  color: #5C7A99;
  font-weight: 800;
}

.cat-label {
  width: 100%;
  font-size: 22rpx;
  font-weight: 700;
  color: #24272B;
  text-align: center;
  line-height: 1.15;
  white-space: nowrap;
}

.cat-label--muted {
  color: #AEAEAE;
}

/* ===== Sort bar ===== */
.sort-bar {
  z-index: 12;
  display: flex;
  align-items: center;
  gap: 44rpx;
  padding: 30rpx 36rpx 18rpx;
  background: #F7F7F9;
}

.sort-tab {
  font-size: 30rpx;
  font-weight: 600;
  color: #96999E;
  position: relative;
  padding-bottom: 14rpx;
  transition: color 0.18s;
}

.sort-tab.on {
  color: #1D1D1F;
  font-weight: 800;
}

.sort-tab.on::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 36rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #4F8FE8;
}

.sort-spacer {
  flex: 1;
}

.sort-filter {
  display: flex;
  align-items: center;
  gap: 8rpx;
  min-height: 56rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(228, 232, 238, 0.9);
  color: #6F747B;
  font-size: 24rpx;
  font-weight: 700;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.08);
}

.sort-filter.on {
  color: #5C7A99;
  background: rgba(92, 122, 153, 0.08);
  border-color: rgba(92, 122, 153, 0.16);
}

.sort-filter-icon {
  width: 30rpx;
  height: 30rpx;
  opacity: 0.68;
}

/* ===== Product grid ===== */
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  align-items: start;
  gap: 18rpx;
  padding: 4rpx 18rpx 210rpx;
}

.product-card {
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  border: 1rpx solid rgba(228, 232, 238, 0.95);
  transition: transform 0.15s ease;
  box-shadow: 0 10rpx 28rpx rgba(92, 122, 153, 0.08);
  padding: 12rpx 12rpx 0;
  box-sizing: border-box;
}

.product-card:active {
  transform: scale(0.98);
}

.product-img {
  position: relative;
  width: 100%;
  aspect-ratio: 1.18 / 1;
  background: #F1F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 18rpx;
}

.product-img-src {
  width: 100%;
  height: 100%;
  border-radius: 18rpx;
}

.product-img-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.product-img-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  color: #8E8E93;
}

.product-img-placeholder-text {
  font-size: 22rpx;
  font-weight: 700;
}

.product-status-badge {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(29, 29, 31, 0.72);
  color: #FFFFFF;
  font-size: 19rpx;
  font-weight: 800;
  line-height: 1;
}

.product-status-badge--reserved {
  background: rgba(92, 122, 153, 0.88);
}

.product-status-badge--sold,
.product-status-badge--offline {
  background: rgba(142, 142, 147, 0.88);
}

.product-price-symbol {
  font-size: 24rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1;
}

.product-price-num {
  font-size: 36rpx;
  font-weight: 850;
  color: #1D1D1F;
  line-height: 1;
}

.product-price-text {
  font-size: 30rpx;
  color: #4A6278;
}

.product-body {
  padding: 16rpx 4rpx 18rpx;
}

.product-main-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.product-name {
  font-size: 27rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1.32;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 0;
}

.product-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #8C929A;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 16rpx;
}

.product-info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 14rpx;
}

.product-info-chip {
  max-width: 116rpx;
  padding: 6rpx 12rpx;
  border-radius: 12rpx;
  background: rgba(79, 143, 232, 0.1);
  color: #2F7FE5;
  font-size: 20rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-info-chip--blue {
  background: rgba(92, 122, 153, 0.08);
  color: #4A6278;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 2rpx;
}

.product-location-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 14rpx;
  margin-bottom: 14rpx;
}

.product-location-icon {
  width: 26rpx;
  height: 26rpx;
  flex-shrink: 0;
  opacity: 0.5;
}

.product-location-label {
  font-size: 19rpx;
  color: #A2A8AF;
  font-weight: 600;
}

.product-location {
  font-size: 22rpx;
  color: #8C929A;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #EEF1F4;
}

.product-ava {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: rgba(92, 122, 153, 0.12);
  color: #fff;
  font-size: 18rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #5C7A99;
}

.product-uname {
  font-size: 21rpx;
  color: #666A70;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-time {
  font-size: 19rpx;
  color: #A2A8AF;
  flex-shrink: 0;
}

/* ===== Empty ===== */
.empty-block {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
}

.empty-icon {
  position: relative;
  width: 82rpx;
  height: 62rpx;
  margin-bottom: 24rpx;
  border: 3rpx solid #8E8E93;
  border-top: 0;
  border-radius: 8rpx 8rpx 12rpx 12rpx;
  background: transparent;
  box-sizing: border-box;
}

.empty-icon::before {
  content: '';
  position: absolute;
  left: -3rpx;
  top: -20rpx;
  width: 82rpx;
  height: 24rpx;
  box-sizing: border-box;
  border: 3rpx solid #8E8E93;
  border-bottom: 0;
  border-radius: 10rpx 10rpx 0 0;
}

.empty-icon::after {
  content: '';
  position: absolute;
  left: 20rpx;
  right: 20rpx;
  top: 18rpx;
  height: 0;
  box-sizing: border-box;
  border-top: 3rpx solid #8E8E93;
  border-radius: 999rpx;
}

.empty-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #888888;
  margin-bottom: 12rpx;
}

.empty-sub {
  font-size: 24rpx;
  color: #B0B0B0;
}

/* detail */
.dimg {
  width: 100%;
  aspect-ratio: 3 / 2;
  background: linear-gradient(135deg, #F0F0F0, #F5F5F5);
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
  color: #6F98D0;
  margin-bottom: 12rpx;
}

.dprice small {
  font-size: 32rpx;
  font-weight: 600;
}

.dtitle {
  font-size: 36rpx;
  font-weight: 700;
  color: #111111;
  margin-bottom: 24rpx;
  line-height: 1.4;
}

.ddesc {
  font-size: 30rpx;
  color: #888888;
  line-height: 1.7;
  margin-bottom: 32rpx;
}

.seller {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  margin: 0 32rpx 32rpx;
}

.sava {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #6F98D0, #5E80B0);
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
  color: #111111;
}

.stime {
  font-size: 24rpx;
  color: #888888;
  margin-top: 4rpx;
}

.sarrow {
  font-size: 28rpx;
  color: #C0C0C0;
}

.abar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 32rpx 56rpx;
  background: linear-gradient(to top, rgba(242, 245, 249, 1) 0%, rgba(242, 245, 249, 0.98) 100%);
  border-top: 2rpx solid rgba(0, 0, 0, 0.06);
  z-index: 50;
}

.abtn {
  height: 88rpx;
  border-radius: 24rpx;
  background: #6F98D0;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(111, 152, 208, 0.45);
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
  background: #6F98D0;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.mava-r {
  background: #6F98D0;
}

.mava-s {
  background: #6F98D0;
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
  background: #6F98D0;
  color: #fff;
}

.mbub-r {
  background: #fff;
  color: #111111;
}

.mtime {
  font-size: 20rpx;
  color: #888888;
  margin-top: 8rpx;
  padding: 0 4rpx;
}

.msys {
  text-align: center;
  font-size: 24rpx;
  color: #888888;
  margin: 32rpx 0;
}

.excard-new {
  margin: 32rpx 0;
  padding: 40rpx;
  background: #F5F5F5;
  border-radius: 24rpx;
  text-align: center;
}

.excard-new-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111111;
  margin-bottom: 12rpx;
}

.excard-new-desc {
  font-size: 24rpx;
  color: #888888;
  margin-bottom: 24rpx;
}

.excard-new-btn {
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: #6F98D0;
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
  background: #6F98D0;
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
  color: #6F98D0;
}

.rev-phone-new {
  font-size: 36rpx;
  font-weight: 800;
  color: #5E80B0;
  margin-bottom: 8rpx;
}

.rev-label-new {
  font-size: 24rpx;
  color: #888888;
}

.chat-footer-new {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(242, 245, 249, 0.98);
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
  color: #6F98D0;
  border: 2rpx solid rgba(111, 152, 208, 0.25);
  text-align: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10rpx);
  z-index: 10;
  white-space: nowrap;
}

.chat-ex-btn-new.exchanged {
  color: rgba(111, 152, 208, 0.45);
  border-color: rgba(111, 152, 208, 0.12);
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
  background: #6F98D0;
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
  border-radius: 16rpx;
  position: relative;
}

.miimg {
  width: 120rpx;
  height: 120rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #F0F0F0, #F5F5F5);
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
  color: #111111;
  margin-bottom: 8rpx;
}

.miprice {
  font-size: 32rpx;
  font-weight: 800;
  color: #6F98D0;
}

.miprice small {
  font-size: 22rpx;
}

.mitime {
  font-size: 22rpx;
  color: #888888;
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
  border-radius: 16rpx;
}

.msava {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #6F98D0;
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
  color: #111111;
  margin-bottom: 8rpx;
}

.msprev {
  font-size: 26rpx;
  color: #888888;
}

.msmeta {
  text-align: right;
}

.mstime {
  font-size: 22rpx;
  color: #888888;
  margin-bottom: 8rpx;
}

.msbadge {
  display: inline-block;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #6F98D0;
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
  position: relative;
  width: 82rpx;
  height: 62rpx;
  margin: 0 auto 24rpx;
  border: 3rpx solid #8E8E93;
  border-top: 0;
  border-radius: 8rpx 8rpx 12rpx 12rpx;
  background: transparent;
  box-sizing: border-box;
}

.empty-i::before {
  content: '';
  position: absolute;
  left: -3rpx;
  top: -20rpx;
  width: 82rpx;
  height: 24rpx;
  box-sizing: border-box;
  border: 3rpx solid #8E8E93;
  border-bottom: 0;
  border-radius: 10rpx 10rpx 0 0;
}

.empty-i::after {
  content: '';
  position: absolute;
  left: 20rpx;
  right: 20rpx;
  top: 18rpx;
  height: 0;
  box-sizing: border-box;
  border-top: 3rpx solid #8E8E93;
  border-radius: 999rpx;
}

.empty-t {
  font-size: 28rpx;
  color: #888888;
}

.empty-icon-msg {
  display: block;
  width: 120rpx;
  height: 120rpx;
  margin: 0 auto 24rpx;
  opacity: 0.45;
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
  margin: 0 auto 24rpx;
}

.empty-block .empty-text {
  font-size: 28rpx;
  color: #888888;
}

/* ===== Filter bottom sheet ===== */
.filter-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(17, 24, 39, 0.32);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  animation: filterFadeIn 0.18s ease-out;
}

.filter-panel {
  width: 100%;
  max-height: 88vh;
  background: #fff;
  border-radius: 42rpx 42rpx 0 0;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -18rpx 64rpx rgba(31, 41, 55, 0.12);
  animation: filterSlideUp 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  overflow: hidden;
}

.filter-handle {
  width: 82rpx;
  height: 9rpx;
  border-radius: 999rpx;
  background: #D0D5DD;
  margin: 18rpx auto 4rpx;
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22rpx 40rpx 26rpx;
  position: relative;
  flex-shrink: 0;
}

.filter-title {
  font-size: 38rpx;
  font-weight: 850;
  color: #1D1D1F;
  line-height: 1.2;
}

.filter-close {
  position: absolute;
  right: 34rpx;
  top: 18rpx;
  font-size: 44rpx;
  color: #6B7280;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.selected-filter-strip {
  display: flex;
  gap: 12rpx;
  padding: 0 38rpx 18rpx;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
  flex-shrink: 0;
}

.selected-filter-strip::-webkit-scrollbar {
  display: none;
}

.selected-filter-chip {
  display: inline-flex;
  align-items: center;
  height: 44rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #EAF3FF;
  border: 1rpx solid #C8DAF0;
  color: #4F7FB8;
  font-size: 21rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.filter-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 38rpx 22rpx;
  max-height: calc(88vh - 210rpx);
  box-sizing: border-box;
}

.filter-group {
  margin-bottom: 42rpx;
  box-sizing: border-box;
}

.filter-group-title {
  position: relative;
  padding-left: 22rpx;
  font-size: 30rpx;
  font-weight: 850;
  color: #2B2F36;
  margin-bottom: 22rpx;
  line-height: 1.25;
}

.filter-group-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4rpx;
  width: 7rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: #3F73C8;
}

.filter-attribute-group {
  width: 100%;
  max-width: 100%;
  padding: 22rpx 18rpx 20rpx;
  margin-top: 8rpx;
  background: #F7FAFD;
  border: 1rpx solid #E9F1FA;
  border-radius: 22rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.attribute-row {
  margin-bottom: 22rpx;
  box-sizing: border-box;
}

.attribute-row:last-child {
  margin-bottom: 0;
}

.attribute-title {
  font-size: 22rpx;
  font-weight: 700;
  color: #4A6278;
  margin-bottom: 12rpx;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
  max-width: 100%;
  box-sizing: border-box;
}

.filter-price-custom {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 22rpx;
}

.filter-price-input {
  flex: 1;
  min-width: 0;
  height: 70rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  border: 1rpx solid #DDE2EA;
  color: #1D1D1F;
  font-size: 26rpx;
  box-sizing: border-box;
}

.filter-price-placeholder {
  color: #A8AFB9;
}

.filter-price-separator {
  color: #1D1D1F;
  font-size: 28rpx;
  font-weight: 700;
}

.filter-opt {
  width: calc((100% - 54rpx) / 4);
  height: 60rpx;
  padding: 0 10rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  border: 1rpx solid #DDE2EA;
  font-size: 25rpx;
  font-weight: 750;
  color: #252A31;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.16s ease, background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-opt:active {
  transform: scale(0.96);
}

.filter-opt.on {
  background: #F1F6FF;
  border-color: #A8C3F0;
  color: #2F6FC8;
}

.filter-footer {
  display: flex;
  gap: 24rpx;
  padding: 22rpx 36rpx calc(22rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #E5E7EB;
  background: rgba(255, 255, 255, 0.98);
  flex-shrink: 0;
}

.filter-btn {
  flex: 1;
  height: 78rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 850;
}

.filter-btn.reset {
  background: #FFFFFF;
  border: 1rpx solid #C8D0DA;
  color: #1D1D1F;
}

.filter-btn.confirm {
  background: #4B7DCE;
  color: #fff;
  box-shadow: 0 10rpx 24rpx rgba(75, 125, 206, 0.24);
}

@keyframes filterFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes filterSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
