import '@ant-design/v5-patch-for-react-19'
import { createRoot } from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import './index.css'
import App from './App.jsx'
import AppErrorBoundary from './components/AppErrorBoundary/AppErrorBoundary.jsx'

// 确保 window.dayjs 存在，Ant Design 内部可能依赖它
window.dayjs = dayjs

// MASTER.md 设计系统主题(淡蓝系):统一 antd 组件(按钮/输入框/卡片/表格)为品牌淡蓝色
const appTheme = {
  token: {
    colorPrimary: '#4A7FAD',
    colorInfo: '#4A7FAD',
    colorLink: '#4A7FAD',
    colorBgLayout: '#F2F7FC',
    colorText: '#0F172A',
    colorTextSecondary: '#4B5563',
    colorBorder: '#E2ECF6',
    colorBorderSecondary: '#E8F0F8',
    borderRadius: 8,
    borderRadiusLG: 12,
    fontFamily: "'Plus Jakarta Sans', 'Avenir Next', 'PingFang SC', 'Microsoft YaHei', sans-serif",
    controlHeight: 38,
  },
  components: {
    Button: {
      fontWeight: 600,
      primaryShadow: 'none',
    },
    Card: {
      headerBg: 'transparent',
    },
    Table: {
      headerBg: '#EEF4FA',
      headerColor: '#4B5563',
      headerSplitColor: '#E2ECF6',
      rowHoverBg: '#F2F7FC',
      borderColor: '#E2ECF6',
    },
  },
}

createRoot(document.getElementById('root')).render(
  <ConfigProvider locale={zhCN} theme={appTheme}>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </ConfigProvider>,
)
