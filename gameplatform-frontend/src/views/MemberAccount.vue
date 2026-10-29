<script setup>
import DatePicker from '../components/DatePicker.vue'
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, session, replaceToken } from '../api'
import GoogleSignInButton from '../components/GoogleSignInButton.vue'

const route = useRoute()
const router = useRouter()
const action = computed(() => route.path.split('/').at(-1))
const loading = ref(false)
const error = ref('')
const notice = ref('')
const profile = ref(null)
const googleLink = ref(null), googleError = ref(''), linking = ref(false)

// 根據當前 action 決定次分頁
const profileSection = computed(() => {
  if (action.value === 'change-password') return 'password'
  if (action.value === 'apply-publisher') return 'publisher'
  return 'profile'
})

const avatarFailed = ref(false)
const birthdayLocked = computed(() => !!profile.value?.birthDate)
const birthdayText = computed(() => String(profile.value?.birthDate || '').replaceAll('-', ' / '))
const avatarPreview = computed(() => /^(https?:\/\/|\/uploads\/avatars\/)/.test(form.value.avatarUrl) ? form.value.avatarUrl : '')
const watchTicket = ref(sessionStorage.getItem('hero-verification-watch') || '')
const verificationComplete = ref(sessionStorage.getItem('hero-verification-complete') === 'true')
const verificationStatusNotice = ref('')
let monitorTimer = null, monitorGeneration = 0

function stopMonitor() { monitorGeneration++; clearTimeout(monitorTimer); monitorTimer = null }
function clearWaiting() {
  for (const key of ['hero-verification-email', 'hero-verification-sent', 'hero-verification-watch']) sessionStorage.removeItem(key)
}
function rememberWatch(result) {
  verificationComplete.value = false
  sessionStorage.removeItem('hero-verification-complete')
  watchTicket.value = result.watchTicket || ''
  if (watchTicket.value) sessionStorage.setItem('hero-verification-watch', watchTicket.value)
}
async function monitorVerification(generation) {
  if (generation !== monitorGeneration || action.value !== 'verification-sent' || !watchTicket.value) return
  const ticket = watchTicket.value
  try {
    const status = await api('/auth/verification-status?ticket=' + encodeURIComponent(ticket))
    if (generation !== monitorGeneration || ticket !== watchTicket.value) return
    if (status.verified) {
      stopMonitor(); verificationComplete.value = true
      sessionStorage.setItem('hero-verification-complete', 'true')
      clearWaiting(); verificationStatusNotice.value = ''; return
    }
    verificationStatusNotice.value = '等待信箱驗證，完成後本頁會顯示驗證成功。'
  } catch (e) {
    if (generation !== monitorGeneration) return
    verificationStatusNotice.value = e.message
    if ([400, 410].includes(e.status)) return
  }
  if (generation === monitorGeneration && action.value === 'verification-sent') monitorTimer = setTimeout(() => monitorVerification(generation), 3000)
}
function startMonitor() { stopMonitor(); if (watchTicket.value && !verificationComplete.value) monitorVerification(monitorGeneration) }
onUnmounted(stopMonitor)

const verificationEmail = ref(sessionStorage.getItem('hero-verification-email') || '')
const verificationSent = ref(sessionStorage.getItem('hero-verification-sent') === 'true')
const form = ref({
  username: '', email: '', nationalId: '', rawPassword: '', confirmPassword: '',
  oldPassword: '', newPassword: '', birthDate: '', address: '', avatarUrl: ''
})
watch(() => form.value.avatarUrl, () => { avatarFailed.value = false })

const publisherStatus = ref({ status: 'UNKNOWN', reviewComment: '' }), publisherStatusError = ref('')
const publisherForm = ref({
  companyName: '',
  taxId: '',
  websiteUrl: '',
  bankName: '',
  bankAccount: ''
})

