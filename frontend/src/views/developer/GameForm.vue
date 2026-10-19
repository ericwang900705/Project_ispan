<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useGamesStore } from '@/stores/games'

// 新增和編輯共用這一頁：網址有 :id 就是編輯（/developer/games/3/edit），沒有就是新增（/developer/games/new）
const route = useRoute()
const router = useRouter()
const gamesStore = useGamesStore()
const { games, genres } = storeToRefs(gamesStore)

const gameId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => gameId.value !== null)

// 表單內容（v-model 綁定）；價格先用字串，送出時再轉成數字
const form = ref({ gameName: '', price: '', originalPrice: '', genre: '', coverUrl: '', description: '' })
const error = ref('')
const saving = ref(false)

// 編輯：遊戲清單抓到之後，把那款遊戲的資料填進表單
const editing = computed(() => games.value.find((g) => g.gameId === gameId.value))
watch(editing, (g) => {
  if (!g) return
  form.value = {
    gameName: g.gameName ?? '',
    price: g.price ?? '',
    originalPrice: g.originalPrice ?? '',
    genre: g.genre ?? '',
    coverUrl: g.coverUrl ?? '',
    description: g.description ?? '',
  }
}, { immediate: true })

onMounted(() => {
  if (!games.value.length) gamesStore.fetchGames()
})

// 原價比售價高才算打折，算出幾 % off（只是給開發商看的預覽）
const discount = computed(() => {
  const price = Number(form.value.price)
  const original = Number(form.value.originalPrice)
  if (!form.value.originalPrice || !(original > price)) return null
  return Math.round((1 - price / original) * 100)
})

// 前端先檢查一次（後端也會再檢查），有問題就回傳錯誤訊息
function validate() {
  if (!form.value.gameName.trim()) return '請填遊戲名稱'
  if (form.value.price === '' || Number(form.value.price) < 0) return '售價不能是空的或負數'
  if (form.value.originalPrice !== '' && Number(form.value.originalPrice) < Number(form.value.price)) {
    return '原價不能比售價低'
  }
  return ''
}

async function submit() {
  error.value = validate()
  if (error.value) return
  saving.value = true
  try {
    await gamesStore.saveGame(gameId.value, {
      gameName: form.value.gameName.trim(),
      price: Number(form.value.price),
      // 原價空白 → null（代表沒打折）
      originalPrice: form.value.originalPrice === '' ? null : Number(form.value.originalPrice),
      genre: form.value.genre.trim() || null,
      coverUrl: form.value.coverUrl.trim() || null,
      description: form.value.description.trim() || null,
    })
    router.push('/developer/games')
  } catch (e) {
    error.value = e.response?.data?.message ?? '儲存失敗'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="game-form">
    <h1>{{ isEdit ? '編輯遊戲' : '新增遊戲' }}</h1>
    <p v-if="!isEdit" class="hint">新增的遊戲一開始是「已下架」，確認內容沒問題後，到「我的遊戲」按上架玩家才看得到。</p>
    <p v-if="isEdit && !editing" class="hint">載入中…</p>

    <!-- @submit.prevent：按 Enter 或送出按鈕都會呼叫 submit，而且不會重新整理頁面 -->
    <form class="layout" @submit.prevent="submit">
      <div class="fields">
        <label for="f-name">遊戲名稱 <b>*</b></label>
        <input id="f-name" v-model="form.gameName" maxlength="100" />

        <div class="row">
          <div>
            <label for="f-price">售價（NT$）<b>*</b></label>
            <input id="f-price" v-model="form.price" type="number" min="0" step="1" />
          </div>
          <div>
            <label for="f-original">原價（NT$，打折才填）</label>
            <input id="f-original" v-model="form.originalPrice" type="number" min="0" step="1" />
          </div>
        </div>
        <p v-if="discount !== null" class="discount">折扣 -{{ discount }}%</p>

        <label for="f-genre">分類</label>
        <!-- datalist：可以選現有的分類，也可以自己打新的 -->
        <input id="f-genre" v-model="form.genre" list="genre-list" maxlength="50" />
        <datalist id="genre-list">
          <option v-for="g in genres" :key="g" :value="g"></option>
        </datalist>

        <label for="f-cover">封面圖片網址</label>
        <input id="f-cover" v-model="form.coverUrl" placeholder="https://..." maxlength="500" />

        <label for="f-desc">遊戲介紹</label>
        <textarea id="f-desc" v-model="form.description" rows="6"></textarea>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="actions">
          <button type="submit" class="save" :disabled="saving">{{ saving ? '儲存中…' : '儲存' }}</button>
          <RouterLink to="/developer/games" class="cancel">取消</RouterLink>
        </div>
      </div>

      <!-- 右邊：封面預覽 -->
      <aside class="preview">
        <span class="label">封面預覽</span>
        <img v-if="form.coverUrl" :src="form.coverUrl" alt="封面預覽" />
        <div v-else class="no-cover">還沒有封面</div>
      </aside>
    </form>
  </section>
</template>

<style scoped>
.game-form h1 { margin: 0 0 8px; font-size: 24px; }
.hint { margin: 0 0 16px; font-size: 13px; color: #98a2c8; }

/* 左邊表單、右邊預覽；窄的時候預覽移到下面 */
.layout { display: flex; gap: 24px; align-items: flex-start; flex-wrap: wrap; }
.fields {
  flex: 1; min-width: 280px; display: flex; flex-direction: column; gap: 6px; padding: 20px;
  background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px;
}
label { margin-top: 8px; font-size: 13px; color: #98a2c8; }
label b { color: #e06060; }
input, textarea {
  width: 100%; box-sizing: border-box; padding: 8px 12px; font: inherit; font-size: 14px; border-radius: 6px;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8; outline: none;
}
input:focus, textarea:focus { border-color: #d4a64a; }
textarea { resize: vertical; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.discount { margin: 4px 0 0; font-size: 13px; color: #f0a0b8; }
.error { margin: 8px 0 0; padding: 8px 12px; border-radius: 6px; background: rgba(178, 58, 42, .2); color: #ff8a6a; }
.actions { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.save {
  padding: 8px 28px; border: none; border-radius: 6px; font-size: 15px; font-weight: bold; cursor: pointer;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.save:disabled { opacity: .6; cursor: default; }
.cancel { color: #98a2c8; font-size: 14px; }
.cancel:hover { color: #e4e8f8; background: transparent; }

.preview {
  width: 300px; max-width: 100%; display: flex; flex-direction: column; gap: 8px; padding: 16px;
  background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px;
}
.preview .label { font-size: 13px; color: #98a2c8; }
.preview img, .no-cover { width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 6px; }
.no-cover { display: flex; align-items: center; justify-content: center; background: #121a4a; color: #5e6894; font-size: 13px; }

@media (max-width: 600px) {
  .row { grid-template-columns: 1fr; }
}
</style>
