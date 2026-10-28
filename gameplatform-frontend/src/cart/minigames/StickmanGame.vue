<script setup>
// 火柴人格鬥：霓虹對決（玩家對電腦 AI）
// A / D 移動、W 跳躍、J 拳擊、K 踢擊、L 防禦、U 氣功波（要 35 能量）、R 重開
// 遊戲邏輯照原本的 stickman_fighter.html；改成元件後，離開頁面時會停掉動畫和鍵盤監聽
import { ref, onMounted, onBeforeUnmount } from 'vue'

const canvas = ref(null)
const keys = new Set()
const BUTTONS = [
  { key: 'a', label: '◀' }, { key: 'd', label: '▶' }, { key: 'w', label: '跳躍' }, { key: 'j', label: '拳擊' },
  { key: 'k', label: '踢擊' }, { key: 'l', label: '防禦' }, { key: 'u', label: '氣功波' },
]

let ctx = null
let raf = null
const floor = 448
let p, e, particles = [], shots = [], over = false, winner = '', shake = 0, frame = 0

const clamp = (n, a, b) => Math.max(a, Math.min(b, n))
const rand = (a, b) => a + Math.random() * (b - a)

function fighter(x, color, face) {
  return { x, y: floor, vx: 0, vy: 0, hp: 100, en: 30, face, color, attack: null, at: 0, hit: false, guard: false, stun: 0, cool: 0 }
}

function reset() {
  p = fighter(225, '#56efff', 1)
  e = fighter(735, '#ff6d9c', -1)
  particles = []
  shots = []
  over = false
  winner = ''
  shake = 0
  frame = 0
}

function burst(x, y, color, n = 13) {
  for (let i = 0; i < n; i++) particles.push({ x, y, vx: rand(-5, 5), vy: rand(-6, 2), life: rand(12, 27), color })
}

function strike(f, type) {
  if (over || f.stun > 0 || f.attack || f.cool > 0) return
  if (type === 'special' && f.en < 35) return
  if (type === 'special') f.en -= 35
  f.attack = type
  f.at = type === 'kick' ? 25 : type === 'special' ? 34 : 17
  f.hit = false
  f.cool = type === 'kick' ? 17 : 10
}

// 被打到：防禦中只吃 22% 傷害、退得比較少
function damage(target, amount, dir) {
  const blocked = target.guard && target.stun === 0
  target.hp = clamp(target.hp - (blocked ? Math.ceil(amount * 0.22) : amount), 0, 100)
  target.vx = dir * (blocked ? 2 : 8)
  target.stun = blocked ? 5 : 15
  shake = blocked ? 3 : 9
  burst(target.x, target.y - 80, blocked ? '#b4faff' : '#ffe17c', blocked ? 7 : 17)
  if (target.hp <= 0) {
    over = true
    winner = target === e ? '玩家獲勝！' : '電腦獲勝！'
  }
}

// 更新一個角色：玩家看按鍵，電腦用簡單的 AI（靠近、攻擊、偶爾防禦或跳）
function updateF(f, other, isPlayer) {
  if (f.cool > 0) f.cool--
  if (f.stun > 0) f.stun--
  let move = 0
  let guard = false
  if (!over) {
    if (isPlayer) {
      move = (keys.has('d') ? 1 : 0) - (keys.has('a') ? 1 : 0)
      guard = keys.has('l')
      if (keys.has('w') && f.y >= floor) f.vy = -14
      if (keys.has('j')) strike(f, 'punch')
      if (keys.has('k')) strike(f, 'kick')
      if (keys.has('u')) strike(f, 'special')
    } else {
      const dist = other.x - f.x
      guard = other.attack && Math.abs(dist) < 110 && Math.random() < 0.07 ? true : f.guard && Math.random() < 0.88
      move = Math.abs(dist) > 78 ? Math.sign(dist) : Math.abs(dist) < 52 ? -Math.sign(dist) : 0
      if (frame % 32 === 0 && Math.abs(dist) < 118) strike(f, Math.random() < 0.35 ? 'kick' : 'punch')
      if (frame % 180 === 0 && Math.abs(dist) > 155) strike(f, 'special')
      if (frame % 135 === 0 && Math.random() < 0.28 && f.y >= floor) f.vy = -13
    }
  }
  if (f.stun > 0) guard = false
  f.guard = guard && !f.attack && f.stun === 0
  if (f.stun === 0) {
    if (!f.attack && !f.guard) f.vx = move * 4.1
    else f.vx *= 0.75
  }
  f.x = clamp(f.x + f.vx, 30, 930)
  f.vy += 0.7
  f.y += f.vy
  if (f.y > floor) {
    f.y = floor
    f.vy = 0
  }
  if (!f.attack && Math.abs(other.x - f.x) > 12) f.face = Math.sign(other.x - f.x)
  f.en = clamp(f.en + 0.045, 0, 100)

  if (f.attack) {
    f.at--
    const type = f.attack
    const range = type === 'kick' ? 112 : 82
    const active = type === 'kick' ? f.at >= 9 && f.at <= 16 : f.at >= 5 && f.at <= 11
    if (type === 'special' && f.at === 18) {
      shots.push({ x: f.x + f.face * 30, y: f.y - 85, vx: f.face * 9, owner: f, life: 105 })
      burst(f.x + f.face * 30, f.y - 85, f.color, 20)
    }
    if (type !== 'special' && active && !f.hit && Math.abs(other.x - f.x) < range && Math.abs(other.y - f.y) < 80
      && Math.sign(other.x - f.x) === f.face) {
      damage(other, type === 'kick' ? 16 : 9, f.face)
      f.en = clamp(f.en + 9, 0, 100)
      f.hit = true
    }
    if (f.at <= 0) f.attack = null
  }
}

