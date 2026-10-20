<script setup>
import DatePicker from '../components/DatePicker.vue'
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {useRoute} from 'vue-router'
import {api} from '../api'
import {backofficeModules,managementLabels} from '../backoffice-modules'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import '../styles/admin-management.css'
const route=useRoute(), area=computed(()=>route.meta.workspaceModule), config=computed(()=>backofficeModules[area.value])
const rows=ref([]),total=ref(0),page=ref(0),query=ref(''),status=ref(''),loading=ref(false),busy=ref(false),error=ref(''),message=ref('')
const selected=ref(null),detail=ref(null),editing=ref(false),reason=ref(''),decision=ref('HIDDEN')
const form=ref({}),editable=computed(()=>['tags','coupons'].includes(area.value))
let run=0,alive=true
const text=(value)=>value==null?'—':managementLabels[value]||String(value)
const date=value=>value?new Date(value).toLocaleString('zh-TW',{hour12:false}):'—'
const cell=(row,key)=>key.endsWith('_at')?date(row[key]):text(row[key])
async function load(reset=false){if(reset)page.value=0;const id=++run;loading.value=true;error.value='';selected.value=null;detail.value=null;editing.value=false
 try{const data=await api('/backoffice/'+area.value+'?'+new URLSearchParams({q:query.value,status:status.value,page:String(page.value)}));if(alive&&id===run){rows.value=data.items;total.value=data.total}}
 catch(e){if(alive&&id===run){error.value=e.message;rows.value=[];total.value=0}}
 finally{if(alive&&id===run)loading.value=false}}
watch(()=>route.fullPath,()=>{query.value='';status.value='';page.value=0;message.value='';load()},{immediate:true})
onBeforeUnmount(()=>{alive=false;run++})
function edit(row=null){selected.value=row;editing.value=true;detail.value=null;error.value='';message.value=''
 form.value=area.value==='tags'?{name:row?.tag_name||''}:{name:row?.coupon_name||'',type:row?.discount_type||'RATE',value:Number(row?.discount_value??0.8),minimum:Number(row?.min_spend??0),totalLimit:row?.total_limit??'',memberLimit:row?.per_member_limit??1,status:row?.status||'ACTIVE',start:row?.start_at?.slice(0,16)||'',end:row?.expires_at?.slice(0,16)||''}}
async function open(row){if(editable.value){edit(row);return}selected.value=row;detail.value=null;editing.value=false;reason.value='';decision.value=row.status==='HIDDEN'?'NORMAL':'HIDDEN'
 if(['orders','refunds'].includes(area.value)){busy.value=true;const id=run;try{const data=await api('/backoffice/'+area.value+'/'+row.order_id);if(alive&&id===run)detail.value=data}catch(e){if(alive&&id===run)error.value=e.message}finally{busy.value=false}}}
async function mutate(path,method,body){if(busy.value)return;busy.value=true;error.value='';message.value='';const id=run
 try{await api(path,{method,body});if(alive&&id===run){message.value='已儲存';await load()}}
 catch(e){if(alive&&id===run)error.value=e.message}
 finally{busy.value=false}}
function save(){let body={...form.value};if(area.value==='coupons')body={...body,value:Number(body.value),minimum:Number(body.minimum),memberLimit:Number(body.memberLimit),totalLimit:body.totalLimit===''||body.totalLimit==null?null:Number(body.totalLimit)}
 mutate('/backoffice/'+area.value+(selected.value?'/'+selected.value[config.value.id]:''),selected.value?'PUT':'POST',body)}
