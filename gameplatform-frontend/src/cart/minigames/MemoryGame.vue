<script setup>
// 記憶翻牌：16 張牌（8 組），一次翻兩張，一樣就留著，不一樣就蓋回去；用最少步數配完
import { ref, computed, onBeforeUnmount } from 'vue'
import { useBest } from './best'

const ICONS = ['🍓', '🍋', '🍇', '🍉', '🥝', '🍑', '🍒', '🍍']

const cards = ref([]) // { id, icon, open, matched }
const moves = ref(0)
const seconds = ref(0)
const newRecord = ref(false)
const { best, submit } = useBest('memory', true)
let picked = [] // 這一輪翻開的牌（最多 2 張）
let busy = false // 兩張不一樣時，等它們蓋回去之前不能再翻
let timer = null
let flipBack = null

const finished = computed(() => cards.value.length > 0 && cards.value.every((c) => c.matched))

// 洗牌（Fisher–Yates）
function shuffle(list) {
  const a = list.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
      ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function newGame() {
  clearInterval(timer)
  clearTimeout(flipBack)
  timer = null
  cards.value = shuffle([...ICONS, ...ICONS]).map((icon, id) => ({ id, icon, open: false, matched: false }))
  moves.value = 0
  seconds.value = 0
  newRecord.value = false
  picked = []
  busy = false
}

function flip(card) {
  if (busy || card.open || card.matched) return
  if (!timer) timer = setInterval(() => seconds.value++, 1000) // 翻第一張牌才開始計時
  card.open = true
  picked.push(card)
  if (picked.length < 2) return
  moves.value++
  const [a, b] = picked
  picked = []
  if (a.icon === b.icon) {
    a.matched = true
    b.matched = true
    if (finished.value) {
      clearInterval(timer)
      newRecord.value = submit(moves.value)
    }
  } else {
    busy = true
    flipBack = setTimeout(() => {
      a.open = false
      b.open = false
      busy = false
    }, 800)
  }
}

newGame()
onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(flipBack)
})
</script>

<template>
  <div class="game">
    <div class="bar">
      <span>步數 <b>{{ moves }}</b></span>
      <span>⏱ <b>{{ seconds }}</b> 秒</span>
      <span>最佳 <b>{{ best === null ? '—' : `${best} 步` }}</b></span>
      <button class="small" @click="newGame">重新洗牌</button>
    </div>
    <div class="board memory">
      <button v-for="c in cards" :key="c.id" class="card" :class="{ open: c.open || c.matched, matched: c.matched }"
        @click="flip(c)">
        <span class="inner">
          <span class="back">?</span>
          <span class="front">{{ c.icon }}</span>
        </span>
      </button>
    </div>
    <p v-if="finished" class="result">
      🎉 全部配對完成！{{ moves }} 步、{{ seconds }} 秒<span v-if="newRecord">　🏆 新紀錄！</span>
    </p>
    <p v-else class="tip">翻開兩張一樣的牌就配對成功</p>
  </div>
</template>

<style scoped>
.game {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 20px;
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
  border: 1px solid #6b4a8a;
  background: #3a1f4f;
  color: #f3e6ff;
  cursor: pointer;
}

.board {
  width: 100%;
  max-width: 420px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.card {
  aspect-ratio: 3 / 4;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  perspective: 600px;
}

/* 翻牌動畫：正反兩面背靠背，翻開時整張轉 180 度 */
.inner {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  transition: transform .35s;
  transform-style: preserve-3d;
}

.card.open .inner {
  transform: rotateY(180deg);
}

.back,
.front {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  backface-visibility: hidden;
  font-weight: 900;
}

.back {
  background: #d4a64a;
  color: #3a1f4f;
  font-size: 30px;
  border: 3px solid #f0c060;
}

.front {
  background: #f8f1dc;
  font-size: 40px;
  transform: rotateY(180deg);
}

.card.matched .front {
  box-shadow: 0 0 0 3px #7fd36a inset;
}

.result {
  margin: 0;
  font-size: 17px;
  font-weight: bold;
  color: #7fd36a;
}

.tip {
  margin: 0;
  font-size: 13px;
  color: #98a2c8;
}
</style>
