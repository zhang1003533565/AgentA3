/**
 * 校园位置配置
 * 仅限本校范围，不接入GPS
 * 可根据实际学校情况修改以下数据
 */

// 校区列表
export const CAMPUS_LIST = [
  { value: 'south', label: '南校区' },
  { value: 'north', label: '北校区' },
  { value: 'east',  label: '东校区' },
  { value: 'west',  label: '西校区' },
  { value: 'main',  label: '主校区' }
]

// 各校区下的交易区域
export const CAMPUS_AREAS = {
  south: [
    { value: 'dorm_south',  label: '学生宿舍区' },
    { value: 'teaching_s',  label: '教学区' },
    { value: 'library_s',   label: '图书馆' },
    { value: 'canteen_s',   label: '食堂' },
    { value: 'gate_south',  label: '南门' }
  ],
  north: [
    { value: 'dorm_north',  label: '学生宿舍区' },
    { value: 'teaching_n',  label: '教学区' },
    { value: 'library_n',   label: '图书馆' },
    { value: 'canteen_n',   label: '食堂' },
    { value: 'gate_north',  label: '北门' }
  ],
  east: [
    { value: 'dorm_east',   label: '学生宿舍区' },
    { value: 'teaching_e',  label: '教学区' },
    { value: 'library_e',   label: '图书馆' },
    { value: 'canteen_e',   label: '食堂' },
    { value: 'gate_east',   label: '东门' }
  ],
  west: [
    { value: 'dorm_west',   label: '学生宿舍区' },
    { value: 'teaching_w',  label: '教学区' },
    { value: 'library_w',   label: '图书馆' },
    { value: 'canteen_w',   label: '食堂' },
    { value: 'gate_west',   label: '西门' }
  ],
  main: [
    { value: 'dorm_main',   label: '学生宿舍区' },
    { value: 'teaching_m',  label: '教学区' },
    { value: 'library_m',   label: '图书馆' },
    { value: 'canteen_m',   label: '食堂' },
    { value: 'gate_main',   label: '正门' },
    { value: 'square',      label: '中心广场' }
  ]
}

// 获取校区 label
export function getCampusLabel(value) {
  const c = CAMPUS_LIST.find(item => item.value === value)
  return c ? c.label : value
}

// 获取区域 label
export function getAreaLabel(campusValue, areaValue) {
  const areas = CAMPUS_AREAS[campusValue] || []
  const a = areas.find(item => item.value === areaValue)
  return a ? a.label : areaValue
}

// 格式化完整位置文本  e.g. "📍南校区 学生宿舍区"
export function formatLocationText(campus, area, pickupPoint) {
  const parts = []
  if (campus) parts.push(getCampusLabel(campus))
  if (area) parts.push(getAreaLabel(campus, area))
  if (pickupPoint) parts.push(pickupPoint)
  return parts.join(' ')
}

// 获取校区下的区域列表
export function getAreasByCampus(campusValue) {
  return CAMPUS_AREAS[campusValue] || []
}
