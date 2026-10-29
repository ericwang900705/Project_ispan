<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, session, refreshUser } from '../api'
import { permissionOptions, fixedPermissions, permissionNames, permissionCatalog, roleName, isHighestAdmin, hasPermission } from '../permissions'

const rows = ref([])
const selectedId = ref(null)
const search = ref('')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const showCreate = ref(false)
const form = ref({ username: '', name: '', email: '', password: '' })
const highest = computed(() => isHighestAdmin(session.user))
const canCreate = highest
const roles = [
  { code: 'STAFF', name: 'Staff 人員' },
  { code: 'REVIEWER', name: '審核人員' },
  { code: 'SUPPORT', name: '客服人員' }
]
const descriptions = Object.fromEntries(permissionCatalog.map(item => [item.code, item.description]))
const catalogByCode = Object.fromEntries(permissionCatalog.map(item => [item.code, item]))
const allCodes = permissionCatalog.map(item => item.code)
const selected = computed(() => rows.value.find(row => row.id === selectedId.value))
const filtered = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return rows.value.filter(row => [row.username, row.name, row.email]
    .some(value => String(value || '').toLocaleLowerCase().includes(query)))
})
const protectedAccount = computed(() => selected.value?.role === 'ADMIN')
const roleChanged = computed(() => selected.value && selected.value.draftRole !== selected.value.role)
const same = (a, b) => JSON.stringify([...new Set(a)].sort()) === JSON.stringify([...new Set(b)].sort())
const dirty = row => row?.role === 'STAFF' && row.draftRole === 'STAFF' && !same(row.selected, row.permissions)
const displayedPermissions = computed(() => {
  const row = selected.value
  if (!row) return []
  const role = row.draftRole
  if (role === 'ADMIN') return allCodes
  if (role === 'REVIEWER' || role === 'SUPPORT') return fixedPermissions[role] || []
  if (role === 'STAFF') return row.selected
  return []
})
const selectedCount = computed(() => displayedPermissions.value.length)
const editable = computed(() => highest.value && !protectedAccount.value && selected.value?.draftRole === 'STAFF' && (selected.value.status === 'ACTIVE' || selected.value.role === 'PENDING'))
const canEditCode = code => editable.value && !catalogByCode[code].reserved
function selectAll() {
  if (editable.value && !busy.value) selected.value.selected = [...new Set([...selected.value.selected, ...allCodes.filter(canEditCode)])]
}
function clearAll() {
  if (editable.value && !busy.value) selected.value.selected = selected.value.selected.filter(code => !canEditCode(code))
}
const available = code => permissionOptions(selected.value?.draftRole).includes(code)
const statusText = row => row.role === 'PENDING' ? '待授權' : row.status !== 'ACTIVE' ? '停用' : '啟用'

