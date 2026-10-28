<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { api, session } from '../api'
import { hasPermission } from '../permissions'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import AdminDashboard from './AdminDashboard.vue'
import UiIcon from '../components/UiIcon.vue'
import '../styles/admin-management.css'
import { backofficeModules } from '../backoffice-modules'
const reviewer = computed(() => session.user?.role === 'REVIEWER')
const can = code => hasPermission(session.user, code)
const games = ref([]), publishers = ref([]), gameTotal = ref(null)
const loading = ref(false), errors = ref({})
let generation = 0, alive = true
const tasks = computed(() => [
  {
    permission: 'GAME_REVIEW',
    icon: 'game',
    name: '待審核遊戲',
    count: gameTotal.value,
    to: '/admin/games?mode=pending',
    note: '僅包含已送出上架申請的遊戲'
  },
  {
    permission: 'PUBLISHER_REVIEW',
    icon: 'building',
    name: '待審核發行商',
    count: errors.value.publishers ? null : publishers.value.length,
    to: '/admin/publishers',
    note: '查看公司資料與申請內容'
  },
  {
    permission: 'MEMBER_SUSPEND',
    icon: 'users',
    name: '會員管理',
    to: '/admin/members',
    note: '查詢會員並處理停權與復權'
  },
  {
    permission: 'DASHBOARD_VIEW',
    icon: 'dashboard',
    name: '平台概況',
    to: '/admin',
    note: '查看會員、遊戲與營收統計'
  },
  ...Object.entries(backofficeModules).filter(([key]) => key !== 'review-reports').map(([key, c]) => ({
    permission: c.permission,
    icon: c.icon,
    name: c.title,
    to: '/admin/' + key,
    note: c.note
  })
  )].filter(item => can(item.permission)))
async function load() {
  const current = ++generation
  loading.value = true;
  errors.value = {};
  games.value = [];
  publishers.value = [];
  gameTotal.value = null
  await Promise.all([
    can('GAME_REVIEW') && (async () => {
      try {
        const result = await api('/admin/games/search?page=0&size=5&keyword=&status=PENDING_REVIEW')
        if (alive && current === generation) {
          games.value = result.content;
          gameTotal.value = result.totalElements
        }
      } catch (e) {
        if (alive && current === generation) errors.value.games = e.message
      }
    })(),
    can('PUBLISHER_REVIEW') && (async () => {
      try {
        const result = await api('/admin/publishers/pending');
        if (alive && current === generation) publishers.value = result
      }
      catch (e) {
        if (alive && current === generation) errors.value.publishers = e.message
      }
    })()
  ])
  if (alive && current === generation) loading.value = false
}
watch(() => [
  session.user?.id,
  session.user?.role,
  [...(session.user?.permissions || [])].sort().join(',')],
  load,
  { immediate: true }
)
onBeforeUnmount(() => { alive = false; generation++ })
</script>
<template>
  <section class="page admin-management-page staff-overview">
    <AdminPageHeader :title="reviewer ? '審核工作總覽' : 'STAFF 工作總覽'" eyebrow="MY WORKSPACE" icon="dashboard"
      :description="reviewer ? '先處理待審核遊戲與發行商申請，再查看相關管理與紀錄。' : '依你的個別授權，彙整待辦工作與可使用的管理功能。'" />
    <div class="overview-toolbar">
      <span>你好，{{ session.user?.displayName || session.user?.username }}</span>
      <button :disabled="loading" @click="load">
        {{ loading ? '更新中…' : '重新整理' }}
      </button>
    </div>
    <nav class="overview-cards" aria-label="我的工作摘要">
      <RouterLink v-for="task in tasks" :key="task.permission" :to="task.to" class="overview-card">
        <UiIcon :name="task.icon" />
        <h2>{{ task.name }}</h2>
        <strong v-if="'count' in task">{{ loading ? '…' : task.count ?? '—' }}</strong>
        <small>{{ task.note }}</small>
        <span>前往處理 ↗</span>
      </RouterLink>
    </nav>
    <p v-if="!tasks.length && !can('TICKET_HANDLE')" class="management-notice">
      目前沒有已串接的工作功能，請聯繫最高管理員確認授權。
    </p>
    <div class="overview-queues">
      <section v-if="can('GAME_REVIEW')" class="overview-queue" :aria-busy="loading">
        <div class="management-section-heading">
          <h2>待審核遊戲</h2>
          <RouterLink to="/admin/games?mode=pending">查看全部 ↗</RouterLink>
        </div>
        <p v-if="loading" role="status">正在載入待審核遊戲…</p>
        <p v-else-if="errors.games" class="error" role="alert">遊戲資料載入失敗：{{ errors.games }}</p>
        <p v-else-if="!games.length">目前沒有待審核遊戲。</p>
        <ul v-else>
          <li v-for="game in games" :key="game.gameId">
            <span>
              <strong>{{ game.gameName }}</strong>
              <small>遊戲 #{{ game.gameId }} · 待審核</small>
            </span>
            <RouterLink to="/admin/games?mode=pending">前往審核</RouterLink>
          </li>
        </ul>
        <RouterLink class="overview-history" to="/admin/games?mode=history">查看遊戲審核紀錄</RouterLink>
      </section>
      <section v-if="can('PUBLISHER_REVIEW')" class="overview-queue" :aria-busy="loading">
        <div class="management-section-heading">
          <h2>待審核發行商</h2>
          <RouterLink to="/admin/publishers">查看全部 ↗</RouterLink>
        </div>
        <p v-if="loading" role="status">正在載入發行商申請…</p>
        <p v-else-if="errors.publishers" class="error" role="alert">發行商資料載入失敗：{{ errors.publishers }}</p>
        <p v-else-if="!publishers.length">目前沒有待審核發行商申請。</p>
        <ul v-else>
          <li v-for="item in publishers.slice(0, 5)" :key="item.applicationId">
            <span>
              <strong>{{ item.companyName || item.username }}</strong>
              <small>申請 #{{ item.applicationId }} · {{ item.username }}</small>
            </span>
            <RouterLink to="/admin/publishers">前往審核</RouterLink>
          </li>
        </ul>
        <RouterLink class="overview-history" to="/admin/publishers?mode=history">查看發行商審核紀錄</RouterLink>
      </section>
    </div>
    <AdminDashboard v-if="can('TICKET_HANDLE')" />

  </section>
