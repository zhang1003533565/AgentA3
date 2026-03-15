import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Form, Select, Button, Table, Tag, Space, Popconfirm, Input, QRCode } from 'antd'
import { CheckCircleOutlined, DownloadOutlined, QrcodeOutlined, SearchOutlined, UserAddOutlined } from '@ant-design/icons'
import { getSignInList, supplementSignIn } from '../api/signin'
import { getActivityList } from '../api/activity'
import { getUserInfo, clearAuth } from '../utils/storage'
import './SignInManage.css'

const { Option } = Select
const { TextArea } = Input

// 签到状态映射
const statusMap = {
  0: { text: '未签到', color: 'default' },
  1: { text: '已签到', color: 'green' },
  2: { text: '补签', color: 'blue' },
  3: { text: '缺席', color: 'red' }
}

function SignInManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [signIns, setSignIns] = useState([])
  const [activities, setActivities] = useState([])
  const [loading, setLoading] = useState(false)
  const [qrModalVisible, setQrModalVisible] = useState(false)
  const [supplementModalVisible, setSupplementModalVisible] = useState(false)
  const [supplementForm] = Form.useForm()
  const [currentRecord, setCurrentRecord] = useState(null)
  const [selectedActivity, setSelectedActivity] = useState(null)
  const [searchForm] = Form.useForm()

  // 检查登录状态
  useEffect(() => {
    const info = getUserInfo()
    if (!info) {
      message.error('请先登录')
      navigate('/')
      return
    }
    setUserInfo(info)
  }, [navigate])

  // 获取签到列表
  const fetchSignIns = async (activityId) => {
    if (!activityId) {
      setSignIns([])
      return
    }
    setLoading(true)
    try {
      const res = await getSignInList(activityId)
      if (res.code === 200) {
        setSignIns(res.data || [])
      }
    } finally {
      setLoading(false)
    }
  }

  // 获取活动列表
  const fetchActivities = async () => {
    try {
      const res = await getActivityList()
      if (res.code === 200) {
        setActivities(res.data || [])
      }
    } catch (error) {
      console.error('获取活动列表失败:', error)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchActivities()
    }
  }, [userInfo])

  // 活动选择变化
  const handleActivityChange = (activityId) => {
    setSelectedActivity(activityId)
    fetchSignIns(activityId)
  }

  // 打开补签弹窗
  const handleSupplement = (record) => {
    setCurrentRecord(record)
    supplementForm.resetFields()
    setSupplementModalVisible(true)
  }

  // 提交补签
  const handleSupplementSubmit = async () => {
    try {
      const values = await supplementForm.validateFields()
      const res = await supplementSignIn(currentRecord.id, values)

      if (res.code === 200) {
        message.success('补签成功')
        setSupplementModalVisible(false)
        fetchSignIns(selectedActivity)
      }
    } catch (error) {
      console.error('补签失败:', error)
    }
  }

  // 生成二维码
  const handleGenerateQR = () => {
    if (!selectedActivity) {
      message.warning('请先选择活动')
      return
    }
    setQrModalVisible(true)
  }

  // 导出签到名单
  const handleExport = () => {
    if (!selectedActivity) {
      message.warning('请先选择活动')
      return
    }
    message.info('导出功能开发中...')
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '签到详情',
      width: 500,
      content: (
        <div className="signin-detail">
          <p><strong>签到ID：</strong>{record.id}</p>
          <p><strong>活动名称：</strong>{record.activityName}</p>
          <p><strong>报名人：</strong>{record.realName || record.username}</p>
          <p><strong>学号：</strong>{record.studentNo}</p>
          <p><strong>手机号：</strong>{record.phone}</p>
          <p><strong>签到时间：</strong>{record.signInTime || '未签到'}</p>
          <p><strong>签到方式：</strong>{record.signInType === 1 ? '二维码' : record.signInType === 2 ? '定位' : '未签到'}</p>
          <p><strong>状态：</strong>{statusMap[record.signInStatus]?.text}</p>
          {record.remark && <p><strong>备注：</strong>{record.remark}</p>}
        </div>
      )
    })
  }

  // 退出登录
  const handleLogout = () => {
    clearAuth()
    message.success('已退出登录')
    navigate('/')
  }

  // 表格列定义
  const columns = [
    {
      title: '签到ID',
      dataIndex: 'id',
      width: 80
    },
    {
      title: '活动名称',
      dataIndex: 'activityName',
      ellipsis: true
    },
    {
      title: '报名人',
      dataIndex: 'realName',
      render: (text, record) => text || record.username
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      width: 120
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 130
    },
    {
      title: '签到时间',
      dataIndex: 'signInTime',
      width: 160,
      render: (text) => text || '-'
    },
    {
      title: '签到方式',
      dataIndex: 'signInType',
      width: 100,
      render: (type) => {
        const typeMap = { 1: '二维码', 2: '定位' }
        return typeMap[type] || '-'
      }
    },
    {
      title: '状态',
      dataIndex: 'signInStatus',
      width: 100,
      render: (status) => (
        <Tag color={statusMap[status]?.color}>
          {statusMap[status]?.text}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            type="text" 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          {record.signInStatus !== 1 && (
            <Button 
              type="text" 
              style={{ color: '#1890ff' }}
              icon={<UserAddOutlined />} 
              onClick={() => handleSupplement(record)}
            >
              补签
            </Button>
          )}
        </Space>
      )
    }
  ]

  // 统计数据
  const stats = {
    total: signIns.length,
    signed: signIns.filter(s => s.signInStatus === 1).length,
    supplement: signIns.filter(s => s.signInStatus === 2).length,
    absent: signIns.filter(s => s.signInStatus === 0 || s.signInStatus === 3).length
  }

  if (!userInfo) {
    return null
  }

  return (
    <div className="signin-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 签到管理</h1>
        </div>
        <div className="header-right">
          <span className="user-name">{userInfo.username}</span>
          <Button onClick={handleLogout}>退出</Button>
        </div>
      </header>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 统计卡片 */}
        <div className="stats-row">
          <div className="stat-card">
            <span className="stat-number">{stats.total}</span>
            <span className="stat-label">总人数</span>
          </div>
          <div className="stat-card signed">
            <span className="stat-number">{stats.signed}</span>
            <span className="stat-label">已签到</span>
          </div>
          <div className="stat-card supplement">
            <span className="stat-number">{stats.supplement}</span>
            <span className="stat-label">补签</span>
          </div>
          <div className="stat-card absent">
            <span className="stat-number">{stats.absent}</span>
            <span className="stat-label">未签到</span>
          </div>
        </div>

        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline">
            <Form.Item name="activityId">
              <Select 
                placeholder="选择活动" 
                allowClear 
                style={{ width: 250 }}
                onChange={handleActivityChange}
              >
                {activities.map(act => (
                  <Option key={act.id} value={act.id}>{act.title}</Option>
                ))}
              </Select>
            </Form.Item>
          </Form>
          <Space>
            <Button 
              icon={<QrcodeOutlined />} 
              onClick={handleGenerateQR}
              disabled={!selectedActivity}
            >
              生成二维码
            </Button>
            <Button 
              icon={<DownloadOutlined />} 
              onClick={handleExport}
              disabled={!selectedActivity}
            >
              导出名单
            </Button>
          </Space>
        </div>

        {/* 签到列表 */}
        <Table
          columns={columns}
          dataSource={signIns}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `共 ${total} 条`
          }}
          scroll={{ x: 1200 }}
        />
      </main>

      {/* 二维码弹窗 */}
      <Modal
        title="签到二维码"
        open={qrModalVisible}
        onCancel={() => setQrModalVisible(false)}
        footer={null}
        width={400}
      >
        <div className="qr-content">
          <QRCode 
            value={`${window.location.origin}/signin/${selectedActivity}`}
            size={256}
            style={{ margin: '20px auto', display: 'block' }}
          />
          <p className="qr-tip">请使用智慧校园APP扫描二维码签到</p>
        </div>
      </Modal>

      {/* 补签弹窗 */}
      <Modal
        title="补签"
        open={supplementModalVisible}
        onOk={handleSupplementSubmit}
        onCancel={() => setSupplementModalVisible(false)}
        width={500}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={supplementForm}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <p>为 <strong>{currentRecord?.realName || currentRecord?.username}</strong> 进行补签</p>
          <Form.Item
            name="remark"
            label="补签原因"
            rules={[{ required: true, message: '请输入补签原因' }]}
          >
            <TextArea rows={3} placeholder="请输入补签原因" maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SignInManage
