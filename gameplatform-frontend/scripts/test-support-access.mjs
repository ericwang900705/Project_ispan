import { test } from 'node:test'
import assert from 'node:assert/strict'
import { guardPage, homeFor } from '../src/permissions.js'
test('support-only routes keep login reachable and separate member and staff access', () => {
  for (const area of ['member', 'staff']) {
    for (const path of ['/staff/login', '/member/login']) assert.equal(guardPage({path, meta:{}}, null, area), true)
  }
  assert.equal(guardPage({path:'/admin',meta:{support:true}},null),'/staff/login')
  for (const role of ['ADMIN','SUPPORT']) {
    assert.equal(homeFor(role),'/admin')
    assert.equal(guardPage({path:'/admin',meta:{support:true}},{role}),true)
  }
  for (const role of ['MEMBER','PUBLISHER']) {
    assert.equal(guardPage({path:'/admin',meta:{support:true}},{role}),'/member/support')
    assert.equal(guardPage({path:'/tickets',meta:{tickets:true}},{role}),true)
  }
  assert.equal(guardPage({path:'/tickets',meta:{tickets:true}},{role:'REVIEWER'}),'/staff/unavailable')
})
