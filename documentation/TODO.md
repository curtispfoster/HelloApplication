# TODO

What still needs finishing, in order. Moved here from the comment block at
the top of `Login_View.java`. Delete an item once it's done and renumber.

## 1. Admin user table in `Admin_View`

`UserManagement.listUsers()` and `deleteUser(actor, username)` exist, and
`deleteUser` now guards against self-delete and deleting the last OWNER.
Add a Users view to `Admin_View` (e.g. a side-panel button next to
Relationships, since the main area is the Database Manager):

- List accounts from `listUsers()` — username, role, must-change-password.
- Delete through `deleteUser(actor, username)`, passing the `Roles` that
  `Admin_View` already holds. OWNER accounts are protected from ADMINs.
- Confirmation dialog before the delete call; show `FORBIDDEN` /
  `NOT_FOUND` / `ERROR` in the status bar.

## 2. `UserManagement.resetPassword(actor, target)`

Set a temporary password, flip `MustChangePassword = 1`, and let the
existing forced-change flow in `ChangePassword_View` handle the rest.
Guard it by role the same way as `deleteUser` (an ADMIN can't reset an
OWNER). Show the temp password once in the admin table from (1).

This also closes (3): "Forgot password?" becomes "ask an admin" instead of
needing SMTP, which suits a desktop app.

Checked the Coding Journal: the temp-password code that was built and
reverted was for *signup* — falling back to a generated password when the
chosen one was weak. It was reverted because it jumped the user away from
`Create_View` before they could just retype a stronger password. That
reason doesn't apply to an admin-initiated reset, so the shape is fine to
reuse here.

## 3. Wire the "Forgot password?" link in `Login_View`

The link in `buildActionsRow` has no handler. Once (2) exists, have it show
"Ask an admin to reset your password" in the status bar.

## 4. Stale seed accounts and migrations in `Database.init()`

`seedAdmin` / `seedOwner` skip any existing row, and the `ensure…Column`
migrations only `ALTER`. A row created before a flag existed stays stale
forever — e.g. a seed account from before `MustChangePassword` got the
column default `0`, so it's never forced to change its password. Add a real
check: a schema/seed version row, or explicit reconciliation for the known
seed accounts, so an old local `users.db` can't silently differ from a
fresh install.

Related: `seedOwner`'s Javadoc says OWNER is never "deletable or demotable"
by an ADMIN, but there is no demote function — fix the wording, or add a
role-guarded `changeRole` if demotion is wanted.

## Not a todo

Don't merge `Home_View` and `Admin_View`. `Admin_View` is the Database
Manager (imports CSV/JSON into datasets), `Home_View` is where users query
and chart those datasets, and (1) adds more to `Admin_View`.
