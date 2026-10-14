import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = rel => readFileSync(join(projectRoot, rel), 'utf8')
function findVue(folder) {
  return readdirSync(join(projectRoot, folder), { withFileTypes: true }).flatMap(e => e.isDirectory() ? findVue(join(folder, e.name)) : e.name.endsWith('.vue') ? [join(folder,e.name)] : [])
}
function channel(v) { v /= 255; return v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4 }
function luminance(hex) { const nums = hex.match(/[0-9a-f]{2}/gi).map(h=>parseInt(h,16)); return .2126*channel(nums[0])+.7152*channel(nums[1])+.0722*channel(nums[2]) }
function blend(top,bottom,alpha) {
  const channels = [top,bottom].map(h=>h.match(/[0-9a-f]{2}/gi).map(x=>parseInt(x,16)))
  return '#'+channels[0].map((c,i)=>Math.round(alpha*c+(1-alpha)*channels[1][i]).toString(16).padStart(2,'0')).join('')
}
function contrast(a,b) { const [x,y] = [luminance(a),luminance(b)].sort((p,q)=>q-p); return (x+.05)/(y+.05) }
test('全域 CSS 精簡為五個；Vue 各自樣式仍保持 scoped 隔離',()=>{
 const expected=['core.css','layout.css','publisher.css','support.css','theme.css']
 const files=readdirSync(join(projectRoot,'src/styles')).filter(name=>name.endsWith('.css')).sort()
 assert.deepEqual(files,expected)
 const main=read('src/main.js')
 for (const name of expected) assert.match(main,new RegExp(`import ['"]\./styles/${name.replace('.','\.')}['"]`))
 const components=findVue('src')
 let local=0
 for (const rel of components) {
   const component=read(rel)
   if (component.includes('<style scoped>')) local++
   assert.doesNotMatch(component,/<style\s+scoped\s+src=/,`obsolete external style: ${rel}`)
 }
 assert.ok(local>=16,`expected 16 or more scoped blocks; got ${local}`)
})
test('垂直漸層與半透明卡片維持原設計，導覽列固定',()=>{
 const theme=read('src/styles/theme.css')
 const layout=read('src/styles/layout.css')
 assert.match(theme,/linear-gradient\(to bottom,/)
 assert.match(theme,/background-attachment:\s*fixed/)
 assert.match(theme,/--hero-page-surface:\s*rgba\(/)
 assert.match(theme,/\.publisher-function-group/)
 assert.match(theme,/\.publisher-panel/)
 assert.match(theme,/\.hero-store-layout/)
 assert.match(layout,/position:\s*sticky/)
})
test('四種漸層背景下，卡片正文與次要文字對比符合 WCAG AA (>=4.5:1)',()=>{
 const bg=['#eaf6ff','#c5e0f7','#7eadd8','#194771']
 const text=['#17364e','#45637b','#4c677a']
 for (const b of bg) for (const t of text) {
   const surface=blend('#f7fcff',b,.90)
   assert.ok(contrast(t,surface)>=4.5,`${b} + ${t}: contrast = ${contrast(t,surface).toFixed(2)}`)
 }
})
test('標題使用我的客服案件風格，與分類和卡片同寬置中',()=>{
 const views=['Games','MemberGames','PointStore','ShoppingCart']
 for (const name of views) {
  const component=read(`src/cart/views/${name}.vue`)
  assert.match(component,/hero-page-heading__title/,`${name} missing shared title`)
  assert.match(component,/hero-case-heading__eyebrow/,`${name} missing eyebrow`)
  assert.match(component,/hero-page-heading/,`${name} missing shared heading`)
 }
 const theme=read('src/styles/theme.css')
 assert.match(theme,/hero-case-heading__eyebrow/)
 assert.match(theme,/max-width:\s*1200px/)
 assert.match(theme,/margin:\s*0 auto 26px/)
 assert.match(theme,/border:\s*0; border-radius:\s*0; background:\s*transparent/)
 assert.doesNotMatch(theme,/border-left:\s*4px solid/)
 const games=read('src/cart/views/Games.vue')
 assert.match(games,/\.section-head, \.panel \{ max-width: 1200px; margin: 0 auto; \}/)
 assert.match(games,/\.genre-bar \{ max-width: 1200px; margin: 12px auto 0/)
 assert.match(games,/minmax\(min\(100%, 260px\), 1fr\)/)
})