const title = computed(() => ({
  register: '建立會員帳號',
  verify: verificationComplete.value ? '信箱驗證完成' : '驗證會員信箱',
  'verification-sent': verificationComplete.value ? '驗證成功' : verificationSent.value ? '已發送驗證信' : '信箱驗證',
  'forgot-password': '忘記密碼',
  'reset-password': '重設密碼',
  profile: '個人資料',
  'change-password': '更改密碼',
  'apply-publisher': '申請遊戲發行商'
})[action.value] || '會員中心')

const leadText = computed(() => {
  if (action.value === 'register') return '建立帳號，開始探索你的遊戲世界。'
  if (action.value === 'verification-sent') return verificationComplete.value ? '你的信箱已完成驗證。' : '請開啟電子郵件中的連結完成驗證。'
  if (action.value === 'apply-publisher') return '填寫公司資料，申請成為平台遊戲發行商。'
  return '會員資料與帳號安全管理'
})

const isProfile = computed(() => ['profile', 'change-password', 'apply-publisher'].includes(action.value))

watch(() => route.fullPath, async () => {
  stopMonitor(); error.value = ''; notice.value = ''
  form.value.oldPassword = form.value.newPassword = form.value.confirmPassword = ''
  if (isProfile.value) await loadProfile()
  if (action.value === 'forgot-password' && typeof route.query.email === 'string') form.value.email = route.query.email
  if (action.value === 'verification-sent') { form.value.email = verificationEmail.value; startMonitor() }
  if (action.value === 'verify') { verificationComplete.value = false; await verify() }
}, { immediate: true })

async function loadProfile() {
  if (!session.user) return
  try {
    profile.value = await api('/members/me')
    form.value.username = profile.value.username || '' // 新增：預填目前的帳號 (例如 Player_xxx)
    form.value.birthDate = profile.value.birthDate || ''
    form.value.address = profile.value.address || ''
    form.value.avatarUrl = profile.value.avatarUrl || ''
    publisherStatusError.value = ''
    try { publisherStatus.value = await api('/members/publisher-application') } catch (e) { publisherStatus.value = { status: 'UNKNOWN', reviewComment: '' }; publisherStatusError.value = e.message }
    googleError.value = ''; googleLink.value = null
    try { googleLink.value = await api('/members/google-link') } catch (e) { googleError.value = e.message }
  } catch (e) { error.value = e.message }
}

async function linkGoogle({ idToken, nonce }) {
  if (linking.value) return; linking.value = true; googleError.value = ''; notice.value = ''
  try { const result = await api('/members/google-link', { method: 'POST', body: { idToken, nonce } }); googleLink.value = result; notice.value = result.message }
  catch (e) { googleError.value = e.message } finally { linking.value = false }
}

async function send(path, body, method = 'POST') {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const result = await api(path, { method, body })
    notice.value = result?.message || '操作成功'
    return result
  } catch (e) { error.value = e.message; return null }
  finally { loading.value = false }
}

async function register() {
  if (form.value.rawPassword !== form.value.confirmPassword) { error.value = '兩次輸入的密碼不同'; return }
  const result = await send('/auth/register', {
    username: form.value.username, email: form.value.email,
    nationalId: form.value.nationalId, rawPassword: form.value.rawPassword
  })
  if (result) {
    rememberWatch(result)
    verificationEmail.value = form.value.email
    verificationSent.value = true
    sessionStorage.setItem('hero-verification-email', verificationEmail.value)
    sessionStorage.setItem('hero-verification-sent', 'true')
    form.value.rawPassword = ''; form.value.confirmPassword = ''; form.value.nationalId = ''
    await router.push('/member/verification-sent')
  }
}

async function forgot() { await send('/auth/forgot-password', { email: form.value.email }) }

async function resendVerification() {
  if (loading.value) return
  const result = await send('/auth/resend-verification', { email: form.value.email })
  if (result) {
    rememberWatch(result)
    verificationEmail.value = form.value.email
    verificationSent.value = true
    sessionStorage.setItem('hero-verification-email', verificationEmail.value)
    sessionStorage.setItem('hero-verification-sent', 'true')
    startMonitor()
  }
}

