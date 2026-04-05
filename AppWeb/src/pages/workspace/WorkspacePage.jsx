import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { deleteActivity, getActivityList, publishActivity } from '../../api/activity'
import { createCategory, getCategoryList, updateCategory } from '../../api/category'
import { getDiscountActivityList } from '../../api/discount'
import { createFacility, deleteFacility, getFacilityList, updateFacility } from '../../api/facility'
import { adminDeleteComment, adminDeletePost, createTopic, deleteTopic, getCommentList, getPostList, getTopicList, updateTopic } from '../../api/forum'
import { deleteMarker, getFacilityHeat, getMapConfig, getMarkerList, getNavigationStatistics } from '../../api/map'
import {
  createMerchant,
  createMerchantCategory,
  deleteMerchant,
  deleteMerchantCategory,
  getMerchantList,
  getMerchantCategoryList,
  getMerchantStatistics,
  updateMerchant,
  updateMerchantCategory,
} from '../../api/merchant'
import { auditRegistration, getRegistrationList } from '../../api/registration'
import {
  createSecondhandCategory,
  deleteSecondhandCategory,
  deleteSecondhandItem,
  getSecondhandAdminList,
  getSecondhandCategoryList,
  getSecondhandStatistics,
  markSecondhandItemSold,
  offlineSecondhandItem,
  onlineSecondhandItem,
  updateSecondhandCategory,
} from '../../api/secondhand'
import { closeSignIn, getSignInList, openSignIn } from '../../api/signin'
import { disableUser, enableUser, getUserList } from '../../api/user'
import { getWorkspacePage } from '../../data/portalData'
import './WorkspacePage.css'

const colorMap = {
  true: 'green',
  false: 'default',
  0: 'red',
  1: 'green',
  正常: 'green',
  启用: 'green',
  PUBLISHED: 'green',
  APPROVED: 'green',
  COMPLETED: 'green',
  DRAFT: 'default',
  PENDING: 'orange',
  REJECTED: 'red',
  CANCELLED: 'red',
  ADMIN: 'red',
  TEACHER: 'blue',
  STUDENT: 'default',
}

const toSummaryRows = (obj, prefix = '') =>
  Object.entries(obj || {}).map(([label, value], index) => ({
    id: `${prefix}${label}-${index}`,
    label: prefix ? `${prefix}${label}` : label,
    value: Array.isArray(value) ? JSON.stringify(value) : String(value ?? '-'),
  }))

