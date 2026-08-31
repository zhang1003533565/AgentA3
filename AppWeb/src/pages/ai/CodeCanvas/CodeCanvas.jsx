import { useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CodeOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileTextOutlined,
  HistoryOutlined,
  LoadingOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { generatePreviewPage } from '../../../api/codeCanvas'
import './CodeCanvas.css'

const { Text, Title, Paragraph } = Typography
const { TextArea } = Input

const HISTORY_KEY = 'code-canvas-history'
const MAX_HISTORY = 10
const MAX_CODE_CHARS = 60000

const SAMPLE_CODE = `// 示例：把你的后端实体类 / Controller / 建表 SQL 粘贴到这里
@Entity
@Table(name = "book")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;          // 图书名称
    private String author;        // 作者
    private String isbn;          // ISBN编号
    private String category;      // 分类
    private BigDecimal price;     // 价格
    private Integer stock;        // 库存
    private Integer status;       // 状态 1=上架 0=下架
    private LocalDateTime createTime;
}`

function loadHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function saveHistory(list) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, MAX_HISTORY)))
}

function CodeCanvas() {
  const [code, setCode] = useState('')
  const [requirement, setRequirement] = useState('')
  const [pageTitle, setPageTitle] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [history, setHistory] = useState(loadHistory)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [activeTab, setActiveTab] = useState('preview')

  const canSubmit = useMemo(() => code.trim().length > 0 && !loading, [code, loading])

  const handleGenerate = async () => {
    if (!code.trim()) {
      message.warning('请先输入后端程序代码')
      return
    }
    setLoading(true)
    try {
      const res = await generatePreviewPage({
        code,
        requirement,
        title: pageTitle,
      })
      const data = res?.data
      if (!data?.html) {
        message.error('AI 未返回有效的页面内容，请重试')
        return
      }
      setResult(data)
      setActiveTab('preview')
      message.success('预览页面生成成功')

      const record = {
        id: Date.now(),
        time: new Date().toLocaleString('zh-CN', { hour12: false }),
        title: data.title || '未命名页面',
        code,
        requirement,
        summary: data.summary || '',
        entities: data.entities || '',
        html: data.html,
      }
      const next = [record, ...history.filter((item) => item.title !== record.title)].slice(0, MAX_HISTORY)
      setHistory(next)
      saveHistory(next)
    } catch (error) {
      message.error(error?.message || '生成失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const handleCopyHtml = async () => {
    if (!result?.html) return
    try {
      await navigator.clipboard.writeText(result.html)
      message.success('HTML 源码已复制到剪贴板')
    } catch {
      message.error('复制失败，请手动在"HTML 源码"页签中全选复制')
    }
  }

  const handleDownloadHtml = () => {
    if (!result?.html) return
    const blob = new Blob([result.html], { type: 'text/html;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${result.title || 'preview-page'}.html`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    message.success('HTML 文件已下载')
  }

  const handleLoadHistory = (record) => {
    setResult({ title: record.title, summary: record.summary, entities: record.entities, html: record.html })
    setCode(record.code || '')
    setRequirement(record.requirement || '')
    setActiveTab('preview')
    setHistoryOpen(false)
    message.success(`已载入历史记录：${record.title}`)
  }

  const handleClearHistory = () => {
    setHistory([])
    localStorage.removeItem(HISTORY_KEY)
    message.success('历史记录已清空')
  }

  const previewTabs = [
    {
      key: 'preview',
      label: (
        <span>
          <EyeOutlined /> 页面预览
        </span>
      ),
      children: result?.html ? (
        <iframe
          className="code-canvas-iframe"
          title="code-canvas-preview"
          srcDoc={result.html}
          sandbox="allow-scripts"
        />
      ) : (
        <Empty
          className="code-canvas-empty"
          description={
            <span>
              在左侧粘贴后端程序代码，点击 <RocketOutlined /> 生成预览页面
            </span>
          }
        />
      ),
    },
    {
      key: 'detail',
      label: (
        <span>
          <FileTextOutlined /> 生成说明
        </span>
      ),
      children: result ? (
        <div className="code-canvas-detail">
          <Paragraph>
            <Tag color="blue">页面标题</Tag>
            <Text strong>{result.title}</Text>
          </Paragraph>
          <Paragraph>
            <Tag color="geekblue">模块说明</Tag>
          </Paragraph>
          <Paragraph className="code-canvas-detail-text">{result.summary || '（无）'}</Paragraph>
          <Paragraph>
            <Tag color="cyan">识别到的实体与字段</Tag>
          </Paragraph>
          <pre className="code-canvas-detail-entities">{result.entities || '（无）'}</pre>
        </div>
      ) : (
        <Empty className="code-canvas-empty" description="生成后这里会展示 AI 对代码的分析说明" />
      ),
    },
    {
      key: 'source',
      label: (
        <span>
          <CodeOutlined /> HTML 源码
        </span>
      ),
      children: result ? (
        <div className="code-canvas-source">
          <Space className="code-canvas-source-actions">
            <Button size="small" icon={<CopyOutlined />} onClick={handleCopyHtml}>
              复制源码
            </Button>
            <Button size="small" type="primary" icon={<DownloadOutlined />} onClick={handleDownloadHtml}>
              下载 HTML
            </Button>
          </Space>
          <pre className="code-canvas-source-pre">{result.html}</pre>
        </div>
      ) : (
        <Empty className="code-canvas-empty" description="生成后这里会展示完整 HTML 源码" />
      ),
    },
  ]

  return (
    <div className="code-canvas-page">
      <Card className="code-canvas-header" bordered={false}>
        <Space align="center" size="middle" wrap>
          <div className="code-canvas-logo">
            <CodeOutlined />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              代码画布
            </Title>
            <Text type="secondary">粘贴后端程序代码（实体类 / Controller / 建表 SQL），AI 自动生成可交互的前端管理页面预览</Text>
          </div>
          <Button
            icon={<HistoryOutlined />}
            onClick={() => setHistoryOpen(true)}
            style={{ marginLeft: 'auto' }}
          >
            历史记录{history.length > 0 ? `（${history.length}）` : ''}
          </Button>
        </Space>
      </Card>

      <div className="code-canvas-body">
        <Card
          className="code-canvas-input-card"
          title={
            <Space>
              <CodeOutlined />
              <span>后端代码输入</span>
              <Tag color="green">{code.length}/{MAX_CODE_CHARS}</Tag>
            </Space>
          }
          size="small"
        >
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="支持 Java 实体类、Controller、Service 或 SQL 建表语句；代码中的字段名、注释会用于推断页面列和表单项"
          />
          <TextArea
            className="code-canvas-textarea"
            value={code}
            onChange={(event) => setCode(event.target.value.slice(0, MAX_CODE_CHARS))}
            placeholder={'在此粘贴后端程序代码……\n\n例如：@Entity 实体类、@RestController 接口类、CREATE TABLE 语句等'}
            spellCheck={false}
          />
          <div className="code-canvas-options">
            <Input
              placeholder="页面标题（可选，留空由 AI 推断）"
              value={pageTitle}
              onChange={(event) => setPageTitle(event.target.value)}
              allowClear
              style={{ marginBottom: 8 }}
            />
            <Input
              placeholder="补充要求（可选），如：需要状态筛选、按创建时间倒序、卡片式布局"
              value={requirement}
              onChange={(event) => setRequirement(event.target.value)}
              allowClear
            />
          </div>
          <Space className="code-canvas-actions" wrap>
            <Button
              type="primary"
              size="large"
              icon={loading ? <LoadingOutlined /> : <RocketOutlined />}
              loading={loading}
              disabled={!canSubmit}
              onClick={handleGenerate}
            >
              {loading ? 'AI 生成中，预计 30-90 秒…' : '生成预览页面'}
            </Button>
            <Button
              size="large"
              onClick={() => {
                setCode(SAMPLE_CODE)
                message.info('已填入示例代码，可直接点击生成体验')
              }}
            >
              填入示例
            </Button>
            <Button
              size="large"
              danger
              disabled={loading || !code}
              onClick={() => {
                setCode('')
                setRequirement('')
                setPageTitle('')
              }}
            >
              清空
            </Button>
          </Space>
        </Card>

        <Card
          className="code-canvas-preview-card"
          title={
            <Space>
              <EyeOutlined />
              <span>前端预览</span>
              {result?.title && <Tag color="blue">{result.title}</Tag>}
            </Space>
          }
          size="small"
        >
          <Spin spinning={loading} tip="AI 正在根据代码生成页面…" size="large">
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={previewTabs}
              className="code-canvas-tabs"
            />
          </Spin>
        </Card>
      </div>

      <Drawer
        title={
          <Space>
            <HistoryOutlined />
            <span>历史生成记录</span>
          </Space>
        }
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        width={420}
        extra={
          history.length > 0 ? (
            <Button danger size="small" icon={<DeleteOutlined />} onClick={handleClearHistory}>
              清空记录
            </Button>
          ) : null
        }
      >
        {history.length === 0 ? (
          <Empty description="暂无历史记录，生成记录会保存在本地浏览器" />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {history.map((record) => (
              <Card
                key={record.id}
                size="small"
                hoverable
                onClick={() => handleLoadHistory(record)}
                title={record.title}
              >
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {record.time}
                </Text>
                <Paragraph
                  ellipsis={{ rows: 2 }}
                  style={{ margin: '8px 0 0', fontSize: 13, color: '#595959' }}
                >
                  {record.summary || '（无说明）'}
                </Paragraph>
              </Card>
            ))}
          </Space>
        )}
      </Drawer>
    </div>
  )
}

export default CodeCanvas
