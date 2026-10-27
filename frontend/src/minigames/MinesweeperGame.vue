<script setup>
// 踩地雷：左鍵翻開、右鍵插旗（手機切換「插旗模式」），翻開所有安全格子就贏
// 第一下一定不會踩到地雷：地雷等第一次點下去之後才埋，而且避開點的那格和周圍 8 格
import { ref, computed, onBeforeUnmount } from 'vue'
import { useBest } from './best'

const LEVELS = {
  easy: { label: '初級', cols: 9, rows: 9, mines: 10 },
  normal: { label: '中級', cols: 16, rows: 16, mines: 40 },
}

const level = ref('easy')
const cells = ref([]) // { mine, adj（周圍地雷數）, open, flag }
const state = ref('ready') // ready 還沒點 / playing 遊戲中 / won 贏了 / lost 踩到地雷
const seconds = ref(0)
const flagMode = ref(false)
const newRecord = ref(false)
const bests = { easy: useBest('minesweeper-easy', true), normal: useBest('minesweeper-normal', true) }
const best = computed(() => bests[level.value].best.value)
let timer = null

const config = computed(() => LEVELS[level.value])
const flagsLeft = computed(() => config.value.mines - cells.value.filter((c) => c.flag).length)

function newGame() {
  const { cols, rows } = config.value
  cells.value = Array.from({ length: cols * rows }, () => ({ mine: false, adj: 0, open: false, flag: false }))
  state.value = 'ready'
  seconds.value = 0
  newRecord.value = false
  clearInterval(timer)
}

function setLevel(key) {
  level.value = key
  newGame()
}

// 某一格周圍 8 格的編號
function neighbors(i) {
  const { cols, rows } = config.value
  const x = i % cols
  const y = Math.floor(i / cols)
  const result = []
  for (let dy = -1; dy <= 1; dy++) {
    for (let dx = -1; dx <= 1; dx++) {
      const nx = x + dx
      const ny = y + dy
      if ((dx || dy) && nx >= 0 && ny >= 0 && nx < cols && ny < rows) result.push(ny * cols + nx)
    }
  }
  return result
}

// 埋地雷：避開第一下點的格子和它周圍
function plantMines(safe) {
  const forbidden = new Set([safe, ...neighbors(safe)])
  const candidates = cells.value.map((_, i) => i).filter((i) => !forbidden.has(i))
  for (let n = 0; n < config.value.mines; n++) {
    const pick = Math.floor(Math.random() * candidates.length)
    cells.value[candidates[pick]].mine = true
    candidates.splice(pick, 1)
  }
  cells.value.forEach((c, i) => {
    c.adj = neighbors(i).filter((j) => cells.value[j].mine).length
  })
}

function open(i) {
  if (state.value === 'won' || state.value === 'lost') return
  const cell = cells.value[i]
  if (flagMode.value) return toggleFlag(i)
  if (cell.flag) return
  if (state.value === 'ready') {
    plantMines(i)
    state.value = 'playing'
    timer = setInterval(() => seconds.value++, 1000)
  }
  // 已經翻開的數字格：周圍插的旗子數量剛好等於數字時，一次翻開周圍其他格子
  if (cell.open) {
    const around = neighbors(i)
    if (cell.adj && around.filter((j) => cells.value[j].flag).length === cell.adj) {
      around.forEach((j) => reveal(j))
    }
  } else {
    reveal(i)
  }
  checkWin()
}

// 翻開一格；是 0 的話往外擴散
function reveal(start) {
  const stack = [start]
  while (stack.length) {
    const i = stack.pop()
    const cell = cells.value[i]
    if (cell.open || cell.flag) continue
    cell.open = true
    if (cell.mine) {
      lose()
      return
    }
    if (cell.adj === 0) stack.push(...neighbors(i))
  }
}

function toggleFlag(i) {
  const cell = cells.value[i]
  if (cell.open || state.value === 'won' || state.value === 'lost') return
  cell.flag = !cell.flag
}

function lose() {
  state.value = 'lost'
  clearInterval(timer)
  cells.value.forEach((c) => { if (c.mine) c.open = true })
}

