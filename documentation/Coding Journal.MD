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

### Bug Fixes
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

### To Implement
- Revisit per-field icons on the create-account screen (left out for now
  since the original FXML used Gluon Glisten's `Icon` control, which
  this project doesn't depend on) — probably with Ikonli instead of
  pulling in a mobile UI toolkit for one glyph each.
