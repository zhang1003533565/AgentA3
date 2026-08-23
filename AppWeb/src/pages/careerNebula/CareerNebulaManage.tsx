import { useEffect, useMemo, useRef, useState } from 'react';
import { getCareerNebulaMap, saveCareerNebulaMap } from '../../api/careerNebula';
import styles from './CareerNebulaManage.module.css';

type NodeStatus = 'enabled' | 'disabled';
type CareerNode = {
  id: string;
  name: string;
  description: string;
  x: number;
  y: number;
  size: number;
  status: NodeStatus;
  image?: string;
};
type LearningItem = { id: string; title: string; type: '知识' | '实践' | '考核' };
type SkillNode = CareerNode & { careerId?: string; items: LearningItem[] };
type Edge = { id: string; source: string; target: string; type: '主线' | '支线' };
type PersistedMap = { careers: CareerNode[]; skills: SkillNode[]; edges: Edge[] };

function normalizePersistedMap(value: unknown): PersistedMap | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const candidate = value as Partial<PersistedMap>;
  if (!Array.isArray(candidate.careers)
    || !Array.isArray(candidate.skills)
    || !Array.isArray(candidate.edges)) return undefined;

  return {
    careers: candidate.careers,
    skills: candidate.skills.map((node) => ({
      ...node,
      items: Array.isArray(node.items) ? node.items : [],
    })),
    edges: candidate.edges,
  };
}

function softenNebulaBackground(context: CanvasRenderingContext2D, size: number) {
  const pixels = context.getImageData(0, 0, size, size);
  const data = pixels.data;
  const center = size / 2;

  for (let index = 0; index < data.length; index += 4) {
    const red = data[index];
    const green = data[index + 1];
    const blue = data[index + 2];
    const brightness = Math.max(red, green, blue);
    const pixel = index / 4;
    const x = pixel % size;
    const y = Math.floor(pixel / size);
    const edgeDistance = Math.hypot((x - center) / (size * 0.5), (y - center) / (size * 0.39));
    const darkAlpha = Math.max(0, Math.min(1, (brightness - 22) / 62));
    const edgeAlpha = Math.max(0, Math.min(1, (1 - edgeDistance) / 0.18));
    data[index + 3] = Math.round(data[index + 3] * darkAlpha * edgeAlpha);
  }

  context.putImageData(pixels, 0, 0);
}

function drawCrop({
  context,
  image,
  size,
  viewportSize,
  zoom,
  offset,
  kind,
}: {
  context: CanvasRenderingContext2D;
  image: HTMLImageElement;
  size: number;
  viewportSize: number;
  zoom: number;
  offset: { x: number; y: number };
  kind: 'nebula' | 'planet';
}) {
  const scale = size / viewportSize;
  const baseScale = Math.max(viewportSize / image.naturalWidth, viewportSize / image.naturalHeight);
  const width = image.naturalWidth * baseScale * zoom;
  const height = image.naturalHeight * baseScale * zoom;
  context.clearRect(0, 0, size, size);
  context.drawImage(
    image,
    ((viewportSize - width) / 2 + offset.x) * scale,
    ((viewportSize - height) / 2 + offset.y) * scale,
    width * scale,
    height * scale
  );
  if (kind === 'nebula') softenNebulaBackground(context, size);
}

