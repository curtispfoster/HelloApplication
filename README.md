# HelloApplication

A native JavaFX desktop app with SQLite-backed login/registration and a
USER / ADMIN / OWNER role hierarchy.

## Stack

- Java 21, JavaFX 21 (no FXML — views are built in code)
- SQLite (`sqlite-jdbc`) for persistence
- Argon2id password hashing via BouncyCastle (`bcprov-jdk18on`)
- JUnit 5 for tests

## Requirements

- **JDK 21 or newer** on your PATH (`maven-compiler-plugin` targets release 21).
- **No separate Maven install needed** — `mvnw` / `mvnw.cmd` download the
  correct Maven version automatically.
- **No separate JavaFX SDK download needed** — `javafx-controls` and the
  `javafx-maven-plugin` pull the JavaFX runtime in as regular Maven
  dependencies.
- **Internet access on first build**, to fetch dependencies from Maven
  Central: `javafx-controls`, `sqlite-jdbc`, `bcprov-jdk18on`
  (BouncyCastle, pure Java — no native crypto library to install), and
  `junit-jupiter` (test scope only).
- Windows, macOS, or Linux — `sqlite-jdbc` bundles the correct native
  SQLite binary for your platform automatically.

## Running

```
./mvnw clean javafx:run
```

Launches `MainApp` (via `Launcher`), which opens `Login_View`. On first run, `Database.init()` creates
`data/users.db` and seeds two accounts, both flagged to require a new password on first login:

| Username | Password  | Role  |
|----------|-----------|-------|
| `admin`  | `secret`  | ADMIN |
| `owner`  | `changeme`| OWNER |

Logging in with either routes straight to `ChangePassword_View` (forced —
no Cancel) before anywhere else. After that, login opens `Admin_View`
(ADMIN/OWNER) or `Home_View` (USER) — both are placeholders for now, and
both have their own optional "Change password" button.

Even with the forced first-login change, treat these as fixed seed
values and rotate them again before any real deployment.

## Roles

- `USER` — created via the "Create an account" flow on the login screen.
- `ADMIN` — can manage `USER`/`ADMIN` accounts.
- `OWNER` — top-tier account. `UserManagement` blocks an `ADMIN` from
  deleting or demoting an `OWNER` account; only another `OWNER` can.

## Testing

```
./mvnw test
```

## Project layout

- `Launcher` / `MainApp` — the one JavaFX entry point (`Application.launch`). Every screen below is a
  plain class with a `show(Stage)` method that reuses the same `Stage` instead of opening its own.
- `Login_View` / `LoginController` / `Authenticator` — login flow
- `Create_View` / `CreateAccountController` / `Registration` — account creation
- `ChangePassword_View` / `ChangePasswordController` / `PasswordChange` — forced (seeded accounts'
  first login) and self-service password changes
- `Home_View` / `Admin_View` — post-login landing screens, routed by role (placeholders for now)
- `Database` — SQLite schema, migrations, and account seeding
- `Roles` / `Role` — login result and role hierarchy (`USER` < `ADMIN` < `OWNER`)
- `UserManagement` — role-guarded account deletion
- `Argon2PasswordHasher` / `PasswordPolicy` — password hashing and strength rules
- `PasswordVisibilityToggle` / `StatusLabelAlignment` — shared UI helpers: a Show/Hide toggle for
  password fields, and centered-unless-wrapped alignment for status messages

See `documentation/Coding Journal.MD` for the day-by-day build log.
