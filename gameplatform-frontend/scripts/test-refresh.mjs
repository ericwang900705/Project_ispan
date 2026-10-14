import assert from 'node:assert/strict'
import { test } from 'node:test'
import { guardPage } from '../src/permissions.js'

test('reload restores verified roles without extending expiry; logout, corruption and rejection clear persisted login', async () => {
  globalThis.window = new EventTarget()
  const saved = new Map()
  globalThis.sessionStorage = {getItem:k=>saved.get(k),setItem:(k,v)=>saved.set(k,v),removeItem:k=>saved.delete(k)}
  let role='SUPPORT', rejected=false, checks=0, ttl=0
  globalThis.fetch=async(url,opts={})=>{
    if(url.endsWith('/events'))return new Promise((_,reject)=>opts.signal.addEventListener('abort',()=>reject(new DOMException('aborted','AbortError')),{once:true}))
    if(url.endsWith('/login'))return Response.json({accessToken:'test-token',expiresIn:ttl})
    if(url.endsWith('/logout'))return new Response(null,{status:204})
    if(url.endsWith('/me')){checks++; assert.equal(opts.headers.Authorization,'Bearer test-token');return rejected?Response.json({message:'revoked'},{status:401}):Response.json({id:2,role,username:'user'})}
    return Response.json({ok:true})
  }
  const auth=await import('../src/api.js')
  try {
    for(role of ['SUPPORT','ADMIN','REVIEWER','MEMBER','PUBLISHER']){
      await auth.login('user','pw',['MEMBER','PUBLISHER'].includes(role)?'member':'staff')
      const state=saved.get('support-auth-session-v1'), before=checks
      await auth.restore()
      assert.equal(checks,before+1,'restored identity must be checked by server')
      assert.equal(auth.session.user.role,role)
      assert.equal(saved.get('support-auth-session-v1'),state,'reload must not extend the expiry time')
      assert.equal(auth.session.ready,true)
      if(role==='SUPPORT')assert.equal(guardPage({path:'/admin/chats',meta:{support:true}},auth.session.user,auth.session.loginArea),true)
      await auth.logout();assert.equal(saved.has('support-auth-session-v1'),false)
      await auth.restore();assert.equal(auth.session.user,null)
    }
    ttl=900;role='SUPPORT';await auth.login('user','pw','staff');await auth.restore();await auth.logout();ttl=0
    saved.set('support-auth-session-v1','invalid-json');await auth.restore();assert.equal(saved.has('support-auth-session-v1'),false)
    saved.set('support-auth-session-v1',JSON.stringify({accessToken:'test-token',expiresAt:Date.now()-1}));const before=checks;await auth.restore();assert.equal(checks,before);assert.equal(auth.session.user,null)
    role='SUPPORT';await auth.login('user','pw','staff');rejected=true;await auth.restore();assert.equal(auth.session.user,null);assert.equal(saved.has('support-auth-session-v1'),false)
  }finally{saved.clear();await auth.restore()}
})
