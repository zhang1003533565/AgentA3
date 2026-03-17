import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Form, Input, Button, Table, Tag, Space, Select } from 'antd'
import { EyeOutlined, CheckOutlined, CloseOutlined, SearchOutlined } from '@ant-design/icons'
import { getUserInfo, clearAuth } from '../../../utils/storage'
import './ReportManage.css'

const { Option } = Select
const { TextArea } = Input

// 举报类型映射
const targetTypeMap = {
  1: { text: '帖子', color: 'blue' },
  2: { text: '评论', color: 'green' },
  3: { text: '用户', color: 'purple' }
}

// 处理状态映射
const statusMap = {
  0: { text: '待处理', color: 'orange' },
  1: { text: '已处理', color: 'green' },
  2: { text: '已驳回', color: 'default' }
}

// 举报原因映射
const reasonMap = {
  1: '垃圾广告',
  2: '虚假信息',
  3: '人身攻击',
  4: '色情低俗',
  5: '违法违规',
  6: '其他'
}

// 模拟数据
const mockReports = [
  {
    id: 1,
    reporterId: 10,
    reporterName: '举报者A',
    targetId: 5,
    targetType: 1,
    targetTitle: '某帖子标题',
    targetContent: '这是被举报的帖子内容...',
    targetAuthor: '被举报用户X',
    reason: 1,
    reasonText: '垃圾广告',
    description: '这个帖子全是广告链接，请管理员处理。',
    status: 0,
    handleBy: null,
    handleTime: null,
    handleResult: null,
    createTime: '2026-03-15 14:30:00'
  },
  {
    id: 2,
    reporterId: 11,
    reporterName: '举报者B',
    targetId: 12,
    targetType: 2,
    targetTitle: '评论',
    targetContent: '这是被举报的评论内容...',
    targetAuthor: '被举报用户Y',
    reason: 3,
    reasonText: '人身攻击',
    description: '这条评论有辱骂性语言。',
    status: 0,
    handleBy: null,
    handleTime: null,
    handleResult: null,
    createTime: '2026-03-15 10:20:00'
  },
  {
    id: 3,
    reporterId: 12,
    reporterName: '举报者C',
    targetId: 8,
    targetType: 1,
    targetTitle: '某帖子标题2',
    targetContent: '这是被举报的帖子内容...',
    targetAuthor: '被举报用户Z',
    reason: 2,
    reasonText: '虚假信息',
    description: '这个帖子发布的信息是假的。',
    status: 1,
    handleBy: '管理员',
    handleTime: '2026-03-14 16:00:00',
    handleResult: '已删除违规帖子，警告发布者',
    createTime: '2026-03-14 15:00:00'
  },
  {
    id: 4,
    reporterId: 13,
    reporterName: '举报者D',
    targetId: 20,
    targetType: 3,
    targetTitle: '用户',
    targetContent: '',
    targetAuthor: '被举报用户W',
    reason: 4,
    reasonText: '色情低俗',
    description: '该用户发布的内容涉及色情低俗。',
    status: 2,
    handleBy: '管理员',
    handleTime: '2026-03-13 11:00:00',
    handleResult: '经核实不属实，驳回举报',
    createTime: '2026-03-13 10:00:00'
  }
]

function ReportManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [handleModalVisible, setHandleModalVisible] = useState(false)
  const [handleForm] = Form.useForm()
  const [currentReport, setCurrentReport] = useState(null)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

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

  // 获取举报列表
  const fetchReports = async (params = {}) => {
    setLoading(true)
    try {
      await new Promise(resolve => setTimeout(resolve, 500))
      setReports(mockReports)
      setPagination({
        ...pagination,
        total: mockReports.length
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchReports()
    }
  }, [userInfo])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchReports(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchReports()
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '举报详情',
      width: 600,
      content: (
        <div className="report-detail">
          <p><strong>举报ID：</strong>{record.id}</p>
          <p><strong>举报人：</strong>{record.reporterName}</p>
          <p><strong>举报类型：</strong>{targetTypeMap[record.targetType]?.text}</p>
          <p><strong>被举报对象：</strong>{record.targetTitle}</p>
          <p><strong>被举报用户：</strong>{record.targetAuthor}</p>
          <p><strong>举报原因：</strong>{record.reasonText}</p>
          <p><strong>举报描述：</strong>{record.description}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          <p><strong>举报时间：</strong>{record.createTime}</p>
          {record.status !== 0 && (
            <>
              <p><strong>处理人：</strong>{record.handleBy}</p>
              <p><strong>处理时间：</strong>{record.handleTime}</p>
              <p><strong>处理结果：</strong>{record.handleResult}</p>
            </>
          )}
          {record.targetContent && (
            <>
              <p><strong>被举报内容：</strong></p>
              <div className="report-content">{record.targetContent}</div>
            </>
          )}
        </div>
      )
    })
  }

  // 打开处理弹窗
  const handleOpenModal = (record) => {
    setCurrentReport(record)
    handleForm.resetFields()
    setHandleModalVisible(true)
  }

  // 提交处理
  const handleSubmit = async () => {
    try {
      const values = await handleForm.validateFields()
      message.success('处理成功')
      setHandleModalVisible(false)
      fetchReports()
    } catch (error) {
      console.error('处理失败:', error)
    }
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
      title: 'ID',
      dataIndex: 'id',
      width: 60
    },
    {
      title: '举报人',
      dataIndex: 'reporterName',
      width: 100
    },
    {
      title: '举报类型',
      dataIndex: 'targetType',
      width: 90,
      render: (type) => (
        <Tag color={targetTypeMap[type]?.color}>
          {targetTypeMap[type]?.text}
        </Tag>
      )
    },
    {
      title: '被举报对象',
      dataIndex: 'targetTitle',
      ellipsis: true,
      width: 150
    },
    {
      title: '被举报用户',
      dataIndex: 'targetAuthor',
      width: 100
    },
    {
      title: '举报原因',
      dataIndex: 'reasonText',
      width: 100
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status) => (
        <Tag color={statusMap[status]?.color}>
          {statusMap[status]?.text}
        </Tag>
      )
    },
    {
      title: '举报时间',
      dataIndex: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            type="text" 
            icon={<EyeOutlined />} 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          {record.status === 0 && (
            <Button 
              type="text" 
              icon={<CheckOutlined />} 
              onClick={() => handleOpenModal(record)}
            >
              处理
            </Button>
          )}
        </Space>
      )
    }
  ]

  if (!userInfo) {
    return null
  }

  return (
    <div className="report-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 举报处理</h1>
        </div>
        <div className="header-right">
          <span className="user-name">{userInfo.username}</span>
          <Button onClick={handleLogout}>退出</Button>
        </div>
      </header>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="targetType">
              <Select placeholder="举报类型" allowClear style={{ width: 120 }}>
                {Object.entries(targetTypeMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="处理状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
        </div>

        {/* 举报列表 */}
        <Table
          columns={columns}
          dataSource={reports}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchReports({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1100 }}
        />
      </main>

      {/* 处理弹窗 */}
      <Modal
        title="处理举报"
        open={handleModalVisible}
        onOk={handleSubmit}
        onCancel={() => setHandleModalVisible(false)}
        width={500}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={handleForm}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <p>举报类型：<strong>{currentReport && targetTypeMap[currentReport.targetType]?.text}</strong></p>
          <p>被举报对象：<strong>{currentReport?.targetTitle}</strong></p>
          <Form.Item
            name="handleResult"
            label="处理结果"
            rules={[{ required: true, message: '请输入处理结果' }]}
          >
            <TextArea rows={4} placeholder="请输入处理结果" maxLength={200} showCount />
          </Form.Item>
          <Form.Item
            name="action"
            label="处理动作"
            rules={[{ required: true, message: '请选择处理动作' }]}
          >
            <Select placeholder="请选择处理动作">
              <Option value={1}>删除违规内容</Option>
              <Option value={2}>警告用户</Option>
              <Option value={3}>封禁用户</Option>
              <Option value={4}>驳回举报</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ReportManage
