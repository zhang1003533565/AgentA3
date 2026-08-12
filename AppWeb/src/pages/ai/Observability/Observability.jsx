import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, Space, Spin, Switch, Tag, Typography, message } from 'antd'
import { EyeOutlined, ReloadOutlined, SaveOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { getLangfuseConfig, testLangfuseConfig, updateLangfuseConfig } from '../../../api/langfuse'
import './Observability.css'

const { Title, Text } = Typography

function Observability() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [config, setConfig] = useState({})

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getLangfuseConfig()
      const data = response.data || {}
      setConfig(data)
      form.setFieldsValue({ enabled: Boolean(data.enabled), baseUrl: data.baseUrl || '', publicKey: '', secretKey: '' })
    } catch (error) {
      message.error(error?.message || '加载观测配置失败')
    } finally {
      setLoading(false)
    }
  }, [form])

  useEffect(() => { load() }, [load])

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const response = await updateLangfuseConfig(values)
      const data = response.data || {}
      setConfig(data)
      form.setFieldsValue({ publicKey: '', secretKey: '' })
      message.success('Langfuse 观测配置已保存')
    } catch (error) {
      if (!error?.errorFields) message.error(error?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const test = async () => {
    setTesting(true)
    try {
      const response = await testLangfuseConfig()
      const result = response.data || {}
      if (result.success) message.success(result.detail || '数据库 Langfuse 配置认证成功')
      else message.error(result.detail || '数据库 Langfuse 配置认证失败')
    } catch (error) {
      message.error(error?.message || '配置测试失败')
    } finally {
      setTesting(false)
    }
  }

  return <div className="observability-page">
    <section className="observability-toolbar">
      <div><div className="observability-kicker">AI 模块 / 观测配置</div><Title level={2}>Langfuse 观测配置</Title><Text type="secondary">统一记录 AI 对话、RAG 和智能体调用，不影响既有业务流程。</Text></div>
      <Space><Button icon={<ReloadOutlined />} onClick={load} loading={loading}>刷新</Button><Button onClick={test} loading={testing}>测试数据库配置</Button><Button type="primary" icon={<SaveOutlined />} onClick={save} loading={saving}>保存配置</Button></Space>
    </section>
    <Card className="observability-card" bordered={false}><Spin spinning={loading}>
      <div className="observability-status"><div className="observability-status-icon"><EyeOutlined /></div><div><Text strong>运行状态</Text><div><Tag color={config.enabled ? 'success' : 'default'}>{config.enabled ? '已启用' : '未启用'}</Tag>{config.updateTime && <Text type="secondary">最近更新：{config.updateTime}</Text>}</div></div></div>
      <Alert type="info" showIcon message="密钥由数据库加密保存" description="保存后页面只展示掩码。留空密钥字段将保留当前已配置的值；启用前必须填写服务地址和两类密钥。" />
      <Form form={form} layout="vertical" className="observability-form">
        <Form.Item name="enabled" label="启用 Langfuse" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
        <Form.Item name="baseUrl" label="Langfuse 服务地址" rules={[{ type: 'url', message: '请输入有效的 HTTP(S) 地址' }]}><Input placeholder="例如：http://localhost:3000" /></Form.Item>
        <div className="observability-key-grid">
          <Form.Item name="publicKey" label={<>Public Key {config.publicKeyConfigured && <Text type="secondary">（当前：{config.publicKeyMasked}）</Text>}</>}><Input.Password placeholder={config.publicKeyConfigured ? '留空则保留当前密钥' : 'pk-lf-...'} autoComplete="new-password" /></Form.Item>
          <Form.Item name="secretKey" label={<>Secret Key {config.secretKeyConfigured && <Text type="secondary">（当前：{config.secretKeyMasked}）</Text>}</>}><Input.Password placeholder={config.secretKeyConfigured ? '留空则保留当前密钥' : 'sk-lf-...'} autoComplete="new-password" /></Form.Item>
        </div>
      </Form>
      <div className="observability-note"><SafetyCertificateOutlined /><span>生产环境请设置 <code>APP_CREDENTIAL_ENCRYPTION_KEY</code>，用于加密数据库中的 Langfuse 密钥。</span></div>
    </Spin></Card>
  </div>
}

export default Observability
