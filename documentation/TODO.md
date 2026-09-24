# TODO

What still needs finishing, in order. Moved here from the comment block at
the top of `Login_View.java`. Delete an item once it's done and renumber.

## 1. No way to change a role after signup

`UserManagement` only deletes accounts — there's no way to promote or
demote one. Add a role-guarded `changeRole(actor, target, newRole)` if
demotion/promotion is wanted (same OWNER-protection shape as `deleteUser`:
an ADMIN can't touch an OWNER's role).

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
