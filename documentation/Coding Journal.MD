# Coding Journal

Day-by-day log of building HelloApplication.

## 9/9

**Goal:** add action events to the login scene and learn how Scene Builder
wires them.

### To Implement
- Add action events to the login scene.
- Learn how Scene Builder wires controls to events.

## 9/10

Scene Builder helped show how a GUI is structured. The login button now
shows a message under the button, and events for the username and
password fields are in place.

### Things Learned
- Scene Builder generates a lot of styling that isn't needed, such as
  `prefHeight` set to infinity — stripped most of it out.
- Leaning toward native JavaFX instead of FXML after seeing how Scene
  Builder structures a GUI.

### To Implement
- Decide between BCrypt and Argon2id for password hashing.
- Block empty login clicks and prompt the user to enter a username or
  password.

## 9/11

Separated password checking from the controller. The controller still
handles the button and status label; a simple checker decides empty,
wrong, or OK (`EMPTY`, `WRONG`, `OK`).

### Things Learned
- A status enum and a role enum answer different questions: `EMPTY` /
  `WRONG` / `OK` is "did this attempt work?", while `ADMIN` / `USER` /
  `GUEST` would be "what can this account do next?". Mixing both in one
  enum gets messy once the next screen needs roles.

### To Implement
- Keep the three login messages for now; add a user-type enum
  (`ADMIN`/`USER`/`GUEST`) only after a login succeeds (`OK`).

## 9/12

Reworked the FXML login view into native JavaFX, and moved login
behavior into `LoginController` instead of handling everything in the
view. The view just builds the fields and buttons; the controller checks
input and returns a status (`EMPTY`, `WRONG`, or `OK`).

### Things Learned
- Scene Builder is faster for getting a layout on screen, but when
  something breaks it's harder to tell why.
- Building the UI in Java takes more setup, but errors show up on the
  line that caused them instead of the whole screen failing to load.

## 9/13

Chose SQLite over Access so the project can grow, and added the
`sqlite-jdbc` dependency in Maven.

### Things Learned
- Want the app to own its own forms/reports in JavaFX instead of
  depending on Access, with room to move to Postgres/MySQL later without
  changing how SQL is written.
- Maven benefit: put the right dependency in `pom.xml` and it fetches the
  driver — no hunting for the correct JAR or guessing which folder it
  belongs in.

### To Implement
- Build the `Database` helper that actually creates/queries the SQLite
  file.

## 9/15

Built the `Database` helper: create `data/users.db`, the `Users` table,
and seed `admin`. Wired login through `Authentication` → SQLite with a
prepared statement — `checkLogin` now returns `Roles` (status, username,
role) so the controller can tell `ADMIN` from `USER`. Updated
`LoginController` and `Login_View` to construct `Database`, call
`init()`, and pass `Authentication` in. Wrote unit tests for `Database`,
`Roles`, and `Authentication`.

### Bug Fixes
- Fixed `init()` so it uses the `Path` directly (not a parsed JDBC URL)
  and a single try-with-resources connection.

### Things Learned
- Lost time on toolchain, not test logic: tests have to live under
  `src/test/java`, JUnit 5 and JUnit 6 can't be mixed with IntelliJ
  2026's runner, and `module-info.java` must `requires`/`opens` for
  JUnit. Settled on JUnit 6.1.3 so the IDE Run button works.
- Password checks should look up the user by username, then verify the
  hash — never `WHERE Password = typed text`.

### To Implement
- Build `PasswordHasher`, hash the seed, and delete the old plaintext
  `users.db` so the seed can be recreated.

## 9/16

Went with Argon2id over bcrypt. `Authentication.checkLogin` no longer
does `WHERE Username = ? AND Password = ?` — it selects by username
only, then verifies the entered password against the stored hash in
Java. `Database.init()` now seeds `admin` with a hashed password and
migrates any existing row whose password isn't already Argon2id-encoded.

Built the create-account screen (`Create_View`, `CreateAccountController`,
`Registration` mirroring `Authentication`) following the same
View → Controller → domain → Database split as login. `Users` gained a
`Name` column (migrated in place via `PRAGMA table_info`), and the
entered email is stored directly as `Username`, so login-by-email works
with zero changes to `Authentication`. Accounts made through this form
are hardcoded to Role `USER`. Also added `PasswordPolicy` (8+ characters,
a digit, a symbol) as its own class, gating registration only — the
seeded admin/secret account still needs to log in.

