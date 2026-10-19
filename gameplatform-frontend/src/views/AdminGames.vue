<script setup>
import { ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
const route=useRoute()
const items=ref([]),total=ref(0),page=ref(0),keyword=ref(''),status=ref(''),selected=ref(null),history=ref([]),error=ref(''),busy=ref(false),loading=ref(false),comment=ref(''),decision=ref('')
const labels={DRAFT:'草稿',PENDING_REVIEW:'待審核',COMING_SOON:'即將發售',PUBLISHED:'已上架',PENDING_OFF_SHELF:'預定下架',OFF_SHELF:'已下架'}
let run=0,detailRun=0
async function load(reset=false){
 if(reset)page.value=0
 const id=++run;loading.value=true;error.value=''
 try{
  if(route.query.mode==='history'){
   const result=await api('/admin/game-reviews/history?page='+page.value+'&size=10')
   if(id===run){history.value=result.content;total.value=result.totalElements}
  }else{
   const query=new URLSearchParams({page:String(page.value),size:'10',keyword:keyword.value,status:status.value})
   const result=await api('/admin/games/search?'+query)
   if(id===run){items.value=result.content;total.value=result.totalElements}
  }
 }catch(e){if(id===run)error.value=e.message}finally{if(id===run)loading.value=false}
}
async function choose(id){
 const current=++detailRun;error.value='';selected.value=null;decision.value=''
 try{const result=await api('/admin/games/'+id);if(current===detailRun)selected.value=result}catch(e){if(current===detailRun)error.value=e.message}
}
async function submit(){
 if(busy.value || !selected.value || !decision.value)return
 busy.value=true;error.value=''
 try{
  const id=selected.value.game.gameId
  await api(`/admin/game-reviews/${id}/${decision.value}`,{method:'PUT',body:{comment:comment.value}})
  decision.value='';comment.value='';await Promise.all([load(),choose(id)])
 }catch(e){error.value=e.message}finally{busy.value=false}
}
watch(()=>route.query.mode,()=>{status.value=route.query.mode==='pending'?'PENDING_REVIEW':'';selected.value=null;load(true)},{immediate:true})
</script>
<template>
 <section class="page admin-game-page">
  <h1>{{route.query.mode==='history'?'遊戲審核紀錄':route.query.mode==='pending'?'遊戲上架審核':'遊戲搜尋'}}</h1>
  <p class="muted">查看遊戲資料、版本與素材，處理上架申請及下架。</p>
  <p v-if="error" class="error" role="alert">{{error}}</p>
  <form v-if="route.query.mode!=='history'" class="game-search" @submit.prevent="load(true)">
   <label>遊戲名稱<input v-model="keyword" placeholder="輸入名稱" maxlength="100"></label>
   <label>狀態<select v-model="status"><option value="">全部</option><option v-for="(name,key) in labels" :key="key" :value="key">{{name}}</option></select></label>
   <button class="primary" :disabled="loading">搜尋</button>
  </form>
  <p v-if="loading" role="status">載入中…</p>
  <div v-if="route.query.mode==='history'" class="game-table-wrap"><table><thead><tr><th>遊戲編號</th><th>審核人員編號</th><th>類型／結果</th><th>原因</th><th>時間</th></tr></thead><tbody><tr v-for="row in history" :key="row.gameAuditId"><td>{{row.gameId}}</td><td>{{row.adminId}}</td><td>{{row.reviewType==='PUBLISH'?'上架':'下架'}}／{{row.decision==='APPROVED'?'通過':'退回'}}</td><td>{{row.comment||'—'}}</td><td>{{row.reviewedAt}}</td></tr></tbody></table><p v-if="!loading&&!history.length">尚無審核紀錄。</p></div>
  <div v-else class="game-columns">
   <section class="game-list"><button v-for="game in items" :key="game.gameId" class="game-row" :class="{active:selected?.game.gameId===game.gameId}" :disabled="busy" @click="choose(game.gameId)"><strong>{{game.gameName}}</strong><small>{{labels[game.status]}} · NT$ {{game.price}}</small></button><p v-if="!loading&&!items.length">沒有符合條件的遊戲。</p></section>
   <article v-if="selected" class="game-detail"><h2>{{selected.game.gameName}}</h2><p>{{labels[selected.game.status]}} · 發行商編號 {{selected.game.memberId}}</p><img v-if="selected.game.coverUrl" :src="selected.game.coverUrl" alt="遊戲封面"><p class="description">{{selected.game.description}}</p><p>發售時間：{{selected.game.releaseDate||'未設定'}}</p><p>標籤：{{selected.tags.map(t=>t.tagName).join('、')||'未設定'}}</p>
    <h3>版本資訊</h3><ul><li v-for="build in selected.builds" :key="build.build_id">{{build.version}} · {{build.status}} <a :href="build.file_url" target="_blank" rel="noopener noreferrer">檔案連結</a></li></ul>
    <h3>宣傳素材</h3><div class="game-media"><template v-for="media in selected.media" :key="media.media_id"><img v-if="media.media_type==='IMAGE'" :src="media.media_url" alt="遊戲宣傳圖"><a v-else :href="media.media_url" target="_blank" rel="noopener noreferrer">查看影片</a></template></div>
    <div class="decision-actions"><template v-if="selected.game.status==='PENDING_REVIEW'"><button class="primary" :disabled="busy" @click="decision='approve';comment=''">核准上架</button><button :disabled="busy" @click="decision='reject';comment=''">退回申請</button></template><button v-if="['PUBLISHED','COMING_SOON','PENDING_OFF_SHELF'].includes(selected.game.status)" :disabled="busy" @click="decision='force-off-shelf';comment=''">強制下架</button></div>
    <form v-if="decision" class="decision-form" @submit.prevent="submit"><label>{{decision==='approve'?'審核說明（選填）':'請填寫原因'}}<textarea v-model="comment" :required="decision!=='approve'" maxlength="500" rows="3"></textarea></label><p>確認{{decision==='approve'?'核准上架':decision==='reject'?'退回申請':'立即強制下架'}}「{{selected.game.gameName}}」？</p><button class="primary" :disabled="busy">{{busy?'處理中…':'確認送出'}}</button><button type="button" :disabled="busy" @click="decision=''">取消</button></form>
   </article><article v-else class="game-detail muted">選擇遊戲以檢視資料。</article>
  </div>
  <div class="pagination"><button :disabled="!page||loading||busy" @click="page--;load()">上一頁</button><span>{{page+1}} / {{Math.max(1,Math.ceil(total/10))}}</span><button :disabled="(page+1)*10>=total||loading||busy" @click="page++;load()">下一頁</button></div>
 </section>
</template>
<style scoped>
.game-search{display:flex;gap:14px;align-items:end;flex-wrap:wrap;margin:24px 0}.game-search label{margin:0;flex:1;min-width:150px}.game-search button{height:44px}.game-columns{display:grid;grid-template-columns:minmax(220px,1fr) minmax(0,2fr);gap:24px}.game-row{display:flex;flex-direction:column;text-align:left;width:100%;border:1px solid #d5e4ec;padding:18px;border-radius:10px;margin-bottom:10px;background:white;gap:8px}.game-row.active{background:#e9f6fa;border-color:#2583a0}.game-detail{background:white;border:1px solid #d5e4ec;border-radius:14px;padding:24px;min-width:0}.game-detail>img{width:100%;max-height:240px;object-fit:contain}.game-media{display:flex;gap:10px;flex-wrap:wrap}.game-media img{max-width:160px;max-height:110px;object-fit:contain}.description{white-space:pre-wrap}.decision-actions,.pagination{display:flex;gap:12px;margin-top:22px;align-items:center}.decision-form{padding:18px;margin-top:18px;background:#f0f7fa;border-radius:10px}.decision-form button{margin-right:10px}.game-table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:14px;border-bottom:1px solid #dce8ee}@media(max-width:800px){.game-columns{grid-template-columns:1fr}}
</style>
