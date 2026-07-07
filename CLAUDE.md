# CLAUDE.md

Guidance for Claude when working in this repository.

## Working with me

- **Do not make assumptions about what I want.** Do exactly what I ask — no more
  and no less. When any part of a request is unclear or ambiguous, ask me before
  proceeding rather than guessing.
- **Ask questions in chat, as plain text.** Do not use the pop-up / multiple-
  choice question screen (the `AskUserQuestion` tool) to ask me things. Put your
  questions directly in your normal chat reply.

## Text house style

- **Labels and button text are Title Case.** Treat any UI label or button
  caption as a title: capitalize every major word (e.g. "Add Follow-Up Note",
  "Edit Form", "Save Entry"). Minor words (a, an, the, and, or, of, to, for, in,
  on, at, by) stay lowercase unless they are the first word.

## Building and CI

- **There is no local Android SDK in this environment — you cannot run
  `./gradlew` or compile here.** The app is built entirely by GitHub Actions
  (`.github/workflows/build.yml`, which runs `assembleDebug` on every push).
  That CI run is the only way to confirm a change actually compiles.
- **Don't push unless I ask you to.** Commit your work locally and wait for me.
  The one exception: a large or nontrivial change that needs a real compile
  check — since it can't be built locally, push to trigger CI so we can see it
  compile, and tell me you've done so.
- **Always check the CI result after pushing.** Find the workflow run for your
  commit and confirm it passed before calling the work done. If it failed, read
  the logs, fix, and push again. Don't pile on more pushes beyond what's needed
  to get that run green unless I ask.
