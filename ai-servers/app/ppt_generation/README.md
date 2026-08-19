# AgentA3 built-in PPT engine

The PPT generation flow is part of `ai-servers`; no Presenton service, API key,
database, or administrator account is required.

## Runtime flow

1. AgentA3 stores the authenticated user's source file in `AI_PPT_SOURCE_ROOT`.
2. `ppt_outline_agent` creates the editable outline from source evidence.
3. `ppt_structure_agent` selects a layout by the Presenton structure prompt and
   component schema.
4. `ppt_content_agent` returns the complete Presenton UI JSON tree for each
   selected layout. Geometry, nesting, CSS attributes, SVGs, fonts, and asset
   references are validated and passed through unchanged.
5. Optional AI visuals are generated asynchronously by AgentA3's `image_agent`
   during the task, so the outline/slides endpoints are not held open by image
   generation.
6. The embedded Presenton runtime resolves only local asset URLs and passes the
   complete UI tree to Presenton's original `template-v2-json-to-html` renderer
   in Chromium. This preserves the template's CSS, SVG, fonts, charts, and
   component hierarchy without an AgentA3-side hydration layer.
7. The pinned `presenton-export` runtime converts the rendered presentation to
   PPTX in the Linux container; the same Chromium page creates the PDF and
   previews.
8. AgentA3's existing task and capability-token stores protect generated files.

## API surface

The Java app exposes the public `/api/app/ai/ppt/*` routes and proxies them to
the internal Python routes below:

- `GET /options` and `GET /templates/{templateId}/thumbnail`
- `POST /files`, `/outlines`, `/slides`, `/slides/tasks`, `/tasks`
- `GET /tasks/{taskId}`, `/tasks/{taskId}/stream`
- `POST /tasks/{taskId}/cancel`, `/retry`, and
  `/slides/{slideIndex}/image` for user-supplied image replacement
- `GET /tasks/{taskId}/files/{pptx|pdf}` and
  `/tasks/{taskId}/previews/{slideIndex}`

The public `/slides` route and the explicit `/slides/tasks` alias both start the
asynchronous page-content phase. They return a task immediately and report
`currentSlide`, `totalSlides`, `completedSlides`, `remainingSlides`, and
`processingSlides` through the existing task SSE stream. The task completes only
after every page has a full Presenton UI JSON tree; the client can then edit
pages and submit them to `/tasks` for rendering/export.

All task and artifact routes are owner-scoped. A generated image is only used
after it has been written into the protected export store; arbitrary local
paths are never accepted from the client.

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

The dedicated PPT flow always uses the in-process Presenton HTML/Chromium and
export runtime. There is no separate Presenton service to start, and the old
python-pptx rendering path has been removed.

For local development, PDF and PNG previews work with the bundled runtime. PPTX
requires the pinned `presenton-export` bundle; the Dockerfile downloads it at
image build time. If that bundle is unavailable, the task remains usable and
returns a `formatErrors.pptx` explanation instead of silently falling back to a
different renderer.
