<script setup>
import { useRoute } from 'vue-router';
const route = useRoute();
function openChat() {
  window.dispatchEvent(new Event('open-chat'))
}
</script>

<template>
  <div class="page home">
    <section class="hero">
      <div>
        <span class="eyebrow">
          WE'RE HERE TO HELP
        </span>
        <h1>
          {{ route.path === '/guide' ? '有問題，慢慢說。' : '你好，今天需要什麼協助？' }}
        </h1>
        <p>
          小問題先聊聊，需要進一步確認的事，
          <br>
          我們會整理成案件，陪你追蹤到完成。
        </p>
        <button class="primary" @click="openChat">
          開啟線上詢問 ↗
        </button>
      </div>
      <div class="hero-art">
        <div class="bubble big">
          有我們在。
        </div>
        <div class="bubble little">
          一起找到答案 ✦
        </div>
        <div class="art-ring">
        </div>
      </div>
    </section>
    <div class="section-label">
      <h2>
        從這裡開始
      </h2>
      <span>
        你的客服紀錄會持續保留
      </span>
    </div>
    <div class="card-grid">
      <button class="feature-card" @click="openChat">
        <span class="icon-box">
          01
        </span>
        <h3>
          線上詢問
        </h3>
        <p>
          操作或帳戶有疑問？
          <br>
          點開右下角，直接跟客服聊聊。
        </p>
        <span class="card-link">
          開始詢問 ↗
        </span>
      </button>
      <RouterLink class="feature-card" to="/tickets">
        <span class="icon-box">
          02
        </span>
        <h3>
          追蹤我的案件
        </h3>
        <p>
          查看客服整理的摘要與處理進度，
          <br>
          在案件中繼續補充與回覆。
        </p>
        <span class="card-link">
          查看案件 ↗
        </span>
      </RouterLink>
      <div class="feature-card soft">
        <span class="icon-box">
          03
        </span>
        <h3>
          不用重新說一次
        </h3>
        <p>
          需要轉案時，客服會整理重點。
          <br>
          聊天紀錄與案件會一起保留。
        </p>
        <span class="card-link">
          讓問題接續處理
        </span>
      </div>
    </div>
    <section class="help-strip">
      <h3>
        切換頁面也能繼續聊
      </h3>
      <p>
        目前的對話會保留。轉成案件後，原問題請到案件頁補充；其他問題可再開始新對話。
      </p>
    </section>
  </div>
</template>
