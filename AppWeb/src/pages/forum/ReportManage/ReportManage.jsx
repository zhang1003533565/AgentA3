import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Form, Input, message, Modal, Select, Space, Table, Tag, Timeline } from 'antd'
import { CheckCircleOutlined, DeleteOutlined, EyeOutlined, SearchOutlined, StopOutlined, WarningOutlined, FileTextOutlined } from '@ant-design/icons'
import { getReportList, getReportLogs, getReportStatistics, handleReport, getForumStatistics } from '../../../api/forum'
import './ReportManage.css'

const { TextArea } = Input

const targetTypeMap = { 1: { text: '帖子', color: 'blue' }, 2: { text: '评论', color: 'green' } }
const statusMap = { 0: { text: '待处理', color: 'orange' }, 1: { text: '已处理', color: 'green' }, 2: { text: '已忽略', color: 'default' } }
const actionMap = { CREATE_REPORT: '提交举报', IGNORE: '忽略举报', DELETE_CONTENT: '删除内容' }

function ReportManage() {
  const [reports, setReports] = useState([])
  const [stats, setStats] = useState(null)
  const [forumStats, setForumStats] = useState(null)
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(false)
  const [handleOpen, setHandleOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [currentReport, setCurrentReport] = useState(null)
  const [searchForm] = Form.useForm()
  const [handleForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  const fetchReports = async (params = {}) => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const res = await getReportList({ page: params.page ?? pagination.current, size: params.size ?? pagination.pageSize, ...values, ...params })
      const data = res?.data || {}
      setReports(data.records || [])
      setPagination((prev) => ({ ...prev, current: data.page || params.page || prev.current, pageSize: data.size || params.size || prev.pageSize, total: data.total || 0 }))
    } catch (error) { message.error(error?.message || '获取举报列表失败') }
    finally { setLoading(false) }
  }

  const fetchStats = async () => {
    try {
      const res = await getReportStatistics()
      setStats(res?.data || null)
    } catch (error) { setStats(null) }
  }

  const fetchForumStats = async () => {
    try {
      const res = await getForumStatistics()
      if (res.code === 200) setForumStats(res.data)
    } catch (e) { /* ignore */ }
  }

  useEffect(() => { fetchReports({ page: 1 }); fetchStats(); fetchForumStats() }, [])

  const openDetail = async (record) => {
    setCurrentReport(record)
    setDetailOpen(true)
    try {
      const res = await getReportLogs(record.id)
      setLogs(res?.data || [])
    } catch (error) { setLogs([]) }
  }

  const openHandle = (record, action) => {
    setCurrentReport(record)
    handleForm.setFieldsValue({ action, handleResult: action === 'DELETE_CONTENT' ? '举报成立，已删除被举报内容' : '举报不成立，已忽略' })
    setHandleOpen(true)
  }

  const submitHandle = async () => {
    const values = await handleForm.validateFields()
    await handleReport(currentReport.id, values)
    message.success('举报处理完成')
    setHandleOpen(false)
    setCurrentReport(null)
    fetchReports()
    fetchStats()
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '举报人', dataIndex: 'reporterName', width: 120 },
    { title: '类型', dataIndex: 'targetType', width: 90, render: (v) => <Tag color={targetTypeMap[v]?.color}>{targetTypeMap[v]?.text || v}</Tag> },
    { title: '被举报内容', dataIndex: 'targetTitle', ellipsis: true, width: 220 },
    { title: '作者', dataIndex: 'targetAuthor', width: 120 },
    { title: '原因', dataIndex: 'reasonText', ellipsis: true, width: 160 },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || v}</Tag> },
    { title: '提交时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作', key: 'action', width: 240, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EyeOutlined />} onClick={() => openDetail(record)}>查看</Button>
          {record.status === 0 && (
            <>
              <Button type="text" icon={<StopOutlined />} onClick={() => openHandle(record, 'IGNORE')}>忽略</Button>
              <Button type="text" danger icon={<DeleteOutlined />} onClick={() => openHandle(record, 'DELETE_CONTENT')}>删除内容</Button>
            </>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="rm-container">
      <div className="rm-bg-fire" />

      {/* 论坛全局概览 */}
      {forumStats && (
        <div className="rm-overview-bar">
          <span className="rm-overview-label">📊 论坛概览：</span>
          <span className="rm-overview-item">帖子 <strong>{forumStats.totalPosts || 0}</strong></span>
          <span className="rm-overview-item">评论 <strong>{forumStats.totalComments || 0}</strong></span>
          <span className="rm-overview-item">话题 <strong>{forumStats.totalTopics || 0}</strong></span>
        </div>
      )}

      {/* 举报统计卡片 */}
      {stats && (
        <div className="rm-stat-row">
          <Card className="rm-stat-card rm-card-total">
            <div className="rm-stat-icon"><FileTextOutlined /></div>
            <div className="rm-stat-value">{stats.total}</div>
            <div className="rm-stat-label">举报总数</div>
          </Card>
          <Card className="rm-stat-card rm-card-pending">
            <div className="rm-stat-icon"><WarningOutlined /></div>
            <div className="rm-stat-value">{stats.pending}</div>
            <div className="rm-stat-label">待处理</div>
          </Card>
          <Card className="rm-stat-card rm-card-handled">
            <div className="rm-stat-icon"><CheckCircleOutlined /></div>
            <div className="rm-stat-value">{stats.handled}</div>
            <div className="rm-stat-label">已处理</div>
          </Card>
          <Card className="rm-stat-card rm-card-rejected">
            <div className="rm-stat-icon"><StopOutlined /></div>
            <div className="rm-stat-value">{stats.rejected}</div>
            <div className="rm-stat-label">已忽略</div>
          </Card>
        </div>
      )}

      {/* 搜索栏 */}
      <div className="rm-search-card">
        <Form form={searchForm} layout="inline" onFinish={() => fetchReports({ page: 1 })}>
          <Form.Item name="targetType">
            <Select placeholder="举报类型" allowClear style={{ width: 130 }} options={[{ value: 1, label: '帖子' }, { value: 2, label: '评论' }]} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="处理状态" allowClear style={{ width: 130 }} options={[{ value: 0, label: '待处理' }, { value: 1, label: '已处理' }, { value: 2, label: '已忽略' }]} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={() => { searchForm.resetFields(); fetchReports({ page: 1, status: undefined, targetType: undefined }) }} style={{ marginLeft: 8 }}>重置</Button>
          </Form.Item>
        </Form>
      </div>

      {/* 举报表格 */}
      <Card className="rm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={reports}
          rowKey="id"
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
          onChange={(pag) => fetchReports({ page: pag.current, size: pag.pageSize })}
          scroll={{ x: 1400 }}
          size="middle"
        />
      </Card>

      {/* 举报详情弹窗 */}
      <Modal title={<Space><EyeOutlined />举报详情</Space>} open={detailOpen} onCancel={() => setDetailOpen(false)} footer={null} width={780}>
        {currentReport && (
          <>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="举报人">{currentReport.reporterName}</Descriptions.Item>
              <Descriptions.Item label="状态"><Tag color={statusMap[currentReport.status]?.color}>{statusMap[currentReport.status]?.text}</Tag></Descriptions.Item>
              <Descriptions.Item label="类型"><Tag color={targetTypeMap[currentReport.targetType]?.color}>{targetTypeMap[currentReport.targetType]?.text}</Tag></Descriptions.Item>
              <Descriptions.Item label="作者">{currentReport.targetAuthor}</Descriptions.Item>
              <Descriptions.Item label="内容" span={2}>{currentReport.targetTitle}</Descriptions.Item>
              <Descriptions.Item label="原因" span={2}>{currentReport.reasonText || '-'}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{currentReport.description || '-'}</Descriptions.Item>
              <Descriptions.Item label="处理人">{currentReport.handleByName || '-'}</Descriptions.Item>
              <Descriptions.Item label="处理时间">{currentReport.handleTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="处理结果" span={2}>{currentReport.handleResult || '-'}</Descriptions.Item>
            </Descriptions>
            <Timeline style={{ marginTop: 24 }} items={logs.map((item) => ({
              dot: item.action === 'DELETE_CONTENT' ? <CheckCircleOutlined /> : null,
              children: `${item.createTime || ''} ${item.operatorName || '系统'} ${actionMap[item.action] || item.action}${item.remark ? `：${item.remark}` : ''}`,
            }))} />
          </>
        )}
      </Modal>

      {/* 处理弹窗 */}
      <Modal title={<Space><StopOutlined />处理举报</Space>} open={handleOpen} onOk={submitHandle} onCancel={() => setHandleOpen(false)} okText="确认处理" cancelText="取消">
        <Form form={handleForm} layout="vertical">
          <Form.Item name="action" label="处理动作" rules={[{ required: true, message: '请选择处理动作' }]}>
            <Select options={[{ value: 'IGNORE', label: '忽略举报' }, { value: 'DELETE_CONTENT', label: '删除被举报内容' }]} />
          </Form.Item>
          <Form.Item name="handleResult" label="处理说明" rules={[{ required: true, message: '请输入处理说明' }]}>
            <TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ReportManage
