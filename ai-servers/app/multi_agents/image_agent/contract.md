# Contract

## Agent
- `image_agent`

## Goal
图片智能体不再只负责生成图片提示词，而是升级为可执行的文生图智能体，支持单张生成、批量生成、任务状态追踪和结果回传。

## Input
基础输入：
- `topic`: 用户主题或创作需求。
- `evidence`: RAG 检索到的知识点、教材片段、课程上下文或用户画像信息。

生成参数：
- `prompt`: 原始提示词或用户自然语言描述。
- `style`: 图片风格，例如教学插画、扁平插画、写实、国风、水彩、PPT封面、课堂板书风。
- `size`: 图片尺寸，例如 `1024x1024`、`1280x720`、`720x1280`。
- `count`: 生成数量，单张默认为 `1`，批量任务可大于 `1`。
- `seed`: 随机种子，可为空；为空时由服务端生成并在结果中回传。
- `negativePrompt`: 负向提示词，例如低清晰度、文字乱码、畸形手、过度拥挤。
- `metadata`: 业务扩展字段，例如课程、章节、用途、用户画像、批次名称。

## Output
统一输出结构：

```json
{
  "taskId": "img_20260530_xxxxxx",
  "mode": "single | batch",
  "status": "pending | running | success | partial_success | failed",
  "prompt": "最终用于生成的提示词",
  "style": "教学插画",
  "size": "1024x1024",
  "count": 4,
  "seed": 123456,
  "negativePrompt": "低清晰度、文字乱码",
  "images": [
    {
      "index": 0,
      "url": "https://...",
      "base64": "",
      "status": "success",
      "seed": 123456,
      "errorMessage": ""
    }
  ],
  "message": "生成完成"
}
```

返回规则：
- 支持返回 `url`，用于前端直接展示。
- 支持返回 `base64`，用于无需对象存储时的临时预览。
- 批量任务必须返回每张图的 `index/status/seed/errorMessage`。
- 必须保留并回传最终提示词、风格、尺寸、数量、种子等参数，便于复现和审计。

## Required Capabilities
- 单张文生图：根据用户输入生成一张图片。
- 批量文生图：同一主题一次生成多张图片，或按多条提示词分别生成。
- 提示词增强：结合 `topic`、`evidence` 和用户画像，将简单需求改写为高质量图片提示词。
- 任务状态查询：批量或耗时任务需要支持查询 `taskId` 的状态。
- 失败可解释：模型调用失败、参数不合法、部分图片失败时必须返回可读错误。

## Error Handling
- 输入为空：返回明确提示，要求补充主题或提示词。
- `count` 非法：返回参数错误，建议范围为 `1-8`。
- `size` 不支持：返回可选尺寸列表。
- 生成服务不可用：返回 `failed` 状态和错误原因，不吞掉异常。
- 批量部分失败：返回 `partial_success`，成功图片仍可展示。
