import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { parse, compileScript } from '@vue/compiler-sfc'
import { guardPage, hasPermission, canManageStaff, permissionOptions, permissionCatalog, fixedPermissions } from '../src/permissions.js'
let count = 0
const check = (value) => { assert.ok(value); count++ }
for (const role of ['ADMIN', 'STAFF', 'REVIEWER', 'SUPPORT']) {
    const user = { role, permissions: permissionOptions(role) }
    check(canManageStaff(user) === (role === 'ADMIN'))
    const target = { path: '/admin/staff', fullPath: '/admin/staff', meta: { management: true, staff: true } }
    check((guardPage(target, user) === true) === (role === 'ADMIN'))
}
for (const code of ['MEMBER_SUSPEND', 'PUBLISHER_REVIEW', 'GAME_REVIEW', 'TICKET_HANDLE']) {
    const target = { path: '/protected', fullPath: '/protected', meta: { staff: true, permission: code } }
    check(guardPage(target, { role: 'STAFF', permissions: [code] }) === true)
    check(guardPage(target, { role: 'STAFF', permissions: [] }) !== true)
}
check(!hasPermission({ role: 'STAFF', permissions: ['PERMISSION_MANAGE'] }, 'PERMISSION_MANAGE'))
check(!hasPermission({ role: 'SUPPORT', permissions: ['GAME_REVIEW'] }, 'GAME_REVIEW'))
check(fixedPermissions.REVIEWER.includes('MEMBER_SUSPEND'))
check(!permissionCatalog.some(p => ['MEMBER_MANAGE', 'SUPPORT_CASES'].includes(p.code)))
for (const name of ['StaffManagement.vue', 'AdminMembers.vue', 'AdminPublishers.vue', 'AdminGames.vue', 'StaffLogin.vue', 'StaffRegister.vue', 'Tickets.vue']) {
    const filename = path.resolve('src/views', name)
    const { descriptor, errors } = parse(fs.readFileSync(filename, 'utf8'), { filename })
    assert.deepEqual(errors, [])
    compileScript(descriptor, { id: name, inlineTemplate: true, templateOptions: { compilerOptions: { onError: e => { throw e } } } })
    count++
}
const filename = path.resolve('src/views/StaffManagement.vue')
const view = fs.readFileSync(filename, 'utf8')

assert.ok(
    /method\s*:\s*['"]PUT['"]/.test(view),
    `${filename}：找不到 PUT 請求設定`
)

assert.ok(
    /permissionCodes\s*:/.test(view),
    `${filename}：找不到 permissionCodes 欄位`
)

assert.ok(
    view.includes('/admin/permissions/staff/'),
    `${filename}：找不到 STAFF 權限 API 路徑`
)

count += 3
console.log(`PASS ${count} policy, route, API and Vue template checks`)
