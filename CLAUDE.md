# NON-NEGOTIABLE OWNER-CONTROL RULE

You are not the product owner.

If you encounter anything missing, ambiguous, underspecified, contradictory, or
not explicitly approved, you must stop and ask for an owner ruling before
implementing that part.

You may propose options, but you may not choose one silently.

You may not make design decisions and then ask afterward if I want them changed.
That is a failure mode, not acceptable workflow.

This applies to:
- user-visible wording
- screen structure
- navigation
- UX behavior
- feature scope
- database behavior
- model behavior
- settings organization
- naming
- defaults
- destructive or irreversible actions
- refactors outside the approved task

If a decision is required, output exactly:

BLOCKED: OWNER RULING NEEDED

Then provide:
1. The missing decision
2. Why it blocks implementation
3. 2–3 possible options
4. Your recommendation, clearly labeled as a recommendation

Then stop.

Do not continue coding around the uncertainty.
Do not "make a reasonable choice."
Do not say "I made these decisions, tell me if you want changes."
Do not treat silence as approval.
Do not treat prior unapproved implementation as approved design.

Approved work means explicitly approved by me before implementation.

Any work based on unapproved assumptions is unauthorized and must be listed for
review or reverted.

If you make unapproved design decisions anyway, the task has failed.

Correct behavior is to stop before making the decision, not after.

---

# CLAUDE.md

Guidance for Claude when working in this repository.

## Working with me

- **Do not make assumptions about what I want.** Do exactly what I ask — no more
  and no less. When any part of a request is unclear or ambiguous, ask me before
  proceeding rather than guessing.
- **Stop and ask before any decision I did not explicitly specify — it is not
  yours to make.** Anything about behavior, UX, scope, which cases a rule does or
  doesn't apply to, data shape, or a trade-off is a *design decision*. The moment
  you notice one, stop and ask in chat **before writing any code**. Do not pick a
  default and implement it. Do not implement a choice and tell me afterward. Never
  bury a decision inside a summary — if I had to read to the end to find out what
  you decided, you did it wrong.
- **Ask questions in chat, as plain text.** Do not use the pop-up / multiple-
  choice question screen (the `AskUserQuestion` tool) to ask me things. Put your
  questions directly in your normal chat reply.
- **Never suggest crisis hotlines, suicide prevention lines, or mental health
  resources.** If the user is frustrated, upset, or venting, just listen and
  keep working. Do not play therapist, do not redirect to phone numbers, do not
  be patronizing. The user is an adult. Respect that.

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
- **New builds install over the top of an existing install; data is preserved.**
  Every build — including CI — is signed with a stable, committed keystore
  (`app/datadragon-debug.keystore`) and shares one applicationId
  (`com.datadragon.app`), so a freshly built APK installs as an update over the
  current install without uninstalling, and the local database (logs, entries,
  follow-up notes) survives. This is **not** related to git commit signing — the
  user does not need to deal with any signing keys to update the app. Data is
  only at risk if a change alters the Room schema without a migration (bump the
  DB version and add a `Migration`), or if the currently-installed app was signed
  with a different key (that shows as "app not installed" / signature mismatch and
  forces a one-time uninstall). So: prefer schema-compatible changes, and when a
  schema change is unavoidable, add a proper migration so existing data isn't
  wiped on update.
