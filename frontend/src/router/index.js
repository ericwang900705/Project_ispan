// createRouter：建立路由器；createWebHistory：使用 HTML5 History 模式（網址不會有 #）
import { createRouter, createWebHistory } from 'vue-router'
import { useModeStore } from '@/stores/mode.js'
// 平台名稱（分頁標題用）
import { SITE_NAME } from '@/stores/studio'
//定義路由
// 每個路由包含：path（網址路徑）、name（路由名稱）、component（要顯示的頁面元件）
// component 使用 () => import(...) 動態載入（lazy loading），進入該頁時才下載對應程式碼
const routes = [

    {
        path: '/',//首頁（遊戲商店首頁，Steam 商店首頁風格）
        name: 'GamesHome',
        component: () => import('../views/GamesHome.vue')
    },
    {
        path: '/about',//平台介紹
        name: 'About',
        component: () => import('../views/AboutView.vue')
    },
    {
        path: '/shopping-cart',
        name: 'ShoppingCart',//購物車
        component: () => import('../views/ShoppingCart.vue')
    },
    {
        path: '/orders',//我的訂單
        name: 'Orders',
        component: () => import('../views/Orders.vue')
    },
    {
        path: '/coupons',//優惠券：領券中心 + 我的優惠券
        name: 'Coupons',
        component: () => import('../views/Coupons.vue')
    },
    {
        // 遊玩小遊戲：:id 是遊戲編號，遊戲庫裡有的才能玩
        path: '/play/:id',
        name: 'PlayGame',
        component: () => import('../views/PlayGame.vue')
    },
    {
        path: '/member-games',//遊戲庫
        name: 'MemberGames',
        component: () => import('../views/MemberGames.vue')
    },
    {
        path: '/games',//舊的遊戲首頁網址：自動轉到首頁
        redirect: '/'
    },
    {
        // 所有遊戲：可帶 ?genre=射擊 只看某分類、?q=關鍵字 搜尋遊戲名稱
        path: '/games/all',
        name: 'Games',
        component: () => import('../views/Games.vue')
    },
    {
        // 遊戲詳情：介紹、價格、玩家評論；:id 是遊戲編號
        path: '/games/:id',
        name: 'GameDetail',
        component: () => import('../views/GameDetail.vue')
    }, {
        // 開發商後台：DeveloperLayout 是外框（左側選單），children 是右邊會換的內容
        path: '/developer',
        // meta：可以在路由上放自訂資料，路由守衛會讀它來決定能不能進
        meta: { role: 'developer' },
        component: () => import('../views/developer/DeveloperLayout.vue'),
        // meta.title：瀏覽器分頁標題（下面的 afterEach 會組成「數據總覽 | 開發商後台 | JavaPlay」）
        children: [
            // path: '' 就是 /developer 本身 → 數據總覽
            { path: '', name: 'DevDashboard', meta: { title: '數據總覽' }, component: () => import('../views/developer/Dashboard.vue') },
            // 子路由的 path 不加開頭的 /，'games' 會自動接成 /developer/games；可帶 ?status=OFF_SALE 只看某個狀態
            { path: 'games', name: 'DevGames', meta: { title: '我的遊戲' }, component: () => import('../views/developer/MyGames.vue') },
            // 新增 / 編輯遊戲：同一個表單元件，有 :id 就是編輯
            { path: 'games/new', name: 'DevGameNew', meta: { title: '新增遊戲' }, component: () => import('../views/developer/GameForm.vue') },
            { path: 'games/:id/edit', name: 'DevGameEdit', meta: { title: '編輯遊戲' }, component: () => import('../views/developer/GameForm.vue') },
            // 折扣 / 特賣：一次幫多款遊戲設定或取消特價
            { path: 'discounts', name: 'DevDiscounts', meta: { title: '折扣特賣' }, component: () => import('../views/developer/Discounts.vue') },
            // 評論管理：看玩家評論、回覆
            { path: 'reviews', name: 'DevReviews', meta: { title: '評論管理' }, component: () => import('../views/developer/DevReviews.vue') },
            // 訂單管理：異常訂單（取消後才收到錢）的補發 / 退款、已付款訂單的退款
            { path: 'orders', name: 'DevOrders', meta: { title: '訂單管理' }, component: () => import('../views/developer/DevOrders.vue') },
            // 工作室設定：名稱、Logo、簡介
            { path: 'studio', name: 'DevStudio', meta: { title: '工作室設定' }, component: () => import('../views/developer/StudioSettings.vue') },
        ],

    },
    {
        // 萬用路由：以上路徑都不符合時，顯示 404 頁面（必須放在最後）
        path: '/:pathMatch(.*)*',//404
        name: 'NotFound',
        component: () => import('../views/404.vue')
    }
]

// 建立路由器實例
// import.meta.env.BASE_URL：Vite 設定中的網站根路徑（預設為 '/'）
const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

// 路由守衛：每次換頁「之前」都會先執行這裡，回傳網址就會改去那個網址
// to.matched：這個網址經過的所有路由（包含父路由），只要有一層標了 role: 'developer' 就要檢查
router.beforeEach((to) => {
    const needDeveloper = to.matched.some((r) => r.meta.role === 'developer')
    if (needDeveloper && useModeStore().mode !== 'developer') {
        return '/' // 不是開發商模式：導回首頁
    }
    // 沒有 return（或 return true）：照原本的網址走
})

// 換頁「之後」：更新瀏覽器分頁標題
// 開發商後台：「數據總覽 | 開發商後台 | JavaPlay」；其他頁面：「JavaPlay」
router.afterEach((to) => {
    const inDeveloper = to.matched.some((r) => r.meta.role === 'developer')
    const parts = [to.meta.title, inDeveloper ? '開發商後台' : null, SITE_NAME].filter(Boolean)
    document.title = parts.join(' | ')
})


// 匯出給 main.js 使用
export default router
