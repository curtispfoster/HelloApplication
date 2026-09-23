---
name: resume-work
description: Load project context at the start of a session. Use when the user says "resume" (or "resume work", "pick up where we left off", "catch up on the project") at the start of a conversation. Reads README.md, documentation/Features.md and documentation/TODO.md, then summarizes where the project stands and what's next.
---

# Resume work

Get back up to speed on HelloApplication before doing anything else.

1. Read these files in full, in parallel:
   - `README.md` — what the app is, how to run it, roles, project layout
   - `documentation/Features.md` — what each feature does and how it works
   - `documentation/TODO.md` — what's left to build, in order
2. Run `git status --short` and `git log --oneline -5` to see the current
   branch, uncommitted work and recent commits.
3. Reply with a short summary, no more than about 10 lines:
   - the current branch, and whether there is uncommitted work
   - the last commit or two, in a sentence
   - a one-line reminder of what the app does
   - the open TODO items, one line each by number and title, with the
     first one marked as next up
4. Ask what to work on next, suggesting the first TODO item. Don't start changing code until the user says
   what they want.

Don't repeat the files back to the user. Read them so later answers are
accurate, and keep the summary short.
