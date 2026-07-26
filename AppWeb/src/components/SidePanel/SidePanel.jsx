import { Drawer } from 'antd'
import './SidePanel.css'

// 通用右侧面板：查看 / 新增 / 编辑统一使用同一尺寸与样式，
// 内容（文字、图片、视频、文件等）由 children 自由渲染
// footer 传入操作按钮（如 取消/保存），不传则不展示底栏
function SidePanel({
  title,
  open,
  onClose,
  loading = false,
  width = 720,
  footer = null,
  className = '',
  children,
  ...rest
}) {
  return (
    <Drawer
      className={`app-side-panel${className ? ` ${className}` : ''}`}
      title={title}
      width={width}
      open={open}
      onClose={onClose}
      loading={loading}
      footer={footer}
      {...rest}
    >
      {children}
    </Drawer>
  )
}

export default SidePanel
