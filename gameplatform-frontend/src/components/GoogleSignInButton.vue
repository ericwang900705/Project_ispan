<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { mountGoogleButton } from '../google-identity'
const props = defineProps({ busy: Boolean, link: Boolean }), emit = defineEmits(['credential'])
const target = ref(null), message = ref(''), loading = ref(true)
let alive = true, dispose = () => { }
onMounted(async () => { try { dispose = await mountGoogleButton(target.value, (idToken, nonce) => { if (!props.busy) emit('credential', { idToken, nonce }) }, () => alive, props.link) } catch (e) { if (alive) message.value = e.message } finally { if (alive) loading.value = false } })
onBeforeUnmount(() => { alive = false; dispose() })
</script>
<template>
  <div class="google-signin" :aria-busy="loading || busy">
    <div class="google-button-frame" :class="{ 'is-busy': busy, 'has-error': !!message }">
      <div v-if="loading" class="google-loading" role="status">
        <span class="google-spinner" aria-hidden="true"></span>正在載入 Google 登入…
      </div>
      <div ref="target" class="google-button" :class="{ disabled: busy }" :inert="busy || undefined"></div>
    </div>
    <p v-if="message" class="google-status google-status-error" role="alert">{{ message }}</p>
    <p v-if="busy" class="google-status" role="status"><span class="google-spinner" aria-hidden="true"></span>正在驗證
      Google 帳號…</p>
  </div>
</template>
<style scoped>
.google-signin {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  margin: 0 auto 18px;
  color: var(--google-status-color, #526b80);
  font-size: 13px;
  line-height: 1.7
}

.google-button-frame {
  box-sizing: border-box;
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 7px;
  border: 1px solid var(--google-frame-border, #d7e1eb);
  border-radius: 17px;
  background: var(--google-frame-bg, #f5f8fb);
  box-shadow: var(--google-frame-shadow, 0 4px 16px #18394e0a);
  transition: border-color .18s ease, box-shadow .18s ease
}

.google-button-frame:focus-within {
  border-color: var(--google-focus-color, #718ec0);
  box-shadow: 0 0 0 3px #7e9ac126
}

.google-button {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 400px;
  min-width: 0;
  min-height: 40px
}

.google-button.disabled {
  pointer-events: none;
  opacity: .65
}

.google-button-frame.has-error .google-button:empty {
  display: none
}

.google-button-frame.has-error {
  display: none
}

.google-loading {
  position: absolute;
  inset: 7px;
  display: flex;
  gap: 9px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  pointer-events: none
}

.google-spinner {
  display: inline-block;
  box-sizing: border-box;
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: google-spin .8s linear infinite;
  opacity: .8
}

.google-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 10px 0 0;
  font-size: 12px;
  text-align: center;
  overflow-wrap: anywhere
}

.google-status-error {
  display: block;
  padding: 11px 13px;
  border: 1px solid #b56c784d;
  border-radius: 10px;
  color: inherit;
  background: #b64c6412
}

@keyframes google-spin {
  to {
    transform: rotate(360deg)
  }
}

@media(prefers-reduced-motion:reduce) {
  .google-spinner {
    animation: none
  }

  .google-button-frame {
    transition: none
  }
}
</style>
