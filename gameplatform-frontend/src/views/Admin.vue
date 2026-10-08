<script setup>
import '../styles/support.css'
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'

import SearchBar from '../components/SearchBar.vue'
import Conversation from '../components/Conversation.vue'
import UiIcon from '../components/UiIcon.vue'

import { api, session, timeText } from '../api'

// ==============================
// Router
// ==============================
const router = useRouter()
const route = useRoute()

// ==============================
// 聊天室狀態
// ==============================
const rooms = ref([])
const room = ref(null)
const status = ref('OPEN')
const mine = ref(false)
const page = ref(0)
const search = ref('')

// ==============================
// UI 狀態
// ==============================
const error = ref('')
const busy = ref(false)
const modal = ref(false)
const formError = ref('')
const transferPanel = ref(null)
const transferButton = ref(null)
const transferRoomId = ref(null)
async function closeTransfer() {
  if (busy.value) return
  modal.value = false
  await nextTick()
  transferButton.value?.focus()
}

// ==============================
// 轉案件表單
// ==============================
const categories = ref([])
const categoryId = ref(null)
const selectedGames = ref([])
const gameIds = computed(() => selectedGames.value.map(game => game.id))

// 遊戲搜尋下拉選單
const games = ref([])
const gameKeyword = ref('')
const gameDropdownOpen = ref(false)
const gameLoading = ref(false)
const subject = ref('')
const summary = ref('')

// ==============================
// Computed
// ==============================
// 是否選擇「遊戲」分類
const isGame = computed(() => {
  return categories.value.find(
    category => category.id === categoryId.value
  )?.name === '遊戲'
})

// 搜尋結果與已選清單分開，重新搜尋不會清除已選項目。
const filteredGames = computed(() => games.value)

// 目前聊天室是否由登入客服負責
const own = computed(() => {
  return room.value?.adminId === session.user?.id
})

// 是否允許傳送訊息
const canSend = computed(() => {
  return own.value && room.value?.status === 'OPEN'
})

// ==============================
// 輪詢 / Request 控制
// ==============================
let alive = true
let timer = null

// 用來避免切換聊天室時舊 Request 覆蓋新聊天室
let selection = 0

// 用來避免列表搜尋時舊 Request 覆蓋新 Request
let listRequest = 0

// ==============================
// 載入聊天室
// ==============================
async function load() {
  const request = ++listRequest

  try {
    const params = new URLSearchParams({
      status: status.value,
      mine: String(mine.value),
      page: String(page.value),
      q: search.value
    })

    const list = await api(`/rooms?${params.toString()}`)

    if (alive && request === listRequest) {
      rooms.value = list
    }

    // 同時更新目前選中的聊天室資料
    if (room.value) {
      const roomId = room.value.id
      const token = selection
      const latestRoom = await api(`/rooms/${roomId}`)

      if (alive && selection === token) {
        room.value = latestRoom
      }
    }
  } catch (e) {
    if (alive) {
      error.value = e.message
    }
  }
}

// ==============================
// 搜尋聊天室
// ==============================
function searchNow(keyword) {
  search.value = keyword
  page.value = 0
  load()
}

// ==============================
// 選擇聊天室
// ==============================
function pick(selectedRoom) {
  if (modal.value || busy.value) return
  selection++

  room.value = selectedRoom
  error.value = ''
  // 將目前對話放在網址，重整後由既有 onMounted 流程還原。
  router.replace({ path: route.path, query: { ...route.query, room: String(selectedRoom.id) } })
}

