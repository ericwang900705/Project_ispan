<template>
  <!-- 關於我們頁面最外層容器（桌機版會橫跨整列，顯示在導覽列的中下方） -->
  <div class="about-container">
    <!-- Banner 區塊：平台標題、標語與簡介 -->
    <section class="hero-section">
      <!-- 頁面主標題 -->
      <h1>平台系統介紹</h1>
      <!-- 英文標語 -->
      <p class="slogan"></p>
      <!-- 平台簡介文字 -->
      <p class="description">
       我們是專為全球玩家與開發者打造的數位遊戲發行平台。我們提供豐富的遊戲庫、流暢的下載體驗與熱絡的社群評論，讓每位玩家都能輕鬆探索並享受遊戲的無窮樂趣。
      </p>
    </section>

    <!-- 平台特色區塊：以卡片列出各項功能 -->
    <section class="features-section">
      <!-- 特色區塊標題 -->
      <h2>平台核心特色</h2>
      <!-- 卡片網格容器 -->
      <div class="features-grid">
        <!-- 特色卡片：遊戲庫 -->
        <div class="feature-card">
          <div class="icon">📚</div>
          <h3>遊戲庫</h3>
          <p>滿足自身喜好的遊戲庫。</p>
        </div>

        <!-- 特色卡片：遊戲 -->
        <div class="feature-card">
          <div class="icon">🎮</div>
          <h3>遊戲</h3>
          <p>多種遊戲任君挑選</p>
        </div>

        <!-- 特色卡片：點數商店 -->
        <div class="feature-card">
          <div class="icon">💎</div>
          <h3>點數商店</h3>
          <p>商品回饋商店</p>
        </div>

        <!-- 特色卡片：遊戲評論 -->
        <div class="feature-card">
          <div class="icon">💬</div>
          <h3>遊戲評論</h3>
          <p>提供遊戲意見交流</p>
        </div>

        <!-- 特色卡片：購物車 -->
        <div class="feature-card">
          <div class="icon">🛒</div>
          <h3>購物車</h3>
          <p>暫存商品區</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 外層容器樣式 */
.about-container {
  max-width: 1200px;     /* 最大寬度限制 */
  margin: 0 auto;        /* 水平置中 */
  padding: 2rem 1rem;    /* 上下 2rem、左右 1rem 內距 */
  width: 100%;           /* 佔滿可用寬度 */
  box-sizing: border-box; /* 寬度計算包含 padding 與 border */
  text-align: center;    /* 文字預設置中 */
}

/* Hero 區塊樣式 */
.hero-section {
  text-align: center;    /* 文字置中 */
  padding: 3rem 1rem;    /* 區塊內距 */
}

/* 主標題 */
.hero-section h1 {
  font-size: 2.5rem;     /* 標題字級 */
  color: #ffffff;        /* 白色文字 */
  margin-bottom: 0.5rem; /* 與標語的間距 */
}

/* 標語 */
.slogan {
  font-size: 1.2rem;     /* 標語字級 */
  color: #ff7a00;        /* 亮橘色強調 */
  font-weight: bold;     /* 粗體 */
  margin-bottom: 1.5rem; /* 與簡介的間距 */
}

/* 簡介文字 */
.description {
  max-width: 800px;      /* 限制行寬方便閱讀 */
  margin: 0 auto;        /* 水平置中 */
  line-height: 1.8;      /* 行高 */
  color: #cccccc;        /* 淺灰色文字 */
}

/* 特色區塊 */
.features-section {
  margin-top: 4rem;      /* 與 Banner 區塊的間距 */
}

/* 特色區塊標題 */
.features-section h2 {
  text-align: center;    /* 標題置中 */
  margin-bottom: 2rem;   /* 與卡片的間距 */
  font-size: 1.8rem;     /* 標題字級 */
}

/* 卡片網格：手機版預設一欄 */
.features-grid {
  display: grid;                         /* 使用 Grid 排版 */
  grid-template-columns: repeat(1, 1fr); /* 一欄 */
  gap: 1.5rem;                           /* 卡片間距 */
}

/* 卡片樣式 */
.feature-card {
  background-color: #1a4697; /* 暗藍色背景 */
  border:0px solid #474c84; /* 深藍色邊框 */
  border-radius: 8px;        /* 圓角 */
  padding: 1.5rem;           /* 卡片內距 */
  text-align: center;        /* 內容置中 */
  transition: transform 0.2s ease, border-color 0.2s ease; /* 懸停動畫 */
}

/* 卡片懸停效果 */
.feature-card:hover {
  transform: translateY(-5px); /* 向上浮起 */
  border-color: #6a2182;       /* 懸停時變紫邊 */
}

/* 卡片圖示 */
.feature-card .icon {
  font-size: 2.5rem;     /* 圖示大小 */
  margin-bottom: 1rem;   /* 與標題的間距 */
}

/* 卡片標題 */
.feature-card h3 {
  font-size: 1.3rem;     /* 標題字級 */
  margin-bottom: 0.5rem; /* 與說明的間距 */
  color: #ffffff;        /* 白色文字 */
}

/* 卡片說明文字 */
.feature-card p {
  color: #aaaaaa;        /* 灰色文字 */
  font-size: 0.95rem;    /* 說明字級 */
  line-height: 1.5;      /* 行高 */
}

/* 平板版響應式 RWD：卡片兩欄 */
@media (min-width: 768px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr); /* 兩欄 */
  }
}

/* 桌機版響應式 RWD */
@media (min-width: 1024px) {
  .about-container {
    /* #app 在桌機版是左右兩欄的 grid，
       讓本頁橫跨所有欄位，排到導覽列下方並置中（中下方） */
    grid-column: 1 / -1;
  }

  .features-grid {
    grid-template-columns: repeat(4, 1fr); /* 四欄 */
  }
}
</style>
