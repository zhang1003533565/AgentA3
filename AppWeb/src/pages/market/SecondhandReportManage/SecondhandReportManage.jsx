import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, message, Modal, Select, Space, Table, Tag } from 'antd'
import { CheckCircleOutlined, DeleteOutlined, SearchOutlined, StopOutlined, WarningOutlined, FileTextOutlined } from '@ant-design/icons'
import { getSecondhandReportList, handleSecondhandReport, getSecondhandReportStatistics } from '../../../api/secondhand'

const { TextArea } = Input

const reasonTypeMap = { 1: { text: '虚假信息', color: 'red' }, 2: { text: '不良行为', color: 'orange' }, 3: { text: '其他违规', color: 'default' } }
const statusMap = { 0: { text: '待处理', color: 'orange' }, 1: { text: '已处理', color: 'green' }, 2: { text: '已驳回', color: 'default' } }

function SecondhandReportManage() {
  const [reports, setReports] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(false)
  const [handleOpen, setHandleOpen] = useState(false)
  const [currentReport, setCurrentReport] = useState(null)
  const [searchForm] = Form.useForm()
  const [handleForm] = Form.useForm()
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  const fetchReports = async (params = {}) => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const res = await getSecondhandReportList({ 
        page: params.page ?? pagination.current, 
        size: params.size ?? pagination.pageSize, 
        status: values.status !== undefined && values.status !== '' ? values.status : undefined,
        ...params 
      })
      const data = res?.data || {}
      setReports(data.content || data.records || [])
      setPagination((prev) => ({ ...prev, current: data.number !== undefined ? data.number + 1 : (data.page || params.page || prev.current), pageSize: data.size || params.size || prev.pageSize, total: data.totalElements || data.total || 0 }))
    } catch (error) { message.error(error?.message || '获取举报列表失败') }
    finally { setLoading(false) }
  }

  const fetchStats = async () => {
    try {
      const res = await getSecondhandReportStatistics()
      setStats(res?.data || null)
    } catch (error) { setStats(null) }
  }

  useEffect(() => { 
    fetchReports({ page: 1 })
    fetchStats() 
  }, [])

  const openHandle = (record, action) => {
    setCurrentReport(record)
    handleForm.setFieldsValue({ action, handleResult: action === 'OFFLINE_ITEM' ? '举报成立，已下架商品' : '举报不成立，已忽略' })
    setHandleOpen(true)
  }

  const submitHandle = async () => {
    const values = await handleForm.validateFields()
    await handleSecondhandReport(currentReport.id, values)
    message.success('举报处理完成')
    setHandleOpen(false)
    setCurrentReport(null)
    fetchReports()
    fetchStats()
  }

  const columns = [
    { title: '举报人', dataIndex: 'reporterName', width: 100 },
    { title: '联系方式', dataIndex: 'reporterContact', width: 140 },
    { title: '商品标题', dataIndex: 'itemTitle', width: 180, ellipsis: true },
    { title: '卖家', dataIndex: 'itemSellerName', width: 100 },
    { title: '详细理由', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 90, render: (v) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || v}</Tag> },
    { title: '提交时间', dataIndex: 'createTime', width: 150 },
    {
      title: '操作', key: 'action', width: 180, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 0 && (
            <>
              <Button type="text" icon={<StopOutlined />} onClick={() => openHandle(record, 'IGNORE')}>忽略</Button>
              <Button type="text" danger icon={<DeleteOutlined />} onClick={() => openHandle(record, 'OFFLINE_ITEM')}>下架商品</Button>
            </>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      {/* 统计卡片 */}
      {stats && (
        <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
          <Card>
            <div style={{ textAlign: 'center' }}>
              <FileTextOutlined style={{ fontSize: 32, color: '#1890ff' }} />
              <div style={{ fontSize: 24, fontWeight: 'bold' }}>{stats.total || 0}</div>
              <div style={{ color: '#999' }}>举报总数</div>
            </div>
          </Card>
          <Card>
            <div style={{ textAlign: 'center' }}>
              <WarningOutlined style={{ fontSize: 32, color: '#faad14' }} />
              <div style={{ fontSize: 24, fontWeight: 'bold' }}>{stats.pending || 0}</div>
              <div style={{ color: '#999' }}>待处理</div>
            </div>
          </Card>
          <Card>
            <div style={{ textAlign: 'center' }}>
              <CheckCircleOutlined style={{ fontSize: 32, color: '#52c41a' }} />
              <div style={{ fontSize: 24, fontWeight: 'bold' }}>{stats.handled || 0}</div>
              <div style={{ color: '#999' }}>已处理</div>
            </div>
          </Card>
          <Card>
            <div style={{ textAlign: 'center' }}>
              <StopOutlined style={{ fontSize: 32, color: '#8c8c8c' }} />
              <div style={{ fontSize: 24, fontWeight: 'bold' }}>{stats.rejected || 0}</div>
              <div style={{ color: '#999' }}>已驳回</div>
            </div>
          </Card>
        </div>
      )}

      {/* 搜索栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline" onFinish={() => fetchReports({ page: 1 })}>
          <Form.Item name="status">
            <Select placeholder="处理状态" allowClear style={{ width: 150 }} options={[
              { value: 0, label: '待处理' },
              { value: 1, label: '已处理' },
              { value: 2, label: '已驳回' },
            ]} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
            <Button onClick={() => { searchForm.resetFields(); fetchReports({ page: 1, status: undefined }) }} style={{ marginLeft: 8 }}>重置</Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 举报表格 */}
      <Card bodyStyle={{ padding: 0 }}>
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

      {/* 处理弹窗 */}
      <Modal title={<Space><StopOutlined />处理举报</Space>} open={handleOpen} onOk={submitHandle} onCancel={() => setHandleOpen(false)} okText="确认处理" cancelText="取消">
        <Form form={handleForm} layout="vertical">
          <Form.Item name="action" label="处理动作" rules={[{ required: true, message: '请选择处理动作' }]}>
            <Select options={[
              { value: 'IGNORE', label: '忽略举报' },
              { value: 'OFFLINE_ITEM', label: '下架商品' },
            ]} />
          </Form.Item>
          <Form.Item name="handleResult" label="处理说明" rules={[{ required: true, message: '请输入处理说明' }]}>
            <TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SecondhandReportManage
