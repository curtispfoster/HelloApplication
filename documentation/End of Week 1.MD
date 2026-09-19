# Week of 9/9–9/18 — summary

Started the week wiring login events in Scene Builder and ended it with a
native JavaFX app: SQLite, Argon2id, roles, registration, forced password
change, and 36 tests running through Maven.

## Shipped

- Dropped FXML for hand-built views (`Login_View`, `Create_View`,
  `ChangePassword_View`, placeholder Home/Admin).
- Split UI from domain: View → Controller → `Authenticator` /
  `Registration` / `PasswordChange` → `Database`.
- SQLite (`users.db`) instead of Access; Maven pulls the driver.
- Login returns `Roles` (`USER` / `ADMIN` / `OWNER`) and actually opens
  the matching screen.
- Argon2id hashes; seed plus in-place migrate of old plaintext rows.
- Create-account with `PasswordPolicy`; seeded admin/owner must change
  password on first login.
- Shared `PasswordVisibilityToggle` and wrapping/centering status labels.
- Private GitHub repo. Tests: none via Maven at the start of the week,
  36 passing at the end.

## Hard-won

- Tooling burned more time than SQL: `src/test/java`, JUnit 5 vs 6 vs
  IntelliJ 2026, `module-info` plus Surefire `useModulePath=false`.
- Layout: don't bind a sibling to `widthProperty()` inside an auto-sizing
  `VBox`; use a shared width constant. `TextAlignment` is not the same as
  `Labeled.alignment`.
- Seeds that only `INSERT` never repair old rows
  (`MustChangePassword=0`). Don't delete `users.db` until you've looked.
- Built a temp-password signup path, tried it, reverted it. Better than
  leaving dead code.

## Still open

- Forgot-password handler (admin reset fits better than SMTP for this
  desktop app).
- Real admin table plus `UserManagement.deleteUser`.
- Repair stale seed rows in `init()` instead of delete-and-reseed.
- Icons, license lock, rotate default `secret` / `changeme`.

## Next

First: admin user list plus reset/delete. Then wire Forgot? on the login
row.