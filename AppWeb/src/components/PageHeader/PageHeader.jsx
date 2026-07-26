import { Breadcrumb } from 'antd'
import { useNavigate } from 'react-router-dom'
import './PageHeader.css'

// 通用页面标题组件：面包屑形式（如 校园设施 / 食堂管理 / 档口管理），最后一级高亮
// items：面包屑数组，每项支持字符串或 { label, path }；带 path 的父级可点击返回
// extra：右侧附加内容（按钮等，可选）
function PageHeader({ items = [], extra = null }) {
  const navigate = useNavigate()

  const breadcrumbItems = items.map((item, index) => {
    const label = typeof item === 'string' ? item : item.label
    const path = typeof item === 'string' ? null : item.path
    const isLast = index === items.length - 1

    // 当前页：高亮，不可点击
    if (isLast) {
      return { title: <span className="app-page-header-active">{label}</span> }
    }
    // 父级：带 path 时可点击返回，用 replace 导航清空中间的页面栈
    if (path) {
      return {
        title: (
          <span
            className="app-page-header-link"
            onClick={() => navigate(path, { replace: true })}
          >
            {label}
          </span>
        ),
      }
    }
    return { title: label }
  })

  return (
    <div className="app-page-header">
      <Breadcrumb items={breadcrumbItems} />
      {extra ? <div className="app-page-header-extra">{extra}</div> : null}
    </div>
  )
}

export default PageHeader
