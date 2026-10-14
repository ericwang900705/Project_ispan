import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = filename => readFileSync(new URL(filename, import.meta.url), 'utf8')
const css = read('../src/styles/publisher.css')
const component = read('../src/components/PublisherStoreEditor.vue')
const publisherCss = read('../src/styles/publisher.css')
const headerCss = read('../src/styles/layout.css')

test('即時預覽使用頂部導覽列高度作為 sticky 偏移', () => {
  assert.match(publisherCss, /\.publisher-live-preview\s*\{\s*position:\s*sticky;\s*top:\s*24px/)
  assert.match(component, /publisher-live-preview/)
  assert.match(css, /top:\s*calc\(var\(--publisher-header-height,\s*70px\)\s*\+\s*16px\)/)
  assert.match(headerCss, /\.site-header-shell\s*\{[^}]*position:\s*sticky;[^}]*top:\s*0/s)
})

test('過長預覽允許獨立捲動且窄螢幕取消 sticky', () => {
  assert.match(css, /max-height:\s*calc\(100dvh\s*-\s*var\(--publisher-header-height,\s*70px\)\s*-\s*32px\)/)
  assert.match(css, /overflow-y:\s*auto/)
  assert.match(css, /@media\s*\(max-width:\s*1100px\)\s*\{[^}]*position:\s*static/s)
  assert.match(css, /@media\s*\(max-width:\s*1100px\)\s*\{[^}]*overflow:\s*visible/s)
})
