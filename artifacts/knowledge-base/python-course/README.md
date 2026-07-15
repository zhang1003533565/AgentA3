# Python 课程知识库交付包

## 当前状态

`manifest.json` 当前为 `needs_export`。仓库中没有能够核验来源、许可证和哈希的 MaxKB 导出，因此文档数、分段数、MaxKB 版本、章节和来源均保持空值；这不是一个可导入的生产知识库包。

系统运行时使用团队已经放入 MaxKB 的 Python 课程知识库，但账号 ID、知识库 ID、API Key 和真实内容不应从运行环境反向猜测或复制进 Git。提交前必须由知识库负责人执行下面的冻结流程。

## 冻结流程

1. 在演示环境记录 MaxKB 产品版本与 Python 知识库的实际文档数、分段数和切分参数。
2. 导出 MaxKB 原生恢复包。如果该导出包含不能再分发的内容、账号标识或凭据，则不要提交原生包，只提交团队原创或许可证允许再分发的源文件和确定性导入说明。
3. 把允许提交的源文件放入本目录的 `sources/`，文件名保持稳定且不得包含姓名、学号、账号 ID 或密钥。
4. 在 `sources.csv` 中为每个文件填写：
   - `source_id`：稳定、无个人信息的来源编号；
   - `title`：资料标题；
   - `origin`：相对本目录的真实文件路径，例如 `sources/python-basics.md`；
   - `author`：作者或组织；
   - `license`：校验器允许的 SPDX/项目许可证标识；
   - `sha256`：文件真实 SHA-256。
5. 在 `manifest.json` 中把 `status` 改为 `ready`，填写实际 `maxkbVersion`、统计、切分配置、已核验章节、`sourceIds` 与 `sources`。`sources` 每项包含 `sourceId`、`path` 和 `sha256`。
6. 如果原生导出可以合法提交，将其保存为 `portable-pack.zip`，并在 `exportArtifact` 中记录路径和真实哈希；否则保持 `null`。
7. 重新计算 `README.md`、`manifest.json`、`sources.csv` 的 SHA-256，更新 `checksums.sha256`。
8. 冻结 `evaluation/python-course/gold.jsonl` 后运行：

```bash
python3 scripts/knowledge/validate_python_course.py
```

任何未知许可证、缺失文件、哈希不一致、路径穿越、未声明证据引用都会使校验失败。

## 中间阶段校验

事实评测集尚未加入时，可以只验证知识库包结构：

```bash
python3 scripts/knowledge/validate_python_course.py --skip-evaluation
```

最终质量门禁不得使用 `--skip-evaluation`。

## 允许的许可证标识

- `Apache-2.0`
- `BSD-2-Clause`
- `BSD-3-Clause`
- `CC-BY-4.0`
- `CC-BY-SA-4.0`
- `CC0-1.0`
- `MIT`
- `PSF-2.0`
- `Team-Authored`

`Team-Authored` 只表示团队确认拥有相应材料的提交权，不会自动给团队代码或第三方材料重新授权。
