# TODO

What still needs finishing, in order. Moved here from the comment block at
the top of `Login_View.java`. Delete an item once it's done and renumber.

## 1. Stale seed accounts and migrations in `Database.init()`

`seedAdmin` / `seedOwner` skip any existing row, and the `ensure…Column`
migrations only `ALTER`. A row created before a flag existed stays stale
forever — e.g. a seed account from before `MustChangePassword` got the
column default `0`, so it's never forced to change its password. Add a real
check: a schema/seed version row, or explicit reconciliation for the known
seed accounts, so an old local `users.db` can't silently differ from a
fresh install.

Related: there's still no way to change a role after signup —
`UserManagement` only deletes accounts. Add a role-guarded `changeRole`
if demotion/promotion is wanted.

## 2. Saved views that users open with one click

Let an admin set up a view with Home's point-and-click controls (table, linked
columns, filters, sort, chart) and "Save for users" under a name like
"Orders by city". Users see saved views in a list on Home and open one with a
single click. Store the view as its `QueryBuilder.Request` plus the chart
choices, not as SQL, so it still works if the builder changes.

## Not a todo

Don't merge `Home_View` and `Admin_View`. `Admin_View` is the Database
Manager (imports CSV/JSON into datasets) plus the Users panel, `Home_View`
is where users query and chart those datasets.
