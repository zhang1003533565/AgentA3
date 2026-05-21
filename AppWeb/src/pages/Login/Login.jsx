import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { login } from '../../api/user'
import { setToken, setUserInfo } from '../../utils/storage'
import './Login.css'

function Login() {
  const navigate = useNavigate()
  const [formData, setFormData] = useState({
    username: '',
    password: '',
  })
  const [loading, setLoading] = useState(false)

  const handleChange = (event) => {
    const { name, value } = event.target
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }))
  }

  const handleLogin = async (event) => {
    event.preventDefault()

    if (!formData.username || !formData.password) {
      message.warning('请输入用户名和密码')
      return
    }

    setLoading(true)
    try {
      const res = await login({
        username: formData.username,
        password: formData.password,
      })

      if (res.code === 200) {
        const { token, username, role, phone } = res.data
        setToken(token)
        setUserInfo({ username, role, phone })
        message.success('登录成功')
        navigate('/home')
      }
    } catch (error) {
      console.error('登录失败:', error)
      message.error(error?.message || '登录失败，请检查用户名和密码')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-container">
      <div className="login-box">
        <div className="login-header">
          <h1>智慧校园</h1>
          <p>Campus Administration Console</p>
          <p className="admin-hint">聚合八阶段任务的 Web 工作台</p>
        </div>

        <form onSubmit={handleLogin} className="login-form">
          <div className="form-group">
            <label>用户名</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="请输入管理员账号"
              required
            />
          </div>

          <div className="form-group">
            <label>密码</label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              placeholder="请输入密码"
              required
            />
          </div>

          <button type="submit" className="login-btn" disabled={loading}>
            {loading ? '登录中...' : '登 录'}
          </button>
        </form>

        <div className="login-tips">
          <span>登录提示</span>
          <p>当前页面只使用真实后端登录接口，不再自动进入演示模式。</p>
        </div>
      </div>

      <div className="login-background">
        <div className="bg-circle circle-1"></div>
        <div className="bg-circle circle-2"></div>
        <div className="bg-circle circle-3"></div>
      </div>
    </div>
  )
}

export default Login
