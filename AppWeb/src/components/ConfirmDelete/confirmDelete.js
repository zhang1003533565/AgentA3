import { Modal } from 'antd'
import './confirmDelete.css'

// 通用删除确认弹窗：全站删除操作统一使用
// 用法：confirmDelete({ title: '删除题目', content: '确定删除该题目吗？', onOk: async () => {…} })
// onOk 支持返回 Promise，等待期间确定按钮自动 loading，完成后关闭
function confirmDelete({
  title = '删除确认',
  content = '确定删除吗？',
  okText = '确定',
  cancelText = '取消',
  onOk,
  onCancel,
} = {}) {
  return Modal.confirm({
    className: 'app-confirm-delete',
    title,
    content,
    okText,
    cancelText,
    okButtonProps: { danger: true },
    onOk,
    onCancel,
  })
}

export default confirmDelete
