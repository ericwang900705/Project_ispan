<script setup>
// 私有 ZIP 使用已登入的 API 憑證下載；外部下載網址維持一般連結。
import { computed, ref } from 'vue'
import { apiFile } from '../api'
const props = defineProps({ url: { type: String, required: true } })
const local = computed(() => props.url.startsWith('/api/publisher-assets/builds/'))
const external = computed(() => { try { const u = new URL(props.url); return ['http:', 'https:'].includes(u.protocol) && !u.username && !u.password } catch { return false } })
const busy = ref(false), error = ref('')
async function download() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const file = await apiFile(props.url.slice(4))
    const url = URL.createObjectURL(file), link = document.createElement('a')
    link.href = url; link.download = 'game-build.zip'; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <span><button v-if="local" type="button" :disabled="busy" @click="download">{{ busy ? '下載中…' : '下載 ZIP 版本檔案'
      }}</button>
    <a v-else-if="external" :href="url" target="_blank" rel="noopener noreferrer">查看版本檔案 ↗</a>
    <small v-else>內建遊戲版本；更新時請另新增 ZIP 或下載網址。</small>
    <small v-if="error" role="alert" class="error">{{ error }}</small>
  </span>
</template>
