# Third-party assets

Bundled artwork and fonts that are not Cairn's own work, and the licences they
carry. Dependencies declared in `gradle/libs.versions.toml` are not listed here
— those are covered by the build, and will be enforced by the `licensee` plugin.

This file covers only what is **copied into the source tree**, where nothing but
this page records where it came from.

## Icons — Material Symbols

**Apache License 2.0.** Copyright Google LLC.

Source: [`google/material-design-icons`](https://github.com/google/material-design-icons),
style *Material Symbols Outlined*, weight 400, optical size 24.

The SVG path data is copied verbatim into
`core/designsystem/src/main/kotlin/app/cairn/core/designsystem/Icons.kt`. No
coordinate is rewritten; the only adaptation is a group translation reconciling
the upstream `0 -960 960 960` viewBox with `ImageVector`, which has a viewport
size but no viewport origin.

| Cairn name | Upstream symbol |
|---|---|
| `Back` | `arrow_back` |
| `Close` | `close` |
| `ChevronRight` | `chevron_right` |
| `ChevronDown` | `expand_more` |
| `ChevronUp` | `expand_less` |
| `Eye` | `visibility` |
| `EyeOff` | `visibility_off` |
| `Upload` | `upload` |
| `Form` | `description` |
| `Settings` | `settings` |
| `Alert` | `error` |

Verbatim copying is deliberate: it means anyone can check a glyph is the glyph
it claims to be, in one command.

```
NAME=settings
curl -s "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/$NAME/materialsymbolsoutlined/${NAME}_24px.svg"
```

Apache 2.0 requires the licence to travel with the work. Full text:
<https://www.apache.org/licenses/LICENSE-2.0>.

## Fonts

All three are **SIL Open Font License 1.1**, bundled as variable TTFs in
`core/designsystem/src/main/res/font/`.

| File | Family | Copyright |
|---|---|---|
| `literata.ttf` | Literata | Copyright The Literata Project Authors |
| `hanken_grotesk.ttf` | Hanken Grotesk | Copyright The Hanken Grotesk Project Authors |
| `jetbrains_mono.ttf` | JetBrains Mono | Copyright The JetBrains Mono Project Authors |

OFL 1.1 permits bundling and redistribution, including in a commercial product,
provided the fonts are not sold on their own and the licence travels with them.
Full text: <https://openfontlicense.org/>.

## Known gap

The full licence texts are referenced here by URL rather than vendored into the
repository. Apache 2.0 §4(a) and OFL 1.1 both want the licence *included* with
the distribution, so `LICENSES/` with the two texts is owed before any release
that ships outside this repository — F-Droid in particular checks for it. Cairn
has published nothing yet, so nothing is currently out of compliance.

Tracked in the wiki backlog.
