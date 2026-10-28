<script setup>
// 會員檢舉評論：只提交原因，來源快照由後端取得；成立前不計次。
import {ref} from 'vue'
import {api} from '../api'
const props=defineProps({review:{type:Object,required:true}})
const emit=defineEmits(['close'])
const reason=ref(''),busy=ref(false),error=ref(''),sent=ref(false)
async function submit(){
  if(busy.value||sent.value||!reason.value.trim())return
  busy.value=true;error.value=''
  try{await api(`/reviews/${props.review.id}/report`,{method:'POST',body:{reason:reason.value.trim()}});sent.value=true}
  catch(e){error.value=e.message}finally{busy.value=false}
}
</script>
<template>
  <div class="report-mask" @click.self="!busy && emit('close')" @keydown.esc="!busy && emit('close')">
    <form class="report-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title" @submit.prevent="submit">
      <h2 id="report-title">檢舉評論</h2><p>{{review.username}} 的評論</p><blockquote>{{review.content}}</blockquote>
      <p v-if="sent" role="status">已送出檢舉，後台確認違規前不會計次或禁言。</p>
      <template v-else><label>檢舉原因<textarea v-model="reason" required maxlength="500" rows="4" :disabled="busy" placeholder="請說明違規內容與原因"></textarea></label><p v-if="error" role="alert">{{error}}</p></template>
      <div class="report-actions"><button type="button" :disabled="busy" @click="emit('close')">{{sent?'關閉':'取消'}}</button><button v-if="!sent" type="submit" :disabled="busy||!reason.trim()">{{busy?'送出中…':'送出檢舉'}}</button></div>
    </form>
  </div>
</template>
<style scoped>
.report-mask{position:fixed;inset:0;z-index:2200;display:grid;place-items:center;padding:20px;background:#050a20bb}.report-dialog{width:min(100%,480px);max-height:85dvh;overflow:auto;padding:24px;border:1px solid #40558a;border-radius:16px;background:#141e45;color:#e5eafa;box-sizing:border-box}.report-dialog h2{margin-top:0}.report-dialog p,.report-dialog blockquote{line-height:1.7;overflow-wrap:anywhere}.report-dialog blockquote{margin:16px 0;padding:12px;background:#0d1533;border-left:3px solid #d4a64a;max-height:160px;overflow:auto;white-space:pre-wrap}.report-dialog label{display:grid;gap:8px}.report-dialog textarea{width:100%;box-sizing:border-box;background:#0d1533;color:#e5eafa;border:1px solid #40558a;border-radius:8px;padding:12px;font:inherit}.report-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:18px}.report-actions button{padding:9px 16px;border:1px solid #5d71a6;border-radius:8px;color:#e5eafa;background:#24335d}.report-actions button[type=submit]{color:#17203b;background:#d4a64a;border-color:#d4a64a}.report-actions button:disabled{opacity:.5;cursor:default}
</style>