async function resetPassword() {
  if (form.value.newPassword !== form.value.confirmPassword) { error.value = '兩次輸入的密碼不同'; return }
  const result = await send('/auth/reset-password', { token: route.query.token, newPassword: form.value.newPassword })
  if (result) form.value.newPassword = form.value.confirmPassword = ''
}

async function verify() {
  if (!route.query.token) { error.value = '缺少驗證碼，請使用電子郵件中的驗證連結'; return }
  const token = route.query.token
  const result = await send('/auth/verify?token=' + encodeURIComponent(token), null, 'GET')
  if (result && action.value === 'verify' && route.query.token === token) {
    verificationComplete.value = true
    clearWaiting()
    await router.replace('/member/login')
  }
}

async function uploadAvatar(event) {
  const input = event.target, file = input.files?.[0]
  if (!file || loading.value) return
  error.value = ''; notice.value = ''
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) { error.value = '請選擇 5 MB 以內的 JPG 或 PNG 圖片'; input.value = ''; return }
  const account = session.user
  loading.value = true
  try {
    const body = new FormData(); body.append('file', file)
    const result = await api('/members/me/avatar', { method: 'POST', body })
    if (session.user !== account) return
    profile.value = result; form.value.avatarUrl = result.avatarUrl || ''; avatarFailed.value = false
    notice.value = '頭像已更新'
  } catch (e) { if (session.user === account) error.value = e.message }
  finally { loading.value = false; input.value = '' }
}

async function updateProfile() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const body = {
      birthDate: birthdayLocked.value ? null : form.value.birthDate || null,
      address: form.value.address
    }

    // 僅在尚未設定密碼 (hasPassword === false) 時，才檢查並送出帳號密碼
    if (profile.value?.hasPassword === false) {
      const usernameChanged = form.value.username && form.value.username !== profile.value.username
      const hasEnteredPassword = !!form.value.newPassword

      if (usernameChanged && !hasEnteredPassword) {
        error.value = '修改登入帳號時，請一併設定登入密碼'
        loading.value = false
        return
      }

      if (hasEnteredPassword) {
        if (form.value.newPassword !== form.value.confirmPassword) {
          error.value = '兩次輸入的密碼不同'
          loading.value = false
          return
        }
        body.username = form.value.username
        body.newPassword = form.value.newPassword
      }
    }

    const updated = await api('/members/profile', {
      method: 'PATCH',
      body
    })

    // 如果後端有回傳新的 accessToken
    if (updated.accessToken) {
      await replaceToken(updated.accessToken)
    }

    profile.value = updated
    form.value.username = profile.value.username || ''
    form.value.birthDate = profile.value.birthDate || ''
    form.value.newPassword = ''
    form.value.confirmPassword = ''
    notice.value = '個人資料已更新'
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function changePassword() {
  if (form.value.newPassword !== form.value.confirmPassword) { error.value = '兩次輸入的新密碼不同'; return }
  const result = await send('/members/change-password', {
    oldPassword: form.value.oldPassword, newPassword: form.value.newPassword
  })
  if (result) form.value.oldPassword = form.value.newPassword = form.value.confirmPassword = ''
}

async function applyPublisher() {
  if (loading.value || !['NONE', 'REJECTED'].includes(publisherStatus.value.status)) return
  if (!/^[0-9]{8}$/.test(publisherForm.value.taxId)) { error.value = '統一編號必須為 8 位數字'; return }
  const result = await send('/members/apply-publisher', { ...publisherForm.value })
  if (result) {
    publisherStatus.value = { status: 'PENDING', reviewComment: '' }
    for (const key of Object.keys(publisherForm.value)) publisherForm.value[key] = ''
    notice.value = result.message || '發行商申請已送出，請等候審核'
  }
}
</script>

