<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {mountGoogleButton} from '../google-identity'
const props=defineProps({busy:Boolean,link:Boolean}),emit=defineEmits(['credential'])
const target=ref(null),message=ref(''),loading=ref(true)
let alive=true,dispose=()=>{}
onMounted(async()=>{try{dispose=await mountGoogleButton(target.value,(idToken,nonce)=>{if(!props.busy)emit('credential',{idToken,nonce})},()=>alive,props.link)}catch(e){if(alive)message.value=e.message}finally{if(alive)loading.value=false}})
onBeforeUnmount(()=>{alive=false;dispose()})
</script>
<template><div class="google-signin" :aria-busy="loading||busy"><p v-if="loading" role="status">正在載入 Google 登入…</p><div ref="target" class="google-button" :class="{disabled:busy}" :inert="busy||undefined"></div><p v-if="message" role="status">{{message}}</p><p v-if="busy" role="status">正在驗證 Google 帳號…</p></div></template>
<style scoped>.google-signin{margin:18px 0;color:#61798c;font-size:13px;line-height:1.8}.google-button{max-width:100%;overflow:hidden}.google-button.disabled{pointer-events:none;opacity:.6}</style>
