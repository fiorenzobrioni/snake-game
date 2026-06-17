# DevLog

Development log folder and project design notes. For roadmap, TODOs, and ideas, see `PLANNING.md`.

## Structure

```
devlog/
├── README.md   <- this file, do not modify
├── 2025.md     <- 2025 log
└── 2026.md     <- 2026 log (highest year = active log)
```

One file per year. The file with the highest year is always the active one: no special files, the name says it all.

## Rules

- Always write in the current year's file (`YYYY.md`). If it doesn't exist, create it using the template below.
- When the year changes, create the new `YYYY.md` and do not touch the old one anymore.
- Latest development always at the top of the file.
- If a file exceeds roughly 1000 lines (rare case), split it into `YYYY-a.md` and `YYYY-b.md`.

## Template for a new year

```markdown
# DEVLOG {repo name} - {YYYY}

Development log {YYYY}. Latest development at the top. For roadmap, TODOs, and ideas, see `PLANNING.md`.

> Statuses: `✅ Done` - `🔧 In progress` - `⬜ To do`
> Used in the section title for the overall status of the cycle, and in sub-bullet points for parts still open or in progress.

---
```
