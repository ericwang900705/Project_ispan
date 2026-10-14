import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = path => readFileSync(new URL(`../${path}`, import.meta.url),'utf8');

test('publisher HERO logo returns to store; staff logo retains backend home', () => {
 const app=read('src/App.vue');
 assert.match(app, /:to="workspace \? homeFor\(session\.user\?\.role\) : '\/'"/);
 assert.match(app, /<nav v-if="!workspace" class="integrated-member-nav"/);
 assert.doesNotMatch(app, /v-else-if="session\.user\?\.role === 'PUBLISHER'"/);
});

test('compact layout retains direct routes and sends nonessential links into a labeled menu',()=>{
 const app=read('src/App.vue');
 const css=read('src/styles/layout.css');
 assert.match(app,/visibleSiteLinks/);
 assert.match(app,/overflowSiteLinks/);
 assert.match(app,/site-more-menu/);
 assert.match(app,/siteMoreOpen = false/);
 assert.match(css,/@media \(max-width: 779px\)/);
 assert.match(css,/integrated-member-nav > a \.ui-icon/);
 assert.doesNotMatch(app,/mobile-site-more|mobileSiteMenuOpen/);
 assert.match(app,/watch\(\(\)=>route\.fullPath/);
});

test('every store header matches the tickets case-tracking pattern',()=>{
 const global=read('src/styles/theme.css');
 assert.match(global,/hero-case-heading__eyebrow/);
 assert.match(global,/font-size: clamp\(27px/);
 assert.doesNotMatch(global,/border-left: 4px/);
 for(const page of ['Games','MemberGames','PointStore','ShoppingCart']) {
  const component=read(`src/cart/views/${page}.vue`);
  assert.match(component,/class="[^"]*hero-case-heading/);
  assert.match(component,/hero-case-heading__eyebrow/);
  assert.match(component,/hero-page-heading__title/);
 }
});