<template>
  <section class="member-account-wrap" :class="{ 'member-profile-layout': isProfile }">
    <aside v-if="isProfile" class="member-profile-nav" aria-label="會員設定">
      <strong>會員設定</strong>
      <RouterLink to="/member/profile" :class="{ active: profileSection === 'profile' }">個人資料</RouterLink>
      <RouterLink to="/member/change-password" :class="{ active: profileSection === 'password' }">更改密碼</RouterLink>
      <RouterLink v-if="!profile?.isPublisher" to="/member/apply-publisher"
        :class="{ active: profileSection === 'publisher' }">申請遊戲發行商</RouterLink>
    </aside>

    <section class="member-account-card">
      <span class="eyebrow">HERO MEMBERSHIP</span>
      <h1>{{ title }}</h1>
      <p class="member-account-lead">{{ leadText }}</p>

      <p v-if="error" class="member-account-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="member-account-success" role="status">{{ notice }}</p>

      <!-- 註冊 -->
      <form v-if="action === 'register'" @submit.prevent="register">
        <label>帳號 <input v-model.trim="form.username" autocomplete="username" required minlength="4" maxlength="30"
            placeholder="4～30 位英數字或底線" /></label>
        <label>電子郵件 <input v-model.trim="form.email" type="email" autocomplete="email" required
            maxlength="100" /></label>
        <label>身分證字號 <input v-model.trim="form.nationalId" autocomplete="off" required maxlength="10"
            placeholder="台灣身分證字號" /></label>
        <label>密碼 <input v-model="form.rawPassword" type="password" autocomplete="new-password" required minlength="8"
            maxlength="72" /></label>
        <label>確認密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" required
            minlength="8" maxlength="72" /></label>
        <button class="primary" :disabled="loading">{{ loading ? '建立中…' : '建立帳號' }}</button>
      </form>

      <!-- 驗證成功 -->
      <div v-else-if="action === 'verification-sent' && verificationComplete" role="status">
        <p>驗證成功！你的會員帳號已啟用，可以在驗證信開啟的頁面登入。</p>
        <RouterLink class="primary" to="/member/login">會員登入</RouterLink>
      </div>

      <!-- 重發驗證信 -->
      <form v-else-if="action === 'verification-sent'" @submit.prevent="resendVerification">
        <p v-if="verificationSent">驗證信已寄送至 <strong>{{ verificationEmail
            }}</strong>，請查看收件匣或垃圾郵件。完成信箱驗證後，本頁會顯示驗證成功；驗證信開啟的頁面會前往會員登入。</p>
        <p v-if="verificationStatusNotice" role="status">{{ verificationStatusNotice }}</p>
        <p>沒有收到或連結過期？確認註冊信箱後重新寄送。</p>
        <label>電子郵件 <input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <button class="primary" :disabled="loading">{{ loading ? '寄送中…' : '重發驗證信' }}</button>
      </form>

      <!-- 忘記密碼 -->
      <form v-else-if="action === 'forgot-password'" @submit.prevent="forgot">
        <p>輸入註冊信箱，我們將寄送密碼重設連結。</p>
        <label>電子郵件 <input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <button class="primary" :disabled="loading">寄送重設密碼信</button>
      </form>

      <!-- 重設密碼 -->
      <form v-else-if="action === 'reset-password'" @submit.prevent="resetPassword">
        <label>新密碼 <input v-model="form.newPassword" type="password" autocomplete="new-password" required minlength="8"
            maxlength="72" /></label>
        <label>確認新密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" required
            minlength="8" maxlength="72" /></label>
        <button class="primary" :disabled="loading || !route.query.token">確定重設密碼</button>
      </form>

      <!-- 驗證中 -->
      <div v-else-if="action === 'verify'">
        <p>{{ loading ? '正在確認信箱驗證，請稍候…' : '請使用驗證郵件內的連結完成啟用。' }}</p>
        <button v-if="!verificationComplete" class="primary" type="button" :disabled="loading || !route.query.token"
          @click="verify">{{ loading ? '驗證中…' : '完成信箱驗證' }}</button>
      </div>

      <!-- 會員專區 (isProfile) -->
      <template v-else-if="isProfile">
        <!-- 1. 個人資料 -->
        <template v-if="profileSection === 'profile'">
          <div v-if="profile" class="member-profile-identity">
            <div class="member-avatar">
              <img v-if="avatarPreview && !avatarFailed" :src="avatarPreview" alt="會員頭像" referrerpolicy="no-referrer"
                @error="avatarFailed = true" />
              <span v-else>{{ (profile.username || '會員').slice(0, 2) }}</span>
            </div>
            <div><strong>{{ profile.username }}</strong>
              <p>{{ profile.email }}</p><small>{{ profile.isPublisher ? '發行商' : '會員' }}</small>
            </div>
          </div>
          <form @submit.prevent="updateProfile">
            <!-- 新增：僅 Google 登入且尚未設定密碼 (無 password_history_id) 時顯示 -->
            <template v-if="profile?.hasPassword === false">
              <label>登入帳號
                <input v-model.trim="form.username" autocomplete="username" minlength="4" maxlength="30"
                  placeholder="4～30 位英數字或底線" />
              </label>
              <label>設定登入密碼
                <input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8"
                  maxlength="72" placeholder="設定 8～72 位密碼，下次即可用帳號密碼登入" />
              </label>
              <label v-if="form.newPassword">確認登入密碼
                <input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8"
                  maxlength="72" required placeholder="請再次輸入密碼" />
              </label>
            </template>
            <div class="profile-birthday"><span>生日</span>
              <p v-if="birthdayLocked" class="profile-static-date">{{ birthdayText }}</p>
              <template v-else>
                <DatePicker v-model="form.birthDate" label="生日" aria-label="生日" /><small>生日只能設定一次，儲存後無法修改。</small>
              </template>
            </div>
            <label>地址 <input v-model="form.address" maxlength="500" /></label>
            <label>上傳頭像 <input type="file" accept="image/jpeg,image/png" :disabled="loading"
                @change="uploadAvatar" /><small>JPG 或
                PNG，最多 5 MB；選取後立即上傳。</small></label>
            <button class="primary" :disabled="loading">儲存個人資料</button>
          </form>
          <section class="google-account-link">
            <h2>Google 帳號</h2>
            <div v-if="googleLink?.linked" class="google-linked-status" role="status" aria-live="polite">
              <div class="google-linked-heading">
                <svg class="google-linked-icon" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
                  <path fill="#EA4335"
                    d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z" />
                  <path fill="#4285F4"
                    d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6C44.4 38.03 46.98 31.86 46.98 24.55Z" />
                  <path fill="#FBBC05"
                    d="M10.53 28.59A14.4 14.4 0 0 1 9.75 24c0-1.59.28-3.13.78-4.59l-7.98-6.19A23.9 23.9 0 0 0 0 24c0 3.87.93 7.52 2.56 10.78l7.97-6.19Z" />
                  <path fill="#34A853"
                    d="M24 48c6.48 0 11.93-2.13 15.91-5.8l-7.73-6c-2.15 1.45-4.92 2.3-8.18 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48Z" />
                </svg>
                <strong>Google</strong><span class="google-linked-badge">已連接</span>
              </div>
              <p v-if="googleLink.email" class="google-linked-email">{{ googleLink.email }}</p>
              <p class="google-linked-help">下次可使用 Google 登入同一個會員帳號。</p>
            </div><template v-else-if="googleLink">
              <p>綁定後可用 Google 登入同一會員帳號，保留遊戲、訂單與發行商資料。</p>
              <GoogleSignInButton link :busy="linking" @credential="linkGoogle" />
            </template>
            <p v-if="googleError" class="error" role="alert">{{ googleError }}</p>
          </section>
        </template>

        <!-- 2. 更改密碼 (明確指定 profileSection === 'password') -->
        <template v-else-if="profileSection === 'password'">
          <div v-if="profile?.hasPassword === false">
            <p>你目前使用 Google 登入，尚未設定本機密碼。可透過信箱驗證設定密碼。</p>
            <RouterLink class="primary" :to="{ path: '/member/forgot-password', query: { email: profile.email } }">
              寄送密碼設定信
            </RouterLink>
          </div>
          <form v-else-if="profile" @submit.prevent="changePassword">
            <label>目前密碼 <input v-model="form.oldPassword" type="password" autocomplete="current-password"
                required /></label>
            <label>新密碼 <input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8"
                maxlength="72" required /></label>
            <label>確認新密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8"
                maxlength="72" required /></label>
            <button class="primary" :disabled="loading">修改密碼</button>
          </form>
        </template>

        <!-- 3. 申請遊戲發行商 -->
        <template v-else-if="profileSection === 'publisher'">
          <div v-if="profile?.isPublisher" role="status">
            <p>您目前已經是遊戲發行商身分，無需重複申請。</p>
          </div>
          <p v-else-if="publisherStatus.status === 'PENDING'" role="status">發行商申請已送出，正在審核中，無需重複申請。</p>
          <div v-else-if="publisherStatus.status === 'UNKNOWN'">
            <p role="alert">{{ publisherStatusError || '正在確認申請狀態…' }}</p><button type="button"
              @click="loadProfile">重新整理</button>
          </div>
          <form v-else @submit.prevent="applyPublisher">
            <p v-if="publisherStatus.status === 'REJECTED'" role="status">上次申請未通過：{{ publisherStatus.reviewComment ||
              '請修正資料後重新申請' }}</p>
            <label>公司名稱
              <input v-model.trim="publisherForm.companyName" required maxlength="100" />
            </label>
            <label>統一編號
              <input v-model.trim="publisherForm.taxId" required maxlength="8" pattern="[0-9]{8}" inputmode="numeric" />
            </label>
            <label>官方網站（選填）
              <input v-model.trim="publisherForm.websiteUrl" type="url" maxlength="255" />
            </label>
            <label>
              收款銀行名稱
              <input v-model.trim="publisherForm.bankName" required maxlength="100" />
            </label>
            <label>
              收款銀行帳號
              <input v-model.trim="publisherForm.bankAccount" required maxlength="30" pattern="[0-9\-]+"
                inputmode="numeric" />
            </label>
            <button class="primary" :disabled="loading">
              {{ loading ? '送出中…' : '送出發行商申請' }}
            </button>
          </form>
        </template>
      </template>

      <div class="member-account-links">
        <RouterLink to="/member/login">會員登入</RouterLink>
        <RouterLink to="/">返回商城</RouterLink>
      </div>
    </section>
  </section>
