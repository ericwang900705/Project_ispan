<script setup>
// 點數商店頁面的程式邏輯（尚未實作）
</script>

<template>
  <!-- 點數商店頁面主要內容 -->
  <main class="point-store">
    <header class="hero-page-heading hero-case-heading">
      <span class="hero-case-heading__eyebrow">POINT STORE</span>
      <div class="hero-case-heading__main">
        <div class="hero-page-heading__content">
          <h1 class="hero-page-heading__title">點數商店</h1>
          <p class="hero-page-heading__caption">探索平台點數相關功能。</p>
        </div>
      </div>
    </header>
    <p class="pending-note">尚未串接：點數商品、兌換紀錄與兌換 API 尚未整合。</p>
  </main>
</template>

<!-- 點數商店頁面樣式（scoped：只作用於此元件） -->
<style scoped>
/* Scoped styles for cart/views/PointStore.vue; Vue supplies data-v scope. */
.pending-note { padding: 1rem; border: 1px solid #e4cba1; border-radius: 10px; color: #75521c; background: #fff9ed; }

/* Page spacing only: common title appearance lives in global/hero-page-heading.css. */
.point-store { max-width: 1132px; min-height: 70vh; margin: 0 auto; padding: 32px 16px; color: var(--hero-page-ink); }
.point-store .hero-page-heading { margin-bottom: 20px; }
</style>
