<script setup>
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
const props = defineProps({ game: { type: Object, required: true } })
const emit = defineEmits(['navigate'])
const active = ref(0), failed = ref(false)
const safe = value => { try { const u = new URL(value); return ['http:', 'https:'].includes(u.protocol) && !u.username && !u.password ? u.href : '' } catch { return '' } }
const gallery = computed(() => [...(safe(props.game.cover_url) ? [{ media_type: 'IMAGE', media_url: props.game.cover_url, label: '封面' }] : []), ...props.game.media.filter(x => safe(x.media_url)).map((x, i) => ({ ...x, label: `宣傳素材 ${i + 1}` }))])
const current = computed(() => gallery.value[active.value])
watch(() => props.game.game_id, () => { active.value = 0; failed.value = false })
watch(active, () => { failed.value = false })
</script>
<template>
  <section class="publisher-preview">
    <div class="publisher-preview-notice"><UiIcon name="image" /><p><strong>商品頁預覽</strong><br>呈現目前已儲存的內容，實際商城可依整合後版型調整。</p></div>
    <div class="publisher-preview-stage">
      <img v-if="current?.media_type === 'IMAGE' && !failed" :src="safe(current.media_url)" :alt="current.label" referrerpolicy="no-referrer" @error="failed = true">
      <video v-else-if="current?.media_type === 'VIDEO' && !failed" :key="current.media_url" :src="safe(current.media_url)" controls preload="none" @error="failed = true"></video>
      <div v-else class="publisher-preview-placeholder"><UiIcon name="game" /><p>{{ failed ? '無法載入素材，請確認網址與分享權限。' : '尚未設定封面與宣傳素材' }}</p></div>
    </div>
    <div v-if="gallery.length" class="publisher-preview-thumbs"><button v-for="(item, index) in gallery" :key="index" :class="{active: active === index}" :aria-pressed="active === index" @click="active = index">{{ item.label }}{{ item.media_type === 'VIDEO' ? '・影片' : '' }}</button></div>
    <div class="publisher-preview-heading"><div><h2>{{ game.game_name }}</h2><div class="publisher-preview-tags"><span v-for="tag in game.tags" :key="tag.tag_id">{{ tag.tag_name }}</span><span v-if="!game.tags.length">尚未設定標籤</span></div></div><div class="publisher-preview-price">NT$ {{ Number(game.price).toLocaleString('zh-TW') }}<small>{{ game.release_date ? '預定發行：' + String(game.release_date).slice(0,10) : '發行日期尚未設定' }}</small></div></div>
    <h3>關於這款遊戲</h3><p class="publisher-description">{{ game.description || '尚未填寫遊戲介紹。' }}</p>
    <div class="publisher-save-actions"><button class="secondary" @click="emit('navigate', 'edit')">返回修改資料</button><button class="primary" @click="emit('navigate', 'publish')">前往上架檢核</button></div>
  </section>
</template>
