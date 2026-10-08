<script setup>
import '../styles/support.css'
import { canSupport, canReview, canUseTickets, homeFor, roleName } from '../permissions'

import { ref, onMounted, onUnmounted, computed } from 'vue'
import { api, session, timeText, statusText } from '../api'
import Conversation from '../components/Conversation.vue'
import UiIcon from '../components/UiIcon.vue'

// --- Props 定義 ---
const props = defineProps({
  id: String
})

// --- 狀態定義 ---
const ticket = ref(null)
const origins = ref([])
const origin = ref(null)

const error = ref('')
const busy = ref(false)

let alive = true
let timer = null

// ==========================================
// 計算屬性
// ==========================================
const isAdmin = computed(() => {
  return canSupport(session.user?.role)
})

const canSend = computed(() => {
  return (
    ticket.value &&
    ticket.value.status !== 'CLOSED' &&
    (
      !isAdmin.value ||
      ticket.value.adminId === session.user?.id
    )
  )
})

// ==========================================
// 載入案件資料
// ==========================================
async function load() {
  try {
    const [t, o] = await Promise.all([
      api(`/aki/tickets/${props.id}`),
      api(`/aki/tickets/${props.id}/origins`)
    ])

    if (alive) {
      ticket.value = t
      origins.value = o
      error.value = ''
    }
  } catch (e) {
    if (alive) {
      error.value = e.message
    }
  }
}

// ==========================================
// 管理員案件操作
// 接手 / 結案
// ==========================================
async function action(name) {
  if (
    name === 'close' &&
    !confirm('確定將案件結案？結案後停止新增回覆。')
  ) {
    return
  }

  busy.value = true
  error.value = ''

  try {
    ticket.value = await api(
      `/aki/admin/tickets/${props.id}/${name}`,
      {
        method: 'POST'
      }
    )
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

// ==========================================
// 原始聊天展開 / 收合
// ==========================================
function toggleOrigin(room) {
  if (origin.value?.id === room.id) {
    origin.value = null
    return
  }
  origin.value = room
}

// ==========================================
// 生命週期
// ==========================================
onMounted(() => {
  load()
  window.addEventListener(
    'support-refresh',
    load
  )

  timer = setInterval(() => {
    if (
      document.visibilityState === 'visible'
    ) {
      load()
    }
  }, 10000)
})

onUnmounted(() => {
  alive = false

  clearInterval(timer)

  window.removeEventListener(
    'support-refresh',
    load
  )
})
</script>

<template>
  <div class="page ticket-detail-page">
    <!-- 返回 -->
    <RouterLink to="/aki/tickets" class="back-link">
      ← 返回案件列表
    </RouterLink>

    <!-- 錯誤訊息 -->
    <p v-if="error" class="error">
      {{ error }}
    </p>

    <!-- ======================================
         案件資料
         ====================================== -->
    <template v-if="ticket">
      <!-- 頁面標題 -->
      <div class="page-title">
        <div>
          <span class="eyebrow">
            CASE {{ ticket.ticketNo }}
            ·
            {{ ticket.categoryName }}
          </span>

          <h1>
            {{ ticket.subject }}
          </h1>

          <p class="muted">
            {{ ticket.memberName }}
            · 建立於
            {{ timeText(ticket.createdAt) }}

            <template v-if="ticket.games?.length || ticket.gameName">
              · {{ ticket.games?.length ? ticket.games.map(game => game.name).join('、') : ticket.gameName }}
            </template>
          </p>

        </div>

        <!-- 案件狀態 -->
        <span class="status" :class="ticket.status">
          {{
            ticket.status === 'CLOSED'
              ? '已結案'
              : statusText(ticket.status)
          }}
        </span>

      </div>

      <!-- ====================================
           詳情雙欄
           ==================================== -->
      <div class="detail-layout">

        <!-- ================================
             左側
             ================================ -->
        <aside class="ticket-sidebar">

          <!-- 問題摘要 -->
          <section class="summary-card">

            <span class="eyebrow">
              客服整理
            </span>

            <h2>
              問題摘要
            </h2>

            <p class="summary-text">
              {{ ticket.content }}
            </p>

            <div class="summary-footer">
              負責客服：
              {{
                ticket.adminId
                  ? '#' + ticket.adminId
                  : '等待接手'
              }}
              <br>
              最後更新：
              {{ timeText(ticket.updatedAt) }}
            </div>
          </section>

          <section v-if="!isAdmin && (ticket.games?.length || ticket.gameName)" class="member-related-games">
            <h3><UiIcon name="game" />相關遊戲</h3>
            <span v-for="game in ticket.games" :key="game.id" class="member-game-chip">{{game.name}}</span>
            <span v-if="!ticket.games?.length && ticket.gameName" class="member-game-chip">{{ticket.gameName}}</span>
          </section>
          <!-- ==================================
               管理員操作
               ================================== -->
          <div v-if="isAdmin" class="action-card">
            <!-- 接手 -->
            <button v-if="
              !ticket.adminId &&
              ticket.status !== 'CLOSED'
            " class="primary full" :disabled="busy" @click="action('claim')">
              {{
                busy
                  ? '處理中…'
                  : '接手案件'
              }}
            </button>

            <!-- 結案 -->
            <button v-if="
              ticket.adminId === session.user?.id &&
              ticket.status !== 'CLOSED'
            " class="secondary full" :disabled="busy" @click="action('close')">
              {{
                busy
                  ? '處理中…'
                  : '處理完成，結案'
              }}
            </button>
          </div>

          <!-- ==================================
               原始聊天
               ================================== -->
          <div v-if="origins.length" class="origin-links">

            <span class="origin-links-label">
              轉案來源
            </span>

            <button v-for="r in origins" :key="r.id" type="button" class="origin-link"
              :aria-expanded="origin?.id === r.id" :class="{
                active:
                  origin?.id === r.id
              }" @click="toggleOrigin(r)">

              <span class="origin-link-icon">
                ◇
              </span>

              <span class="origin-link-text">

                {{
                  origin?.id === r.id
                    ? '收起'
                    : '查看'
                }}

                原始聊天 #{{ r.id }}

              </span>

              <span class="origin-link-arrow">

                {{
                  origin?.id === r.id
                    ? '↑'
                    : '↗'
                }}

              </span>
            </button>
          </div>
        </aside>

        <!-- ================================
             右側案件對話
             ================================ -->
        <section class="discussion-card">

          <div class="discussion-heading">

            <h2>
              案件對話
            </h2>

            <span class="muted">
              在這裡接續回覆
            </span>

          </div>

          <Conversation kind="tickets" :id="ticket.id" :can-send="canSend" :closed-text="ticket.status === 'CLOSED'
            ? '此案件已結案，對話紀錄保留。'
            : '請先由負責客服接手此案件。'
            " />

        </section>

      </div>

      <!-- ====================================
           原始聊天展開區
           ==================================== -->

      <section v-if="origin" class="origin-panel" aria-label="原始聊天紀錄">

        <div class="discussion-heading">

          <div>

            <span class="origin-panel-label">
              ORIGINAL CONVERSATION
            </span>

            <h2>
              轉案前的聊天 #{{ origin.id }}
            </h2>

          </div>

          <button type="button" class="origin-close-button" @click="origin = null">
            關閉
          </button>

        </div>

        <Conversation kind="rooms" :id="origin.id" :can-send="false" closed-text="原始聊天僅供查閱，請在案件對話繼續回覆。" />

      </section>

    </template>

    <!-- 載入中 -->
    <p v-else-if="!error" class="empty">
      載入案件中…
    </p>

  </div>
</template>