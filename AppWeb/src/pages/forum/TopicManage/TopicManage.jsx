import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, FireOutlined } from '@ant-design/icons'
import { getUserInfo, clearAuth } from '../../../utils/storage'
import './TopicManage.css'

// 模拟数据
const mockTopics = [
  {
    id: 1,
    topicName: '学习交流',
    topicIcon: '📚',
    description: '学习心得、课程讨论、资料分享',
    postCount: 256,
    isHot: 1,
    status: 1,
    createTime: '2026-01-01 00:00:00'
  },
  {
    id: 2,
    topicName: '运动健身',
    topicIcon: '🏃',
    description: '运动约伴、健身心得、赛事讨论',
    postCount: 128,
    isHot: 1,
    status: 1,
    createTime: '2026-01-01 00:00:00'
  },
  {
    id: 3,
    topicName: '二手交易',
    topicIcon: '💰',
    description: '二手物品买卖、闲置转让',
    postCount: 89,
    isHot: 0,
    status: 1,
    createTime: '2026-01-05 00:00:00'
  },
  {
    id: 4,
    topicName: '美食推荐',
    topicIcon: '🍜',
    description: '校园美食、餐厅推荐、美食分享',
    postCount: 167,
    isHot: 1,
    status: 1,
    createTime: '2026-01-10 00:00:00'
  },
  {
    id: 5,
    topicName: '求职招聘',
    topicIcon: '💼',
    description: '实习招聘、求职经验、职场分享',
    postCount: 45,
    isHot: 0,
    status: 1,
    createTime: '2026-02-01 00:00:00'
  },
  {
    id: 6,
    topicName: '校园生活',
    topicIcon: '🏫',
    description: '校园趣事、生活分享、问题求助',
    postCount: 312,
    isHot: 1,
    status: 1,
    createTime: '2026-01-01 00:00:00'
  }
]

function TopicManage() {
  const navigate = useNavigate()
  const [userInfo, setUserInfo] = useState(null)
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建话题')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchKeyword, setSearchKeyword] = useState('')

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

  // 获取话题列表
  const fetchTopics = async () => {
    setLoading(true)
    try {
      await new Promise(resolve => setTimeout(resolve, 500))
      setTopics(mockTopics)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (userInfo) {
      fetchTopics()
    }
  }, [userInfo])

  // 搜索过滤
  const filteredTopics = topics.filter(topic => 
    topic.topicName?.toLowerCase().includes(searchKeyword.toLowerCase())
  )

  // 打开创建弹窗
  const handleCreate = () => {
    setModalTitle('创建话题')
    setEditingId(null)
    form.resetFields()
    setModalVisible(true)
  }

  // 打开编辑弹窗
  const handleEdit = (record) => {
    setModalTitle('编辑话题')
    setEditingId(record.id)
    form.setFieldsValue({
      topicName: record.topicName,
      topicIcon: record.topicIcon,
      description: record.description,
      sort: record.sort || 0,
      isHot: record.isHot,
      status: record.status
    })
    setModalVisible(true)
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      message.success(editingId ? '更新成功' : '创建成功')
      setModalVisible(false)
      fetchTopics()
    } catch (error) {
      console.error('提交失败:', error)
    }
  }

  // 删除话题
  const handleDelete = async (id) => {
    message.success('删除成功')
    fetchTopics()
  }

  // 切换热门状态
  const handleToggleHot = (id, isHot) => {
    message.success(isHot ? '已设为热门' : '已取消热门')
    fetchTopics()
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
      title: '图标',
      dataIndex: 'topicIcon',
      width: 60,
      render: (icon) => <span style={{ fontSize: 20 }}>{icon}</span>
    },
    {
      title: '话题名称',
      dataIndex: 'topicName',
      render: (text, record) => (
        <Space>
          {text}
          {record.isHot === 1 && <Tag color="red" icon={<FireOutlined />}>热门</Tag>}
        </Space>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true
    },
    {
      title: '帖子数',
      dataIndex: 'postCount',
      width: 100,
      sorter: (a, b) => a.postCount - b.postCount
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => (
        <span className={`status-tag ${status === 1 ? 'active' : 'inactive'}`}>
          {status === 1 ? '启用' : '禁用'}
        </span>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button 
            type="text" 
            icon={<EditOutlined />} 
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button 
            type="text" 
            icon={<FireOutlined />}
            style={{ color: record.isHot === 1 ? '#ff4d4f' : '#999' }}
            onClick={() => handleToggleHot(record.id, record.isHot === 0 ? 1 : 0)}
          >
            {record.isHot === 1 ? '取消热门' : '设为热门'}
          </Button>
          <Popconfirm
            title="确定删除该话题吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="text" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  if (!userInfo) {
    return null
  }

  return (
    <div className="topic-manage-container">
      {/* 顶部导航 */}
      <header className="manage-header">
        <div className="header-left">
          <h1>智慧校园 - 话题管理</h1>
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
          <Input
            placeholder="搜索话题名称"
            prefix={<SearchOutlined />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            style={{ width: 250 }}
            allowClear
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            创建话题
          </Button>
        </div>

        {/* 话题列表 */}
        <Table
          columns={columns}
          dataSource={filteredTopics}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `共 ${total} 条`
          }}
        />
      </main>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={modalTitle}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={500}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="topicName"
            label="话题名称"
            rules={[{ required: true, message: '请输入话题名称' }]}
          >
            <Input placeholder="请输入话题名称" maxLength={20} showCount />
          </Form.Item>

          <Form.Item
            name="topicIcon"
            label="话题图标"
            rules={[{ required: true, message: '请输入话题图标' }]}
          >
            <Input placeholder="请输入emoji图标，如：📚" maxLength={4} />
          </Form.Item>

          <Form.Item
            name="description"
            label="话题描述"
            rules={[{ required: true, message: '请输入话题描述' }]}
          >
            <Input.TextArea rows={3} placeholder="请输入话题描述" maxLength={100} showCount />
          </Form.Item>

          <Form.Item
            name="sort"
            label="排序"
            initialValue={0}
          >
            <Input type="number" placeholder="数字越小排序越靠前" />
          </Form.Item>

          <Form.Item
            name="isHot"
            label="热门话题"
            valuePropName="checked"
            initialValue={false}
          >
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>

          <Form.Item
            name="status"
            label="状态"
            initialValue={1}
            valuePropName="checked"
            getValueFromEvent={(checked) => checked ? 1 : 0}
            getValueProps={(value) => ({ checked: value === 1 })}
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TopicManage
