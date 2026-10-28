<script setup>
// 後台登入／註冊共用視覺外框；只提供版面與背景，不處理帳號、權限或 API。
import UiIcon from './UiIcon.vue'
defineProps({ register: Boolean })
</script>

<template>
  <div class="staff-auth-shell">
    <div class="auth-backdrop" aria-hidden="true"><span /><span /><span /></div>
    <div class="staff-auth-layout">
      <aside class="staff-auth-story" aria-label="HERO 工作後台介紹">
        <div class="auth-brand">
          <span class="auth-brand-mark"><UiIcon name="game" /></span>
          <div><strong>HERO <span>赫洛</span></strong><small>STAFF WORKSPACE</small></div>
        </div>
        <div class="auth-story-copy">
          <span class="auth-kicker"><span /> 為每一份熱愛，做好每一次服務</span>
          <h2>{{ register ? '加入團隊，' : '讓每一次協作，' }}<br><em>{{ register ? '一起打造更好的體驗。' : '成就更好的遊戲體驗。' }}</em></h2>
          <p>從遊戲上架、平台營運到玩家服務，<br class="auth-desktop-break">在同一個工作空間，讓每一件事有序進行。</p>
        </div>
        <div class="auth-visual" aria-hidden="true">
          <div class="auth-orbit orbit-one" /><div class="auth-orbit orbit-two" />
          <div class="auth-console">
            <div class="auth-console-bar"><i /><i /><i /><span>HERO WORKSPACE</span></div>
            <div class="auth-console-body">
              <div class="auth-console-menu"><b /><b /><b /><b /></div>
              <div class="auth-console-content"><div class="auth-console-cards"><b /><b /><b /></div><div class="auth-chart"><i /><i /><i /><i /><i /><i /><i /></div><div class="auth-console-lines"><b /><b /></div></div>
            </div>
          </div>
          <div class="auth-shield"><UiIcon name="shield" /></div>
        </div>
        <div class="auth-capabilities">
          <span><UiIcon name="game" />遊戲與發行管理</span>
          <span><UiIcon name="users" />團隊協作</span>
          <span><UiIcon name="shield" />角色權限</span>
        </div>
        <p class="auth-story-footer">HERO GAME PLATFORM <span>專注管理，連結玩家。</span></p>
      </aside>
      <div class="staff-auth-content"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.staff-auth-shell {
  position: relative;
  isolation: isolate;
  display: grid;
  align-items: center;
  min-height: 100dvh;
  padding: clamp(28px, 4vw, 64px);
  box-sizing: border-box;
  color: #edf6fc;
  background: #0b1d30;
}
.auth-backdrop { position: absolute; inset: 0; z-index: -1; overflow: hidden; pointer-events: none; background: radial-gradient(ellipse at 5% 5%, #174963b3, transparent 53%), radial-gradient(ellipse at 96% 85%, #17657066, transparent 53%), linear-gradient(125deg, #0d2339, #0c192b 60%, #0c2c3c); }
.auth-backdrop::before { content: ''; position: absolute; inset: 0; background-image: linear-gradient(#a1dce409 1px, transparent 1px), linear-gradient(90deg, #a1dce409 1px, transparent 1px); background-size: 64px 64px; mask-image: linear-gradient(120deg, #000, transparent 80%); }
.auth-backdrop span { position: absolute; width: 580px; height: 580px; border: 1px solid #7bc5d21c; border-radius: 50%; left: -260px; bottom: -310px; }
.auth-backdrop span:nth-child(2) { width: 750px; height: 750px; left: -345px; bottom: -395px; }
.auth-backdrop span:nth-child(3) { width: 920px; height: 920px; left: -430px; bottom: -480px; }
.staff-auth-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 450px); gap: clamp(44px, 6vw, 100px); align-items: center; width: min(100%, 1160px); margin: 0 auto; }
.staff-auth-story { min-width: 0; }
.auth-brand { display: flex; align-items: center; gap: 14px; }
.auth-brand-mark { width: 48px; height: 48px; display: grid; place-items: center; border: 1px solid #8bcfdf55; background: linear-gradient(135deg, #58c4ca30, #75a4ed10); border-radius: 14px; color: #9be8e9; }
.auth-brand-mark svg { width: 28px; height: 28px; }
.auth-brand strong { display: block; font-size: 26px; line-height: 1.3; letter-spacing: 2px; color: #f3f9ff; }
.auth-brand strong span { font-size: 17px; font-weight: 500; letter-spacing: 1px; margin-left: 8px; }
.auth-brand small { display: block; font-size: 9px; letter-spacing: 3px; color: #8bb9ca; margin-top: 5px; }
.auth-story-copy { margin-top: 58px; }
.auth-kicker { display: flex; align-items: center; gap: 10px; font-size: 12px; color: #9bc5d1; letter-spacing: 1px; }
.auth-kicker > span { width: 6px; height: 6px; border-radius: 50%; background: #71d9cb; box-shadow: 0 0 0 4px #71d9cb12; flex-shrink: 0; }
.auth-story-copy h2 { margin: 20px 0; font-size: clamp(28px, 2.8vw, 40px); line-height: 1.55; color: #f3f8ff; font-weight: 650; letter-spacing: -.8px; }
.auth-story-copy em { font-style: normal; color: #8bdedc; }
.auth-story-copy p { margin: 0; color: #a0b6c8; font-size: 14px; line-height: 1.95; }
.auth-visual { position: relative; width: min(100%, 400px); height: 220px; margin: 28px 0 16px; }
.auth-orbit { position: absolute; border-radius: 50%; border: 1px solid #78c9d51a; width: 230px; height: 230px; left: 82px; top: -10px; }
.orbit-two { width: 280px; height: 280px; left: 57px; top: -35px; border-style: dashed; }
.auth-console { position: absolute; left: 10px; right: 26px; top: 25px; border-radius: 12px; overflow: hidden; border: 1px solid #75aebb45; background: linear-gradient(140deg, #264256e8, #172e43f2); box-shadow: 0 22px 60px #0003; transform: perspective(900px) rotateY(-8deg) rotateX(5deg); }
.auth-console-bar { height: 30px; border-bottom: 1px solid #90b9cf20; display: flex; align-items: center; gap: 5px; padding: 0 12px; }
.auth-console-bar > i { width: 5px; height: 5px; border-radius: 50%; background: #7aabbc; }
.auth-console-bar > i:first-child { background: #70cabb; }
.auth-console-bar > span { font-size: 7px; color: #9bb8c9; letter-spacing: 2px; margin-left: auto; }
.auth-console-body { display: flex; height: 135px; }
.auth-console-menu { width: 48px; border-right: 1px solid #8bb7c820; padding: 17px 10px; }
.auth-console-menu b { display: block; height: 4px; margin: 0 0 16px; background: #81a9bb30; border-radius: 3px; }
.auth-console-menu b:first-child { background: #6fd5cc80; }
.auth-console-content { flex: 1; padding: 14px; }
.auth-console-cards { display: flex; gap: 8px; }
.auth-console-cards b { height: 25px; border: 1px solid #9ac0d021; background: #90b5ca12; border-radius: 5px; flex: 1; }
.auth-chart { display: flex; align-items: flex-end; justify-content: space-around; height: 48px; padding: 6px 8px 0; margin-top: 7px; border-bottom: 1px solid #a2cbd522; }
.auth-chart i { width: 14px; height: 40%; border-radius: 3px 3px 0 0; background: linear-gradient(#7dd3d0a0, #5fbdcc20); }
.auth-chart i:nth-child(2n) { height: 62%; }.auth-chart i:nth-child(3n) { height: 48%; }.auth-chart i:nth-child(5) { height: 83%; }.auth-chart i:last-child { height: 95%; }
.auth-console-lines { display: flex; gap: 8px; margin-top: 10px; }.auth-console-lines b { height: 4px; flex: 1; background: #9fc4d229; border-radius: 3px; }
.auth-shield { position: absolute; display: grid; place-items: center; width: 65px; height: 72px; right: 0; top: 120px; border: 1px solid #97e4df66; border-radius: 18px; color: #9eede1; background: linear-gradient(135deg, #346a77, #254552); box-shadow: 0 12px 30px #03121d50; }
.auth-shield svg { width: 34px; height: 34px; }
.auth-capabilities { display: flex; gap: 18px; flex-wrap: wrap; }
.auth-capabilities span { display: inline-flex; align-items: center; gap: 7px; color: #adcad5; font-size: 11px; }
.auth-capabilities svg { width: 15px; height: 15px; color: #75c4cc; }
.auth-story-footer { margin: 32px 0 0; padding-top: 22px; border-top: 1px solid #9ac9d21f; color: #6d99ad; font-size: 9px; letter-spacing: 1.6px; }
.auth-story-footer span { margin-left: 14px; letter-spacing: .5px; }
.staff-auth-content { min-width: 0; }
.staff-auth-content :deep(.staff-login), .staff-auth-content :deep(.staff-register-page) { width: 100%; margin: 0; padding: 0; }
.staff-auth-shell .staff-auth-content :deep(.staff-login .staff-login-card),
.staff-auth-shell .staff-auth-content :deep(.staff-register) { box-sizing: border-box; padding: 36px; border: 1px solid #e1ebf0; border-top: 4px solid #36a3ac; border-radius: 22px; background: #fcfefe; box-shadow: 0 24px 70px #020d224d, 0 1px 0 #ffffff80 inset; }
.staff-auth-content :deep(.eyebrow), .staff-auth-content :deep(.register-eyebrow) { color: #227c8c; letter-spacing: 1.5px; font-size: 11px; }
.staff-auth-content :deep(.staff-login-card h1) { margin: 12px 0 14px; font-size: 28px; line-height: 1.4; color: #1c354c; }
.staff-auth-content :deep(.staff-login-card > .muted) { color: #607b8e; font-size: 13px; line-height: 1.85; margin-bottom: 28px; }
.staff-auth-content :deep(input:not([type=checkbox]):not([type=radio])) { height: 46px; padding: 0 13px; background: #f5f9fb; border: 1px solid #cfdee7; border-radius: 9px; color: #203e53; box-sizing: border-box; }
.staff-auth-content :deep(input:focus-visible) { outline: 3px solid #16829725; outline-offset: 1px; border-color: #168297; }
.staff-auth-content :deep(label) { color: #36546a; font-size: 12px; }
.staff-auth-content :deep(button.primary) { min-height: 46px; border-radius: 10px; background: linear-gradient(110deg, #187e96, #159897); border: 1px solid #187f90; box-shadow: 0 5px 12px #16829722; font-size: 13px; font-weight: 600; }
.staff-auth-content :deep(button.primary:hover:not(:disabled)) { background: linear-gradient(110deg, #126b81, #117f80); }
.staff-auth-content :deep(button.primary:focus-visible) { outline: 3px solid #16829766; outline-offset: 3px; }
.staff-auth-content :deep(.staff-account-entry small), .staff-auth-content :deep(.staff-login-note), .staff-auth-content :deep(.account-divider), .staff-auth-content :deep(.register-footer) { color: #617e8f; }
.staff-auth-content :deep(.register-heading p), .staff-auth-content :deep(.register-tip p) { color: #617e8f; }
.staff-auth-content :deep(input::placeholder) { color: #6e8594; }
@media (min-width: 1500px) { .staff-auth-layout { gap: 120px; }.auth-story-copy { margin-top: 68px; } }
@media (max-width: 900px) {
  .staff-auth-layout { grid-template-columns: minmax(0, 1fr) minmax(340px, 410px); gap: 32px; }
  .staff-auth-shell { padding: 32px; }.auth-story-copy h2 { font-size: 27px; }.auth-story-copy { margin-top: 40px; }
  .auth-visual { height: 200px; }.auth-capabilities { gap: 12px; }.auth-story-footer span { display: block; margin: 8px 0 0; }
  .staff-auth-shell .staff-auth-content :deep(.staff-login .staff-login-card), .staff-auth-shell .staff-auth-content :deep(.staff-register) { padding: 28px; }
}
@media (max-width: 720px) {
  .staff-auth-shell { padding: 28px 20px 36px; }
  .staff-auth-layout { grid-template-columns: minmax(0, 1fr); gap: 28px; max-width: 450px; }
  .auth-brand { justify-content: center; }.auth-brand-mark { width: 42px; height: 42px; }
  .auth-story-copy, .auth-visual, .auth-capabilities, .auth-story-footer { display: none; }
  .staff-auth-shell .staff-auth-content :deep(.staff-login .staff-login-card), .staff-auth-shell .staff-auth-content :deep(.staff-register) { padding: 28px 24px; border-radius: 18px; }
  .staff-auth-content :deep(.staff-login-card h1) { font-size: 25px; }
}
</style>
