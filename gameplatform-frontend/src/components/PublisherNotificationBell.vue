<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import UiIcon from './UiIcon.vue'
const router=useRouter(), root=ref(null), opened=ref(false), unread=ref(0), items=ref([]), failed=ref(false), busy=ref(false)
let timer, alive=true, run=0
async function refresh(){const id=++run;try{const result=await api('/publisher/notifications?page=0');if(alive&&id===run){items.value=result.items||[];unread.value=result.unread||0;failed.value=false}}catch{if(alive&&id===run)failed.value=true}}
function outside(event){if(root.value&&!root.value.contains(event.target))opened.value=false}
function escape(event){if(event.key==='Escape')opened.value=false}
function visible(){if(document.visibilityState==='visible')refresh()}
async function openItem(item){if(busy.value)return;busy.value=true;try{if(!item.publisher_read_at)await api('/publisher/notifications/'+item.notification_id+'/read',{method:'POST'});opened.value=false;window.dispatchEvent(new Event('publisher-notifications-changed'));await router.push({path:'/publisher/games',query:{mode:'history',game:item.game_id}});await refresh()}catch{failed.value=true}finally{busy.value=false}}
async function readAll(){if(busy.value)return;busy.value=true;try{const result=await api('/publisher/notifications?page=0');if(result.latestId)await api('/publisher/notifications/read-all?throughId='+result.latestId,{method:'POST'});window.dispatchEvent(new Event('publisher-notifications-changed'));await refresh()}catch{failed.value=true}finally{busy.value=false}}
const title=n=>n.review_type==='OFF_SHELF'?'遊戲下架通知':n.decision==='APPROVED'?'上架審核通過':'上架申請退回'
onMounted(()=>{refresh();timer=setInterval(visible,30000);window.addEventListener('publisher-notifications-changed',refresh);document.addEventListener('visibilitychange',visible);document.addEventListener('click',outside);document.addEventListener('keydown',escape)})
onUnmounted(()=>{alive=false;clearInterval(timer);window.removeEventListener('publisher-notifications-changed',refresh);document.removeEventListener('visibilitychange',visible);document.removeEventListener('click',outside);document.removeEventListener('keydown',escape)})
</script>
<template>
<div ref="root" class="publisher-bell-wrap">
  <button type="button" class="publisher-notification-bell" :aria-expanded="opened" aria-label="審核通知" title="審核通知" @click="opened=!opened;if(opened)refresh()"><UiIcon name="bell" /><b v-if="unread">{{unread>99?'99+':unread}}</b><small v-if="failed">!</small></button>
  <div v-if="opened" class="publisher-bell-popover" role="dialog" aria-label="審核通知">
    <header><strong>審核通知</strong><button type="button" class="secondary" :disabled="busy||!unread" @click="readAll">全部已讀</button></header>
    <p v-if="failed" class="error">通知更新失敗，可稍後重試。</p>
    <p v-if="!items.length" class="publisher-empty">目前沒有通知</p>
    <button v-for="item in items.slice(0,6)" :key="item.notification_id" type="button" class="publisher-bell-item" :class="{unread:!item.publisher_read_at}" :disabled="busy" @click="openItem(item)"><span :class="item.decision==='APPROVED'?'approved':'rejected'">{{title(item)}}</span><strong>{{item.game_name}}</strong><small>{{item.comment||'點擊查看審核紀錄'}}</small></button>
    <footer><button type="button" @click="opened=false;router.push('/publisher/notifications')">查看全部通知 →</button></footer>
  </div>
</div>
</template>
