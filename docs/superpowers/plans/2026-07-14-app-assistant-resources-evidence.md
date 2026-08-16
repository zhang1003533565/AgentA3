# APP Assistant Resources and Evidence Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Every behavior change starts with a failing test and ends with fresh focused and regression verification.

**Goal:** Make APP assistant resources render consistently in live/history/full/float contexts, persist a transparent evidence chain, secure generated-file access behind the Java session boundary, and feed resource interactions back into the existing profile evidence mechanism.

**Architecture:** Python remains the resource/evidence normalizer, Java remains the user/session/persistence/download authority, and uni-app uses one pure helper contract in both assistant surfaces. Existing response fields remain as a compatibility layer.

**Tech Stack:** Python 3.11, FastAPI/Pydantic/pytest; Java 21, Spring Boot 4, JPA/WebClient/JUnit/Mockito; uni-app Vue 3 and Node `node:test` source-contract tests.

## Global constraints

- Work directly on `master`; do not create a branch or worktree.
- Preserve `answer`, `outputTypes`, `outputMeta`, `attachments`, `trace`, and existing application API routes; the unauthenticated Python static export mount is explicitly replaced, not preserved.
- No new dependency.
- Do not expose Authorization, model API keys, raw profile snapshots, or Python internal URLs in resources/evidence.
- Do not expose generated-file capability tokens to APP clients or history DTOs; they exist only in the internal persisted snapshot and the Java-to-Python proxy header.
- SHA-256 manifests are integrity digests, not signatures.
- Java must verify user, session, message, and exact resource membership before proxying a generated export; Python independently verifies a per-file 256-bit capability in constant time.
- Pure model answers remain allowed but must be labeled `model_only`.
- Public matched-result/evidence snapshots are capped at 20 entries, 800 characters per excerpt, and safe metadata allowlists.
- Resource interaction evidence must go through a resource-specific Java endpoint that derives all required profile fields and deduplicates the same user/message/resource/action.
- Java download authorization uses a typed `AiLeaderGeneratedExport` manifest, never a scan of model-influenced JSON.
- All IDs persist unchanged across live/history; time is RFC 3339 UTC and digests are lowercase `sha256:<64 hex>` over the specified canonical JSON.

## Task 1: Lock the Python resource/evidence contract

**Files:**

- Create `ai-servers/app/services/assistant_resource_builder.py`
- Modify `ai-servers/app/models/schemas.py`
- Modify `ai-servers/app/api/routes/rag.py`
- Create `ai-servers/tests/test_assistant_resource_builder.py`
- Extend `ai-servers/tests/test_sse_stream_contract.py`

- [ ] Write failing unit tests for attachment resources, the six exact business-card allowlists from the design, forbidden PII stripping, typed content/file payloads, response-level content resources, deterministic source digests, mutually exclusive response/resource `grounded/context_only/model_only`, 20x800 caps, envelope byte caps, safe metadata filtering, exact bidirectional resource-to-evidence links, and integrity digest verification. Generated artifacts alone must not produce `grounded`.
- [ ] Run RED: `cd ai-servers && .venv/bin/python -m pytest tests/test_assistant_resource_builder.py -q`.
- [ ] Add Pydantic `resources` and `evidenceChain` fields and implement one builder that consumes response answer/documents/trace/metadata/attachments plus safe request context.
- [ ] Finalize every sync response and SSE `done` payload through the same builder; include `resources` and `evidenceChain` in conversation context only as safe summaries.
- [ ] Run GREEN and SSE contract tests.

## Task 2: Make generated exports durable and internally readable

**Files:**

- Modify `ai-servers/app/rag/document_conversion/generated_exporter.py`
- Modify `ai-servers/app/main.py`
- Modify `ai-servers/app/api/routes/rag.py`
- Modify `.gitignore`
- Extend `ai-servers/tests/test_generated_exporter.py`
- Create `ai-servers/tests/test_generated_export_download.py`

