<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api, session } from '../api'
import { publisherFunctions, publisherLink, publisherGroups, publisherIcons } from '../publisher-functions'
import UiIcon from '../components/UiIcon.vue'
import '../styles/publisher.css'

const data = ref(null)
const error = ref('')
onMounted(async () => {
  try { data.value = await api('/aki/publisher/overview') }
  catch (e) { error.value = e.message }
})
</script>

<template>
  <section class="member-support-home publisher-home">
    <header class="publisher-welcome">
      <div><span class="eyebrow">PUBLISHER CENTER</span><h1>發行商主頁</h1><p>{{ session.user?.username }}，歡迎回來。管理遊戲，讓下一個作品準備好與玩家見面。</p></div>
      <RouterLink class="primary publisher-new-link" :to="publisherLink('new')"><UiIcon name="plus" />新增遊戲</RouterLink>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="publisher-metrics">
      <article><span class="publisher-metric-icon"><UiIcon name="game" /></span><div><span>我的遊戲</span><p><strong>{{ data?.gameCount ?? '—' }}</strong><small>款</small></p></div></article>
      <article><span class="publisher-metric-icon"><UiIcon name="shield" /></span><div><span>待審核申請</span><p><strong>{{ data?.pendingCount ?? '—' }}</strong><small>筆</small></p></div></article>
      <article><span class="publisher-metric-icon"><UiIcon name="users" /></span><div><span>購買會員</span><p><strong>{{ data?.buyerCount ?? '—' }}</strong><small>人</small></p></div></article>
    </div>
    <div class="publisher-section-heading"><div><h2>工作捷徑</h2><p>從遊戲資料、素材管理到上架進度，一次找到需要的功能。</p></div><span>{{ publisherFunctions.length }} 項功能</span></div>
    <div class="publisher-function-grid">
      <section v-for="group in publisherGroups" :key="group.title" class="publisher-function-group">
        <header><span class="publisher-group-icon"><UiIcon :name="group.icon" /></span><h2>{{ group.title }}</h2></header>
        <RouterLink v-for="mode in group.modes" :key="mode" class="publisher-function-link" :to="publisherLink(mode)">
          <UiIcon :name="publisherIcons[mode]" /><span><strong>{{ publisherFunctions.find(x => x.mode === mode).label }}</strong><small>{{ publisherFunctions.find(x => x.mode === mode).description }}</small></span><UiIcon name="chevron" />
        </RouterLink>
      </section>
    </div>
    <footer class="publisher-home-note"><UiIcon name="shield" /><p>購買會員僅計算已付款訂單；同一會員購買多款遊戲仍計為一人。遊戲下架後有 24 小時恢復機會。</p></footer>
  </section>
</template>