function drawF(f) {
  const run = Math.sin(frame * 0.28) * Math.min(Math.abs(f.vx) / 4, 1) * 14
  ctx.save()
  ctx.translate(f.x, f.y)
  ctx.scale(f.face, 1)
  ctx.strokeStyle = f.color
  ctx.lineWidth = 7
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.shadowColor = f.color
  ctx.shadowBlur = 16
  // 頭、身體、腳
  ctx.beginPath()
  ctx.arc(0, -116, 17, 0, Math.PI * 2)
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(0, -99); ctx.lineTo(0, -53)
  ctx.moveTo(0, -53); ctx.lineTo(-18 + run, -5)
  ctx.moveTo(0, -53); ctx.lineTo(19 - run, -5)
  ctx.stroke()
  // 手：防禦、出拳、平常三種姿勢
  ctx.beginPath()
  ctx.moveTo(0, -91); ctx.lineTo(-23, -72); ctx.lineTo(-32, -51)
  if (f.guard) {
    ctx.moveTo(0, -91); ctx.lineTo(22, -104); ctx.lineTo(32, -89)
  } else if (f.attack === 'punch') {
    ctx.moveTo(0, -91); ctx.lineTo(33, -86); ctx.lineTo(67, -87)
  } else {
    ctx.moveTo(0, -91); ctx.lineTo(22, -69); ctx.lineTo(31, -51)
  }
  ctx.stroke()
  if (f.attack === 'kick') {
    ctx.beginPath()
    ctx.moveTo(0, -53); ctx.lineTo(39, -56); ctx.lineTo(83, -70)
    ctx.stroke()
  }
  if (f.guard) {
    ctx.strokeStyle = '#ffffff88'
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.arc(18, -80, 44, -1.2, 1.1)
    ctx.stroke()
  }
  ctx.restore()
}

function bar(x, y, w, value, color, label, flip = false) {
  ctx.fillStyle = '#1d2949'
  ctx.fillRect(x, y, w, 20)
  ctx.fillStyle = color
  const ww = (w * value) / 100
  ctx.fillRect(flip ? x + w - ww : x, y, ww, 20)
  ctx.strokeStyle = '#d7e7ff'
  ctx.lineWidth = 2
  ctx.strokeRect(x, y, w, 20)
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 17px system-ui'
  ctx.fillText(label, x, y - 10)
}

