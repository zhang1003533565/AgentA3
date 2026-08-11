import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Button,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Upload,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  GiftOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  ShopOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import SidePanel from '../../components/SidePanel/SidePanel'
import {
  getDiscountActivityList,
  createDiscountActivity,
  updateDiscountActivity,
  deleteDiscountActivity,
  endDiscountActivityEarly,
  getDiscountActivityDetail,
} from '../../api/discount'
import { getMerchantList } from '../../api/merchant'
import { uploadImage } from '../../api/upload'
import './ActivityManage.css'

const DISCOUNT_UPLOAD_FOLDER = 'discount-activities'

const STATUS_CONFIG = {
  0: { color: 'default', label: '未开始' },
  1: { color: 'processing', label: '进行中' },
  2: { color: 'warning', label: '已领完' },
  3: { color: 'default', label: '已结束' },
}

const fmtTime = (t) => (t ? t.replace('T', ' ') + ':00' : t)

export default function ActivityManage() {
  const [searchParams] = useSearchParams()
  const filterMerchantId = searchParams.get('merchantId')
  const filterMerchantName = searchParams.get('merchantName')

  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState(undefined)
  const [merchantOptions, setMerchantOptions] = useState([])
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailRecord, setDetailRecord] = useState(null)

  const fetchMerchants = useCallback(async () => {
    try {
      const res = await getMerchantList({ page: 1, size: 500 })
      setMerchantOptions(
        (res.data?.records || []).map((m) => ({
          value: m.id,
          label: m.merchantName,
        })),
      )
    } catch { /* non-critical */ }
  }, [])

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getDiscountActivityList({
        page: pagination.current,
        size: pagination.pageSize,
        keyword: keyword || undefined,
        status: statusFilter !== undefined ? Number(statusFilter) : undefined,
        merchantId: filterMerchantId || undefined,
      })
      setData(res.data?.records || [])
      setTotal(res.data?.total || 0)
    } catch {
      message.error('活动数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [pagination, keyword, statusFilter, filterMerchantId])

  useEffect(() => { fetchMerchants() }, [fetchMerchants])
  useEffect(() => { fetchData() }, [fetchData])

  const openCreate = () => {
    setEditingRecord(null)
    setEditorOpen(true)
  }

  const openEdit = (record) => {
    setEditingRecord(record)
    setEditorOpen(true)
  }

  const openDetail = async (record) => {
    try {
      const res = await getDiscountActivityDetail(record.id)
      setDetailRecord(res.data || record)
    } catch {
      setDetailRecord(record)
    }
    setDetailOpen(true)
  }

  const handleDelete = async (id) => {
    try {
      await deleteDiscountActivity(id)
      message.success('活动已删除')
      fetchData()
    } catch {
      message.error('删除失败')
    }
  }

  const handleEndEarly = async (id) => {
    Modal.confirm({
      title: '确认提前结束该活动？结束后状态将变为「已领完」',
      onOk: async () => {
        try {
          await endDiscountActivityEarly(id)
          message.success('活动已提前结束')
          fetchData()
        } catch {
          message.error('操作失败')
        }
      },
    })
  }

  const activeCount = data.filter((r) => r.status === 1).length
  const claimedCount = data.filter((r) => r.status === 2).length
  const endedCount = data.filter((r) => r.status === 3).length

  const columns = [
    {
      title: '活动封面',
      dataIndex: 'coverImage',
      width: 90,
      render: (url) =>
        url ? (
          <img src={url} alt="" className="activity-cover-img" />
        ) : (
          <div className="activity-cover-placeholder">🎄</div>
        ),
    },
    {
      title: '活动名称',
      dataIndex: 'title',
      width: 220,
      ellipsis: true,
      render: (text) => (
        <div className="activity-title">{text}</div>
      ),
    },
    {
      title: '所属商家',
      dataIndex: 'merchantName',
      width: 130,
      render: (text) => text || '-',
    },
    {
      title: '时间范围',
      width: 200,
      render: (_, record) => (
        <div className="time-range">
          <div><ClockCircleOutlined style={{ marginRight: 6 }} />{record.startTime ? record.startTime.slice(0, 10) : '-'}</div>
          <div><ClockCircleOutlined style={{ marginRight: 6 }} />{record.endTime ? record.endTime.slice(0, 10) : '-'}</div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status, record) => {
        // 已到结束时间且当前状态不是已结束的，自动显示为已结束
        const now = new Date()
        const endTime = record.endTime ? new Date(record.endTime) : null
        const displayStatus =
          endTime && endTime < now && [0, 1, 2].includes(status) ? 3 : status
        const cfg = STATUS_CONFIG[displayStatus] || STATUS_CONFIG[3]
        return <Tag color={cfg.color}>{cfg.label}</Tag>
      },
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
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetail(record)}
          >
            查看
          </Button>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          {record.status === 1 ? (
            <Button
              type="text"
              size="small"
              icon={<StopOutlined />}
              onClick={() => handleEndEarly(record.id)}
            >
              提前结束
            </Button>
          ) : (
            <Popconfirm
              title="确定删除此活动吗？"
              onConfirm={() => handleDelete(record.id)}
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
              >
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="discount-manage-page">
      <header className="discount-header">
        <div className="discount-header-left">
          <h2>
            {filterMerchantName ? (
              <span>{filterMerchantName} <span style={{ color: '#94a3b8', fontWeight: 400, fontSize: 16, margin: '0 4px' }}>›</span> 活动管理</span>
            ) : '活动管理'}
          </h2>
          <p>
            {filterMerchantName
              ? `查看「${filterMerchantName}」开展的优惠活动`
              : '管理全校范围内的促销活动与优惠券分发'}
          </p>
        </div>
        <div className="discount-header-right">
          <Input
            placeholder="搜索活动名称"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPagination((p) => ({ ...p, current: 1 }))
            }}
            onPressEnter={fetchData}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="所有状态"
            value={statusFilter}
            onChange={(v) => {
              setStatusFilter(v)
              setPagination((p) => ({ ...p, current: 1 }))
            }}
            allowClear
            style={{ width: 140 }}
            options={[
              { value: 1, label: '进行中' },
              { value: 2, label: '已领完' },
              { value: 3, label: '已结束' },
            ]}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增活动
          </Button>
        </div>
      </header>

      <div className="discount-stats">
        <button type="button" className="stat-item active">
          <div className="stat-icon-circle blue"><GiftOutlined /></div>
          <div className="stat-text-group">
            <span className="stat-number">{total}</span>
            <span className="stat-label">活动总数</span>
          </div>
        </button>
        <button type="button" className="stat-item">
          <div className="stat-icon-circle green"><PlayCircleOutlined /></div>
          <div className="stat-text-group">
            <span className="stat-number">{activeCount}</span>
            <span className="stat-label">进行中</span>
          </div>
        </button>
        <button type="button" className="stat-item">
          <div className="stat-icon-circle purple"><CheckCircleOutlined /></div>
          <div className="stat-text-group">
            <span className="stat-number">{claimedCount}</span>
            <span className="stat-label">已领完</span>
          </div>
        </button>
        <button type="button" className="stat-item">
          <div className="stat-icon-circle slate"><StopOutlined /></div>
          <div className="stat-text-group">
            <span className="stat-number">{endedCount}</span>
            <span className="stat-label">已结束</span>
          </div>
        </button>
      </div>

      <div className="discount-table-card">
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={data}
            rowKey="id"
            scroll={{ x: 1000 }}
            locale={{ emptyText: <Empty description="暂无活动数据" /> }}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
              onChange: (page, pageSize) =>
                setPagination({ current: page, pageSize }),
            }}
          />
        </Spin>
      </div>

      <ActivityEditor
        open={editorOpen}
        record={editingRecord}
        merchantOptions={merchantOptions}
        defaultMerchantId={filterMerchantId}
        saving={saving}
        onClose={() => setEditorOpen(false)}
        onSave={async (values) => {
          setSaving(true)
          try {
            const payload = {
              ...values,
              merchantId: Number(values.merchantId),
              startTime: fmtTime(values.startTime),
              endTime: fmtTime(values.endTime),
            }
            if (editingRecord) {
              await updateDiscountActivity(editingRecord.id, payload)
              message.success('活动已更新')
            } else {
              await createDiscountActivity(payload)
              message.success('活动已创建')
            }
            setEditorOpen(false)
            fetchData()
          } catch (e) {
            message.error(e?.message || '保存失败')
          } finally {
            setSaving(false)
          }
        }}
      />

      <Modal
        title="活动详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={
          <Button onClick={() => setDetailOpen(false)}>关闭</Button>
        }
        width={560}
      >
        {detailRecord && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="活动名称">
              {detailRecord.title}
            </Descriptions.Item>
            <Descriptions.Item label="所属商家">
              {detailRecord.merchantName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={(STATUS_CONFIG[detailRecord.status] || STATUS_CONFIG[3]).color}>
                {(STATUS_CONFIG[detailRecord.status] || STATUS_CONFIG[3]).label}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="开始时间">
              {detailRecord.startTime || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="结束时间">
              {detailRecord.endTime || '-'}
            </Descriptions.Item>
            {detailRecord.coverImage && (
              <Descriptions.Item label="封面图片">
                <img
                  src={detailRecord.coverImage}
                  alt=""
                  style={{ maxWidth: '100%', maxHeight: 200, borderRadius: 8 }}
                />
              </Descriptions.Item>
            )}
            <Descriptions.Item label="活动描述">
              {detailRecord.description || '暂无描述'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  )
}

function ActivityEditor({ open, record, merchantOptions, defaultMerchantId, saving, onClose, onSave }) {
  const [form] = Form.useForm()
  const [uploading, setUploading] = useState(false)
  const [previewUrl, setPreviewUrl] = useState('')
  const isEdit = Boolean(record)

  useEffect(() => {
    if (!open) return
    const cover = record?.coverImage || ''
    form.setFieldsValue({
      title: record?.title || '',
      merchantId: record?.merchantId || (defaultMerchantId ? Number(defaultMerchantId) : undefined),
      description: record?.description || '',
      coverImage: cover,
      startTime: record?.startTime?.slice(0, 16) || '',
      endTime: record?.endTime?.slice(0, 16) || '',
      status: record?.status ?? 1,
    })
    setPreviewUrl(cover)
  }, [record, form, open, defaultMerchantId])

  const submit = async () => {
    try {
      const values = await form.validateFields()
      onSave(values)
    } catch {
      // Ant Design shows validation errors
    }
  }

  const beforeUpload = async (file) => {
    if (!file.type?.startsWith('image/')) {
      message.warning('请选择图片文件')
      return false
    }
    if (file.size > 5 * 1024 * 1024) {
      message.warning('图片不能超过 5MB')
      return false
    }
    setUploading(true)
    try {
      const url = await uploadImage(file, DISCOUNT_UPLOAD_FOLDER)
      form.setFieldValue('coverImage', url)
      setPreviewUrl(url)
      message.success('封面上传成功')
    } catch (e) {
      message.error(e?.message || '封面上传失败')
    } finally {
      setUploading(false)
    }
    return false
  }

  return (
    <SidePanel
      title={isEdit ? '编辑活动' : '新增活动'}
      open={open}
      onClose={onClose}
      destroyOnHidden
      footer={
        <>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving || uploading} onClick={submit}>
            保存
          </Button>
        </>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="活动标题"
          name="title"
          rules={[{ required: true, message: '请输入活动标题' }]}
        >
          <Input placeholder="请输入活动标题" />
        </Form.Item>
        <Form.Item
          label="所属商家"
          name="merchantId"
          rules={[{ required: true, message: '请选择商家' }]}
        >
          <Select placeholder="选择参与活动的商家" options={merchantOptions} />
        </Form.Item>
        <Form.Item
          label="开始时间"
          name="startTime"
          rules={[{ required: true, message: '请选择开始时间' }]}
        >
          <Input type="datetime-local" />
        </Form.Item>
        <Form.Item
          label="结束时间"
          name="endTime"
          rules={[{ required: true, message: '请选择结束时间' }]}
        >
          <Input type="datetime-local" />
        </Form.Item>
        <Form.Item label="活动封面图">
          <Upload
            listType="picture-card"
            accept="image/jpeg,image/png,image/webp,image/gif"
            fileList={
              previewUrl
                ? [{ uid: '-1', name: '封面图', status: 'done', url: previewUrl }]
                : []
            }
            beforeUpload={beforeUpload}
            onRemove={() => {
              form.setFieldValue('coverImage', '')
              setPreviewUrl('')
            }}
            showUploadList={{ showPreviewIcon: true, showRemoveIcon: true }}
            disabled={uploading}
          >
            {previewUrl ? null : (
              <div>
                <UploadOutlined />
                <div style={{ marginTop: 8 }}>上传封面</div>
              </div>
            )}
          </Upload>
          <div className="discount-upload-tip">
            推荐比例 16:9，支持 JPG/PNG/WebP/GIF，不超过 5MB
          </div>
        </Form.Item>
        <Form.Item name="coverImage" hidden>
          <Input />
        </Form.Item>
        <Form.Item label="活动状态" name="status">
          <Select
            options={[
              { value: 0, label: '未开始' },
              { value: 1, label: '进行中' },
              { value: 2, label: '已领完' },
              { value: 3, label: '已结束' },
            ]}
          />
        </Form.Item>
        <Form.Item label="活动详细介绍" name="description">
          <Input.TextArea
            rows={6}
            placeholder="请详细描述活动内容、亮点及流程"
          />
        </Form.Item>
      </Form>
    </SidePanel>
  )
}