async function load(resetId = null) {
  const previous = new Map(rows.value.map(row => [row.id, row]))
  await refreshUser()
  const [data, catalog] = await Promise.all([api('/management/staff'), api('/admin/permissions')])
  const known = new Set(catalog.map(item => item.permissionCode))
  if (permissionCatalog.some(item => !item.reserved && !known.has(item.code)))
    throw new Error('權限資料尚未初始化，請先執行更新後 SQL 的第 10 段。')
  rows.value = data.map(row => {
    const old = previous.get(row.id)
    const permissions = row.permissions || []
    const preserve = old && row.id !== resetId && old.role === row.role && same(old.permissions, permissions)
    return {
      ...row, permissions: [...permissions],
      selected: preserve ? [...old.selected] : row.role === 'STAFF' ? permissions.filter(code => permissionOptions(row.role).includes(code)) : [...(fixedPermissions[row.role] || [])],
      draftRole: preserve ? old.draftRole : row.role
    }
  })
  if (!rows.value.some(row => row.id === selectedId.value)) selectedId.value = rows.value[0]?.id ?? null
}
function restoreSavedState(row) {
  if (!row) return
  row.draftRole = row.role
  row.selected = row.role === 'STAFF'
    ? row.permissions.filter(code => permissionOptions('STAFF').includes(code))
    : [...(fixedPermissions[row.role] || [])]
}
function choose(row) {
  if (busy.value || selectedId.value === row.id) return
  // Switching people discards unsaved role and permission drafts.
  restoreSavedState(selected.value)
  restoreSavedState(row)
  selectedId.value = row.id
  error.value = ''; notice.value = ''
}
function resetPermissions() {
  if (!selected.value) return
  selected.value.selected = roleChanged.value ? [] : selected.value.permissions.filter(available)
}
function onRoleChange(role) {
  const row = selected.value
  if (!row) return
  row.draftRole = role
  // Changing the role is a local draft until the administrator confirms it.
  row.selected = role === 'STAFF'
    ? (role === row.role ? row.permissions.filter(code => permissionOptions(role).includes(code)) : [])
    : [...(fixedPermissions[role] || [])]
}
async function changeRole() {
  const row = selected.value
  if (!row || busy.value || !highest.value || protectedAccount.value || !roleChanged.value) return
  const id = row.id
  const nextRole = row.draftRole
  const requested = nextRole === 'STAFF' ? row.selected.filter(code => permissionOptions('STAFF').includes(code)) : []
  busy.value = true; error.value = ''; notice.value = ''
  let roleUpdated = false
  try {
    await api(`/management/staff/${id}/role`, { method: 'PATCH', body: { role: nextRole } })
    roleUpdated = true
    if (nextRole === 'STAFF') {
      await api(`/admin/permissions/staff/${id}`, {
        method: 'PUT',
        body: { permissionCodes: requested }
      })
    }
    await load(id)
    notice.value = '角色與對應權限已更新；該人員須重新登入。'
  } catch (e) {
    if (roleUpdated) {
      // The role PATCH succeeded; reload actual persisted state rather than
      // claiming that the second permission request was rolled back.
      try { await load(id) } catch (_) { /* preserve the original error */ }
      error.value = `角色已更新，但後續授權或資料重新載入失敗：${e.message}。請檢查實際權限後再操作。`
    } else {
      error.value = e.message
    }
  } finally {
    busy.value = false
  }
}

