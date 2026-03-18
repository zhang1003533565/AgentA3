import { createRoot } from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import './index.css'
import App from './App.jsx'

// 确保 window.dayjs 存在，Ant Design 内部可能依赖它
window.dayjs = dayjs

createRoot(document.getElementById('root')).render(
  <ConfigProvider locale={zhCN}>
    <App />
  </ConfigProvider>,
)
