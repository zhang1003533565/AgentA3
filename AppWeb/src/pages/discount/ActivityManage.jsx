import { useState, useEffect, useCallback } from 'react'
import {
 getDiscountActivityList, createDiscountActivity,
 updateDiscountActivity, deleteDiscountActivity,
 offlineDiscountActivity, getDiscountActivityDetail,
} from '../../api/discount'
import { getMerchantList } from '../../api/merchant'

export default function ActivityManage() {
 const [data, setData] = useState([])
 const [total, setTotal] = useState(0)
 const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
 const [keyword, setKeyword] = useState('')
 const [statusFilter, setStatusFilter] = useState('all')
 const [merchantOptions, setMerchantOptions] = useState([])

 // Modal state
 const [formOpen, setFormOpen] = useState(false)
 const [detailOpen, setDetailOpen] = useState(false)
 const [editingRecord, setEditingRecord] = useState(null)
 const [detailRecord, setDetailRecord] = useState(null)
 const [saving, setSaving] = useState(false)

 // Form state
 const [formData, setFormData] = useState({ title: '', merchantId: '', description: '', coverImage: '', startTime: '', endTime: '' })

 const fetchMerchants = useCallback(async () => {
 try {
  const res = await getMerchantList({ page: 1, size: 500 })
  setMerchantOptions((res.data?.records || []).map((m) => ({ value: m.id, label: m.merchantName })))
 } catch { /* non-critical */ }
 }, [])

 const fetchData = useCallback(async () => {
 try {
  const res = await getDiscountActivityList({
  page: pagination.current, size: pagination.pageSize,
  keyword: keyword || undefined,
      status: statusFilter !== 'all' ? Number(statusFilter) : undefined,
  })
  setData(res.data?.records || [])
  setTotal(res.data?.total || 0)
 } catch { /* ignore */ }
 }, [pagination, keyword, statusFilter])

 useEffect(() => { fetchMerchants() }, [fetchMerchants])
 useEffect(() => { fetchData() }, [fetchData])

 // ===== CRUD Actions =====
 const openCreate = () => {
 setEditingRecord(null)
 setFormData({ title: '', merchantId: '', description: '', coverImage: '', startTime: '', endTime: '' })
 setFormOpen(true)
 }

 const openEdit = (record) => {
 setEditingRecord(record)
 setFormData({
  title: record.title || '',
  merchantId: record.merchantId || '',
  description: record.description || '',
  coverImage: record.coverImage || '',
  startTime: record.startTime || '',
  endTime: record.endTime || '',
 })
 setFormOpen(true)
 }

 const openDetail = async (record) => {
 try {
  const res = await getDiscountActivityDetail(record.id)
  setDetailRecord(res.data || record)
 } catch { setDetailRecord(record) }
 setDetailOpen(true)
 }

 const handleDelete = async (id) => {
 if (!confirm('确定要删除此优惠活动吗？此操作不可撤销。')) return
 try {
  await deleteDiscountActivity(id)
  fetchData()
 } catch { alert('删除失败') }
 }

 const handleOffline = async (id) => {
 try {
  await offlineDiscountActivity(id)
  fetchData()
 } catch { alert('操作失败') }
 }

 const handleSubmit = async () => {
 if (!formData.title || !formData.merchantId) return alert('请填写活动名称和所属商家')
 setSaving(true)
 try {
  const fmtTime = (t) => t ? t.replace("T", " ") + ":00" : t
      const payload = { ...formData, merchantId: Number(formData.merchantId), startTime: fmtTime(formData.startTime), endTime: fmtTime(formData.endTime) }
  if (editingRecord) {
  await updateDiscountActivity(editingRecord.id, payload)
  } else {
  await createDiscountActivity(payload)
  }
  setFormOpen(false)
  fetchData()
 } catch (e) { alert('操作失败: ' + (e?.message || '')) }
 finally { setSaving(false) }
 }

 // ===== Status Badge =====
 const statusConfig = {
 0: { cls: 'bg-surface-container-highest text-on-surface-variant', label: '未开始' },
 1: { cls: 'bg-primary/10 text-primary', label: '进行中', dot: true },
 2: { cls: 'bg-tertiary-fixed text-tertiary', label: '已领完' },
 3: { cls: 'bg-surface-container-highest text-on-surface-variant', label: '已结束', dim: true },
 4: { cls: 'bg-error-container text-error', label: '已下架' },
 }

 const renderStatus = (status) => {
 const c = statusConfig[status] || statusConfig[3]
 return (
  <span className={`inline-flex items-center px-2 py-1 rounded-full font-label-sm text-label-sm font-bold ${c.cls}`}>
  {c.dot && <span className="w-1.5 h-1.5 rounded-full bg-primary mr-2 animate-pulse" />}
  {c.label}
  </span>
 )
 }

 // ===== Stats =====
 const activeCount = data.filter((r) => r.status === 1).length
 const endedCount = data.filter((r) => r.status === 3).length
 const offlineCount = data.filter((r) => r.status === 4).length

 return (
 <div className="min-h-screen p-6 space-y-6" style={{ background: '#f9f9f9' }} data-page="discount">
  {/* Page Header */}
  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
  <div>
   <h2 className="font-h2 text-h2 text-on-surface">活动管理</h2>
   <p className="font-body-md text-body-md text-on-surface-variant">管理全校范围内的促销活动与优惠券分发</p>
  </div>
  <div className="flex items-center gap-4">
   <select
   className="bg-surface-container-lowest border-outline-variant rounded-lg font-body-md text-body-md pl-4 pr-8 py-2 focus:ring-primary min-w-[130px]"
   value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
   >
   <option value="all">所有状态</option>
   <option value="1">进行中</option>
   <option value="2">已领完</option>
   <option value="3">已结束</option>
   <option value="4">已下架</option>
   </select>
   <button
   className="flex items-center gap-1 bg-primary text-on-primary px-6 py-3 rounded-lg shadow-sm hover:shadow-md hover:bg-primary-container transition-all active:scale-95"
   onClick={openCreate}
   >
   <span className="material-symbols-outlined text-[20px]">add</span>
   <span className="font-body-md text-body-md font-bold">新增活动</span>
   </button>
  </div>
  </div>

  {/* Stats Cards */}
  <div className="grid grid-cols-1 md:grid-cols-4 gap-4 px-6">
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">活动总数</span>
   <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
    <span className="material-symbols-outlined text-[20px]">campaign</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{total}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">优惠活动累计记录</div>
  </div>
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">进行中</span>
   <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
    <span className="material-symbols-outlined text-[20px]">rocket_launch</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{activeCount}</div>
   <div className="font-label-sm text-label-sm text-primary flex items-center gap-1 mt-1">
   <span className="material-symbols-outlined text-[14px]">trending_up</span>
   <span>当前有效活动</span>
   </div>
  </div>
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">已结束</span>
   <div className="w-8 h-8 rounded-full bg-tertiary/10 flex items-center justify-center text-tertiary">
    <span className="material-symbols-outlined text-[20px]">event_busy</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{endedCount}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">已过期的活动</div>
  </div>
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">已下架</span>
   <div className="w-8 h-8 rounded-full bg-error/10 flex items-center justify-center text-error">
    <span className="material-symbols-outlined text-[20px]">warning</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-error">{offlineCount}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">已下架的活动</div>
  </div>
  </div>

  {/* Table Card */}
  <div className="mb-lg">
  <div className="bg-surface-container-lowest rounded-xl shadow-md border border-outline-variant/10 overflow-hidden">
   {/* Search bar */}
   <div className="px-6 py-4 bg-surface-container-low flex items-center gap-4">
   <div className="relative">
    <span className="absolute left-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-outline text-[20px]">search</span>
    <input
    className="pl-10 pr-4 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-md font-body-md w-[280px] focus:ring-2 focus:ring-primary-container focus:border-primary transition-all"
    placeholder="搜索活动名称..." type="text"
    value={keyword} onChange={(e) => { setKeyword(e.target.value); setPagination({ ...pagination, current: 1 }) }}
    onKeyDown={(e) => { if (e.key === 'Enter') fetchData() }}
    />
   </div>
   </div>

   <div className="overflow-x-auto">
   <table className="w-full text-left border-collapse">
    <thead>
    <tr className="bg-surface-container-low border-b border-outline-variant/20">
     <th className="px-6 py-4 w-[100px] font-body-md text-body-md text-on-surface-variant font-bold">活动封面</th>
     <th className="px-6 py-4 font-body-md text-body-md text-on-surface-variant font-bold">活动名称</th>
     <th className="px-6 py-4 w-[120px] font-body-md text-body-md text-on-surface-variant font-bold">所属商家</th>
     <th className="px-6 py-4 w-[160px] font-body-md text-body-md text-on-surface-variant font-bold">时间范围</th>
     <th className="px-6 py-4 w-[100px] font-body-md text-body-md text-on-surface-variant font-bold">活动状态</th>
     <th className="px-6 py-4 font-body-md text-body-md text-on-surface-variant font-bold text-right">操作</th>
    </tr>
    </thead>
    <tbody className="divide-y divide-outline-variant/10">
    {data.length === 0 ? (
     <tr><td colSpan={6} className="px-6 py-8 text-center text-on-surface-variant">暂无活动数据</td></tr>
    ) : data.map((record) => {
     const sc = statusConfig[record.status] || statusConfig[3]
     return (
     <tr key={record.id} className={`hover:bg-surface-container-low/50 transition-colors group ${sc.dim ? 'opacity-75' : ''}`}>
      <td className="px-6 py-4">
      {record.coverImage ? (
       <img className="w-16 h-12 object-cover rounded shadow-sm border border-outline-variant/20" src={record.coverImage} alt="" />
      ) : (
       <div className="w-16 h-12 rounded bg-surface-container-low flex items-center justify-center text-outline text-[24px]">🎄</div>
      )}
      </td>
      <td className="px-6 py-4">
      <div className="font-body-md text-body-md text-on-surface font-bold">{record.title}</div>
      <div className="font-label-sm text-label-sm text-on-surface-variant">ID: {record.id}</div>
      </td>
      <td className="px-6 py-4">
      <div className="flex items-center gap-1">
       <span className="material-symbols-outlined text-[18px] text-primary">store</span>
       <span className="font-body-md text-body-md">{record.merchantName || '-'}</span>
      </div>
      </td>
      <td className="px-6 py-4 font-label-sm text-label-sm text-on-surface">
      <div>起: {record.startTime ? record.startTime.slice(0, 10) : '-'}</div>
      <div>止: {record.endTime ? record.endTime.slice(0, 10) : '-'}</div>
      </td>
      <td className="px-6 py-4">{renderStatus(record.status)}</td>
      <td className="px-6 py-4 text-right space-x-md">
      <button className="text-primary hover:underline font-body-md text-body-md" onClick={() => openDetail(record)}>查看</button>
      <button className="text-on-surface-variant hover:text-primary transition-colors" onClick={() => openEdit(record)}>编辑</button>
      {record.status === 1 ? (
       <button className="text-on-surface-variant hover:text-tertiary transition-colors" onClick={() => handleOffline(record.id)}>下架</button>
      ) : (
       <button className="text-error hover:text-red-700 transition-colors" onClick={() => handleDelete(record.id)}>删除</button>
      )}
      </td>
     </tr>
     )
    })}
    </tbody>
   </table>
   </div>

   {/* Pagination */}
   <div className="px-6 py-4 bg-surface-container-low flex items-center justify-between">
   <span className="font-label-sm text-label-sm text-on-surface-variant">
    共 {total} 条活动，第 {pagination.current} 页
   </span>
   <div className="flex items-center gap-1">
    <button
    className="p-1 rounded hover:bg-surface-container transition-colors disabled:opacity-40"
    disabled={pagination.current <= 1}
    onClick={() => setPagination((p) => ({ ...p, current: p.current - 1 }))}
    >
    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
    </button>
    {Array.from({ length: Math.min(5, Math.ceil(total / pagination.pageSize) || 1) }, (_, i) => i + 1).map((p) => (
    <button
     key={p}
     className={`w-8 h-8 rounded font-body-md text-body-md transition-colors ${p === pagination.current ? 'bg-primary text-on-primary' : 'hover:bg-surface-container'}`}
     onClick={() => setPagination((prev) => ({ ...prev, current: p }))}
    >{p}</button>
    ))}
    <button
    className="p-1 rounded hover:bg-surface-container transition-colors disabled:opacity-40"
    disabled={pagination.current >= Math.ceil(total / pagination.pageSize)}
    onClick={() => setPagination((p) => ({ ...p, current: p.current + 1 }))}
    >
    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
    </button>
   </div>
   </div>
  </div>
  </div>

  {/* ===== Create/Edit Modal (matches stitch _7 form) ===== */}
  {formOpen && (
  <div className="fixed inset-0 z-50 flex items-start justify-center" style={{ background: 'rgba(0,0,0,0.45)', paddingTop: '40px', paddingBottom: '40px' }} onClick={() => setFormOpen(false)}>
   <div className="bg-surface-container-lowest rounded-xl shadow-lg w-full max-w-5xl mx-4 max-h-full overflow-y-auto" onClick={(e) => e.stopPropagation()}>
   {/* Form Header */}
   <div className="p-6 border-b border-outline-variant/20 bg-surface-bright flex items-center gap-3">
    <div className="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
    <span className="material-symbols-outlined text-primary">add_task</span>
    </div>
    <div>
    <h3 className="font-h2 text-h2 text-primary">{editingRecord ? '编辑活动信息' : '活动基本信息'}</h3>
    <p className="text-on-surface-variant text-body-md">请填写完整的优惠活动详情以供审核发布</p>
    </div>
   </div>

   {/* Form Body */}
   <div className="p-6">
    <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
    {/* Left Column */}
    <div className="space-y-6">
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">活动标题 <span className="text-error">*</span></label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
      placeholder="输入引人注目的活动标题，如：学期末餐饮5折大促" type="text"
      value={formData.title} onChange={(e) => setFormData({ ...formData, title: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
     </div>
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">所属商家 <span className="text-error">*</span></label>
     <select className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all appearance-none"
      value={formData.merchantId} onChange={(e) => setFormData({ ...formData, merchantId: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     >
      <option value="">选择参与活动的商家</option>
      {merchantOptions.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
     </select>
     </div>
     <div className="space-y-2">
     </div>
     <div className="grid grid-cols-2 gap-4">
     <div className="space-y-2">
      <label className="block font-bold text-on-surface-variant">开始时间 <span className="text-error">*</span></label>
      <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all"
      style={{ boxShadow: 'none' }} type="datetime-local"
      value={formData.startTime} onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
      />
     </div>
     <div className="space-y-2">
      <label className="block font-bold text-on-surface-variant">结束时间 <span className="text-error">*</span></label>
      <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all"
      style={{ boxShadow: 'none' }} type="datetime-local"
      value={formData.endTime} onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
      />
     </div>
     </div>
     <div className="space-y-2">
     </div>
    </div>

    {/* Right Column — Image Upload */}
    <div className="space-y-6">
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">活动封面图</label>
     <div
      className="relative border-2 border-dashed border-outline-variant rounded-xl p-6 flex flex-col items-center justify-center gap-4 hover:border-primary/50 hover:bg-primary/5 cursor-pointer transition-all"
      style={{ minHeight: '200px' }}
      onClick={() => document.getElementById('cover-file-input').click()}
     >
      {formData.coverImage ? (
      <img src={formData.coverImage} alt="" className="max-w-full max-h-[180px] object-cover rounded-lg" />
      ) : (
      <>
       <span className="material-symbols-outlined text-on-surface-variant text-[48px]">add_photo_alternate</span>
       <div className="text-center">
       <p className="text-on-surface font-bold">点击或拖拽图片上传</p>
       <p className="text-on-surface-variant text-label-sm">推荐比例 16:9，文件大小不超过 5MB</p>
       </div>
      </>
      )}
      <input
      id="cover-file-input"
      className="absolute inset-0 opacity-0 cursor-pointer"
      type="file"
      accept="image/*"
      onChange={(e) => {
       const file = e.target.files?.[0]
       if (file) setFormData({ ...formData, coverImage: URL.createObjectURL(file) })
      }}
      />
     </div>
     {formData.coverImage && (
      <div className="space-y-2 mt-2">
      <label className="block font-bold text-on-surface-variant">或输入图片 URL</label>
      <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all"
       style={{ boxShadow: 'none' }} placeholder="https://..."
       value={formData.coverImage} onChange={(e) => setFormData({ ...formData, coverImage: e.target.value })}
       onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
       onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
      />
      </div>
     )}
     </div>
    </div>
    </div>

    {/* Full Width — Rich Text Description */}
    <div className="space-y-2 mt-6">
    <label className="block font-bold text-on-surface-variant">活动详细介绍</label>
    <div className="border border-outline-variant rounded-xl overflow-hidden bg-surface-bright">
     <div className="flex gap-3 p-2 border-b border-outline-variant bg-surface-container-low">
     <button type="button" className="p-1 rounded hover:bg-surface-variant transition-colors" title="加粗">
      <span className="material-symbols-outlined text-[20px]">format_bold</span>
     </button>
     <button type="button" className="p-1 rounded hover:bg-surface-variant transition-colors" title="斜体">
      <span className="material-symbols-outlined text-[20px]">format_italic</span>
     </button>
     <button type="button" className="p-1 rounded hover:bg-surface-variant transition-colors" title="列表">
      <span className="material-symbols-outlined text-[20px]">format_list_bulleted</span>
     </button>
     </div>
     <textarea
     className="w-full px-4 py-3 border-none bg-transparent font-body-md focus:ring-0 resize-none"
     style={{ outline: 'none', boxShadow: 'none' }}
     placeholder="请详细描述活动内容、亮点及流程，吸引更多同学参与..."
     rows={6}
     value={formData.description}
     onChange={(e) => setFormData({ ...formData, description: e.target.value })}
     />
    </div>
    </div>
   </div>

   {/* Action Buttons */}
   <div className="flex items-center justify-end gap-4 pt-8 border-t border-outline-variant/20 mx-6">
    <button className="px-8 py-2 rounded-full text-secondary font-bold hover:bg-secondary-container transition-all active:scale-95" onClick={() => setFormOpen(false)}>取消</button>
    <button className="flex items-center gap-2 px-8 py-2 bg-primary text-on-primary rounded-full font-bold shadow-lg hover:shadow-xl hover:bg-primary/90 transition-all active:scale-95" onClick={handleSubmit} disabled={saving}>
    <span className="material-symbols-outlined text-[20px]">{saving ? 'sync' : 'save'}</span>
    {saving ? '保存中...' : '保存发布'}
    </button>
   </div>
   </div>
  </div>
  )}

  {/* ===== Detail Modal ===== */}
  {detailOpen && detailRecord && (
  <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.45)' }} onClick={() => setDetailOpen(false)}>
   <div className="bg-surface-container-lowest rounded-xl shadow-lg w-full max-w-lg mx-4" onClick={(e) => e.stopPropagation()}>
   <div className="p-6 border-b border-outline-variant/20 flex items-center justify-between">
    <h3 className="font-h3 text-h3 text-on-surface">活动详情</h3>
    <button className="p-1 hover:bg-surface-container rounded-full transition-colors" onClick={() => setDetailOpen(false)}>
    <span className="material-symbols-outlined">close</span>
    </button>
   </div>
   <div className="p-6 space-y-4">
    <div>
    <span className="font-label-sm text-on-surface-variant">活动名称</span>
    <p className="font-body-lg text-on-surface font-bold">{detailRecord.title}</p>
    </div>
    <div className="grid grid-cols-2 gap-4">
    <div>
     <span className="font-label-sm text-on-surface-variant">所属商家</span>
     <p className="font-body-md">{detailRecord.merchantName || '-'}</p>
    </div>
    <div>
    </div>
    <div>
     <span className="font-label-sm text-on-surface-variant">开始时间</span>
     <p className="font-body-md">{detailRecord.startTime || '-'}</p>
    </div>
    <div>
     <span className="font-label-sm text-on-surface-variant">结束时间</span>
     <p className="font-body-md">{detailRecord.endTime || '-'}</p>
    </div>
    <div>
     <span className="font-label-sm text-on-surface-variant">状态</span>
     <div className="mt-1">{renderStatus(detailRecord.status)}</div>
    </div>
    </div>
    {detailRecord.coverImage && (
    <div>
     <span className="font-label-sm text-on-surface-variant">封面图片</span>
     <img className="mt-1 rounded-lg max-w-full max-h-48 object-cover" src={detailRecord.coverImage} alt="" />
    </div>
    )}
    <div>
    <span className="font-label-sm text-on-surface-variant">活动描述</span>
    <p className="font-body-md mt-1 text-on-surface">{detailRecord.description || '暂无描述'}</p>
    </div>
   </div>
   <div className="p-6 border-t border-outline-variant/20 bg-surface-container-low flex justify-end">
    <button className="px-8 py-2 rounded-full text-secondary font-bold hover:bg-secondary-container transition-all active:scale-95" onClick={() => setDetailOpen(false)}>关闭</button>
   </div>
   </div>
  </div>
  )}
 </div>
 )
}