async function savePermissions() {
  const row = selected.value
  if (!row || busy.value || !editable.value || (!roleChanged.value && !dirty(row)))
    return
  // When role is still a draft, the role must be persisted before STAFF grants.
  // changeRole() handles both requests and reports partial failure.
  if (roleChanged.value) {
    await changeRole()
    return
  }
  busy.value = true; error.value = ''
  notice.value = ''
  try {
    await api(`/admin/permissions/staff/${row.id}`, {
      method: 'PUT',
      body: { permissionCodes: [...row.selected] }
    })
    await load(row.id)
    notice.value = `${row.name || row.username} 的功能權限已儲存。`
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
async function create() {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  const username = form.value.username
  try {
    await api('/management/staff', { method: 'POST', body: { ...form.value } })
    await load()
    selectedId.value = rows.value.find(row => row.username === username)?.id ?? selectedId.value
    form.value = { username: '', name: '', email: '', password: '' }
    showCreate.value = false
    notice.value = '人員已建立。請先指定角色，再設定功能權限。'
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  try { await load() }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
})
</script>

<template>
  <section class="staff-management">
    <header class="page-heading">
      <div><span class="eyebrow">STAFF & PERMISSIONS</span>
        <h1>人員與權限管理</h1>
        <p>先從左側選擇人員，再於右側設定角色與功能權限。</p>
      </div>
      <button v-if="canCreate" type="button" class="primary-action" :disabled="busy || loading"
        :aria-expanded="showCreate" @click="showCreate = !showCreate">
        {{ showCreate ? '收起表單' : '＋ 新增人員' }}
      </button>
    </header>
    <p v-if="error" class="feedback error-message" role="alert">{{ error }}</p>
    <p v-if="notice" class="feedback success-message" role="status">{{ notice }}</p>

    <form v-if="showCreate && canCreate" class="create-form" @submit.prevent="create">
      <label>登入帳號
        <input v-model.trim="form.username" required pattern="[A-Za-z0-9_]{4,50}" maxlength="50" :disabled="busy"
          autocomplete="off" />
      </label>
      <label>姓名
        <input v-model.trim="form.name" required maxlength="50" :disabled="busy" />
      </label>
      <label>信箱
        <input v-model.trim="form.email" type="email" required maxlength="100" :disabled="busy" />
      </label>
      <label>初始密碼
        <input v-model="form.password" type="password" required minlength="12" maxlength="72" :disabled="busy"
          autocomplete="new-password" />
      </label>
      <button class="primary-action" :disabled="busy">新增為待授權</button>
    </form>

    <div class="staff-layout" :aria-busy="loading">
      <aside class="staff-panel" aria-label="選擇後台人員">
        <header class="panel-heading">
          <h2>後台人員</h2>
          <span class="count-badge">{{ rows.length }} 位</span>
        </header>
        <div class="staff-search">
          <input v-model="search" type="search" aria-label="搜尋人員" placeholder="搜尋姓名、帳號或信箱" />
        </div>
        <p v-if="loading" class="empty-state">載入人員中…</p>
        <p v-else-if="!filtered.length" class="empty-state">
          {{ rows.length ? '找不到符合的人員' : '目前沒有後台人員' }}
        </p>
        <div v-else class="staff-list">
          <button v-for="row in filtered" :key="row.id" type="button" class="staff-item"
            :class="{ active: selectedId === row.id }" :aria-pressed="selectedId === row.id" :disabled="busy"
            @click="choose(row)">
            <span class="avatar" aria-hidden="true">
              {{ (row.name || row.username).slice(0, 1) }}
            </span>
            <span class="staff-label">
              <strong>{{ row.name || row.username }}</strong>
              <span class="account-name">{{ row.username }}</span>
              <span class="staff-meta">
                {{ roleName(row.role) }}
                <span class="meta-dot">·</span>
                {{ statusText(row) }}
              </span>
              <small v-if="dirty(row) || row.draftRole !== row.role" class="draft-label">尚未儲存</small>
            </span>
            <span class="selection-dot" aria-hidden="true"></span>
          </button>
        </div>
        <footer class="staff-list-footer">僅 ADMIN 可指定角色與分配 STAFF 業務功能</footer>
      </aside>

      <section v-if="selected" class="permissions-panel" aria-labelledby="permissions-title">
        <header class="person-heading">
          <div class="person-title">
            <span class="avatar large" aria-hidden="true">
              {{ (selected.name || selected.username).slice(0, 1) }}
            </span>
            <div>
              <span class="eyebrow">目前選擇的人員</span>
              <h2 id="permissions-title">{{ selected.name || selected.username }}</h2>
              <p class="person-account">
                {{ selected.username }}
                <span>·</span>
                {{ selected.email }}
              </p>
            </div>
          </div>
          <span class="status-badge" :class="{ inactive: selected.status !== 'ACTIVE' }">
            {{ statusText(selected) }}
          </span>
        </header>

        <div class="role-section">
          <div>
            <h3>人員角色</h3>
            <p>最高管理員可切換一般後台人員的角色；審核與客服的功能權限固定。</p>
          </div>
          <div v-if="protectedAccount" class="protected-badge">最高管理員 · 受保護</div>
          <div v-else-if="highest" class="role-actions">
            <select :value="selected.draftRole" @change="onRoleChange($event.target.value)" :disabled="busy"
              aria-label="人員角色">
              <option v-for="role in roles" :key="role.code" :value="role.code">
                {{ role.name }}
              </option>
            </select>
            <button type="button" class="secondary-action" :disabled="busy || !roleChanged" @click="changeRole">
              更新角色
            </button>
          </div>
        </div>
        <p v-if="roleChanged" class="role-notice" role="status">
          目前是尚未儲存的角色預覽。若選擇 STAFF，可先勾選 13 項業務權限，再按「更新角色」一併提交；審核與客服使用固定權限。
        </p>
        <p v-else-if="protectedAccount" class="role-notice">
          最高管理員具有完整權限，帳號角色與功能授權受保護。
        </p>
        <p v-else-if="['REVIEWER', 'SUPPORT'].includes(selected.role)" class="role-notice">
          此角色使用固定功能權限，不能個別增減權限；最高管理員仍可切換其角色。
        </p>
        <p v-else-if="!highest && selected.id === session.user?.id" class="role-notice">
          Staff 人員不能修改自己的權限。
        </p>
        <p v-else-if="selected.role === 'PENDING'" class="role-notice">
          此人員尚未授權。請先指定角色並按「更新角色」。
        </p>

        <section class="function-section" aria-labelledby="function-title">
          <div class="function-heading">
            <div>
              <h3 id="function-title">權限管理</h3>
              <p>勾選允許使用的權限，完成後儲存。</p>
            </div>
            <span class="count-badge">
              已勾選 {{ selectedCount }} / {{ allCodes.length }} 項
            </span>
          </div>
          <div v-if="editable" class="selection-actions">
            <button type="button" class="secondary-action" :disabled="busy || !editable" @click="selectAll">
              全選可授權項目
            </button>
            <button type="button" class="secondary-action" :disabled="busy || !editable" @click="clearAll">
              清空勾選
            </button>
          </div>
          <div class="permission-grid">
            <label v-for="code in allCodes" :key="`${selected.id}-${code}`" class="permission-card"
              :class="{ checked: displayedPermissions.includes(code), unavailable: !protectedAccount && !available(code) }">
              <input v-if="protectedAccount" type="checkbox" checked disabled :aria-label="permissionNames[code]" />
              <input v-else-if="selected.draftRole === 'STAFF'" v-model="selected.selected" type="checkbox"
                :value="code" :disabled="busy || !canEditCode(code)" :aria-label="permissionNames[code]" />
              <input v-else type="checkbox" :checked="displayedPermissions.includes(code)" disabled
                :aria-label="permissionNames[code]" />
              <span class="permission-copy">
                <small class="permission-category">
                  {{ catalogByCode[code].group }}
                </small>
                <strong>{{ permissionNames[code] }}</strong>
                <span>{{ descriptions[code] }}</span>
                <small v-if="!protectedAccount && !available(code)">
                  {{ selected.draftRole === 'PENDING' ? '請先指定角色' : '此角色不開放' }}
                </small>
                <small v-if="available(code) && !canEditCode(code) && !protectedAccount">
                  {{ catalogByCode[code].reserved ? '僅 ADMIN 可使用，不可授予 STAFF' : '固定或未獲授權，已鎖定' }}
                </small>
                <small v-if="catalogByCode[code].status === 'pending'" class="connection-status">
                  尚未串接 · 可先設定權限
                </small>
                <small v-else-if="catalogByCode[code].status === 'partial'" class="connection-status">
                  已接查詢 · 不執行金流退款
                </small>
                <small v-else-if="catalogByCode[code].status === 'backend'" class="connection-status">
                  後端已接 · 前端待串接
                </small>
              </span>
            </label>
          </div>
        </section>
        <footer class="permissions-footer">
          <span>
            {{ protectedAccount ? '完整權限，不需另行儲存' : roleChanged ? '角色與權限尚未儲存，可按更新角色或儲存權限變更' : dirty(selected) ?
              '有尚未儲存的功能權限' : '功能權限已同步' }}
          </span>
          <div v-if="editable" class="footer-actions">
            <button type="button" class="secondary-action" :disabled="busy || (!roleChanged && !dirty(selected))"
              @click="resetPermissions">
              還原勾選
            </button>
            <button type="button" class="primary-action"
              :disabled="busy || !editable || (!roleChanged && !dirty(selected))" @click="savePermissions">
              {{ busy ? '處理中…' : '儲存權限變更' }}
            </button>
          </div>
        </footer>
      </section>
      <section v-else class="permissions-panel detail-empty">
        <h2>{{ loading ? '正在載入' : '請選擇人員' }}</h2>
        <p>選擇左側人員後，即可在這裡設定功能權限。</p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.staff-management {
  max-width: 1240px;
  margin: 0 auto;
  padding: 30px 28px 40px;
  color: #20384e;
  font-size: 14px;
}

.staff-management,
.staff-management * {
  box-sizing: border-box;
}

.staff-management .page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}

.staff-management .eyebrow {
  font-size: 10px;
  letter-spacing: 1.8px;
  color: #6e8a9c;
  font-weight: 600;
}

.staff-management h1 {
  font-size: 26px;
  line-height: 1.4;
  margin: 6px 0 8px;
  color: #20384e;
}

.staff-management h2 {
  font-size: 16px;
  line-height: 1.4;
  margin: 0;
  color: #20384e;
}

.staff-management h3 {
  font-size: 15px;
  line-height: 1.5;
  margin: 0 0 5px;
  color: #20384e;
}

.staff-management p {
  color: #718597;
  line-height: 1.7;
  margin: 0;
}

.staff-management button {
  font: inherit;
  cursor: pointer;
  transform: none;
}

.staff-management button:disabled {
  cursor: not-allowed;
}

.staff-management .primary-action,
.staff-management .secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 17px;
  height: 40px;
  min-height: 40px;
  margin: 0;
  border-radius: 8px;
  font-size: 13px;
  white-space: nowrap;
  font-weight: 600;
}

