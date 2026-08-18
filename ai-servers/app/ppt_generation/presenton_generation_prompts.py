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

The response must include a complete `ui` object copied from the supplied
Presenton layout JSON. The UI tree, component order, names, coordinates, sizes,
fonts, colors, SVG paths, image references, and nesting are immutable. Change only
audience-visible text runs and the data fields of table/chart components. Do not
return a simplified slot map and do not invent a new layout.

For text components, replace the existing `runs[].text` or `text` value with:
- headline/heading/title: a concise slide conclusion
- body/paragraph/description/copy: short audience-facing explanation
- item_title/item_body/feature_title/feature_description: repeated card content
- text-list: the requested number of concise list entries
For data components use the existing Presenton shape:
- table: `columns` and `rows`
- chart: `categories` and `series` with numeric `values`

Do not invent numbers, images, sources, or examples. Keep template image paths
unchanged; image decisions belong in `visualPrompt` and are applied by the task
worker after the UI JSON is validated. Do not emit layout metadata, prompt text,
or speaker instructions as slide content. Rephrase instead of clipping sentences.
Keep each slide centered on one conclusion.
""".strip()


__all__ = ["PRESENTON_STRUCTURE_RULES", "PRESENTON_CONTENT_RULES"]
