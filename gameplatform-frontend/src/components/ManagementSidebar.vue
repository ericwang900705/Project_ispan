<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { session } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close'])
const sidebar = ref(null)

watch(() =>
    props.open, async value => {
        if (value) {
            await nextTick();
            sidebar.value?.querySelector('a,button:not(:disabled)')?.focus()
        }
    }
)
function trap(event) {
    if (!props.open) return;
    const items = [...sidebar.value.querySelectorAll('a,button:not(:disabled)')]
        .filter(el => el.getClientRects().length);
    if (!items.length) return;
    const first = items[0]
    const last = items.at(-1);
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus()
    }
}
const route = useRoute()
const expanded = ref({})
const admin = computed(() => session.user?.role === 'ADMIN')
// 未串接項目只顯示狀態與負責模組，不提供會呼叫其他組 API 的路由。
const pending = (name, owner) => ({ name, owner })
const groups = computed(() => {
    const list = []
    if (admin.value) list.push(
        { id: 'staff', name: '後台人員', icon: 'users', children: [pending('人員與權限管理', '人員／權限組')] },
        { id: 'members', name: '會員管理', icon: 'users', children: [pending('會員列表', '會員組'), pending('會員處理紀錄', '會員組')] },
        { id: 'games', name: '遊戲管理', icon: 'game', children: [pending('遊戲搜尋', '遊戲組'), pending('上架審核', '遊戲／審核組'), pending('標籤管理', '遊戲組')] },
        { id: 'publishers', name: '發行商管理', icon: 'building', children: [pending('發行商列表', '發行商組'), pending('發行商資料', '發行商組')] },
        { id: 'orders', name: '訂單管理', icon: 'cart', children: [pending('訂單列表', '購物車／訂單組'), pending('退款管理', '訂單／金流組')] }
    )
    if (admin.value || session.user?.role === 'SUPPORT') list.push({
        id: 'support', name: '客服', icon: 'headset', children: [
            { name: '工作總覽', to: '/admin' },
            { name: '即時客服', to: '/admin/chats' },
            { name: '客服案件', to: '/tickets' }
        ]
    })
    if (admin.value) list.push(
        { id: 'comments', name: '評論', icon: 'chat', children: [pending('評論審核', '評論組')] },
        { id: 'offers', name: '優惠活動', icon: 'tag', children: [pending('優惠活動列表', '優惠活動組'), pending('優惠券管理', '優惠活動組')] },
        { id: 'permissions', name: '管理員權限管理', icon: 'shield', children: [pending('管理員列表', '人員／權限組'), pending('角色權限', '人員／權限組')] },
        { id: 'logs', name: '系統紀錄管理', icon: 'file', children: [pending('操作紀錄', '各模組負責人'), pending('登入紀錄', '登入／會員組')] }
    )
    return list
})
watch(() => route.fullPath,
    () => {
        emit('close');
        for (const group of groups.value)
            if (group.children.some(item => item.to === route.path))
                expanded.value[group.id] = true
    })
</script>
<template>
    <div class="management-sidebar-wrap support-ui staff-ui">
        <button v-if="open" class="sidebar-backdrop" aria-label="關閉管理選單" @click="emit('close')"></button>
        <aside ref="sidebar" id="management-sidebar" class="management-sidebar" :class="{ open }" aria-label="後台功能選單"
            @keydown.esc="emit('close')" @keydown.tab="trap">
            <div class="sidebar-heading">
                <small>WORKSPACE</small>
                <strong>{{ admin ? '管理者工作區' : '工作後台' }}</strong>
            </div>
            <RouterLink v-if="admin" class="sidebar-dashboard" to="/admin/overview" @click="emit('close')">
                <UiIcon name="dashboard" />
                <span>主頁</span>
            </RouterLink>
            <nav>
                <section v-for="group in groups" :key="group.id" class="sidebar-group">
                    <button class="sidebar-group-toggle" :aria-expanded="!!expanded[group.id]"
                        :aria-controls="'menu-' + group.id" @click="expanded[group.id] = !expanded[group.id]">

                        <UiIcon :name="group.icon" />
                        <span class="sidebar-group-name">{{ group.name }}<small
                                v-if="group.id !== 'support'">尚未串接</small></span>
                        <UiIcon class="sidebar-chevron" :class="{ expanded: expanded[group.id] }" name="chevron" />

                    </button>
                    <div v-show="expanded[group.id]" :id="'menu-' + group.id" class="sidebar-children">
                        <template v-for="item in group.children" :key="item.name">
                            <RouterLink v-if="item.to" :to="item.to" @click="emit('close')">
                                {{ item.name }}
                            </RouterLink>
                            <button v-else class="sidebar-pending" disabled
                                :title="`${item.name}：尚未串接，需由${item.owner}串接`">
                                <span>{{ item.name }}</span>
                                <small>尚未串接 · {{ item.owner }}串接</small>
                            </button>
                        </template>
                    </div>
                </section>
            </nav>
            <p class="sidebar-note">
                依帳號權限顯示功能<br>
                客服已串接；其餘項目顯示待串接模組。
            </p>
        </aside>
    </div>
</template>

<style scoped>
.support-ui .sidebar-children>.sidebar-pending {
    flex-direction: column;
    align-items: flex-start;
    gap: 3px;
    cursor: default;
}

.support-ui .sidebar-pending small {
    white-space: normal;
    overflow-wrap: anywhere;
    font-size: 10px;
    line-height: 1.6;
    color: #718492;
}

.support-ui .sidebar-note {
    color: #718492;
}

.sidebar-group-name {
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.sidebar-group-name small {
    font-size: 10px;
    font-weight: normal;
    color: #718492;
}
</style>
