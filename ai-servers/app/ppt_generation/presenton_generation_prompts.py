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
Generate the selected slide's content strictly from the supplied source and outline.

The response must include layoutContent. Its keys must be real component names from
the selected layout slots. Generate values by component role:
- headline/heading/title: a concise slide conclusion
- body/paragraph/description/copy: short audience-facing explanation
- item_title/item_body/feature_title/feature_description: repeated card content
- text-list: a string array with only the requested number of items
- table_data: {columns: [...], rows: [[...], ...]}
- chart_data: {categories: [...], series: [{name: "...", values: [...]}]}

Do not invent numbers, images, sources, or examples. Do not emit layout metadata,
prompt text, or speaker instructions as slide content. Rephrase instead of clipping
sentences. Keep each slide centered on one conclusion.
""".strip()


__all__ = ["PRESENTON_STRUCTURE_RULES", "PRESENTON_CONTENT_RULES"]
