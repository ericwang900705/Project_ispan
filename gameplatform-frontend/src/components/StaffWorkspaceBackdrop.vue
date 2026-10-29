<script setup>
// 登入後工作區的固定背景；獨立於頁面卡片，捲動及切換管理頁仍保留。
</script>

<template>
  <div class="staff-workspace-backdrop" aria-hidden="true">
    <div class="workspace-grid" />
    <div class="workspace-orbit orbit-outer" />
    <div class="workspace-orbit orbit-inner" />
    <div class="workspace-horizon" />
  </div>
</template>

<style scoped>
.staff-workspace-backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(ellipse at 90% 8%, #93d7e480, transparent 50%),
    radial-gradient(ellipse at 12% 88%, #90b3e75c, transparent 45%),
    linear-gradient(150deg, #e0effa, #bdd8ec 55%, #90b5d2);
}

.workspace-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(#436e8c0a 1px, transparent 1px), linear-gradient(90deg, #436e8c0a 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(135deg, transparent 10%, #000 70%);
}

.workspace-orbit {
  position: absolute;
  width: 680px;
  height: 680px;
  right: -340px;
  top: 70px;
  border: 1px solid #fff9;
  border-radius: 50%;
}

.orbit-inner {
  width: 530px;
  height: 530px;
  right: -265px;
  top: 145px;
  border-color: #fff6;
}

.workspace-horizon {
  position: absolute;
  width: 75vw;
  height: 30vw;
  left: 20vw;
  bottom: -25vw;
  border-radius: 50%;
  border: 1px solid #ffffff70;
  box-shadow: 0 0 0 45px #ffffff0b, 0 0 0 90px #ffffff09;
}

@media (max-width: 700px) {
  .workspace-grid {
    background-size: 40px 40px;
  }

  .workspace-orbit {
    opacity: .5;
  }
}
</style>
