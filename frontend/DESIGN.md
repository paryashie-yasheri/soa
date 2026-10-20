# Riso UI

The API workspace uses warm paper, two print inks, and offset shadows to make
collection data readable and interactive controls easy to identify.

Source skill: [Riso](../.codex/skills/design-system/SKILL.md), installed with
`npx typeui.sh pull riso --format skill --providers codex`.

## Tokens and foundations

The semantic tokens live in `src/style.css`. Font files are bundled locally.

| Token            | Value                       | Use                                               |
| ---------------- | --------------------------- | ------------------------------------------------- |
| `--paper`        | `#F8F3E8`                   | Every page, panel, input, and dialog surface      |
| `--ink-blue`     | `#2C40A7`                   | Headings, content, icons, outlines, print shadows |
| `--ink-muted`    | `#4F5A82`                   | Secondary text and placeholders                   |
| `--brand-pink`   | `#F237A1`                   | Primary actions, selected controls, focus rings   |
| `--button-ink`   | `#15152F`                   | Text on pink controls                             |
| `--rule`         | `#7B86B8`                   | Input boundaries and table separators             |
| `--print-shadow` | `4px 4px 0 var(--ink-blue)` | Cards and panels                                  |

Use Space Grotesk for interface text and headings; Overpass Mono for identifiers,
routes, and small uppercase labels. The type scale is 12/14/16/20/24/32 px.
Use 4/8/12/16/24/32 px spacing. The paper grain is static and ignores pointer events.

## Components

- Primary buttons use pink with dark text, a blue border, and a crisp print shadow.
  Hover lightens the pink and increases the offset; pressing removes the offset.
- Secondary buttons and inputs retain the paper surface with blue text.
  Selected navigation and query modes use pink so their state stays explicit.
- Cards, tables, and dialogs use blue borders and square or 2 px corners.
- Disabled buttons remove motion and shadows. Loading actions retain their labels.
  Service errors appear as readable text; organization errors also appear in the dialog.
- Tables scroll inside their container on narrow screens. Sidebar labels collapse
  visually while their accessible names remain available.

## Accessibility

- Text contrast must reach 4.5:1; control boundaries and focus indicators must reach 3:1.
  Pink buttons use dark text because white text on the brand pink misses 4.5:1.
- Keyboard focus uses a visible 3 px pink outline. The first tab stop skips to main content.
- Toolbar controls and icon buttons must have accessible names. Navigation identifies
  the selected view with `aria-current`; the filter disclosure exposes `aria-expanded`.
- Mobile icon actions have 44 px targets. Radix dialogs handle focus and Escape.
- `prefers-reduced-motion` disables transitions and spinning animations.

## Content

Use direct labels such as “Add organization,” “Apply filters,” and “Run query.”
State exact matching explicitly: “Filter by exact name.” Keep backend errors visible.

## Migration and prohibited patterns

Replace the earlier violet and white theme with semantic Riso tokens. Do not
introduce white card surfaces, blurred drop shadows, pale unreadable labels,
decorative pink headings, or remote font dependencies.

## Review checks

- Build regenerated OpenAPI types and prerendered HTML successfully.
- Verify text and boundary contrast using the tokens above.
- Tab through the skip link, navigation, filters, and dialogs; test Escape and focus return.
- Check CRUD, employees, directory operations, statistics, and service errors.
- At 390 px width, contain table overflow within the panel and retain action targets.
- Verify reduced motion and locally served fonts.
