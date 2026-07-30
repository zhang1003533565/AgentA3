<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { createComment, getCommentList, getPostDetail, toggleCommentLike, toggleFollowUser, togglePostFavorite, togglePostLike } from '../api/forum'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const comments = ref([])
const draft = ref('')
const error = ref('')
const loading = ref(true)
const rows = (value) => Array.isArray(value) ? value : value?.content || value?.records || value?.list || []
async function load() {
  loading.value=true
  try {
    const [detail, commentData] = await Promise.all([getPostDetail(route.params.postId), getCommentList({ postId:route.params.postId,page:0,size:100 })])
    post.value=detail; comments.value=rows(commentData)
  } catch(cause){error.value=cause.message} finally{loading.value=false}
}
async function comment() { if(!draft.value.trim())return; try{await createComment({postId:Number(route.params.postId),content:draft.value.trim()});draft.value='';await load()}catch(cause){error.value=cause.message} }
async function likePost(){try{const r=await togglePostLike(post.value.id);post.value.likeCount=r?.likeCount??post.value.likeCount;post.value.isLiked=r?.liked??!post.value.isLiked}catch(cause){error.value=cause.message}}
async function likeComment(item){try{const r=await toggleCommentLike(item.id);item.likeCount=r?.likeCount??item.likeCount;item.isLiked=r?.liked??!item.isLiked}catch(cause){error.value=cause.message}}
onMounted(load)
</script>
<template><div class="feature-page"><AppTabBar/><main class="post-layout">
  <button class="back" @click="router.push('/forum')">‹ 返回论坛</button><div v-if="error" class="feature-error">{{error}}</div><div v-if="loading" class="feature-empty">正在加载帖子…</div>
  <template v-else-if="post"><article class="feature-card post-detail"><header><span class="avatar" @click="router.push(`/forum/users/${post.userId}`)">{{(post.username||'匿').slice(0,1)}}</span><div><strong @click="router.push(`/forum/users/${post.userId}`)">{{post.username||'匿名用户'}}</strong><small>{{post.createTime}}</small></div><button class="feature-button" @click="toggleFollowUser(post.userId)">关注</button></header><h1>{{post.title}}</h1><p>{{post.content}}</p><footer><button @click="likePost">{{post.isLiked?'已点赞':'点赞'}} {{post.likeCount||0}}</button><button @click="togglePostFavorite(post.id)">收藏</button><span>{{post.viewCount||0}} 浏览</span></footer></article>
  <section class="feature-card comments"><h2>评论 {{comments.length}}</h2><form @submit.prevent="comment"><textarea v-model="draft" class="feature-textarea" placeholder="理性交流，分享你的看法"></textarea><button class="feature-button feature-button--primary">发表评论</button></form><div v-if="!comments.length" class="feature-empty">暂无评论</div><article v-for="item in comments" :key="item.id"><span class="avatar">{{(item.username||'匿').slice(0,1)}}</span><div><header><strong>{{item.username||'匿名用户'}}</strong><time>{{item.createTime}}</time></header><p>{{item.content}}</p><button @click="likeComment(item)">{{item.isLiked?'已点赞':'点赞'}} {{item.likeCount||0}}</button></div></article></section></template>
</main></div></template>
<style scoped>
.post-layout{width:min(850px,calc(100% - 32px));margin:auto;padding:24px 0 48px}.back{margin-bottom:16px;color:#45657f;background:transparent;font-weight:700}.post-detail,.comments{padding:25px}.post-detail header{display:flex;align-items:center;gap:11px}.post-detail header>div{flex:1}.post-detail strong,.post-detail small{display:block}.post-detail small{margin-top:4px;color:#8996a3}.avatar{display:grid;flex:0 0 42px;place-items:center;width:42px;height:42px;border-radius:50%;color:#315f8c;background:#e7eff5;font-weight:800}.post-detail h1{margin:25px 0 12px}.post-detail>p{min-height:120px;color:#43576c;line-height:1.9;white-space:pre-wrap}.post-detail footer{display:flex;gap:22px;padding-top:18px;border-top:1px solid #e8edf1;color:#718096}.post-detail footer button{color:inherit;background:transparent}.comments{margin-top:16px}.comments h2{margin-top:0}.comments form{display:flex;align-items:end;gap:10px;margin-bottom:25px}.comments form textarea{min-height:76px}.comments>article{display:flex;gap:12px;padding:18px 0;border-top:1px solid #e9edf1}.comments>article>div{flex:1}.comments article header{display:flex;justify-content:space-between}.comments time{color:#8b98a4;font-size:11px}.comments article p{color:#4f6275;line-height:1.7}.comments article button{color:#60768b;background:transparent}
</style>
