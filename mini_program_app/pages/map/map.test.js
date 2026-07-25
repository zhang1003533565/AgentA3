const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'map.vue'), 'utf8')

test('category and keyword filters update markers without remounting the map provider', () => {
  assert.doesNotMatch(source, /:key="mapFilterKey"/)
  assert.doesNotMatch(source, /mapFilterKey\s*\(/)
  assert.match(source, /:markers="amapMarkers"/)
  assert.doesNotMatch(source, /name: '全部'/)
  assert.match(source, /selectedFacilityTypes\.includes\(item\.id\)/)
  assert.match(source, /params\.facilityTypes = selected\.join\(','\)/)
  assert.match(source, /const allSelected = selected\.length === this\.categories\.length/)
  assert.match(source, /this\.categories = types\.map\(\(item\) => \(\{ id: Number\(item\.value\), name: item\.label \}\)\)/)
  assert.doesNotMatch(source, /availableTypes/)
  assert.match(source, /const requestId = \+\+this\.mapDataRequestId[\s\S]*if \(!this\.selectedFacilityTypes\.length\)/)
})

test('map exposes a current-location control with permission failure feedback', () => {
  assert.match(source, /aria-label="回到当前位置"/)
  assert.match(source, /@click\.stop="locateToCurrent"/)
  assert.match(source, /fetchCurrentLocation\(\{ centerMap: true, showError: true \}\)/)
  assert.match(source, /uni\.createMapContext\('campusAmap', this\)/)
  assert.match(source, /mapContext\.moveToLocation\(\{ longitude, latitude \}\)/)
  assert.match(source, /无法获取当前位置，请检查定位权限/)
})