Later the same day: refactored `Authentication` to `Authenticator`, and
replaced the single `isAdmin()` boolean with a `Role` enum
(`USER`/`ADMIN`/`OWNER`). Seeded a protected `owner` account alongside
`admin`; added `UserManagement.deleteUser` so an `ADMIN` can delete
other `ADMIN`/`USER` accounts but only an `OWNER` can delete an `OWNER`.
Connected the project to GitHub as a private repo, rewrote history into
one commit per journal date, and added a `README` covering the stack,
requirements, seeded accounts, and roles. Wired `handleLogin`'s returned
`Roles` to actually route to new `Admin_View`/`Home_View` placeholder
screens instead of being discarded. Extracted each view class's
one-method-does-everything `start()` into small named `buildX()`
methods. Replaced every `printStackTrace()` call with
`java.util.logging.Logger`.

### Bug Fixes
- `seedOwner`'s existence check queried `Username = "owner"` (lowercase)
  but the insert wrote `"Owner"` (capital). Since SQLite string
  comparison is case-sensitive, the check never found the row it just
  inserted, so every later `init()` call tried to insert `"Owner"` again
  and hit the `UNIQUE` constraint. Fixed by making both lowercase.
- `Login_View` called `controller.handleLogin()` purely for its side
  effect and threw away the returned `Roles`, so a successful login had
  no visible effect beyond the status label — the whole role hierarchy
  never actually gated anything. Added `Home_View` (USER) and
  `Admin_View` (ADMIN/OWNER) and routed to the right one.
- `mvn test` had never actually run a single test in this project.
  Three gaps: `junit-jupiter-api` was on the classpath but not
  `junit-jupiter-engine`, so there was no engine to discover tests with;
  the pom never pinned `maven-surefire-plugin`, so it fell back to
  Maven's ancient default (2.12.4), which predates JUnit 5; and once
  both were fixed, pinning a modern Surefire surfaced a JPMS
  module-path/classpath clash from having `module-info.java` in a
  project with test-only dependencies. Fixed by adding the engine
  dependency, pinning Surefire 3.2.5, and setting `useModulePath` to
  `false`. All 15 tests pass now, first time they've actually executed
  via Maven.
- The "Create an account" link left the old login window open underneath
  the new screen. Fixed by closing `primaryStage` before opening the
  next view, same as the success path already did.

