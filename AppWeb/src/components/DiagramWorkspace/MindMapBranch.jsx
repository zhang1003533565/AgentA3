import { useEffect, useState } from 'react'
import { nodeLabel } from '../../utils/diagramUtils'

export default function MindMapBranch({ node, depth = 1, branchIndex = 0, collapseAll = false, onSelect }) {
  const [collapsed, setCollapsed] = useState(false)

  useEffect(() => {
    setCollapsed(Boolean(collapseAll))
  }, [collapseAll])

  const depthClass = `mind-node--depth-${Math.min(depth, 4)}`

  return (
    <article
      className={`mind-node ${depthClass}${collapsed ? ' mind-node--collapsed' : ''}`}
      style={{ '--branch-index': branchIndex }}
      onClick={() => onSelect?.(node, depth)}
    >
      <div className="mind-node__body">
        <strong>{nodeLabel(node)}</strong>
        {node.children?.length ? (
          <button
            type="button"
            title={collapsed ? '展开子主题' : '收起子主题'}
            onClick={(event) => {
              event.stopPropagation()
              setCollapsed((value) => !value)
            }}
          >
            {collapsed ? '+' : '−'}
          </button>
        ) : null}
      </div>
      {node.children?.length && !collapsed ? (
        <div className="mind-node__children">
          {node.children.map((child, index) => (
            <MindMapBranch
              key={child.id || `${depth}-${index}-${nodeLabel(child)}`}
              node={child}
              depth={depth + 1}
              branchIndex={branchIndex}
              collapseAll={collapseAll}
              onSelect={onSelect}
            />
          ))}
        </div>
      ) : null}
    </article>
  )
}
