import { useState, useEffect, useCallback } from 'react'
import {
  getMerchantCategoryList, createMerchantCategory,
  updateMerchantCategory, deleteMerchantCategory,
} from '../../api/merchant'

export default function CategoryManage() {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formData, setFormData] = useState({ categoryName: '', status: 1 })

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getMerchantCategoryList()
      let rows = Array.isArray(res.data) ? res.data : []
      if (keyword) rows = rows.filter((r) => r.categoryName && r.categoryName.includes(keyword))
      setData(rows)
    } catch { /* ignore */ }
    finally { setLoading(false) }
  }, [keyword])

  useEffect(() => { fetchData() }, [fetchData])

  const openCreate = () => {
    setEditingRecord(null)
    setFormData({ categoryName: '', status: 1 })
    setFormOpen(true)
  }

  const openEdit = (record) => {
    setEditingRecord(record)
    setFormData({ categoryName: record.categoryName || '', status: record.status ?? 1 })
    setFormOpen(true)
  }

  const handleDelete = async (id) => {
    if (!confirm('确定要删除此分类吗？')) return
    try { await deleteMerchantCategory(id); fetchData() }
    catch { alert('删除失败') }
  }

  const handleSubmit = async () => {
    if (!formData.categoryName) return alert('请输入分类名称')
    setSaving(true)
    try {
      if (editingRecord) { await updateMerchantCategory(editingRecord.id, formData) }
      else { await createMerchantCategory(formData) }
      setFormOpen(false)
      fetchData()
    } catch (e) { alert('操作失败: ' + (e?.message || '')) }
    finally { setSaving(false) }
  }

  const enabledCount = data.filter((r) => r.status === 1).length
  const disabledCount = data.filter((r) => r.status === 0).length

  return (
    <div className="min-h-screen p-6 space-y-6" style={{ background: '#f9f9f9' }} data-page="discount">

      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="font-h2 text-h2 text-on-surface">分类管理</h2>
          <p className="font-body-md text-body-md text-on-surface-variant">维护校园特惠商家分类，用于商家归类与前端筛选展示</p>
        </div>
        <button className="flex items-center gap-1 bg-primary text-on-primary px-6 py-3 rounded-lg shadow-sm hover:shadow-md hover:bg-primary-container transition-all active:scale-95" onClick={openCreate}>
          <span className="material-symbols-outlined text-[20px]">add</span>
          <span className="font-body-md text-body-md font-bold">新增分类</span>
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard icon="category" color="blue" label="分类总数" value={data.length} sub="已创建的分类" />
        <StatCard icon="check_circle" color="green" label="已启用" value={enabledCount} sub="前端可见的分类" />
        <StatCard icon="cancel" color="red" label="已停用" value={disabledCount} sub="已隐藏的分类" />
      </div>

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-xl shadow-md border border-outline-variant/10 overflow-hidden">
        <div className="px-6 py-4 bg-surface-container-low flex items-center gap-4">
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-outline text-[20px]">search</span>
            <input className="pl-10 pr-4 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-body-md font-body-md w-[240px] focus:ring-2 focus:ring-primary-container focus:border-primary transition-all" placeholder="搜索分类名称..." type="text" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant/20">
                <th className="px-6 py-3 font-body-md text-body-md text-on-surface-variant font-bold w-[60px]">编号</th>
                <th className="px-6 py-3 font-body-md text-body-md text-on-surface-variant font-bold text-center">分类名称</th>
                <th className="px-6 py-3 font-body-md text-body-md text-on-surface-variant font-bold text-center w-[90px]">状态</th>
                <th className="px-6 py-3 font-body-md text-body-md text-on-surface-variant font-bold text-right" style={{whiteSpace:'nowrap'}}>操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/10">
              {data.length === 0 ? (
                <tr><td colSpan={4} className="px-6 py-8 text-center text-on-surface-variant">{loading ? '加载中...' : '暂无分类数据'}</td></tr>
              ) : data.map((record) => (
                <tr key={record.id} className="hover:bg-surface-container-low/50 transition-colors group">
                  <td className="px-6 py-4 text-on-surface-variant" style={{fontSize:'13px'}}>#{record.id}</td>
                  <td className="px-6 py-4 text-center"><span className="font-body-md text-body-md text-primary font-bold">{record.categoryName}</span></td>
                  <td className="px-6 py-4 text-center">{record.status === 1 ? <span className="inline-flex items-center px-2 py-1 rounded-full font-label-sm text-label-sm font-bold" style={{ background: '#e6f4ea', color: '#1e7e34' }}>启用</span> : <span className="inline-flex items-center px-2 py-1 rounded-full font-label-sm text-label-sm font-bold" style={{ background: '#fce8e6', color: '#d93025' }}>停用</span>}</td>
                  <td className="px-6 py-4 text-right" style={{whiteSpace:'nowrap'}}>
                    <div className="flex items-center justify-end gap-4">
                      <button className="text-on-surface-variant hover:text-primary transition-colors flex items-center gap-1" onClick={() => openEdit(record)}><span className="material-symbols-outlined text-[18px]">edit</span> 编辑</button>
                      <button className="text-error hover:text-red-700 transition-colors flex items-center gap-1" onClick={() => handleDelete(record.id)}><span className="material-symbols-outlined text-[18px]">delete</span> 删除</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="px-6 py-4 bg-surface-container-low flex items-center">
          <span className="font-label-sm text-on-surface-variant">共 {data.length} 条分类</span>
        </div>
      </div>

      {/* Create/Edit Modal */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.45)' }} onClick={() => setFormOpen(false)}>
          <div className="bg-surface-container-lowest rounded-xl shadow-lg w-full max-w-lg mx-4" onClick={(e) => e.stopPropagation()}>
            <div className="p-6 border-b border-outline-variant/20 flex items-center gap-3">
              <div className="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
                <span className="material-symbols-outlined text-primary">category</span>
              </div>
              <div>
                <h3 className="font-h3 text-h3 text-primary">{editingRecord ? '编辑分类' : '新增分类'}</h3>
                <p className="text-on-surface-variant text-body-md">维护分类名称、排序和状态</p>
              </div>
            </div>
            <div className="p-6 space-y-6">
              <div>
                <label className="block font-bold text-on-surface-variant mb-xs">分类名称 <span className="text-error">*</span></label>
                <input className="w-full px-4 py-3 rounded-lg border border-outline-variant bg-surface-bright font-body-md focus:border-primary focus:ring-2 focus:ring-primary/10 transition-all" placeholder="例如：餐饮美食" value={formData.categoryName} onChange={(e) => setFormData({ ...formData, categoryName: e.target.value })} />
              </div>
              <div>
                <label className="block font-bold text-on-surface-variant mb-xs">状态</label>
                <div className="flex items-center gap-3">
                  <button type="button" className={`px-6 py-2 rounded-lg font-body-md font-bold transition-all ${formData.status === 1 ? 'bg-primary text-on-primary shadow-md' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`} onClick={() => setFormData({ ...formData, status: 1 })}>启用</button>
                  <button type="button" className={`px-6 py-2 rounded-lg font-body-md font-bold transition-all ${formData.status === 0 ? 'bg-primary text-on-primary shadow-md' : 'bg-surface-container-low text-on-surface-variant hover:bg-surface-container'}`} onClick={() => setFormData({ ...formData, status: 0 })}>停用</button>
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-outline-variant/20 bg-surface-container-low flex justify-end gap-4">
              <button className="px-8 py-2 rounded-full text-secondary font-bold hover:bg-secondary-container transition-all active:scale-95" onClick={() => setFormOpen(false)}>取消</button>
              <button className="px-8 py-2 bg-primary text-on-primary rounded-full font-bold shadow-lg hover:bg-primary-container transition-all active:scale-95 flex items-center gap-1" onClick={handleSubmit} disabled={saving}>
                <span className="material-symbols-outlined text-[20px]">{saving ? 'sync' : 'save'}</span>
                {saving ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function StatCard({ icon, color, label, value, sub }) {
  const colorMap = { blue: 'bg-primary/10 text-primary', green: 'bg-green-100 text-green-600', red: 'bg-error/10 text-error' }
  return (
    <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm border border-outline-variant/10 hover:-translate-y-1 transition-transform cursor-default">
      <div className="flex justify-between items-start mb-2">
        <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">{label}</span>
        <div className={`w-8 h-8 rounded-full flex items-center justify-center ${colorMap[color] || colorMap.blue}`}>
          <span className="material-symbols-outlined text-[20px]">{icon}</span>
        </div>
      </div>
      <div className="font-h1 text-h1 text-on-surface">{value}</div>
      <div className="font-label-sm text-label-sm text-on-surface-variant mt-1">{sub}</div>
    </div>
  )
}
