<script setup>
// 首頁的粒子連線背景：光點慢慢飄，距離近的連成線；
// 滑鼠靠近會把光點推開，並跟附近的光點連金色的線
import { ref, onMounted, onUnmounted } from 'vue'

const canvas = ref(null)
let ctx = null
let dots = []          // 光點：{ x, y, vx, vy }（位置和速度）
let mouse = null       // 滑鼠位置；滑鼠離開視窗就是 null
let frameId = 0        // requestAnimationFrame 的編號，離開頁面時用來停止動畫
let width = 0
let height = 0

const LINK = 110       // 兩個光點距離小於這個數字才連線
const MOUSE_LINK = 160 // 滑鼠跟光點距離小於這個數字才連線
const PUSH = 100       // 滑鼠推開光點的範圍
// 使用者在系統裡開了「減少動態效果」，光點就不要自己飄
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

// 畫布大小跟著視窗；高解析度螢幕要乘上 devicePixelRatio，畫面才不會糊
function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  width = window.innerWidth
  height = window.innerHeight
  canvas.value.width = width * dpr
  canvas.value.height = height * dpr
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  // 光點數量依畫面大小決定，最多 160 個，避免電腦跑不動
  const count = Math.min(160, Math.round((width * height) / 9000))
  dots = Array.from({ length: count }, () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
  }))
}

function onMove(e) {
  mouse = { x: e.clientX, y: e.clientY }
}
function onLeave() {
  mouse = null
}

// 每一格畫面：移動光點 → 畫線 → 畫點，畫完再排下一格
function draw() {
  ctx.clearRect(0, 0, width, height)

  for (const d of dots) {
    if (!reduceMotion) {
      d.x += d.vx
      d.y += d.vy
    }
    // 碰到邊界就反彈
    if (d.x < 0 || d.x > width) d.vx *= -1
    if (d.y < 0 || d.y > height) d.vy *= -1
    // 滑鼠附近的光點被推開
    if (mouse) {
      const dx = d.x - mouse.x
      const dy = d.y - mouse.y
      const dist = Math.hypot(dx, dy)
      if (dist > 0 && dist < PUSH) {
        d.x += (dx / dist) * 2
        d.y += (dy / dist) * 2
      }
    }
  }

  ctx.lineWidth = 1
  for (let i = 0; i < dots.length; i++) {
    const a = dots[i]
    // 光點跟光點：越近線越亮（藍色）
    for (let j = i + 1; j < dots.length; j++) {
      const b = dots[j]
      const dist = Math.hypot(a.x - b.x, a.y - b.y)
      if (dist < LINK) {
        ctx.strokeStyle = `rgba(120, 150, 255, ${(1 - dist / LINK) * 0.45})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
    // 光點跟滑鼠：金色的線
    if (mouse) {
      const dist = Math.hypot(a.x - mouse.x, a.y - mouse.y)
      if (dist < MOUSE_LINK) {
        ctx.strokeStyle = `rgba(212, 166, 74, ${(1 - dist / MOUSE_LINK) * 0.8})`
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(mouse.x, mouse.y)
        ctx.stroke()
      }
    }
  }

  // 最後畫光點，蓋在線的上面
  ctx.fillStyle = 'rgba(201, 212, 255, 0.9)'
  for (const d of dots) {
    ctx.beginPath()
    ctx.arc(d.x, d.y, 1.8, 0, Math.PI * 2)
    ctx.fill()
  }

  frameId = requestAnimationFrame(draw)
}

onMounted(() => {
  ctx = canvas.value.getContext('2d')
  resize()
  window.addEventListener('resize', resize)
  window.addEventListener('pointermove', onMove)
  document.documentElement.addEventListener('mouseleave', onLeave)
  frameId = requestAnimationFrame(draw)
})

// 離開頁面時一定要停掉動畫、拿掉監聽，不然換頁後還會在背景一直跑
onUnmounted(() => {
  cancelAnimationFrame(frameId)
  window.removeEventListener('resize', resize)
  window.removeEventListener('pointermove', onMove)
  document.documentElement.removeEventListener('mouseleave', onLeave)
})
</script>

<template>
  <canvas ref="canvas" class="particles"></canvas>
</template>

<style scoped>
/* 固定撐滿整個視窗、放在所有內容後面
   pointer-events: none → 滑鼠點擊會穿過畫布，不會擋到上面的按鈕 */
.particles {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  pointer-events: none;
  background: #0b0c32;
}
</style>
