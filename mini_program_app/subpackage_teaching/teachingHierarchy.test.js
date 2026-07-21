const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const listSource = readFileSync(join(__dirname, 'buildingList/buildingList.vue'), 'utf8')
const detailSource = readFileSync(join(__dirname, 'buildingDetail/buildingDetail.vue'), 'utf8')
const apiSource = readFileSync(join(__dirname, '../api/teaching.js'), 'utf8')

test('teaching pages use backend buildings and classroom children without mock facilities', () => {
  assert.match(listSource, /getTeachingBuildings\(\)/)
  assert.match(detailSource, /getTeachingBuilding\(id\)/)
  assert.match(detailSource, /building\.classrooms/)
  assert.doesNotMatch(`${listSource}\n${detailSource}`, /mockData|教学楼A栋|picsum\.photos/)
  assert.match(apiSource, /\/api\/v1\/teaching\/buildings/)
})