### Things Learned
- Cost params for Argon2id (19 MiB memory, 2 iterations, 1 lane —
  OWASP's floor) are encoded right into the stored hash string
  (`$argon2id$v=19$m=...,t=...,p=...$salt$hash`), so they can be tuned
  later without breaking old hashes. BouncyCastle's pure-Java Argon2
  (`bcprov-jdk18on`) avoids a native binary to load on Windows.
- Deleted `data/users.db` by hand to force a reseed before checking
  whether the in-place migration already made that unnecessary (it did)
  and before checking what was actually in the file. Turned out to only
  be the seeded admin/secret row, so nothing was lost, but it was still
  the wrong move — should have trusted the migration or at least looked
  first.
- A single `isAdmin()` boolean doesn't scale to three tiers. Used a
  `Role` enum instead, with `Roles.isAdmin()` true for both `ADMIN` and
  `OWNER` and `isOwner()` true only for `OWNER`, so an owner counts as
  an admin but not the other way around.
- Rewriting already-pushed git history (to backdate commits per journal
  date) needs a force-push. Made a local backup branch first as a safety
  net before doing it.
- All four view classes shared the same "one giant `start()` method"
  shape. Splitting each into named `buildX()` methods per section made
  `start()` read as a table of contents instead of one block, and made
  the duplication between `Home_View`/`Admin_View` obvious.

### To Implement
- Revisit per-field icons on the create-account screen (left out for now
  since the original FXML used Gluon Glisten's `Icon` control, which
  this project doesn't depend on) — probably with Ikonli instead of
  pulling in a mobile UI toolkit for one glyph each.
- Wire the "Forgot Password?" link — still no handler.
- Build a real admin screen that calls `UserManagement.deleteUser`;
  `Admin_View`/`Home_View` are still placeholders (welcome label + log
  out button only).
- Change the seeded `admin`/`secret` and `owner`/`changeme` passwords
  before any real use.
- Explore license-file + machine-lock activation and bytecode
  obfuscation (ProGuard) to protect the software from unauthorized
  copying — discussed, not yet implemented.

## 9/17

Added forced password setup for the seeded accounts. `Users` gained a
`MustChangePassword` column; both `admin` and `owner` seed with it set,
so logging in with the default password routes to a new
`ChangePassword_View` (forced, no Cancel) before `Home_View`/
`Admin_View`. The same view doubles as an optional self-service screen
behind a new "Change password" button on `Home_View`/`Admin_View`.
Backing logic follows the established shape: `PasswordChange` (service)
and `ChangePasswordController` mirror `Registration`/`Authenticator`,
and both reuse `PasswordPolicy` so the strength rule stays in one place.

Built `PasswordVisibilityToggle` — a shared helper pairing a masked
`PasswordField` with a plain-text mirror field and a Show/Hide
`ToggleButton` — and wired it into `Login_View` (toggle sits next to
the Login button), `Create_View`, and both fields on
`ChangePassword_View`. One control, reused everywhere a password is
typed, instead of copy-pasting show/hide wiring per screen.

Split `Authenticator.Status.WRONG` into `WRONG` and `WRONG_PASSWORD`:
an unknown username still gets the generic "wrong username or password"
message, but a known username with the wrong password now says so
specifically.

### Things Learned
- The point of splitting wrong-username from wrong-password isn't
  friendlier errors — it's staying specific when it's safe to be while
  still never confirming whether a given username exists in the
  unknown-username case.

### To Implement
- Wire the "Forgot Password?" link — still no handler.
- Build a real admin screen that calls `UserManagement.deleteUser`;
  `Admin_View`/`Home_View` are still placeholders otherwise.
- Actually rotate the seeded `admin`/`secret` and `owner`/`changeme`
  passwords for a real deployment — the forced `ChangePassword_View`
  only helps if someone actually logs in as the seed and follows
  through.
- Revisit per-field icons on the create-account screen (still using
  plain labels, no Gluon/Ikonli yet).
- Explore license-file + machine-lock activation and bytecode
  obfuscation (ProGuard) — discussed, not yet implemented.

## 9/18

Relabeled the login field to "Email" and reworked the password row
(label + "Forgot Password?" link) to sit right above the field. That
surfaced a real layout bug: to keep the label row the same width as the
field, its width was bound to the field's live `widthProperty()`. That's
circular — the enclosing `VBox` needs the header's preferred width
*before* layout to size itself, but the header's bound width only
resolves *after* that layout pass runs. The binding never converged, so
instead of just being narrower, the header collapsed to nothing — the
"Forgot Password?" link and its spacer disappeared entirely, leaving
only the "Password" label (a `Label` doesn't shrink below its own
content, so it kept rendering; the link and spacer, with no honest width
to lay out into, didn't). Before the binding existed at all, the same
row had the opposite failure: nothing constrained its width, so it
rendered at full size and visibly overflowed past the narrower field.

Fixed by dropping the property binding entirely and using a fixed
`FIELD_WIDTH = 200` constant on both the field and the header — the
same plain-literal approach the rest of the form already used
successfully. Also shortened "Forgot Password?" to "Forgot?", since the
full phrase doesn't fit next to "Password" in 200px no matter how the
width is decided.

Reworked `ChangePassword_View` to use one shared show/hide toggle
instead of two separate per-field ones. Added a second entry point to
`PasswordVisibilityToggle` — `wrap(PasswordField, ToggleButton)` — so
several fields can be driven by one externally-owned toggle instead of
each getting its own. Placed that single toggle to the right of Submit,
so one click reveals (or hides) both "New password" and "Confirm
password" together, letting the user visually check they match instead
of comparing two separately-toggled fields.

Later the same day: fixed a real overflow bug on the status labels
themselves (`Login_View`, `Create_View`, `ChangePassword_View`) — none
had `setWrapText(true)`, so a longer message (e.g. the password-policy
text) ran past the fixed-width window instead of wrapping. Reworked
`Create_View` to match `Login_View`'s toggle placement (next to "Create
Account" instead of inline with the field) and added a Cancel button
there.

Explored, built, and then fully reverted a bigger idea: letting a weak
password on signup fall back to creating the account with a
system-generated temp password and forcing `ChangePassword_View`
instead of blocking creation outright. Added a `TempPasswordGenerator`
and a `PendingSetup` reclaim flag so an app killed mid-signup wouldn't
leave that email permanently unclaimed, all covered by tests (144
passing at that point). After actually trying it, jumping away from
`Create_View` before getting a chance to just retype a stronger
password there didn't feel right, so reverted the whole thing back to
the original "show the policy message, stay put" behavior (back to 36
tests, `TempPasswordGenerator` deleted).

Also chased down a false alarm: logging in as `owner`/`changeme` wasn't
rerouting to `ChangePassword_View`. Cause: the local `data/users.db` had
stale `admin`/`owner` rows with `MustChangePassword=0` predating that
column — `seedAdmin`/`seedOwner` only insert when a username is
missing, so an already-existing row is never repaired. Deleted the file
to force a clean reseed, and filed a TODO to actually fix the
underlying gap instead of relying on delete-and-reseed next time.

Asked for status messages to stay centered unless the text overflows.
First pass added `StatusLabelAlignment` and set `Label.textAlignment`
based on measured wrapped height — looked right by every code-level
signal (correct `CENTER`/`LEFT` value, `getWidth()` reporting the full
320px), but "didn't fix it either." Stopped reasoning from code and
built a throwaway `AlignmentPreview` (mirrors the mainClass trick from
the earlier header-bug investigation) to actually screenshot it via
`PrintWindow`. A border-only style made the box look tightly wrapped
around the text either way, which was itself misleading; switching to a
solid `-fx-background-color` fill made the real assigned width
unambiguous — and revealed the actual bug: `TextAlignment` only governs
how multiple lines justify *relative to each other*; it does nothing
for a single line's position within the label's box. That's a separate
property, `Labeled.alignment` (default `CENTER_LEFT`), which the first
pass never touched. Fixed by setting both together — `Pos.CENTER` +
`TextAlignment.CENTER` for one line, `Pos.CENTER_LEFT` +
`TextAlignment.LEFT` once it wraps — verified with an actual screenshot
this time, and deleted the preview class and reverted `pom.xml`
afterward.

### Bug Fixes
- The circular width-binding on `Login_View`'s password header
  described above — replaced with a fixed constant.
- Status labels across all three password screens didn't wrap text, so
  longer messages overflowed the fixed 400px window. Added
  `setWrapText(true)`, a 320px max width, and centered alignment to all
  three.
- `data/users.db` had stale `admin`/`owner` rows with
  `MustChangePassword=0` left over from before that column existed.
  `Database.init()`'s seed methods only ever insert when a username is
  missing, so they silently never repair an already-existing row — not
  a code bug so much as a real gap (see the new To Implement item below).
- `StatusLabelAlignment`'s first version set `TextAlignment` only, which
  has no effect on where a single line sits in the label's box — short
  messages stayed flush-left despite reporting `alignment=CENTER`. Fixed
  by also setting `Labeled.alignment` (`Pos.CENTER` / `Pos.CENTER_LEFT`)
  alongside it.

### Things Learned
- A screen-coordinate screenshot (`CopyFromScreen` at a window's
  `GetWindowRect` bounds) can be off due to DPI scaling and miss content
  entirely. `PrintWindow` grabs the window's actual rendered surface
  directly and doesn't have that problem — used it to catch the header
  bug in the first place, since a clean compile said nothing about the
  UI actually being broken.
- Binding a sibling's width to another node's `widthProperty()` inside
  an auto-sizing container is fragile: that property is a layout
  *output*, not a stable input, and using it as a binding source can
  create a resolution order that never settles. A plain shared constant
  sidesteps the whole problem.
- `System.Windows.Automation` can drive a running JavaFX window
  directly — invoke a `ToggleButton` via `TogglePattern`, set field text
  via `ValuePattern` — which made it possible to actually verify the
  shared toggle reveals both fields together, not just that the window
  opens without throwing.
- Built a whole feature (temp password on weak signup + a `PendingSetup`
  reclaim flag) before actually trying it and realizing the immediate
  jump away from `Create_View` didn't feel right. Reverted cleanly
  rather than leaving the now-unused generator/flag/reclaim logic
  sitting around unused.
- A flag like `MustChangePassword` isn't safe to reuse for "is this an
  abandoned signup nobody can ever claim" — a real account (seeded
  admin/owner) can need a password change without ever having been
  abandoned. Would need a second, distinct flag if this idea comes back.
- When something won't delete because a file is "in use," check what's
  actually holding it open before touching anything else — this time it
  was DataGrip's own JDBC connection to `data/users.db`, not a leftover
  process from a test run.
- `TextAlignment` (per-line justification within wrapped text) and
  `Labeled.alignment` (where the whole content sits in the control's
  box) are independent properties — for a message that's sometimes one
  line and sometimes wrapped, both need to change together, not just
  the one that sounds like "alignment."
- Code-level diagnostics can all look correct (right enum value, right
  `getWidth()`) while the actual rendered result is still wrong — a
  border-only style even *looked* consistent with the bug (tight box)
  for the wrong reason. A background-fill screenshot was what actually
  settled it, not reasoning about the properties from memory.

### Before Committing
Uncommitted as of now: `Coding Journal.MD`, `ChangePassword_View.java`,
`Create_View.java`, `Login_View.java`, `PasswordVisibilityToggle.java`,
`StatusLabelAlignment.java`.
- [x] Ran `mvn test` again after all of today's later changes too —
  36/36 passing.
- [x] Status-label centering specifically — actually screenshot-verified
  this time (see above), not just code-reviewed.
- [ ] Click through the rest of the app yourself: Login screen (password
  header no longer overflows/disappears), Create an account (Cancel
  button, toggle now beside "Create Account" instead of inline), and
  Change Password both forced (login as `admin`/`secret` or
  `owner`/`changeme`) and self-service (button on Home_View/Admin_View)
  — still only verified via screenshots/UI Automation and code review,
  not a manual run-through.
- [x] Confirm `pom.xml`'s `<mainClass>` reads `Login_View` — still correct
  (reverted again after the `AlignmentPreview` detour).
- [x] Confirm no trace of `ChangePasswordPreview.java` or
  `AlignmentPreview.java` remains — confirmed, neither file exists.
- [x] "Email" label on the login field: keeping it as-is for now — a
  cosmetic mismatch (`admin`/`owner` still log in with plain usernames),
  not worth blocking on.

### To Implement
- Wire the "Forgot Password?" link — still no handler (discussed:
  admin-initiated reset is the better fit than email tokens for a
  no-SMTP desktop app, since it'd reuse `PasswordChange`/
  `MustChangePassword` instead of adding a whole subsystem).
- Build a real admin screen that calls `UserManagement.deleteUser`;
  `Admin_View`/`Home_View` are still placeholders otherwise.
- Actually rotate the seeded `admin`/`secret` and `owner`/`changeme`
  passwords for a real deployment.
- Revisit per-field icons on the create-account screen (still using
  plain labels, no Gluon/Ikonli yet).
- Explore license-file + machine-lock activation and bytecode
  obfuscation (ProGuard) — discussed, not yet implemented.
- Add a real check so `Database.init()` can reconcile stale existing
  rows (e.g. seed accounts) against what a fresh install would produce,
  instead of only ever inserting/altering and never repairing.

## 9/22

Redesigned the login screen. Built three options side by side (a
"Record" data-grid form, a "Schema" split window with an ER diagram, and
a two-step "Connection" string), then mixed them into the one that
stuck. It's a split window: the blueprint side panel draws the app's own
`Users` table (new `SchemaDiagram` helper), and the form has an
`admin@users.db` headline that mirrors the username as it's typed. The
credentials are a key/value grid keyed by the real column names, and
there's a status bar along the bottom. While a field has focus, its
column lights up in the diagram. The comparison scaffolding (a design
interface, Ctrl+1–4 switcher, per-design stylesheets) is deleted; it's
all back in `Login_View` + `login.css`.

Split the styling so screens can share it: `theme.css` holds the palette
tokens, the side panel, buttons, links and the status bar. `login.css`
and the new `home.css` only add what's specific to each screen.

`Home_View` is no longer a placeholder: it's a read-only SQLite
browser. "Open database" picks any `.db`/`.sqlite`/`.sqlite3` file, the
side panel lists its tables, and the main area previews the selected
table's first 200 rows ("Showing 200 of 1,532 rows"), with NULLs muted
and BLOBs shown as `BLOB (n bytes)`. Change password and Log out moved
to the bottom of the side panel. The reads live in a new
`DatabaseBrowser` class (no UI, unit-tested) and run on a background
`Task`, so a slow `COUNT(*)` doesn't freeze the window.

### Things Learned
- Browsing the app's own `data/users.db` from Home would have shown
  every account and its password hash to any USER. Browsing a file the
  user picks avoids that entirely.
- sqlite-jdbc opens read-only with the `open_mode=1` connection
  property (SQLITE_OPEN_READONLY). That also means a mistyped path
  fails instead of silently creating an empty database.
- Table names can't be bound as `?` parameters. `DatabaseBrowser` only
  accepts a name that `sqlite_master` returned, then quotes it as an
  identifier (doubling any `"`).
- CSS `-fx-cell-size` didn't shrink the `TableView` rows;
  `setFixedCellSize()` in code did.

### Before Committing
- [x] `mvn test` passes: 47/47, including the new `DatabaseBrowserTest`
  (8) and `HomeViewTextTest` (3).
- [x] Screenshot-verified: login, create account, sign in as a USER,
  Home empty state, opening a fixture `shop.db` (table list, capped
  preview, NULL/BLOB cells, empty table), opening a `.txt` file (red
  status bar, previous database stays open), Change password → Cancel
  back to Home, Log out back to login.
- [ ] Created a test USER account (`demo@example.com`) in the local
  `data/users.db` during that check. Delete it if you don't want it.

### To Implement
- Style `Create_View`, `ChangePassword_View` and `Admin_View` with
  `theme.css`; they still use the JavaFX defaults.
- Returning to Home (e.g. Cancel on Change password) builds a fresh
  screen, so the open database is lost. Keep it, or remember the last
  opened file.
- Enter in the password field doesn't submit while "Show" is on (the
  plain-text mirror field has no action handler). This was already the
  case before today.

### Later on 9/22: sample database, PostgreSQL and MySQL
There was nothing to browse without a database of your own, so Home now
offers a generic sample. `SampleDatabase` builds a small shop at
`data/sample.db` the first time it's opened: 40 customers, 4 suppliers,
14 products, 1,200 orders and their order lines, with some NULLs. The
data comes from a fixed random seed, so every install gets the same file.
It's built in a `.partial` file and moved into place, so an interrupted
build never leaves a half-filled sample. `data/` is gitignored, so the
file isn't committed.

Home can also browse PostgreSQL and MySQL. "Open database" is now a menu
(Sample database, SQLite file, PostgreSQL server, MySQL server). The new
`ServerConnectDialog` asks for host, port, database, user and password.
It remembers everything except the password for the session, and never
saves anything. `DatabaseBrowser` now builds either a SQLite browser or a
server browser, and lists tables through JDBC `DatabaseMetaData`, so one
code path serves all three databases. PostgreSQL tables outside `public`
show as `schema.table`. Server sessions are switched to read-only with
`setReadOnly(true)`. Error messages are chosen from the SQLState class:
08 is "can't reach the server", 28 is "user or password rejected", and
3D000 or "Unknown database" is "database doesn't exist".

### Things Learned
- JavaFX CSS has no `:first-child`/`:last-child`, so the segmented
  PostgreSQL | MySQL toggle uses explicit style classes.
- `DriverManager`'s "No suitable driver" error uses SQLState 08001, the
  same code as a refused connection. It's checked first so a missing
  driver isn't blamed on the server.
- MySQL reports each database as a catalog, so `getTables` has to be
  limited to the connected catalog or it lists every database's tables.

### Before Committing
- [x] `mvn test` passes: 61/61. That includes a real connection attempt
  through the PostgreSQL driver (refused on port 1).
- [x] In the running app: sample database opens and previews, the
  connect dialog validates as you type, and both drivers load in the
  modular runtime. PostgreSQL on localhost:5432 was refused (no server
  here). MySQL was tried on port 3307 on purpose, so the local MySQL on
  3306 wasn't touched. Both showed the "check the server" message.
- [ ] Not yet tried: a successful connection to a real PostgreSQL or
  MySQL server. There's a MySQL server listening on localhost:3306; sign
  in to it from Home to check the table list and previews.

### To Implement
- Saved connections (without the password, or using the OS credential
  store) so a server doesn't have to be re-entered every session.
- Views are not listed yet, only tables.

### Later on 9/22: CSV drag and drop, and relationships
Drop CSV files anywhere on Home (or pick them from Open database → CSV
files) and they're imported into a new SQLite database at
`data/imports/import-<date>-<time>.db`, with the links between the files
worked out and saved as foreign keys.

- `CsvReader` handles quoted fields (commas, newlines, doubled quotes), a
  UTF-8 BOM, a Windows-1252 fallback, and `;` or tab delimiters (picked
  from the header line). It also pads or trims ragged rows, turns empty
  fields into NULL, and makes repeated or blank header names unique.
- `RelationshipFinder` treats a column as a key when every row has a
  value and no value repeats. A link from column C to key K needs values
  (90%+ of C's distinct values are in K) and names that agree
  (`customer_id` to `customers.customer_id`, or to `customers.id`,
  `product_sku` to `products.sku`). With both, it's STRONG (every value
  matched) or LIKELY. Values alone are only POSSIBLE, and only for
  non-integer keys, because small integer ids overlap by coincidence (a
  `quantity` of 1-5 "matches" any id column). Numbers are compared by
  value, so "007", "7.0" and "7" are the same. Also handles
  self-references (`manager_id`) and one-to-one tables that would
  otherwise be found in both directions.
- `CsvImporter` infers INTEGER/REAL/TEXT per column. Leading zeros keep
  a column TEXT, so zip codes survive. Only STRONG and LIKELY links are
  saved as foreign keys; POSSIBLE ones are shown but not saved. The file
  is built as `.partial` and then moved into place.
- `DatabaseBrowser` gained `listForeignKeys()` and `previewJoin()`, both
  using plain JDBC metadata. So the Relationships view also works for the
  sample database and for PostgreSQL/MySQL databases with foreign keys.
  The join is a LEFT JOIN with `table.column` headers, plus a count of
  child rows whose value has no parent row.
- Home's Relationships view: a diagram (`RelationshipDiagram`: owner
  tables on the left, and a sorting pass so lines don't cross), the list
  of links with their match rates, possible links that weren't saved, and
  tables not linked to anything. Clicking a curve or a link opens the
  join, with "Back to relationships" to return.

### Things Learned
- Automating a real file drop needs an OLE drag source: a small WinForms
  window calling `DoDragDrop` with a FileDrop, with the mouse driven from
  a second process, because `DoDragDrop` blocks its own thread.
- `Start-Process -WindowStyle Hidden` also hides the first window the
  child process shows, including a WinForms form. Use PowerShell's own
  `-WindowStyle Hidden` flag to hide only the console.
- In PowerShell, `$null` passed to a P/Invoke `string` parameter arrives
  as `""`, so `FindWindow($null, title)` finds nothing. Use
  `[NullString]::Value`.

### Before Committing
- [x] `mvn test` passes: 91/91 (new: `CsvReaderTest`,
  `RelationshipFinderTest`, `CsvImporterTest`, and more `HomeViewTextTest`).
- [x] In the running app: dragged 8 CSV files from a real OLE drag
  source onto the window. The overlay appeared, the import found 6 links
  and 1 possible one, the diagram drew without crossings, and clicking
  orders → customers showed the join, including one order whose customer
  doesn't exist.
- [ ] Every import makes a new file in `data/imports/`; nothing cleans
  them up yet.

### To Implement
- Remove or rename past imports from the app.
- Multi-column keys (e.g. `order_id` + `line_no`) aren't detected or
  drawn.
- Allow joining along a "possible" link on request, or saving it as a
  link after the user confirms it.

### Later on 9/22: Admin imports, Home queries and charts; servers removed
The app is now built around DataFest: an admin drops the event's CSV or
JSON files in, and users query and chart them.

- The old Home_View (the database browser, CSV drop, Relationships view)
  moved to `Admin_View`, which is now the Database Manager. It imports
  CSV, TSV, JSON and JSON Lines, and can "Share with users" (copy an
  opened SQLite file into `data/imports/`) or "Remove from datasets"
  (delete one, after a confirmation).
- `data/imports/` is the handoff. Every database there is a dataset
  (`Datasets`), and imports are named after their tables
  (`orders-payments.db`) instead of a timestamp. Older `import-<date>`
  files still show up, as "Import of Sep 22, 2026 18:39".
- New `Home_View` for USER accounts: dataset list, then the tables and
  columns of the open one; a SQL editor (Ctrl+Enter); a Rows tab (first
  500 rows); and a Chart tab (bar, line, pie or scatter, grouped by one
  column and measured as count, sum, average, min or max). `ChartMaker`
  wraps the user's query as a subquery so SQLite does the grouping over
  the whole result, not just the rows on screen.
- `JsonReader`: a small hand-written JSON parser (no new dependency).
  An array of objects is one table; `{"a": [...], "b": [...]}` is one
  table per array; nested objects are flattened to `parent_child`
  columns; nested arrays stay as JSON text. Numbers are kept exactly as
  written so the importer's type inference still keeps leading zeros.
- `CsvImporter` became `DataImporter`.
- PostgreSQL and MySQL are gone: the drivers in pom.xml,
  `ServerConnectDialog`, `DatabaseBrowser.server()`/`ServerKind`, the
  server error wording, and their tests. `DatabaseBrowser` is SQLite-only.
- Shared between the two screens: `ViewText` (wording), `BackgroundWork`
  (reads off the UI thread) and `ResultTable` (the grid).

### Things Learned
- A user's query can't be trusted to be one statement, so it's checked
  first (`DatabaseBrowser.checkQuery`: a single SELECT/WITH, with
  semicolons inside quotes allowed). The connection is SQLITE read-only
  anyway, so `WITH x AS (...) DELETE ...`, which gets past the
  first-word check, still fails.
- The chart SQL groups and orders by position (`GROUP BY 1 ORDER BY 2
  DESC`), not by alias. With an alias, a data column named `value` would
  be mistaken for the aggregate.
- A trailing `-- comment` would swallow the `)` the query is wrapped in,
  so the wrapper puts the `)` on its own line.
- A `ListView` cell with `setPrefWidth(0)` follows the list's width, so
  long names end in "…" instead of adding a horizontal scrollbar.
- A wrapped `Label` in a `VBox` is still cut off unless it has
  `setMinHeight(Region.USE_PREF_SIZE)`.

### Before Committing
- [x] `mvn test` passes: 125/125 (new: `JsonReaderTest`,
  `ChartMakerTest`, `DatasetsTest`, `ViewTextTest`, `AdminViewTextTest`,
  and query tests in `DatabaseBrowserTest`).
- [x] In the running app (a scratch harness that opens each screen
  without logging in, against a copy of `data/`): Admin imported a CSV
  plus a nested JSON file and found the link between them. Home listed
  the new dataset first, ran a join across the two tables, and drew the
  bar, pie and scatter charts. It refused `DELETE FROM orders` with a
  red status message.
- [ ] Not yet clicked through by hand: "Share with users" and "Remove
  from datasets" in Admin, and a real login as USER and ADMIN.

### To Implement
- Save queries and charts, so they can be reopened and shown at the event.
- Export a chart as PNG (needs `javafx-swing`, or writing the pixels
  directly), and export results as CSV.
- Rename datasets from Admin.

## 9/24

Removed the hardcoded seed passwords. `Database.init()` used to seed
`admin`/`secret` and `owner`/`changeme`, and the README published both, so
anyone who had read the repo could sign in to any install where nobody had
finished the forced password change yet. Now no accounts are seeded at all.
`Login_View` checks `OwnerSetup.isNeeded()`, and on a fresh `users.db` it opens
the new `OwnerSetup_View`, where the first person to launch the app chooses the
owner's username and password. It follows the usual shape: `OwnerSetup` (the
service), `OwnerSetupController`, and the view copied from `ChangePassword_View`.

