<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { session } from '../api'
import UiIcon from './UiIcon.vue'
const props = defineProps({open:Boolean})
const emit = defineEmits(['close'])
const sidebar = ref(null)
watch(() => props.open, async value=>{if(value){await nextTick();sidebar.value?.querySelector('a,button:not(:disabled)')?.focus()}})
function trap(event){if(!props.open)return;const items=[...sidebar.value.querySelectorAll('a,button:not(:disabled)')].filter(el=>el.getClientRects().length);if(!items.length)return;const first=items[0],last=items.at(-1);if(event.shiftKey && document.activeElement===first){event.preventDefault();last.focus()}else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first.focus()}}
const route = useRoute(), expanded = ref({games:true,support:true})
const admin = computed(() => session.user?.role === 'ADMIN')
const unavailable = (name) => ({name})
const groups = computed(() => {
 const list=[]
 if(admin.value) list.push({id:'members',name:'會員管理',icon:'users',children:[unavailable('會員列表'),unavailable('會員處理紀錄')]})
 if(admin.value || session.user?.role === 'REVIEWER') list.push({id:'games',name:'遊戲管理',icon:'game',children:[...(admin.value?[{name:'遊戲搜尋',to:'/admin/games'}]:[]),{name:'上架審核',to:'/review'},...(admin.value?[unavailable('標籤管理')]:[])]})
 if(admin.value) list.push({id:'publishers',name:'發行商管理',icon:'building',children:[unavailable('發行商列表'),unavailable('發行商資料')]},{id:'orders',name:'訂單管理',icon:'cart',children:[unavailable('訂單列表'),unavailable('退款管理')]})
 if(admin.value || session.user?.role === 'SUPPORT') list.push({id:'support',name:'客服',icon:'headset',children:[{name:'工作總覽',to:'/admin'},{name:'即時客服',to:'/admin/chats'},{name:'客服案件',to:'/tickets'}]})
 if(admin.value || session.user?.role === 'REVIEWER') list.push({id:'comments',name:'評論',icon:'chat',children:[{name:'評論審核',to:'/review/comments'}]})
 if(admin.value) list.push({id:'offers',name:'優惠活動',icon:'tag',children:[unavailable('優惠活動列表'),unavailable('優惠券管理')]},{id:'permissions',name:'管理員權限管理',icon:'shield',children:[unavailable('管理員列表'),unavailable('角色權限')]},{id:'logs',name:'系統紀錄管理',icon:'file',children:[unavailable('操作紀錄'),unavailable('登入紀錄')]})
 return list
})
watch(() => route.fullPath, () => {emit('close'); for(const group of groups.value) if(group.children.some(item=>item.to === route.path)) expanded.value[group.id] = true})
</script>
<template>
 <div class="management-sidebar-wrap support-ui staff-ui">
  <button v-if="open" class="sidebar-backdrop" aria-label="關閉管理選單" @click="emit('close')"></button>
  <aside ref="sidebar" id="management-sidebar" class="management-sidebar" :class="{open}" aria-label="後台功能選單" @keydown.esc="emit('close')" @keydown.tab="trap">
   <div class="sidebar-heading"><small>WORKSPACE</small><strong>{{admin ? '管理者工作區' : '工作後台'}}</strong></div>
   <RouterLink v-if="admin" class="sidebar-dashboard" to="/admin/overview" @click="emit('close')"><UiIcon name="dashboard" /><span>主頁</span></RouterLink>
   <nav>
    <section v-for="group in groups" :key="group.id" class="sidebar-group">
     <button class="sidebar-group-toggle" :aria-expanded="!!expanded[group.id]" :aria-controls="'menu-'+group.id" @click="expanded[group.id] = !expanded[group.id]"><UiIcon :name="group.icon" /><span>{{group.name}}</span><UiIcon class="sidebar-chevron" :class="{expanded:expanded[group.id]}" name="chevron" /></button>
     <div v-show="expanded[group.id]" :id="'menu-'+group.id" class="sidebar-children">
      <template v-for="item in group.children" :key="item.name"><RouterLink v-if="item.to" :to="item.to" @click="emit('close')">{{item.name}}</RouterLink><button v-else disabled :title="item.name + '尚未接入功能'">{{item.name}}<small>尚未接入</small></button></template>
     </div>
    </section>
   </nav>
   <p class="sidebar-note">依帳號權限顯示功能<br>未接入的功能暫不開放操作。</p>
  </aside>
 </div>
</template>
