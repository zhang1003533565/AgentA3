import { useState, useEffect } from 'react'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Tag, Select, Card } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import { getTopicList, createTopic, updateTopic, deleteTopic } from '../../../api/forum'
import './TopicManage.css'

function TopicManage() {
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建话题')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // 获取话题列表
  const fetchTopics = async (params = {}) => {
    setLoading(true)
    try {
      const values = searchForm.getFieldsValue()
      const res = await getTopicList({
        page: pagination.current,
        size: pagination.pageSize,
        ...values,
        ...params
      })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setTopics(Array.isArray(records) ? records : [])
        setPagination({
          ...pagination,
          total: res.data?.total || records.length
        })
      }
    } catch (error) {
      console.error('获取话题列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTopics()
  }, [])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchTopics(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchTopics()
  }

  // 打开创建弹窗
  const handleCreate = () => {
    setModalTitle('创建话题')
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ isHot: 0, status: 'ACTIVE' })
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
      isHot: record.isHot === 1 ? 1 : 0,
      status: record.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'
    })
    setModalVisible(true)
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = {
        ...values,
        isHot: values.isHot === 1 ? 1 : 0,
        status: values.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'
      }
      
      let res
      if (editingId) {
        res = await updateTopic(editingId, data)
      } else {
        res = await createTopic(data)
      }

      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功')
        setModalVisible(false)
        fetchTopics()
      }
    } catch (error) {
      console.error('提交失败:', error)
    }
  }

  // 删除话题
  const handleDelete = async (id) => {
    try {
      const res = await deleteTopic(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchTopics()
      }
    } catch (error) {
      console.error('删除失败:', error)
    }
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
      render: (icon) => <span style={{ fontSize: 20 }}>{icon || '📌'}</span>
    },
    {
      title: '话题名称',
      dataIndex: 'topicName',
      width: 150,
      render: (text, record) => (
        <Space>
          {text}
          {record.isHot === 1 && <Tag color="red">热门</Tag>}
        </Space>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      width: 200,
      render: (text) => text || '-'
    },
    {
      title: '帖子数',
      dataIndex: 'postCount',
      width: 100,
      sorter: (a, b) => (a.postCount || 0) - (b.postCount || 0)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
        </Tag>
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
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button 
            type="text" 
            icon={<EditOutlined />} 
            onClick={() => handleEdit(record)}
          >
            编辑
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

  return (
    <div className="topic-manage-container">
      {/* 统计卡片 */}
      <div className="stat-cards">
        <Card size="small" className="stat-card">
          <div className="stat-value">{pagination.total}</div>
          <div className="stat-label">话题总数</div>
        </Card>
        <Card size="small" className="stat-card stat-green">
          <div className="stat-value">--</div>
          <div className="stat-label">启用中</div>
        </Card>
        <Card size="small" className="stat-card stat-orange">
          <div className="stat-value">--</div>
          <div className="stat-label">热门话题</div>
        </Card>
      </div>

      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索话题名称" prefix={<SearchOutlined />} allowClear style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="状态" allowClear style={{ width: 100 }}>
                <Option value="ACTIVE">启用</Option>
                <Option value="INACTIVE">禁用</Option>
              </Select>
            </Form.Item>
            <Form.Item name="isHot">
              <Select placeholder="热门" allowClear style={{ width: 80 }}>
                <Option value={1}>是</Option>
                <Option value={0}>否</Option>
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            创建话题
          </Button>
        </div>

        {/* 话题列表 */}
        <Table
          columns={columns}
          dataSource={topics}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50']
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchTopics({ page: pag.current, size: pag.pageSize })
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
            label="话题图标（Emoji）"
            rules={[{ required: true, message: '请输入话题图标' }]}
          >
            <Input placeholder="如：📚" maxLength={4} />
          </Form.Item>

          <Form.Item
            name="description"
            label="话题描述"
            rules={[{ required: true, message: '请输入话题描述' }]}
          >
            <Input.TextArea rows={3} placeholder="请输入话题描述" maxLength={100} showCount />
          </Form.Item>

          <Form.Item
            name="isHot"
            label="热门话题"
            initialValue={0}
            rules={[{ required: true, message: '请选择' }]}
          >
            <Select placeholder="请选择">
              <Option value={1}>是</Option>
              <Option value={0}>否</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="status"
            label="状态"
            initialValue="ACTIVE"
            rules={[{ required: true, message: '请选择' }]}
          >
            <Select placeholder="请选择">
              <Option value="ACTIVE">启用</Option>
              <Option value="INACTIVE">禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TopicManage
