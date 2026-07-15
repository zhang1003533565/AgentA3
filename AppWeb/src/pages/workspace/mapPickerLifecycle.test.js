import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./WorkspacePage.jsx', import.meta.url), 'utf8')

test('地图选点只在 Drawer 生命周期初始化地图实例', () => {
  const lifecycle = source.match(
    /\/\/ 地图选点 Drawer 内地图初始化[\s\S]*?\}, \[clearAmapOverlays, mapPickerAmapReady, mapPickerOpen\]\)/,
  )

  assert.ok(lifecycle, '地图初始化 Effect 应只依赖 Drawer 开关与 SDK 就绪状态')
  assert.doesNotMatch(lifecycle[0], /mapPickerLat, mapPickerLng|mapPickerLng, mapPickerLat/)
})

test('坐标变化由独立 Effect 更新标记而不重建地图', () => {
  assert.match(source, /\/\/ 坐标变化只同步选点标记，不重建地图实例/)
  assert.match(
    source,
    /\}, \[clearAmapOverlays, mapPickerAmapReady, mapPickerFacilityName, mapPickerLat, mapPickerLng, mapPickerOpen\]\)/,
  )
})
