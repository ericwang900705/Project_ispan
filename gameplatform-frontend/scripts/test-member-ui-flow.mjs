import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import fs from 'node:fs'
const code=fs.readFileSync(new URL('../src/views/MemberAccount.vue',import.meta.url),'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'')
const tick=()=>new Promise(setImmediate)
function setup(path,query={},stored={}){
 const route={path,fullPath:path,query},storage=new Map(Object.entries(stored)),moves=[],timers=new Map();let serial=0
 const ctx={ref:v=>({value:v}),computed:f=>({get value(){return f()}}),watch:(_f,cb,opt)=>{if(opt?.immediate)cb()},onUnmounted:()=>{},useRoute:()=>route,useRouter:()=>({push:async p=>moves.push(p),replace:async p=>moves.push(p)}),session:{user:{id:1}},sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},setTimeout:fn=>{timers.set(++serial,fn);return serial},clearTimeout:id=>timers.delete(id),api:async()=>({verified:false}),verified:false}
 vm.createContext(ctx);vm.runInContext(code+'\nglobalThis.state={profile,form,birthdayLocked,updateProfile,notice,verificationComplete,monitorVerification};',ctx)
 return {ctx,state:ctx.state,moves,storage,timers,route}
}
test('original waiting page moves to login only after server confirms verification',async()=>{
 const t=setup('/member/verification-sent',{}, {'hero-verification-watch':'x'.repeat(43)})
 await tick();assert.equal(t.moves.length,0);assert.equal(t.timers.size,1)
 t.ctx.api=async()=>({verified:true});await [...t.timers.values()][0]();assert.equal(t.moves[0],'/member/login');assert.equal(t.storage.has('hero-verification-watch'),false)
})
test('separate email tab shows completion without opening a second login page',async()=>{
 const t=setup('/member/verify',{token:'valid'});await tick();assert.equal(t.state.verificationComplete.value,true);assert.equal(t.moves.length,0);assert.match(t.state.notice.value,/原本.*註冊等待頁/)
})
test('same-tab verification still redirects to login',async()=>{
 const t=setup('/member/verify',{token:'valid'},{'hero-verification-watch':'x'.repeat(43)});await tick();assert.equal(t.moves[0],'/member/login')
})
test('existing birthday is sent unchanged as omitted update while avatar and address remain editable',async()=>{
 const t=setup('/member/forgot-password');t.state.profile.value={birthDate:'2000-01-02'};t.state.form.value.birthDate='1990-01-01';t.state.form.value.address='new address'
 let submitted;t.ctx.api=async(_path,options)=>{submitted=options.body;return {birthDate:'2000-01-02',address:'new address'}}
 await t.state.updateProfile();assert.equal(submitted.birthDate,null);assert.equal(submitted.address,'new address');assert.equal(t.state.birthdayLocked.value,true)
})
