import { useState, useEffect } from 'react'
import { message, Modal, Form, Input, Button, Table, Space, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, FireOutlined } from '@ant-design/icons'
import { getTopicList, createTopic, updateTopic, deleteTopic, toggleTopicHot } from '../../../api/forum'
import './TopicManage.css'

function TopicManage() {
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建话题')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchKeyword, setSearchKeyword] = useState('')

  // 获取话题列表
  const fetchTopics = async () => {
    setLoading(true)
    try {
      const res = await getTopicList()
      if (res.code === 200) {
        // 适配分页响应结构 { records: [...], total: ... }
        const records = res.data?.records || res.data?.list || res.data || []
        setTopics(Array.isArray(records) ? records : [])
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
      isHot: record.isHot === 1,
      status: record.status === 'ACTIVE'
    })
    setModalVisible(true)
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = {
        ...values,
        isHot: values.isHot ? 1 : 0,
        status: values.status ? 'ACTIVE' : 'INACTIVE'
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

  // 切换热门状态
  const handleToggleHot = async (id, isHot) => {
    try {
      const res = await toggleTopicHot(id, isHot)
      if (res.code === 200) {
        message.success(isHot ? '已设为热门' : '已取消热门')
        fetchTopics()
      }
    } catch (error) {
      console.error('操作失败:', error)
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
      render: (icon) => <span style={{ fontSize: 20 }}>{icon}</span>
    },
    {
      title: '话题名称',
      dataIndex: 'topicName',
      width: 150,
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
      ellipsis: true,
      width: 200
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
        <span className={`status-tag ${status === 'ACTIVE' ? 'active' : 'inactive'}`}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
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
      width: 280,
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

  return (
    <div className="topic-manage-container">
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
          scroll={{ x: 1280 }}
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
            initialValue={true}
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TopicManage
