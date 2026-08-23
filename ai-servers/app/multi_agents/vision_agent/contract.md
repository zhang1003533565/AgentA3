# Contract

- agentName: `vision_agent`
- invokedBy: `recognize_image_tool`
- requiredModelModalities: `vision`
- input: 用户问题和聊天中上传的图片
- output: `image_analysis_text`

## Boundaries

- 至少需要一张可访问的图片。
- 单次最多处理 8 张图片。
- 输出必须区分事实、推断和无法确认的内容。
- 不修改图片，不生成图片，不调用文生图模型。