</template>

<style scoped>
.google-account-link {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid #cfe1ec;
  line-height: 1.8
}

.google-linked-status {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid #c9dfd6;
  border-radius: 14px;
  background: #f5fbf8
}

.google-linked-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: #17364e
}

.google-linked-icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0
}

.google-linked-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: #e1f2e8;
  color: #166534;
  font-size: 13px;
  font-weight: 700
}

.google-linked-status .google-linked-email {
  margin: 10px 0 0;
  color: #17364e;
  overflow-wrap: anywhere
}

.google-linked-status .google-linked-help {
  margin: 4px 0 0;
  color: #536c79;
  font-size: 13px
}

.member-account-wrap {
  position: relative;
  isolation: isolate;
  z-index: 0;
  width: min(100% - 32px, 620px);
  margin: clamp(36px, 5vw, 76px) auto 70px;
  color: #17364e
}

.member-account-card {
  padding: clamp(24px, 4vw, 44px);
  background: rgba(247, 252, 255, .94);
  border: 1px solid rgba(125, 170, 202, .55);
  border-radius: 22px;
  box-shadow: 0 16px 48px rgba(18, 59, 95, .15)
}

.member-account-card h1 {
  margin: 8px 0 6px;
  font-size: clamp(27px, 3vw, 35px);
  color: #17364e
}

