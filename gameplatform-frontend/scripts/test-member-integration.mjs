import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const root=resolve(fileURLToPath(import.meta.url),'../../..')
function content(path){return readFileSync(resolve(root,path),'utf8')}
const src=(path)=>content('gameplatform-frontend/src/'+path)
const java=(path)=>content('gameplatform-backend/src/main/java/gameplatform/'+path)

test('only HERO JwtService signs and verifies tokens; new member controller does not duplicate login routes',()=>{
  const auth=java('support/controller/AuthController.java')
  const ctrl=java('member/controller/MemberAccountController.java')
  const sec=java('support/config/SecurityConfig.java')
  assert.match(auth,/"\/member\/login"/)
  assert.doesNotMatch(ctrl,/@PostMapping\("\/api\/auth\/login"\)/)
  assert.match(sec,/oauth2ResourceServer/)
  assert.match(sec,/\.hasRole\("PUBLISHER"\)/)
  assert.match(sec,/\.hasRole\("ADMIN"\)/)
})

test('all member APIs routes wired, while support and storefront remain unchanged',()=>{
  const c=java('member/controller/MemberAccountController.java')
  const sec=java('support/config/SecurityConfig.java')
  for(const endpoint of ['/api/auth/register','/api/auth/verify','/api/auth/resend-verification','/api/auth/forgot-password','/api/auth/reset-password','/api/members/me','/api/members/profile','/api/members/change-password']) {
    assert.ok(c.includes(`"${endpoint}"`),`Missing endpoint ${endpoint}`)
  }
  for(const endpoint of ['/api/auth/register','/api/auth/verify','/api/auth/resend-verification','/api/auth/forgot-password','/api/auth/reset-password'])
    assert.ok(sec.includes(`"${endpoint}"`),`Missing public auth route ${endpoint}`)
  assert.match(sec,/"\/api\/publisher\/\*\*"/)
  assert.match(sec,/"\/api\/management\/\*\*"/)
  assert.match(sec,/"\/api\/admin\/\*\*"/)
})

test('frontend routes and login links present once, with members protected',()=>{
  const main=src('main.js'), login=src('views/MemberLogin.vue'),guard=src('permissions.js'), app=src('App.vue')
  for(const path of ['register','verify','verification-sent','forgot-password','reset-password','profile']){
    const fragment=`{path:'/member/${path}',component:MemberAccount`
    assert.equal(main.split(fragment).length-1,1,`${path} route must be unique`)
  }
  assert.match(main,/\/member\/profile',component:MemberAccount,meta:\{member:true\}/)
  assert.match(login,/to="\/member\/register"/)
  assert.match(login,/to="\/member\/forgot-password"/)
  assert.match(guard,/publicMemberPage/)
  assert.equal(guard.split('const publicMemberPage =').length-1,1)
  assert.match(app,/to="\/member\/profile"/)
  assert.match(app,/workspace \? homeFor\(session\.user\?\.role\) : '\/'/)
})

test('SQL-safe profile and verification plus password lockout',()=>{
  const service=java('member/service/MemberAccountService.java'), login=java('support/controller/AuthController.java')
  assert.match(service,/INSERT INTO members\(username,email,national_id\)/)
  assert.match(service,/INSERT INTO password_history\(member_id,password_hash\)/)
  assert.match(service,/INSERT INTO member_verifications/)
  assert.match(service,/INSERT INTO password_resets/)
  assert.match(service,/requireMember\(actor\);/)
  assert.match(service,/passwords\.encode/)
  assert.match(login,/failed_login_count=failed_login_count\+1/)
  assert.match(login,/locked_until=NULL/)
})

test('pom has one Mail dependency and no Google/JJWT duplicate; secrets from environment',()=>{
  const pom=content('gameplatform-backend/pom.xml'),p=content('gameplatform-backend/src/main/resources/application.properties')
  assert.equal(pom.split('<artifactId>spring-boot-starter-mail</artifactId>').length-1,1)
  assert.doesNotMatch(pom,/<artifactId>jjwt-/)
  assert.doesNotMatch(pom,/<artifactId>google-api-client/)
  for(const v of ['JWT_SECRET','SMTP_HOST','SMTP_USERNAME','SMTP_PASSWORD','APP_MEMBER_MAIL_FROM'])assert.ok(p.includes('${'+v+':')||p.includes('${'+v+'}'))
  assert.match(p,/JWT_TTL_SECONDS:3600/)
})
