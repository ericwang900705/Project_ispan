<script setup>
// 打磚塊：滑鼠或方向鍵移動板子，空白鍵 / 點一下發球；3 條命，每打掉一塊球會快一點
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useBest } from './best'

const W = 480
const H = 360
const PADDLE_W = 80
const PADDLE_H = 10
const BALL_R = 6
const COLS = 8
const ROWS = 5
const BRICK_W = 52
const BRICK_H = 16
const BRICK_GAP = 6
const BRICK_LEFT = (W - (COLS * BRICK_W + (COLS - 1) * BRICK_GAP)) / 2
const BRICK_TOP = 40
const ROW_COLORS = ['#ff6b6b', '#ffa94d', '#ffd43b', '#69db7c', '#4dabf7']
const ROW_POINTS = [50, 40, 30, 20, 10] // 越上面越高分

const canvas = ref(null)
const score = ref(0)
const lives = ref(3)
const state = ref('ready') // ready 球在板子上 / playing / won 全部打完 / over 沒命了
const newRecord = ref(false)
const { best, submit } = useBest('breakout')

let paddleX = (W - PADDLE_W) / 2
let ball = { x: 0, y: 0, vx: 0, vy: 0 }
let bricks = []
let keys = { left: false, right: false }
let frame = null
let lastTime = 0

function buildBricks() {
  bricks = []
  for (let r = 0; r < ROWS; r++) {
    for (let c = 0; c < COLS; c++) {
      bricks.push({ x: BRICK_LEFT + c * (BRICK_W + BRICK_GAP), y: BRICK_TOP + r * (BRICK_H + BRICK_GAP), row: r, alive: true })
    }
  }
}

function newGame() {
  score.value = 0
  lives.value = 3
  newRecord.value = false
  buildBricks()
  resetBall()
}

// 球放回板子上，等玩家發球
function resetBall() {
  state.value = 'ready'
  ball = { x: paddleX + PADDLE_W / 2, y: H - 30 - BALL_R, vx: 0, vy: 0 }
}

function launch() {
  if (state.value === 'won' || state.value === 'over') {
    newGame()
    return
  }
  if (state.value !== 'ready') return
  state.value = 'playing'
  const speed = currentSpeed()
  const angle = (Math.random() * 0.6 - 0.3) - Math.PI / 2 // 往上，左右稍微偏一點
  ball.vx = Math.cos(angle) * speed
  ball.vy = Math.sin(angle) * speed
}

// 速度（每秒幾 px）：每打掉一塊加一點
function currentSpeed() {
  const broken = bricks.filter((b) => !b.alive).length
  return 260 + broken * 4
}

function update(dt) {
  // 板子
  if (keys.left) paddleX -= 420 * dt
  if (keys.right) paddleX += 420 * dt
  paddleX = Math.max(0, Math.min(W - PADDLE_W, paddleX))
  if (state.value === 'ready') {
    ball.x = paddleX + PADDLE_W / 2
    return
  }
  if (state.value !== 'playing') return

  ball.x += ball.vx * dt
  ball.y += ball.vy * dt
  // 牆壁
  if (ball.x < BALL_R) { ball.x = BALL_R; ball.vx = Math.abs(ball.vx) }
  if (ball.x > W - BALL_R) { ball.x = W - BALL_R; ball.vx = -Math.abs(ball.vx) }
  if (ball.y < BALL_R) { ball.y = BALL_R; ball.vy = Math.abs(ball.vy) }
  // 板子：打到板子的哪裡決定反彈角度（越邊邊越斜）
  const paddleY = H - 30
  if (ball.vy > 0 && ball.y + BALL_R >= paddleY && ball.y + BALL_R <= paddleY + PADDLE_H + 6
    && ball.x >= paddleX - BALL_R && ball.x <= paddleX + PADDLE_W + BALL_R) {
    const hit = (ball.x - (paddleX + PADDLE_W / 2)) / (PADDLE_W / 2) // -1（左邊）～ 1（右邊）
    const angle = hit * (Math.PI / 3) // 最斜 60 度
    const speed = currentSpeed()
    ball.vx = Math.sin(angle) * speed
    ball.vy = -Math.cos(angle) * speed
    ball.y = paddleY - BALL_R
  }
  // 掉下去
  if (ball.y > H + BALL_R) {
    lives.value--
    if (lives.value <= 0) {
      state.value = 'over'
      newRecord.value = submit(score.value)
    } else {
      resetBall()
    }
    return
  }
  // 磚塊：一次只處理一塊，從球比較深入的那一邊反彈
  for (const b of bricks) {
    if (!b.alive) continue
    const nearestX = Math.max(b.x, Math.min(ball.x, b.x + BRICK_W))
    const nearestY = Math.max(b.y, Math.min(ball.y, b.y + BRICK_H))
    const dx = ball.x - nearestX
    const dy = ball.y - nearestY
    if (dx * dx + dy * dy > BALL_R * BALL_R) continue
    b.alive = false
    score.value += ROW_POINTS[b.row]
    if (Math.abs(dx) > Math.abs(dy)) ball.vx = -ball.vx
    else ball.vy = -ball.vy
    // 球速跟著變快
    const speed = currentSpeed()
    const len = Math.hypot(ball.vx, ball.vy)
    ball.vx = (ball.vx / len) * speed
    ball.vy = (ball.vy / len) * speed
    if (bricks.every((x) => !x.alive)) {
      state.value = 'won'
      score.value += lives.value * 100 // 剩下的命每條加 100 分
      newRecord.value = submit(score.value)
    }
    break
  }
}