function checkWin() {
  if (state.value !== 'playing') return
  if (cells.value.every((c) => c.mine || c.open)) {
    state.value = 'won'
    clearInterval(timer)
    cells.value.forEach((c) => { if (c.mine) c.flag = true })
    newRecord.value = bests[level.value].submit(seconds.value)
  }
}

// 數字的顏色
const NUM_COLORS = ['', '#6aa8ff', '#7fd36a', '#ff7a6a', '#b48cff', '#ffb74d', '#4dd0e1', '#e4e8f8', '#98a2c8']

newGame()
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="game">
    <div class="bar">
      <div class="levels">
        <button v-for="(lv, key) in LEVELS" :key="key" :class="{ on: level === key }" @click="setLevel(key)">{{ lv.label }}</button>
      </div>
      <span>🚩 <b>{{ flagsLeft }}</b></span>
      <span>⏱ <b>{{ seconds }}</b> 秒</span>
      <span>最佳 <b>{{ best === null ? '—' : `${best} 秒` }}</b></span>
    </div>
    <div class="board" :style="{ gridTemplateColumns: `repeat(${config.cols}, 1fr)`, maxWidth: `${config.cols * 34}px` }"
      @contextmenu.prevent>
      <button v-for="(c, i) in cells" :key="i" class="cell" :class="{ open: c.open, boom: c.open && c.mine }"
        :style="c.open && !c.mine && c.adj ? { color: NUM_COLORS[c.adj] } : null"
        @click="open(i)" @contextmenu.prevent="toggleFlag(i)">
        <template v-if="c.open">{{ c.mine ? '💣' : c.adj || '' }}</template>
        <template v-else-if="c.flag">🚩</template>
      </button>
    </div>
    <div class="foot">
      <p v-if="state === 'won'" class="result win">🎉 成功！花了 {{ seconds }} 秒<span v-if="newRecord">　🏆 新紀錄！</span></p>
      <p v-else-if="state === 'lost'" class="result lose">💥 踩到地雷了</p>
      <p v-else class="tip">左鍵翻開 · 右鍵插旗 · 點數字可以一次翻開周圍</p>
      <div class="actions">
        <button class="mode" :class="{ on: flagMode }" @click="flagMode = !flagMode">🚩 插旗模式{{ flagMode ? '：開' : '' }}</button>
        <button class="go" @click="newGame">{{ state === 'ready' ? '重新開始' : '再玩一次' }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.game { display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%; }
.bar { display: flex; align-items: center; flex-wrap: wrap; justify-content: center; gap: 18px; font-size: 15px; color: #98a2c8; }
.bar b { color: #d4a64a; font-size: 17px; }
.levels { display: flex; gap: 6px; }
.levels button { padding: 4px 12px; border-radius: 14px; border: 1px solid #2e4a9a; background: #121a4a; color: #98a2c8; cursor: pointer; }
.levels button.on { border-color: #d4a64a; color: #e4e8f8; }
.board { width: 100%; display: grid; gap: 2px; padding: 6px; border-radius: 8px; background: #11161f; user-select: none; }
.cell {
  aspect-ratio: 1; padding: 0; border: none; border-radius: 3px; cursor: pointer;
  background: #5a6b86; color: #e4e8f8; font-size: 15px; font-weight: 900; line-height: 1;
}
.cell:hover:not(.open) { background: #6d7fa0; }
.cell.open { background: #2c3a52; cursor: default; }
.cell.boom { background: #8a2c2c; }
.foot { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.foot p { margin: 0; }
.result { font-size: 18px; font-weight: bold; }
.win { color: #7fd36a; }
.lose { color: #ff7a6a; }
.tip { font-size: 13px; color: #98a2c8; }
.actions { display: flex; gap: 10px; }
.mode { padding: 6px 14px; border-radius: 6px; border: 1px solid #2e4a9a; background: #121a4a; color: #98a2c8; cursor: pointer; }
.mode.on { border-color: #ff5a4a; color: #ffb0a8; }
.go { padding: 6px 20px; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; background: #d4a64a; color: #1b0f0d; }
</style>