const STORAGE_KEY = 'smart-campus:career-nebula-admin:v1';
const skillCareerId = (node: SkillNode) => node.careerId ?? 'testing';
const starterCareers: CareerNode[] = [
  {
    id: 'testing',
    name: '软件测试工程师',
    description: '从测试基础到自动化与性能测试的完整职业路线。',
    x: 23,
    y: 32,
    size: 118,
    status: 'enabled',
  },
  {
    id: 'java',
    name: 'Java 开发工程师',
    description: '企业级后端开发与服务架构。',
    x: 62,
    y: 27,
    size: 104,
    status: 'enabled',
  },
  {
    id: 'frontend',
    name: '前端开发工程师',
    description: '现代 Web 界面、交互与工程化。',
    x: 38,
    y: 70,
    size: 98,
    status: 'enabled',
  },
  {
    id: 'ai',
    name: 'AI 应用工程师',
    description: '大模型应用、智能体与工程部署。',
    x: 76,
    y: 64,
    size: 92,
    status: 'disabled',
  },
];
const starterSkills: SkillNode[] = [
  {
    id: 'foundation',
    name: '测试基础',
    description: '测试流程、测试类型、用例设计与缺陷生命周期。',
    x: 12,
    y: 47,
    size: 76,
    status: 'enabled',
    items: [
      { id: 'f1', title: '软件测试基本概念', type: '知识' },
      { id: 'f2', title: '测试用例设计实战', type: '实践' },
    ],
  },
  {
    id: 'linux',
    name: 'Linux 与网络',
    description: '常用命令、HTTP 与网络故障排查。',
    x: 30,
    y: 25,
    size: 70,
    status: 'enabled',
    items: [{ id: 'l1', title: 'Linux 常用命令', type: '知识' }],
  },
  {
    id: 'database',
    name: '数据库测试',
    description: 'SQL、数据校验、事务与一致性测试。',
    x: 32,
    y: 72,
    size: 70,
    status: 'enabled',
    items: [{ id: 'd1', title: 'SQL 查询与数据校验', type: '实践' }],
  },
  {
    id: 'web',
    name: 'Web 功能测试',
    description: '需求分析、页面功能、兼容性与探索式测试。',
    x: 50,
    y: 23,
    size: 72,
    status: 'enabled',
    items: [{ id: 'w1', title: 'Web 测试检查清单', type: '知识' }],
  },
  {
    id: 'api',
    name: '接口测试',
    description: 'HTTP、Postman、断言、参数化和接口自动化。',
    x: 53,
    y: 53,
    size: 82,
    status: 'enabled',
    items: [
      { id: 'a1', title: 'HTTP 请求与响应', type: '知识' },
      { id: 'a2', title: 'Postman 环境与变量', type: '实践' },
      { id: 'a3', title: '登录与订单接口挑战', type: '考核' },
    ],
  },
  {
    id: 'automation',
    name: '自动化测试',
    description: 'Python、Pytest、Selenium 与测试框架。',
    x: 72,
    y: 30,
    size: 76,
    status: 'disabled',
    items: [{ id: 'au1', title: 'Pytest 自动化框架', type: '实践' }],
  },
  {
    id: 'performance',
    name: '性能测试',
    description: '负载模型、JMeter、监控与瓶颈分析。',
    x: 74,
    y: 70,
    size: 76,
    status: 'disabled',
    items: [{ id: 'p1', title: 'JMeter 场景设计', type: '实践' }],
  },
  {
    id: 'boss',
    name: '综合质量项目',
    description: '完成商城系统全链路测试并提交质量报告。',
    x: 91,
    y: 50,
    size: 88,
    status: 'disabled',
    items: [{ id: 'b1', title: '全链路质量守卫战', type: '考核' }],
  },
];
const starterEdges: Edge[] = [
  ['foundation', 'linux'],
  ['foundation', 'database'],
  ['linux', 'web'],
  ['database', 'api'],
  ['web', 'api'],
  ['web', 'automation'],
  ['api', 'automation'],
  ['api', 'performance'],
  ['automation', 'boss'],
  ['performance', 'boss'],
].map(([source, target], index) => ({
  id: `edge-${index}`,
  source,
  target,
  type: index === 2 || index === 5 ? '支线' : '主线',
}));

function initialData(): PersistedMap {
  const fallback = { careers: starterCareers, skills: starterSkills, edges: starterEdges };
  if (typeof window === 'undefined')
    return fallback;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? normalizePersistedMap(JSON.parse(raw)) ?? fallback : fallback;
  } catch {
    return fallback;
  }
}