.member-account-lead,
.member-account-hint {
  color: #45637b
}

.member-account-card form {
  display: grid;
  gap: 14px;
  margin-top: 24px
}

.member-account-card h2 {
  font-size: 19px;
  color: #17364e;
  margin: 6px 0 0
}

.member-account-card label {
  display: grid;
  gap: 7px;
  color: #17364e;
  font-weight: 600
}

.member-account-card input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #91b2cd;
  border-radius: 10px;
  background: #fff;
  color: #17364e;
  padding: 12px 13px;
  font-size: 16px
}

.member-account-card input:focus {
  outline: 2px solid #1784a4;
  outline-offset: 1px
}

.member-account-card button.primary {
  width: 100%;
  min-height: 45px;
  border: 0;
  border-radius: 10px;
  background: #096a86;
  color: white;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer
}

.member-account-card button:disabled {
  opacity: .55;
  cursor: not-allowed
}

.member-account-links {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid rgba(125, 170, 202, .35)
}

.member-account-links a {
  color: #0b678b;
  font-weight: 650;
  text-decoration: underline
}

.member-account-hint {
  display: block;
  margin-top: 24px;
  line-height: 1.6
}

.member-account-error {
  padding: 12px;
  background: #fff0ed;
  color: #942a1a;
  border-radius: 9px
}

