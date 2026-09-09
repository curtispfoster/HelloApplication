## 9/9
Goal: add action events to the login scene and learn how Scene Builder wires them.

## 9/10
Scene Builder helped me see how a GUI is structured and where controls belong. It 
also generated a lot of styling I did not need, such as `prefHeight` set to
infinity. I stripped most of that out and started leaning toward native JavaFX 
instead of FXML.

On events: the login button now shows a message under the button. Events for the 
username field and password field are in place. Still deciding between BCrypt and
Argon2id for password hashing. Also need to block an empty login click and prompt 
the user to enter a username or password.

## 9/11
Separated password checking from the controller. The controller still handles the 
button and status label; a simple checker decides empty, wrong, or OK. That split 
is in place, but the check is still basic and needs to be hardened later.

The status label now covers three cases: empty fields, wrong email/username or 
password, or success. Those results use an enum (`EMPTY`, `WRONG`, `OK`). That 
works for messages, but I want to stop using enums for login *outcomes* and 
reserve them for *whom the user is* after a successful login: `ADMIN`, `USER`, 
or `GUEST`.

A status enum and a role enum answer different questions. `EMPTY` / `WRONG` / `OK`
is “did this attempt work?” `ADMIN` / `USER` / `GUEST` is “what can this account 
do next?” Mixing both in one enum gets messy when the next screen needs roles. 
Keep the three login messages for now; add a user-type enum only after `OK`.

## 9/12
Reworked the FXML login view into native JavaFX. Scene Builder is faster for 
getting a layout on screen, but when something breaks it is harder to tell why. 
Building the UI in Java takes more setup, but errors show up on the line that 
caused them instead of the whole screen failing to load.

Also moved login behavior into `LoginController` instead of handling everything 
in the view. The view builds the fields and buttons; the controller checks input 
and returns a status (`EMPTY`, `WRONG`, or `OK`). The UI can show “email/username 
or password is empty” or “wrong email/username or password” without mixing layout 
and login logic in the same class.

## 9/13

Chose SQLite over Access so the project can grow. I want the app to own forms and 
reports in JavaFX instead of depending on Access, and if it ever needs a real
multi-user server I can move to Postgres or MySQL without changing how I write SQL.
Added the sqlite-jdbc dependency in Maven. That showed a real benefit of Maven: put 
the right dependency in pom.xml, and it fetches the driver. No hunting for the 
correct JAR or guessing which folder it belongs in.

## 9/15

Built the Database helper: create data/users.db, Users table, and seed admin. Fixed 
init() so it uses the Path (not a parsed JDBC URL) and one try-with-resources connection.
Wired login through Authentication → SQLite with a prepared statement. checkLogin now 
returns Roles (status, username, role) so the controller can tell ADMIN from USER. 
Updated LoginController and Login_View to construct Database, call init(), and pass 
Authentication in. Wrote unit tests for Database, Roles, and Authentication. 
Lost time on toolchain, not test logic: tests have to live under src/test/java, 
JUnit 5 and JUnit 6 can’t be mixed with IntelliJ 2026’s runner, and module-info.java 
must requires / opens for JUnit. Settled on JUnit 6.1.3 so the IDE Run button works.
Started password hashing: look up the user by username, then verify bcrypt 
(don’t WHERE Password = typed text). Next is PasswordHasher, hashed seed, and deleting 
the old plain-text users.db so the seed can be recreated.

## 9/16

Went with Argon2id over bcrypt. Added `Argon2PasswordHasher` using Bouncy Castle's 
pure-Java Argon2 (`bcprov-jdk18on`) instead of a JNI-backed library, so there's no 
native binary to load on Windows. Cost params (19 MiB memory, 2 iterations, 1 lane — 
OWASP's floor for Argon2id) are encoded right into the stored hash string 
(`$argon2id$v=19$m=...,t=...,p=...$salt$hash`), so they can be tuned later without 
breaking old hashes. Swapped the unused jbcrypt dependency for bcprov-jdk18on and 
added `requires org.bouncycastle.provider;` to module-info.

`Authentication.checkLogin` no longer does `WHERE Username = ? AND Password = ?`. 
It selects by username only, then verifies the entered password against the stored 
hash in Java. `Database.init()` now seeds `admin` with a hashed password instead of 
plaintext, and also migrates any existing row whose password isn't already 
Argon2id-encoded by hashing it in place — so an old dev database upgrades itself on 
next launch instead of needing to be deleted.

Found that out the hard way: I deleted `data/users.db` by hand to force a reseed, 
before checking whether the in-place migration I'd just written made that 
unnecessary (it did) and before checking what was actually in the file. Turned out 
to only be the seeded admin/secret row, so nothing was lost, but it was still the 
wrong move — should have trusted the migration or at least looked first.

Also discovered `mvn test` had never actually run a single test in this project. 
Three separate gaps: `junit-jupiter-api` was on the classpath but not 
`junit-jupiter-engine`, so there was no engine to discover tests with; the pom never 
pinned `maven-surefire-plugin`, so it fell back to Maven's ancient default (2.12.4), 
which predates JUnit 5; and once both of those were fixed, pinning a modern Surefire 
version surfaced a JPMS module-path/classpath clash from having `module-info.java` 
in a project with test-only dependencies. Fixed by adding the engine dependency, 
pinning Surefire 3.2.5, and setting `useModulePath` to `false` so tests run on the 
classpath. All 15 existing tests pass now, first time they've actually executed via 
Maven.

Built the create-account screen. `Create_View` is native JavaFX (no FXML) laid out to 
match `Documents/FXML/Create-View.fxml` — welcome header, then Name/Email/Password 
rows, then the Create Account button. Left the per-field icons out for now (the FXML 
used Gluon Glisten's `Icon` control, which this project doesn't depend on) — 
revisiting that later, probably with Ikonli instead of pulling in a mobile UI toolkit 
for one glyph each.

Followed the same View → Controller → domain → Database split as login: added 
`Registration` (mirrors `Authentication`) and `CreateAccountController` (mirrors 
`LoginController`). The form has Name/Email/Password but Users only had 
Username/Password/Role, so `Database` gained a `Name` column (migrated in place via 
`PRAGMA table_info`, same pattern as the plaintext-password migration), and the 
entered email is stored directly as `Username` — matches the login screen's 
"Username or Email" label, and means login-by-email works with zero changes to 
`Authentication`. Accounts made through this form are hardcoded to Role `USER`; this 
flow doesn't create admins.

Wired navigation both directions: the login screen's new "Create an account" link 
closes the login stage and opens `Create_View`; a successful account creation shows 
"Account created." for a second, then closes and reopens `Login_View`. First pass 
left the old login window open underneath when you followed the link — fixed by 
closing `primaryStage` before opening the next view, same as the success path already 
did.

Added a password policy for new accounts: 8+ characters, at least one digit, at 
least one symbol. Lives in its own `PasswordPolicy` class rather than inside 
`Registration`, since a password-change screen will want the same rule later. 
Deliberately only gates registration, not login — the seeded admin/secret account 
still needs to work, and login shouldn't be the place that starts rejecting existing 
passwords.