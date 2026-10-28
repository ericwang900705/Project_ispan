<!-- src/components/ReviewForm.vue -->
<!-- 撰寫 / 修改評論的彈出視窗 -->
<!-- 用法：<ReviewForm v-if="open" :game="game" :review="editing" @close="open = false" /> -->
<!-- review 有值就是修改，沒傳就是新增 -->
<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from '../../api'
import { useReviewStore } from '@/cart/stores/reviews'

// 元件接收的參數
const props = defineProps({
  // 要評論的遊戲（要用 gameId、gameName）
  game: { type: Object, required: true },
  // 自己原本的評論：修改時傳入，新增時不用傳
  review: { type: Object, default: null },
})
// close：關閉視窗；saved：送出成功
const emit = defineEmits(['close', 'saved'])

const store = useReviewStore()

// 表單內容：修改時先帶入原本的值
const rating = ref(props.review?.rating ?? 0)
const recommended = ref(props.review?.recommended ?? null)
const content = ref(props.review?.content ?? '')
// 滑鼠停在第幾顆星（用來預覽評分）
const hoverStar = ref(0)
const saving = ref(false)
const restriction = ref(null), restrictionLoading = ref(true), restrictionError = ref(''), now = ref(Date.now())
let timer, alive = true
const muted = computed(() => restriction.value?.muted && Date.parse(restriction.value.mutedUntil) > now.value)
async function loadRestriction() {
  restrictionLoading.value = true; restrictionError.value = ''
  try { const value = await api('/reviews/mute-status'); if (alive) restriction.value = value }
  catch (e) { if (alive) restrictionError.value = e.message }
  finally { if (alive) restrictionLoading.value = false }
}
onMounted(() => { loadRestriction(); timer = setInterval(() => now.value = Date.now(), 30000) })
onUnmounted(() => { alive = false; clearInterval(timer) })

// 送出：前端先檢查必填，其他規則（例如要擁有遊戲）交給後端
async function submit() {
  if (restrictionLoading.value || restrictionError.value || muted.value) return
  if (!rating.value) return alert('請選擇評分')
  if (recommended.value === null) return alert('請選擇是否推薦')
  saving.value = true
  try {
    await store.saveReview(
      props.game.gameId,
      { rating: rating.value, recommended: recommended.value, content: content.value.trim() },
      props.review?.id
    )
    emit('saved')
    emit('close')
  } catch (e) {
    restrictionError.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <!-- 半透明遮罩：點外面關閉視窗 -->
  <div class="mask" @click.self="emit('close')">
    <form class="dialog" @submit.prevent="submit">
      <h2>{{ review ? '修改評論' : '撰寫評論' }}</h2>
      <p class="game-name">{{ game.gameName }}</p>
      <p v-if="muted" role="status" class="review-restriction">評論功能暫停至 {{ new
        Date(restriction.mutedUntil).toLocaleString('zh-TW', { hour12:false})}}。期間仍可登入、購買與聯絡客服。</p>
      <p v-if="restrictionError" role="alert" class="review-restriction">{{ restrictionError }} <button type="button"
          @click="loadRestriction">重新檢查</button></p>

      <!-- 是否推薦：兩個大按鈕擇一 -->
      <div class="label">你推薦這款遊戲嗎？</div>
      <div class="recommend">
        <button type="button" :class="{ on: recommended === true }" @click="recommended = true">👍 推薦</button>
        <button type="button" :class="{ on: recommended === false, no: true }" @click="recommended = false">👎
          不推薦</button>
      </div>

      <!-- 評分：1～5 顆星，滑鼠移上去預覽 -->
      <div class="label">評分</div>
      <div class="stars" @mouseleave="hoverStar = 0">
        <button v-for="n in 5" :key="n" type="button" :class="{ lit: n <= (hoverStar || rating) }"
          :aria-label="`${n} 顆星`" @mouseenter="hoverStar = n" @click="rating = n">★</button>
      </div>

      <!-- 評論內容（選填） -->
      <div class="label">評論內容</div>
      <textarea v-model="content" rows="5" placeholder="分享你對這款遊戲的心得（選填）"></textarea>

      <div class="actions">
        <button type="button" class="cancel" @click="emit('close')">取消</button>
        <button type="submit" class="submit" :disabled="saving || restrictionLoading || !!restrictionError || muted">{{
          saving ? '送出中…' : '送出' }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.review-restriction {
  padding: 12px;
  border: 1px solid #d6a747;
  border-radius: 8px;
  background: #302a24;
  color: #ffe2a0;
  font-size: 13px;
  line-height: 1.7
}

/* 遮罩蓋住整個畫面，視窗置中 */
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, .65);
}

.dialog {
  width: 100%;
  max-width: 460px;
  padding: 20px 22px;
  border-radius: 8px;
  color: #e4e8f8;
  background: linear-gradient(180deg, #1c2a66, #121a4a);
  border: 1px solid #d4a64a;
  box-shadow: 0 12px 32px rgba(0, 0, 0, .6);
}

h2 {
  margin: 0;
  font-size: 20px;
  color: #d4a64a;
}

.game-name {
  margin: 4px 0 14px;
  font-size: 14px;
  color: #98a2c8;
}

.label {
  margin: 12px 0 6px;
  font-size: 13px;
  color: #98a2c8;
}

/* 推薦 / 不推薦按鈕 */
.recommend {
  display: flex;
  gap: 8px;
}

.recommend button {
  flex: 1;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  border: 1px solid #2e4a9a;
  background: #0b0c32;
  color: #e4e8f8;
}

.recommend button.on {
  border-color: #66c0f4;
  background: #1e4a6e;
}

.recommend button.no.on {
  border-color: #c45050;
  background: #5a1e24;
}

/* 星星 */
.stars {
  display: flex;
  gap: 2px;
}

.stars button {
  border: none;
  background: none;
  padding: 0 2px;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  color: #3a4680;
}

.stars button.lit {
  color: #f0c060;
}

textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border-radius: 4px;
  resize: vertical;
  border: 1px solid #2e4a9a;
  background: #0b0c32;
  color: #e4e8f8;
  font: inherit;
  font-size: 14px;
  outline: none;
}

textarea:focus {
  border-color: #d4a64a;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.actions button {
  padding: 8px 18px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
}

.cancel {
  background: #2e3a70;
  color: #e4e8f8;
}

.submit {
  font-weight: bold;
  color: #1b0f0d;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}

.submit:disabled {
  opacity: .6;
  cursor: default;
}
</style>
