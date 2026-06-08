# Model Providers

模型服务商的运行时代码统一放在这里。

- `base.py`：通用模型服务商接口
- `deepseek/`：DeepSeek 兼容 OpenAI API 的实现
- `xiaomi/`：小米 MiMo OpenAI API 格式实现，推荐 `base-url=https://api.xiaomimimo.com/v1`、`model=mimo-v2.5-pro`
- `qwen/`：阿里云百炼 / DashScope OpenAI 兼容实现，推荐 `base-url=https://dashscope.aliyuncs.com/compatible-mode/v1`；文本模型可用 `qwen-plus` / `qwen-max`，图片理解请配置 `qwen-vl-plus` / `qwen-vl-max` / `qwen3-vl-plus`。
- `xfyun/`：讯飞星火 OpenAI 兼容文本模型实现，推荐 `base-url=https://spark-api-open.xf-yun.com/v1`、`model=4.0Ultra`；图片理解、图片生成、视频生成已进入 catalog，专用运行时适配待接入。

后续新增 OpenAI、Ollama 等 provider 时，也按一服务商一目录放在这里。