.staff-management .primary-action {
  color: #fff;
  background: #176b80;
  border: 1px solid #176b80;
}

.staff-management .primary-action:hover:not(:disabled) {
  background: #10566b;
}

.staff-management .secondary-action {
  color: #446477;
  background: #fff;
  border: 1px solid #cfdee7;
}

.staff-management .primary-action:disabled,
.staff-management .secondary-action:disabled {
  opacity: .45;
}

.staff-management input:not([type="checkbox"]),
.staff-management select {
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  margin: 0;
  border: 1px solid #cddde7;
  border-radius: 8px;
  color: #20384e;
  background: #fff;
  font: inherit;
  box-shadow: none;
}

.staff-management :is(button, input, select):focus-visible {
  outline: 2px solid #20879b;
  outline-offset: 3px;
}

.staff-management .staff-layout {
  display: grid;
  grid-template-columns: minmax(250px, 310px) minmax(0, 1fr);
  align-items: start;
  gap: 22px;
}

.staff-management .staff-panel,
.staff-management .permissions-panel {
  min-width: 0;
  border: 1px solid #dce6ed;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 22px #163e5708;
  overflow: hidden;
}

.staff-management .panel-heading {
  padding: 22px 20px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.staff-management .count-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  color: #617e91;
  background: #f0f5f8;
  font-size: 11px;
  white-space: nowrap;
}

