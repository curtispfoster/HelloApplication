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
`data/users.db` and seeds two accounts:

| Username | Password  | Role  |
|----------|-----------|-------|
| `admin`  | `secret`  | ADMIN |
| `owner`  | `changeme`| OWNER |

Change both passwords before any real use — these are fixed seed values
for local development.

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

- `Login_View` / `LoginController` / `Authenticator` — login flow
- `Create_View` / `CreateAccountController` / `Registration` — account creation
- `Database` — SQLite schema, migrations, and account seeding
- `Roles` / `Role` — login result and role hierarchy (`USER` < `ADMIN` < `OWNER`)
- `UserManagement` — role-guarded account deletion
- `Argon2PasswordHasher` / `PasswordPolicy` — password hashing and strength rules

See `documentation/Coding Journal.MD` for the day-by-day build log.
