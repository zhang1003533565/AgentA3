import { useState, useEffect, useCallback } from 'react'
import {
 getMerchantList, createMerchant, updateMerchant, deleteMerchant,
 getMerchantCategoryList, updateMerchantStatus,
} from '../../api/merchant'

const CATEGORY_COLORS = [
 'bg-primary-fixed text-on-primary-fixed-variant',
 'bg-tertiary-fixed text-on-tertiary-fixed-variant',
 'bg-secondary-fixed text-on-secondary-fixed-variant',
 'bg-orange-100 text-orange-600',
 'bg-purple-100 text-purple-600',
]

export default function MerchantManage() {
 const [data, setData] = useState([])
 const [total, setTotal] = useState(0)
 const [loading, setLoading] = useState(false)
 const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
 const [keyword, setKeyword] = useState('')
 const [categoryOptions, setCategoryOptions] = useState([])
 const [categoryFilter, setCategoryFilter] = useState('')

 const [formOpen, setFormOpen] = useState(false)
 const [editingRecord, setEditingRecord] = useState(null)
 const [saving, setSaving] = useState(false)
 const [formData, setFormData] = useState({
 merchantName: '', categoryId: '', description: '', address: '',
 contactName: '', contactPhone: '', businessHours: '', status: 1,
 })

 const fetchCategories = useCallback(async () => {
 try {
  const res = await getMerchantCategoryList()
  setCategoryOptions((Array.isArray(res.data) ? res.data : []).map((c) => ({ value: c.id, label: c.categoryName })))
 } catch { /* non-critical */ }
 }, [])

 const fetchData = useCallback(async () => {
 setLoading(true)
 try {
  const res = await getMerchantList({
  page: pagination.current, size: pagination.pageSize,
  keyword: keyword || undefined,
  categoryId: categoryFilter || undefined,
  })
  setData(res.data?.records || [])
  setTotal(res.data?.total || 0)
 } catch { /* ignore */ }
 finally { setLoading(false) }
 }, [pagination, keyword, categoryFilter])

 useEffect(() => { fetchCategories() }, [fetchCategories])
 useEffect(() => { fetchData() }, [fetchData])

 const openCreate = () => {
 setEditingRecord(null)
 setFormData({ merchantName: '', categoryId: '', description: '', address: '', contactName: '', contactPhone: '', businessHours: '', status: 1 })
 setFormOpen(true)
 }

 const openEdit = (record) => {
 setEditingRecord(record)
 setFormData({
  merchantName: record.merchantName || '',
  categoryId: record.categoryId || '',
  description: record.description || '',
  address: record.address || '',
  contactName: record.contactName || '',
  contactPhone: record.contactPhone || '',
  businessHours: record.businessHours || '',
  status: record.status ?? 1,
 })
 setFormOpen(true)
 }

 const handleDelete = async (id) => {
 if (!confirm('确定要删除此商家吗？')) return
 try {
  await deleteMerchant(id)
  fetchData()
 } catch { alert('删除失败') }
  }

  const handleStatusToggle = async (record) => {
    const nextStatus = record.status === 1 ? 0 : 1
    const label = nextStatus === 1 ? '营业中' : '休息中'
    if (!confirm('确定将状态改为「' + label + '」吗？')) return
    try { await updateMerchantStatus(record.id, { status: nextStatus }); fetchData() }
    catch { alert('状态更新失败') }
  }

 const handleSubmit = async () => {
 if (!formData.merchantName || !formData.categoryId) return alert('请填写商家名称和分类')
 setSaving(true)
 try {
  const payload = { ...formData, categoryId: Number(formData.categoryId) }
  if (editingRecord) {
  await updateMerchant(editingRecord.id, payload)
  } else {
  await createMerchant(payload)
  }
  setFormOpen(false)
  fetchData()
 } catch (e) { alert('操作失败: ' + (e?.message || '')) }
 finally { setSaving(false) }
 }

 const openCount = data.filter((r) => r.status === 1).length
 const closedCount = data.filter((r) => r.status === 0).length

 return (
 <div className="min-h-screen p-6 space-y-6" style={{ background: '#f9f9f9' }} data-page="discount">
  {/* Page Header */}
  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
  <div>
   <h2 className="font-h2 text-h2 text-on-surface">商家管理</h2>
   <p className="font-body-md text-body-md text-on-surface-variant">管理参与校园特惠活动的商家信息与登录账号</p>
  </div>
  <button
   className="flex items-center gap-1 bg-primary text-on-primary px-6 py-3 rounded-lg shadow-sm hover:shadow-md hover:bg-primary-container transition-all active:scale-95"
   onClick={openCreate}
  >
   <span className="material-symbols-outlined text-[20px]">add</span>
   <span className="font-body-md text-body-md font-bold">新增商家</span>
  </button>
  </div>

  {/* Stats */}
  <div className="grid grid-cols-1 md:grid-cols-3 gap-4 px-6">
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">商家总数</span>
   <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
    <span className="material-symbols-outlined text-[20px]">storefront</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{total}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">所有已注册商家</div>
  </div>
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">营业中</span>
   <div className="w-8 h-8 rounded-full bg-green-100 flex items-center justify-center text-green-600">
    <span className="material-symbols-outlined text-[20px]">check_circle</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{openCount}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">当前可接单商家</div>
  </div>
  <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
   <div className="flex justify-between items-start mb-2">
   <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">休息中</span>
   <div className="w-8 h-8 rounded-full bg-error/10 flex items-center justify-center text-error">
    <span className="material-symbols-outlined text-[20px]">cancel</span>
   </div>
   </div>
   <div className="font-h1 text-h1 text-on-surface">{closedCount}</div>
   <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">暂不营业商家</div>
  </div>
  </div>

  {/* Table Card */}
  <div className="mb-lg">
  <div className="bg-surface-container-lowest rounded-xl shadow-md border border-outline-variant/10 overflow-hidden">
   {/* Filters */}
   <div className="py-4 bg-surface-container-low flex items-center gap-4 flex-wrap">
   <div className="relative">
    <span className="absolute left-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-outline text-[20px]">search</span>
    <input
    className="pl-10 pr-4 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-md font-body-md w-[240px] focus:ring-2 focus:ring-primary-container focus:border-primary transition-all"
    placeholder="搜索商家名称..." type="text"
    value={keyword} onChange={(e) => { setKeyword(e.target.value); setPagination({ ...pagination, current: 1 }) }}
    onKeyDown={(e) => { if (e.key === 'Enter') fetchData() }}
    />
   </div>
   <div className="flex items-center gap-2">
    <span className="font-label-sm text-outline">分类:</span>
    <select
    className="bg-surface-container-lowest border-outline-variant rounded-lg pl-3 pr-8 py-2 font-body-md focus:ring-primary min-w-[130px]"
    value={categoryFilter} onChange={(e) => { setCategoryFilter(e.target.value); setPagination({ ...pagination, current: 1 }) }}
    >
    <option value="">全部分类</option>
    {categoryOptions.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
    </select>
   </div>
   </div>

   <div className="overflow-x-auto">
   <table className="w-full text-left border-collapse">
    <thead>
    <tr className="bg-surface-container-low border-b border-outline-variant/20">
     <th className="px-6 py-4 w-[30%] font-body-md text-body-md text-on-surface-variant font-bold">商家名称</th>
     <th className="px-6 py-4 w-[100px] font-body-md text-body-md text-on-surface-variant font-bold">分类</th>
     <th className="px-6 py-4 w-[130px] font-body-md text-body-md text-on-surface-variant font-bold">联系电话</th>
     <th className="px-6 py-4 font-body-md text-body-md text-on-surface-variant font-bold">地址</th>
     <th className="px-6 py-4 w-[100px] font-body-md text-body-md text-on-surface-variant font-bold">状态</th>
     <th className="px-6 py-4 font-body-md text-body-md text-on-surface-variant font-bold text-right">操作</th>
    </tr>
    </thead>
    <tbody className="divide-y divide-outline-variant/10">
    {data.length === 0 ? (
     <tr><td colSpan={6} className="px-6 py-8 text-center text-on-surface-variant">{loading ? '加载中...' : '暂无商家数据'}</td></tr>
    ) : data.map((record, idx) => (
     <tr key={record.id} className="hover:bg-surface-container-low/50 transition-colors group">
     <td className="px-6 py-4">
      <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center text-primary">
       <span className="material-symbols-outlined text-[20px]">store</span>
      </div>
      <div>
       <div className="font-body-md text-body-md text-on-surface font-bold">{record.merchantName}</div>
       <div className="font-label-sm text-on-surface-variant truncate max-w-[180px]">{record.address}</div>
      </div>
      </div>
     </td>
     <td className="px-6 py-4">
      {record.categoryName ? (
      <span className={`inline-flex items-center px-3 py-1 rounded-full font-label-sm text-label-sm ${CATEGORY_COLORS[idx % CATEGORY_COLORS.length]}`}>
       {record.categoryName}
      </span>
      ) : '-'}
     </td>
     <td className="px-6 py-4 font-body-md">{record.contactPhone || '-'}</td>
     <td className="px-6 py-4 font-body-md text-on-surface-variant max-w-[160px] truncate">{record.address || '-'}</td>
     <td className="px-6 py-4">
      {record.status === 1 ? (
      <span className="inline-flex items-center gap-1 text-green-600 font-body-md font-bold">
       <span className="w-2 h-2 rounded-full bg-green-500" /> 营业中
      </span>
      ) : (
      <span className="inline-flex items-center gap-1 text-on-surface-variant font-body-md font-bold">
       <span className="w-2 h-2 rounded-full bg-outline" /> 休息中
      </span>
      )}
     </td>
     <td className="px-6 py-4 text-right">
      <div className="flex items-center justify-end gap-4">
       <button className="text-on-surface-variant hover:text-tertiary transition-colors flex items-center gap-1" onClick={() => handleStatusToggle(record)}><span className="material-symbols-outlined text-[18px]">swap_horiz</span> 状态</button>
      <button className="text-on-surface-variant hover:text-primary transition-colors flex items-center gap-1" onClick={() => openEdit(record)}>
       <span className="material-symbols-outlined text-[18px]">edit</span> 编辑
      </button>
      <button className="text-error hover:text-red-700 transition-colors flex items-center gap-1" onClick={() => handleDelete(record.id)}>
       <span className="material-symbols-outlined text-[18px]">delete</span> 删除
      </button>
      </div>
     </td>
     </tr>
    ))}
    </tbody>
   </table>
   </div>

   {/* Pagination */}
   <div className="px-6 py-4 bg-surface-container-low flex items-center justify-between">
   <span className="font-label-sm text-label-sm text-on-surface-variant">共 {total} 条，第 {pagination.current} 页</span>
   <div className="flex items-center gap-1">
    <button className="p-1 rounded hover:bg-surface-container transition-colors disabled:opacity-40" disabled={pagination.current <= 1}
    onClick={() => setPagination((p) => ({ ...p, current: p.current - 1 }))}>
    <span className="material-symbols-outlined text-[20px]">chevron_left</span>
    </button>
    {Array.from({ length: Math.min(5, Math.ceil(total / pagination.pageSize) || 1) }, (_, i) => i + 1).map((p) => (
    <button key={p}
     className={`w-8 h-8 rounded font-body-md text-body-md transition-colors ${p === pagination.current ? 'bg-primary text-on-primary' : 'hover:bg-surface-container'}`}
     onClick={() => setPagination((prev) => ({ ...prev, current: p }))}>{p}</button>
    ))}
    <button className="p-1 rounded hover:bg-surface-container transition-colors disabled:opacity-40"
    disabled={pagination.current >= Math.ceil(total / pagination.pageSize)}
    onClick={() => setPagination((p) => ({ ...p, current: p.current + 1 }))}>
    <span className="material-symbols-outlined text-[20px]">chevron_right</span>
    </button>
   </div>
   </div>
  </div>
  </div>

  {/* Create/Edit Modal (matches stitch _11 form) */}
  {formOpen && (
  <div className="fixed inset-0 z-50 flex items-start justify-center" style={{ background: 'rgba(0,0,0,0.45)', paddingTop: '40px', paddingBottom: '40px' }} onClick={() => setFormOpen(false)}>
   <div className="bg-surface-container-lowest rounded-xl shadow-lg w-full max-w-4xl mx-4 max-h-full overflow-y-auto" onClick={(e) => e.stopPropagation()}>
   {/* Form Header */}
   <div className="p-6 border-b border-outline-variant/20 bg-surface-bright flex items-center gap-3">
    <div className="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
    <span className="material-symbols-outlined text-primary">storefront</span>
    </div>
    <div>
    <h3 className="font-h2 text-h2 text-primary">{editingRecord ? '编辑商家信息' : '新增商家信息'}</h3>
    <p className="text-on-surface-variant text-body-md">请填写商户基本信息以供后台管理</p>
    </div>
   </div>

   {/* Form Body */}
   <div className="p-6">
    <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
    {/* Left — Logo Upload */}
    <div className="space-y-2">
     <label className="block font-body-md text-body-md font-semibold text-on-surface">商家 Logo</label>
     <div className="relative group">
     <div
      className="w-full rounded-lg border-2 border-dashed border-outline-variant bg-surface-container-lowest flex flex-col items-center justify-center gap-3 overflow-hidden hover:border-primary transition-colors cursor-pointer"
      style={{ minHeight: '200px' }}
      onClick={() => document.getElementById('merchant-logo-input').click()}
     >
      <div className="flex flex-col items-center p-4 text-center">
      <span className="material-symbols-outlined text-[48px] text-outline-variant mb-sm">image</span>
      <p className="font-body-md text-body-md text-on-surface-variant">支持 JPG, PNG 格式</p>
      <p className="font-label-sm text-label-sm text-outline">建议比例 1:1, 大小不超过 2MB</p>
      </div>
     </div>
     <input id="merchant-logo-input" className="absolute inset-0 opacity-0 cursor-pointer" type="file" accept="image/*" />
     </div>
    </div>

    {/* Right — Form Fields */}
    <div className="space-y-6">
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">商家名称 <span className="text-error">*</span></label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
      placeholder="请输入商家全称" value={formData.merchantName}
      onChange={(e) => setFormData({ ...formData, merchantName: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
     </div>
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">商家分类 <span className="text-error">*</span></label>
     <select className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all appearance-none"
      value={formData.categoryId} onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     >
      <option value="">选择经营类目</option>
      {categoryOptions.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
     </select>
     </div>
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">联系电话 <span className="text-error">*</span></label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
      placeholder="请输入有效联系方式" type="tel" value={formData.contactPhone}
      onChange={(e) => setFormData({ ...formData, contactPhone: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
     </div>
     <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">营业时间</label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
      placeholder="例如：09:00-21:00" value={formData.businessHours}
      onChange={(e) => setFormData({ ...formData, businessHours: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
     </div>
    </div>
    </div>

    {/* Full Width Fields */}
    <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mt-lg">
    <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">联系人</label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
     placeholder="联系人姓名" value={formData.contactName}
     onChange={(e) => setFormData({ ...formData, contactName: e.target.value })}
     onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
     onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
    </div>
    <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">登录账号 <span className="text-error">*</span></label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
     placeholder="商家登录用户名" value={formData.username}
     onChange={(e) => setFormData({ ...formData, username: e.target.value })}
     onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
     onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
    </div>
    <div className="md:col-span-2 space-y-2">
     <label className="block font-bold text-on-surface-variant">详细地址 <span className="text-error">*</span></label>
     <div className="relative">
     <span className="material-symbols-outlined absolute left-3 top-3 text-on-surface-variant text-[20px]">location_on</span>
     <textarea className="w-full pl-8 pr-md py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all resize-none"
      style={{ boxShadow: 'none' }} rows={2} placeholder="请输入商家的具体物理地址，如：校区西门外商业街102号"
      value={formData.address} onChange={(e) => setFormData({ ...formData, address: e.target.value })}
      onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
      onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
     </div>
    </div>
    <div className="space-y-2">
     <label className="block font-bold text-on-surface-variant">登录密码 {editingRecord ? '' : <span className="text-error">*</span>}</label>
     <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all" style={{ boxShadow: 'none' }}
     type="password" placeholder={editingRecord ? '留空则不修改' : '设置密码'} value={formData.password}
     onChange={(e) => setFormData({ ...formData, password: e.target.value })}
     onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
     onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
     />
    </div>
    </div>

    <div className="space-y-2 mt-lg">
    <label className="block font-bold text-on-surface-variant">商家介绍</label>
    <textarea className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary transition-all resize-none"
     style={{ boxShadow: 'none' }} rows={3} placeholder="简要介绍商家特色、经营范围"
     value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })}
     onFocus={(e) => { e.target.style.borderColor = '#005daa'; e.target.style.boxShadow = '0 0 0 2px rgba(0,93,170,0.1)' }}
     onBlur={(e) => { e.target.style.borderColor = '#c0c7d6'; e.target.style.boxShadow = 'none' }}
    />
    </div>
   </div>

   {/* Tips */}
   <div className="mx-6 grid grid-cols-1 md:grid-cols-3 gap-4 opacity-60">
    <div className="bg-surface-container-high rounded-lg p-4 flex items-start gap-3">
    <span className="material-symbols-outlined text-primary">verified_user</span>
    <div>
     <p className="font-body-md text-body-md font-bold">合规核验</p>
     <p className="font-label-sm text-label-sm">所有入驻商家需经过校方资质审核。</p>
    </div>
    </div>
    <div className="bg-surface-container-high rounded-lg p-4 flex items-start gap-3">
    <span className="material-symbols-outlined text-primary">analytics</span>
    <div>
     <p className="font-body-md text-body-md font-bold">流量扶持</p>
     <p className="font-label-sm text-label-sm">优质商家将获得首页推荐位展示。</p>
    </div>
    </div>
    <div className="bg-surface-container-high rounded-lg p-4 flex items-start gap-3">
    <span className="material-symbols-outlined text-primary">help</span>
    <div>
     <p className="font-body-md text-body-md font-bold">需要帮助？</p>
     <p className="font-label-sm text-label-sm">如有问题，请联系系统管理员。</p>
    </div>
    </div>
   </div>

   {/* Actions */}
   <div className="flex items-center justify-end gap-4 pt-8 border-t border-outline-variant/20 mx-6 pb-6">
    <button className="px-8 py-2 rounded-full text-secondary font-bold hover:bg-secondary-container transition-all active:scale-95" onClick={() => setFormOpen(false)}>取消</button>
    <button className="flex items-center gap-2 px-8 py-2 bg-primary text-on-primary rounded-full font-bold shadow-lg hover:shadow-xl hover:bg-primary/90 transition-all active:scale-95" onClick={handleSubmit} disabled={saving}>
    <span className="material-symbols-outlined text-[20px]" style={{ fontVariationSettings: "'FILL' 1" }}>{saving ? 'sync' : 'save'}</span>
    {saving ? '保存中...' : '保存商户'}
    </button>
   </div>
   </div>
  </div>
  )}
 </div>
 )
}
