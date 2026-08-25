<script setup>
import AppTabBar from '../components/AppTabBar.vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const helpSections = [
  {
    title: '加水印如何使用',
    description: '选择图片，输入水印文字，可调整字体、字号、字体颜色、透明度，支持修改水印位置、旋转角度，开启重复平铺可以铺满整张图片，设置完成后点击生成即可导出带水印图片。',
  },
  {
    title: '去水印如何使用',
    description: '上传图片，调整画笔大小，涂抹需要去除水印的区域，点击执行去除，等待AI处理完成，预览无误后保存图片到本地相册。',
  },
  {
    title: '批量处理',
    description: '支持多张图片一次性导入，统一设置水印参数，一键批量处理，全部完成后可以逐个保存图片。分为批量加水印、批量去水印两种模式。',
  },
  {
    title: '历史记录',
    description: '查看之前处理过的图片记录，点击管理可以多选删除记录，点击记录可以弹窗预览，支持跳回编辑页面二次修改图片。',
  },
  {
    title: '保存与导出',
    description: '处理完成后点击保存，图片会保存到手机相册。小程序会有权限弹窗，请允许相册存储权限，否则图片无法保存。',
  },
  {
    title: '常见问题',
    description: '1.图片导出失败：请检查图片大小，建议不要上传过大图片，确认已授予相册存储权限。\n2.去水印效果不好：涂抹区域尽量完整覆盖水印，可切换高清模式获得更好修复效果。\n3.页面加载卡顿：图片像素过高会导致处理变慢，建议压缩原图后再操作。',
  },
  {
    title: '温馨提示',
    description: '本工具仅用于个人学习使用，请不要用于侵权、伪造类用途，请尊重图片版权。',
  },
]
</script>

<template>
  <div class="watermark-help-page">
    <AppTabBar />

    <main class="help-content">
      <header class="help-heading">
        <button class="back-button" type="button" aria-label="返回水印工具" @click="router.push('/ai-original')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m15 5-7 7 7 7" />
          </svg>
        </button>
        <h1>使用帮助</h1>
        <span class="heading-spacer" aria-hidden="true"></span>
      </header>

      <section class="help-list" aria-label="水印工具使用帮助">
        <article v-for="section in helpSections" :key="section.title" class="help-block">
          <h2>{{ section.title }}</h2>
          <p>{{ section.description }}</p>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
.watermark-help-page {
  min-height: 100vh;
  padding-top: 60px;
  color: #1f2937;
  background: #f5f7fa;
}

.help-content {
  width: min(1440px, calc(100% - 32px));
  margin: 0 auto;
  padding: 0 0 32px;
}

.help-heading {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  min-height: 82px;
}

.help-heading h1 {
  margin: 0;
  color: #17233a;
  font-size: 22px;
  font-weight: 800;
  text-align: center;
}

.back-button {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 0;
  border-radius: 8px;
  color: #334155;
  background: transparent;
}

.back-button:hover,
.back-button:focus-visible {
  background: #e8eef5;
  outline: none;
}

.back-button svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.help-list {
  display: grid;
  gap: 12px;
}

.help-block {
  padding: 25px 24px 24px;
  border-radius: 8px;
  background: #fff;
}

.help-block h2 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 17px;
  font-weight: 800;
}

.help-block p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-line;
}

@media (max-width: 720px) {
  .help-content {
    width: min(100% - 24px, 560px);
  }

  .help-heading {
    min-height: 64px;
  }

  .help-block {
    padding: 20px 18px;
  }

  .help-block h2 {
    font-size: 16px;
  }

  .help-block p {
    font-size: 13px;
  }
}
</style>
