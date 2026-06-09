# intellij-carve

Carve markup language support for JetBrains IDEs (IntelliJ IDEA, PhpStorm,
WebStorm, PyCharm, GoLand, RubyMine, Rider, and the rest of the family).

## Features

- **Syntax highlighting** via TextMate grammar (shared with
  [vscode-carve](https://github.com/markup-carve/vscode-carve))
- **Live preview** panel (split editor view)
- **IDE theme sync** - preview follows dark/light mode
- **Code highlighting** in preview code blocks (highlight.js)
- **Export to HTML**
- **Live templates** for Carve's visual mnemonics (type `c` + `Tab`)
- **File type** recognition for `.crv` and `.carve`

## Requirements

- JetBrains IDE 2024.1+
- Java 17+

## Installation

### From disk (manual)

1. Download the latest release from
   [GitHub Releases](https://github.com/markup-carve/intellij-carve/releases),
   or build it yourself (see [docs/development.md](docs/development.md)).
2. In your IDE: **Settings → Plugins → ⚙️ → Install Plugin from Disk**.
3. Select the `intellij-carve-*.zip` file (in `build/distributions/` if built locally).
4. Restart the IDE.

## Usage

1. Open any `.crv` or `.carve` file - the editor opens in split view (source + preview).
2. The preview updates live as you type.
3. Right-click for **Export to HTML**.
4. Press `Ctrl+Shift+D` to toggle the Carve preview tool window.

## Live Templates

Type a prefix and press `Tab` to expand:

| Prefix | Expands to |
|--------|------------|
| `ch1`-`ch6` | Headings |
| `cb`, `ci`, `cbi` | Bold `*…*`, italic `/…/`, bold-italic `/*…*/` |
| `cu`, `cs`, `chl` | Underline `_…_`, strike `~…~`, highlight `==…==` |
| `csup`, `csub`, `cc` | Superscript `^…^`, subscript `,,…,,`, inline code |
| `clink`, `cimg` | Link, image |
| `cref`, `cwiki` | Cross-reference `</#id>`, implicit heading ref `[[Heading]]` |
| `ccode`, `cquote`, `chr` | Fenced code block, blockquote, thematic break |
| `cul`, `col`, `ctask`, `ctable` | Lists and table |
| `cdiv` | Div / admonition `::: name` |
| `cmath`, `cmathb` | Inline / display math |
| `ccomment`, `ccommentb`, `cfront` | Line comment, block comment, frontmatter |

## Preview Rendering

Configurable via **Settings → Tools → Carve**:

- **carve-js (default)** - the bundled `@markup-carve/carve` renderer runs on
  GraalJS. No dependencies required.
- **carve-php (PHP CLI)** - renders via
  [markup-carve/carve-php](https://github.com/markup-carve/carve-php). Requires
  PHP and `composer require markup-carve/carve-php` in your project; uses
  `vendor/bin/carve` when present.

## About Carve

[Carve](https://github.com/markup-carve/carve) is a post-Markdown lightweight
markup language that builds on Djot with visual mnemonics - syntax that looks
like its output (`/italic/`, `*bold*`, `_underline_`, `~strike~`,
`==highlight==`) - plus a Markdown-like reading flow.

## Development

Building, the bundled renderer, grammar updates, and the release process are
documented in [docs/development.md](docs/development.md).

## License

[MIT](LICENSE)