// ==============================
// 聊天室操作
// claim：接手
// close：結束
// ==============================
async function action(name) {
  if (!room.value) return

  if (
    name === 'close' &&
    !confirm('確定問題已解決並結束對話？')
  ) {
    return
  }

  busy.value = true
  error.value = ''

  try {
    let path

    if (name === 'claim') {
      path = `/aki/admin/rooms/${room.value.id}/claim`
    } else if (name === 'close') {
      path = `/rooms/${room.value.id}/close`
    } else {
      return
    }

    room.value = await api(path, {
      method: 'POST'
    })

    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

// ==============================
// 遊戲下拉選單
// ==============================
let gameRequest = 0
let gameSearchTimer
let gameBlurTimer
async function loadGames() {
  const version = ++gameRequest
  const keyword = gameKeyword.value.trim()
  gameLoading.value = true
  try {
    const catalog = await api('/games?q=' + encodeURIComponent(keyword))
    const result = catalog
    if (version === gameRequest) games.value = result
  } catch (e) {
    if (version === gameRequest) { games.value = []; formError.value = e.message }
  } finally {
    if (version === gameRequest) gameLoading.value = false
  }
}
function openGameDropdown() {
  clearTimeout(gameBlurTimer)
  gameDropdownOpen.value = true
  clearTimeout(gameSearchTimer)
  loadGames()
}
function closeGameDropdown() {
  gameBlurTimer = setTimeout(() => { gameDropdownOpen.value = false }, 120)
}
function onGameInput() {
  gameRequest++
  games.value = []
  gameLoading.value = true
  gameDropdownOpen.value = true
  clearTimeout(gameSearchTimer)
  gameSearchTimer = setTimeout(loadGames, 200)
}
function selectGame(game) {
  if (gameIds.value.includes(game.id)) return
  if (selectedGames.value.length >= 20) { formError.value = '每個案件最多 20 款遊戲'; return }
  selectedGames.value.push({ id: game.id, name: game.name })
  clearSelectedGame()
  gameDropdownOpen.value = false
}
// 清除搜尋文字，不影響已選遊戲。
function clearSelectedGame() {
  gameKeyword.value = ''
  onGameInput()
}
function removeGame(id) {
  selectedGames.value = selectedGames.value.filter(game => game.id !== id)
}
function resetGameState() {
  gameRequest++
  clearTimeout(gameSearchTimer)
  clearTimeout(gameBlurTimer)
  selectedGames.value = []
  games.value = []
  gameKeyword.value = ''
  gameLoading.value = false
  gameDropdownOpen.value = false
}

// ==============================
// 開啟轉案件 Modal
// ==============================
async function openModal() {
  if (!canSend.value || busy.value) return
  transferRoomId.value = room.value.id
  formError.value = ''

  categoryId.value = null
  resetGameState()

  subject.value = ''

  summary.value =
    `問題：

    已確認事項：

    已嘗試處理：

    待處理事項：`

  modal.value = true
  await nextTick()
  transferPanel.value?.focus()

  try {
    categories.value = await api('/categories')
  } catch (e) {
    formError.value = e.message
  }
}

// ==============================
// 即時客服 → 案件
// ==============================
async function transfer() {
  if (!transferRoomId.value || busy.value) return

  busy.value = true
  formError.value = ''

  try {
    const ticket = await api(
      `/aki/admin/rooms/${transferRoomId.value}/escalate`,
      {
        method: 'POST',
        body: {
          categoryId: categoryId.value,
          gameIds: isGame.value
            ? gameIds.value
            : [],
          subject: subject.value.trim(),
          summary: summary.value.trim()
        }
      }
    )

    modal.value = false

    router.push(`/aki/tickets/${ticket.id}`)
  } catch (e) {
    formError.value = e.message
  } finally {
    busy.value = false
  }
}

// ==============================
// Watch
// ==============================

// 切換聊天室狀態 / 我的聊天室
watch([status, mine], () => {
  page.value = 0
  load()
})

// 換頁
watch(page, () => {
  load()
})

// 更換分類時重置遊戲；選到「遊戲」後先載入全部遊戲
watch(categoryId, async () => {
  resetGameState()

  if (isGame.value && !games.value.length) {
    await loadGames()
  }
})

// ==============================
// Lifecycle
// ==============================
onMounted(async () => {
  // 如果網址帶有 ?room=xxx
  if (route.query.room) {
    try {
      room.value = await api(
        `/rooms/${encodeURIComponent(route.query.room)}`
      )
    } catch (e) {
      error.value = e.message
    }
  }

  await load()

  // Conversation 有更新時重新讀取
  window.addEventListener(
    'support-refresh',
    load
  )

  // 每 10 秒更新一次
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      load()
    }
  }, 10000)
})

onUnmounted(() => {
  alive = false
  gameRequest++
  clearTimeout(gameSearchTimer)
  clearTimeout(gameBlurTimer)

  clearInterval(timer)

  window.removeEventListener(
    'support-refresh',
    load
  )
})
</script>

