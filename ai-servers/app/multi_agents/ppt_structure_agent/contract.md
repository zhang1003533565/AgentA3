# Contract

Input: JSON containing `templateId`, `slideCount`, `layouts`, `outline`,
`settings`, and optional `userInstructions`.

Output: `{"layouts":[{"slideIndex":1,"layoutId":"title_intro"}]}` with one
one-based entry per slide. `layoutId` must come from the supplied catalog.
