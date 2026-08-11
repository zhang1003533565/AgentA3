const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

async function loadLayoutModule() {
  const source = readFileSync(join(__dirname, 'flowchartLayout.js'), 'utf8')
  const url = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
  return import(url)
}

function sampleChart(direction) {
  return {
    resolvedLayoutDirection: direction,
    nodes: [
      { id: 'start', name: '开始', type: 'start' },
      { id: 'step', name: '处理', type: 'process' },
      { id: 'end', name: '结束', type: 'end' }
    ],
    edges: [
      { source: 'start', target: 'step' },
      { source: 'step', target: 'end' }
    ]
  }
}

test('flowchart layout changes coordinates for vertical and horizontal directions', async () => {
  const { layoutFlowchart } = await loadLayoutModule()

  const vertical = layoutFlowchart(sampleChart('VERTICAL'))
  const horizontal = layoutFlowchart(sampleChart('HORIZONTAL'))
  const vStart = vertical.nodes.find(node => node.id === 'start')
  const vStep = vertical.nodes.find(node => node.id === 'step')
  const hStart = horizontal.nodes.find(node => node.id === 'start')
  const hStep = horizontal.nodes.find(node => node.id === 'step')

  assert.equal(vertical.direction, 'VERTICAL')
  assert.equal(horizontal.direction, 'HORIZONTAL')
  assert.ok(vStep.cy > vStart.cy)
  assert.equal(vertical.edges[0].kind, 'v')
  assert.ok(hStep.cx > hStart.cx)
  assert.equal(horizontal.edges[0].kind, 'h')
})

test('flowchart direction is wired through generation page, API, animation, and viewer', () => {
  const generatePage = readFileSync(join(__dirname, 'flowchartGenerate/flowchartGenerate.vue'), 'utf8')
  const api = readFileSync(join(__dirname, '../api/aiDiagram.js'), 'utf8')
  const generatingPage = readFileSync(join(__dirname, 'flowchartGenerating/flowchartGenerating.vue'), 'utf8')
  const viewerPage = readFileSync(join(__dirname, 'flowchartViewer/flowchartViewer.vue'), 'utf8')

  assert.match(generatePage, /const selectedDirection = ref\('VERTICAL'\)/)
  assert.match(generatePage, /layoutDirection: selectedDirection\.value/)
  assert.match(generatePage, /显示方向/)
  assert.match(api, /requestedLayoutDirection: normalizeFlowLayoutDirection/)
  assert.match(api, /resolvedLayoutDirection: normalizeFlowLayoutDirection/)
  assert.match(generatingPage, /state\.laid\?\.direction/)
  assert.match(generatingPage, /正在规划横向流程路径/)
  assert.match(viewerPage, /directionTip/)
})
