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
          subTitle="页面运行时出现异常，请重新加载；如果问题仍然存在，请检查页面数据或浏览器扩展。"
          extra={<Button type="primary" onClick={this.reloadPage}>重新加载页面</Button>}
        />
      </main>
    )
  }
}
