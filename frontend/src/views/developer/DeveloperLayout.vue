<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useStudioStore } from '@/stores/studio'

// 目前的工作室（名稱、Logo），顯示在選單最上面
const { studio } = storeToRefs(useStudioStore())

// 左側選單的項目：之後要加頁面，在這裡多加一行
const menu = [
  { to: '/developer', label: '📊 數據總覽' },
  { to: '/developer/games', label: '🎮 我的遊戲' },
  { to: '/developer/games/new', label: '＋ 新增遊戲' },
  { to: '/developer/discounts', label: '🏷️ 折扣特賣' },
  { to: '/developer/reviews', label: '💬 評論管理' },
  { to: '/developer/orders', label: '🧾 訂單管理' },
  { to: '/developer/studio', label: '⚙️ 工作室設定' },
]
</script>
<template>
    <div class="dev">
        <aside class="side">
            <!-- 工作室：Logo + 名稱，點了到工作室設定 -->
            <RouterLink to="/developer/studio" class="studio">
                <img v-if="studio.logoUrl" :src="studio.logoUrl" alt="" class="studio-logo" />
                <span v-else class="studio-logo empty">{{ studio.name.slice(0, 1) }}</span>
                <span class="studio-text">
                    <small>開發商後台</small>
                    <b>{{ studio.name }}</b>
                </span>
            </RouterLink>
            <nav>
               <RouterLink v-for="m in menu" :key="m.to" :to="m.to">{{ m.label }}</RouterLink>
            </nav>
        </aside>
        <!-- 巢狀路由的出口：/developer 底下的子頁面會顯示在這裡 -->
        <main class="content">
            <RouterView />
        </main>
    </div>
    </template>
    <style scoped>
    .dev { display: flex; gap: 24px; align-items: flex-start; max-width: 1200px; margin: 0 auto; padding: 24px 16px; color: #e4e8f8; }
    .side{
        width: 200px; flex-shrink: 0; position: sticky; top: 88px; padding:16px;
        background: rgba(18,26,74,.85); border: 1px solid#2e4a9a;border-radius: 8px;
    }
    /* 選單最上面的工作室 */
    .studio { display: flex; align-items: center; gap: 10px; padding: 4px 4px 14px; margin-bottom: 10px; border-bottom: 1px solid #2e4a9a; }
    .studio:hover { background: transparent; }
    .studio-logo { width: 40px; height: 40px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
    .studio-logo.empty { display: flex; align-items: center; justify-content: center; background: #1c2a66; color: #d4a64a; font-size: 18px; font-weight: bold; }
    .studio-text { display: flex; flex-direction: column; min-width: 0; }
    .studio-text small { font-size: 11px; color: #98a2c8; }
    .studio-text b { font-size: 15px; color: #d4a64a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .side nav{display: flex;flex-direction: column;gap:4px;}
    .side a {padding:8px 12px;border-radius: 6px;color: #98a2c8; font-size: 14px; }
    .side a:hover { background: #1c2a66; color: #e4e8f8; }
    .side a.router-link-exact-active { background: #1c2a66; color: #e4e8f8; border-left: 3px solid #d4a64a; }

    .content {flex:1;min-width: 0;}

    @media (max-width:800px){
        .dev{flex-direction: column;align-items: stretch;}
        .side {width: auto;position: static;}
        .side nav{flex-direction: row;flex-wrap: wrap;}
    }

    </style>