For existing databases, seed version 2 deletes an `admin` or `owner` row that
still verifies against its old default password. Nobody has used a row like
that, because the forced change would have replaced the password. An account
whose password was changed is kept. If the only owner gets removed this way,
the setup screen appears once on the next launch.

Team model: one shared computer. The owner imports datasets in Admin, and
teammates sign up through "Create an account" as USER accounts to view them.

### Things Learned
- "Only create an owner if none exists" has to be a single statement
  (`INSERT ... SELECT ... WHERE NOT EXISTS`). A separate check followed by an
  insert leaves a gap where two submits could both pass the check.
- Tests that relied on the seeded rows now add them with a small
  `TestAccounts` helper, so the fixtures are visible in each test instead of
  depending on what `init()` happens to insert.

### Before Committing
- [x] `mvn test` passes: 175/175 (new: `OwnerSetupTest`, plus the version-2
  cases in `DatabaseTest`).
- [ ] Clicked through by hand: fresh `users.db` → setup screen → sign in as
  the owner; relaunch goes to login, not setup.

Also packaged the app for Windows. `package-windows.ps1` runs `javafx:jlink` and
then `jpackage`: a portable folder or zip with `HelloApplication.exe`, or a
setup `.exe` when WiX is installed. The new `AppPaths` class moves the data
folder to `%LOCALAPPDATA%\HelloApplication\data` when running as the packaged
app (detected by the `jpackage.app-path` property), and keeps `./data` for
`javafx:run`. That folder is inside the owner's Windows profile, so other
Windows logins can't delete `users.db`.

`module-info` no longer requires `org.junit.jupiter.api`, and
`junit-jupiter-api` is test scope again. The main code never used it, and it
would have been bundled into the runtime.

### Things Learned (packaging)
- jlink refuses signed modular jars, and `bcprov` is signed. The app calls
  BouncyCastle's Argon2 class directly, not through the JCE provider that
  needs the signature, so `<ignoreSigningInformation>` is safe here.
- jpackage's launcher sets `jpackage.app-path`, which gives a simple way to
  tell "installed" apart from "run from source".

### To Implement
- Install WiX and build the setup `.exe`. Only the portable zip has been
  built so far.
- App icon (`--icon` needs a `.ico`).
