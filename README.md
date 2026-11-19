# intellij-carve

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/32204-carve.svg)](https://plugins.jetbrains.com/plugin/32204-carve)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32204-carve.svg)](https://plugins.jetbrains.com/plugin/32204-carve)
[![Build](https://img.shields.io/github/actions/workflow/status/markup-carve/intellij-carve/build.yml?branch=main)](https://github.com/markup-carve/intellij-carve/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Carve support for JetBrains IDEs, with highlighting, preview, and optional language server features. Plugin version 0.1.19 targets JetBrains IDE 2025.1 and later. See the [Carve documentation](https://markup-carve.github.io/carve/) for the markup language.

## Features

- Syntax highlighting and live preview. On the `next2` branch the TextMate grammar reads Carve 0.2 source (root scope `source.carve`), and preview, export, import and the bundled language server run on the unreleased Carve 0.2 engine (carve-js and carve-lsp `next2`, pinned by commit).
- The preview enables the standard 0.2 extensions it can draw (details, spoiler, tabs, code groups, list tables, math, mermaid, charts, `:cite[...]` citations with a `references` list, `:term[...]` glossary links and `:index[...]` entries) and links bare URLs. The `cwiki` template writes the 0.2 heading reference `[Heading][]`.
- Import and export commands.
- Optional diagnostics, completion, and navigation through LSP4IJ.

## Install

Install [Carve from JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32204-carve). Install LSP4IJ to enable language server features.

## Usage

Open a `.crv` file to edit beside its live preview.

### Export to PDF

Right-click a Carve file in the editor and choose **Export to PDF**. The plugin uses `crv2pdf` when installed, preserving its print layout. If `crv2pdf` is unavailable, it renders the same standalone page as **Export to HTML** and prints it with an installed Chrome, Chromium, or Edge browser. Set either executable path under **Settings | Tools | Carve | PDF Export** if automatic discovery does not find it. The export reports command failures and missing tools in an IDE notification.

## Documentation

- [Carve documentation](https://markup-carve.github.io/carve/)
- [Setup and full guide](docs/guide.md)
- [Documentation index](docs/README.md)
