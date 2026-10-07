<template>
  <!-- 404 頁面最外層容器：讓內容垂直、水平置中 -->
  <div class="not-found-container">
    <div class="content">
      <!-- 大字錯誤代碼 -->
      <h1 class="error-code">404</h1>
      <!-- 錯誤標題 -->
      <h2>頁面不存在</h2>
      <!-- 錯誤說明 -->
      <p>您尋找的頁面不存在，或已被移至其他網址。</p>
      
      <!-- 返回首頁按鈕（使用 router-link 切換頁面不會重新整理） -->
      <router-link to="/" class="btn-home">
        返回首頁
      </router-link>
    </div>
  </div>
</template>

<!-- scoped：樣式只作用於此元件 -->
<style scoped>
/* 外層容器：至少 80% 視窗高度，使用 flex 讓內容置中 */
.not-found-container {
  min-height: 80vh;        /* 最小高度 */
  display: flex;           /* flex 排版 */
  align-items: center;     /* 垂直置中 */
  justify-content: center; /* 水平置中 */
  text-align: center;      /* 文字置中 */
  color: #ffffff;          /* 白色文字 */
  padding: 2rem;           /* 內距 */
}

/* 404 大字 */
.error-code {
  font-size: 8rem;   /* 超大字級 */
  font-weight: 900;  /* 最粗字重 */
  margin: 0;         /* 移除預設外距 */
  color: #00bfff; /* 亮藍色文字 */
  text-shadow: 0 0 20px rgba(0, 191, 255, 0.5); /* 藍色光暈效果 */
  line-height: 1;    /* 行高縮小，避免上下空白過多 */
}

/* 錯誤標題 */
h2 {
  font-size: 2rem;         /* 標題字級 */
  margin: 1.5rem 0 1rem;   /* 上 1.5rem、下 1rem 外距 */
  color: #ffffff;          /* 白色文字 */
}

/* 錯誤說明文字 */
p {
  color: #aaaaaa;          /* 灰色文字 */
  margin-bottom: 2rem;     /* 與按鈕的間距 */
  font-size: 1.1rem;       /* 字級 */
}

/* 返回首頁按鈕 */
.btn-home {
  display: inline-block;   /* 讓連結可設定內距 */
  padding: 0.8rem 2rem;    /* 按鈕內距 */
  background-color: #ff7a00; /* 亮橘色按鈕 */
  color: #ffffff;          /* 白色文字 */
  text-decoration: none;   /* 移除底線 */
  font-weight: bold;       /* 粗體 */
  border-radius: 6px;      /* 圓角 */
  transition: background-color 0.2s ease, transform 0.2s ease; /* 懸停動畫 */
}

/* 按鈕懸停效果 */
.btn-home:hover {
  background-color: #ff9433;   /* 顏色變淺 */
  transform: translateY(-2px); /* 向上浮起 */
}
</style>