export default function CareerNebulaManage() {
  const [careers, setCareers] = useState(starterCareers);
  const [skills, setSkills] = useState(starterSkills);
  const [edges, setEdges] = useState(starterEdges);
  const [level, setLevel] = useState<'careers' | 'skills'>('careers');
  const [activeCareerId, setActiveCareerId] = useState('testing');
  const [selectedId, setSelectedId] = useState('testing');
  const [preview, setPreview] = useState(false);
  const [connectMode, setConnectMode] = useState(false);
  const [connectSource, setConnectSource] = useState<string>();
  const [editingItems, setEditingItems] = useState(false);
  const [notice, setNotice] = useState('已加载本地星图');
  const [dirty, setDirty] = useState(false);
  const [routeDirty, setRouteDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveSucceeded, setSaveSucceeded] = useState(false);
  const [confirmReturn, setConfirmReturn] = useState(false);
  const [cropSource, setCropSource] = useState<string>();
  const [imageUrl, setImageUrl] = useState('');
  const canvasRef = useRef<HTMLDivElement>(null);
  const saveSuccessTimer = useRef<number | undefined>(undefined);
  const savedData = useRef<PersistedMap>({
    careers: starterCareers,
    skills: starterSkills,
    edges: starterEdges,
  });
  const dragging = useRef<
    { id: string; pointer: number; startX: number; startY: number; moved: boolean } | undefined
  >(undefined);
  const ignoreClick = useRef<string | undefined>(undefined);
  const resizing = useRef<
    { id: string; pointer: number; startX: number; startY: number; startSize: number } | undefined
  >(undefined);

  useEffect(() => {
    let cancelled = false;
    const localData = initialData();

    async function loadCareerMap() {
      try {
        const data = normalizePersistedMap(await getCareerNebulaMap());
        if (!data) throw new Error('岗位星图数据结构不完整');
        if (cancelled) return;
        setCareers(data.careers);
        setSkills(data.skills);
        setEdges(data.edges);
        savedData.current = JSON.parse(JSON.stringify(data)) as PersistedMap;
        setNotice('已从智慧校园数据库加载');
      } catch {
        if (cancelled) return;
        setCareers(localData.careers);
        setSkills(localData.skills);
        setEdges(localData.edges);
        savedData.current = JSON.parse(JSON.stringify(localData)) as PersistedMap;
        setNotice('后端暂不可用，当前显示浏览器缓存');
      }
    }

    void loadCareerMap();
    return () => {
      cancelled = true;
    };
  }, []);
  useEffect(
    () => () => {
      if (saveSuccessTimer.current) window.clearTimeout(saveSuccessTimer.current);
    },
    []
  );
  const careerSkills = useMemo(
    () => skills.filter((node) => skillCareerId(node) === activeCareerId),
    [activeCareerId, skills]
  );
  const activeCareer = careers.find((career) => career.id === activeCareerId);
  const nodes = level === 'careers' ? careers : careerSkills;
  const selected = useMemo(() => nodes.find((node) => node.id === selectedId), [nodes, selectedId]);
  const selectedSkill =
    level === 'skills' ? skills.find((node) => node.id === selectedId) : undefined;

  function updateSelected(patch: Partial<CareerNode>) {
    if (level === 'careers')
      setCareers((all) =>
        all.map((node) => (node.id === selectedId ? { ...node, ...patch } : node))
      );
    else
      setSkills((all) =>
        all.map((node) => (node.id === selectedId ? { ...node, ...patch } : node))
      );
    setNotice('有未保存的修改');
    setDirty(true);
    if (level === 'skills') setRouteDirty(true);
  }
  function addNode() {
    const id = crypto.randomUUID();
    if (level === 'careers')
      setCareers((all) => [
        ...all,
        {
          id,
          name: '新岗位',
          description: '请填写岗位介绍。',
          x: 50,
          y: 50,
          size: 100,
          status: 'enabled',
        },
      ]);
    else
      setSkills((all) => [
        ...all,
        {
          id,
          careerId: activeCareerId,
          name: '新学习节点',
          description: '请填写学习目标。',
          x: 50,
          y: 50,
          size: 72,
          status: 'enabled',
          items: [],
        },
      ]);
    setSelectedId(id);
    setNotice('已新增节点，请编辑属性');
    setDirty(true);
    if (level === 'skills') setRouteDirty(true);
  }
  function deleteNode() {
    if (!selected) return;
    if (level === 'careers') {
      const relatedSkillIds = skills
        .filter((node) => skillCareerId(node) === selected.id)
        .map((node) => node.id);
      setCareers((all) => all.filter((node) => node.id !== selected.id));
      setSkills((all) => all.filter((node) => skillCareerId(node) !== selected.id));
      setEdges((all) =>
        all.filter(
          (edge) => !relatedSkillIds.includes(edge.source) && !relatedSkillIds.includes(edge.target)
        )
      );
    } else {
      setSkills((all) => all.filter((node) => node.id !== selected.id));
      setEdges((all) =>
        all.filter((edge) => edge.source !== selected.id && edge.target !== selected.id)
      );
    }
    setSelectedId('');
    setNotice('节点已删除，保存后生效');
    setDirty(true);
    if (level === 'skills') setRouteDirty(true);
  }
  async function save(): Promise<boolean> {
    const data = { careers, skills, edges };
    setSaving(true);
    try {
      await saveCareerNebulaMap(data);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
      savedData.current = JSON.parse(JSON.stringify(data)) as PersistedMap;
      setDirty(false);
      setRouteDirty(false);
      setSaveSucceeded(true);
      if (saveSuccessTimer.current) window.clearTimeout(saveSuccessTimer.current);
      saveSuccessTimer.current = window.setTimeout(() => setSaveSucceeded(false), 1800);
      setNotice(
        `已保存到智慧校园数据库 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
      );
      return true;
    } catch {
      setNotice('保存失败，请确认后端服务已启动后重试');
      return false;
    } finally {
      setSaving(false);
    }
  }
  function move(clientX: number, clientY: number, id: string) {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = Math.min(94, Math.max(6, ((clientX - rect.left) / rect.width) * 100));
    const y = Math.min(90, Math.max(12, ((clientY - rect.top) / rect.height) * 100));
    if (level === 'careers')
      setCareers((all) => all.map((node) => (node.id === id ? { ...node, x, y } : node)));
    else setSkills((all) => all.map((node) => (node.id === id ? { ...node, x, y } : node)));
    setNotice('节点位置已变化');
    setDirty(true);
    if (level === 'skills') setRouteDirty(true);
  }
  function chooseLocalImage(file?: File) {
    if (!file?.type.startsWith('image/')) return;
    const reader = new FileReader();
    reader.onload = () => setCropSource(String(reader.result));
    reader.readAsDataURL(file);
  }
  function chooseUrlImage() {
    const value = imageUrl.trim();
    if (!/^https?:\/\//i.test(value)) {
      setNotice('请输入以 http:// 或 https:// 开头的图片地址');
      return;
    }
    setCropSource(value);
  }
  function resizeNode(clientX: number, clientY: number) {
    const current = resizing.current;
    if (!current) return;
    const minimum = level === 'careers' ? 80 : 54;
    const maximum = level === 'careers' ? 320 : 240;
    const distance = Math.max(clientX - current.startX, clientY - current.startY);
    const size = Math.min(maximum, Math.max(minimum, current.startSize + distance));
    if (level === 'careers')
      setCareers((all) => all.map((node) => (node.id === current.id ? { ...node, size } : node)));
    else setSkills((all) => all.map((node) => (node.id === current.id ? { ...node, size } : node)));
    setNotice(`节点尺寸已调整为 ${Math.round(size)}px`);
    setDirty(true);
    if (level === 'skills') setRouteDirty(true);
  }
  function enterSkills() {
    if (!selected) return;
    const nextSkills = skills.filter((node) => skillCareerId(node) === selected.id);
    setActiveCareerId(selected.id);
    setLevel('skills');
    setSelectedId(nextSkills[0]?.id ?? '');
    setPreview(false);
    setConnectMode(false);
    setRouteDirty(false);
    setNotice(
      nextSkills.length ? `正在配置${selected.name}学习路线` : '该岗位暂无学习节点，可以新增'
    );
  }
  function selectSkill(id: string) {
    if (!connectMode) {
      setSelectedId((current) => (current === id ? '' : id));
      return;
    }
    if (!connectSource) {
      setConnectSource(id);
      setNotice('请选择目标节点');
      return;
    }
    if (connectSource === id) {
      setConnectSource(undefined);
      setNotice('');
      return;
    }
    const existing = edges.find(
      (edge) =>
        (edge.source === connectSource && edge.target === id) ||
        (edge.source === id && edge.target === connectSource)
    );
    if (existing) {
      setEdges((all) => all.filter((edge) => edge.id !== existing.id));
      setConnectSource(undefined);
      setNotice('两个节点已有连线，现已取消连线');
      setDirty(true);
      setRouteDirty(true);
      return;
    }
    setEdges((all) => [
      ...all,
      { id: crypto.randomUUID(), source: connectSource, target: id, type: '主线' },
    ]);
    setConnectSource(undefined);
    setNotice('连线已创建');
    setDirty(true);
    setRouteDirty(true);
  }
  function updateItems(items: LearningItem[]) {
    setSkills((all) => all.map((node) => (node.id === selectedId ? { ...node, items } : node)));
    setNotice('学习内容已更新');
    setDirty(true);
    setRouteDirty(true);
  }
  function returnToCareers() {
    if (routeDirty) {
      setConfirmReturn(true);
      return;
    }
    finishReturn();
  }
  function finishReturn() {
    setLevel('careers');
    setSelectedId(activeCareerId);
    setConnectMode(false);
    setConnectSource(undefined);
    setConfirmReturn(false);
  }
  function discardAndReturn() {
    const snapshot = savedData.current;
    setCareers(JSON.parse(JSON.stringify(snapshot.careers)) as CareerNode[]);
    setSkills(JSON.parse(JSON.stringify(snapshot.skills)) as SkillNode[]);
    setEdges(JSON.parse(JSON.stringify(snapshot.edges)) as Edge[]);
    setDirty(false);
    setRouteDirty(false);
    setNotice('已放弃未保存的修改');
    finishReturn();
  }
  async function saveAndReturn() {
    if (await save()) finishReturn();
  }

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div>
          <small>STARPATH ADMIN / VISUAL MAP EDITOR</small>
          <h1>
            {level === 'careers'
              ? '岗位星云配置'
              : `${activeCareer?.name ?? '岗位'} · 学习路线配置`}
          </h1>
        </div>
        <div className={styles.toolbar}>
          {level === 'skills' && <button onClick={returnToCareers}>← 返回岗位</button>}
          {level === 'skills' && (
            <button
              className={connectMode ? styles.activeTool : ''}
              onClick={() => {
                setConnectMode((value) => !value);
                setConnectSource(undefined);
              }}
            >
              {connectMode ? '退出连接' : '连接节点'}
            </button>
          )}
          <button onClick={() => setPreview((value) => !value)}>
            {preview ? '返回编辑' : '预览效果'}
          </button>
          <button className={styles.save} onClick={save}>
            保存星图
          </button>
        </div>
      </header>
      <div className={`${styles.editor} ${preview ? styles.preview : ''}`}>
        {!preview && (
          <aside className={styles.catalog}>
            <div className={styles.panelTitle}>
              节点目录 <small>{nodes.length} NODES</small>
            </div>
            <button className={styles.addNodeButton} onClick={addNode}>
              ＋ {level === 'careers' ? '新增岗位' : '新增学习节点'}
            </button>
            <div className={styles.nodeList}>
              {nodes.map((node, index) => (
                <button
                  key={node.id}
                  className={selectedId === node.id ? styles.selectedRow : ''}
                  onClick={() => {
                    if (level === 'skills') selectSkill(node.id);
                    else setSelectedId((current) => (current === node.id ? '' : node.id));
                  }}
                >
                  <span>{String(index + 1).padStart(2, '0')}</span>
                  <div>
                    <b>{node.name}</b>
                    <small>{node.status === 'enabled' ? '已启用' : '已禁用'}</small>
                  </div>
                  <i
                    className={`${level === 'careers' ? styles.nebulaThumb : styles.planetThumb} ${node.image ? styles.hasImage : ''}`}
                    style={{ backgroundImage: node.image ? `url(${node.image})` : undefined }}
                  />
                </button>
              ))}
            </div>
          </aside>
        )}
        <main className={styles.canvasWrap}>
          {!preview && (
            <div className={styles.canvasStatus}>
              <span>
                {connectMode
                  ? connectSource
                    ? '连接模式：请选择目标节点'
                    : '连接模式：请选择起始节点'
                  : '拖动节点调整位置'}
              </span>
              <b>{notice}</b>
            </div>
          )}
          <div ref={canvasRef} className={styles.canvas}>
            <div className={styles.canvasHeading}>
              <small>{level === 'careers' ? 'CAREER NEBULA' : 'CAREER LEARNING PATH'}</small>
              <h2>
                {level === 'careers' ? '岗位星云' : `${activeCareer?.name ?? '岗位'}学习星系`}
              </h2>
            </div>
            {level === 'skills' && (
              <svg className={styles.edges} viewBox="0 0 100 100" preserveAspectRatio="none">
                {edges.map((edge) => {
                  const source = careerSkills.find((node) => node.id === edge.source);
                  const target = careerSkills.find((node) => node.id === edge.target);
                  if (!source || !target) return null;
                  if (preview && (source.status !== 'enabled' || target.status !== 'enabled'))
                    return null;
                  return (
                    <g key={edge.id}>
                      <line
                        className={styles.edgeHit}
                        x1={source.x}
                        y1={source.y}
                        x2={target.x}
                        y2={target.y}
                      />
                      <line
                        className={edge.type === '主线' ? styles.mainEdge : styles.branchEdge}
                        x1={source.x}
                        y1={source.y}
                        x2={target.x}
                        y2={target.y}
                      />
                    </g>
                  );
                })}
              </svg>
            )}
            {nodes
              .filter((node) => !preview || node.status === 'enabled')
              .map((node) => (
                <div
                  key={node.id}
                  role="button"
                  tabIndex={0}
                  className={`${styles.mapNode} ${selectedId === node.id ? styles.selectedNode : ''} ${node.status === 'disabled' ? styles.disabledNode : ''} ${connectSource === node.id ? styles.connectSource : ''}`}
                  style={{
                    left: `${node.x}%`,
                    top: `${node.y}%`,
                    ['--size' as string]: `${node.size}px`,
                  }}
                  onClick={() => {
                    if (ignoreClick.current === node.id) {
                      ignoreClick.current = undefined;
                      return;
                    }
                    if (level === 'skills') selectSkill(node.id);
                    else setSelectedId((current) => (current === node.id ? '' : node.id));
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      if (level === 'skills') selectSkill(node.id);
                      else setSelectedId(node.id);
                    }
                  }}
                  onPointerDown={(event) => {
                    if (preview || connectMode) return;
                    dragging.current = {
                      id: node.id,
                      pointer: event.pointerId,
                      startX: event.clientX,
                      startY: event.clientY,
                      moved: false,
                    };
                    event.currentTarget.setPointerCapture(event.pointerId);
                  }}
                  onPointerMove={(event) => {
                    if (dragging.current?.pointer === event.pointerId) {
                      const moved =
                        Math.hypot(
                          event.clientX - dragging.current.startX,
                          event.clientY - dragging.current.startY
                        ) > 3;
                      if (moved) {
                        dragging.current.moved = true;
                        move(event.clientX, event.clientY, node.id);
                      }
                    }
                  }}
                  onPointerUp={() => {
                    if (dragging.current?.moved) ignoreClick.current = node.id;
                    dragging.current = undefined;
                  }}
                >
                  {!node.image && <span className={styles.orbit} />}
                  <span
                    className={`${styles.core} ${level === 'careers' ? styles.nebulaCore : styles.planetCore} ${node.image ? styles.hasImage : ''}`}
                    style={{ backgroundImage: node.image ? `url(${node.image})` : undefined }}
                  >
                    {level === 'skills' ? node.name.slice(0, 1) : ''}
                  </span>
                  {selectedId === node.id && !preview && !connectMode && (
                    <span
                      className={`${styles.resizeFrame} ${level === 'careers' ? styles.nebulaResizeFrame : styles.planetResizeFrame}`}
                      style={{ width: node.size, height: node.size }}
                    >
                      <span
                        className={styles.resizeHandle}
                        role="slider"
                        tabIndex={0}
                        aria-label="拖动调整节点尺寸"
                        aria-valuemin={level === 'careers' ? 80 : 54}
                        aria-valuemax={level === 'careers' ? 320 : 240}
                        aria-valuenow={Math.round(node.size)}
                        onPointerDown={(event) => {
                          event.stopPropagation();
                          resizing.current = {
                            id: node.id,
                            pointer: event.pointerId,
                            startX: event.clientX,
                            startY: event.clientY,
                            startSize: node.size,
                          };
                          event.currentTarget.setPointerCapture(event.pointerId);
                        }}
                        onPointerMove={(event) => {
                          if (resizing.current?.pointer === event.pointerId)
                            resizeNode(event.clientX, event.clientY);
                        }}
                        onPointerUp={(event) => {
                          event.stopPropagation();
                          resizing.current = undefined;
                        }}
                      />
                    </span>
                  )}
                  <span className={styles.nodeName}>
                    {node.name}
                    {(level === 'skills' || !preview) && (
                      <small>
                        {level === 'skills'
                          ? `${skills.find((skillNode) => skillNode.id === node.id)?.items.length ?? 0} 项学习内容`
                          : node.status === 'enabled'
                            ? '已启用'
                            : '已禁用'}
                      </small>
                    )}
                  </span>
                </div>
              ))}
            {nodes.length === 0 && (
              <div className={styles.empty}>
                当前画布没有节点
                <br />
                <button type="button" onClick={addNode}>
                  创建第一个节点
                </button>
              </div>
            )}
          </div>
        </main>
        {!preview && (
          <aside className={styles.properties}>
            <div className={styles.panelTitle}>
              属性编辑 <small>PROPERTIES</small>
            </div>
            {selected ? (
              <div className={styles.form}>
                <label>
                  <span>{level === 'careers' ? '岗位名称' : '学习节点名称'}</span>
                  <input
                    value={selected.name}
                    onChange={(event) => updateSelected({ name: event.target.value })}
                  />
                </label>
                <label>
                  <span>{level === 'careers' ? '岗位介绍' : '学习目标'}</span>
                  <textarea
                    value={selected.description}
                    onChange={(event) => updateSelected({ description: event.target.value })}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') event.stopPropagation();
                    }}
                  />
                </label>
                <label>
                  <span>本地图片</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(event) => chooseLocalImage(event.target.files?.[0])}
                  />
                </label>
                <label>
                  <span>或者使用图片 URL</span>
                  <div className={styles.urlInputRow}>
                    <input
                      type="url"
                      placeholder="https://example.com/nebula.jpg"
                      value={imageUrl}
                      onChange={(event) => setImageUrl(event.target.value)}
                    />
                    <button type="button" onClick={chooseUrlImage}>
                      载入
                    </button>
                  </div>
                </label>
                <label>
                  <span>节点尺寸　{selected.size}px</span>
                  <input
                    type="range"
                    min={level === 'careers' ? 80 : 54}
                    max={level === 'careers' ? 320 : 240}
                    value={selected.size}
                    onChange={(event) => updateSelected({ size: Number(event.target.value) })}
                  />
                </label>
                <label>
                  <span>节点状态</span>
                  <select
                    value={selected.status}
                    onChange={(event) =>
                      updateSelected({ status: event.target.value as NodeStatus })
                    }
                  >
                    <option value="enabled">启用</option>
                    <option value="disabled">禁用</option>
                  </select>
                </label>
                {level === 'careers' ? (
                  <button className={styles.routeButton} onClick={enterSkills}>
                    编辑岗位学习路线 →
                  </button>
                ) : (
                  <button className={styles.routeButton} onClick={() => setEditingItems(true)}>
                    编辑具体学习内容（{selectedSkill?.items.length ?? 0}）
                  </button>
                )}
                <button className={styles.delete} onClick={deleteNode}>
                  删除当前节点
                </button>
              </div>
            ) : (
              <div className={styles.noSelection}>请在画布或目录中选择一个节点。</div>
            )}
          </aside>
        )}
      </div>
      {editingItems && selectedSkill && (
        <LearningEditor
          node={selectedSkill}
          onChange={updateItems}
          onClose={() => setEditingItems(false)}
        />
      )}
      {confirmReturn && (
        <div className={styles.modalBackdrop} onClick={() => setConfirmReturn(false)}>
          <section className={styles.confirmDialog} onClick={(event) => event.stopPropagation()}>
            <small>UNSAVED CHANGES</small>
            <h2>是否保存当前修改？</h2>
            <p>学习路线已经发生变化。返回岗位画布前，请选择如何处理这些修改。</p>
            <div>
              <button onClick={() => setConfirmReturn(false)}>取消</button>
              <button className={styles.discardButton} onClick={discardAndReturn}>
                不保存
              </button>
              <button
                className={styles.confirmSaveButton}
                disabled={saving}
                onClick={saveAndReturn}
              >
                {saving ? '正在保存…' : '保存并返回'}
              </button>
            </div>
          </section>
        </div>
      )}
      {saveSucceeded && (
        <div className={styles.saveSuccessToast} role="status" aria-live="polite">
          <span>✓</span>
          <div>
            <b>保存成功</b>
            <small>星图数据已保存到智慧校园数据库</small>
          </div>
        </div>
      )}
      {cropSource && (
        <ImageCropper
          source={cropSource}
          kind={level === 'careers' ? 'nebula' : 'planet'}
          onCancel={() => setCropSource(undefined)}
          onApply={(image) => {
            updateSelected({ image });
            setCropSource(undefined);
            setNotice('图片裁剪已应用，请保存星图');
          }}
          onError={(message) => setNotice(message)}
        />
      )}
    </div>
  );
}

function ImageCropper({
  source,
  kind,
  onCancel,
  onApply,
  onError,
}: {
  source: string;
  kind: 'nebula' | 'planet';
  onCancel: () => void;
  onApply: (image: string) => void;
  onError: (message: string) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | undefined>(undefined);
  const dragRef = useRef<{ x: number; y: number; offsetX: number; offsetY: number } | undefined>(
    undefined
  );
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [ready, setReady] = useState(false);
  const viewportSize = 360;

  useEffect(() => {
    const image = new Image();
    if (/^https?:\/\//i.test(source)) image.crossOrigin = 'anonymous';
    image.onload = () => {
      imageRef.current = image;
      setReady(true);
    };
    image.onerror = () => {
      onError('图片加载失败，或图片地址不允许跨域读取');
      onCancel();
    };
    image.src = source;
  }, [onCancel, onError, source]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const image = imageRef.current;
    if (!canvas || !image || !ready) return;
    const context = canvas.getContext('2d');
    if (!context) return;
    try {
      drawCrop({ context, image, size: viewportSize, viewportSize, zoom, offset, kind });
    } catch {
      // 跨域图片仍可显示；确认裁剪时会给出明确的本地上传提示。
    }
  }, [kind, offset, ready, zoom]);

  function applyCrop() {
    const image = imageRef.current;
    if (!image || !ready) return;
    try {
      const output = document.createElement('canvas');
      output.width = 720;
      output.height = 720;
      const context = output.getContext('2d');
      if (!context) return;
      drawCrop({ context, image, size: 720, viewportSize, zoom, offset, kind });
      onApply(
        kind === 'nebula' ? output.toDataURL('image/png') : output.toDataURL('image/jpeg', 0.9)
      );
    } catch {
      onError('该图片地址禁止跨域裁剪，请下载图片后使用本地上传');
    }
  }

  return (
    <div className={styles.modalBackdrop} onClick={onCancel}>
      <section className={styles.cropModal} onClick={(event) => event.stopPropagation()}>
        <small>IMAGE CROP EDITOR</small>
        <h2>调整{kind === 'nebula' ? '星云' : '星球'}图片</h2>
        <p>
          拖动图片选择展示区域，使用下方滑块放大或缩小。
          {kind === 'nebula' ? ' 暗色背景和边缘会自动羽化为透明。' : ''}
        </p>
        <div
          className={`${styles.cropViewport} ${kind === 'nebula' ? styles.nebulaCrop : styles.planetCrop}`}
          onPointerDown={(event) => {
            dragRef.current = {
              x: event.clientX,
              y: event.clientY,
              offsetX: offset.x,
              offsetY: offset.y,
            };
            event.currentTarget.setPointerCapture(event.pointerId);
          }}
          onPointerMove={(event) => {
            const start = dragRef.current;
            if (!start) return;
            setOffset({
              x: start.offsetX + event.clientX - start.x,
              y: start.offsetY + event.clientY - start.y,
            });
          }}
          onPointerUp={() => {
            dragRef.current = undefined;
          }}
        >
          <canvas ref={canvasRef} width={viewportSize} height={viewportSize} />
          <span />
          {!ready && <b>图片载入中……</b>}
        </div>
        <label className={styles.cropZoom}>
          <span>缩小</span>
          <input
            type="range"
            min="1"
            max="3"
            step="0.01"
            value={zoom}
            onChange={(event) => setZoom(Number(event.target.value))}
          />
          <span>放大</span>
        </label>
        <div className={styles.cropActions}>
          <button onClick={onCancel}>取消</button>
          <button
            onClick={() => {
              setZoom(1);
              setOffset({ x: 0, y: 0 });
            }}
          >
            重置
          </button>
          <button className={styles.confirmSaveButton} disabled={!ready} onClick={applyCrop}>
            使用此区域
          </button>
        </div>
      </section>
    </div>
  );
}

function LearningEditor({
  node,
  onChange,
  onClose,
}: {
  node: SkillNode;
  onChange: (items: LearningItem[]) => void;
  onClose: () => void;
}) {
  function addItem() {
    onChange([...node.items, { id: crypto.randomUUID(), title: '新学习内容', type: '知识' }]);
  }
  return (
    <div className={styles.modalBackdrop} onClick={onClose}>
      <section className={styles.modal} onClick={(event) => event.stopPropagation()}>
        <button className={styles.close} onClick={onClose}>
          ×
        </button>
        <small>LEARNING CONTENT EDITOR</small>
        <h2>{node.name} · 学习内容</h2>
        <p>配置学生进入该技能节点后需要完成的知识、实践与考核。</p>
        <div className={styles.itemList}>
          {node.items.map((item, index) => (
            <div key={item.id} className={styles.itemRow}>
              <b>{String(index + 1).padStart(2, '0')}</b>
              <input
                value={item.title}
                onChange={(event) =>
                  onChange(
                    node.items.map((current) =>
                      current.id === item.id ? { ...current, title: event.target.value } : current
                    )
                  )
                }
              />
              <select
                value={item.type}
                onChange={(event) =>
                  onChange(
                    node.items.map((current) =>
                      current.id === item.id
                        ? { ...current, type: event.target.value as LearningItem['type'] }
                        : current
                    )
                  )
                }
              >
                <option>知识</option>
                <option>实践</option>
                <option>考核</option>
              </select>
              <button
                onClick={() => onChange(node.items.filter((current) => current.id !== item.id))}
              >
                删除
              </button>
            </div>
          ))}
        </div>
        <button className={styles.addItem} onClick={addItem}>
          ＋ 新增学习内容
        </button>
        <button className={styles.doneButton} onClick={onClose}>
          完成编辑
        </button>
      </section>
    </div>
  );
}
