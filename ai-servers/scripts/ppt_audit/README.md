# PPTX Forensic Audit

这是最终 `.pptx` 文件的可重复审计入口。它只读 PPTX，不修改输入文件，不调用模型。

## 基本运行

在 `ai-servers` 目录执行：

```powershell
.\scripts\run_ppt_audit.ps1 `
  -Pptx "D:\path\to\sample.pptx" `
  -OutputDir "D:\path\to\audit-output"
```

如需使用本机 Microsoft PowerPoint 生成原生 PNG：

```powershell
.\scripts\run_ppt_audit.ps1 `
  -Pptx "D:\path\to\sample.pptx" `
  -OutputDir "D:\path\to\audit-output" `
  -RenderOffice
```

## 输出

- `audit.json`：完整机器可读结果，包括每页文本、字体、字号、AutoFit、坐标、媒体关系和 findings。
- `audit.md`：人类可读摘要。
- `render.json`：Office 渲染是否可用、版本链路返回和错误信息。
- `environment.json`：操作系统、locale、字体目录、请求字体匹配结果和可用渲染器。
- `office-render/slide-N.png`：PowerPoint 原生渲染结果（仅在安装 Office 且 `-RenderOffice` 成功时生成）。

## 检查范围

- PPTX ZIP 包和关键 XML 是否完整。
- 实际页面尺寸、比例和 `type` 元数据是否一致。
- 元素是否越出画布、是否存在零尺寸元素。
- PPTX 中真实写入的文本、字号、字体、段落数、Run 数和 AutoFit。
- 省略号、模板占位文本、同页/跨页重复文本。
- 文本框容量风险和带文字元素的高度重叠。
- 图片关系是否指向实际存在的 `ppt/media/*` 文件。
- 渲染机器的实际字体/渲染器环境是否可用；环境不可用会被记录为环境阻断，不会伪装成 PPT 通过。

几何/文本确定性检查与视觉判断分开记录：脚本不会把“没有安装 Office”伪装成 PPT 通过，也不会把模板中故意的装饰重叠直接判为生成错误。

## 模板结构审计

在修改模板语义、内容填充或连接线规则之前，先运行全模板结构扫描：

```powershell
uv run python scripts/ppt_audit/audit_template_structure.py `
  --out "D:\path\to\template-structure-audit"
```

该脚本只读 `app/ppt_generation/assets/templates/*/template.json`，检查全部模板和布局中的：

- 线状 vector 是否有显式绑定目标，或能根据端点与有效文本盒子高置信度推断目标；
- 重复文字槽位是否需要保留 occurrence 和父级关系；
- Flex/Group 动态布局是否存在；
- 标题/正文成对槽位是否与连接器同时出现。

它会按统一的模板模型解析 Flex/Group 的有效位置，但不会修改模板坐标；无法高置信度判定为连接目标的线条会单独记为 `UNRESOLVED_CONNECTOR_CANDIDATE`，不等同于确认存在视觉错误。它不会调用模型或生成 PPTX。输出 `template-structure.json` 和 `template-structure.md`，作为后续通用修复和回归的基线。

如需对全部布局执行一次确定性的内容填充回归（不调用模型、不导出 PPTX）：

```powershell
uv run python scripts/ppt_audit/audit_template_fill.py `
  --out "D:\path\to\template-fill-audit"
```

该回归把 `TEXT_OVERFLOW` 单独统计为内容适配前的风险，把连接目标为空、结构缺失等问题作为结构失败；这样不会把“尚未运行 RepairEngine 的短样例”误报成模板结构错误。
