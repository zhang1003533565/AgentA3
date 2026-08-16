import { Alert, Button, Card, Empty, Result, Space, Spin, Tag, Typography } from 'antd'
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'

const { Text } = Typography

function ExamPaperPreview({ preview, loading, error, dirty, onRefresh }) {
  const unavailable = error?.code === 'LIBREOFFICE_UNAVAILABLE' || /LibreOffice|soffice/i.test(error?.message || '')

  return (
    <Card
      title={<Space><EyeOutlined /><span>真实 Word PDF 预览</span>{dirty && <Tag color="orange">预览已过期</Tag>}</Space>}
      className="exam-paper-card exam-paper-preview-card"
      extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={onRefresh}>{preview ? '重新生成预览' : '生成预览'}</Button>}
    >
      {dirty && preview && <Alert type="warning" showIcon message="题目或页面参数已变化，请重新生成预览后再确认。" />}
      {loading ? (
        <div className="exam-paper-preview-state"><Spin size="large" tip="正在用 Word 模板生成并转换 PDF…" /></div>
      ) : error ? (
        unavailable
          ? <Result status="warning" title="精确预览暂不可用" subTitle={error.message || '服务器未安装或未配置 LibreOffice；仍可稍后重试。'} extra={<Button onClick={onRefresh}>重试</Button>} />
          : <Result status="error" title="预览生成失败" subTitle={error.message || '请检查页面参数后重试。'} extra={<Button onClick={onRefresh}>重试</Button>} />
      ) : preview?.blobUrl ? (
        <div className="exam-paper-preview-shell">
          <div className="exam-paper-preview-meta">
            <Space wrap>
              <Tag color="blue">{preview.pageCount || '—'} 页</Tag>
              <Text type="secondary">有效期至 {dayjs(preview.expiresAt).format('YYYY-MM-DD HH:mm:ss')}</Text>
              <a href={preview.blobUrl} target="_blank" rel="noreferrer">新窗口打开（使用浏览器缩放/翻页）</a>
            </Space>
          </div>
          <object data={preview.blobUrl} type="application/pdf" className="exam-paper-pdf-object" aria-label="试卷 PDF 预览">
            <Empty description="浏览器无法内嵌 PDF"><a href={preview.blobUrl} target="_blank" rel="noreferrer">在新窗口打开预览</a></Empty>
          </object>
        </div>
      ) : (
        <div className="exam-paper-preview-state"><Empty description="请先生成预览，确认 Word 分栏、页眉页脚和装订线效果" /></div>
      )}
    </Card>
  )
}

export default ExamPaperPreview