.member-account-success {
  padding: 12px;
  background: #edf9f8;
  color: #115c55;
  border-radius: 9px
}

.member-profile-summary {
  margin: 22px 0;
  background: rgba(209, 230, 244, .66);
  padding: 12px 18px;
  border-radius: 12px
}

.member-profile-summary p {
  margin: 8px 0
}

@media(max-width:480px) {
  .member-account-wrap {
    position: relative;
    isolation: isolate;
    z-index: 0;
    width: calc(100% - 24px);
    margin: 25px auto 40px
  }

  .member-account-card {
    padding: 20px
  }
}

.member-profile-layout {
  width: min(100% - 32px, 820px);
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  align-items: start;
  gap: 20px;
  margin: 36px auto 56px
}

.member-profile-layout .member-account-card {
  padding: 26px;
  border-radius: 18px
}

.member-profile-nav {
  display: grid;
  gap: 8px;
  padding: 18px 12px;
  background: rgba(247, 252, 255, .94);
  border: 1px solid var(--hero-page-border);
  border-radius: 15px;
  position: sticky;
  top: 94px
}

.member-profile-nav strong {
  padding: 5px 10px 12px;
  font-size: 14px
}

.member-profile-nav a {
  text-align: left;
  padding: 12px;
  border-radius: 9px;
  color: #45637b;
  background: transparent
}

.member-profile-nav a.active {
  background: #d8edf6;
  color: #095c77;
  font-weight: 700
}

.member-profile-identity {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 22px 0;
  padding-bottom: 20px;
  border-bottom: 1px solid #d1e2ec
}

.member-profile-identity strong {
  font-size: 18px
}

.member-profile-identity p {
  margin: 5px 0;
  font-size: 14px;
  overflow-wrap: anywhere
}

.member-profile-identity small {
  color: #45637b
}

.member-avatar {
  width: 76px;
  height: 76px;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  background: #d8edf6;
  display: grid;
  place-items: center;
  color: #0b678b;
  font-size: 23px;
  font-weight: 700;
  border: 2px solid #fff
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover
}

.profile-birthday {
  display: grid;
  gap: 7px;
  font-weight: 600
}

.profile-birthday small {
  font-size: 12px;
  font-weight: 400;
  color: #45637b
}

.profile-static-date {
  margin: 0;
  padding: 10px 0;
  font-variant-numeric: tabular-nums;
  font-size: 16px
}

@media(max-width:650px) {
  .member-profile-layout {
    grid-template-columns: 1fr;
    gap: 12px
  }

  .member-profile-nav {
    position: static;
    grid-template-columns: 1fr 1fr;
    padding: 10px
  }

  .member-profile-nav strong {
    display: none
  }

  .member-profile-nav a {
    text-align: center
  }

  .member-profile-layout .member-account-card {
    padding: 22px
  }
}
</style>
