import {api} from './api'
let sdkPromise,initialized=false,receiver=null
const nonce=Array.from(crypto.getRandomValues(new Uint8Array(32)),b=>b.toString(16).padStart(2,'0')).join('')
function sdk(){
 if(window.google?.accounts?.id)return Promise.resolve(window.google.accounts.id)
 if(!sdkPromise)sdkPromise=new Promise((resolve,reject)=>{
  const script=document.createElement('script');script.src='https://accounts.google.com/gsi/client?hl=zh_TW';script.async=true
  const fail=()=>{script.remove();sdkPromise=null;reject(Error('Google 登入無法載入，請重新整理或使用帳號密碼登入'))}
  const timer=setTimeout(fail,15000)
  script.onload=()=>{clearTimeout(timer);if(window.google?.accounts?.id)resolve(window.google.accounts.id);else fail()}
  script.onerror=()=>{clearTimeout(timer);fail()};document.head.append(script)
 })
 return sdkPromise
}
export async function mountGoogleButton(element,receive,active,link=false){
 const config=await api('/public/google-login');if(!active())return()=>{}
 if(!config.enabled||!config.clientId)throw Error('Google 登入暫未開放，請使用帳號密碼登入')
 const id=await sdk();if(!active())return()=>{}
 if(!initialized){id.initialize({client_id:config.clientId,nonce,auto_select:false,ux_mode:'popup',callback:r=>{if(r.credential)receiver?.(r.credential,nonce)}});initialized=true}
 const own=(token,n)=>{if(active())receive(token,n)};receiver=own
 id.renderButton(element,{type:'standard',theme:'outline',size:'large',text:link?'continue_with':'signin_with',shape:'rectangular',locale:'zh_TW',width:Math.min(360,Math.max(200,element.clientWidth))})
 return()=>{if(receiver===own)receiver=null}
}
