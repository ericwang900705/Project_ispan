<script setup>
import { computed } from 'vue'
import UiIcon from './UiIcon.vue'
const props = defineProps({ game: { type: Object, required: true }, mode: String, canRestore: Boolean, remaining: String, editable: Boolean, busy: Boolean })
const emit = defineEmits(['offShelf', 'restore', 'navigate'])
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
const latest = computed(() => props.game.history[0])
const canOffShelf = computed(() => !props.game.pending_review_type && ['PUBLISHED', 'COMING_SOON'].includes(props.game.status))
</script>
<template>
  <section class="publisher-operations">
    <h3>{{ mode === 'list' ? '遊戲營運與數據' : mode === 'buyers' ? '購買人數' : mode === 'history' ? '審核歷史與拒絕原因' : '下架與恢復上架' }}</h3>
    <div v-if="['list','buyers'].includes(mode)" class="publisher-buyer-total"><strong>{{ game.buyer_count }}</strong><span>人購買此遊戲</span><UiIcon name="users" /></div>
    <p v-if="['list','buyers'].includes(mode)" class="publisher-count-note">僅計算已付款的不重複買家，排除取消與退款；送禮計入付款的會員。</p>
    <dl v-if="mode === 'list'" class="publisher-operation-facts"><div><dt>售價</dt><dd>NT$ {{ game.price }}</dd></div><div><dt>啟用版本</dt><dd>{{ game.builds.filter(x => x.status === 'ACTIVE').map(x => x.version).join('、') || '尚未設定' }}</dd></div></dl>
    <p v-if="game.pending_review_type && mode !== 'buyers'" class="publisher-pending" role="status"><strong>審核中</strong><br>送出時間：{{ date(game.review_requested_at) }}。完成前無法修改遊戲資料。</p>
    <p v-else-if="latest?.decision === 'REJECTED' && ['list','history'].includes(mode)" class="publisher-pending"><strong>最近一次退回原因</strong><br>{{ latest.comment }}</p>
    <details v-if="['list','history'].includes(mode)" class="publisher-audit-details" :open="mode === 'history' || !!game.pending_review_type"><summary>審核歷史與拒絕原因 <span>{{ game.history.length }} 筆</span></summary><p v-if="!game.history.length" class="muted">尚無已完成的審核紀錄。</p><article v-for="record in game.history" :key="record.decision_id" class="publisher-history"><strong>{{ record.review_type === 'PUBLISH' ? '上架' : '下架' }} · {{ record.decision === 'APPROVED' ? '通過' : '退回' }}</strong><small>{{ record.reviewer_name }} · {{ date(record.decided_at) }}</small><p>{{ record.comment }}</p></article></details>
    <div v-if="['list','offShelf'].includes(mode)" class="publisher-operating-actions">
      <template v-if="game.off_shelf_source === 'ADMIN'"><p class="publisher-pending">管理員強制下架的遊戲不能自行恢復、修改或送審，請聯絡管理員處理。</p></template>
      <template v-else-if="canOffShelf"><p>下架立即停止販售；24小時內可恢復，遊戲與購買紀錄仍保留。</p><button class="publisher-danger" :disabled="busy" @click="emit('offShelf')"><UiIcon name="power" />下架遊戲</button></template>
      <template v-else-if="canRestore"><p>下架時間：{{ date(game.off_shelf_at) }}</p><p class="publisher-pending">可恢復至 {{ date(game.restore_until) }}<br>剩餘 {{ remaining }}。恢復期間保留原審核資料。</p><button class="publisher-positive" :disabled="busy" @click="emit('restore')">恢復上架</button></template>
      <template v-else-if="game.status === 'OFF_SHELF' && !game.pending_review_type"><p>恢復期限已過，資料仍保留。重新上架需要送審。</p><button class="primary" :disabled="busy" @click="emit('navigate', 'publish')">重新送審上架</button></template>
      <template v-else-if="editable"><p>這款遊戲尚未上架，可繼續設定資料並準備送審。</p><div class="publisher-button-row"><button class="secondary" :disabled="busy" @click="emit('navigate', 'edit')">修改遊戲資料</button><button class="primary" :disabled="busy" @click="emit('navigate', 'publish')">前往上架檢核</button></div></template>
    </div>
  </section>
</template>
