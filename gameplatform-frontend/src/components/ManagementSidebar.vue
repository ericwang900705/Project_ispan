<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { session } from '../api'
import { isHighestAdmin, hasPermission, homeFor, canManageStaff } from '../permissions'
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
const admin = computed(() => isHighestAdmin(session.user))
// 未串接項目只顯示狀態與負責模組，不提供會呼叫其他組 API 的路由。
const pending = (name, owner) => ({ name, owner })
const groups = computed(() => {
    const list = [], can = code => hasPermission(session.user, code)
    if (canManageStaff(session.user)) list.push({ id: 'staff', name: '管理員權限管理', icon: 'shield', children: [{ name: '人員與權限管理', to: '/admin/staff' }] })
    if (can('MEMBER_SUSPEND')) list.push({ id: 'members', name: '會員管理', icon: 'users', children: [{ name: '會員列表', to: '/admin/members' }] })
    if (can('PUBLISHER_REVIEW')) list.push({ id: 'publishers', name: '發行商管理', icon: 'building', children: [{ name: '發行商申請與審核', to: '/admin/publishers' }] })
    if (can('ORDER_VIEW') || can('REFUND_MANAGE')) list.push({ id: 'orders', name: '訂單管理', icon: 'cart', children: [can('ORDER_VIEW') && { name:'訂單列表',to:'/admin/orders' }, can('REFUND_MANAGE') && { name:'退款訂單查詢',to:'/admin/refunds' }].filter(Boolean) })
    if (can('GAME_REVIEW') || can('TAG_MANAGE')) list.push({ id: 'games', name: '遊戲管理', icon: 'game', children: [...(can('GAME_REVIEW') ? [{ name: '遊戲搜尋', to: '/admin/games' }, { name: '上架審核', to: '/admin/games?mode=pending' }, { name: '審核紀錄', to: '/admin/games?mode=history' }] : []), ...(can('TAG_MANAGE') ? [{ name:'平台標籤維護',to:'/admin/tags' }] : [])] })
    if (can('TICKET_HANDLE')) list.push({ id: 'support', name: '客服', icon: 'headset', children: [...(homeFor(session.user?.role) === '/admin/support' ? [] : [{ name: '工作總覽', to: '/admin/support' }]), { name: '即時客服', to: '/admin/chats' }, { name: '客服案件', to: '/tickets' }, ...(admin.value ? [{name:'常用回覆',to:'/admin/support/reply-templates'},{name:'FAQ 管理',to:'/admin/support/faqs'}] : [])] })
    if (can('COMMENT_REVIEW')) list.push({ id: 'comments', name: '評論', icon: 'chat', children: [{ name:'評論審核',to:'/admin/reviews' },{name:'評論檢舉',to:'/admin/review-reports'}] })
    if (can('PROMOTION_MANAGE') || can('COUPON_MANAGE')) list.push({ id: 'offers', name: '優惠活動', icon: 'tag', children: [can('PROMOTION_MANAGE') && pending('優惠活動列表', '優惠活動組'), can('COUPON_MANAGE') && { name:'優惠券管理',to:'/admin/coupons' }].filter(Boolean) })
    if (can('OPERATION_LOG_VIEW') || can('LOGIN_LOG_VIEW')) list.push({ id: 'logs', name: '系統紀錄管理', icon: 'file', children: [can('OPERATION_LOG_VIEW') && { name:'操作紀錄',to:'/admin/operations' }, can('LOGIN_LOG_VIEW') && { name:'會員登入紀錄',to:'/admin/login-logs' }].filter(Boolean) })
    return list
})
watch(() => route.fullPath,
    () => {
        emit('close');
        for (const group of groups.value)
            if (group.children.some(item => item.to?.split('?')[0] === route.path))
                expanded.value[group.id] = true
    }, { immediate: true })
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
            <RouterLink class="sidebar-dashboard" :to="homeFor(session.user?.role)" @click="emit('close')">
                <UiIcon name="dashboard" />
                <span>{{ admin ? '平台主頁' : '工作總覽' }}</span>
            </RouterLink>
            <nav>
                <section v-for="group in groups" :key="group.id" class="sidebar-group">
                    <button class="sidebar-group-toggle" :aria-expanded="!!expanded[group.id]"
                        :aria-controls="'menu-' + group.id" @click="expanded[group.id] = !expanded[group.id]">

                        <UiIcon :name="group.icon" />
                        <span class="sidebar-group-name">{{ group.name }}<small
                                v-if="group.children.every(item => !item.to)">尚未串接</small></span>
                        <UiIcon class="sidebar-chevron" :class="{ expanded: expanded[group.id] }" name="chevron" />

                    </button>
                    <div v-show="expanded[group.id]" :id="'menu-' + group.id" class="sidebar-children">
                        <template v-for="item in group.children" :key="item.name">
                            <RouterLink v-if="item.to" :to="item.to" active-class="" exact-active-class=""
                                :class="{ 'sidebar-active': route.fullPath === item.to }" @click="emit('close')">
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
                已串接功能依授權顯示；優惠活動尚待串接。
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
