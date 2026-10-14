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
const route = useRoute(), expanded = ref({support:true})
const admin = computed(() => session.user?.role === 'ADMIN')
const groups = computed(() => {
 const list=[]
 if(admin.value || session.user?.role === 'SUPPORT') list.push({id:'support',name:'客服',icon:'headset',children:[{name:'工作總覽',to:'/admin'},{name:'即時客服',to:'/admin/chats'},{name:'客服案件',to:'/tickets'}]})
 return list
})
watch(() => route.fullPath, () => {emit('close'); for(const group of groups.value) if(group.children.some(item=>item.to === route.path)) expanded.value[group.id] = true})
</script>
<template>
 <div class="management-sidebar-wrap support-ui staff-ui">
  <button v-if="open" class="sidebar-backdrop" aria-label="關閉管理選單" @click="emit('close')"></button>
  <aside ref="sidebar" id="management-sidebar" class="management-sidebar" :class="{open}" aria-label="後台功能選單" @keydown.esc="emit('close')" @keydown.tab="trap">
   <div class="sidebar-heading"><small>WORKSPACE</small><strong>{{admin ? '管理者工作區' : '工作後台'}}</strong></div>
   <RouterLink v-if="admin" class="sidebar-dashboard" to="/admin" @click="emit('close')"><UiIcon name="dashboard" /><span>主頁</span></RouterLink>
   <nav>
    <section v-for="group in groups" :key="group.id" class="sidebar-group">
     <button class="sidebar-group-toggle" :aria-expanded="!!expanded[group.id]" :aria-controls="'menu-'+group.id" @click="expanded[group.id] = !expanded[group.id]"><UiIcon :name="group.icon" /><span>{{group.name}}</span><UiIcon class="sidebar-chevron" :class="{expanded:expanded[group.id]}" name="chevron" /></button>
     <div v-show="expanded[group.id]" :id="'menu-'+group.id" class="sidebar-children">
      <template v-for="item in group.children" :key="item.name"><RouterLink v-if="item.to" :to="item.to" @click="emit('close')">{{item.name}}</RouterLink><button v-else disabled :title="item.name + '尚未接入功能'">{{item.name}}<small>尚未接入</small></button></template>
     </div>
    </section>
   </nav>
   <p class="sidebar-note">依帳號權限顯示功能<br>本版僅包含客服功能。</p>
  </aside>
 </div>
</template>
