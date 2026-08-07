import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AimOutlined,
  ArrowLeftOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  PushpinOutlined,
} from '@ant-design/icons'
import { Button, Card, Empty, Popconfirm, Select, Spin, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  deleteIndoorPosition,
  getFloorPlan,
  getFloorPlanPositions,
  getMapPlaceDetail,
  getMapPlaceList,
  saveIndoorPosition,
} from '../../api/mapPlace'
import './StallIndoorManage.css'

const getRows = (response) => {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.records)) return response.data.records
  return []
}

export default function StallIndoorManage() {
  const { canteenId } = useParams()
  const navigate = useNavigate()
  const [canteen, setCanteen] = useState(null)
  const [floors, setFloors] = useState([])
  const [selectedFloorId, setSelectedFloorId] = useState(null)
  const [floorPlan, setFloorPlan] = useState(null)
  const [stalls, setStalls] = useState([])
  const [positions, setPositions] = useState([])
  const [activeStallId, setActiveStallId] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const activeStall = useMemo(
    () => stalls.find((item) => String(item.id) === String(activeStallId)) || null,
    [activeStallId, stalls],
  )

  const positionByPlaceId = useMemo(
    () => new Map(positions.map((item) => [String(item.placeId), item])),
    [positions],
  )

  const loadBase = useCallback(async () => {
    setLoading(true)
    try {
      const [canteenResponse, floorResponse] = await Promise.all([
        getMapPlaceDetail(canteenId),
        getMapPlaceList({ sceneType: 'CANTEEN', parentId: canteenId }),
      ])
      const floorRows = getRows(floorResponse)
        .filter((item) => item.placeType === 'FLOOR')
        .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
      setCanteen(canteenResponse.data || null)
      setFloors(floorRows)
      setSelectedFloorId((current) => (
        floorRows.some((floor) => String(floor.id) === String(current))
          ? current
          : floorRows[0]?.id ?? null
      ))
    } finally {
      setLoading(false)
    }
  }, [canteenId])

  const loadFloor = useCallback(async () => {
    if (!selectedFloorId) {
      setFloorPlan(null)
      setStalls([])
      setPositions([])
      return
    }

    setLoading(true)
    try {
      const [planResponse, stallResponse] = await Promise.all([
        getFloorPlan(selectedFloorId),
        getMapPlaceList({ sceneType: 'CANTEEN', parentId: selectedFloorId }),
      ])
      const nextPlan = planResponse.data || null
      const stallRows = getRows(stallResponse)
        .filter((item) => item.placeType === 'CANTEEN_STALL')
        .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
      const positionResponse = nextPlan
        ? await getFloorPlanPositions(nextPlan.id)
        : null

      setFloorPlan(nextPlan)
      setStalls(stallRows)
      setPositions(getRows(positionResponse))
      setActiveStallId((current) => (
        stallRows.some((stall) => String(stall.id) === String(current))
          ? current
          : stallRows[0]?.id ?? null
      ))
    } finally {
      setLoading(false)
    }
  }, [selectedFloorId])

  useEffect(() => {
    loadBase()
  }, [loadBase])

  useEffect(() => {
    loadFloor()
  }, [loadFloor])

  const placeActiveStall = async (event) => {
    if (!floorPlan || !activeStall || saving) return
    const rect = event.currentTarget.getBoundingClientRect()
    const xRatio = Number((((event.clientX - rect.left) / rect.width) * 100).toFixed(4))
    const yRatio = Number((((event.clientY - rect.top) / rect.height) * 100).toFixed(4))

    setSaving(true)
    try {
      const response = await saveIndoorPosition(activeStall.id, {
        floorPlanId: floorPlan.id,
        xRatio,
        yRatio,
      })
      const saved = response.data
      setPositions((previous) => [
        ...previous.filter((item) => String(item.placeId) !== String(activeStall.id)),
        saved,
      ])
      message.success(`${activeStall.name}的楼层位置已保存`)
    } finally {
      setSaving(false)
    }
  }

  const removeActivePosition = async () => {
    const current = positionByPlaceId.get(String(activeStallId))
    if (!current) return
    await deleteIndoorPosition(current.id)
    setPositions((previous) => previous.filter((item) => item.id !== current.id))
    message.success('档口楼层位置已删除')
  }

  const selectedFloor = floors.find((floor) => String(floor.id) === String(selectedFloorId))

  const renderStallSelector = () => {
    if (!stalls.length) return null
    return (
      <div className="stall-indoor-selector">
        {stalls.map((stall, index) => {
          const positioned = positionByPlaceId.has(String(stall.id))
          const active = String(stall.id) === String(activeStallId)
          return (
            <button
              type="button"
              key={stall.id}
              className={`stall-indoor-chip${active ? ' active' : ''}${positioned ? ' positioned' : ''}`}
              onClick={() => setActiveStallId(stall.id)}
              title={positioned ? '已设置楼层位置' : '未设置楼层位置'}
            >
              <span className="chip-index">{index + 1}</span>
              <span className="chip-name">{stall.name}</span>
              {positioned ? <PushpinOutlined className="chip-icon" /> : <EnvironmentOutlined className="chip-icon" />}
            </button>
          )
        })}
      </div>
    )
  }

  return (
    <div className="stall-indoor-page">
      <Spin spinning={loading || saving}>
        <Card className="stall-section-card">
          <div className="stall-section-heading">
            <div>
              <Button
                type="link"
                icon={<ArrowLeftOutlined />}
                className="stall-section-back"
                onClick={() => navigate(`/facility/canteen/${canteenId}/stalls`)}
              >
                返回档口列表
              </Button>
              <h2>{canteen?.name || '食堂'} · 楼层档口定位</h2>
              <p>点击平面图即可为当前选中档口设置室内坐标。</p>
            </div>
            <div className="stall-section-tools">
              <Select
                value={selectedFloorId}
                onChange={setSelectedFloorId}
                placeholder="选择楼层"
                className="stall-floor-filter"
                options={floors.map((floor) => ({ value: floor.id, label: floor.name }))}
              />
              {activeStall && (
                <span className={`stall-indoor-active-tip${positionByPlaceId.has(String(activeStallId)) ? ' positioned' : ''}`}>
                  <AimOutlined />
                  {activeStall.name}
                </span>
              )}
              {positionByPlaceId.has(String(activeStallId)) && (
                <Popconfirm title="确定删除当前档口的楼层位置吗？" onConfirm={removeActivePosition}>
                  <Button danger icon={<DeleteOutlined />}>删除定位</Button>
                </Popconfirm>
              )}
            </div>
          </div>

          {renderStallSelector()}

          {!floorPlan?.imageUrl ? (
            <Empty
              description="该楼层还没有平面图，请先在“楼层与菜系”中上传"
              className="stall-indoor-empty"
            />
          ) : (
            <div className="stall-indoor-plan-wrapper">
              <div className="stall-indoor-plan" onClick={placeActiveStall}>
                <img src={floorPlan.imageUrl} alt="楼层平面图" />
                {stalls.map((stall, index) => {
                  const position = positionByPlaceId.get(String(stall.id))
                  if (!position) return null
                  const active = String(stall.id) === String(activeStallId)
                  return (
                    <span
                      key={stall.id}
                      className={`stall-indoor-marker${active ? ' active' : ''}`}
                      style={{ left: `${position.xRatio}%`, top: `${position.yRatio}%` }}
                      title={stall.name}
                    >
                      <i data-index={index + 1} />
                      <b>{stall.name}</b>
                    </span>
                  )
                })}
                {!positionByPlaceId.has(String(activeStallId)) && activeStall && (
                  <div className="stall-indoor-hint">
                    点击地图设置 <b>{activeStall.name}</b> 的位置
                  </div>
                )}
              </div>
              <p className="stall-indoor-caption">
                共 <b>{stalls.length}</b> 个档口，已定位 <b>{positions.length}</b> 个
              </p>
            </div>
          )}
        </Card>
      </Spin>
    </div>
  )
}