.staff-management .staff-search {
  padding: 0 16px 16px;
}

.staff-management .staff-search input {
  font-size: 12px;
  background: #f7fafc;
}

.staff-management .staff-list {
  display: grid;
  gap: 6px;
  padding: 0 10px 12px;
  max-height: 530px;
  overflow-y: auto;
}

.staff-management .staff-item {
  width: 100%;
  height: auto;
  min-height: 88px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 14px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #20384e;
  text-align: left;
  white-space: normal;
}

.staff-management .staff-item:hover:not(:disabled) {
  background: #f4f8fb;
}

.staff-management .staff-item.active {
  background: #eaf6f9;
  border-color: #c8e6ec;
}

.staff-management .avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: #eaf0f6;
  color: #607d93;
  font-size: 15px;
  font-weight: 600;
}

.staff-management .active .avatar {
  background: #d6eef3;
  color: #176b80;
}

.staff-management .staff-label {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.staff-management .staff-label strong {
  font-size: 13px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.staff-management .account-name {
  font-size: 11px;
  color: #718597;
  overflow-wrap: anywhere;
}

.staff-management .staff-meta {
  color: #7c91a1;
  font-size: 10px;
}

.staff-management .meta-dot {
  padding: 0 6px;
}

.staff-management .selection-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 7px;
  background: transparent;
}

.staff-management .active .selection-dot {
  background: #1a8298;
}

.staff-management .draft-label {
  font-size: 10px;
  color: #a16a19;
}

.staff-management .staff-list-footer {
  padding: 14px 16px;
  border-top: 1px solid #e8eef3;
  color: #8a9ca9;
  font-size: 10px;
  text-align: center;
}

.staff-management .person-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 26px;
  background: #fcfdff;
  border-bottom: 1px solid #e6edf2;
}

