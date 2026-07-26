<script setup>import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import AppTabBar from '../components/AppTabBar.vue';
import { getMyRegistrations, getMyFavorites, cancelRegistration, registerActivity, removeFavorite, getActivityList } from '../api/activity';
import { getUserInfo } from '../utils/auth';
import { mockRegistrations, mockHistoryRegistrations, mockFavorites } from '../mock/activityData';
const router = useRouter();
const activeTab = ref('registered');
const loading = ref(false);
const error = ref(null);
const registeredActivities = ref([]);
const favoriteActivities = ref([]);
const historyActivities = ref([]);
const myCreatedActivities = ref([]);
const activeCountdownId = ref(null);
const isCreator = computed(() => {
 const user = getUserInfo();
 return user?.role === 'admin' || user?.role === 'organizer';
});
const TABS = [
 { key: 'registered', label: '已报名', icon: '📋' },
 { key: 'favorites', label: '我的收藏', icon: '⭐' },
 { key: 'history', label: '历史参与', icon: '📜' },
 { key: 'created', label: '我发起的', icon: '📝', creatorOnly: true },
];
onMounted(() => {
 loadAllData();
 startCountdown();
});
onUnmounted(() => {
 if (activeCountdownId.value) {
 clearInterval(activeCountdownId.value);
 }
});
function startCountdown() {
 activeCountdownId.value = setInterval(() => {
 registeredActivities.value = registeredActivities.value.map(item => ({
 ...item,
 canCancel: canCancelRegistration(item),
 }));
 }, 60000);
}
function canCancelRegistration(item) {
 if (!item.activity?.startTime)
 return true;
 const startTime = new Date(item.activity.startTime.replace(' ', 'T'));
 const now = new Date();
 const diffHours = (startTime - now) / (1000 * 60 * 60);
 return diffHours > 2;
}
async function loadAllData() {
 loading.value = true;
 error.value = null;
 try {
 await Promise.all([
 loadRegisteredActivities(),
 loadFavoriteActivities(),
 loadHistoryActivities(),
 loadCreatedActivities(),
 ]);
 }
 catch (err) {
 console.error('加载数据失败:', err);
 error.value = err?.message || '加载失败';
 }
 finally {
 loading.value = false;
 }
}
async function loadRegisteredActivities() {
 try {
 const res = await getMyRegistrations();
 const data = res?.data || {};
 let records = Array.isArray(data) ? data : data.records || [];
 if (records.length === 0) {
 records = mockRegistrations;
 }
 registeredActivities.value = records.map(item => ({
 ...item,
 canCancel: canCancelRegistration(item),
 }));
 }
 catch (err) {
 console.error('加载报名活动失败，使用Mock数据:', err);
 registeredActivities.value = mockRegistrations.map(item => ({
 ...item,
 canCancel: canCancelRegistration(item),
 }));
 }
}
async function loadFavoriteActivities() {
 try {
 const res = await getMyFavorites();
 const data = res?.data || {};
 let records = Array.isArray(data) ? data : data.records || [];
 if (records.length === 0) {
 records = mockFavorites;
 }
 favoriteActivities.value = records;
 }
 catch (err) {
 console.error('加载收藏活动失败，使用Mock数据:', err);
 favoriteActivities.value = mockFavorites;
 }
}
async function loadHistoryActivities() {
 try {
 const res = await getMyRegistrations({ status: 'completed' });
 const data = res?.data || {};
 let records = Array.isArray(data) ? data : data.records || [];
 if (records.length === 0) {
 records = mockHistoryRegistrations;
 }
 historyActivities.value = records;
 }
 catch (err) {
 console.error('加载历史活动失败，使用Mock数据:', err);
 historyActivities.value = mockHistoryRegistrations;
 }
}
async function loadCreatedActivities() {
 if (!isCreator.value) {
 myCreatedActivities.value = [];
 return;
 }
 try {
 const res = await getActivityList({ createdBy: 'me' });
 const data = res?.data || {};
 myCreatedActivities.value = Array.isArray(data) ? data : data.records || [];
 }
 catch (err) {
 console.error('加载我发起的活动失败:', err);
 myCreatedActivities.value = [];
 }
}
function handleTabChange(tabKey) {
 activeTab.value = tabKey;
}
function handleViewDetail(activityId) {
 router.push(`/activities/${activityId}`);
}
async function handleCancelRegistration(registrationId, activity) {
 if (!confirm(`确定要取消报名"${activity?.title || '该活动'}"吗？`))
 return;
 try {
 await cancelRegistration(registrationId);
 alert('取消报名成功');
 loadRegisteredActivities();
 }
 catch (err) {
 alert(err?.message || '取消报名失败');
 }
}
async function handleRemoveFavorite(activityId) {
 try {
 await removeFavorite(activityId);
 alert('已取消收藏');
 loadFavoriteActivities();
 }
 catch (err) {
 alert(err?.message || '操作失败');
 }
}
async function handleRegisterFromFavorite(activityId) {
 try {
 await registerActivity(activityId);
 alert('报名成功');
 loadFavoriteActivities();
 loadRegisteredActivities();
 }
 catch (err) {
 alert(err?.message || '报名失败');
 }
}
function handleExportCertificate(activity) {
 const certData = {
 title: activity?.title || '活动证书',
 participant: getUserInfo()?.name || '参与者',
 date: activity?.startTime || new Date().toISOString(),
 organizer: activity?.organizer || '',
 certificateNo: 'CERT-' + Date.now(),
 };
 const blob = new Blob([JSON.stringify(certData, null, 2)], { type: 'application/json' });
 const url = URL.createObjectURL(blob);
 const a = document.createElement('a');
 a.href = url;
 a.download = `活动参与证书-${activity?.title || '活动'}.json`;
 a.click();
 URL.revokeObjectURL(url);
 alert('证书已导出');
}
function handleImportToResume(activity) {
 const resumeEntry = {
 type: 'activity',
 title: activity?.title || '',
 date: activity?.startTime || '',
 description: activity?.description || '',
 organizer: activity?.organizer || '',
 };
 const existing = JSON.parse(localStorage.getItem('resumeActivities') || '[]');
 existing.push(resumeEntry);
 localStorage.setItem('resumeActivities', JSON.stringify(existing));
 alert('已导入简历！可在简历模块中查看');
}
function handleShareActivity(activity) {
 const url = `${window.location.origin}/activities/${activity?.id}`;
 if (navigator.clipboard) {
 navigator.clipboard.writeText(url).then(() => {
 alert('链接已复制到剪贴板');
 }).catch(() => {
 prompt('复制以下链接分享:', url);
 });
 }
 else {
 prompt('复制以下链接分享:', url);
 }
}
function handleEditActivity(activityId) {
 router.push(`/activity/${activityId}/edit`);
}
function formatDate(dateStr) {
 if (!dateStr)
 return '待定';
 const date = new Date(String(dateStr).replace(' ', 'T'));
 if (isNaN(date.getTime()))
 return dateStr;
 const month = date.getMonth() + 1;
 const day = date.getDate();
 return `${month}月${day}日`;
}
function formatDateTime(dateStr) {
 if (!dateStr)
 return '待定';
 const date = new Date(String(dateStr).replace(' ', 'T'));
 if (isNaN(date.getTime()))
 return dateStr;
 const month = date.getMonth() + 1;
 const day = date.getDate();
 const hour = String(date.getHours()).padStart(2, '0');
 const minute = String(date.getMinutes()).padStart(2, '0');
 return `${month}月${day}日 ${hour}:${minute}`;
}
function getActivityStatus(item) {
 const now = new Date();
 const startTime = new Date(item.activity?.startTime?.replace(' ', 'T') || item.startTime?.replace(' ', 'T'));
 const endTime = new Date(item.activity?.endTime?.replace(' ', 'T') || item.endTime?.replace(' ', 'T'));
 if (now > endTime)
 return { text: '已结束', class: 'status-ended' };
 if (now < startTime)
 return { text: '未开始', class: 'status-upcoming' };
 return { text: '进行中', class: 'status-ongoing' };
}
function getTabCount(tabKey) {
 switch (tabKey) {
 case 'registered': return registeredActivities.value?.length || 0;
 case 'favorites': return favoriteActivities.value?.length || 0;
 case 'history': return historyActivities.value?.length || 0;
 case 'created': return myCreatedActivities.value?.length || 0;
 default: return 0;
 }
}
</script>

