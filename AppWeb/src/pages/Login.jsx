import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { message } from 'antd'
import { login, register } from '../api/user'
import { setToken, setUserInfo } from '../utils/storage'
import './Login.css'

function Login() {
  const navigate = useNavigate()
  const [isLogin, setIsLogin] = useState(true) // true: 登录, false: 注册
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: ''
  })
  const [loading, setLoading] = useState(false)

  const handleChange = (e) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: value
    }))
  }

  // 处理登录
  const handleLogin = async (e) => {
    e.preventDefault()
    
    if (!formData.username || !formData.password) {
      message.warning('请输入用户名和密码')
      return
    }

    setLoading(true)
    try {
      const res = await login({
        username: formData.username,
        password: formData.password
      })
      
      if (res.code === 200) {
        const { token, username, role, phone } = res.data
        // 保存token和用户信息
        setToken(token)
        setUserInfo({ username, role, phone })
        message.success('登录成功！')
        navigate('/home')
      }
    } catch (error) {
      console.error('登录失败:', error)
    } finally {
      setLoading(false)
    }
  }

  // 处理注册
  const handleRegister = async (e) => {
    e.preventDefault()
    
    // 表单验证
    if (!formData.username || !formData.password) {
      message.warning('请输入用户名和密码')
      return
    }
    
    if (formData.password.length < 6) {
      message.warning('密码长度至少6位')
      return
    }
    
    if (formData.password !== formData.confirmPassword) {
      message.warning('两次输入的密码不一致')
      return
    }

    setLoading(true)
    try {
      const res = await register({
        username: formData.username,
        password: formData.password,
        email: formData.email || undefined,
        phone: formData.phone || undefined
      })
      
      if (res.code === 200) {
        const { token, username, role, phone } = res.data
        // 保存token和用户信息
        setToken(token)
        setUserInfo({ username, role, phone })
        message.success('注册成功！')
        navigate('/home')
      }
    } catch (error) {
      console.error('注册失败:', error)
    } finally {
      setLoading(false)
    }
  }

  // 切换登录/注册
  const toggleMode = () => {
    setIsLogin(!isLogin)
    setFormData({
      username: '',
      password: '',
      confirmPassword: '',
      email: '',
      phone: ''
    })
  }

  return (
    <div className="login-container">
      <div className="login-box">
        <div className="login-header">
          <h1>智慧校园</h1>
          <p>Smart Campus Management System</p>
        </div>

        <form onSubmit={isLogin ? handleLogin : handleRegister} className="login-form">
          <div className="form-group">
            <label>用户名</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="请输入用户名"
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
              placeholder="请输入密码（至少6位）"
              required
              minLength={6}
            />
          </div>

          {/* 注册时显示的额外字段 */}
          {!isLogin && (
            <>
              <div className="form-group">
                <label>确认密码</label>
                <input
                  type="password"
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="请再次输入密码"
                  required
                />
              </div>

              <div className="form-group">
                <label>邮箱（选填）</label>
                <input
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="请输入邮箱"
                />
              </div>

              <div className="form-group">
                <label>手机号（选填）</label>
                <input
                  type="tel"
                  name="phone"
                  value={formData.phone}
                  onChange={handleChange}
                  placeholder="请输入手机号"
                />
              </div>
            </>
          )}

          {isLogin && (
            <div className="form-options">
              <label className="remember-me">
                <input type="checkbox" />
                <span>记住我</span>
              </label>
              <a href="#" className="forgot-password">忘记密码?</a>
            </div>
          )}

          <button 
            type="submit" 
            className="login-btn"
            disabled={loading}
          >
            {loading ? (isLogin ? '登录中...' : '注册中...') : (isLogin ? '登 录' : '注 册')}
          </button>
        </form>

        <div className="login-footer">
          <p>
            {isLogin ? '还没有账号?' : '已有账号?'}
            <a href="#" onClick={(e) => { e.preventDefault(); toggleMode(); }}>
              {isLogin ? '立即注册' : '立即登录'}
            </a>
          </p>
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