function remove(){if(confirm('確定刪除這個未使用的標籤？'))mutate('/backoffice/tags/'+selected.value.tag_id,'DELETE')}
function review(){if(!reason.value.trim()){error.value='請填寫處理原因';return}mutate('/backoffice/reviews/'+selected.value.review_id,'PATCH',{status:decision.value,reason:reason.value})}
function report(remove){if(!reason.value.trim()){error.value='請填寫處理原因';return}mutate('/backoffice/review-reports/'+selected.value.report_id+'/decision','POST',{remove,reason:reason.value})}
function clear(){query.value='';status.value='';load(true)}
const orderLabels={order_no:'訂單編號',username:'會員',order_status:'狀態',subtotal_amount:'小計',discount_amount:'折扣',total_amount:'總金額',points_earned:'獲得點數',created_at:'下單時間',paid_at:'付款時間',payment_type:'付款方式',ecpay_trade_no:'金流交易編號'}
</script>
<template>
 <section class="page admin-management-page operations-page">
  <AdminPageHeader :title="config.title" eyebrow="ADMIN WORKSPACE" :icon="config.icon" :description="config.note" :count="total" count-label="符合條件的紀錄" />
  <p v-if="error" class="error" role="alert">{{error}}</p><p v-if="message" class="management-notice" role="status">{{message}}</p>
  <form class="management-search" @submit.prevent="load(true)"><label>搜尋<input v-model="query" maxlength="100" :disabled="busy" placeholder="輸入名稱、帳號或編號"></label><label v-if="config.states">狀態<select v-model="status" :disabled="busy"><option value="">全部</option><option v-for="item in config.states" :value="item" :key="item">{{text(item)}}</option></select></label><div class="management-actions"><button class="primary" :disabled="loading||busy">搜尋</button><button type="button" :disabled="loading||busy" @click="clear">清除篩選</button><button v-if="editable" type="button" :disabled="busy" @click="edit()">新增</button></div></form>
  <p v-if="loading" role="status">載入中…</p>
  <div class="management-table-wrap" :aria-busy="loading"><table><thead><tr><th v-for="col in config.columns" :key="col.key">{{col.label}}</th><th v-if="!['login-logs','operations'].includes(area)">操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row[config.id]"><td v-for="col in config.columns" :key="col.key" class="operation-cell">{{cell(row,col.key)}}</td><td v-if="!['login-logs','operations'].includes(area)"><button class="action-info" :disabled="busy||loading" @click="open(row)">{{editable?'編輯':'查看'}}</button></td></tr><tr v-if="!loading&&!rows.length"><td :colspan="config.columns.length+1" class="management-empty">沒有符合條件的資料。</td></tr></tbody></table></div>
  <nav class="management-pagination" aria-label="列表分頁"><button :disabled="!page||busy||loading" @click="page--;load()">上一頁</button><span>{{page+1}} / {{Math.max(1,Math.ceil(total/20))}}</span><button :disabled="(page+1)*20>=total||busy||loading" @click="page++;load()">下一頁</button></nav>
  <form v-if="editing" class="management-panel" @submit.prevent="save"><h2>{{selected?'編輯':'新增'}}{{area==='tags'?'標籤':'優惠券'}}</h2><label>名稱<input v-model.trim="form.name" :maxlength="area==='tags'?50:100" required :disabled="busy"></label>
   <template v-if="area==='coupons'"><div class="coupon-grid"><label>折扣類型<select v-model="form.type" :disabled="busy"><option value="RATE">折扣率</option><option value="AMOUNT">折抵金額</option></select></label><label>折扣值<input v-model="form.value" type="number" min="0.01" :max="form.type==='RATE'?0.99:99999999.99" step="0.01" required :disabled="busy"><small>折扣率 0.80 表示八折。</small></label><label>最低消費<input v-model="form.minimum" type="number" min="0" max="99999999.99" step="0.01" required :disabled="busy"></label><label>總使用額度<input v-model="form.totalLimit" type="number" min="1" step="1" placeholder="留空為不限制" :disabled="busy"></label><label>每位會員額度<input v-model="form.memberLimit" type="number" min="1" step="1" required :disabled="busy"></label><label>狀態<select v-model="form.status" :disabled="busy"><option v-for="value in config.states" :key="value" :value="value">{{text(value)}}</option></select></label><label>開始時間<DatePicker v-model="form.start" type="datetime-local" label="開始時間" required :disabled="busy" /></label><label>到期時間<DatePicker v-model="form.end" type="datetime-local" label="到期時間" required :disabled="busy" /></label></div><p v-if="selected">已使用 {{selected.used_count}} 次；總額度不得低於此數。</p></template>
   <div class="management-actions"><button class="primary" :disabled="busy">儲存</button><button type="button" :disabled="busy" @click="editing=false;selected=null">取消</button><button class="action-danger" v-if="area==='tags'&&selected" type="button" :disabled="busy||selected.game_count>0" @click="remove">刪除標籤</button></div>
  </form>
  <section v-if="detail" class="management-panel"><h2>訂單明細</h2><dl class="coupon-grid"><div v-for="(label,key) in orderLabels" :key="key"><dt>{{label}}</dt><dd>{{cell(detail.order,key)}}</dd></div></dl><div class="management-table-wrap"><table><thead><tr><th>遊戲</th><th>數量</th><th>原價</th><th>折扣</th><th>實付</th><th>贈送對象</th></tr></thead><tbody><tr v-for="item in detail.items" :key="item.order_item_id"><td>{{item.game_name}}</td><td>{{item.quantity}}</td><td>{{item.unit_price}}</td><td>{{item.discount_amount}}</td><td>{{item.final_amount}}</td><td>{{item.is_gift?item.recipient:'自用'}}</td></tr></tbody></table></div></section>
  <form v-if="selected&&area==='reviews'" class="management-panel" @submit.prevent="review"><h2>評論處理</h2><p>{{selected.game_name}} · {{selected.username}}</p><p class="review-text">{{selected.content}}</p><template v-if="selected.status!=='DELETED'"><label>處理方式<select v-model="decision" :disabled="busy"><option value="HIDDEN">隱藏評論</option><option value="NORMAL">恢復正常</option></select></label><label>處理原因<textarea v-model="reason" rows="3" maxlength="500" required :disabled="busy"></textarea></label><button class="primary" :class="decision==='HIDDEN'?'action-danger':'action-success'" :disabled="busy">確認儲存</button></template><p v-else>此評論已由會員刪除，不能恢復。</p></form>
  <section v-if="selected&&area==='review-reports'" class="management-panel"><h2>檢舉 #{{selected.report_id}}</h2><h3>檢舉快照</h3><p class="review-text">{{selected.snapshot_text}}</p><h3>目前內容</h3><p class="review-text">{{selected.current_content||'評論不存在或無內容'}}</p><p>檢舉原因：{{selected.reason}}</p><template v-if="selected.status==='PENDING'"><label>處理原因<textarea v-model="reason" rows="3" maxlength="500" :disabled="busy"></textarea></label><div class="management-actions"><button class="action-danger" :disabled="busy" @click="report(true)">隱藏評論並結案</button><button class="action-success" :disabled="busy" @click="report(false)">檢舉不成立</button></div></template><p v-else>處理說明：{{selected.review_comment}}</p></section>
 </section>
</template>
<style scoped>
.operation-cell{max-width:300px;white-space:pre-wrap;overflow-wrap:anywhere}.coupon-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.coupon-grid dt{color:#6e8495;font-size:13px}.coupon-grid dd{margin:6px 0;overflow-wrap:anywhere}.review-text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9}.management-panel>label{display:grid;gap:8px;margin:15px 0}.management-panel button{margin-top:12px}.management-panel textarea{width:100%;box-sizing:border-box}@media(max-width:700px){.coupon-grid{grid-template-columns:1fr}}
</style>