function draw() {
  ctx.save()
  if (shake > 0) {
    ctx.translate(rand(-shake, shake), rand(-shake, shake))
    shake *= 0.78
    if (shake < 0.5) shake = 0
  }
  const g = ctx.createLinearGradient(0, 0, 0, 540)
  g.addColorStop(0, '#101737')
  g.addColorStop(1, '#26365c')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 960, 540)
  ctx.strokeStyle = '#4a78a533'
  ctx.lineWidth = 1
  for (let x = 0; x < 960; x += 48) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, 540); ctx.stroke() }
  for (let y = 0; y < 540; y += 48) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(960, y); ctx.stroke() }
  ctx.fillStyle = '#1b3151'
  ctx.fillRect(0, floor, 960, 92)
  ctx.fillStyle = '#3bd6ff'
  ctx.fillRect(0, floor, 960, 3)
  ctx.fillStyle = '#ffffff22'
  ctx.fillRect(0, floor + 32, 960, 2)
  for (const s of shots) {
    ctx.shadowColor = s.owner.color
    ctx.shadowBlur = 30
    ctx.fillStyle = s.owner.color
    ctx.beginPath()
    ctx.arc(s.x, s.y, 17, 0, Math.PI * 2)
    ctx.fill()
    ctx.shadowBlur = 0
  }
  for (const t of particles) {
    ctx.globalAlpha = clamp(t.life / 25, 0, 1)
    ctx.fillStyle = t.color
    ctx.fillRect(t.x, t.y, 5, 5)
  }
  ctx.globalAlpha = 1
  drawF(p)
  drawF(e)
  ctx.restore()
  bar(32, 42, 340, p.hp, '#48e7ff', '玩家')
  bar(588, 42, 340, e.hp, '#ff628e', '電腦 AI', true)
  bar(32, 81, 220, p.en, '#f3d45b', '能量')
  ctx.fillStyle = '#dce8ff'
  ctx.font = 'bold 22px system-ui'
  ctx.textAlign = 'center'
  ctx.fillText('VS', 480, 63)
  if (over) {
    ctx.fillStyle = '#050a19bb'
    ctx.fillRect(0, 0, 960, 540)
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 54px system-ui'
    ctx.fillText(winner, 480, 240)
    ctx.font = '24px system-ui'
    ctx.fillText('按 R 或「重新開始」再戰一場', 480, 293)
  }
  ctx.textAlign = 'left'
}

function tick() {
  frame++
  if (!over) {
    updateF(p, e, true)
    updateF(e, p, false)
    for (const s of shots) {
      s.x += s.vx
      s.life--
      const t = s.owner === p ? e : p
      if (Math.abs(s.x - t.x) < 31 && Math.abs(s.y - (t.y - 85)) < 53) {
        damage(t, 19, Math.sign(s.vx))
        s.life = 0
      }
    }
    shots = shots.filter((s) => s.life > 0 && s.x > 0 && s.x < 960)
  }
  for (const t of particles) {
    t.x += t.vx
    t.y += t.vy
    t.vy += 0.25
    t.life--
  }
  particles = particles.filter((t) => t.life > 0)
  draw()
  raf = requestAnimationFrame(tick)
}

function onKeydown(ev) {
  const k = ev.key.toLowerCase()
  if (k.length === 1 && 'adwjklur'.includes(k)) {
    ev.preventDefault()
    keys.add(k)
    if (k === 'r') reset()
  }
}
function onKeyup(ev) {
  keys.delete(ev.key.toLowerCase())
}
function onBlur() {
  keys.clear()
}

// 畫面上的按鈕（手機用）：按住等於按住鍵盤
function press(ev, k) {
  ev.preventDefault()
  ev.currentTarget.setPointerCapture(ev.pointerId)
  keys.add(k)
}
function release(k) {
  keys.delete(k)
}

onMounted(() => {
  ctx = canvas.value.getContext('2d')
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keyup', onKeyup)
  window.addEventListener('blur', onBlur)
  reset()
  tick()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keyup', onKeyup)
  window.removeEventListener('blur', onBlur)
})
</script>

<template>
  <div class="game">
    <canvas ref="canvas" width="960" height="540"></canvas>
    <p class="hint">A / D 移動　W 跳躍　J 拳擊　K 踢擊　L 防禦　U 氣功波　R 重開</p>
    <div class="controls">
      <button v-for="b in BUTTONS" :key="b.key" @pointerdown="press($event, b.key)" @pointerup="release(b.key)"
        @pointercancel="release(b.key)" @lostpointercapture="release(b.key)">{{ b.label }}</button>
      <button class="restart" @click="reset">重新開始</button>
    </div>
  </div>
</template>

<style scoped>
.game { display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%; }
canvas {
  display: block; width: 100%; aspect-ratio: 16 / 9; border: 2px solid #385a85; border-radius: 14px;
  background: #0c1430; box-shadow: 0 0 35px #0b89ff40;
}
.hint { margin: 0; text-align: center; font-size: 14px; color: #b9c8e4; }
.controls { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
button {
  border: 1px solid #5079aa; background: #172b4c; color: white; border-radius: 9px; padding: 11px 15px;
  cursor: pointer; font-weight: bold; touch-action: none;
}
button:active { background: #347db1; }
.restart { background: #28617a; }
</style>

