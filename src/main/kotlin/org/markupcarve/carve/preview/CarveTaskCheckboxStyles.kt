package org.markupcarve.carve.preview

/** Task markers in preview and exported HTML. */
internal object CarveTaskCheckboxStyles {
    const val CSS = """
        :root {
            --carve-task-border: var(--carve-ink-soft, #6a737d);
            --carve-task-checked: var(--carve-success, #22863a);
            --carve-task-check-icon: url("data:image/svg+xml,%3Csvg xmlns='http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg' viewBox='0 0 16 16'%3E%3Cpath fill='none' stroke='%23fff' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round' d='M3 7l3 3 7-7'/%3E%3C/svg%3E");
        }
        :root[data-theme="dark"] {
            --carve-task-border: var(--carve-ink-soft, #8b949e);
            --carve-task-checked: var(--carve-success, #85e89d);
            --carve-task-check-icon: url("data:image/svg+xml,%3Csvg xmlns='http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg' viewBox='0 0 16 16'%3E%3Cpath fill='none' stroke='%230d1117' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round' d='M3 7l3 3 7-7'/%3E%3C/svg%3E");
        }
        li > input[type="checkbox"] {
            appearance: none;
            opacity: 1;
            display: inline-grid;
            place-content: center;
            width: 1em;
            height: 1em;
            margin-right: 0.5em;
            vertical-align: middle;
            border: 1.5px solid var(--carve-task-border);
            border-radius: 3px;
            background: transparent;
        }
        li > input[type="checkbox"]:checked {
            border-color: var(--carve-task-checked);
            background: var(--carve-task-checked);
            background-image: var(--carve-task-check-icon);
            background-position: center;
            background-repeat: no-repeat;
            background-size: 80% 80%;
        }
        li[data-task-state] > input[type="checkbox"] { outline-offset: 2px; }
        li[data-task-state="-"] > input[type="checkbox"] {
            border-color: var(--carve-ink-soft, #6a737d);
            background-image: linear-gradient(var(--carve-ink-soft, #6a737d), var(--carve-ink-soft, #6a737d));
            background-position: center;
            background-size: 60% 2px;
            background-repeat: no-repeat;
        }
        li[data-task-state="/"] > input[type="checkbox"] {
            border-color: var(--carve-accent, #14607f);
            background-color: var(--carve-accent-soft, #e6f4f8);
            background-image: linear-gradient(135deg, transparent 38%, var(--carve-accent, #14607f) 39% 61%, transparent 62%);
            background-position: center;
            background-size: 70% 70%;
            background-repeat: no-repeat;
        }
        li[data-task-state="!"] > input[type="checkbox"] {
            border-color: var(--carve-danger, #9c2c1c);
            background-color: var(--carve-danger-wash, #fff0ec);
            background-image: linear-gradient(var(--carve-danger, #9c2c1c), var(--carve-danger, #9c2c1c)),
                radial-gradient(circle, var(--carve-danger, #9c2c1c) 65%, transparent 70%);
            background-position: center 30%, center 78%;
            background-size: 2px 47%, 4px 4px;
            background-repeat: no-repeat;
        }
        li[data-task-state=">"] > input[type="checkbox"] {
            outline: var(--carve-border-width, 1px) solid var(--carve-accent, #14607f);
        }
        li[data-task-state="?"] > input[type="checkbox"] {
            outline: var(--carve-border-width, 1px) dotted var(--carve-ink-soft, #6a737d);
        }
        :root[data-carve-contrast="high"] li > input[type="checkbox"] { border-width: 2px; }
        @media (prefers-contrast: more) {
            :root:not([data-carve-contrast="normal"]) li > input[type="checkbox"] { border-width: 2px; }
        }
        @media (forced-colors: active) {
            li > input[type="checkbox"] { appearance: auto; background-image: none; }
        }
    """
}
