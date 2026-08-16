import { useEffect, useState } from 'react'
import dayjs from 'dayjs'
import { Button, Card, Descriptions, Form, Input, message, Modal, Select, Space, Table, Tag, Timeline, Popover, Tooltip } from 'antd'
import { CheckCircleOutlined, DeleteOutlined, EyeOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons'
import { getReportList, getReportLogs, getReportStatistics, handleReport, batchDeleteReports } from '../../../api/forum'
import './ReportManage.css'

const { TextArea } = Input

const targetTypeMap = { 1: { text: '帖子', color: 'blue' }, 2: { text: '评论', color: 'green' } }
const statusMap = { 0: { text: '待处理', color: 'orange' }, 1: { text: '已处理', color: 'green' }, 2: { text: '已忽略', color: 'default' } }
const actionMap = { CREATE_REPORT: '提交举报', IGNORE: '忽略举报', DELETE_CONTENT: '删除内容' }

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

function ReportManage() {
  const [reports, setReports] = useState([])
  const [stats, setStats] = useState(null)
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(false)
  const [handleOpen, setHandleOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [currentReport, setCurrentReport] = useState(null)
  const [searchForm] = Form.useForm()
  const [handleForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedRowKeys, setSelectedRowKeys] = useState([])

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

  useEffect(() => { fetchReports({ page: 1 }); fetchStats() }, [])

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

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    try {
      const res = await batchDeleteReports(selectedRowKeys)
      if (res.code === 200) {
        message.success(`已删除 ${selectedRowKeys.length} 条举报记录`)
        setSelectedRowKeys([])
        fetchReports()
        fetchStats()
      }
    } catch (error) { message.error(error?.message || '删除失败') }
  }

  const rowSelection = { selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys) }

  const renderRowPopover = (record) => (
    <div className="rm-row-pop">
      <div className="rm-row-pop-title">{record.targetTitle}</div>
      <div className="rm-row-pop-meta">
        <div><span className="rm-row-pop-label">举报人</span>{record.reporterName}</div>
        <div><span className="rm-row-pop-label">类型</span><Tag color={targetTypeMap[record.targetType]?.color}>{targetTypeMap[record.targetType]?.text || record.targetType}</Tag></div>
        <div><span className="rm-row-pop-label">作者</span>{record.targetAuthor || '-'}</div>
        <div><span className="rm-row-pop-label">原因</span>{record.reasonText || '-'}</div>
        <div><span className="rm-row-pop-label">状态</span><Tag color={statusMap[record.status]?.color}>{statusMap[record.status]?.text || record.status}</Tag></div>
        <div><span className="rm-row-pop-label">提交时间</span>{formatTime(record.createTime)}</div>
      </div>
      {record.description && <div className="rm-row-pop-desc">{record.description}</div>}
    </div>
  )

  const columns = [
    { title: '举报人', dataIndex: 'reporterName', width: 100 },
    { title: '类型', dataIndex: 'targetType', width: 70, render: (v) => <Tag color={targetTypeMap[v]?.color}>{targetTypeMap[v]?.text || v}</Tag> },
    {
      title: '被举报内容', dataIndex: 'targetTitle', ellipsis: true, width: 160,
      render: (text, record) => (
        <Popover content={renderRowPopover(record)} title="举报完整信息" trigger="hover" placement="bottomLeft" mouseEnterDelay={0.3}>
          <span>{text}</span>
        </Popover>
      ),
    },
    { title: '作者', dataIndex: 'targetAuthor', width: 90 },
    { title: '原因', dataIndex: 'reasonText', ellipsis: true, width: 120 },
    { title: '状态', dataIndex: 'status', width: 80, render: (v) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || v}</Tag> },
    { title: '提交时间', dataIndex: 'createTime', width: 130, render: (t) => formatTime(t) },
    {
      title: '操作', key: 'action', width: 130,
      render: (_, record) => (
        <Space size={0}>
          <Tooltip title="查看">
            <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)} />
          </Tooltip>
          {record.status === 0 && (
            <>
              <Tooltip title="忽略">
                <Button type="text" size="small" icon={<StopOutlined />} onClick={() => openHandle(record, 'IGNORE')} />
              </Tooltip>
              <Tooltip title="删除内容">
                <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => openHandle(record, 'DELETE_CONTENT')} />
              </Tooltip>
            </>
          )}
        </Space>
      ),
    },
  ]

  const statItems = [
    { label: '举报总数', value: stats?.total ?? 0, className: 'rm-header-stat-blue' },
    { label: '待处理', value: stats?.pending ?? 0, className: 'rm-header-stat-orange' },
    { label: '已处理', value: stats?.handled ?? 0, className: 'rm-header-stat-green' },
    { label: '已忽略', value: stats?.rejected ?? 0, className: 'rm-header-stat-gray' },
  ]

  return (
    <div className="rm-container">

      {/* 顶部统计区（白色矩形，仅保留统计） */}
      <div className="rm-header">
        <div className="rm-header-stats">
          {statItems.map((s) => (
            <div key={s.label} className={`rm-header-stat ${s.className}`}>
              <span className="rm-header-stat-value">{s.value}</span>
              <span className="rm-header-stat-label">{s.label}</span>
            </div>
          ))}
        </div>
      </div>

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

      {/* 批量操作栏 */}
      {selectedRowKeys.length > 0 && (
        <div className="rm-batch-bar">
          <span className="rm-batch-count">已选择 <strong>{selectedRowKeys.length}</strong> 条</span>
          <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 条举报记录吗？`} onConfirm={handleBatchDelete} okText="确定" cancelText="取消">
            <Button type="primary" danger size="small" icon={<DeleteOutlined />}>批量删除</Button>
          </Popconfirm>
        </div>
      )}

      {/* 举报表格 */}
      <Card className="rm-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={reports}
          rowKey="id"
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
          onChange={(pag) => fetchReports({ page: pag.current, size: pag.pageSize })}
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
              <Descriptions.Item label="处理时间">{formatTime(currentReport.handleTime)}</Descriptions.Item>
              <Descriptions.Item label="处理结果" span={2}>{currentReport.handleResult || '-'}</Descriptions.Item>
            </Descriptions>
            <Timeline style={{ marginTop: 24 }} items={logs.map((item) => ({
              dot: item.action === 'DELETE_CONTENT' ? <CheckCircleOutlined /> : null,
              children: `${formatTime(item.createTime)} ${item.operatorName || '系统'} ${actionMap[item.action] || item.action}${item.remark ? `：${item.remark}` : ''}`,
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