- [ ] Write failing tests requiring full-UUID storage keys, repository-local default storage, SHA-256/size/createdAt/expiresAt metadata, a random per-file capability whose digest is stored in an atomic sidecar manifest, restart recovery from that sidecar, pairwise TTL/capacity cleanup, traversal rejection, constant-time capability rejection, and absence of the public static mount.
- [ ] Run RED: `cd ai-servers && .venv/bin/python -m pytest tests/test_generated_exporter.py tests/test_generated_export_download.py -q`.
- [ ] Move the development default root to ignored `ai-servers/data/ai-exports`, atomically write each file plus `<storageKey>.meta.json` sidecar containing only capability digest/integrity/expiry, return `storageKey`, integrity/expiry metadata, `serverGenerated=true` and the one-time internal capability, and remove public `StaticFiles` exposure. Production mode fails fast unless `AI_EXPORT_ROOT` is an explicit persistent shared mount/object-store adapter path.
- [ ] Add `GET /internal/rag/exports/{storage_key}` with safe basename/path resolution and required `X-AI-Export-Capability`; ordinary/forged Authorization alone is insufficient, and capability digests are compared in constant time.
- [ ] Run GREEN and the existing exporter regression suite.

## Task 3: Persist the complete envelope in Java

**Files:**

- Modify `AppBackend/src/main/java/com/example/appbackend/dto/LlmChatResponse.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/dto/AiLeaderMessageItem.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/entity/AiLeaderMessage.java`
- Create `AppBackend/src/main/java/com/example/appbackend/entity/AiLeaderGeneratedExport.java`
- Create `AppBackend/src/main/java/com/example/appbackend/repository/AiLeaderGeneratedExportRepository.java`
- Create typed assistant resource/evidence DTOs under `AppBackend/src/main/java/com/example/appbackend/dto/`
- Modify `AppBackend/src/main/java/com/example/appbackend/controller/AppAiLeaderController.java`
- Create `AppBackend/src/test/java/com/example/appbackend/controller/AppAiLeaderControllerTest.java`

- [ ] Write failing controller tests for sync mapping, SSE `messageId`, typed validation, assistant-message persistence and history recovery of sanitized/capped `matchedResults/resources/evidenceChain`, and export manifest creation only for `serverGenerated=true` exporter records.
- [ ] Run RED: `cd AppBackend && mvn -Dtest=AppAiLeaderControllerTest test`.
- [ ] Add `matched_results_json`, `resources_json`, and `evidence_chain_json`, map the new response fields, and restore them in history DTOs.
- [ ] Persist capability/digest/size/expiry only in the typed export manifest, then rewrite both `resources` and legacy `attachments` to the authenticated Java `sessionId/messageId/storageKey` path before returning them; strip capability from message JSON and all client DTOs.
- [ ] Recompute canonical evidence integrity on history read and expose formal `evidenceState`; never treat malformed/integrity-failed evidence as available.
- [ ] Mark old Python static URLs as `legacy_unavailable` instead of reopening the static directory.
- [ ] Log field/message identity on malformed stored JSON and expose formal `evidenceState=malformed` without leaking payloads.
- [ ] Run GREEN.

## Task 4: Enforce session-owned export download

**Files:**

- Modify `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/controller/AppAiLeaderController.java`
- Extend `AppBackend/src/test/java/com/example/appbackend/controller/AppAiLeaderControllerTest.java`
- Extend `AppBackend/src/test/java/com/example/appbackend/service/impl/PythonAiProxyServiceTest.java`

- [ ] Write failing tests proving a foreign session, foreign message and unbound storage key are rejected; a bound manifest is proxied with its persisted capability; response bytes respect the configured maximum; downloaded SHA-256/size are verified; content type, sanitized disposition and 410 expiry are preserved.
- [ ] Run RED for both focused test classes.
- [ ] Add a bounded byte-response proxy method and `GET /api/ai/leader/sessions/{sessionId}/messages/{messageId}/exports/{storageKey}`.
- [ ] Query the typed export manifest by user/session/message/storageKey/status, retrieve its hidden capability, proxy bounded bytes, then verify digest/size before returning.
- [ ] Run GREEN.

## Task 5: Add validated, idempotent resource interaction evidence

**Files:**

- Create `AppBackend/src/main/java/com/example/appbackend/dto/AssistantResourceInteractionRequest.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/controller/AppAiLeaderController.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/repository/UserProfileEvidenceRepository.java`
- Modify `AppBackend/src/main/java/com/example/appbackend/service/impl/UserProfileServiceImpl.java`
- Extend `AppBackend/src/test/java/com/example/appbackend/controller/AppAiLeaderControllerTest.java`

- [ ] Write failing tests for action whitelist, foreign session/message/resource rejection, server-derived required profile fields, `assistant_resource` source reliability, positive/negative deltas, and duplicate user/message/resource/action suppression.
- [ ] Run RED: `cd AppBackend && mvn -Dtest=AppAiLeaderControllerTest test`.
- [ ] Add `POST /api/ai/leader/sessions/{sessionId}/messages/{messageId}/resources/{resourceId}/interactions`; derive `dimensionKey=resource_preference`, nonblank evidence, object fields and a deterministic source ID before calling the existing `UserProfileService.addEvidence`; register `assistant_resource` at the existing click/resource reliability tier.
- [ ] Run GREEN.

