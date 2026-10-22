<script setup>
import DatePicker from './DatePicker.vue'
import { computed, ref } from 'vue'
import { api } from '../api'

const form = defineModel('form', { required: true })
const tagIds = defineModel('tagIds', { required: true })
const media = defineModel('media', { required: true })
const props = defineProps({ tags: { type: Array, default: () => [] }, disabled: Boolean, busy: Boolean })
const emit = defineEmits(['uploading'])
const uploading = ref(false)
const uploadError = ref('')
const dragIndex = ref(null)
const activeImage = ref('')
const imageItems = computed(() => media.value.filter(item => item.type === 'IMAGE'))
const selectedTags = computed(() => props.tags.filter(tag => tagIds.value.includes(tag.tag_id)))
const summary = computed(() => (form.value.description || '').trim().slice(0, 160))
const shownImage = computed(() => imageItems.value.some(item => item.url === activeImage.value) ? activeImage.value : form.value.coverUrl || imageItems.value[0]?.url)
const price = computed(() => new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 2 }).format(Number(form.value.price) || 0))
const locked = computed(() => props.disabled || props.busy || uploading.value)

function renumber() { media.value.forEach((item, index) => { item.order = index + 1 }) }
function move(from, to) {
  if (locked.value || from == null || to < 0 || to >= media.value.length || from === to) return
  const items = [...media.value]
  items.splice(to, 0, items.splice(from, 1)[0])
  items.forEach((item, index) => { item.order = index + 1 })
  media.value = items
}
function drop(index) { move(dragIndex.value, index); dragIndex.value = null }
function remove(index) {
  if (locked.value) return
  media.value.splice(index, 1)
  renumber()
}
async function upload(event, cover = false) {
  const input = event.target
  const files = [...(input.files || [])]
  input.value = ''
  if (!files.length || locked.value) return
  uploadError.value = ''
  if (!cover && media.value.length + files.length > 50) {
    uploadError.value = '最多可新增 50 個媒體，請減少選取數量。'
    return
  }
  if (files.some(file => file.size === 0 || file.size > 5 * 1024 * 1024 || !/\.(png|jpe?g)$/i.test(file.name))) {
    uploadError.value = '請選擇 JPG 或 PNG 圖片，每張不超過 5 MB。'
    return
  }
  uploading.value = true
  emit('uploading', true)
  let completed = 0
  try {
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      const result = await api('/publisher/images', { method: 'POST', body })
      if (cover) { form.value.coverUrl = result.url; activeImage.value = '' }
      else media.value.push({ type: 'IMAGE', url: result.url, order: media.value.length + 1 })
      completed++
    }
  } catch (error) {
    uploadError.value = `${completed ? `已上傳 ${completed} 張，其餘未完成。` : ''}${error.message}`
  } finally {
    uploading.value = false
    emit('uploading', false)
  }
}
</script>

