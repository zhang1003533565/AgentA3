<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getHotPosts, getPostList, getTopicList, publishPost, togglePostLike } from '../api/forum'

const router = useRouter()
const posts = ref([])
const hotPosts = ref([])
const topics = ref([])
const topicId = ref('')
const loading = ref(true)
const error = ref('')
const showPublish = ref(false)
const publishing = ref(false)
const form = ref({ title:'',content:'',topicId:'',images:[] })
const rows = (value) => Array.isArray(value) ? value : value?.content || value?.records || value?.list || []
const parseImages = (value) => { if (Array.isArray(value)) return value; try { return JSON.parse(value||'[]') } catch { return [] } }

async function load() {
  loading.value = true
  try { posts.value = rows(await getPostList({ page:0,size:30,topicId:topicId.value || undefined })) } catch (cause) { error.value = cause.message } finally { loading.value = false }
}
async function like(post) {
  try { const result = await togglePostLike(post.id); post.isLiked = result?.liked ?? !post.isLiked; post.likeCount = result?.likeCount ?? post.likeCount } catch (cause) { error.value = cause.message }
}
async function publish() {
  publishing.value = true
  try { await publishPost({ ...form.value, topicId:Number(form.value.topicId) }); showPublish.value=false; form.value={title:'',content:'',topicId:'',images:[]}; await load() } catch (cause) { error.value=cause.message } finally { publishing.value=false }
}
watch(topicId, load)
onMounted(async () => {
  try {
    const [topicData, hotData] = await Promise.all([getTopicList({ page:0,size:50 }), getHotPosts({ page:0,size:8 })])
    topics.value = rows(topicData); hotPosts.value = rows(hotData)
  } catch (cause) { error.value = cause.message }
  await load()
})
</script>

<template>
  <div class="feature-page"><AppTabBar /><main class="feature-container">
    <header class="feature-heading"><div><h1>校园论坛</h1><p>分享校园生活，围绕话题交流经验</p></div><button class="feature-button feature-button--primary" @click="showPublish=true">发布帖子</button></header>
    <div v-if="error" class="feature-error">{{ error }}</div>
    <div class="forum-layout">
      <aside class="feature-card topic-panel"><h2>话题</h2><button :class="{active:!topicId}" @click="topicId=''">全部动态</button><button v-for="topic in topics" :key="topic.id" :class="{active:String(topicId)===String(topic.id)}" @click="topicId=topic.id"><span>{{ topic.name || topic.topicName }}</span><small>{{ topic.postCount || 0 }}</small></button></aside>
      <section class="forum-feed"><div v-if="loading" class="feature-empty">正在加载帖子…</div><div v-else-if="!posts.length" class="feature-empty">暂无帖子</div><article v-for="post in posts" :key="post.id" class="feature-card post-card" @click="router.push(`/forum/posts/${post.id}`)"><header><span class="avatar" @click.stop="router.push(`/forum/users/${post.userId}`)">{{ (post.username||'匿').slice(0,1) }}</span><div><strong @click.stop="router.push(`/forum/users/${post.userId}`)">{{ post.username || '匿名用户' }}</strong><small>{{ post.createTime }}</small></div><em v-if="post.topicName">{{ post.topicName }}</em></header><h2 v-if="post.title">{{ post.title }}</h2><p>{{ post.content }}</p><div v-if="parseImages(post.images).length" class="post-images"><img v-for="image in parseImages(post.images).slice(0,3)" :key="image" :src="image" alt="" /></div><footer><button @click.stop="like(post)"><i :class="{active:post.isLiked}"></i>{{ post.likeCount || 0 }} 点赞</button><span>{{ post.commentCount || 0 }} 评论</span><span>{{ post.viewCount || 0 }} 浏览</span></footer></article></section>
      <aside class="feature-card hot-panel"><h2>热门讨论</h2><button v-for="(post,index) in hotPosts" :key="post.id" @click="router.push(`/forum/posts/${post.id}`)"><b>{{ index+1 }}</b><span>{{ post.title || post.content }}</span></button></aside>
    </div>
  </main>
  <div v-if="showPublish" class="feature-modal-mask" @click.self="showPublish=false"><form class="feature-modal feature-form" @submit.prevent="publish"><div class="feature-modal__head"><h2>发布帖子</h2><button type="button" class="feature-modal__close" @click="showPublish=false">×</button></div><label>话题<select v-model="form.topicId" class="feature-select" required><option value="">请选择话题</option><option v-for="topic in topics" :key="topic.id" :value="topic.id">{{ topic.name || topic.topicName }}</option></select></label><label>标题<input v-model="form.title" class="feature-input" maxlength="100" /></label><label>正文<textarea v-model="form.content" class="feature-textarea" required maxlength="3000"></textarea></label><button class="feature-button feature-button--primary" :disabled="publishing">{{ publishing?'发布中…':'确认发布' }}</button></form></div>
  </div>
</template>

<style scoped>
.forum-layout{display:grid;grid-template-columns:220px minmax(0,1fr) 260px;gap:18px}.topic-panel,.hot-panel{height:fit-content;padding:18px}.topic-panel h2,.hot-panel h2{margin:0 0 14px;font-size:18px}.topic-panel>button{display:flex;justify-content:space-between;width:100%;padding:10px 12px;border-radius:7px;color:#5c6f83;background:transparent;text-align:left}.topic-panel>button.active{color:#294c68;background:#eaf1f6;font-weight:750}.topic-panel small{color:#8d99a5}.forum-feed{display:grid;gap:14px}.post-card{padding:20px;cursor:pointer}.post-card header{display:flex;align-items:center;gap:10px}.post-card header>div{flex:1}.post-card header strong,.post-card header small{display:block}.post-card header small{margin-top:3px;color:#8b98a4;font-size:11px}.post-card header em{padding:4px 8px;border-radius:999px;color:#466782;background:#edf3f7;font-size:11px;font-style:normal}.post-card h2{margin:17px 0 7px;color:#26384d;font-size:18px}.post-card>p{margin:0;color:#56697d;line-height:1.75;white-space:pre-wrap}.post-images{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:14px}.post-images img{width:100%;height:150px;border-radius:7px;object-fit:cover}.post-card footer{display:flex;gap:24px;margin-top:17px;padding-top:14px;border-top:1px solid #edf1f4;color:#7b8997;font-size:12px}.post-card footer button{color:inherit;background:transparent}.post-card footer i{display:inline-block;width:9px;height:9px;margin-right:6px;border:1px solid #8293a4;border-radius:50%}.post-card footer i.active{border-color:#b85e56;background:#b85e56}.hot-panel button{display:flex;gap:9px;width:100%;padding:11px 0;border-bottom:1px solid #edf1f4;color:#4a5f73;background:transparent;text-align:left}.hot-panel b{color:#8293a4}.hot-panel span{display:-webkit-box;overflow:hidden;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:2}@media(max-width:1050px){.forum-layout{grid-template-columns:190px 1fr}.hot-panel{display:none}}@media(max-width:700px){.forum-layout{grid-template-columns:1fr}.topic-panel{display:flex;gap:7px;overflow:auto}.topic-panel h2{display:none}.topic-panel>button{width:auto;white-space:nowrap}}
</style>
