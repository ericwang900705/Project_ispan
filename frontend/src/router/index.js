// createRouter：建立路由器；createWebHistory：使用 HTML5 History 模式（網址不會有 #）
import { createRouter, createWebHistory } from 'vue-router'

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
        path: '/point-store',//點數商店
        name: 'PointStore',
        component: () => import('../views/PointStore.vue')
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

// 匯出給 main.js 使用
export default router
