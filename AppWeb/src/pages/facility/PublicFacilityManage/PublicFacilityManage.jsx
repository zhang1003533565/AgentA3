import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Button, Drawer, Form, Input, InputNumber, message, Modal, Pagination,
  Select, Tag, Empty, Spin, Popconfirm, Upload,
} from 'antd'
import {
  PlusOutlined, SearchOutlined, SettingOutlined, EditOutlined,
  DeleteOutlined, EnvironmentOutlined, UploadOutlined,
} from '@ant-design/icons'
import {
  createPublicFacility, deletePublicFacility,
  getPublicFacilities, updatePublicFacility,
} from '../../../api/publicFacility'
import './PublicFacilityManage.css'

const BUILTIN_TYPES = [
  { value: 'ALL', label: '全部', emoji: '📍', builtin: true },
  { value: 'BENCH', label: '长椅', emoji: '🪑', builtin: true },
  { value: 'STREET_LAMP', label: '路灯', emoji: '💡', builtin: true },
  { value: 'TRASH_BIN', label: '垃圾桶', emoji: '🗑️', builtin: true },
  { value: 'WATER_DISPENSER', label: '饮水机', emoji: '🚰', builtin: true },
  { value: 'BICYCLE_RACK', label: '自行车停放点', emoji: '🚲', builtin: true },
  { value: 'OTHER', label: '其他', emoji: '🔧', builtin: true },
]

const STORAGE_KEY = 'public-facility-custom-types'
const DISABLED_BUILTIN_KEY = 'public-facility-disabled-builtins'
const PAGE_SIZE = 12

const SORT_OPTIONS = [
  { value: '-createdAt', label: '最新创建' },
  { value: 'createdAt', label: '最早创建' },
  { value: 'name', label: '名称 A-Z' },
  { value: '-name', label: '名称 Z-A' },
  { value: 'distance', label: '距离最近' },
  { value: '-distance', label: '距离最远' },
]

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '正常', color: 'green' },
  { value: 'MAINTENANCE', label: '维护中', color: 'orange' },
  { value: 'INACTIVE', label: '停用', color: 'default' },
]

const loadCustomTypes = () => {
  let types = []
  let disabled = []
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      types = Array.isArray(parsed) ? parsed : []
    }
    const rawDisabled = localStorage.getItem(DISABLED_BUILTIN_KEY)
    if (rawDisabled) {
      const parsedDisabled = JSON.parse(rawDisabled)
      disabled = Array.isArray(parsedDisabled) ? parsedDisabled : []
    }
  } catch {}
  return { types, disabled }
}

const saveCustomTypes = (types, disabledBuiltins) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(types))
    if (disabledBuiltins !== undefined) {
      localStorage.setItem(DISABLED_BUILTIN_KEY, JSON.stringify(disabledBuiltins))
    }
  } catch (e) {
    console.warn('保存类型数据失败', e)
  }
}

const getTypeMeta = (typeValue, customTypes) => {
  const allTypes = [...BUILTIN_TYPES, ...customTypes]
  return allTypes.find((t) => t.value === typeValue) || { label: typeValue, emoji: '📍' }
}

const getStatusMeta = (status) => {
  return STATUS_OPTIONS.find((s) => s.value === status) || STATUS_OPTIONS[0]
}

/* 内部上传函数，不依赖 upload.js */
const doUploadImage = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const token = localStorage.getItem('token') || ''
  const response = await fetch('/api/upload/image?folder=public-facilities', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  const result = await response.json()
  if (!response.ok || result?.code !== 200) {
    throw new Error(result?.message || '图片上传失败')
  }
  return result?.data?.url || ''
}

