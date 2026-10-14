import assert from 'node:assert/strict'
import { test } from 'node:test'
import { canSupport, canReview, homeFor, guardPage, senderTypeFor } from '../src/permissions.js'

test('role inheritance and page access matrix', () => {
  const pages = [
    {path:'/admin/overview',meta:{management:true}},
    {path:'/review',meta:{review:true}},
    {path:'/admin',meta:{support:true}},
    {path:'/tickets/1',meta:{tickets:true}}
  ]
  const expected = {ADMIN:[true,true,true,true],REVIEWER:[false,true,false,false],SUPPORT:[false,false,true,true],MEMBER:[false,false,false,true]}
  for (const [role, permissions] of Object.entries(expected)) {
    for(let i=0;i<pages.length;i++) assert.equal(guardPage(pages[i],{role})===true,permissions[i],role+' '+pages[i].path)
    assert.equal(guardPage({path:'/login',meta:{}},{role}),homeFor(role))
  }
  assert.equal(canSupport('ADMIN'),true)
  assert.equal(canReview('ADMIN'),true)
  assert.equal(canSupport('REVIEWER'),false)
  assert.equal(canReview('SUPPORT'),false)
  assert.equal(guardPage(pages[0],null),'/staff/login')
  assert.equal(guardPage(pages[3],null),'/member/login')
  assert.equal(guardPage(pages[3],null,'staff'),'/staff/login')
  assert.equal(guardPage({path:'/member/login',meta:{}},null),true)
  assert.equal(guardPage({path:'/staff/login',meta:{}},null),true)
  assert.equal(senderTypeFor('SUPPORT'),'ADMIN')
  assert.equal(senderTypeFor('ADMIN'),'ADMIN')
  assert.equal(senderTypeFor('MEMBER'),'MEMBER')
})

test('publisher does not inherit platform statistics, support or review access', () => {
  const user={role:'PUBLISHER'}
  assert.equal(homeFor('PUBLISHER'),'/publisher/overview')
  assert.equal(guardPage({path:'/publisher/overview',meta:{publisher:true}},user),true)
  for(const meta of [{management:true},{support:true},{review:true}]) assert.notEqual(guardPage({path:'/protected',meta},user),true)
  assert.equal(guardPage({path:'/tickets',meta:{tickets:true}},user),true, '發行商也是會員，可向客服送出案件')
  for(const role of ['ADMIN','SUPPORT','REVIEWER','MEMBER']) assert.notEqual(guardPage({path:'/publisher/overview',meta:{publisher:true}},{role}),true)
})

test('publisher uses member login and can use member support but not staff pages', () => {
 assert.equal(guardPage({path:'/publisher/games',meta:{publisher:true}},null,'staff'),'/member/login')
 assert.equal(guardPage({path:'/member/support',meta:{member:true}},{role:'PUBLISHER'}),true)
})
