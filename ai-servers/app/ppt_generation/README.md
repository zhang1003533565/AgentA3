# AgentA3 built-in PPT engine

The PPT generation flow is part of `ai-servers`; no Presenton service, API key,
database, or administrator account is required.

## Runtime flow

1. AgentA3 stores the authenticated user's source file in `AI_PPT_SOURCE_ROOT`.
2. `ppt_outline_agent` creates the editable outline from source evidence.
3. `ppt_content_agent` writes grounded per-slide content.
4. `ppt_layout_agent` produces layout intent.
5. `presenton_renderer` selects a bundled Presenton layout and renders its JSON
   elements (text, vectors, images, containers, grids, charts and tables) into
   an editable PPTX.
6. LibreOffice converts PPTX to PDF; PyMuPDF creates page previews.
7. AgentA3's existing task and capability-token stores protect generated files.

## Configuration

```env
PPT_DEFAULT_TEMPLATE=general
AI_PPT_SOURCE_ROOT=/app/data/ppt-sources
PPT_SOURCE_FILE_TTL_SECONDS=86400
PPT_TASK_TTL_SECONDS=604800
```

The seven bundled template resource packages are derived from the pinned
Presenton source recorded in `vendor_notices/presenton/UPSTREAM.md`. AgentA3
owns the runtime implementation; upstream account, database, API, and editor
code are not used.

The dedicated PPT flow always uses the in-process Presenton template renderer.
There is no legacy fixed-layout fallback and no separate Presenton service to start.
