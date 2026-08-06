import { useCallback, useEffect, useMemo, useState } from 'react'
import { AimOutlined, ArrowLeftOutlined, DeleteOutlined } from '@ant-design/icons'
import { Button, Card, Empty, Popconfirm, Select, Spin, Tag, message } from 'antd'
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

  return (
    <div className="stall-indoor-page">
      <div className="stall-indoor-header">
        <div>
          <Button
            type="link"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/facility/canteen/${canteenId}/stalls`)}
          >
            返回档口列表
          </Button>
          <h1>{canteen?.name || '食堂'} · 楼层档口定位</h1>
          <p>这是楼层平面图上的室内位置，使用 X/Y 百分比坐标，不是校园地图经纬度。</p>
        </div>
        <Select
          value={selectedFloorId}
          onChange={setSelectedFloorId}
          placeholder="选择楼层"
          className="stall-indoor-floor-select"
          options={floors.map((floor) => ({ value: floor.id, label: floor.name }))}
        />
      </div>

      <Spin spinning={loading || saving}>
        <div className="stall-indoor-layout">
          <Card className="stall-indoor-stall-list" title="本层档口">
            {stalls.length ? stalls.map((stall) => {
              const positioned = positionByPlaceId.has(String(stall.id))
              const active = String(stall.id) === String(activeStallId)
              return (
                <button
                  type="button"
                  key={stall.id}
                  className={`stall-indoor-stall-item${active ? ' active' : ''}`}
                  onClick={() => setActiveStallId(stall.id)}
                >
                  <span>
                    <strong>{stall.name}</strong>
                    <small>{stall.locationDesc || '未填写位置说明'}</small>
                  </span>
                  <Tag color={positioned ? 'success' : 'default'}>
                    {positioned ? '已定位' : '未定位'}
                  </Tag>
                </button>
              )
            }) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该楼层暂无档口" />}
          </Card>

          <Card
            className="stall-indoor-plan-card"
            title={floors.find((floor) => String(floor.id) === String(selectedFloorId))?.name || '楼层平面图'}
            extra={activeStall ? (
              <span className="stall-indoor-active-tip">
                <AimOutlined /> 当前定位：{activeStall.name}
              </span>
            ) : null}
          >
            {!floorPlan?.imageUrl ? (
              <Empty
                description="该楼层还没有平面图，请先在“楼层与菜系”中上传"
                className="stall-indoor-empty"
              />
            ) : (
              <>
                <div className="stall-indoor-plan" onClick={placeActiveStall}>
                  <img src={floorPlan.imageUrl} alt="楼层平面图" />
                  {stalls.map((stall) => {
                    const position = positionByPlaceId.get(String(stall.id))
                    if (!position) return null
                    return (
                      <span
                        key={stall.id}
                        className={`stall-indoor-marker${String(stall.id) === String(activeStallId) ? ' active' : ''}`}
                        style={{ left: `${position.xRatio}%`, top: `${position.yRatio}%` }}
                      >
                        <i />
                        <b>{stall.name}</b>
                      </span>
                    )
                  })}
                </div>
                <div className="stall-indoor-plan-footer">
                  <span>选择左侧档口，然后点击平面图设置 X/Y 位置。</span>
                  {positionByPlaceId.has(String(activeStallId)) ? (
                    <Popconfirm title="确定删除当前档口的楼层位置吗？" onConfirm={removeActivePosition}>
                      <Button danger icon={<DeleteOutlined />}>删除当前定位</Button>
                    </Popconfirm>
                  ) : null}
                </div>
              </>
            )}
          </Card>
        </div>
      </Spin>
    </div>
  )
}
