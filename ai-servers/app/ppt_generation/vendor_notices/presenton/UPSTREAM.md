# Presenton resource provenance

- Upstream repository: https://github.com/presenton/presenton
- Embedded source: `presenton_runtime/src/template-v2-json-to-html.ts`
- Embedded export runtime: Presenton Export v0.4.2 (downloaded during Linux image build)
- Imported commit: `c31611f9f2a5afc41b1119019f1d7332268d05a8`
- License: Apache License 2.0

AgentA3 does not embed or run the Presenton application service. The built-in
PPT engine carries the seven upstream template resource packages under
`app/ppt_generation/assets/templates`, renders their JSON layouts in-process,
and adapts the upstream content-only, layout-selection, schema-fit, and
anti-fabrication prompt rules for AgentA3's PPT agents. Authorization, task
ownership, source parsing, and PPTX export remain AgentA3 implementations.

The upstream LICENSE and NOTICE are retained in this directory. Local changes
must continue to preserve those notices and must not move Presenton account,
database, API-key, or administrator features into AgentA3.
