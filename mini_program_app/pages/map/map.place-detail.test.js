const assert = require('node:assert/strict')
const { existsSync } = require('node:fs')
const { join } = require('node:path')
const { pathToFileURL } = require('node:url')
const test = require('node:test')

const mapPageSource = require('node:fs').readFileSync(join(__dirname, 'map.vue'), 'utf8')

// 点位纯函数已抽取为无依赖 ESM 模块，这里直接按真实模块加载（不再用 vm 执行源码字符串）
let coreModulePromise
function loadMapApiCore() {
  if (!coreModulePromise) {
    coreModulePromise = import(pathToFileURL(join(__dirname, '../../utils/mapPlaceCore.js')).href)
  }
  return coreModulePromise
}

test('native pin icons exist for map markers', () => {
  const iconDir = join(__dirname, '../../static/icons/map')
  ;['pin-teaching.png', 'pin-canteen.png', 'pin-sport.png', 'pin-infra.png', 'pin-active.png', 'pin-cluster.png'].forEach((name) => {
    assert.equal(existsSync(join(iconDir, name)), true, name)
  })
})

test('marker tap loads backend place detail before showing amap-style card', () => {
  assert.match(mapPageSource, /getMarkerDetail/)
  assert.match(mapPageSource, /async selectLocation\(/)
  assert.match(mapPageSource, /fetchPlaceDetail/)
  assert.match(mapPageSource, /await this\.fetchPlaceDetail\(item\.id\)/)
  assert.match(mapPageSource, /includeChildren:\s*false/)
  assert.match(mapPageSource, /openFloorPanel/)
  assert.match(mapPageSource, /getPlaceChildren/)
  assert.match(mapPageSource, /getFloorPlan/)
  assert.match(mapPageSource, /locationDesc/)
  assert.match(mapPageSource, /placeTypeName/)
  assert.match(mapPageSource, /formatWalkSummary/)
  assert.match(mapPageSource, /applySelectedFence/)
  assert.match(mapPageSource, /:polygons="amapPolygons"/)
  assert.match(mapPageSource, /教学楼/)
  assert.match(mapPageSource, /基础设施/)
  assert.match(mapPageSource, /体育场馆/)
  assert.doesNotMatch(mapPageSource, /上传照片/)
})

test('initializeMap shows all places first without forcing user location jump', () => {
  assert.match(mapPageSource, /await this\.loadFacilityTypes\(\)/)
  assert.match(mapPageSource, /await this\.loadMapData\(\{ resetViewport: true \}\)/)
  assert.match(mapPageSource, /fetchCurrentLocation\(\{ centerMap: false \}\)/)
  assert.match(mapPageSource, /selectedFacilityTypes:\s*\[\]/)
})

test('toMapPlaceMarker keeps MapPlaceResponse fields and legacy aliases', async () => {
  const { toMapPlaceMarker } = await loadMapApiCore()
  const marker = toMapPlaceMarker({
    id: 2,
    parentId: null,
    sceneType: 'OTHER',
    placeType: 'ADMIN_BUILDING',
    name: '明志行政楼',
    description: '校园行政办公楼',
    status: 'ENABLED',
    longitude: 114.89852,
    latitude: 40.75662,
    locationDesc: '朝阳校区行政区',
    mapVisible: true,
    imageUrl: null,
    images: [],
  })
  assert.equal(marker.id, 2)
  assert.equal(marker.name, '明志行政楼')
  assert.equal(marker.markerName, '明志行政楼')
  assert.equal(marker.locationDesc, '朝阳校区行政区')
  assert.equal(marker.location, '朝阳校区行政区')
  assert.equal(marker.description, '校园行政办公楼')
  assert.equal(marker.placeType, 'ADMIN_BUILDING')
  assert.equal(marker.facilityTypeName, '基础设施')
  assert.equal(marker.facilityType, 99)
  assert.equal(marker.sceneType, 'OTHER')
})

test('toMapPlaceMarker keeps children floorPlan and indoorPosition from detail', async () => {
  const { toMapPlaceMarker, formatFloorTabLabel, isBuildingPlaceType } = await loadMapApiCore()
  const marker = toMapPlaceMarker({
    id: 10,
    parentId: null,
    sceneType: 'TEACHING',
    placeType: 'TEACHING_BUILDING',
    name: '第一教学楼',
    status: 'ENABLED',
    longitude: 114.89,
    latitude: 40.75,
    locationDesc: '主校区',
    mapVisible: true,
    businessHours: '08:00-22:00',
    updatedAt: '2026-08-15T09:00:00',
    children: [{
      id: 11,
      parentId: 10,
      sceneType: 'TEACHING',
      placeType: 'FLOOR',
      name: '一层',
      status: 'ENABLED',
      floorPlan: { id: 3, imageUrl: 'https://example.test/1f.png' },
      children: [{
        id: 12,
        parentId: 11,
        sceneType: 'TEACHING',
        placeType: 'CLASSROOM',
        name: '101教室',
        description: '多媒体教室',
        indoorPosition: { id: 8, placeId: 12, xRatio: 25.5, yRatio: 40 }
      }]
    }]
  })
  assert.equal(marker.children.length, 1)
  assert.equal(marker.children[0].floorPlan.imageUrl, 'https://example.test/1f.png')
  assert.equal(marker.children[0].children[0].name, '101教室')
  assert.equal(marker.children[0].children[0].indoorPosition.xRatio, 25.5)
  assert.equal(formatFloorTabLabel('一层'), '1F')
  assert.equal(formatFloorTabLabel('地下一层'), 'B1')
  assert.equal(isBuildingPlaceType('TEACHING_BUILDING'), true)
  assert.equal(isBuildingPlaceType('LANDSCAPE'), false)
})

test('compactParams drops undefined keyword that would empty map-places', async () => {
  const { compactParams } = await loadMapApiCore()
  assert.equal(
    JSON.stringify(compactParams({ keyword: undefined, status: 'ENABLED' })),
    JSON.stringify({ status: 'ENABLED' })
  )
  assert.equal(
    JSON.stringify(compactParams({ keyword: 'undefined', status: 'ENABLED' })),
    JSON.stringify({ status: 'ENABLED' })
  )
})