function PublicFacilityManage() {
  const [loading, setLoading] = useState(false)
  const [list, setList] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [activeType, setActiveType] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  const [sortBy, setSortBy] = useState('-createdAt')
  const [customTypes, setCustomTypes] = useState(() => loadCustomTypes().types)
  const [disabledBuiltins, setDisabledBuiltins] = useState(() => loadCustomTypes().disabled)

  const [drawerVisible, setDrawerVisible] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [typeModalVisible, setTypeModalVisible] = useState(false)
  const [newTypeLabel, setNewTypeLabel] = useState('')
  const [newTypeEmoji, setNewTypeEmoji] = useState('📍')
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [fileList, setFileList] = useState([])
  const [uploading, setUploading] = useState(false)
  const searchInputRef = useRef(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getPublicFacilities({
        type: activeType === 'ALL' ? undefined : activeType,
        keyword: keyword || undefined,
        sortBy,
        page,
        size: PAGE_SIZE,
      })
      setList(res.data?.records || [])
      setTotal(res.data?.total || 0)
    } catch (e) {
      setList([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }, [activeType, keyword, sortBy, page])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const handleTypeClick = (typeValue) => {
    setActiveType(typeValue)
    setPage(1)
  }

  const handleSearch = () => {
    setPage(1)
    fetchData()
  }

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch()
  }

  const handleAdd = () => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({
      type: activeType === 'ALL' ? 'BICYCLE_RACK' : activeType,
    })
    setFileList([])
    setDrawerVisible(true)
  }

  const handleEdit = (item) => {
    setEditingId(item.id)
    form.setFieldsValue({
      name: item.name,
      type: item.type,
      location: item.location,
      description: item.description,
      status: item.status,
      latitude: item.latitude,
      longitude: item.longitude,
      distance: item.distance,
      imageUrl: item.imageUrl,
    })
    if (item.imageUrl) {
      setFileList([{ uid: '-1', name: 'image', thumbUrl: item.imageUrl, url: item.imageUrl }])
    } else {
      setFileList([])
    }
    setDrawerVisible(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      values.status = 'ACTIVE'
      values.distance = 0
      if (fileList.length > 0 && fileList[0].originFileObj) {
        try {
          setUploading(true)
          const url = await doUploadImage(fileList[0].originFileObj)
          values.imageUrl = url
        } catch (err) {
          message.error('图片上传失败')
          setUploading(false)
          return
        }
        setUploading(false)
      } else if (fileList.length > 0 && fileList[0].url) {
        values.imageUrl = fileList[0].url
      }
      if (editingId) {
        await updatePublicFacility(editingId, values)
        message.success('更新成功')
      } else {
        await createPublicFacility(values)
        message.success('创建成功')
      }
      setDrawerVisible(false)
      setFileList([])
      fetchData()
    } catch (e) {
      if (e?.message && !e?.showMessage) {
        message.error(e.message)
      }
    }
  }

  const handleDeleteClick = (item) => { setDeleteTarget(item) }

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return
    try {
      await deletePublicFacility(deleteTarget.id)
      message.success('删除成功')
      setDeleteTarget(null)
      if (list.length === 1 && page > 1) {
        setPage(page - 1)
      } else {
        fetchData()
      }
    } catch (e) {
      setDeleteTarget(null)
    }
  }

  const handleAddCustomType = () => {
    const label = newTypeLabel.trim()
    if (!label) { message.warning('请输入类型名称'); return }
    const value = `CUSTOM_${Date.now()}`
    const newType = { value, label, emoji: newTypeEmoji || '📍', builtin: false }
    const next = [...customTypes, newType]
    setCustomTypes(next)
    saveCustomTypes(next)
    setNewTypeLabel('')
    setNewTypeEmoji('📍')
    message.success('新增类型成功')
  }

  const handleDeleteBuiltinType = (typeValue) => {
    const next = [...disabledBuiltins, typeValue]
    setDisabledBuiltins(next)
    saveCustomTypes(customTypes, next)
    if (activeType === typeValue) setActiveType('ALL')
  }

  const handleDeleteCustomType = (typeValue) => {
    const next = customTypes.filter((t) => t.value !== typeValue)
    setCustomTypes(next)
    saveCustomTypes(next)
    if (activeType === typeValue) setActiveType('ALL')
    message.success('删除成功')
  }

  const handleRenameCustomType = (typeValue, newLabel) => {
    const next = customTypes.map((t) =>
      t.value === typeValue ? { ...t, label: newLabel } : t
    )
    setCustomTypes(next)
    saveCustomTypes(next)
  }

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (deleteTarget) setDeleteTarget(null)
        else if (drawerVisible) setDrawerVisible(false)
        else if (typeModalVisible) setTypeModalVisible(false)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [deleteTarget, drawerVisible, typeModalVisible])

  const allTypeTabs = useMemo(() => {
    return BUILTIN_TYPES.filter((t) => !disabledBuiltins.includes(t.value)).concat(customTypes)
  }, [customTypes, disabledBuiltins])

  const currentTypeMeta = useMemo(() => {
    return getTypeMeta(activeType, customTypes)
  }, [activeType, customTypes])

  const formTypeOptions = useMemo(() => {
    return BUILTIN_TYPES
      .filter((t) => t.value !== 'ALL' && !disabledBuiltins.includes(t.value))
      .concat(customTypes)
      .map((t) => ({ value: t.value, label: `${t.emoji} ${t.label}` }))
  }, [customTypes, disabledBuiltins])

  return (
    <div className="pfm-page">
      <div className="pfm-header">
        <div className="pfm-title-row">
          <h2 className="pfm-title">
            <span className="pfm-title-icon">{currentTypeMeta.emoji}</span>
            <span>{currentTypeMeta.label === '全部' ? '公共设施设置' : currentTypeMeta.label}</span>
            <Tag color="blue" className="pfm-badge">{total} 个结果</Tag>
          </h2>
          <div className="pfm-header-actions">
            <Button icon={<SettingOutlined />} onClick={() => setTypeModalVisible(true)} className="pfm-btn-outline">管理类型</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd} className="pfm-btn-primary">新增设施</Button>
          </div>
        </div>
      </div>

      <div className="pfm-filter-bar">
        <div className="pfm-type-tabs">
          {allTypeTabs.map((type) => (
            <button key={type.value} className={`pfm-type-tab ${activeType === type.value ? 'active' : ''}`} onClick={() => handleTypeClick(type.value)}>
              <span className="pfm-type-emoji">{type.emoji}</span>
              <span>{type.label}</span>
            </button>
          ))}
        </div>
        <div className="pfm-filter-right">
          <Select value={sortBy} onChange={setSortBy} options={SORT_OPTIONS} style={{ width: 140 }} size="middle" />
          <Input ref={searchInputRef} placeholder="搜索设施名称..." value={keyword} onChange={(e) => setKeyword(e.target.value)} onKeyDown={handleSearchKeyDown} onPressEnter={handleSearch} prefix={<SearchOutlined style={{ color: '#999' }} />} style={{ width: 220 }} allowClear className="pfm-search-input" />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
        </div>
      </div>

      <div className="pfm-card-grid-wrapper">
        <Spin spinning={loading}>
          {list.length === 0 && !loading ? (
            <div className="pfm-empty"><Empty description="暂无设施数据" /></div>
          ) : (
            <div className="pfm-card-grid">
              {list.map((item) => {
                const typeMeta = getTypeMeta(item.type, customTypes)
                const statusMeta = getStatusMeta(item.status)
                return (
                  <div key={item.id} className="pfm-card">
                    <div className="pfm-card-image-wrap">
                      {item.imageUrl ? (
                        <img src={item.imageUrl} alt={item.name} className="pfm-card-image" onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex' }} />
                      ) : null}
                      <div className="pfm-card-image-placeholder" style={{ display: item.imageUrl ? 'none' : 'flex' }}>
                        <span className="pfm-card-image-emoji">{typeMeta.emoji}</span>
                      </div>
                      {item.distance != null && item.distance !== undefined && (
                        <Tag className="pfm-distance-tag">{item.distance} m</Tag>
                      )}
                      <Tag color={statusMeta.color} className="pfm-status-tag">{statusMeta.label}</Tag>
                    </div>
                    <div className="pfm-card-body">
                      <h3 className="pfm-card-title">{item.name}</h3>
                      <div className="pfm-card-meta">
                        <Tag color="blue" className="pfm-type-tag">{typeMeta.emoji} {typeMeta.label}</Tag>
                        {item.location && (
                          <span className="pfm-card-location"><EnvironmentOutlined /> {item.location}</span>
                        )}
                      </div>
                      <p className="pfm-card-desc">{item.description || '暂无描述'}</p>
                      <div className="pfm-card-actions">
                        <Button type="text" size="small" icon={<EditOutlined />} onClick={() => handleEdit(item)} className="pfm-card-btn">编辑</Button>
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteClick(item)} className="pfm-card-btn">删除</Button>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </Spin>
      </div>

      {total > 0 && (
        <div className="pfm-pagination">
          <Pagination current={page} pageSize={PAGE_SIZE} total={total} onChange={(p) => setPage(p)} showSizeChanger={false} showTotal={(t) => `共 ${t} 条`} />
        </div>
      )}

      <Drawer
        title={editingId ? '编辑设施' : '新增设施'}
        open={drawerVisible}
        onClose={() => { setDrawerVisible(false); setFileList([]) }}
        width={480}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setDrawerVisible(false); setFileList([]) }} style={{ marginRight: 8 }}>取消</Button>
            <Button type="primary" onClick={handleSubmit} loading={uploading}>确定</Button>
          </div>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item label="设施名称" name="name" rules={[{ required: true, message: '请输入设施名称' }]}>
            <Input placeholder="请输入设施名称" maxLength={100} />
          </Form.Item>
          <Form.Item label="设施类型" name="type" rules={[{ required: true, message: '请选择设施类型' }]}>
            <Select placeholder="请选择设施类型" options={formTypeOptions} />
          </Form.Item>
          <Form.Item label="详细描述" name="description">
            <Input.TextArea rows={3} placeholder="请输入设施详细描述" maxLength={500} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item label="经度" name="longitude" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} placeholder="如 116.397428" precision={6} step={0.0001} />
            </Form.Item>
            <Form.Item label="纬度" name="latitude" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} placeholder="如 39.90923" precision={6} step={0.0001} />
            </Form.Item>
          </div>
          <Form.Item label="图片上传" name="imageUrl">
            <Upload
              listType="picture-card"
              fileList={fileList}
              maxCount={1}
              beforeUpload={(file) => {
                const isImage = file.type.startsWith('image/')
                if (!isImage) { message.error('只能上传图片文件'); return Upload.LIST_IGNORE }
                const isLt10M = file.size / 1024 / 1024 < 10
                if (!isLt10M) { message.error('图片不能超过10MB'); return Upload.LIST_IGNORE }
                const reader = new FileReader()
                reader.onload = (e) => {
                  setFileList([{ uid: '-1', name: file.name, thumbUrl: e.target.result, originFileObj: file }])
                }
                reader.readAsDataURL(file)
                return false
              }}
              onRemove={() => { setFileList([]); return true }}
            >
              {fileList.length === 0 && (
                <div>
                  <UploadOutlined />
                  <div style={{ marginTop: 8 }}>上传图片</div>
                </div>
              )}
            </Upload>
          </Form.Item>
        </Form>
      </Drawer>

      <Modal title="确认删除" open={!!deleteTarget} onCancel={() => setDeleteTarget(null)} onOk={handleDeleteConfirm} okText="删除" okButtonProps={{ danger: true }} cancelText="取消">
        <p>确定要删除设施 <strong>{deleteTarget?.name}</strong> 吗？此操作不可撤销。</p>
      </Modal>

      <Modal title="⚙️ 管理设施类型" open={typeModalVisible} onCancel={() => setTypeModalVisible(false)} footer={null} width={560} destroyOnClose>
        <div className="pfm-type-manage">
          <div className="pfm-type-section">
            <h4>内置类型</h4>
            <div className="pfm-type-list">
              {BUILTIN_TYPES.filter((t) => t.value !== 'ALL' && !disabledBuiltins.includes(t.value)).map((t) => (
                <div key={t.value} className="pfm-type-item">
                  <span className="pfm-type-item-emoji">{t.emoji}</span>
                  <span className="pfm-type-item-label">{t.label}</span>
                  <Popconfirm title={`确认移除「${t.label}」？移除后仍可通过刷新页面恢复。`} onConfirm={() => handleDeleteBuiltinType(t.value)} okText="移除" cancelText="取消" okButtonProps={{ danger: true }}>
                    <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                  </Popconfirm>
                </div>
              ))}
            </div>
          </div>
          <div className="pfm-type-section">
            <h4>自定义类型</h4>
            {customTypes.length === 0 && <p style={{ color: '#999', fontSize: 13 }}>暂无自定义类型</p>}
            <div className="pfm-type-list">
              {customTypes.map((t) => (
                <div key={t.value} className="pfm-type-item">
                  <span className="pfm-type-item-emoji">{t.emoji}</span>
                  <Input className="pfm-type-rename-input" value={t.label} onChange={(e) => handleRenameCustomType(t.value, e.target.value)} size="small" style={{ flex: 1, width: 'auto' }} />
                  <Popconfirm title="确认删除此类型？" onConfirm={() => handleDeleteCustomType(t.value)} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
                    <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                  </Popconfirm>
                </div>
              ))}
            </div>
            <div className="pfm-add-type-row">
              <Input placeholder="表情符号" value={newTypeEmoji} onChange={(e) => setNewTypeEmoji(e.target.value)} style={{ width: 70, textAlign: 'center' }} maxLength={4} />
              <Input placeholder="类型名称" value={newTypeLabel} onChange={(e) => setNewTypeLabel(e.target.value)} style={{ flex: 1 }} onPressEnter={handleAddCustomType} />
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAddCustomType}>添加</Button>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default PublicFacilityManage