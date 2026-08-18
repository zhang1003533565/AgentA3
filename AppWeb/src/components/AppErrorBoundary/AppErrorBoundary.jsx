import { Component } from 'react'
import { Button, Result } from 'antd'
import './AppErrorBoundary.css'

export default class AppErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('页面渲染异常：', error, info)
  }

  reloadPage = () => {
    window.location.reload()
  }

  render() {
    if (!this.state.error) return this.props.children

    return (
      <main className="app-error-boundary notranslate" translate="no">
        <Result
          status="error"
          title="页面暂时无法显示"
          subTitle="浏览器扩展或页面翻译可能修改了页面结构，请关闭当前站点的自动翻译后重新加载。"
          extra={<Button type="primary" onClick={this.reloadPage}>重新加载页面</Button>}
        />
      </main>
    )
  }
}