<template>
  <div class="my-activities-view">
    <AppTabBar />
    
    <main class="page">
      <div class="container">
        <div class="page-header">
          <h2>我的活动</h2>
          <p class="header-desc">管理您的活动报名、收藏和参与记录</p>
        </div>
        
        <div class="tabs-container">
          <div class="tabs">
            <button
              v-for="tab in TABS"
              :key="tab.key"
              v-show="!tab.creatorOnly || isCreator"
              :class="['tab-btn', { active: activeTab === tab.key }]"
              @click="handleTabChange(tab.key)"
            >
              <span class="tab-icon">{{ tab.icon }}</span>
              <span class="tab-label">{{ tab.label }}</span>
              <span v-if="getTabCount(tab.key) > 0" class="tab-count">
                {{ getTabCount(tab.key) }}
              </span>
            </button>
          </div>
        </div>
        
        <div v-if="loading" class="state-container">
          <div class="loader"></div>
          <p>加载中...</p>
        </div>
        
        <div v-else-if="error" class="state-container error">
          <p class="error-msg">❌ {{ error }}</p>
          <button class="retry-btn" @click="loadAllData">重新加载</button>
        </div>
        
        <div v-else class="content-area">
          <template v-if="activeTab === 'registered'">
            <div v-if="registeredActivities.length === 0" class="empty-container">
              <p class="empty-icon">📋</p>
              <p class="empty-text">还没有报名任何活动</p>
              <button class="go-activities-btn" @click="router.push('/activities')">去探索活动</button>
            </div>
            <div v-else class="activity-list">
              <div
                v-for="(item, index) in registeredActivities"
                :key="item.id"
                class="activity-card"
                :style="{ animationDelay: `${index * 80}ms` }"
              >
                <div class="card-header">
                  <h3 class="activity-title" @click="handleViewDetail(item.activity?.id || item.activityId)">
                    {{ item.activity?.title || item.title || '活动详情' }}
                  </h3>
                  <span :class="['status-badge', getActivityStatus(item).class]">
                    {{ getActivityStatus(item).text }}
                  </span>
                </div>
                
                <div class="card-body">
                  <div class="info-row">
                    <span class="info-icon">🕐</span>
                    <span>{{ formatDateTime(item.activity?.startTime || item.startTime) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">📍</span>
                    <span>{{ item.activity?.location || item.location || '线上活动' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">🏢</span>
                    <span>{{ item.activity?.organizer || item.organizer || '未知主办方' }}</span>
                  </div>
                </div>
                
                <div class="card-footer">
                  <div v-if="!item.canCancel" class="cancel-notice">
                    ⚠️ 活动开始前2小时内不可取消
                  </div>
                  <div class="action-buttons">
                    <button 
                      v-if="item.canCancel" 
                      class="btn btn-cancel"
                      @click="handleCancelRegistration(item.id, item.activity || item)"
                    >
                      取消报名
                    </button>
                    <button class="btn btn-detail" @click="handleViewDetail(item.activity?.id || item.activityId)">
                      查看详情
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>
          
          <template v-else-if="activeTab === 'favorites'">
            <div v-if="favoriteActivities.length === 0" class="empty-container">
              <p class="empty-icon">⭐</p>
              <p class="empty-text">还没有收藏任何活动</p>
              <button class="go-activities-btn" @click="router.push('/activities')">去发现活动</button>
            </div>
            <div v-else class="activity-list">
              <div
                v-for="(item, index) in favoriteActivities"
                :key="item.id"
                class="activity-card favorite-card"
                :style="{ animationDelay: `${index * 80}ms` }"
              >
                <div class="card-header">
                  <h3 class="activity-title" @click="handleViewDetail(item.id)">
                    {{ item.title || '活动详情' }}
                  </h3>
                  <span :class="['status-badge', getActivityStatus(item).class]">
                    {{ getActivityStatus(item).text }}
                  </span>
                </div>
                
                <div class="card-body">
                  <div class="info-row">
                    <span class="info-icon">🕐</span>
                    <span>{{ formatDateTime(item.startTime) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">📍</span>
                    <span>{{ item.location || '线上活动' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">🏢</span>
                    <span>{{ item.organizer || '未知主办方' }}</span>
                  </div>
                </div>
                
                <div class="card-footer">
                  <div class="action-buttons">
                    <button class="btn btn-remove" @click="handleRemoveFavorite(item.id)">
                      取消收藏
                    </button>
                    <button 
                      class="btn btn-primary" 
                      :disabled="getActivityStatus(item).text === '已结束'"
                      @click="handleRegisterFromFavorite(item.id)"
                    >
                      立即报名
                    </button>
                    <button class="btn btn-detail" @click="handleViewDetail(item.id)">
                      查看详情
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>
          
          <template v-else-if="activeTab === 'history'">
            <div v-if="historyActivities.length === 0" class="empty-container">
              <p class="empty-icon">📜</p>
              <p class="empty-text">还没有历史参与记录</p>
              <button class="go-activities-btn" @click="router.push('/activities')">去参加活动</button>
            </div>
            <div v-else class="activity-list">
              <div
                v-for="(item, index) in historyActivities"
                :key="item.id"
                class="activity-card history-card"
                :style="{ animationDelay: `${index * 80}ms` }"
              >
                <div class="card-header">
                  <h3 class="activity-title" @click="handleViewDetail(item.activity?.id || item.activityId)">
                    {{ item.activity?.title || item.title || '活动详情' }}
                  </h3>
                  <span class="status-badge status-ended">已结束</span>
                </div>
                
                <div class="card-body">
                  <div class="info-row">
                    <span class="info-icon">🕐</span>
                    <span>{{ formatDateTime(item.activity?.startTime || item.startTime) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">📍</span>
                    <span>{{ item.activity?.location || item.location || '线上活动' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">🏢</span>
                    <span>{{ item.activity?.organizer || item.organizer || '未知主办方' }}</span>
                  </div>
                </div>
                
                <div class="card-footer">
                  <div class="highlight-badge">✨ 可用于简历证明</div>
                  <div class="action-buttons">
                    <button class="btn btn-primary" @click="handleImportToResume(item.activity || item)">
                      导入简历
                    </button>
                    <button class="btn btn-cert" @click="handleExportCertificate(item.activity || item)">
                      导出证书
                    </button>
                    <button class="btn btn-detail" @click="handleViewDetail(item.activity?.id || item.activityId)">
                      查看详情
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>
          
          <template v-else-if="activeTab === 'created' && isCreator">
            <div v-if="myCreatedActivities.length === 0" class="empty-container">
              <p class="empty-icon">📝</p>
              <p class="empty-text">还没有发起任何活动</p>
            </div>
            <div v-else class="activity-list">
              <div
                v-for="(item, index) in myCreatedActivities"
                :key="item.id"
                class="activity-card created-card"
                :style="{ animationDelay: `${index * 80}ms` }"
              >
                <div class="card-header">
                  <h3 class="activity-title" @click="handleViewDetail(item.id)">
                    {{ item.title || '活动详情' }}
                  </h3>
                  <span :class="['status-badge', getActivityStatus(item).class]">
                    {{ getActivityStatus(item).text }}
                  </span>
                </div>
                
                <div class="card-body">
                  <div class="info-row">
                    <span class="info-icon">🕐</span>
                    <span>{{ formatDateTime(item.startTime) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">📍</span>
                    <span>{{ item.location || '线上活动' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-icon">👥</span>
                    <span>{{ item.currentPeople || 0 }}/{{ item.maxPeople || 0 }} 人报名</span>
                  </div>
                </div>
                
                <div class="card-footer">
                  <div class="action-buttons">
                    <button class="btn btn-share" @click="handleShareActivity(item)">
                      分享
                    </button>
                    <button class="btn btn-edit" @click="handleEditActivity(item.id)">
                      编辑活动
                    </button>
                    <button class="btn btn-detail" @click="handleViewDetail(item.id)">
                      查看详情
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.my-activities-view {
  min-height: 100vh;
  background: #f4f7fb;
}

.page {
  padding: 80px 20px 40px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
}

.header-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.tabs-container {
  background: #ffffff;
  border-radius: 16px;
  padding: 8px;
  margin-bottom: 24px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  animation: slideDown 0.4s ease-out;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab-btn {
  flex: 1;
  padding: 12px 16px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #3b82f6;
  background: #f8fafc;
}

.tab-btn.active {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #ffffff;
}

.tab-icon {
  font-size: 16px;
}

.tab-count {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  background: rgba(100, 116, 139, 0.2);
}

.tab-btn.active .tab-count {
  background: rgba(255, 255, 255, 0.25);
}

.state-container {
  text-align: center;
  padding: 60px 20px;
  background: #ffffff;
  border-radius: 16px;
}

.state-container.error .error-msg {
  color: #ef4444;
  margin: 0 0 16px;
}

.loader {
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
  border: 4px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.retry-btn,
.go-activities-btn {
  padding: 10px 24px;
  border: none;
  border-radius: 10px;
  background: #3b82f6;
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn:hover,
.go-activities-btn:hover {
  background: #2563eb;
}

.empty-container {
  text-align: center;
  padding: 80px 20px;
  background: #ffffff;
  border-radius: 16px;
}

.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 15px;
  color: #64748b;
  margin-bottom: 24px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.activity-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
  transition: all 0.3s;
  animation: cardFadeIn 0.5s ease-out backwards;
}

.activity-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.activity-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
  cursor: pointer;
  line-height: 1.4;
  flex: 1;
  transition: color 0.2s;
}

.activity-title:hover {
  color: #3b82f6;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-upcoming {
  background: rgba(250, 204, 21, 0.15);
  color: #854d0e;
}

.status-ongoing {
  background: rgba(34, 197, 94, 0.15);
  color: #166534;
}

.status-ended {
  background: rgba(100, 116, 139, 0.15);
  color: #475569;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #64748b;
}

.info-icon {
  font-size: 16px;
}

.card-footer {
  border-top: 1px solid #f1f5f9;
  padding-top: 16px;
}

.cancel-notice {
  font-size: 12px;
  color: #f59e0b;
  margin-bottom: 12px;
}

.highlight-badge {
  font-size: 12px;
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.1);
  padding: 6px 12px;
  border-radius: 8px;
  margin-bottom: 12px;
  display: inline-block;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.btn {
  padding: 8px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
}

.btn-cancel {
  border-color: #fca5a5;
  color: #ef4444;
}

.btn-cancel:hover {
  background: #fef2f2;
  border-color: #ef4444;
  color: #dc2626;
}

.btn-remove {
  border-color: #fde68a;
  color: #d97706;
}

.btn-remove:hover {
  background: #fffbeb;
  border-color: #d97706;
  color: #b45309;
}

.btn-cert {
  border-color: #c4b5fd;
  color: #7c3aed;
}

.btn-cert:hover {
  background: #f5f3ff;
  border-color: #7c3aed;
  color: #6d28d9;
}

.btn-share {
  border-color: #bae6fd;
  color: #0284c7;
}

.btn-share:hover {
  background: #f0f9ff;
  border-color: #0284c7;
  color: #0369a1;
}

.btn-edit {
  border-color: #bbf7d0;
  color: #16a34a;
}

.btn-edit:hover {
  background: #f0fdf4;
  border-color: #16a34a;
  color: #15803d;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .page {
    padding: 70px 16px 30px;
  }
  
  .tabs {
    flex-wrap: wrap;
  }
  
  .tab-btn {
    flex: 1 1 calc(50% - 4px);
    min-width: calc(50% - 4px);
  }
  
  .action-buttons {
    gap: 8px;
  }
  
  .btn {
    flex: 1;
    padding: 10px 12px;
    font-size: 12px;
  }
}
</style>
