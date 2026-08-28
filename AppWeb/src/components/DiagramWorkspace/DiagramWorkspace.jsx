import { useMemo, useRef, useState } from 'react'
import MindMapBranch from './MindMapBranch'
import { nodeLabel, toMindTree } from '../../utils/diagramUtils'
import './DiagramWorkspace.css'

const LANE_PALETTE = [
  { fill: '#f4f8fc', stroke: '#c9dbea', label: '#466b89' },
  { fill: '#f5faf7', stroke: '#c9e4d4', label: '#42775a' },
  { fill: '#faf8f4', stroke: '#eadbbc', label: '#82663a' },
  { fill: '#f8f6fc', stroke: '#dcd2ef', label: '#67568e' },
]

function flowShape(node) {
  const type = String(node.type || '').toLowerCase()
  if (type.includes('decision') || type.includes('condition')) return 'decision'
  if (type.includes('start') || type.includes('end')) return 'terminal'
  return 'process'
}

function flowPath(edge, isHorizontal) {
  if (isHorizontal) {
    const startX = edge.from.x + 74
    const endX = edge.to.x - 74
    const middleX = (startX + endX) / 2
    return `M ${startX} ${edge.from.y} H ${middleX} V ${edge.to.y} H ${endX}`
  }
  const startY = edge.from.y + 28
  const endY = edge.to.y - 28
  const middleY = (startY + endY) / 2
  return `M ${edge.from.x} ${startY} V ${middleY} H ${edge.to.x} V ${endY}`
}

function flowEdgeLabelPosition(edge, isHorizontal) {
  return isHorizontal
    ? { x: (edge.from.x + edge.to.x) / 2, y: (edge.from.y + edge.to.y) / 2 - 8 }
    : { x: (edge.from.x + edge.to.x) / 2 + 9, y: (edge.from.y + edge.to.y) / 2 - 5 }
}

function flowLabelLines(node) {
  const label = nodeLabel(node)
  const limit = flowShape(node) === 'decision' ? 7 : 11
  const lines = []
  for (let index = 0; index < label.length; index += limit) lines.push(label.slice(index, index + limit))
  return lines.slice(0, 2)
}

function architectureNodeLabel(node) {
  return node.name || node.label || node.title || '服务模块'
}

