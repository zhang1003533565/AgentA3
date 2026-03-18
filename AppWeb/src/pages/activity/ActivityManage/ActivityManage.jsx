import { useState, useEffect, useRef } from 'react'
import { message, Modal, Form, Input, Select, InputNumber, Button, Table, Tag, Space, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons'
import { getActivityList, createActivity, updateActivity, deleteActivity, getCategoryList } from '../../../api/activity'
import './ActivityManage.css'

const { TextArea } = Input
const { Option } = Select

// 活动状态映射
const statusMap = {
  0: { text: '草稿', color: 'default' },
  1: { text: '待审核', color: 'orange' },
  2: { text: '报名中', color: 'green' },
  3: { text: '报名结束', color: 'blue' },
  4: { text: '进行中', color: 'processing' },
  5: { text: '已结束', color: 'default' },
  6: { text: '已取消', color: 'red' }
}

function ActivityManage() {
  const [activities, setActivities] = useState([])
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建活动')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const initialized = useRef(false)

  // 获取活动列表
  const fetchActivities = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getActivityList({
        page: pagination.current,
        size: pagination.pageSize,
        ...params
      })
      if (res.code === 200) {
        setActivities(res.data?.records || [])
        setPagination({
          ...pagination,
          total: res.data?.total || 0
        })
      }
    } finally {
      setLoading(false)
    }
  }

  // 获取分类列表
  const fetchCategories = async () => {
    try {
      const res = await getCategoryList()
      if (res.code === 200) {
        setCategories(res.data || [])
      }
    } catch (error) {
      console.error('获取分类失败:', error)
    }
  }

  useEffect(() => {
    if (!initialized.current) {
      initialized.current = true
      fetchActivities()
      fetchCategories()
    }
  }, [])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchActivities(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchActivities()
  }

  // 打开创建弹窗
  const handleCreate = () => {
    setModalTitle('创建活动')
    setEditingId(null)
    form.resetFields()
    setModalVisible(true)
  }

  // 打开编辑弹窗
  const handleEdit = (record) => {
    setModalTitle('编辑活动')
    setEditingId(record.id)
    form.setFieldsValue({
      title: record.title,
      categoryId: record.categoryId,
      location: record.location,
      maxPeople: record.maxPeople,
      content: record.content,
      contactName: record.contactName,
      contactPhone: record.contactPhone,
      startTime: record.startTime,
      endTime: record.endTime,
      signupStartTime: record.signupStartTime,
      signupEndTime: record.signupEndTime
    })
    setModalVisible(true)
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = {
        title: values.title,
        categoryId: values.categoryId,
        location: values.location,
        maxPeople: values.maxPeople,
        content: values.content,
        contactName: values.contactName,
        contactPhone: values.contactPhone,
        startTime: values.startTime,
        endTime: values.endTime,
        signupStartTime: values.signupStartTime,
        signupEndTime: values.signupEndTime
      }

      let res
      if (editingId) {
        res = await updateActivity(editingId, data)
      } else {
        res = await createActivity(data)
      }

      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功')
        setModalVisible(false)
        fetchActivities()
      }
    } catch (error) {
      console.error('提交失败:', error)
    }
  }

  // 删除活动
  const handleDelete = async (id) => {
    try {
      const res = await deleteActivity(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchActivities()
      }
    } catch (error) {
      console.error('删除失败:', error)
    }
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '活动详情',
      width: 600,
      content: (
        <div className="activity-detail">
          <p><strong>标题：</strong>{record.title}</p>
          <p><strong>分类：</strong>{record.categoryName}</p>
          <p><strong>地点：</strong>{record.location}</p>
          <p><strong>人数：</strong>{record.currentPeople}/{record.maxPeople}</p>
          <p><strong>活动时间：</strong>{record.startTime} 至 {record.endTime}</p>
          <p><strong>报名时间：</strong>{record.signupStartTime} 至 {record.signupEndTime}</p>
          <p><strong>联系人：</strong>{record.contactName} ({record.contactPhone})</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          <p><strong>详情：</strong>{record.content}</p>
        </div>
      )
    })
  }

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 80
    },
    {
      title: '活动标题',
      dataIndex: 'title',
      ellipsis: true
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 100,
      render: (category) => category?.categoryName || '-'
    },
    {
      title: '地点',
      dataIndex: 'location',
      ellipsis: true,
      width: 150
    },
    {
      title: '报名人数',
      dataIndex: 'currentPeople',
      width: 100,
      render: (text, record) => `${text}/${record.maxPeople}`
    },
    {
      title: '活动开始时间',
      dataIndex: 'startTime',
      width: 160
    },
    {
      title: '报名截止时间',
      dataIndex: 'signupEndTime',
      width: 160
    },
    {
      title: '状态',
      dataIndex: 'status',
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
            icon={<EyeOutlined />} 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          <Button 
            type="text" 
            icon={<EditOutlined />} 
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除该活动吗？"
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
    <div className="activity-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索活动标题" prefix={<SearchOutlined />} allowClear />
            </Form.Item>
            <Form.Item name="categoryId">
              <Select placeholder="选择分类" allowClear style={{ width: 120 }}>
                {categories.map(cat => (
                  <Option key={cat.id} value={cat.id}>{cat.name}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            创建活动
          </Button>
        </div>

        {/* 活动列表 */}
        <Table
          columns={columns}
          dataSource={activities}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchActivities({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1200 }}
        />
      </main>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={modalTitle}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={700}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="title"
            label="活动标题"
            rules={[{ required: true, message: '请输入活动标题' }]}
          >
            <Input placeholder="请输入活动标题" maxLength={100} showCount />
          </Form.Item>

          <div className="form-row">
            <Form.Item
              name="categoryId"
              label="活动分类"
              rules={[{ required: true, message: '请选择活动分类' }]}
              style={{ flex: 1 }}
            >
              <Select placeholder="请选择分类">
                {categories.map(cat => (
                  <Option key={cat.id} value={cat.id}>{cat.name}</Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              name="maxPeople"
              label="人数上限"
              rules={[{ required: true, message: '请输入人数上限' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <InputNumber min={1} max={9999} placeholder="人数上限" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <Form.Item
            name="location"
            label="活动地点"
            rules={[{ required: true, message: '请输入活动地点' }]}
          >
            <Input placeholder="请输入活动地点" />
          </Form.Item>

          <div className="form-row">
            <Form.Item
              name="startTime"
              label="活动开始时间"
              rules={[{ required: true, message: '请输入活动开始时间' }]}
              style={{ flex: 1 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="endTime"
              label="活动结束时间"
              rules={[{ required: true, message: '请输入活动结束时间' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div className="form-row">
            <Form.Item
              name="signupStartTime"
              label="报名开始时间"
              rules={[{ required: true, message: '请输入报名开始时间' }]}
              style={{ flex: 1 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="signupEndTime"
              label="报名结束时间"
              rules={[{ required: true, message: '请输入报名结束时间' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div className="form-row">
            <Form.Item
              name="contactName"
              label="联系人"
              rules={[{ required: true, message: '请输入联系人' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="请输入联系人" />
            </Form.Item>

            <Form.Item
              name="contactPhone"
              label="联系电话"
              rules={[{ required: true, message: '请输入联系电话' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input placeholder="请输入联系电话" />
            </Form.Item>
          </div>

          <Form.Item
            name="content"
            label="活动详情"
            rules={[{ required: true, message: '请输入活动详情' }]}
          >
            <TextArea rows={4} placeholder="请输入活动详情" maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ActivityManage