const loadWorkspaceData = async (pageKey, { current, pageSize, keyword, contextId }) => {
  switch (pageKey) {
    case 'user-manage': {
      const res = await getUserList({ page: current, size: pageSize, username: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-center': {
      const res = await getActivityList({ page: current, size: pageSize, title: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-category': {
      const res = await getCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'activity-audit': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getRegistrationList(contextId, { page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'activity-signin': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getSignInList(contextId, current, pageSize)
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-post': {
      const res = await getPostList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-comment': {
      if (!contextId) return { rows: [], total: 0 }
      const res = await getCommentList({ postId: contextId, page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'forum-topic': {
      const res = await getTopicList({ page: current, size: pageSize })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-restaurant': {
      const res = await getFacilityList({ type: 1, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-sports': {
      const res = await getFacilityList({ type: 2, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-teaching': {
      const res = await getFacilityList({ type: 3, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'facility-dormitory': {
      const res = await getFacilityList({ type: 4, page: current, size: pageSize, name: keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'map-config': {
      const res = await getMapConfig()
      const rows = toSummaryRows(res.data)
      return { rows, total: rows.length }
    }
    case 'map-marker': {
      const res = await getMarkerList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'map-analytics': {
      const [navigationRes, heatRes] = await Promise.all([getNavigationStatistics(), getFacilityHeat({ limit: 10 })])
      const rows = [
        ...toSummaryRows(navigationRes.data, 'navigation.'),
        ...(Array.isArray(heatRes.data)
          ? heatRes.data.map((item, index) => ({
              id: `heat-${index}`,
              label: item.markerName || item.facilityName || `heat-${index + 1}`,
              value: String(item.visitCount ?? item.navigationCount ?? item.count ?? '-'),
            }))
          : []),
      ]
      return { rows, total: rows.length }
    }
    case 'market-item':
    case 'market-audit': {
      const res = await getSecondhandAdminList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'market-category': {
      const res = await getSecondhandCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'discount-merchant': {
      const res = await getMerchantList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'discount-activity': {
      const res = await getDiscountActivityList({ page: current, size: pageSize, keyword })
      return { rows: res.data?.records || [], total: res.data?.total || 0 }
    }
    case 'discount-category': {
      const res = await getMerchantCategoryList()
      const rows = Array.isArray(res.data) ? res.data : []
      return { rows, total: rows.length }
    }
    case 'discount-analytics': {
      const [merchantStatRes, secondhandStatRes] = await Promise.all([getMerchantStatistics(), getSecondhandStatistics()])
      const rows = [
        ...toSummaryRows(merchantStatRes.data, 'discount.'),
        ...toSummaryRows(secondhandStatRes.data, 'secondhand.'),
      ]
      return { rows, total: rows.length }
    }
    default:
      return { rows: [], total: 0 }
  }
}

function renderCell(value, type, row) {
  if (type === 'tag' || type === 'status') {
    const text = value === undefined || value === null || value === '' ? '-' : String(value)
    return <Tag color={colorMap[text] || 'default'}>{text}</Tag>
  }

  if (row && value === undefined) {
    return '-'
  }

  return value ?? '-'
}

function WorkspacePage({ pageKey }) {
  const page = getWorkspacePage(pageKey)
  const [form] = Form.useForm()
  const [keyword, setKeyword] = useState('')
  const [status] = useState('全部')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [modalMode, setModalMode] = useState('create')
  const [editingRecord, setEditingRecord] = useState(null)
  const [merchantCategoryOptions, setMerchantCategoryOptions] = useState([])
  const [contextInput, setContextInput] = useState('')
  const [contextId, setContextId] = useState('')
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      if (!page) return
      setLoading(true)
      try {
        const result = await loadWorkspaceData(pageKey, {
          current: pagination.current,
          pageSize: pagination.pageSize,
          keyword,
          status,
          contextId,
        })
        if (!cancelled) {
          setRows(result.rows)
          setPagination((prev) => ({
            ...prev,
            total: result.total,
          }))
        }
      } catch (error) {
        if (!cancelled) {
          setRows([])
          setPagination((prev) => ({
            ...prev,
            total: 0,
          }))
        }
        console.error(`加载 ${pageKey} 数据失败:`, error)
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    run()
    return () => {
      cancelled = true
    }
  }, [contextId, page, pageKey, pagination.current, pagination.pageSize, keyword, status])

  const runAction = async (fn, successText) => {
    setActionLoading(true)
    try {
      await fn()
      message.success(successText)
      const result = await loadWorkspaceData(pageKey, {
        current: pagination.current,
        pageSize: pagination.pageSize,
        keyword,
        status,
        contextId,
      })
      setRows(result.rows)
      setPagination((prev) => ({ ...prev, total: result.total }))
    } catch (error) {
      message.error(error?.message || '操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  const formEnabledPages = [
    'activity-category',
    'forum-topic',
    'facility-restaurant',
    'facility-sports',
    'facility-teaching',
    'facility-dormitory',
    'market-category',
    'discount-category',
    'discount-merchant',
  ]

  const openCreateModal = async () => {
    setModalMode('create')
    setEditingRecord(null)
    form.resetFields()
    if (pageKey === 'discount-merchant') {
      const res = await getMerchantCategoryList()
      setMerchantCategoryOptions(Array.isArray(res.data) ? res.data : [])
    }
    setModalOpen(true)
  }

  const openEditModal = async (record) => {
    setModalMode('edit')
    setEditingRecord(record)
    if (pageKey === 'discount-merchant') {
      const res = await getMerchantCategoryList()
      setMerchantCategoryOptions(Array.isArray(res.data) ? res.data : [])
    }
    form.setFieldsValue({
      name: record.categoryName,
      sort: record.sort,
      status: record.status,
      topicName: record.topicName,
      topicIcon: record.topicIcon,
      description: record.description,
      isHot: record.isHot,
      facilityName: record.facilityName,
      facilityType: record.facilityType,
      location: record.location,
      longitude: record.longitude,
      latitude: record.latitude,
      images: typeof record.images === 'string' ? record.images : JSON.stringify(record.images || []),
      categoryName: record.categoryName,
      merchantName: record.merchantName,
      categoryId: record.categoryId,
      address: record.address,
      contactPhone: record.contactPhone,
      contactName: record.contactName,
      businessHours: record.businessHours,
      username: record.merchantUsername,
      password: record.merchantPassword,
    })
    setModalOpen(true)
  }

  const submitModal = async () => {
    try {
      const values = await form.validateFields()
      const actionMap = {
        'activity-category': {
          create: () => createCategory({ name: values.name, sort: values.sort, status: values.status }),
          edit: () => updateCategory(editingRecord.id, { name: values.name, sort: values.sort, status: values.status }),
        },
        'forum-topic': {
          create: () => createTopic(values),
          edit: () => updateTopic(editingRecord.id, values),
        },
        'facility-restaurant': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-sports': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-teaching': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'facility-dormitory': {
          create: () => createFacility(values),
          edit: () => updateFacility(editingRecord.id, values),
        },
        'market-category': {
          create: () => createSecondhandCategory(values),
          edit: () => updateSecondhandCategory(editingRecord.id, values),
        },
        'discount-category': {
          create: () => createMerchantCategory(values),
          edit: () => updateMerchantCategory(editingRecord.id, values),
        },
        'discount-merchant': {
          create: () => createMerchant(values),
          edit: () => updateMerchant(editingRecord.id, values),
        },
      }
      const entry = actionMap[pageKey]
      if (!entry) return
      await runAction(modalMode === 'create' ? entry.create : entry.edit, modalMode === 'create' ? '创建成功' : '更新成功')
      setModalOpen(false)
      form.resetFields()
    } catch (error) {
      if (!error?.errorFields) {
        message.error(error?.message || '提交失败')
      }
    }
  }

  const renderModalFields = () => {
    switch (pageKey) {
      case 'activity-category':
        return (
          <>
            <Form.Item name="name" label="分类名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
            </Form.Item>
          </>
        )
      case 'forum-topic':
        return (
          <>
            <Form.Item name="topicName" label="话题名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="topicIcon" label="图标地址">
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="isHot" label="是否热门">
              <Select options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '禁用' }]} />
            </Form.Item>
          </>
        )
      case 'facility-restaurant':
      case 'facility-sports':
      case 'facility-teaching':
      case 'facility-dormitory':
        return (
          <>
            <Form.Item name="facilityName" label="设施名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="facilityType" label="设施类型" rules={[{ required: true }]}>
              <Select options={[{ value: 1, label: '餐厅' }, { value: 2, label: '运动场' }, { value: 3, label: '教学楼' }, { value: 4, label: '宿舍' }]} />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="location" label="位置">
              <Input />
            </Form.Item>
            <Form.Item name="longitude" label="经度" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="latitude" label="纬度" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="images" label="图片(JSON数组字符串)">
              <Input.TextArea rows={2} />
            </Form.Item>
          </>
        )
      case 'market-category':
      case 'discount-category':
        return (
          <>
            <Form.Item name="categoryName" label="分类名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="sort" label="排序">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )
      case 'discount-merchant':
        return (
          <>
            <Form.Item name="merchantName" label="商家名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="categoryId" label="分类" rules={[{ required: true }]}>
              <Select options={merchantCategoryOptions.map((item) => ({ value: item.id, label: item.categoryName }))} />
            </Form.Item>
            <Form.Item name="description" label="介绍">
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item name="address" label="地址" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="contactName" label="联系人">
              <Input />
            </Form.Item>
            <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="businessHours" label="营业时间">
              <Input />
            </Form.Item>
            <Form.Item name="username" label="账号" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true }]}>
              <Input.Password />
            </Form.Item>
          </>
        )
      default:
        return null
    }
  }

  const renderRowActions = (record) => {
    switch (pageKey) {
      case 'user-manage':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => enableUser(record.id), '已启用用户')}>
              启用
            </Button>
            <Button size="small" danger loading={actionLoading} onClick={() => runAction(() => disableUser(record.id), '已禁用用户')}>
              禁用
            </Button>
          </Space>
        )
      case 'activity-center':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => publishActivity(record.id), '活动已发布')}>
              发布
            </Button>
            <Popconfirm title="确定删除该活动吗？" onConfirm={() => runAction(() => deleteActivity(record.id), '活动已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'activity-audit':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => auditRegistration(record.id, 'APPROVED'), '审核通过')}>
              通过
            </Button>
            <Button size="small" danger loading={actionLoading} onClick={() => runAction(() => auditRegistration(record.id, 'REJECTED'), '已拒绝')}>
              拒绝
            </Button>
          </Space>
        )
      case 'forum-post':
        return (
          <Popconfirm title="确定删除该帖子吗？" onConfirm={() => runAction(() => adminDeletePost(record.id), '帖子已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'forum-comment':
        return (
          <Popconfirm title="确定删除该评论吗？" onConfirm={() => runAction(() => adminDeleteComment(record.id), '评论已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'forum-topic':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该话题吗？" onConfirm={() => runAction(() => deleteTopic(record.id), '话题已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'activity-category':
        return <Button size="small" onClick={() => openEditModal(record)}>编辑</Button>
      case 'facility-restaurant':
      case 'facility-sports':
      case 'facility-teaching':
      case 'facility-dormitory':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该设施吗？" onConfirm={() => runAction(() => deleteFacility(record.id), '设施已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'map-marker':
        return (
          <Popconfirm title="确定删除该标记吗？" onConfirm={() => runAction(() => deleteMarker(record.id), '标记已删除')}>
            <Button size="small" danger loading={actionLoading}>
              删除
            </Button>
          </Popconfirm>
        )
      case 'market-item':
      case 'market-audit':
        return (
          <Space size="small">
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => onlineSecondhandItem(record.id), '物品已上架')}>
              上架
            </Button>
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => offlineSecondhandItem(record.id), '物品已下架')}>
              下架
            </Button>
            <Button size="small" loading={actionLoading} onClick={() => runAction(() => markSecondhandItemSold(record.id), '物品已标记售出')}>
              售出
            </Button>
            <Popconfirm title="确定删除该物品吗？" onConfirm={() => runAction(() => deleteSecondhandItem(record.id), '物品已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'market-category':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该分类吗？" onConfirm={() => runAction(() => deleteSecondhandCategory(record.id), '分类已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'discount-category':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该分类吗？" onConfirm={() => runAction(() => deleteMerchantCategory(record.id), '分类已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      case 'discount-merchant':
        return (
          <Space size="small">
            <Button size="small" onClick={() => openEditModal(record)}>
              编辑
            </Button>
            <Popconfirm title="确定删除该商家吗？" onConfirm={() => runAction(() => deleteMerchant(record.id), '商家已删除')}>
              <Button size="small" danger loading={actionLoading}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      default:
        return null
    }
  }

  const columns = useMemo(() => {
    if (!page?.columns?.length) return []
    const baseColumns = page.columns.map((column) => ({
      title: column.title,
      dataIndex: column.dataIndex,
      key: column.dataIndex,
      render: (value, record) => renderCell(value, column.type, record),
    }))
    const hasActions = [
      'user-manage',
      'activity-center',
      'activity-category',
      'activity-audit',
      'forum-post',
      'forum-comment',
      'forum-topic',
      'facility-restaurant',
      'facility-sports',
      'facility-teaching',
      'facility-dormitory',
      'map-marker',
      'market-item',
      'market-audit',
      'market-category',
      'discount-category',
      'discount-merchant',
    ].includes(pageKey)

    if (!hasActions) return baseColumns

    return [
      ...baseColumns,
      {
        title: '操作',
        key: 'actions',
        render: (_, record) => renderRowActions(record),
      },
    ]
  }, [page, pageKey, actionLoading])

  if (!page) {
    return <Empty description="页面配置不存在" />
  }

  return (
    <div className="workspace-page">
      <section className="workspace-hero">
        <div>
          <span className="workspace-badge">{page.badge}</span>
          <h1>{page.title}</h1>
          <p>{page.description}</p>
        </div>
      </section>

      <section className="workspace-main workspace-main-single">
        <Card
          className="workspace-table-card"
          title="真实数据列表"
          extra={
            columns.length ? (
              <div className="workspace-filters">
                <Input
                  allowClear
                  placeholder="搜索关键字"
                  prefix={<SearchOutlined />}
                  value={keyword}
                  onChange={(event) => {
                    setPagination((prev) => ({ ...prev, current: 1 }))
                    setKeyword(event.target.value)
                  }}
                />
                <Select
                  value="全部"
                  disabled
                  options={page.filters.status.map((item) => ({ value: item, label: item }))}
                />
                {page.requiresInput ? (
                  <>
                    <Input
                      placeholder={page.inputPlaceholder}
                      value={contextInput}
                      onChange={(event) => setContextInput(event.target.value)}
                    />
                    <Button
                      type="primary"
                      onClick={() => {
                        setPagination((prev) => ({ ...prev, current: 1 }))
                        setContextId(contextInput.trim())
                      }}
                    >
                      加载
                    </Button>
                  </>
                ) : null}
                {pageKey === 'activity-signin' && contextId ? (
                  <>
                    <Button size="small" loading={actionLoading} onClick={() => runAction(() => openSignIn(contextId), '签到已开启')}>
                      开启签到
                    </Button>
                    <Button size="small" loading={actionLoading} onClick={() => runAction(() => closeSignIn(contextId), '签到已关闭')}>
                      关闭签到
                    </Button>
                  </>
                ) : null}
                {formEnabledPages.includes(pageKey) ? (
                  <Button type="primary" onClick={openCreateModal}>
                    新增
                  </Button>
                ) : null}
              </div>
            ) : null
          }
        >
          {columns.length ? (
            <Table
              columns={columns}
              dataSource={rows}
              loading={loading}
              rowKey={(record) => record.id || record.key || JSON.stringify(record)}
              locale={{ emptyText: page.emptyText }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                showSizeChanger: true,
              }}
              onChange={(nextPagination) => {
                setPagination((prev) => ({
                  ...prev,
                  current: nextPagination.current,
                  pageSize: nextPagination.pageSize,
                }))
              }}
            />
          ) : (
            <Empty description={page.emptyText} />
          )}
        </Card>
      </section>

      <Modal
        open={modalOpen}
        title={modalMode === 'create' ? `新增${page.title}` : `编辑${page.title}`}
        onCancel={() => setModalOpen(false)}
        onOk={submitModal}
        confirmLoading={actionLoading}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {renderModalFields()}
        </Form>
      </Modal>
    </div>
  )
}

export default WorkspacePage