export default function DiagramWorkspace({ type, result }) {
  const flowSvgRef = useRef(null)
  const [scale, setScale] = useState(1)
  const [selectedNode, setSelectedNode] = useState(null)
  const [collapseAllMindNodes, setCollapseAllMindNodes] = useState(false)

  const title = result?.title || 'AI 图谱'
  const summary = result?.fileSummary || result?.subtitle || result?.description || ''
  const flowNodes = result?.nodes?.length ? result.nodes : []
  const lanes = result?.lanes || []
  const isHorizontal = String(result?.resolvedLayoutDirection || result?.layoutDirection || '').toUpperCase().includes('HORIZONTAL')
  const hasLanes = lanes.length > 0 && !String(result?.resolvedSwimlaneMode || '').includes('NONE')
  const architectureLayers = result?.layers || []
  const mindTree = useMemo(() => toMindTree(result?.nodes || [], title), [result?.nodes, title])
  const mindBranches = mindTree.children || []

  const flowLaneFor = (node) => {
    const candidates = [node.laneId, node.lane, node.role, node.department].filter(Boolean).map(String)
    return lanes.find((lane) => candidates.includes(String(lane.id))
      || candidates.includes(String(lane.label))
      || lane.nodes?.map(String).includes(String(node.id)))
  }

  const flowLaneIndex = (node, fallbackIndex) => {
    if (!hasLanes) return 0
    const lane = flowLaneFor(node)
    return lane ? Math.max(0, lanes.indexOf(lane)) : fallbackIndex % lanes.length
  }

  const flowPositions = useMemo(() => flowNodes.map((node, index) => {
    const laneIndex = flowLaneIndex(node, index)
    if (isHorizontal) {
      const column = hasLanes ? Math.floor(index / lanes.length) : index
      return { ...node, x: 120 + column * 190, y: hasLanes ? 74 + laneIndex * 78 : 190 }
    }
    return { ...node, x: hasLanes ? 101 + laneIndex * 150 : 330, y: 92 + index * 116 }
  }), [flowNodes, hasLanes, isHorizontal, lanes.length])

  const flowEdges = useMemo(() => {
    const positions = new Map(flowPositions.map((node) => [String(node.id), node]))
    const generated = result?.edges?.map((edge) => ({
      ...edge,
      from: positions.get(String(edge.source)),
      to: positions.get(String(edge.target)),
    })).filter((edge) => edge.from && edge.to) || []
    return generated.length
      ? generated
      : flowPositions.slice(1).map((node, index) => ({ from: flowPositions[index], to: node, label: '' }))
  }, [flowPositions, result?.edges])

  const flowViewBox = useMemo(() => {
    if (isHorizontal) {
      const columns = hasLanes ? Math.ceil(flowNodes.length / lanes.length) : flowNodes.length
      return `0 0 ${Math.max(760, 220 + columns * 190)} ${Math.max(430, 108 + lanes.length * 78)}`
    }
    return `0 0 ${Math.max(720, 70 + lanes.length * 150)} ${Math.max(760, 150 + flowNodes.length * 116)}`
  }, [flowNodes.length, hasLanes, isHorizontal, lanes.length])

  const zoom = (delta) => setScale((value) => Math.min(1.5, Math.max(0.65, Number((value + delta).toFixed(2)))))
  const reset = () => {
    setScale(1)
    setSelectedNode(null)
  }

  const selectNode = (node, kind) => setSelectedNode({ ...node, label: nodeLabel(node), kind })
  const selectMindNode = (node, depth) => selectNode(node, depth === 1 ? '一级主题' : `${depth} 级主题`)

  return (
    <section className={`diagram-workspace diagram-workspace--${type}`}>
      <header className="diagram-workspace__head">
        <div>
          <strong>{title}</strong>
          <span>{summary || '已生成 · 可缩放、查看节点详情'}</span>
        </div>
        <div className="diagram-workspace__actions">
          <button type="button" title="缩小" onClick={() => zoom(-0.1)}>−</button>
          <button type="button" title="重置缩放" onClick={reset}>{Math.round(scale * 100)}%</button>
          <button type="button" title="放大" onClick={() => zoom(0.1)}>＋</button>
          {type === 'mind_map' ? (
            <button
              type="button"
              className="diagram-workspace__secondary"
              onClick={() => setCollapseAllMindNodes((value) => !value)}
            >
              {collapseAllMindNodes ? '展开全部' : '收起全部'}
            </button>
          ) : null}
        </div>
      </header>
      <div className="diagram-workspace__viewport">
        <div className="diagram-workspace__stage" style={{ transform: `scale(${scale})` }}>
          {type === 'flowchart' ? (
            <svg ref={flowSvgRef} className="flow-canvas" viewBox={flowViewBox} role="img" aria-label="生成的流程图">
              <defs>
                <marker id="flow-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                  <path d="M0,0 L8,4 L0,8 Z" fill="#50708e" />
                </marker>
              </defs>
              {hasLanes ? (
                <g className="flow-lanes">
                  {lanes.map((lane, index) => (
                    <g
                      key={lane.id || lane.label || index}
                      transform={isHorizontal ? `translate(0, ${42 + index * 78})` : `translate(${35 + index * 150}, 0)`}
                    >
                      {isHorizontal ? (
                        <rect
                          x="35"
                          y="0"
                          width={Math.max(680, 160 + Math.ceil(flowNodes.length / lanes.length) * 190)}
                          height="64"
                          rx="10"
                          fill={LANE_PALETTE[index % LANE_PALETTE.length].fill}
                          stroke={LANE_PALETTE[index % LANE_PALETTE.length].stroke}
                          strokeWidth="1.5"
                        />
                      ) : (
                        <rect
                          x="0"
                          y="38"
                          width="132"
                          height={Math.max(700, 110 + flowNodes.length * 116)}
                          rx="10"
                          fill={LANE_PALETTE[index % LANE_PALETTE.length].fill}
                          stroke={LANE_PALETTE[index % LANE_PALETTE.length].stroke}
                          strokeWidth="1.5"
                        />
                      )}
                      <text
                        x={isHorizontal ? 48 : 14}
                        y={isHorizontal ? 24 : 62}
                        fill={LANE_PALETTE[index % LANE_PALETTE.length].label}
                        className="flow-lane-label"
                      >
                        {lane.label || `泳道 ${index + 1}`}
                      </text>
                    </g>
                  ))}
                </g>
              ) : null}
              {flowEdges.map((edge) => (
                <g key={`${edge.from.id}-${edge.to.id}-${edge.label}`}>
                  <path d={flowPath(edge, isHorizontal)} fill="none" stroke="#50708e" strokeWidth="2" markerEnd="url(#flow-arrow)" />
                  {edge.label ? (
                    <text {...flowEdgeLabelPosition(edge, isHorizontal)} textAnchor="middle" className="flow-edge-label">
                      {edge.label}
                    </text>
                  ) : null}
                </g>
              ))}
              {flowPositions.map((node) => {
                const shape = flowShape(node)
                const labelLines = flowLabelLines(node)
                return (
                  <g
                    key={node.id}
                    transform={`translate(${node.x}, ${node.y})`}
                    className="flow-node-group"
                    onClick={() => selectNode({ ...node, laneLabel: flowLaneFor(node)?.label }, '流程节点')}
                  >
                    {shape === 'process' ? (
                      <rect x="-74" y="-27" width="148" height="54" rx="10" className="flow-node flow-node--process" />
                    ) : shape === 'terminal' ? (
                      <rect x="-64" y="-23" width="128" height="46" rx="23" className="flow-node flow-node--terminal" />
                    ) : (
                      <rect x="-33" y="-33" width="66" height="66" transform="rotate(45)" className="flow-node flow-node--decision" />
                    )}
                    <text textAnchor="middle" className="flow-node-label">
                      {labelLines.map((line, lineIndex) => (
                        <tspan key={lineIndex} x="0" dy={lineIndex === 0 ? -(labelLines.length - 1) * 8 : 16}>
                          {line}
                        </tspan>
                      ))}
                    </text>
                  </g>
                )
              })}
            </svg>
          ) : type === 'architecture' ? (
            <div className="architecture-canvas">
              <div className="architecture-canvas__caption">
                {result.systemType || 'WEB'} · {result.resolvedRelationMode || result.requestedRelationMode || '自动分析关系'}
              </div>
              {architectureLayers.map((layer) => (
                <div key={layer.key || layer.name} className="architecture-layer">
                  <div className="architecture-layer__name">{layer.name}</div>
                  <div className="architecture-layer__nodes">
                    {layer.groups?.length ? layer.groups.map((group) => (
                      <div key={group.id || group.name} className="architecture-group">
                        <b>{group.name}</b>
                        {group.nodes?.map((node) => (
                          <article
                            key={node.id || node.name}
                            className="architecture-node"
                            onClick={() => selectNode(node, `${layer.name} / ${group.name}`)}
                          >
                            <strong>{architectureNodeLabel(node)}</strong>
                            {node.description ? <span>{node.description}</span> : null}
                          </article>
                        ))}
                      </div>
                    )) : layer.nodes?.map((node) => (
                      <article
                        key={node.id || node.name}
                        className="architecture-node"
                        onClick={() => selectNode(node, layer.name)}
                      >
                        <strong>{architectureNodeLabel(node)}</strong>
                        {node.description ? <span>{node.description}</span> : null}
                      </article>
                    ))}
                    {!layer.nodes?.length && !layer.groups?.length ? (
                      <span className="architecture-empty">由 AI 自动补全</span>
                    ) : null}
                  </div>
                </div>
              ))}
              {result.thirdParty?.length ? (
                <aside className="architecture-third-party">
                  <strong>第三方服务</strong>
                  {result.thirdParty.map((item) => (
                    <span key={item.name || item}>{item.name || item}</span>
                  ))}
                </aside>
              ) : null}
              {result.features?.length ? (
                <footer className="architecture-features">
                  {result.features.map((feature) => (
                    <span key={feature}>{feature}</span>
                  ))}
                </footer>
              ) : null}
            </div>
          ) : (
            <div className="mind-canvas">
              <button type="button" className="mind-root" onClick={() => selectNode(mindTree, '中心主题')}>
                {mindTree.label}
              </button>
              <div className="mind-branches">
                {mindBranches.map((branch, index) => (
                  <MindMapBranch
                    key={branch.id || index}
                    node={branch}
                    branchIndex={index}
                    collapseAll={collapseAllMindNodes}
                    onSelect={selectMindNode}
                  />
                ))}
              </div>
              {!mindBranches.length ? <p className="mind-empty">本次导图暂无可展示节点</p> : null}
            </div>
          )}
        </div>
      </div>
      {selectedNode ? (
        <aside className="diagram-inspector">
          <div>
            <span>{selectedNode.kind}</span>
            <strong>{selectedNode.label}</strong>
            {selectedNode.description || selectedNode.content ? (
              <p>{selectedNode.description || selectedNode.content}</p>
            ) : null}
            {selectedNode.laneLabel ? <small>所属泳道：{selectedNode.laneLabel}</small> : null}
          </div>
          <button type="button" onClick={() => setSelectedNode(null)}>×</button>
        </aside>
      ) : null}
    </section>
  )
}
