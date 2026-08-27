import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, Space, Spin, Switch, Typography, message } from 'antd'
import { EyeOutlined, SaveOutlined, CheckCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { getAiModelConfig, testAiModelConfig, updateAiModelConfig } from '../../../api/aiModelConfig'
import './AiModelConfig.css'

const { Title, Text } = Typography

function AiModelConfig() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [config, setConfig] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const response = await getAiModelConfig()
      const data = response.data || {}
      setConfig(data)
      
      // Set form values - only fill API key if it's not already configured (keep existing on save)
      form.setFieldsValue({
        provider: data.provider || '',
        baseUrl: data.baseUrl || '',
        apiKey: '', // Empty to keep existing when saving
        modelName: data.modelName || '',
        status: data.status || 0
      })
    } catch (error) {
      message.error(error?.message || '加载模型配置失败')
    } finally {
      setLoading(false)
    }
  }, [form])

  useEffect(() => { load() }, [load])

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      
      // Keep existing API key if empty in the form (to preserve what's already stored)
      if (!values.apiKey && config?.apiKeyMasked) {
        values.apiKey = '' // Backend will handle keeping existing
      }
      
      const response = await updateAiModelConfig(values)
      const savedData = response.data || {}
      setConfig(savedData)
      
      message.success('AI 模型配置已保存')
      form.resetFields()
    } catch (error) {
      if (!error?.errorFields) {
        console.error('Save error:', error)
        message.error(error?.message || '保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  const test = async () => {
    setTesting(true)
    try {
      const response = await testAiModelConfig()
      const result = response.data || {}
      
      if (result.success) {
        message.success(result.message || '连通测试成功')
      } else {
        message.error(result.message || '连通测试失败')
      }
    } catch (error) {
      message.error(error?.message || '测试请求失败')
    } finally {
      setTesting(false)
    }
  }

  if (!config) {
    return <div className="ai-model-config-page">加载中...</div>
  }

  return (
    <div className="ai-model-config-page">
      <section className="ai-model-config-toolbar">
        <div>
          <div className="ai-model-config-kicker">AI 模块 / 模型配置</div>
          <Title level={2}>DeepSeek 模型配置</Title>
          <Text type="secondary">配置 DeepSeek AI 模型的连接参数和 API Key</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>刷新</Button>
          <Button icon={<CheckCircleOutlined />} onClick={test} loading={testing}>测试连通性</Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={save} loading={saving}>保存配置</Button>
        </Space>
      </section>

      <Card className="ai-model-config-card" bordered={false}>
        <Spin spinning={loading}>
          <div className="ai-model-config-status">
            <div className="ai-model-config-status-icon">
              <EyeOutlined />
            </div>
            <div>
              <Text strong>服务状态</Text>
              <div className="ai-model-config-tags">
                <Tag className={config.status === 1 ? 'status-active' : 'status-inactive'}>
                  {config.status === 1 ? '已启用' : '已禁用'}
                </Tag>
                {config.updateTime && (
                  <Text type="secondary">最近更新：{formatDateTime(config.updateTime)}</Text>
                )}
              </div>
            </div>
          </div>

          <Alert 
            type="info" 
            showIcon 
            message="密钥由数据库加密存储" 
            description="安全提示：保存后的密钥在页面中脱敏显示。留空不修改当前已配置的密钥值。"
          />

          <Form form={form} layout="vertical" className="ai-model-config-form">
            <Form.Item name="provider" label="供应商名称" rules={[{ required: true, message: '请选择或输入供应商名称' }]}>
              <Input placeholder="例如：deepseek, openai" disabled value="deepseek" />
            </Form.Item>

            <Form.Item name="baseUrl" label="API 接口地址" rules={[{ required: true, message: '请输入 API 地址' }]}>
              <Input placeholder="例如：https://api.deepseek.com" />
              <small className="form-hint">完整 URL，包含协议头</small>
            </Form.Item>

            <Form.Item name="apiKey" label="API Key">
              <Input.Password 
                placeholder={config.apiKeyMasked ? '留空则保留当前密钥' : '请输入您的 API Key'} 
                autoComplete="new-password"
              />
              <small className="form-hint">密钥将以加密方式存储到数据库</small>
            </Form.Item>

            <Form.Item name="modelName" label="模型标识" rules={[{ required: true, message: '请输入模型标识' }]}>
              <Input placeholder="例如：deepseek-chat" />
              <small className="form-hint">用于指定调用的具体模型版本</small>
            </Form.Item>

            <Form.Item name="status" label="启用状态" valuePropName="checked">
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </Form>
        </Spin>
      </Card>
    </div>
  )
}

// Format datetime string for display
function formatDateTime(dateStr) {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (e) {
    return dateStr
  }
}

export default AiModelConfig
