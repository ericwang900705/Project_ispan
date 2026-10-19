<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { permissionOptions, permissionNames } from '../permissions'
import { useRoute } from 'vue-router'

const route = useRoute()
const rows = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)
const roles = [
    {
        code: 'PENDING',
        name: '待授權'
    },
    {
        code: 'REVIEWER',
        name: '審核人員'
    },
    {
        code: 'SUPPORT',
        name: '客服人員'
    },
    {
        code: 'ADMIN',
        name: '管理員'
    }
]
const form = ref({
    username: '',
    name: '',
    email: '',
    password: ''
})
const showCreate = ref(false)

async function load() {
    rows.value = (await api('/management/staff')).map(row => ({ ...row, selected: [...row.permissions] }))
}
async function change(row, event) {
    const role = event.target.value;
    event.target.value = row.role;
    if (busy.value || role === row.role)
        return;
    busy.value = true;
    error.value = '';
    notice.value = '';
    try {
        const result = await api(`/management/staff/${row.id}/role`, { method: 'PATCH', body: { role } });
        notice.value = result.message;
        await load()
    } catch (e) {
        error.value = e.message
    } finally { busy.value = false }
}
async function savePermissions(row) {
    if (busy.value) return
    busy.value = true; error.value = ''; notice.value = ''
    try {
        const result = await api(`/management/staff/${row.id}/permissions`, {
            method: 'PATCH', body: { permissions: row.selected }
        })
        notice.value = result.message
        await load()
    } catch (e) { error.value = e.message }
    finally { busy.value = false }
}
async function create() {
    busy.value = true; error.value = '';
    try {
        const r = await api('/management/staff', { method: 'POST', body: form.value });
        notice.value = r.message; form.value = {
            username: '',
            name: '',
            email: '',
            password: ''
        };
        showCreate.value = false; await load()
    } catch (e) {
        error.value = e.message
    } finally {
        busy.value = false
    }
}
onMounted(() => load().catch(e => error.value = e.message))
</script>
<template>
    <section class="staff-management">
        <header>
            <div>
                <h1>{{ route.query.mode === 'roles' ? '後台角色權限管理' : '後台人員管理' }}</h1>
                <p>僅最高管理員可以授權。變更角色會清除原有功能權限，請再勾選並儲存；變更角色後須重新登入。</p>
            </div><button @click="showCreate = !showCreate">新增人員</button>
        </header>
        <p v-if="error" role="alert">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <form v-if="showCreate" @submit.prevent="create"><label>帳號<input v-model.trim="form.username" required
                    pattern="[A-Za-z0-9_]{4,50}" /></label><label>姓名<input v-model.trim="form.name" required
                    maxlength="50" /></label><label>信箱<input v-model.trim="form.email" type="email" required
                    maxlength="100" /></label><label>初始密碼<input v-model="form.password" type="password" required
                    minlength="12" maxlength="72" autocomplete="new-password" /></label><button
                :disabled="busy">新增為待授權</button></form>
        <div class="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th>帳號</th>
                        <th>姓名</th>
                        <th>信箱</th>
                        <th>狀態</th>
                        <th>角色</th>
                        <th>個別功能權限</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in rows" :key="row.id">
                        <td>{{ row.username }}</td>
                        <td>{{ row.name }}</td>
                        <td>{{ row.email }}</td>
                        <td>{{ row.role === 'PENDING' ? '待授權' : row.status === 'ACTIVE' ? '啟用' : '停用' }}</td>
                        <td><span v-if="row.role === 'SUPERADMIN'">最高管理員（受保護）</span><select v-else :value="row.role" :disabled="busy" :aria-label="row.username + ' 的權限'"
                                @change="change(row, $event)">
                                <option v-for="role in roles" :key="role.code" :value="role.code">{{ role.name }}
                                </option>
                            </select></td>
                        <td>
                            <span v-if="row.role === 'SUPERADMIN'">完整權限</span>
                            <span v-else-if="row.role === 'PENDING'">請先選擇角色</span>
                            <div v-else class="permission-list">
                                <label v-for="code in permissionOptions(row.role)" :key="code">
                                    <input v-model="row.selected" type="checkbox" :value="code" :disabled="busy" />
                                    {{ permissionNames[code] }}
                                </label>
                                <button :disabled="busy" @click="savePermissions(row)">儲存功能權限</button>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </section>
</template>
<style scoped>
.permission-list { display: grid; gap: 10px; min-width: 170px; }
.permission-list label { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.staff-management .permission-list input[type="checkbox"] { width: 18px; height: 18px; padding: 0; }

.staff-management {
    max-width: 1200px;
    margin: auto;
    padding: 28px;
    color: #20384e;
}

.staff-management,
.staff-management * {
    box-sizing: border-box;
}

.staff-management>header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 20px;
    margin-bottom: 24px;
}

.staff-management h1 {
    margin: 0 0 10px;
    font-size: 28px;
}

.staff-management header p {
    margin: 0;
    color: #61798c;
    line-height: 1.7;
}

.staff-management button {
    padding: 0 18px;
    height: 42px;
    border: 0;
    border-radius: 8px;
    background: #17647d;
    color: white;
    font: inherit;
    white-space: nowrap;
    cursor: pointer;
}

.staff-management button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

/* 新增人員表單 */
.staff-management>form {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
    align-items: end;
    gap: 18px;
    margin: 0 0 24px;
    padding: 24px;
    background: white;
    border: 1px solid #dce7ee;
    border-radius: 12px;
    box-shadow: 0 4px 16px #163e5710;
}

.staff-management form label {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
}

.staff-management input,
.staff-management select {
    width: 100%;
    min-width: 0;
    height: 42px;
    padding: 0 12px;
    border: 1px solid #c5dce8;
    border-radius: 8px;
    background: #f8fbfd;
    color: #20384e;
    font: inherit;
}

.staff-management input:focus,
.staff-management select:focus {
    outline: 2px solid #17869c;
    outline-offset: 2px;
}

/* 人員列表 */
.table-scroll {
    overflow-x: auto;
    background: white;
    border: 1px solid #dce7ee;
    border-radius: 12px;
}

table {
    width: 100%;
    border-collapse: collapse;
}

th,
td {
    padding: 16px;
    text-align: left;
    border-bottom: 1px solid #e7eff4;
}

th {
    background: #f3f8fb;
    color: #61798c;
    font-size: 13px;
}

tbody tr:last-child td {
    border-bottom: 0;
}

td select {
    min-width: 130px;
}

@media (max-width: 1100px) {
    .staff-management>form {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .staff-management form>button {
        grid-column: 1 / -1;
        justify-self: end;
    }
}

@media (max-width: 600px) {
    .staff-management {
        padding: 20px 16px;
    }

    .staff-management>header {
        align-items: flex-start;
    }

    .staff-management h1 {
        font-size: 23px;
    }

    .staff-management>form {
        grid-template-columns: 1fr;
        padding: 20px;
    }

    .staff-management form>button {
        width: 100%;
    }
}

/* 固定欄位與送出按鈕底部對齊 */
.staff-management>form {
    align-items: end !important;
}

.staff-management>form>label {
    margin: 0 !important;
    padding: 0;
    align-self: end;
}

.staff-management>form>label>input {
    height: 42px !important;
    margin: 0 !important;
}

.staff-management>form>button {
    height: 42px !important;
    min-height: 42px;
    margin: 0 !important;
    padding: 0 18px;
    align-self: end !important;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transform: none;
}
</style>
