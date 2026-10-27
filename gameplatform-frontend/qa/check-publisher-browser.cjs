// 完整 Vue 頁面操作；API 模擬成功空回應、上傳與草稿儲存，後端另由 Spring 測試驗證。
const assert = require('node:assert/strict')
const { chromium } = require('playwright')
let checks = 0
function check(value, label) { assert.ok(value, label); checks++ }
async function run() {
 const browser = await chromium.launch({ headless: true })
 try {
 const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
 await ctx.addInitScript(() => {
   const exp = Math.floor(Date.now() / 1000) + 3600
   sessionStorage.setItem('support-auth-session-shared-jwt-v2', JSON.stringify({ accessToken: 'e30.' + btoa(JSON.stringify({ exp })) + '.test', expiresAt: exp * 1000 }))
   const original = window.fetch
   window.fetch = (url, opts) => String(url).includes('/api/events') ? Promise.resolve(new Response(new ReadableStream({ start(c) { c.enqueue(new TextEncoder().encode('event: heartbeat\ndata: {}\n\n')) } }), { headers: { 'Content-Type': 'text/event-stream' } })) : original(url, opts)
   window.WebSocket = class { static OPEN=1; readyState=1; constructor(){setTimeout(()=>this.onopen?.({}),0)} send(){} close(){this.readyState=3} }
 })
 const localZip = '/api/publisher-assets/builds/7/11111111-1111-1111-1111-111111111111.zip'
 const video = '/uploads/publisher-files/videos/7/11111111-1111-1111-1111-111111111111.mp4'
 let savedDocument, savedBuilds, uploads = [], readAll = 0
 const game = { game_id: 4, game_name: '測試草稿', price: 599, description: '測試遊戲介紹', cover_url: '', release_date: null, status:'DRAFT', server_time:Date.now(),buyer_count:0, history:[], tags:[{tag_id:1,tag_name:'RPG'}],builds:[{build_id:1,version:'1.0',file_url:'https://example.com/game.zip',file_size:128,status:'ACTIVE'}],media:[],publish_checks:[{key:'basic',label:'基本資料',mode:'edit',complete:true},{key:'media',label:'遊戲封面與宣傳素材',mode:'media',complete:false},{key:'tags',label:'遊戲標籤',mode:'tags',complete:true},{key:'builds',label:'啟用版本',mode:'builds',complete:true}] }
 let notices=[{ notification_id:1,event_type:'CHAT_REPLY',target_path:'/member/support',body:'聊天室 #12 有新訊息',title:'客服已回覆聊天室',read_at:null },{ notification_id:2,event_type:'TICKET_REPLY',target_path:'/tickets/8',body:'案件 #8',title:'客服已回覆案件',read_at:null },{ notification_id:3,event_type:'GAME_REJECTED',target_path:'/publisher/games',related_game_id:4,title:'上架申請退回',body:'請補上素材',read_at:null }]
 await ctx.route('**/api/**', async route => {
   const req=route.request(), p=new URL(req.url()).pathname, method=req.method()
   let data=[]
   if (p==='/api/auth/me') data={id:7,role:'PUBLISHER',username:'publisher_test',displayName:'測試發行商',permissions:[]}
   else if (p==='/api/workspace/publisher/tags') data=[{tag_id:1,tag_name:'RPG'}]
   else if (p==='/api/workspace/publisher/games') data={items:[game],total:1}
   else if (p==='/api/workspace/publisher/games/4/document') {savedDocument=req.postDataJSON();game.cover_url=savedDocument.game.coverUrl;game.media=savedDocument.media.media.map(m=>({media_type:m.type,media_url:m.url,display_order:m.order}));data=game}
   else if (p==='/api/workspace/publisher/games/4/builds') {savedBuilds=req.postDataJSON();data=game}
   else if (p==='/api/workspace/publisher/games/4') data=game
   else if (p==='/api/publisher/files') {const body=req.postDataBuffer().toString();uploads.push(body);data=body.includes('VIDEO')?{url:video,type:'VIDEO',fileSize:24}:{url:localZip,type:'BUILD',fileSize:128}}
   else if (p==='/api/notifications') data={items:notices,unread:notices.filter(n=>!n.read_at).length}
   else if (/\/notifications\/.*read/.test(p)) {if(p.endsWith('read-all'))readAll++;notices.forEach(n=>n.read_at='2026-10-27');return route.fulfill({status:200,body:''})}
   else if (p==='/api/games') data=[{gameId:10,gameName:'未來遊戲',status:'COMING_SOON',price:599,description:'尚未發售',coverUrl:'',genre:'RPG'},{gameId:11,gameName:'已發售遊戲',status:'PUBLISHED',price:599,coverUrl:'',genre:'RPG'}]
   else if (p==='/api/cart/7') data={items:[],totalPrice:0}
   else if (p==='/api/member/rooms/latest') data=null
   else if (p==='/api/tickets/8') data={id:8,ticketNo:'TEST-8',subject:'測試案件',status:'OPEN',priority:'NORMAL',games:[],memberId:7,createdAt:'2026-10-27T10:00:00'}
   else if (p==='/api/rooms/12') data={id:12,status:'CLOSED',memberId:7,adminId:1,createdAt:'2026-10-27T10:00:00',ticketId:null}
   else if (p.includes('/messages')) data={items:[],hasMore:false}
   else if((p.endsWith('/links') || p.endsWith('/related-data'))) data={orders:[],coupons:[]}
   else if(p.endsWith('/media')) data=[{mediaType:'VIDEO',mediaUrl:video}]
   else if(p.endsWith('/summary')) data={reviewCount:0,positiveRate:null}
   return route.fulfill({json:data})
 })
 const page=await ctx.newPage(), errors=[]
 page.on('pageerror',e=>errors.push(e.stack))
 const go=path=>page.goto('http://127.0.0.1:5173/#'+path,{waitUntil:'networkidle'})
 await go('/publisher/games?mode=media&game=4')
 await page.getByLabel('新增遊戲圖片與影片').waitFor()
 check(page.url().includes('mode=edit'),'素材管理導向修改遊戲')
 check((await page.getByLabel('新增遊戲圖片與影片').getAttribute('accept')).includes('.mp4'),'支援影片選檔')
 await page.getByLabel('新增遊戲圖片與影片').setInputFiles({name:'trailer.mp4',mimeType:'video/mp4',buffer:Buffer.from([0,0,0,24,102,116,121,112,105,115,111,109])})
 await page.locator('video').first().waitFor()
 await page.getByRole('button',{name:'儲存草稿',exact:true}).click()
 await page.waitForFunction(()=>document.querySelector('.publisher-editor-actions button')?.textContent==='儲存草稿')
 check(savedDocument?.media.media[0].type==='VIDEO' && savedDocument.media.media[0].url===video,'影片連同草稿儲存')
 await go('/publisher/games?mode=publish&game=4')
 await page.getByRole('button',{name:'遊戲封面與宣傳素材',exact:true}).click()
 await page.getByLabel('新增遊戲圖片與影片').waitFor()
 check(page.url().includes('mode=edit'),'送審封面素材項目直達編輯')
 await go('/publisher/games?mode=builds&game=4')
 await page.getByRole('radio',{name:'上傳 ZIP',exact:true}).check()
 await page.getByLabel('選擇 ZIP 檔案（最多 100 MB）').setInputFiles({name:'demo.zip',mimeType:'application/zip',buffer:Buffer.from('PK\u0003\u0004test')})
 await page.waitForFunction(()=>document.querySelector('input[readonly]')?.value.includes('/api/publisher-assets/builds/'))
 check(await page.getByLabel('檔案大小（Bytes，可留空）').inputValue()==='128','ZIP 自動填入檔案大小')
 await page.getByRole('button',{name:'儲存草稿',exact:true}).click()
 await page.waitForTimeout(100)
 check(savedBuilds?.builds[0].fileUrl===localZip && !('source' in savedBuilds.builds[0]),'ZIP 版本儲存且不送 UI 專用欄位')
 check(uploads.some(v=>v.includes('VIDEO')) && uploads.some(v=>v.includes('BUILD')),'影片與 ZIP 使用正確上傳類型')
 await page.getByRole('radio',{name:'使用網址',exact:true}).check()
 await page.getByLabel('版本檔案網址',{exact:true}).fill('https://example.com/new.zip')
 await page.getByRole('button',{name:'儲存草稿',exact:true}).click()
 await page.waitForTimeout(100)
 check(savedBuilds.builds[0].fileUrl==='https://example.com/new.zip','網址來源也能獨立儲存')
 await page.getByRole('button',{name:'通知中心',exact:true}).click()
 await page.getByRole('button',{name:'全部已讀',exact:true}).click()
 await page.waitForTimeout(100)
 check(readAll===1 && await page.locator('.global-bell-error').count()===0,'成功空回應不再造成 JSON 解析錯誤')
 await page.getByRole('button',{name:/上架申請退回/}).click()
 await page.waitForURL(/mode=history&game=4/)
 check(page.url().includes('game=4'),'遊戲通知跳到對應遊戲')
 await page.getByRole('button',{name:'通知中心',exact:true}).click()
 await page.getByRole('button',{name:/客服已回覆聊天室/}).click()
 await page.waitForURL(/member\/support\?room=12/)
 check(page.url().includes('room=12'),'聊天室通知帶入正確 ID')
 await page.getByRole('button',{name:'通知中心',exact:true}).click()
 await page.getByRole('button',{name:/客服已回覆案件/}).click()
 await page.waitForURL(/member\/support\/8/)
 check(page.url().endsWith('/member/support/8'),'案件通知跳到對應案件')
 await go('/games/10')
 await page.locator('.coming-soon-label').waitFor()
 check(await page.getByRole('button',{name:'加入購物車',exact:true}).count()===0,'即將發售沒有購買按鈕')
 check(await page.getByRole('button',{name:/送禮/}).count()===0,'即將發售沒有送禮操作')
 check(await page.locator('span.coming-soon-label').count()===1,'即將發售是純文字 span')
 check(await page.locator('#media video').count()===1,'商城商品頁可播放宣傳影片')
 await go('/games/11')
 check(await page.getByRole('button',{name:'加入購物車',exact:true}).count()===1,'已發售保留購物車按鈕')
 check(errors.length===0,'頁面無 JS 錯誤：'+errors.join(';'))
 console.log(`PASS ${checks} publisher upload, notifications, media and purchase UI checks`)
 } finally {await browser.close()}
}
run().catch(e=>{console.error(e);process.exit(1)})
