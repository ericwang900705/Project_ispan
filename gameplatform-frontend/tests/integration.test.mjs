import test from 'node:test'
import assert from 'node:assert/strict'
import { configureAkiModule, api, apiImage, connectedFor, createAkiFetchAdapter, NotConnectedError } from '../src/api.js'
const user = {id:3,role:'PUBLISHER',username:'host-user'}
test('unconnected modules make no requests; host wiring normalizes paths and preserves payloads',async () => {
  let calls=[]
  configureAkiModule()
  await assert.rejects(api('/publisher/games'),NotConnectedError)
  configureAkiModule({user,request:async (...args)=>{calls.push(args);return {items:[]}},connected:{publisher:true}})
  assert(connectedFor('publisher'))
  await api('/aki/publisher/games',{method:'POST',body:{name:'host-game'}})
  assert.equal(calls[0][0],'/publisher/games');assert.deepEqual(calls[0][1].body,{name:'host-game'})
  await assert.rejects(api('/review/comments'),NotConnectedError);assert.equal(calls.length,1)
  configureAkiModule();await assert.rejects(api('/publisher/games'),NotConnectedError)
})
test('account switch rejects late data and logout disconnects external subscriptions',async () => {
  let resolve, stopped=0
  configureAkiModule({user:{...user,role:'MEMBER'},request:()=>new Promise(r=>{resolve=r}),connected:{support:true},subscribe:()=>()=>{stopped++}})
  const pending=api('/tickets')
  configureAkiModule()
  resolve({private:'old account'})
  await assert.rejects(pending,/身分已變更/);assert.equal(stopped,1)
})
test('private images require an image Blob from the host transport',async () => {
  configureAkiModule({user,request:async()=>new Blob(['x'],{type:'text/html'}),connected:{support:true}})
  await assert.rejects(apiImage('/tickets/1/attachments/1'),/無法讀取/)
  configureAkiModule()
})
test('fetch adapter supports host headers, CSRF, cookies and binary responses',async () => {
  const prior=global.fetch;let captured
  global.fetch=async(url,options)=>{captured={url,options};return new Response(JSON.stringify({ok:true}),{headers:{'Content-Type':'application/json'}})}
  try {
    const request=createAkiFetchAdapter({getHeaders:()=>({'X-CSRF-TOKEN':'host-csrf'}),credentials:'include'})
    assert.deepEqual(await request('/publisher/games',{method:'POST',body:{name:'game'}}),{ok:true})
    assert.equal(captured.url,'/api/publisher/games');assert.equal(captured.options.headers.get('X-CSRF-TOKEN'),'host-csrf');assert.equal(captured.options.credentials,'include');assert.equal(captured.options.body,'{"name":"game"}')
  } finally {global.fetch=prior}
})

test('API routing uses support/review/publisher groups without a personal prefix',async () => {
  const paths=[]
  configureAkiModule({user,connected:{support:true,gameReview:true,publisher:true,adminGames:true},request:async path=>{paths.push(path);return {}}})
  for (const path of ['/aki/admin/queue','/aki/review/games','/management/games','/aki/publisher/overview','/support/tickets']) await api(path)
  assert.deepEqual(paths,['/support/admin/queue','/review/games','/review/admin-games','/publisher/overview','/support/tickets'])
  configureAkiModule()
})
