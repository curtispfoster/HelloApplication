# TODO

What still needs finishing, in order. Moved here from the comment block at
the top of `Login_View.java`. Delete an item once it's done and renumber.

## 1. `UserManagement.resetPassword(actor, target)`

Set a temporary password, flip `MustChangePassword = 1`, and let the
existing forced-change flow in `ChangePassword_View` handle the rest.
Guard it by role the same way as `deleteUser` (an ADMIN can't reset an
OWNER). Show the temp password once in the Users panel in `Admin_View`.

This also closes (2): "Forgot password?" becomes "ask an admin" instead of
needing SMTP, which suits a desktop app.

Checked the Coding Journal: the temp-password code that was built and
reverted was for *signup* — falling back to a generated password when the
chosen one was weak. It was reverted because it jumped the user away from
`Create_View` before they could just retype a stronger password. That
reason doesn't apply to an admin-initiated reset, so the shape is fine to
reuse here.

## 2. Wire the "Forgot password?" link in `Login_View`

The link in `buildActionsRow` has no handler. Once (1) exists, have it show
"Ask an admin to reset your password" in the status bar.

## 3. Stale seed accounts and migrations in `Database.init()`

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

## 4. Saved views that users open with one click

Let an admin set up a view with Home's point-and-click controls (table, linked
columns, filters, sort, chart) and "Save for users" under a name like
"Orders by city". Users see saved views in a list on Home and open one with a
single click. Store the view as its `QueryBuilder.Request` plus the chart
choices, not as SQL, so it still works if the builder changes.

## Not a todo

Don't merge `Home_View` and `Admin_View`. `Admin_View` is the Database
Manager (imports CSV/JSON into datasets) plus the Users panel, `Home_View`
is where users query and chart those datasets.
