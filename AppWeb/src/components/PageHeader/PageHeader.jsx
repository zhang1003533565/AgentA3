import { Breadcrumb } from 'antd'
import './PageHeader.css'

// 通用页面标题组件：面包屑形式（如 题库管理 / 题库），最后一级高亮
// items：面包屑文字数组；extra：右侧附加内容（按钮等，可选）
function PageHeader({ items = [], extra = null }) {
  const breadcrumbItems = items.map((item, index) => ({
    title: index === items.length - 1
      ? <span className="app-page-header-active">{item}</span>
      : item,
  }))
  return (
    <div className="app-page-header">
      <Breadcrumb items={breadcrumbItems} />
      {extra ? <div className="app-page-header-extra">{extra}</div> : null}
    </div>
  )
}

export default PageHeader
