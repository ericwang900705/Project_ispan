<script setup>
// 貪食蛇：方向鍵 / WASD 控制，吃到果實變長、變快，撞牆或撞到自己就結束
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useBest } from './best'

const SIZE = 20 // 20 × 20 格
const CELL = 20 // 每格 20px，畫布 400 × 400
const DIRS = {
  ArrowUp: { x: 0, y: -1 }, KeyW: { x: 0, y: -1 },
  ArrowDown: { x: 0, y: 1 }, KeyS: { x: 0, y: 1 },
  ArrowLeft: { x: -1, y: 0 }, KeyA: { x: -1, y: 0 },
  ArrowRight: { x: 1, y: 0 }, KeyD: { x: 1, y: 0 },
}

const canvas = ref(null)
const score = ref(0)
const state = ref('ready') // ready 還沒開始 / playing 遊戲中 / paused 暫停 / over 結束
const newRecord = ref(false)
const { best, submit } = useBest('snake')

let snake = []
let dir = DIRS.ArrowRight
let nextDir = dir
let food = { x: 0, y: 0 }
let timer = null

function randomCell() {
  return Math.floor(Math.random() * SIZE)
}

function reset() {
  snake = [{ x: 8, y: 10 }, { x: 7, y: 10 }, { x: 6, y: 10 }]
  dir = DIRS.ArrowRight
  nextDir = dir
  score.value = 0
  newRecord.value = false
  placeFood()
}

// 果實不能出現在蛇身上
function placeFood() {
  do {
    food = { x: randomCell(), y: randomCell() }
  } while (snake.some((s) => s.x === food.x && s.y === food.y))
}

function start() {
  if (state.value === 'ready' || state.value === 'over') reset()
  state.value = 'playing'
  schedule()
}

function togglePause() {
  if (state.value === 'playing') {
    state.value = 'paused'
    clearTimeout(timer)
  } else if (state.value === 'paused') {
    start()
  }
}

// 吃越多走越快：每吃一個快 3ms，最快 60ms 走一格
function schedule() {
  clearTimeout(timer)
  timer = setTimeout(step, Math.max(60, 150 - score.value * 3))
}

function step() {
  if (state.value !== 'playing') return
  dir = nextDir
  const head = { x: snake[0].x + dir.x, y: snake[0].y + dir.y }
  const eats = head.x === food.x && head.y === food.y
  // 沒吃到東西時尾巴會移走，所以撞到「尾巴那格」不算撞到自己
  const body = eats ? snake : snake.slice(0, -1)
  const hitWall = head.x < 0 || head.y < 0 || head.x >= SIZE || head.y >= SIZE
  if (hitWall || body.some((s) => s.x === head.x && s.y === head.y)) {
    state.value = 'over'
    newRecord.value = submit(score.value)
    draw()
    return
  }
  snake.unshift(head)
  if (eats) {
    score.value++
    placeFood()
  } else {
    snake.pop()
  }
  draw()
  schedule()
}

// 換方向：不能直接掉頭（例如往右時按左）
function turn(d) {
  if (d.x === -dir.x && d.y === -dir.y) return
  nextDir = d
  if (state.value === 'ready' || state.value === 'over') start()
}

function onKeydown(e) {
  if (DIRS[e.code]) {
    e.preventDefault() // 方向鍵不要捲動網頁
    turn(DIRS[e.code])
  } else if (e.code === 'Space') {
    e.preventDefault()
    if (state.value === 'ready' || state.value === 'over') start()
    else togglePause()
  }
}

function draw() {
  const ctx = canvas.value?.getContext('2d')
  if (!ctx) return
  ctx.fillStyle = '#07201a'
  ctx.fillRect(0, 0, SIZE * CELL, SIZE * CELL)
  // 淡淡的格線
  ctx.strokeStyle = '#123a2e'
  for (let i = 1; i < SIZE; i++) {
    ctx.beginPath(); ctx.moveTo(i * CELL, 0); ctx.lineTo(i * CELL, SIZE * CELL); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(0, i * CELL); ctx.lineTo(SIZE * CELL, i * CELL); ctx.stroke()
  }
  // 果實
  ctx.fillStyle = '#ff6b4a'
  ctx.beginPath()
  ctx.arc(food.x * CELL + CELL / 2, food.y * CELL + CELL / 2, CELL / 2 - 3, 0, Math.PI * 2)
  ctx.fill()
  // 蛇：頭比較亮
  snake.forEach((s, i) => {
    ctx.fillStyle = i === 0 ? '#d4f78a' : '#9be15d'
    ctx.fillRect(s.x * CELL + 1, s.y * CELL + 1, CELL - 2, CELL - 2)
  })
}

onMounted(() => {
  reset()
  draw()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="game">
    <div class="bar">
      <span>分數 <b>{{ score }}</b></span>
      <span>最佳 <b>{{ best ?? '—' }}</b></span>
    </div>
    <div class="stage">
      <canvas ref="canvas" :width="SIZE * CELL" :height="SIZE * CELL"></canvas>
      <div v-if="state !== 'playing'" class="overlay">
        <template v-if="state === 'over'">
          <p class="big">遊戲結束</p>
          <p>得分 {{ score }}<span v-if="newRecord" class="record">　🏆 新紀錄！</span></p>
        </template>
        <p v-else-if="state === 'paused'" class="big">暫停中</p>
        <p v-else class="big">貪食蛇</p>
        <button class="go" @click="start">{{ state === 'paused' ? '繼續' : state === 'over' ? '再玩一次' : '開始' }}</button>
        <p class="tip">方向鍵 / WASD 移動 · 空白鍵暫停</p>
      </div>
    </div>
    <!-- 手機用的方向按鈕 -->
    <div class="pad">
      <button @click="turn(DIRS.ArrowUp)">▲</button>
      <div>
        <button @click="turn(DIRS.ArrowLeft)">◀</button>
        <button @click="turn(DIRS.ArrowDown)">▼</button>
        <button @click="turn(DIRS.ArrowRight)">▶</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.game { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.bar { display: flex; gap: 24px; font-size: 15px; color: #98a2c8; }
.bar b { color: #d4a64a; font-size: 18px; }
.stage { position: relative; width: 100%; max-width: 400px; aspect-ratio: 1; }
canvas { width: 100%; height: 100%; display: block; border-radius: 8px; border: 2px solid #1c5a44; }
.overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; background: rgba(7, 32, 26, .82); border-radius: 8px; color: #e4f8d6; text-align: center; padding: 16px;
}
.overlay p { margin: 0; }
.big { font-size: 28px; font-weight: bold; }
.record { color: #f0c060; }
.tip { font-size: 13px; color: #9fc4b4; }
.go { padding: 8px 28px; border: none; border-radius: 6px; font-size: 16px; font-weight: bold; cursor: pointer; background: #9be15d; color: #07201a; }
.go:hover { background: #b6ef7e; }
.pad { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.pad div { display: flex; gap: 6px; }
.pad button { width: 52px; height: 42px; border-radius: 8px; border: 1px solid #1c5a44; background: #0f3d2e; color: #d4f78a; font-size: 18px; cursor: pointer; }
.pad button:active { background: #1c5a44; }
@media (min-width: 900px) { .pad { display: none; } }
</style>
