import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, Card, Empty, Skeleton, Tag } from 'antd'
import { ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import * as echarts from 'echarts'
import { getActivityList } from '../../api/activity'
import { getForumStatistics, getHotPosts, getHotTopics, getPostList } from '../../api/forum'
import { getFacilityHeat, getNavigationStatistics, getNearbyFacilityCount } from '../../api/map'
import { getMerchantStatistics } from '../../api/merchant'
import { getSecondhandReportStatistics, getSecondhandStatistics } from '../../api/secondhand'
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

const formatPercent = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '-'
  return `${numeric.toFixed(0)}%`
}

const getList = (value, fallback = []) => (Array.isArray(value) ? value : fallback)

const countItemName = (item) => item.categoryName || item.facilityTypeName || item.name || item.label || '未命名'
const countItemValue = (item) => Number(item.count ?? item.value ?? 0)

const formatTrendLabel = (item, index) => {
  const raw = item?.date || item?.label || item?.name || ''
  if (!raw) return `D${index + 1}`
  return String(raw).length > 10 ? String(raw).slice(5) : raw
}

const CHART_COLORS = ['#4a7fad', '#5b8f72', '#c07a45', '#8b7bb8', '#c45c5c', '#6b8cae']

function EChart({ option, height = 280 }) {
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

function PanelEmpty({ description, actionLabel, onAction }) {
  return (
    <div className="home-panel-empty">
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />
      {actionLabel && onAction ? (
        <Button type="link" onClick={onAction}>{actionLabel}</Button>
      ) : null}
    </div>
  )
}

function InsightStat({ label, value, hint }) {
  return (
    <div className="home-insight-stat">
      <span>{label}</span>
      <strong>{value}</strong>
      {hint ? <em>{hint}</em> : null}
    </div>
  )
}

function Home() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [updatedAt, setUpdatedAt] = useState('')
  const [dashboard, setDashboard] = useState({
    totalUsers: 0,
    totalActivities: 0,
    totalPosts: 0,
    totalNavigations: 0,
    todayNavigations: 0,
    completedNavigations: 0,
    cancelledNavigations: 0,
    averageNavigationDuration: 0,
    activeMerchants: 0,
    activeDiscountActivities: 0,
    totalDiscountActivities: 0,
    merchantAvgScore: 0,
    merchantReviews: 0,
    onSaleItems: 0,
    soldItems: 0,
    offlineItems: 0,
    totalSecondhandItems: 0,
    hotTopics: [],
    hotPosts: [],
    hotFacilities: [],
    popularDestinations: [],
    recentActivities: [],
    secondhandCategoryDistribution: [],
    secondhandPublishTrend: [],
    activityTrend: [],
    topMerchants: [],
    facilityTypeDistribution: [],
    forum: {
      totalComments: 0,
      activeTopics: 0,
      hotTopicsCount: 0,
      pendingReports: 0,
      hiddenPosts: 0,
      publishedPosts: 0,
    },
    moderation: {
      pendingForumReports: 0,
      pendingSecondhandReports: 0,
    },
  })

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    try {
      const end = new Date()
      const start = new Date()
      start.setDate(end.getDate() - 6)
      const fmt = (date) => date.toISOString().slice(0, 10)
      const range = { startDate: fmt(start), endDate: fmt(end) }

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
        forumStatRes,
        secondhandReportRes,
        facilityCountRes,
      ] = await Promise.allSettled([
        getUserList({ page: 1, size: 1 }),
        getActivityList({ page: 1, size: 5 }),
        getPostList({ page: 1, size: 1 }),
        getHotPosts({ page: 1, size: 5 }),
        getHotTopics(5),
        getNavigationStatistics(range),
        getFacilityHeat({ limit: 5 }),
        getMerchantStatistics(range),
        getSecondhandStatistics(range),
        getForumStatistics(),
        getSecondhandReportStatistics(),
        getNearbyFacilityCount(),
      ])

      const userData = userRes.status === 'fulfilled' ? userRes.value?.data : null
      const activityData = activityRes.status === 'fulfilled' ? activityRes.value?.data : null
      const postData = postRes.status === 'fulfilled' ? postRes.value?.data : null
      const hotPostData = hotPostRes.status === 'fulfilled' ? hotPostRes.value?.data : null
      const hotTopicData = hotTopicRes.status === 'fulfilled' ? hotTopicRes.value?.data : null
      const navigationData = navigationRes.status === 'fulfilled' ? navigationRes.value?.data : null
      const facilityHeatData = facilityHeatRes.status === 'fulfilled' ? facilityHeatRes.value?.data : null
      const merchantStatData = merchantStatRes.status === 'fulfilled' ? merchantStatRes.value?.data : null
      const secondhandStatData = secondhandStatRes.status === 'fulfilled' ? secondhandStatRes.value?.data : null
      const forumStatData = forumStatRes.status === 'fulfilled' ? forumStatRes.value?.data : null
      const secondhandReportData = secondhandReportRes.status === 'fulfilled' ? secondhandReportRes.value?.data : null
      const facilityCountData = facilityCountRes.status === 'fulfilled' ? facilityCountRes.value?.data : null

      const hotFacilities = getList(facilityHeatData).slice(0, 5)
      const popularDestinations = getList(navigationData?.popularDestinations).slice(0, 5)

      setDashboard({
        totalUsers: userData?.total || 0,
        totalActivities: activityData?.total || 0,
        totalPosts: postData?.total || 0,
        totalNavigations: navigationData?.totalNavigations || 0,
        todayNavigations: navigationData?.todayNavigations || 0,
        completedNavigations: navigationData?.completedNavigations || 0,
        cancelledNavigations: navigationData?.cancelledNavigations || 0,
        averageNavigationDuration: navigationData?.averageDuration || 0,
        activeMerchants: merchantStatData?.totalMerchants || 0,
        activeDiscountActivities: merchantStatData?.activeActivities || 0,
        totalDiscountActivities: merchantStatData?.totalActivities || 0,
        merchantAvgScore: merchantStatData?.avgScore || 0,
        merchantReviews: merchantStatData?.totalReviews || 0,
        onSaleItems: secondhandStatData?.onSaleItems || 0,
        soldItems: secondhandStatData?.soldItems || 0,
        offlineItems: secondhandStatData?.offlineItems || 0,
        totalSecondhandItems: secondhandStatData?.totalItems || 0,
        hotTopics: getList(hotTopicData).slice(0, 5),
        hotPosts: getList(hotPostData?.records || hotPostData).slice(0, 5),
        hotFacilities: hotFacilities.length ? hotFacilities : popularDestinations,
        popularDestinations,
        recentActivities: getList(activityData?.records).slice(0, 5),
        secondhandCategoryDistribution: getList(secondhandStatData?.categoryDistribution).slice(0, 6),
        secondhandPublishTrend: getList(secondhandStatData?.dailyPublishTrend).slice(-7),
        activityTrend: getList(merchantStatData?.activityTrend).slice(-7),
        topMerchants: getList(merchantStatData?.topMerchants).slice(0, 5),
        facilityTypeDistribution: getList(facilityCountData?.statistics).slice(0, 6),
        forum: {
          totalComments: forumStatData?.totalComments || 0,
          activeTopics: forumStatData?.activeTopics || 0,
          hotTopicsCount: forumStatData?.hotTopics || 0,
          pendingReports: forumStatData?.pendingReports || 0,
          hiddenPosts: forumStatData?.hiddenPosts || 0,
          publishedPosts: forumStatData?.publishedPosts || 0,
        },
        moderation: {
          pendingForumReports: forumStatData?.pendingReports || 0,
          pendingSecondhandReports: secondhandReportData?.pending || 0,
        },
      })
      setUpdatedAt(new Date().toLocaleString('zh-CN', { hour12: false }))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  const pendingModerationTotal = dashboard.moderation.pendingForumReports + dashboard.moderation.pendingSecondhandReports

  const navigationCompletionRate = useMemo(() => {
    const total = Number(dashboard.totalNavigations) || 0
    const completed = Number(dashboard.completedNavigations) || 0
    if (!total) return null
    return (completed / total) * 100
  }, [dashboard.totalNavigations, dashboard.completedNavigations])

  const hasNavigationData = dashboard.totalNavigations > 0
    || dashboard.hotFacilities.length > 0
    || dashboard.popularDestinations.length > 0

  const primaryMetrics = useMemo(() => ([
    { label: '用户总数', value: formatNumber(dashboard.totalUsers), hint: '注册账号规模' },
    { label: '论坛帖子', value: formatNumber(dashboard.totalPosts), hint: `${formatNumber(dashboard.forum.totalComments)} 条评论互动` },
    { label: '在售旧物', value: formatNumber(dashboard.onSaleItems), hint: `累计 ${formatNumber(dashboard.totalSecondhandItems)} 件发布` },
    { label: '优惠活动', value: formatNumber(dashboard.activeDiscountActivities), hint: `${formatNumber(dashboard.activeMerchants)} 家在营商家` },
  ]), [dashboard])

  const secondaryMetrics = useMemo(() => ([
    { label: '活动总数', value: formatNumber(dashboard.totalActivities) },
    { label: '活跃话题', value: formatNumber(dashboard.forum.activeTopics) },
    { label: '累计导航', value: formatNumber(dashboard.totalNavigations) },
    { label: '导航完成率', value: navigationCompletionRate == null ? '-' : formatPercent(navigationCompletionRate) },
    { label: '已售旧物', value: formatNumber(dashboard.soldItems) },
    { label: '待处理举报', value: formatNumber(pendingModerationTotal) },
  ]), [dashboard, navigationCompletionRate, pendingModerationTotal])

  const facilityHeatChartOption = useMemo(() => ({
    grid: { left: 8, right: 16, top: 8, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e8edf2' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: dashboard.hotFacilities.map((item) => item.markerName || item.facilityName || `设施 ${item.id}`),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#475569', fontSize: 12 },
    },
    series: [{
      type: 'bar',
      data: dashboard.hotFacilities.map((item) => item.visitCount || item.viewCount || item.navigationCount || 0),
      barWidth: 14,
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: '#4a7fad',
      },
    }],
  }), [dashboard.hotFacilities])

  const categoryPieChartOption = useMemo(() => ({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center', textStyle: { fontSize: 11 } },
    color: CHART_COLORS,
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '42%'],
      label: { formatter: '{b}\n{c}', fontSize: 11 },
      data: dashboard.secondhandCategoryDistribution.map((item) => ({
        name: countItemName(item),
        value: countItemValue(item),
      })),
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
    }],
  }), [dashboard.secondhandCategoryDistribution])

  const secondhandStatusOption = useMemo(() => ({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center', textStyle: { fontSize: 11 } },
    color: ['#4a7fad', '#5b8f72', '#94a3b8'],
    series: [{
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['50%', '44%'],
      label: { show: false },
      data: [
        { name: '在售', value: dashboard.onSaleItems },
        { name: '已售', value: dashboard.soldItems },
        { name: '已下架', value: dashboard.offlineItems },
      ].filter((item) => item.value > 0),
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
    }],
  }), [dashboard.onSaleItems, dashboard.soldItems, dashboard.offlineItems])

  const navigationStatusOption = useMemo(() => {
    const inProgress = Math.max(
      0,
      Number(dashboard.totalNavigations) - Number(dashboard.completedNavigations) - Number(dashboard.cancelledNavigations),
    )
    const data = [
      { name: '已完成', value: dashboard.completedNavigations },
      { name: '已取消', value: dashboard.cancelledNavigations },
      { name: '进行中/其他', value: inProgress },
    ].filter((item) => item.value > 0)

    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center', textStyle: { fontSize: 11 } },
      color: ['#5b8f72', '#c45c5c', '#94a3b8'],
      series: [{
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['50%', '44%'],
        label: { show: false },
        data,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
      }],
    }
  }, [dashboard.totalNavigations, dashboard.completedNavigations, dashboard.cancelledNavigations])

  const discountTrendOption = useMemo(() => ({
    grid: { left: 8, right: 12, top: 20, bottom: 12, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dashboard.activityTrend.map(formatTrendLabel),
      axisLine: { lineStyle: { color: '#dbe3ea' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef2f6' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: dashboard.activityTrend.map(countItemValue),
      lineStyle: { width: 3, color: '#4a7fad' },
      itemStyle: { color: '#2f6088' },
      areaStyle: { color: 'rgba(74, 127, 173, 0.12)' },
    }],
  }), [dashboard.activityTrend])

  const secondhandTrendOption = useMemo(() => ({
    grid: { left: 8, right: 12, top: 20, bottom: 12, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: dashboard.secondhandPublishTrend.map(formatTrendLabel),
      axisLine: { lineStyle: { color: '#dbe3ea' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef2f6' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: dashboard.secondhandPublishTrend.map(countItemValue),
      barWidth: 18,
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: '#5b8f72',
      },
    }],
  }), [dashboard.secondhandPublishTrend])

  const topMerchantOption = useMemo(() => ({
    grid: { left: 8, right: 16, top: 8, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#e8edf2' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: dashboard.topMerchants.map(countItemName),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#475569', fontSize: 12 },
    },
    series: [{
      type: 'bar',
      data: dashboard.topMerchants.map(countItemValue),
      barWidth: 14,
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: '#c07a45',
      },
    }],
  }), [dashboard.topMerchants])

  const facilityTypeOption = useMemo(() => ({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center', textStyle: { fontSize: 11 } },
    color: CHART_COLORS,
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '42%'],
      label: { formatter: '{b}\n{c}', fontSize: 11 },
      data: dashboard.facilityTypeDistribution.map((item) => ({
        name: countItemName(item),
        value: countItemValue(item),
      })),
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
    }],
  }), [dashboard.facilityTypeDistribution])

  const quickLinks = [
    { label: '论坛帖子', desc: '内容审核与置顶', route: '/forum/post' },
    { label: '活动管理', desc: '发布与报名查看', route: '/activity/manage' },
    { label: '导航统计', desc: '路线与目的地分析', route: '/facility/nav-analytics' },
    { label: '二手物品', desc: '上架与分类运营', route: '/market/item' },
    { label: '特惠商家', desc: '优惠与评价管理', route: '/discount/merchant' },
    { label: '食堂管理', desc: '档口与菜品维护', route: '/facility/canteen' },
  ]

  const renderRankList = (items, getTitle, getMeta) => {
    if (!items.length) {
      return <PanelEmpty description="暂无数据" />
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
      <div className="home-toolbar">
        <p>基于论坛、活动、地图导航、特惠与二手等模块的真实运营数据，帮助管理员快速发现趋势、热度与待办事项。</p>
        <div className="home-toolbar__actions">
          {updatedAt ? <span>更新于 {updatedAt}</span> : null}
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadDashboard}>刷新</Button>
        </div>
      </div>

      {!loading && pendingModerationTotal > 0 ? (
        <section className="home-alert-strip">
          {dashboard.moderation.pendingForumReports > 0 ? (
            <button type="button" className="home-alert home-alert--warn" onClick={() => navigate('/forum/report')}>
              <strong>{formatNumber(dashboard.moderation.pendingForumReports)}</strong>
              <span>条论坛举报待处理</span>
              <RightOutlined />
            </button>
          ) : null}
          {dashboard.moderation.pendingSecondhandReports > 0 ? (
            <button type="button" className="home-alert home-alert--warn" onClick={() => navigate('/market/report')}>
              <strong>{formatNumber(dashboard.moderation.pendingSecondhandReports)}</strong>
              <span>条二手举报待处理</span>
              <RightOutlined />
            </button>
          ) : null}
        </section>
      ) : null}

      <section className="home-metric-grid home-metric-grid--primary">
        {primaryMetrics.map((item) => (
          <Card key={item.label} className="home-metric-card home-metric-card--primary" styles={{ body: { padding: '18px 20px' } }}>
            {loading ? <Skeleton active paragraph={{ rows: 1 }} title={false} /> : (
              <>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <em>{item.hint}</em>
              </>
            )}
          </Card>
        ))}
      </section>

      <section className="home-metric-grid home-metric-grid--secondary">
        {secondaryMetrics.map((item) => (
          <div key={item.label} className="home-metric-chip">
            {loading ? <Skeleton.Input active size="small" style={{ width: 120 }} /> : (
              <>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </>
            )}
          </div>
        ))}
      </section>

      <section className="home-insight-grid">
        <Card className="home-board-card" title="社区健康度">
          {loading ? <Skeleton active paragraph={{ rows: 3 }} /> : (
            <div className="home-insight-stats">
              <InsightStat label="已发布帖子" value={formatNumber(dashboard.forum.publishedPosts)} />
              <InsightStat label="评论总量" value={formatNumber(dashboard.forum.totalComments)} />
              <InsightStat label="活跃话题" value={formatNumber(dashboard.forum.activeTopics)} hint={`${formatNumber(dashboard.forum.hotTopicsCount)} 个热门话题`} />
              <InsightStat label="隐藏帖子" value={formatNumber(dashboard.forum.hiddenPosts)} hint="需关注的内容风险" />
            </div>
          )}
        </Card>

        <Card className="home-board-card" title="导航完成结构">
          {loading ? <Skeleton active paragraph={{ rows: 3 }} /> : (
            dashboard.totalNavigations > 0 ? (
              <div className="home-insight-chart-wrap">
                <EChart option={navigationStatusOption} height={180} />
                <p className="home-insight-caption">
                  平均时长 {formatSecondsToMinuteText(dashboard.averageNavigationDuration)} · 今日 {formatNumber(dashboard.todayNavigations)} 次
                </p>
              </div>
            ) : (
              <PanelEmpty description="暂无导航记录" actionLabel="查看导航统计" onAction={() => navigate('/facility/nav-analytics')} />
            )
          )}
        </Card>

        <Card className="home-board-card" title="旧物流转结构">
          {loading ? <Skeleton active paragraph={{ rows: 3 }} /> : (
            dashboard.totalSecondhandItems > 0 ? (
              <div className="home-insight-chart-wrap">
                <EChart option={secondhandStatusOption} height={180} />
                <p className="home-insight-caption">
                  在售 {formatNumber(dashboard.onSaleItems)} · 已售 {formatNumber(dashboard.soldItems)} · 下架 {formatNumber(dashboard.offlineItems)}
                </p>
              </div>
            ) : (
              <PanelEmpty description="暂无旧物数据" actionLabel="查看二手管理" onAction={() => navigate('/market/item')} />
            )
          )}
        </Card>
      </section>

      <div className="home-layout">
        <section className="home-layout__main">
          <div className="home-section-head">
            <h3>社区与活动</h3>
            <span>热度榜单与近期动态</span>
          </div>

          <div className="home-dashboard-grid home-dashboard-grid--community">
            <Card className="home-board-card" title="热门话题">
              {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
                dashboard.hotTopics,
                (item) => item.topicName || item.name || `话题 ${item.id}`,
                (item) => `${item.description || '论坛活跃话题'}`.slice(0, 40),
              )}
            </Card>

            <Card className="home-board-card" title="热门帖子">
              {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
                dashboard.hotPosts,
                (item) => item.title || `帖子 ${item.id}`,
                (item) => `作者 ${item.authorName || item.username || '-'} · 赞 ${formatNumber(item.likeCount || 0)}`,
              )}
            </Card>

            <Card className="home-board-card home-board-card--wide" title="最近活动">
              {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : renderRankList(
                dashboard.recentActivities,
                (item) => item.title || `活动 ${item.id}`,
                (item) => `${item.location || '未填写地点'} · ${item.startTime || '未设置开始时间'}`,
              )}
            </Card>
          </div>

          <div className="home-trend-grid">
            <Card className="home-board-card" title="近 7 日优惠活动趋势">
              {loading ? <Skeleton active paragraph={{ rows: 5 }} /> : (
                dashboard.activityTrend.length ? (
                  <EChart option={discountTrendOption} height={220} />
                ) : (
                  <PanelEmpty description="暂无优惠趋势数据" actionLabel="前往特惠管理" onAction={() => navigate('/discount/merchant')} />
                )
              )}
            </Card>

            <Card className="home-board-card" title="近 7 日旧物上架趋势">
              {loading ? <Skeleton active paragraph={{ rows: 5 }} /> : (
                dashboard.secondhandPublishTrend.length ? (
                  <EChart option={secondhandTrendOption} height={220} />
                ) : (
                  <PanelEmpty description="暂无旧物上架趋势" actionLabel="查看二手管理" onAction={() => navigate('/market/item')} />
                )
              )}
            </Card>
          </div>
        </section>

        <aside className="home-layout__side">
          <div className="home-section-head">
            <h3>地图与交易</h3>
            <span>设施热度、商家与分类分布</span>
          </div>

          <Card className="home-board-card" title="热门设施 / 目的地">
            {loading ? <Skeleton active paragraph={{ rows: 5 }} /> : (
              dashboard.hotFacilities.length ? (
                <EChart option={facilityHeatChartOption} height={220} />
              ) : (
                <PanelEmpty
                  description={hasNavigationData ? '暂无设施热度数据' : '用户发起导航后，这里会展示热门设施与目的地'}
                  actionLabel="查看导航统计"
                  onAction={() => navigate('/facility/nav-analytics')}
                />
              )
            )}
          </Card>

          <Card className="home-board-card" title="热门目的地">
            {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
              dashboard.popularDestinations.length ? renderRankList(
                dashboard.popularDestinations,
                (item) => item.markerName || `目的地 ${item.markerId || item.id}`,
                (item) => `${item.facilityTypeName || '设施'} · 导航 ${formatNumber(item.navigationCount || item.visitCount || 0)} 次`,
              ) : (
                <PanelEmpty description="暂无热门目的地" actionLabel="管理地图标点" onAction={() => navigate('/facility/marker')} />
              )
            )}
          </Card>

          <Card className="home-board-card" title="热门商家（按活动数）">
            {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
              dashboard.topMerchants.length ? (
                <>
                  <div className="home-card-meta">
                    <Tag color="blue">累计活动 {formatNumber(dashboard.totalDiscountActivities)}</Tag>
                    {dashboard.merchantReviews > 0 ? (
                      <Tag color="green">商家评分 {dashboard.merchantAvgScore.toFixed(1)}</Tag>
                    ) : null}
                  </div>
                  <EChart option={topMerchantOption} height={200} />
                </>
              ) : (
                <PanelEmpty description="暂无商家排行" actionLabel="前往特惠管理" onAction={() => navigate('/discount/merchant')} />
              )
            )}
          </Card>

          <Card className="home-board-card" title="旧物分类分布">
            {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
              dashboard.secondhandCategoryDistribution.length ? (
                <EChart option={categoryPieChartOption} height={220} />
              ) : (
                <PanelEmpty description="暂无旧物分类数据" actionLabel="查看二手管理" onAction={() => navigate('/market/item')} />
              )
            )}
          </Card>

          <Card className="home-board-card" title="校园设施类型分布">
            {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
              dashboard.facilityTypeDistribution.length ? (
                <EChart option={facilityTypeOption} height={220} />
              ) : (
                <PanelEmpty description="暂无设施分布数据" actionLabel="管理公共设施" onAction={() => navigate('/facility/public')} />
              )
            )}
          </Card>
        </aside>
      </div>

      <section className="home-quick-links">
        <div className="home-section-head">
          <h3>快捷入口</h3>
          <span>常用后台模块</span>
        </div>
        <div className="home-quick-links__grid">
          {quickLinks.map((item) => (
            <button key={item.route} type="button" className="home-quick-link" onClick={() => navigate(item.route)}>
              <strong>{item.label}</strong>
              <span>{item.desc}</span>
              <RightOutlined />
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}

export default Home
