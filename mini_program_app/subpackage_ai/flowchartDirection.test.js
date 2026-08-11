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

test('flowchart decision branch labels stay near decision exits and outside target nodes', async () => {
  const { layoutFlowchart, FLOW_NODE_H } = await loadLayoutModule()
  const chart = {
    resolvedLayoutDirection: 'VERTICAL',
    nodes: [
      { id: 'check', name: '验证码校验', type: 'decision' },
      { id: 'ok', name: '验证验证码', type: 'process' },
      { id: 'fail', name: '返回错误', type: 'end' }
    ],
    edges: [
      { source: 'check', target: 'ok', label: '是', type: 'branch' },
      { source: 'check', target: 'fail', label: '否', type: 'branch' }
    ]
  }

  const laid = layoutFlowchart(chart)
  const decision = laid.nodes.find(node => node.id === 'check')
  const fail = laid.nodes.find(node => node.id === 'fail')
  const noEdge = laid.edges.find(edge => edge.label === '否')
  const yesEdge = laid.edges.find(edge => edge.label === '是')

  assert.equal(noEdge.kind, 'branch')
  assert.ok(Math.abs(noEdge.labelX - decision.cx) > 40)
  assert.ok(noEdge.labelY < fail.cy - FLOW_NODE_H / 2)
  assert.ok(yesEdge.labelY < fail.cy - FLOW_NODE_H / 2)
  assert.match(noEdge.path, / C /)
})

test('flowchart direction is wired through generation page, API, animation, and viewer', () => {
  const generatePage = readFileSync(join(__dirname, 'flowchartGenerate/flowchartGenerate.vue'), 'utf8')
  const api = readFileSync(join(__dirname, '../api/aiDiagram.js'), 'utf8')
  const generatingPage = readFileSync(join(__dirname, 'flowchartGenerating/flowchartGenerating.vue'), 'utf8')
  const viewerPage = readFileSync(join(__dirname, 'flowchartViewer/flowchartViewer.vue'), 'utf8')

  assert.match(generatePage, /const selectedDirection = ref\('VERTICAL'\)/)
  assert.match(generatePage, /const selectedScene = ref\('AUTO'\)/)
  assert.match(generatePage, /\{ key: 'AUTO', label: '自动' \}/)
  assert.match(generatePage, /layoutDirection: selectedDirection\.value/)
  assert.match(generatePage, /显示方向/)
  assert.match(api, /requestedLayoutDirection: normalizeFlowLayoutDirection/)
  assert.match(api, /resolvedLayoutDirection: normalizeFlowLayoutDirection/)
  assert.match(generatingPage, /state\.laid\?\.direction/)
  assert.match(generatingPage, /targetCameraXForNode/)
  assert.match(generatingPage, /flowCameraFollowKey/)
  assert.match(generatingPage, /正在规划横向流程路径/)
  assert.match(viewerPage, /directionTip/)
})
