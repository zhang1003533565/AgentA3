import { useState, useEffect } from 'react'
import { message, Modal, Form, Input, Button, Table, Tag, Space, Popconfirm, Select } from 'antd'
import { EditOutlined, LockOutlined, CheckOutlined, StopOutlined, SearchOutlined } from '@ant-design/icons'
import { getUserList, updateUser, enableUser, disableUser, resetPassword } from '../../api/user'
import './UserManage.css'

const { Option } = Select

// 角色映射
const roleMap = {
  'STUDENT': { text: '学生', color: 'blue' },
  'TEACHER': { text: '教师', color: 'green' },
  'ADMIN': { text: '管理员', color: 'red' }
}

function UserManage() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // 获取用户列表
  const fetchUsers = async (params = {}) => {
    setLoading(true)
    try {
      const res = await getUserList({
        page: pagination.current,
        size: pagination.pageSize,
        ...params
      })
      if (res.code === 200) {
        const records = res.data?.records || res.data?.list || res.data || []
        setUsers(Array.isArray(records) ? records : [])
        setPagination({
          ...pagination,
          total: res.data?.total || 0
        })
      }
    } catch (error) {
      console.error('获取用户列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchUsers()
  }, [pagination.current, pagination.pageSize])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchUsers(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchUsers()
  }

  // 打开编辑弹窗
  const handleEdit = (record) => {
    setModalTitle('编辑用户')
    setEditingId(record.id)
    form.setFieldsValue({
      realName: record.realName,
      phone: record.phone,
      email: record.email
    })
    setModalVisible(true)
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const res = await updateUser(editingId, values)
      if (res.code === 200) {
        alert('更新成功')
        setModalVisible(false)
        fetchUsers()
      }
    } catch (error) {
      console.error('更新失败:', error)
    }
  }

  // 启用用户
  const handleEnable = async (id) => {
    try {
      const res = await enableUser(id)
      if (res.code === 200) {
        alert('启用成功')
        fetchUsers()
      }
    } catch (error) {
      console.error('启用失败:', error)
    }
  }

  // 禁用用户
  const handleDisable = async (id) => {
    try {
      const res = await disableUser(id)
      if (res.code === 200) {
        alert('禁用成功')
        fetchUsers()
      }
    } catch (error) {
      console.error('禁用失败:', error)
    }
  }

  // 重置密码
  const handleResetPassword = async (id) => {
    const newPassword = prompt('请输入新密码：')
    if (!newPassword) return
    try {
      const res = await resetPassword(id, newPassword)
      if (res.code === 200) {
        alert('密码重置成功')
      }
    } catch (error) {
      console.error('重置密码失败:', error)
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
      title: '用户名',
      dataIndex: 'username',
      width: 120
    },
    {
      title: '姓名',
      dataIndex: 'realName',
      width: 100
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (role) => (
        <Tag color={roleMap[role]?.color}>
          {roleMap[role]?.text || role}
        </Tag>
      )
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 130
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 180,
      ellipsis: true
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (status) => (
        <Tag color={status === 1 ? 'green' : 'red'}>
          {status === 1 ? '启用' : '禁用'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
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
          {record.status === 1 ? (
            <Button 
              type="text" 
              danger
              icon={<StopOutlined />}
              onClick={() => handleDisable(record.id)}
            >
              禁用
            </Button>
          ) : (
            <Button 
              type="text" 
              icon={<CheckOutlined />}
              onClick={() => handleEnable(record.id)}
            >
              启用
            </Button>
          )}
          <Button 
            type="text" 
            icon={<LockOutlined />}
            onClick={() => handleResetPassword(record.id)}
          >
            重置密码
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div className="user-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 页面标题 */}
        <div className="page-header">
          <h2>用户管理</h2>
        </div>
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="username">
              <Input placeholder="搜索用户名" prefix={<SearchOutlined />} allowClear />
            </Form.Item>
            <Form.Item name="role">
              <Select placeholder="选择角色" allowClear style={{ width: 120 }}>
                <Option value="STUDENT">学生</Option>
                <Option value="TEACHER">教师</Option>
                <Option value="ADMIN">管理员</Option>
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                <Option value={1}>启用</Option>
                <Option value={0}>禁用</Option>
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
        </div>

        {/* 用户列表 */}
        <Table
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
          }}
        />
      </main>

      {/* 编辑弹窗 */}
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
            name="realName"
            label="姓名"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="请输入姓名" />
          </Form.Item>
          <Form.Item
            name="phone"
            label="手机号"
          >
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
          >
            <Input placeholder="请输入邮箱" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default UserManage