function draw() {
  const ctx = canvas.value?.getContext('2d')
  if (!ctx) return
  const bg = ctx.createLinearGradient(0, 0, 0, H)
  bg.addColorStop(0, '#141a52')
  bg.addColorStop(1, '#0a0c2e')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)
  for (const b of bricks) {
    if (!b.alive) continue
    ctx.fillStyle = ROW_COLORS[b.row]
    ctx.fillRect(b.x, b.y, BRICK_W, BRICK_H)
  }
  ctx.fillStyle = '#d4a64a'
  ctx.fillRect(paddleX, H - 30, PADDLE_W, PADDLE_H)
  ctx.fillStyle = '#f8f1dc'
  ctx.beginPath()
  ctx.arc(ball.x, ball.y, BALL_R, 0, Math.PI * 2)
  ctx.fill()
}

function loop(time) {
  // dt 最多算 1/30 秒：切到別的分頁再回來時，球不會一下子飛很遠
  const dt = Math.min((time - lastTime) / 1000, 1 / 30)
  lastTime = time
  update(dt)
  draw()
  frame = requestAnimationFrame(loop)
}

// 滑鼠在畫布上移動：板子跟著滑鼠（畫布可能被縮放，要換算回 480 寬的座標）
function onPointerMove(e) {
  const rect = canvas.value.getBoundingClientRect()
  const x = ((e.clientX - rect.left) / rect.width) * W
  paddleX = Math.max(0, Math.min(W - PADDLE_W, x - PADDLE_W / 2))
}

function onKeydown(e) {
  if (e.code === 'ArrowLeft' || e.code === 'KeyA') { keys.left = true; e.preventDefault() }
  if (e.code === 'ArrowRight' || e.code === 'KeyD') { keys.right = true; e.preventDefault() }
  if (e.code === 'Space') { e.preventDefault(); launch() }
}
function onKeyup(e) {
  if (e.code === 'ArrowLeft' || e.code === 'KeyA') keys.left = false
  if (e.code === 'ArrowRight' || e.code === 'KeyD') keys.right = false
}

onMounted(() => {
  newGame()
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keyup', onKeyup)
  lastTime = performance.now()
  frame = requestAnimationFrame(loop)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keyup', onKeyup)
})
</script>

<template>
  <div class="game">
    <div class="bar">
      <span>分數 <b>{{ score }}</b></span>
      <span>生命 <b>{{ '❤'.repeat(Math.max(lives, 0)) || '—' }}</b></span>
      <span>最佳 <b>{{ best ?? '—' }}</b></span>
    </div>
    <div class="stage">
      <canvas ref="canvas" :width="W" :height="H" @pointermove="onPointerMove" @pointerdown="onPointerMove" @click="launch"></canvas>
      <div v-if="state !== 'playing'" class="overlay">
        <template v-if="state === 'won'">
          <p class="big">🎉 全部打完了！</p>
          <p>得分 {{ score }}<span v-if="newRecord" class="record">　🏆 新紀錄！</span></p>
          <p class="tip">點一下再玩一次</p>
        </template>
        <template v-else-if="state === 'over'">
          <p class="big">遊戲結束</p>
          <p>得分 {{ score }}<span v-if="newRecord" class="record">　🏆 新紀錄！</span></p>
          <p class="tip">點一下再玩一次</p>
        </template>
        <p v-else class="tip">點一下或按空白鍵發球 · 滑鼠 / 方向鍵移動板子</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.game { display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%; }
.bar { display: flex; gap: 24px; font-size: 15px; color: #98a2c8; }
.bar b { color: #d4a64a; font-size: 18px; }
.stage { position: relative; width: 100%; max-width: 480px; aspect-ratio: 4 / 3; }
canvas { width: 100%; height: 100%; display: block; border-radius: 8px; border: 2px solid #2e4a9a; cursor: none; touch-action: none; }
/* 提示文字放下面，不要擋住磚塊；pointer-events: none 讓滑鼠穿過去，板子才跟得到滑鼠 */
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
  gap: 8px; padding: 0 16px 72px; border-radius: 8px; color: #e4e8f8; text-align: center; pointer-events: none;
}
.overlay p { margin: 0; text-shadow: 0 2px 6px rgba(0, 0, 0, .8); }
.big { font-size: 26px; font-weight: bold; }
.record { color: #f0c060; }
.tip { font-size: 14px; color: #c9cee8; }
</style>

