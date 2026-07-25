import { useEffect, useMemo, useRef, useState } from 'react'
import { Card, Empty, Skeleton } from 'antd'
import * as echarts from 'echarts'
import { getActivityList } from '../../api/activity'
import { getHotPosts, getHotTopics, getPostList } from '../../api/forum'
import { getFacilityHeat, getNavigationStatistics } from '../../api/map'
import { getMerchantStatistics } from '../../api/merchant'
import { getSecondhandStatistics } from '../../api/secondhand'
import { getUserList } from '../../api/user'
import './Home.css'

const formatNumber = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '-'
  return new Intl.NumberFormat('zh-CN').format(numeric)
}

const formatSecondsToMinuteText = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) return '-'
  if (numeric < 60) return `${numeric} 秒`
  return `${(numeric / 60).toFixed(1)} 分钟`
}

const getList = (value, fallback = []) => (Array.isArray(value) ? value : fallback)

function EChart({ option, height = 320 }) {
  const chartRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return undefined

    const chart = echarts.init(chartRef.current)
    chart.setOption(option)

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
    }
  }, [option])

  return <div ref={chartRef} style={{ width: '100%', height }} />
}

function Home() {
  const [loading, setLoading] = useState(true)
  const [dashboard, setDashboard] = useState({
    totalUsers: 0,
    totalActivities: 0,
    totalPosts: 0,
    totalNavigations: 0,
    todayNavigations: 0,
    averageNavigationDuration: 0,
    activeMerchants: 0,
    activeDiscountActivities: 0,
    onSaleItems: 0,
    soldItems: 0,
    hotTopics: [],
    hotPosts: [],
    hotFacilities: [],
    popularDestinations: [],
    recentActivities: [],
    secondhandCategoryDistribution: [],
    activityTrend: [],
  })

  useEffect(() => {
    let cancelled = false

    const loadDashboard = async () => {
      setLoading(true)
      try {
        const [
          userRes,
          activityRes,
          postRes,
          hotPostRes,
          hotTopicRes,
          navigationRes,
          facilityHeatRes,
          merchantStatRes,
          secondhandStatRes,
        ] = await Promise.allSettled([
          getUserList({ page: 1, size: 1 }),
          getActivityList({ page: 1, size: 5 }),
          getPostList({ page: 1, size: 1 }),
          getHotPosts({ page: 1, size: 5 }),
          getHotTopics(5),
          getNavigationStatistics(),
          getFacilityHeat({ limit: 5 }),
          getMerchantStatistics(),
          getSecondhandStatistics(),
        ])

        if (cancelled) return

        const userData = userRes.status === 'fulfilled' ? userRes.value?.data : null
        const activityData = activityRes.status === 'fulfilled' ? activityRes.value?.data : null
        const postData = postRes.status === 'fulfilled' ? postRes.value?.data : null
        const hotPostData = hotPostRes.status === 'fulfilled' ? hotPostRes.value?.data : null
        const hotTopicData = hotTopicRes.status === 'fulfilled' ? hotTopicRes.value?.data : null
        const navigationData = navigationRes.status === 'fulfilled' ? navigationRes.value?.data : null
        const facilityHeatData = facilityHeatRes.status === 'fulfilled' ? facilityHeatRes.value?.data : null
        const merchantStatData = merchantStatRes.status === 'fulfilled' ? merchantStatRes.value?.data : null
        const secondhandStatData = secondhandStatRes.status === 'fulfilled' ? secondhandStatRes.value?.data : null

        setDashboard({
          totalUsers: userData?.total || 0,
          totalActivities: activityData?.total || 0,
          totalPosts: postData?.total || 0,
          totalNavigations: navigationData?.totalNavigations || 0,
          todayNavigations: navigationData?.todayNavigations || 0,
          averageNavigationDuration: navigationData?.averageDuration || 0,
          activeMerchants: merchantStatData?.totalMerchants || 0,
          activeDiscountActivities: merchantStatData?.activeActivities || 0,
          onSaleItems: secondhandStatData?.onSaleItems || 0,
          soldItems: secondhandStatData?.soldItems || 0,
          hotTopics: getList(hotTopicData).slice(0, 5),
          hotPosts: getList(hotPostData?.records || hotPostData).slice(0, 5),
          hotFacilities: getList(facilityHeatData).slice(0, 5),
          popularDestinations: getList(navigationData?.popularDestinations).slice(0, 5),
          recentActivities: getList(activityData?.records).slice(0, 5),
          secondhandCategoryDistribution: getList(secondhandStatData?.categoryDistribution).slice(0, 6),
          activityTrend: getList(merchantStatData?.activityTrend).slice(-7),
        })
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadDashboard()
    return () => {
      cancelled = true
    }
  }, [])

  const metrics = useMemo(() => ([
    { label: '用户总数', value: formatNumber(dashboard.totalUsers), hint: '后台账号规模' },
    { label: '活动总数', value: formatNumber(dashboard.totalActivities), hint: '活动中心累计记录' },
    { label: '论坛帖子', value: formatNumber(dashboard.totalPosts), hint: '论坛当前帖子总量' },
    { label: '累计导航', value: formatNumber(dashboard.totalNavigations), hint: '地图导航总调用次数' },
    { label: '今日导航', value: formatNumber(dashboard.todayNavigations), hint: '当天发起的导航请求' },
    { label: '平均导航时长', value: formatSecondsToMinuteText(dashboard.averageNavigationDuration), hint: '导航闭环耗时均值' },
    { label: '在营商家', value: formatNumber(dashboard.activeMerchants), hint: '特惠商家总量' },
    { label: '在售旧物', value: formatNumber(dashboard.onSaleItems), hint: '二手市场当前在售' },
  ]), [dashboard])

  const facilityHeatChartOption = useMemo(() => ({
    grid: { left: 12, right: 12, top: 12, bottom: 12, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'category',
      data: dashboard.hotFacilities.map((item) => item.markerName || item.facilityName || `设施 ${item.id}`),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#475569' },
    },
    series: [{
      type: 'bar',
      data: dashboard.hotFacilities.map((item) => item.visitCount || item.viewCount || item.navigationCount || 0),
      barWidth: 18,
      itemStyle: {
        borderRadius: [0, 8, 8, 0],
        color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
          { offset: 0, color: '#14b8a6' },
          { offset: 1, color: '#2563eb' },
        ]),
      },
    }],
  }), [dashboard.hotFacilities])

  const categoryPieChartOption = useMemo(() => ({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [{
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['50%', '44%'],
      label: { formatter: '{b}\n{c}' },
      data: dashboard.secondhandCategoryDistribution.map((item) => ({
        name: item.categoryName || item.label || '未命名分类',
        value: Number(item.count || item.value || 0),
      })),
      itemStyle: {
        borderColor: '#fff',
        borderWidth: 3,
      },
    }],
  }), [dashboard.secondhandCategoryDistribution])

  const trendChartOption = useMemo(() => ({
    grid: { left: 12, right: 12, top: 24, bottom: 20, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dashboard.activityTrend.map((item, index) => item.date || item.label || `统计点${index + 1}`),
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b' },
    },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      data: dashboard.activityTrend.map((item) => Number(item.count || item.value || 0)),
      lineStyle: { width: 4, color: '#2563eb' },
      itemStyle: { color: '#0f766e' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(37, 99, 235, 0.35)' },
          { offset: 1, color: 'rgba(20, 184, 166, 0.08)' },
        ]),
      },
    }],
  }), [dashboard.activityTrend])

  const renderRankList = (items, getTitle, getMeta) => {
    if (!items.length) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
    }

    return (
      <div className="home-rank-list">
        {items.map((item, index) => (
          <div key={`${getTitle(item)}-${index}`} className="home-rank-item">
            <div className="home-rank-index">{String(index + 1).padStart(2, '0')}</div>
            <div className="home-rank-content">
              <strong>{getTitle(item)}</strong>
              <span>{getMeta(item)}</span>
            </div>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="home-container">
      <section className="home-metric-grid">
        {metrics.map((item) => (
          <Card key={item.label} className="home-metric-card" styles={{ body: { padding: 22 } }}>
            {loading ? (
              <Skeleton active paragraph={{ rows: 1 }} title={false} />
            ) : (
              <>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <em>{item.hint}</em>
              </>
            )}
          </Card>
        ))}
      </section>

      <section className="home-dashboard-grid">
        <Card className="home-board-card" title="热门设施">
          {loading ? <Skeleton active paragraph={{ rows: 6 }} /> : (
            dashboard.hotFacilities.length ? <EChart option={facilityHeatChartOption} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
          )}
        </Card>

        <Card className="home-board-card" title="热门目的地">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
            dashboard.popularDestinations,
            (item) => item.markerName || `目的地 ${item.markerId || item.id}`,
            (item) => `${item.facilityTypeName || '设施'} · 导航 ${formatNumber(item.navigationCount || item.visitCount || 0)} 次`
          )}
        </Card>

        <Card className="home-board-card" title="热门话题">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
            dashboard.hotTopics,
            (item) => item.topicName || item.name || `话题 ${item.id}`,
            (item) => `${item.description || '论坛活跃话题'}`.slice(0, 36)
          )}
        </Card>

        <Card className="home-board-card" title="热门帖子">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
            dashboard.hotPosts,
            (item) => item.title || `帖子 ${item.id}`,
            (item) => `作者 ${item.authorName || item.username || '-'}`
          )}
        </Card>

        <Card className="home-board-card" title="最近活动">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
            dashboard.recentActivities,
            (item) => item.title || `活动 ${item.id}`,
            (item) => `${item.location || '未填写地点'} · ${item.startTime || '未设置开始时间'}`
          )}
        </Card>

        <Card className="home-board-card" title="旧物分类分布">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
            dashboard.secondhandCategoryDistribution.length ? (
              <EChart option={categoryPieChartOption} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无分布数据" />
            )
          )}
        </Card>

        <Card className="home-board-card home-board-card--wide" title="近7个统计点优惠活跃趋势">
          {loading ? <Skeleton active paragraph={{ rows: 5 }} /> : (
            dashboard.activityTrend.length ? (
              <EChart option={trendChartOption} height={340} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无趋势数据" />
            )
          )}
        </Card>
      </section>
    </div>
  )
}

export default Home
