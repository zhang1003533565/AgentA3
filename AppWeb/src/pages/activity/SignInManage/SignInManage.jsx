import { useState, useEffect } from 'react'
import { message, Modal, Form, Select, Button, Table, Tag, Space, Input, QRCode } from 'antd'
import { DownloadOutlined, QrcodeOutlined, UserAddOutlined } from '@ant-design/icons'
import { batchReviewSignIn, getSignInList, reviewSignIn, supplementSignIn } from '../../../api/signin'
import { getActivityList } from '../../../api/activity'
import './SignInManage.css'

const { Option } = Select
const { TextArea } = Input

const statusMap = {
  0: { text: '未签到', color: 'default' },
  1: { text: '已签到', color: 'green' },
  2: { text: '补签', color: 'blue' },
  3: { text: '缺席', color: 'red' }
}

const reviewStatusMap = {
  PENDING: { text: '待复核', color: 'orange' },
  APPROVED: { text: '已发学分', color: 'green' },
  REJECTED: { text: '复核未通过', color: 'red' },
}

function SignInManage() {
  const [signIns, setSignIns] = useState([])
  const [activities, setActivities] = useState([])
  const [loading, setLoading] = useState(false)
  const [qrModalVisible, setQrModalVisible] = useState(false)
  const [supplementModalVisible, setSupplementModalVisible] = useState(false)
  const [supplementForm] = Form.useForm()
  const [reviewModalVisible, setReviewModalVisible] = useState(false)
  const [reviewForm] = Form.useForm()
  const [isBatchReview, setIsBatchReview] = useState(false)
  const [currentRecord, setCurrentRecord] = useState(null)
  const [selectedActivity, setSelectedActivity] = useState(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])

  const fetchSignIns = async (activityId) => {
    if (!activityId) {
      setSignIns([])
      return
    }
    setLoading(true)
    try {
      const res = await getSignInList(activityId)
      if (res.code === 200) {
        setSignIns(res.data?.records || [])
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchActivities = async () => {
    try {
      const res = await getActivityList({ page: 1, size: 999 })
      if (res.code === 200) {
        setActivities(res.data?.records || [])
      }
    } catch (error) {
      console.error('获取活动列表失败:', error)
    }
  }

  useEffect(() => {
    fetchActivities()
  }, [])

  const handleActivityChange = (activityId) => {
    setSelectedActivity(activityId)
    setSelectedRowKeys([])
    fetchSignIns(activityId)
  }

  const handleSupplement = (record) => {
    setCurrentRecord(record)
    supplementForm.resetFields()
    setSupplementModalVisible(true)
  }

  const handleSupplementSubmit = async () => {
    try {
      await supplementForm.validateFields()
      const res = await supplementSignIn(selectedActivity, currentRecord.userId)
      if (res.code === 200) {
        message.success('补签成功')
        setSupplementModalVisible(false)
        fetchSignIns(selectedActivity)
      }
    } catch (error) {
      console.error('补签失败:', error)
    }
  }

  const handleReview = (record, batch = false) => {
    setCurrentRecord(record)
    setIsBatchReview(batch)
    reviewForm.setFieldsValue({ status: 'APPROVED', remark: '' })
    setReviewModalVisible(true)
  }

  const handleReviewSubmit = async () => {
    try {
      const values = await reviewForm.validateFields()
      if (isBatchReview) {
        await batchReviewSignIn(selectedRowKeys, values.status, values.remark)
      } else {
        await reviewSignIn(currentRecord.id, values.status, values.remark)
      }
      message.success('复核完成')
      setReviewModalVisible(false)
      setSelectedRowKeys([])
      fetchSignIns(selectedActivity)
    } catch (error) {
      console.error('复核失败:', error)
    }
  }

  const handleGenerateQR = () => {
    if (!selectedActivity) {
      message.warning('请先选择活动')
      return
    }
    setQrModalVisible(true)
  }

  const handleExport = () => {
    if (!selectedActivity) {
      message.warning('请先选择活动')
      return
    }
    message.info('导出功能开发中...')
  }

  const columns = [
    { title: '签到ID', dataIndex: 'id', width: 80 },
    { title: '活动名称', dataIndex: 'activityTitle', ellipsis: true },
    { title: '报名人', dataIndex: 'realName', render: (text, record) => text || record.username },
    { title: '学号', dataIndex: 'personalNumber', width: 130 },
    { title: '手机号', dataIndex: 'phone', width: 130 },
    { title: '签到时间', dataIndex: 'signInTime', width: 170, render: (text) => text || '-' },
    {
      title: '签到状态',
      dataIndex: 'signInStatus',
      width: 100,
      render: (status) => <Tag color={statusMap[status]?.color}>{statusMap[status]?.text || '-'}</Tag>,
    },
    {
      title: '复核状态',
      dataIndex: 'reviewStatus',
      width: 120,
      render: (status) => <Tag color={reviewStatusMap[status]?.color || 'default'}>{reviewStatusMap[status]?.text || '-'}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.signInStatus !== 1 && (
            <Button type="text" style={{ color: '#1890ff' }} icon={<UserAddOutlined />} onClick={() => handleSupplement(record)}>
              补签
            </Button>
          )}
          {record.signInStatus === 1 && record.reviewStatus !== 'APPROVED' && (
            <Button type="text" style={{ color: '#52c41a' }} onClick={() => handleReview(record, false)}>
              复核发分
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const stats = {
    total: signIns.length,
    signed: signIns.filter((s) => s.signInStatus === 1).length,
    pendingReview: signIns.filter((s) => s.signInStatus === 1 && s.reviewStatus !== 'APPROVED').length,
    granted: signIns.filter((s) => s.reviewStatus === 'APPROVED').length,
  }

  return (
    <div className="signin-manage-container">
      <main className="manage-main">
        <div className="page-header">
          <h2>签到管理</h2>
        </div>

        <div className="stats-row">
          <div className="stat-card"><span className="stat-number">{stats.total}</span><span className="stat-label">总人数</span></div>
          <div className="stat-card signed"><span className="stat-number">{stats.signed}</span><span className="stat-label">已签到</span></div>
          <div className="stat-card supplement"><span className="stat-number">{stats.pendingReview}</span><span className="stat-label">待复核</span></div>
          <div className="stat-card absent"><span className="stat-number">{stats.granted}</span><span className="stat-label">已发学分</span></div>
        </div>

        <div className="search-bar">
          <Form layout="inline">
            <Form.Item>
              <Select placeholder="选择活动" allowClear style={{ width: 280 }} onChange={handleActivityChange}>
                {activities.map((act) => (
                  <Option key={act.id} value={act.id}>{act.title}</Option>
                ))}
              </Select>
            </Form.Item>
          </Form>
          <Space>
            <Button icon={<QrcodeOutlined />} onClick={handleGenerateQR} disabled={!selectedActivity}>生成二维码</Button>
            <Button icon={<DownloadOutlined />} onClick={handleExport} disabled={!selectedActivity}>导出名单</Button>
            <Button type="primary" disabled={selectedRowKeys.length === 0} onClick={() => handleReview(null, true)}>
              批量复核发分 ({selectedRowKeys.length})
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={signIns}
          rowKey="id"
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
            getCheckboxProps: (record) => ({ disabled: !(record.signInStatus === 1 && record.reviewStatus !== 'APPROVED') }),
          }}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
          scroll={{ x: 1300 }}
        />
      </main>

      <Modal title="签到二维码" open={qrModalVisible} onCancel={() => setQrModalVisible(false)} footer={null} width={400}>
        <div className="qr-content">
          <QRCode value={`${window.location.origin}/signin/${selectedActivity}`} size={256} style={{ margin: '20px auto', display: 'block' }} />
          <p className="qr-tip">请使用小程序扫码签到</p>
        </div>
      </Modal>

      <Modal title="补签" open={supplementModalVisible} onOk={handleSupplementSubmit} onCancel={() => setSupplementModalVisible(false)} width={500}>
        <Form form={supplementForm} layout="vertical" style={{ marginTop: 16 }}>
          <p>为 <strong>{currentRecord?.realName || currentRecord?.username}</strong> 进行补签</p>
          <Form.Item name="remark" label="补签原因" rules={[{ required: true, message: '请输入补签原因' }]}>
            <TextArea rows={3} placeholder="请输入补签原因" maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={isBatchReview ? '批量复核发分' : '签到复核发分'} open={reviewModalVisible} onOk={handleReviewSubmit} onCancel={() => setReviewModalVisible(false)}>
        <Form form={reviewForm} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="status" label="复核结果" rules={[{ required: true, message: '请选择复核结果' }]}>
            <Select>
              <Option value="APPROVED">通过并发放学分</Option>
              <Option value="REJECTED">不通过</Option>
            </Select>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={3} maxLength={200} showCount placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SignInManage
