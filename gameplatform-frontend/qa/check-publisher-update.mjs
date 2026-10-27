// 空回應與舊通知路徑的可重現回歸測試。
import assert from 'node:assert/strict'
import { readJsonResponse } from '../src/response-body.js'
import { notificationTarget } from '../src/notification-target.js'
let checks = 0
const check = (actual, expected) => { assert.deepEqual(actual, expected); checks++ }
for (const status of [200, 201, 204, 205]) check(await readJsonResponse(new Response(status < 204 ? '' : null, { status })), null)
check(await readJsonResponse(new Response('  \n ')), null)
check(await readJsonResponse(new Response('{"success":true}')), { success: true })
await assert.rejects(() => readJsonResponse(new Response('{bad}'))); checks++
check(notificationTarget({ event_type: 'CHAT_REPLY', target_path: '/member/support', body: '聊天室 #12 有新訊息' }, 'MEMBER'), '/member/support?room=12')
check(notificationTarget({ event_type: 'CHAT_MESSAGE', target_path: '/admin', body: '聊天室 #12 有會員回覆' }, 'SUPPORT'), '/admin/chats?room=12')
check(notificationTarget({ event_type: 'ROOM_CLOSED', target_path: '/member/support?room=22' }, 'PUBLISHER'), '/member/support?room=22')
check(notificationTarget({ event_type: 'TICKET_REPLY', target_path: '/tickets/8' }, 'MEMBER'), '/member/support/8')
check(notificationTarget({ event_type: 'TICKET_MESSAGE', target_path: '/tickets/8' }, 'SUPPORT'), '/tickets/8')
check(notificationTarget({ event_type: 'GAME_REJECTED', target_path: '/publisher/games', related_game_id: 4 }, 'PUBLISHER'), '/publisher/games?mode=history&game=4')
check(notificationTarget({ event_type: 'GAME_SUBMITTED', target_path: '/admin/games?game=4' }, 'REVIEWER'), '/admin/games?game=4')
for (const path of ['//other.invalid', 'https://other.invalid', '/\\other.invalid']) { assert.throws(() => notificationTarget({ target_path: path }, 'MEMBER')); checks++ }
console.log(`PASS ${checks} response and notification routing checks`)
