<script setup>
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useStudioStore } from '@/stores/studio'

const studioStore = useStudioStore()
const { studio } = storeToRefs(studioStore)

// 表單先複製一份，按儲存才寫回去（打到一半離開不會改到）
const form = ref({ ...studio.value })
const saved = ref(false)
const error = ref('')

function submit() {
  if (!form.value.name.trim()) {
    error.value = '請填工作室名稱'
    return
  }
  error.value = ''
  studioStore.save({
    name: form.value.name.trim(),
    logoUrl: form.value.logoUrl.trim(),
    bio: form.value.bio.trim(),
  })
  saved.value = true
  setTimeout(() => (saved.value = false), 2000)
}
</script>

<template>
  <section class="studio-settings">
    <h1>工作室設定</h1>
    <p class="hint">目前還沒有開發商的資料表，設定先存在這台電腦的瀏覽器裡。</p>

    <form class="layout" @submit.prevent="submit">
      <div class="fields">
        <label for="s-name">工作室名稱 <b>*</b></label>
        <input id="s-name" v-model="form.name" maxlength="50" />

        <label for="s-logo">Logo 圖片網址</label>
        <input id="s-logo" v-model="form.logoUrl" placeholder="https://..." maxlength="500" />

        <label for="s-bio">工作室簡介</label>
        <textarea id="s-bio" v-model="form.bio" rows="5" maxlength="500"></textarea>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="actions">
          <button type="submit" class="save">儲存</button>
          <span v-if="saved" class="ok">已儲存</span>
        </div>
      </div>

      <!-- 右邊：預覽（玩家會看到的樣子） -->
      <aside class="preview">
        <span class="label">預覽</span>
        <div class="card">
          <img v-if="form.logoUrl" :src="form.logoUrl" alt="工作室 Logo" class="logo" />
          <div v-else class="logo empty">{{ (form.name || '?').slice(0, 1) }}</div>
          <div class="text">
            <b>{{ form.name || '（未命名）' }}</b>
            <p>{{ form.bio || '還沒有簡介' }}</p>
          </div>
        </div>
      </aside>
    </form>
  </section>
</template>

<style scoped>
.studio-settings h1 { margin: 0 0 8px; font-size: 24px; }
.hint { margin: 0 0 16px; font-size: 13px; color: #98a2c8; }
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
.error { margin: 8px 0 0; color: #ff8a6a; }
.actions { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.save {
  padding: 8px 28px; border: none; border-radius: 6px; font-size: 15px; font-weight: bold; cursor: pointer;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.ok { color: #8fd07a; font-size: 14px; }
.preview {
  width: 320px; max-width: 100%; display: flex; flex-direction: column; gap: 8px; padding: 16px;
  background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px;
}
.preview .label { font-size: 13px; color: #98a2c8; }
.card { display: flex; gap: 12px; align-items: flex-start; }
.logo { width: 56px; height: 56px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.logo.empty { display: flex; align-items: center; justify-content: center; background: #1c2a66; color: #d4a64a; font-size: 24px; font-weight: bold; }
.text { min-width: 0; }
.text b { font-size: 16px; }
.text p { margin: 4px 0 0; font-size: 13px; color: #98a2c8; white-space: pre-wrap; word-break: break-word; }
</style>
