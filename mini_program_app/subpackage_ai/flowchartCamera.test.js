const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

async function loadCameraModule() {
  const source = readFileSync(join(__dirname, 'flowchartCamera.js'), 'utf8')
  const url = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
  return import(url)
}

test('vertical swimlane flow follows both level and lane position', async () => {
  const { flowCameraFollowKey, targetCameraXForNode } = await loadCameraModule()
  const node = { level: 2, laneId: 'backend', cx: 700 }

  assert.equal(flowCameraFollowKey(node, 'VERTICAL', 0), 'y:2')
  assert.equal(flowCameraFollowKey(node, 'VERTICAL', 3), 'xy:2:backend:700')
  assert.equal(flowCameraFollowKey(node, 'HORIZONTAL', 3), 'x:2')

  const centeredX = targetCameraXForNode({
    node,
    direction: 'VERTICAL',
    laneCount: 0,
    viewW: 375,
    canvasW: 900,
    contentW: 900,
    scale: 1
  })
  const swimlaneX = targetCameraXForNode({
    node,
    direction: 'VERTICAL',
    laneCount: 3,
    viewW: 375,
    canvasW: 900,
    contentW: 900,
    scale: 1
  })

  assert.equal(centeredX, 0)
  assert.ok(swimlaneX < centeredX)
})