<template>
  <div class="page admin-page">

    <!-- =========================
         頁面標題
    ========================== -->
    

    <div class="page-title">
      <div>
        <h1>即時客服工作台</h1>

        <p class="muted">
          先理解問題，需要追蹤時再整理摘要轉成案件。
        </p>
      </div>

      <span class="connection" :class="{ live: session.online }">
        {{
          session.online
            ? '● 訊息連線正常'
            : '○ 重新連線中'
        }}
      </span>
    </div>

    <!-- 全域錯誤 -->
    <p v-if="error" class="error" role="alert">
      {{ error }}
    </p>

    <!-- =========================
         客服工作區
    ========================== -->
    <div class="admin-layout" :class="{ 'transfer-open': modal }">

      <!-- =========================
           左側聊天室列表
      ========================== -->
      <aside class="room-sidebar">

        <div class="room-filters">

          <SearchBar label="搜尋聊天室" placeholder="編號、會員名稱或訊息" @search="searchNow" />

          <select v-model="status" aria-label="聊天室狀態">
            <option value="OPEN">
              進行中的對話
            </option>

            <option value="CLOSED">
              已結束的對話
            </option>

            <option value="">
              全部對話
            </option>
          </select>

          <label class="checkbox">
            <input v-model="mine" type="checkbox">

            只看我負責的
          </label>

        </div>

        <!-- 沒有聊天室 -->
        <p v-if="!rooms.length" class="empty">
          目前沒有符合條件的對話
        </p>

        <!-- 聊天室列表 -->
        <button v-for="r in rooms" :key="r.id" class="room-row" :disabled="modal || busy" :aria-pressed="room?.id === r.id" :class="{
          selected: room?.id === r.id
        }" @click="pick(r)">
          <span class="room-avatar">{{ r.memberName?.charAt(0) || '?' }}</span>
          <div class="room-person">
            <strong>
              {{ r.memberName }}
            </strong>

            <small>
              #{{ r.id }}
            </small>
          </div>

          <p>
            <template v-if="r.ticketId">
              已轉案件 {{ r.ticketNo }}
            </template>

            <template v-else-if="r.status === 'CLOSED'">
              已結束
            </template>

            <template v-else-if="r.adminId">
              客服 #{{ r.adminId }} 處理中
            </template>

            <template v-else>
              等待客服接手
            </template>
          </p>

          <small>
            {{ timeText(r.createdAt) }}
          </small>
        </button>

        <!-- 分頁 -->
        <div class="pager">

          <button :disabled="page === 0" @click="page--">
            上一頁
          </button>

          <button :disabled="rooms.length < 30" @click="page++">
            下一頁
          </button>

        </div>

      </aside>

      <!-- =========================
           右側聊天室內容
      ========================== -->
      <section class="admin-discussion">

        <template v-if="room">

          <!-- 聊天室標題 -->
          <div class="discussion-heading">

            <div>
              <h2>
                {{ room.memberName }}
              </h2>

              <small class="muted">
                對話 #{{ room.id }}
                ·
                {{
                  room.status === 'OPEN'
                    ? '進行中'
                    : '已結束'
                }}
              </small>
            </div>

            <!-- 操作 -->
            <div class="actions">

              <!-- 尚未有人接手 -->
              <button v-if="
                room.status === 'OPEN' &&
                !room.adminId
              " class="primary" :disabled="busy" @click="action('claim')">
                接手對話
              </button>

              <!-- 自己負責 -->
              <template v-if="canSend">

                <button ref="transferButton" class="secondary" :disabled="busy || modal" @click="openModal">
                  轉成案件
                </button>

                <button class="text-button" :disabled="busy" @click="action('close')">
                  已解決，結束
                </button>

              </template>

              <!-- 已轉案件 -->
              <RouterLink v-if="room.ticketId" :to="`/aki/tickets/${room.ticketId}`" class="primary">
                查看案件
                {{ room.ticketNo }}
              </RouterLink>

            </div>
          </div>

          <!-- 聊天元件 -->
          <Conversation :key="room.id" kind="rooms" :id="room.id" :can-send="canSend" :closed-text="room.status === 'CLOSED'
            ? '對話已結束，請查看案件或歷史紀錄。'
            : room.adminId
              ? '目前由其他客服負責。'
              : '請先接手對話，再回覆會員。'
            " />

        </template>

        <!-- 尚未選聊天室 -->
        <div v-else class="workbench-empty">
          <div class="empty-card">
            <div class="empty-icon"><UiIcon name="headset" /></div>

            <span class="empty-kicker">
              SUPPORT WORKBENCH
            </span>

            <h2>選擇一段對話</h2>

            <p>
              從左側接手會員的詢問，<br>
              先理解問題，再決定是直接解決或整理成案件。
            </p>

            <div class="empty-tips">
              <div class="tip-item">
                <strong>1.</strong>
                <span>查看等待中的對話</span>
              </div>

              <div class="tip-item">
                <strong>2.</strong>
                <span>接手後即可回覆會員</span>
              </div>

              <div class="tip-item">
                <strong>3.</strong>
                <span>需要追蹤時再轉成案件</span>
              </div>
            </div>
          </div>
        </div>

      </section>

      <section v-if="modal" ref="transferPanel" class="modal transfer-panel" role="region" tabindex="-1" aria-labelledby="transfer-title" @keydown.esc="closeTransfer">

      <!-- Modal 標題 -->
      <div class="modal-heading">

        <div>
          <span class="eyebrow">
            案件整理
          </span>

          <h2 id="transfer-title">
            轉成案件
          </h2>
        </div>

        <button class="text-button" :disabled="busy" aria-label="關閉轉案表單" @click="closeTransfer">
          ✕
        </button>

      </div>

      <p class="muted">
        轉案後，會員會看到案件入口，並在案件頁繼續對話。
      </p>

      <!-- =========================
     轉案件表單
     ========================== -->
      <form @submit.prevent="transfer">

        <!-- 問題分類 -->
        <label>
          問題分類

          <select v-model="categoryId" aria-label="問題分類" class="case-category-select" required>
            <option :value="null" disabled>
              選擇問題類型
            </option>

            <option v-for="c in categories" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select>
        </label>


        <!-- =========================
       遊戲選擇
       ========================== -->
        <div v-if="isGame" class="game-search-section">
          <label class="game-search-label">
            選擇遊戲
          </label>

          <div class="game-combobox">

            <div class="game-combobox-input">

              <input v-model="gameKeyword" type="text" placeholder="搜尋或選擇遊戲" maxlength="100" autocomplete="off"
                role="combobox" aria-autocomplete="list" :aria-expanded="gameDropdownOpen" @focus="openGameDropdown"
                @click="openGameDropdown" @input="onGameInput" @blur="closeGameDropdown"
                @keydown.esc="gameDropdownOpen = false" @keydown.enter.prevent>

              <!-- 清除 -->
              <button v-if="gameKeyword" type="button" class="game-combobox-clear" aria-label="清除遊戲" @mousedown.prevent
                @click="clearSelectedGame">
                ×
              </button>

              <!-- 下拉箭頭 -->
              <span class="game-combobox-arrow" :class="{ open: gameDropdownOpen }" aria-hidden="true">
                ▾
              </span>

            </div>


            <!-- =========================
           遊戲下拉清單
           ========================== -->
            <div v-if="gameDropdownOpen" class="game-search-dropdown" role="listbox" aria-multiselectable="true">

              <!-- 載入中 -->
              <div v-if="gameLoading" class="game-search-empty">
                載入遊戲中…
              </div>

              <template v-else>

                <!-- 清單標題 -->
                <div class="game-dropdown-summary">

                  <span>
                    {{
                      gameKeyword
                        ? '搜尋結果'
                        : '遊戲（最多顯示 30 筆，可輸入搜尋）'
                    }}
                  </span>

                  <small>
                    {{ filteredGames.length }} 款
                  </small>

                </div>

                <!-- 遊戲項目 -->
                <button v-for="game in filteredGames" :key="game.id" type="button" class="game-search-option" :class="{
                  selected: gameIds.includes(game.id)
                }" role="option" :aria-selected="gameIds.includes(game.id)" @mousedown.prevent
                  @click="selectGame(game)">

                  <div class="game-search-option-main">

                    <strong>
                      {{ game.name }}
                    </strong>

                    <small v-if="game.price != null">NT$ {{ game.price }}</small>

                  </div>

                  <span v-if="gameIds.includes(game.id)" class="game-selected-mark">
                    ✓
                  </span>

                </button>

                <!-- 找不到 -->
                <div v-if="!filteredGames.length" class="game-search-empty">
                  找不到符合「{{ gameKeyword }}」的遊戲
                </div>

              </template>
            </div>
          </div>

          <!-- 已選遊戲：獨立於搜尋結果保存 -->
          <div v-if="selectedGames.length" class="selected-games">
            <small>已選擇 {{ selectedGames.length }} 款遊戲（最多 20 款）</small>
            <div class="selected-game-chips">
              <button v-for="game in selectedGames" :key="game.id" type="button" class="selected-game-chip"
                :aria-label="'移除 ' + game.name" @click="removeGame(game.id)">
                {{ game.name }} <span aria-hidden="true">×</span>
              </button>
            </div>
          </div>

        </div>

        <!-- 案件標題 -->
        <label>
          案件標題
          <input v-model="subject" required maxlength="100" placeholder="以一句話說明會員的問題">
        </label>

        <!-- 客服摘要 -->
        <label>
          客服摘要

          <textarea v-model="summary" class="case-summary-textarea" required maxlength="8000" rows="8" />
        </label>

        <!-- 表單錯誤 -->
        <p v-if="formError" class="error" role="alert">
          {{ formError }}
        </p>

        <!-- Modal 按鈕 -->
        <div class="modal-actions">

          <button type="button" class="secondary" :disabled="busy" @click="closeTransfer">
            取消
          </button>

          <button class="primary" :disabled="busy ||
            !categoryId ||
            !subject.trim() ||
            !summary.trim() ||
            (isGame && !gameIds.length)
            ">
            {{
              busy
                ? '建立案件中…'
                : '建立案件'
            }}
          </button>
        </div>
      </form>

      </section>
    </div>
  </div>

  <!-- =========================
       轉案件 Modal
  ========================== -->

</template>