.staff-management .person-title {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.staff-management .person-title>div {
  min-width: 0;
}

.staff-management .avatar.large {
  flex-basis: 48px;
  width: 48px;
  height: 48px;
  font-size: 20px;
  border-radius: 13px;
  background: #e5f2f6;
  color: #176b80;
}

.staff-management .person-title h2 {
  font-size: 20px;
  margin: 4px 0;
  overflow-wrap: anywhere;
}

.staff-management .person-account {
  font-size: 11px;
  overflow-wrap: anywhere;
}

.staff-management .person-account span {
  padding: 0 8px;
  color: #adbbc5;
}

.staff-management .status-badge {
  border: 1px solid #c8e9dc;
  border-radius: 20px;
  padding: 4px 10px;
  color: #328366;
  background: #eef9f4;
  font-size: 11px;
  white-space: nowrap;
}

.staff-management .status-badge.inactive {
  background: #f6f2ef;
  border-color: #e8dacf;
  color: #92735e;
}

.staff-management .role-section {
  padding: 22px 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.staff-management .role-section p,
.staff-management .function-heading p {
  font-size: 12px;
}

.staff-management .role-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.staff-management .role-actions select {
  width: 140px;
  font-size: 12px;
}

.staff-management .protected-badge {
  padding: 8px 12px;
  border-radius: 8px;
  color: #7b6a44;
  background: #f8f4e9;
  font-size: 12px;
}

.staff-management .role-notice {
  margin: 0 26px 18px;
  padding: 10px 13px;
  border: 1px solid #dfebf2;
  border-radius: 8px;
  background: #f4f9fc;
  color: #617f92;
  font-size: 12px;
}

.staff-management .function-section {
  padding: 23px 26px 26px;
  border-top: 1px solid #e9eff3;
}

.staff-management .function-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.staff-management .selection-actions {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
}

.staff-management .permission-category {
  color: #7097a9;
  font-size: 10px;
}

.staff-management .connection-status {
  color: #a18243;
  font-size: 10px;
}

.staff-management .permission-grid {
  max-height: 570px;
  overflow-y: auto;
  padding: 3px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.staff-management .permission-card {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 11px;
  min-height: 102px;
  margin: 0;
  padding: 17px 14px;
  border: 1px solid #dde6ed;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}

.staff-management .permission-card.checked {
  border-color: #9ecfd9;
  background: #f1fafb;
}

.staff-management .permission-card.unavailable {
  background: #f8fafb;
  border-color: #e8edf2;
  cursor: default;
}

.staff-management .permission-card input[type="checkbox"] {
  appearance: auto;
  display: inline-block;
  flex: 0 0 17px;
  width: 17px;
  height: 17px;
  min-width: 17px;
  min-height: 17px;
  margin: 2px 0 0;
  padding: 0;
  border-radius: 3px;
  accent-color: #197f95;
  box-shadow: none;
}

.staff-management .permission-copy {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.staff-management .permission-copy strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #33566d;
}

.staff-management .permission-copy>span {
  font-size: 11px;
  line-height: 1.7;
  font-weight: 400;
  color: #7b91a0;
}

.staff-management .permission-copy small {
  font-size: 10px;
  line-height: 1.5;
  color: #98a8b3;
}

.staff-management .unavailable .permission-copy strong {
  color: #8a9ba8;
}

.staff-management .permissions-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 26px;
  border-top: 1px solid #e6edf2;
  background: #fcfdff;
}

.staff-management .permissions-footer>span {
  font-size: 11px;
  color: #8397a5;
}

.staff-management .footer-actions {
  display: flex;
  gap: 8px;
}

.staff-management .empty-state {
  padding: 30px 18px;
  text-align: center;
  font-size: 12px;
}

.staff-management .detail-empty {
  min-height: 350px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 30px;
  text-align: center;
}

.staff-management .detail-empty p {
  font-size: 12px;
}

.staff-management .feedback {
  margin: 0 0 18px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
}

.staff-management .error-message {
  color: #9c3b42;
  background: #fff0f1;
  border: 1px solid #f1d2d5;
}

.staff-management .success-message {
  color: #33705e;
  background: #edf8f2;
  border: 1px solid #cfe7da;
}

.staff-management .create-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: 14px;
  align-items: end;
  padding: 22px;
  margin-bottom: 22px;
  border: 1px solid #dce6ed;
  border-radius: 12px;
  background: #fff;
}

.staff-management .create-form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  font-size: 12px;
  color: #567083;
}

@media (max-width: 1100px) {
  .staff-management .staff-layout {
    grid-template-columns: 250px minmax(0, 1fr);
    gap: 16px;
  }

  .staff-management .create-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .staff-management .permission-grid {
    grid-template-columns: 1fr;
  }

  .staff-management .role-section {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 800px) {
  .staff-management {
    padding: 22px 16px 32px;
  }

  .staff-management .staff-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .staff-management .staff-list {
    max-height: 260px;
  }

  .staff-management .permission-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .staff-management .person-heading,
  .staff-management .role-section,
  .staff-management .function-section {
    padding: 20px;
  }

  .staff-management .role-notice {
    margin-left: 20px;
    margin-right: 20px;
  }
}

@media (max-width: 520px) {
  .staff-management .page-heading {
    align-items: flex-start;
  }

  .staff-management h1 {
    font-size: 22px;
  }

  .staff-management .page-heading p {
    font-size: 12px;
  }

  .staff-management .permission-grid,
  .staff-management .create-form {
    grid-template-columns: 1fr;
  }

  .staff-management .person-heading {
    flex-wrap: wrap;
  }

  .staff-management .person-account span {
    display: none;
  }

  .staff-management .person-account {
    display: flex;
    flex-direction: column;
  }

  .staff-management .permissions-footer {
    align-items: flex-start;
    flex-direction: column;
    padding: 18px 20px;
  }

  .staff-management .footer-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
