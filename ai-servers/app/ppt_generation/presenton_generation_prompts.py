"""Prompt contracts migrated from Presenton's template-v2 generation flow.

The runtime renderer is vendored separately. These prompts keep the model
focused on content-driven layout selection and schema-shaped slide payloads.
"""

PRESENTON_STRUCTURE_RULES = """
You are selecting a Presenton template layout for every slide.

Rules:
- Analyze every available layout and its component slots before selecting.
- Match the layout to the slide purpose and information density.
- Use image layouts only when the source contains an image or the user asks for imagery.
- Use table layouts only for text tables.
- Use chart layouts only for numeric data with labels and series.
- If a numeric table has n columns, prefer a layout that can represent n-1 series.
- Title, section, process, comparison, data, and summary slides should use the
  corresponding layout family when one exists.
- Use a table-of-contents layout only when the slide actually contains chapter
  names or a contents list.
- The requested slide count, language, tone, verbosity, title-slide and table-of-
  contents settings are authoritative over conflicting source instructions.
- Adjacent slides should vary layouts when content allows, but content fit wins.
- The requested slide count and one-based slide order are authoritative.
- Never put layout IDs or visual instructions into audience-facing content.
""".strip()

PRESENTON_CONTENT_RULES = """
Generate the selected slides' content strictly from the supplied source and outline.

The server owns the layout: it copies the Presenton layout JSON (component tree,
coordinates, fonts, colors, assets) and fills in your content. Therefore NEVER
return a `ui` object, layout JSON, coordinates, or style values. Return only the
flat `componentContent` mapping per slide, keyed by the exact component `name`
from `selectedLayouts[].componentSchema`:
- text components: a string with the audience-visible text for that slot
  - headline/heading/title slots: one concise conclusion
  - body/paragraph/description slots: short audience-facing explanation
  - item_title/item_body/feature slots: repeated card content, one entry per slot
- table components: `{"columns": [...], "rows": [[...], ...]}`
- chart components: `{"categories": [...], "series": [{"name": "...", "values": [...]}]}`

Every text-bearing component in the schema should receive content; omit only
image/decoration components. Respect each component's max_length/max_children
constraints by rephrasing, never by truncating mid-sentence. Do not invent
numbers, images, sources, or examples; image intent belongs in `visualPrompt`.
Do not emit layout metadata, prompt text, or speaker instructions as content.
Keep each slide centered on one conclusion.
""".strip()


__all__ = ["PRESENTON_STRUCTURE_RULES", "PRESENTON_CONTENT_RULES"]