<template>
  <div class="publisher-store-layout">
    <fieldset class="publisher-store-fields" :disabled="locked">
      <section class="publisher-edit-section">
        <h3>基本資料 <span class="publisher-draft-label">草稿編輯</span></h3>
        <label>遊戲名稱<input v-model="form.name" maxlength="150" placeholder="輸入遊戲名稱" required></label>
        <div class="publisher-form-pair">
          <label>售價（NT$）<input v-model.number="form.price" type="number" min="0" max="99999999.99" step="0.01" required></label>
          <label>預定發行時間<DatePicker v-model="form.releaseDate" type="datetime-local" label="預定發行時間" /></label>
        </div>
      </section>

      <section class="publisher-edit-section">
        <h3>遊戲封面</h3>
        <div class="publisher-cover-upload">
          <img v-if="form.coverUrl" :src="form.coverUrl" alt="目前遊戲封面">
          <div v-else class="publisher-image-placeholder">尚未選擇封面</div>
          <div>
            <label class="publisher-upload-button">選擇檔案<input type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" aria-label="上傳遊戲封面" @change="upload($event, true)"></label>
            <p class="muted">可從電腦的「下載」資料夾選取圖片。<br>JPG、PNG，每張最多 5 MB。</p>
            <button v-if="form.coverUrl" type="button" class="text-button" @click="form.coverUrl = ''; activeImage = ''">移除封面</button>
          </div>
        </div>
        <details class="publisher-url-option"><summary>或使用圖片網址</summary><label>封面網址<input v-model="form.coverUrl" type="text" maxlength="500" placeholder="https://… 或已上傳的 /uploads/publisher/…"></label></details>
      </section>

      <section class="publisher-edit-section">
        <div class="publisher-section-title"><h3>遊戲圖片</h3><label class="publisher-upload-button">新增圖片<input type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" multiple aria-label="新增遊戲圖片" @change="upload($event)"></label></div>
        <p class="muted">拖曳縮圖或使用左右按鈕調整順序，最多 50 個媒體。</p>
        <div v-if="media.length" class="publisher-sort-gallery">
          <article v-for="(item, index) in media" :key="item.url + ':' + index" class="publisher-sort-item" :draggable="!locked" @dragstart="dragIndex = index" @dragend="dragIndex = null" @dragover.prevent @drop.prevent="drop(index)">
            <span class="publisher-image-number">{{ index + 1 }}</span>
            <img v-if="item.type === 'IMAGE'" :src="item.url" :alt="`遊戲圖片 ${index + 1}`" draggable="false">
            <div v-else class="publisher-image-placeholder">影片 {{ index + 1 }}</div>
            <div class="publisher-sort-actions"><button type="button" :disabled="index === 0" :aria-label="`將第 ${index + 1} 張向前移`" @click="move(index, index - 1)">←</button><button type="button" :disabled="index === media.length - 1" :aria-label="`將第 ${index + 1} 張向後移`" @click="move(index, index + 1)">→</button><button type="button" :aria-label="`移除第 ${index + 1} 個媒體`" @click="remove(index)">移除</button></div>
          </article>
        </div>
        <div v-else class="publisher-upload-empty">新增遊戲截圖，右側將同步顯示預覽。</div>
        <p v-if="uploading" role="status" class="publisher-upload-status">圖片上傳中，請稍候…</p>
        <p v-if="uploadError" role="alert" class="error">{{ uploadError }}</p>
      </section>

      <section class="publisher-edit-section">
        <h3>遊戲標籤</h3><p class="muted">僅能選擇平台提供的標籤，可複選。</p>
        <div class="publisher-select-tags"><label v-for="tag in tags" :key="tag.tag_id" :class="{ selected: tagIds.includes(tag.tag_id) }"><input v-model="tagIds" type="checkbox" :value="tag.tag_id"><span>{{ tag.tag_name }}</span></label></div>
        <p v-if="!tags.length" class="muted">尚無可選用標籤，請確認資料庫已建立平台標籤。</p>
      </section>

      <section class="publisher-edit-section">
        <label>完整遊戲介紹<textarea v-model="form.description" maxlength="20000" rows="8" placeholder="介紹遊戲玩法、故事與特色…"></textarea></label>
        <div class="publisher-description-footer"><small>右側簡介自動擷取介紹的前 160 字。</small><small>{{ (form.description || '').length }} / 20000</small></div>
      </section>
      <div class="publisher-editor-actions"><button type="submit" class="secondary">{{ busy ? '儲存中…' : '儲存草稿' }}</button><button type="submit" class="primary" data-next="preview">儲存並預覽</button></div>
    </fieldset>

    <aside class="publisher-live-preview" aria-label="即時遊戲預覽">
      <header><h2>即時預覽</h2><p class="muted">隨編輯同步更新 · 尚未上架</p></header>
      <img v-if="shownImage" class="publisher-preview-cover" :src="shownImage" alt="遊戲商品頁圖片預覽">
      <div v-else class="publisher-preview-cover publisher-image-placeholder">上傳封面後，圖片會顯示在這裡</div>
      <div class="publisher-preview-content">
        <h2>{{ form.name || '你的遊戲名稱' }}</h2>
        <div class="publisher-preview-tags"><span v-for="tag in selectedTags" :key="tag.tag_id">{{ tag.tag_name }}</span><small v-if="!selectedTags.length" class="muted">尚未選擇標籤</small></div>
        <strong class="publisher-preview-price">NT$ {{ price }}</strong>
        <p class="publisher-preview-summary">{{ summary || '填寫遊戲介紹，讓玩家了解你的作品。' }}</p>
        <div v-if="imageItems.length" class="publisher-preview-thumbnails"><button v-for="(item, index) in imageItems" :key="item.url + ':' + index" type="button" :aria-label="`預覽第 ${index + 1} 張圖片`" @click="activeImage = item.url"><img :src="item.url" :alt="`縮圖 ${index + 1}`"></button></div>
        <button v-if="activeImage && form.coverUrl" class="text-button" type="button" @click="activeImage = ''">顯示封面</button>
        <p v-if="form.releaseDate" class="muted">預定發行：{{ form.releaseDate.replace('T', ' ') }}</p>
        <section class="publisher-preview-description"><h3>關於此遊戲</h3><p>{{ form.description || '完整遊戲介紹將顯示於此。' }}</p></section>
      </div>
    </aside>
  </div>
</template>