## Task 6: Share APP normalization and resource evidence payloads

**Files:**

- Create `AppFrontend/subpackage_ai/assistantMessage.js`
- Create `AppFrontend/subpackage_ai/assistantMessage.test.js`
- Modify `AppFrontend/api/ai.js`

- [ ] Write failing pure tests for old attachment fallback, new resource precedence, `legacy_unavailable`, typed/unknown resource fallback, local business-route allowlists, rejected unsafe external routes, URL resolution, authenticated media/file download headers, evidence summary, the resource-interaction route/payload, and non-duplicated hit count.
- [ ] Run RED: `node --test AppFrontend/subpackage_ai/assistantMessage.test.js`.
- [ ] Implement pure normalization helpers and an API helper for the new validated resource-interaction endpoint.
- [ ] Run GREEN.

## Task 7: Render resources and evidence in the full conversation page

**Files:**

- Modify `AppFrontend/subpackage_ai/aiConversation/aiConversation.vue`
- Create `AppFrontend/subpackage_ai/assistantPages.test.js`

- [ ] Write a failing source-contract test requiring the shared helper, resource cards, evidence/source panel, authenticated resource open, interaction evidence, and the corrected hit-count helper. This test proves wiring only; pure helper tests prove behavior.
- [ ] Run RED: `node --test AppFrontend/subpackage_ai/assistantMessage.test.js AppFrontend/subpackage_ai/assistantPages.test.js`.
- [ ] Replace duplicated attachment guessing with shared helpers, render business/generic resources and source details, download protected files/media to authenticated local temp paths, and submit non-blocking open/download/preview evidence.
- [ ] Preserve old history behavior when the new fields are absent.
- [ ] Run GREEN.

## Task 8: Bring the floating assistant to feature parity

**Files:**

- Modify `AppFrontend/components/ai-float-assistant/ai-float-assistant.vue`
- Extend `AppFrontend/subpackage_ai/assistantPages.test.js`

- [ ] Add failing assertions for document/video/business-card rendering, follow-up actions, compact evidence summary, history-field retention, and shared helper use.
- [ ] Run RED.
- [ ] Render the same normalized resources, retain `messageId/matchedResults/retrievalMeta/trace/resources/evidenceChain` on history and SSE completion, and support authenticated file/media open plus resource evidence.
- [ ] Run GREEN.

## Task 9: Cross-layer verification and documentation

- [ ] Python focused: `cd ai-servers && .venv/bin/python -m pytest tests/test_assistant_resource_builder.py tests/test_generated_exporter.py tests/test_generated_export_download.py tests/test_sse_stream_contract.py -q`.
- [ ] Python full: `cd ai-servers && .venv/bin/python -m pytest -q`.
- [ ] Java focused: `cd AppBackend && mvn -Dtest=AppAiLeaderControllerTest,PythonAiProxyServiceTest test`.
- [ ] Java full: `cd AppBackend && mvn test`.
- [ ] APP AI: `node --test AppFrontend/subpackage_ai/assistantMessage.test.js AppFrontend/subpackage_ai/assistantPages.test.js`.
- [ ] Existing APP regression: `node --test AppFrontend/subpackage_exam/examState.test.js AppFrontend/subpackage_exam/examPages.test.js`.
- [ ] Static checks: `git diff --check` and review `git diff --stat`/`git status --short`.
- [ ] Review security properties: no static export mount, forged ordinary Bearer cannot access Python exports, no capability/internal URL in client payload, exact typed manifest ownership check, downloaded digest verification, no secret fields in evidence snapshots.
- [ ] Record that local export storage is single-instance and that uni-app true runtime/visual E2E was not executed unless an actual runtime becomes available.
- [ ] Update the design if implementation discovers a contract change; do not silently diverge.

## Completion criteria

- Live and history responses preserve identical resources and evidence.
- Full and floating assistants both expose documents, video, business cards, actions, and evidence state.
- Generated exports require both a logged-in Java user/session/message binding and the hidden per-file Python capability.
- Resource interactions enter the existing candidate evidence workflow.
- Focused suites pass; full-suite failures, if any, are proven pre-existing and documented with exact commands.