</template>
<style scoped>
.overview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0;
  color: #527589;
  gap: 16px
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 18px;
  margin-bottom: 24px
}

.overview-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 24px;
  border: 1px solid #d5e7ef;
  border-radius: 14px;
  background: #fff;
  color: #245870;
  text-decoration: none
}

.overview-card:hover {
  border-color: #168da5;
  background: #f3fbfd
}

.overview-card svg {
  width: 28px;
  height: 28px;
  color: #0695ad
}

.overview-card h2 {
  font-size: 17px;
  margin: 0
}

.overview-card strong {
  font-size: 32px;
  color: #087c94
}

.overview-card small {
  line-height: 1.8;
  color: #6a8393
}

.overview-card>span {
  font-size: 13px;
  margin-top: auto;
  color: #087c94
}

.overview-queues {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
  gap: 20px
}

.overview-queue {
  background: white;
  padding: 24px;
  border: 1px solid #d5e7ef;
  border-radius: 14px
}

.overview-queue ul {
  list-style: none;
  padding: 0;
  margin: 0
}

.overview-queue li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #e8f0f5;
  padding: 16px 0
}

.overview-queue li span {
  display: grid;
  gap: 6px;
  min-width: 0;
  overflow-wrap: anywhere
}

.overview-queue small {
  color: #6a8393
}

.overview-queue a {
  color: #087c94;
  font-size: 13px
}

.overview-queue li a {
  flex-shrink: 0
}

.overview-history {
  display: inline-block;
  margin-top: 20px
}

.overview-pending {
  color: #617c90;
  padding: 18px;
  background: #edf5f9;
  border-radius: 10px;
  margin-top: 24px
}

.staff-overview :deep(.dashboard-page) {
  margin-top: 28px
}

@media(max-width:600px) {
  .overview-cards {
    grid-template-columns: 1fr
  }

  .overview-queue {
    padding: 16px
  }

  .overview-toolbar {
    align-items: flex-start
  }
}
</style>
