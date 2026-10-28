<script setup>
// 2048：方向鍵 / WASD（手機用滑動）讓所有方塊往同一邊滑，相同數字碰到就合併
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useBest } from './best'

const KEYS = {
  ArrowLeft: 'left', KeyA: 'left', ArrowRight: 'right', KeyD: 'right',
  ArrowUp: 'up', KeyW: 'up', ArrowDown: 'down', KeyS: 'down',
}

// 棋盤：16 格攤平成一個陣列，0 代表空格
const board = ref(Array(16).fill(0))
const score = ref(0)
const over = ref(false)
const won = ref(false) // 拼出 2048 之後還可以繼續玩
const keepGoing = ref(false)
const newRecord = ref(false)
const { best, submit } = useBest('2048')

function newGame() {
  const b = Array(16).fill(0)
  addTile(b)
  addTile(b)
  board.value = b
  score.value = 0
  over.value = false
  won.value = false
  keepGoing.value = false
  newRecord.value = false
}

// 在隨機一個空格放 2（10% 機率是 4）
function addTile(b) {
  const empty = b.map((v, i) => (v ? -1 : i)).filter((i) => i >= 0)
  if (!empty.length) return
  b[empty[Math.floor(Math.random() * empty.length)]] = Math.random() < 0.9 ? 2 : 4
}

// 往某個方向滑時，每一條線的格子順序（第一格是方塊會靠過去的那一邊）
function lines(direction) {
  const result = []
  for (let i = 0; i < 4; i++) {
    const line = []
    for (let j = 0; j < 4; j++) {
      if (direction === 'left') line.push(i * 4 + j)
      else if (direction === 'right') line.push(i * 4 + (3 - j))
      else if (direction === 'up') line.push(j * 4 + i)
      else line.push((3 - j) * 4 + i)
    }
    result.push(line)
  }
  return result
}

function move(direction) {
  if (over.value || (won.value && !keepGoing.value)) return
  const b = board.value.slice()
  let moved = false
  for (const line of lines(direction)) {
    // 先把方塊靠過去，再兩兩合併（每個方塊一次只合併一次）
    const values = line.map((i) => b[i]).filter((v) => v)
    const merged = []
    for (let k = 0; k < values.length; k++) {
      if (values[k] === values[k + 1]) {
        merged.push(values[k] * 2)
        score.value += values[k] * 2
        if (values[k] * 2 === 2048) won.value = true
        k++
      } else {
        merged.push(values[k])
      }
    }
    while (merged.length < 4) merged.push(0)
    line.forEach((idx, k) => {
      if (b[idx] !== merged[k]) moved = true
      b[idx] = merged[k]
    })
  }
  if (!moved) return
  addTile(b)
  board.value = b
  // 拼出 2048 的那一刻先存一次分數（之後繼續玩、結束時會再比一次）
  if (won.value && !keepGoing.value) newRecord.value = submit(score.value)
  if (!canMove(b)) {
    over.value = true
    newRecord.value = submit(score.value)
  }
}

// 還有空格，或相鄰有一樣的數字，就還能動
function canMove(b) {
  for (let i = 0; i < 16; i++) {
    if (!b[i]) return true
    if (i % 4 < 3 && b[i] === b[i + 1]) return true
    if (i < 12 && b[i] === b[i + 4]) return true
  }
  return false
}

function onKeydown(e) {
  if (KEYS[e.code]) {
    e.preventDefault()
    move(KEYS[e.code])
  }
}

// 手機滑動：手指移動超過 30px 才算
let touchStart = null
function onTouchStart(e) {
  touchStart = { x: e.touches[0].clientX, y: e.touches[0].clientY }
}
function onTouchEnd(e) {
  if (!touchStart) return
  const dx = e.changedTouches[0].clientX - touchStart.x
  const dy = e.changedTouches[0].clientY - touchStart.y
  touchStart = null
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 30) return
  move(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'))
}

// 數字越大顏色越深
const COLORS = {
  2: ['#eee4da', '#776e65'], 4: ['#ede0c8', '#776e65'], 8: ['#f2b179', '#fff'], 16: ['#f59563', '#fff'],
  32: ['#f67c5f', '#fff'], 64: ['#f65e3b', '#fff'], 128: ['#edcf72', '#fff'], 256: ['#edcc61', '#fff'],
  512: ['#edc850', '#fff'], 1024: ['#edc53f', '#fff'], 2048: ['#edc22e', '#fff'],
}
function tileStyle(v) {
  const [bg, color] = COLORS[v] ?? ['#3c3a32', '#fff']
  return { background: bg, color, fontSize: v >= 1024 ? '26px' : v >= 128 ? '32px' : '38px' }
}

// 拼出 2048 之後選擇繼續玩
function continueAfterWin() {
  keepGoing.value = true
}

onMounted(() => {
  newGame()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="game">
    <div class="bar">
      <span>分數 <b>{{ score }}</b></span>
      <span>最佳 <b>{{ best ?? '—' }}</b></span>
      <button class="small" @click="newGame">重新開始</button>
    </div>
    <div class="board" @touchstart.passive="onTouchStart" @touchend="onTouchEnd">
      <div v-for="(v, i) in board" :key="i" class="cell">
        <div v-if="v" class="tile" :style="tileStyle(v)">{{ v }}</div>
      </div>
      <div v-if="over || (won && !keepGoing)" class="overlay">
        <p class="big">{{ over ? '沒有步可以走了' : '🎉 拼出 2048！' }}</p>
        <p>得分 {{ score }}<span v-if="newRecord" class="record">　🏆 新紀錄！</span></p>
        <div class="actions">
          <button v-if="!over" class="go" @click="continueAfterWin">繼續挑戰</button>
          <button class="go" @click="newGame">再玩一次</button>
        </div>
      </div>
    </div>
    <p class="tip">方向鍵 / WASD 滑動方塊（手機直接滑）</p>
  </div>
</template>

<style scoped>
.game {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.bar {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 15px;
  color: #98a2c8;
}

.bar b {
  color: #d4a64a;
  font-size: 18px;
}

.small {
  padding: 4px 12px;
  border-radius: 4px;
  border: 1px solid #5c4d7a;
  background: #3d3058;
  color: #e8dcf8;
  cursor: pointer;
}

.board {
  position: relative;
  width: 100%;
  max-width: 400px;
  aspect-ratio: 1;
  padding: 10px;
  border-radius: 10px;
  background: #3d3058;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  touch-action: none;
}

.cell {
  border-radius: 6px;
  background: #52436f;
  position: relative;
}

.tile {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-weight: 900;
  animation: pop .12s ease-out;
}

@keyframes pop {
  from {
    transform: scale(.7);
  }

  to {
    transform: scale(1);
  }
}

.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(43, 33, 64, .85);
  border-radius: 10px;
  color: #f3e6ff;
  text-align: center;
}

.overlay p {
  margin: 0;
}

.big {
  font-size: 26px;
  font-weight: bold;
}

.record {
  color: #f0c060;
}

.actions {
  display: flex;
  gap: 10px;
}

.go {
  padding: 8px 22px;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: bold;
  cursor: pointer;
  background: #edc22e;
  color: #2b2140;
}

.tip {
  margin: 0;
  font-size: 13px;
  color: #98a2c8;
}
</style>
