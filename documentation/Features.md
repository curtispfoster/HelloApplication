# Features

What each part of HelloApplication does and how it works. The Javadoc comments
used to hold this; they've been moved here so the explanations live in one
place. For setup and running, see the [README](../README.md); for what's left
to build, see [TODO.md](TODO.md).

Every screen is a plain class with a `show(Stage)` method. `Launcher` calls
`MainApp`, the app's only JavaFX entry point (`Application.launch` runs once),
and every screen reuses that one `Stage` — moving between screens just swaps
the `Scene`. No FXML: every view is built in code.

---

## 1. Accounts and sign-in

### Login screen — `Login_View`, `LoginController`, `Authenticator`

- A split window. The left "blueprint" side panel draws the app's own `Users`
  table (`SchemaDiagram`); the form sits on the right.
- The headline reads like a connection string, `username@users.db`. The user
  part mirrors the username field as it's typed, shows a grey `username`
  placeholder while the field is empty, and has a caret after it while the
  field has focus. "Sign in to connect" sits underneath.
- The username and password fields are a key/value grid labelled with the
  real column names. The matching row of the diagram lights up while each
  field has focus.
- **Connect** (button click or Enter in either field) runs one login
  attempt. `LoginController` reads the fields, calls
  `Authenticator.checkLogin`, writes the message into the status bar and
  colours it green (granted) or red (denied).
- `Authenticator` checks blanks first, then looks up the account and
  verifies the password against its stored Argon2id hash. Results:
  - `EMPTY` — username and/or password missing.
  - `WRONG` — no account with that username/email. The message is kept
    generic, so it never confirms whether a username exists.
  - `WRONG_PASSWORD` — the account exists but the password didn't match.
  - `ERROR` — the database couldn't be read.
  - `OK` — signed in.
- On success the window routes by role (see §1.4). On anything else it stays
  put; the status bar already says why.
- "New here? Create an account" switches to `Create_View`.
- "Forgot password?" is shown but not wired up yet (see TODO.md).

### Create an account — `Create_View`, `CreateAccountController`, `Registration`

- A centred form: welcome header, name, email and password, then
  **Create Account**, **Cancel** and a Show/Hide toggle for the password.
- Create runs on click or Enter in any field. `CreateAccountController`
  reads the fields, calls `Registration.createAccount`, and shows the result.
- `Registration` stores the email as the `Username` (the login screen accepts
  "Username or Email") and always creates role `USER` — this flow never makes
  admins. New passwords must pass `PasswordPolicy`.
- On success the message shows briefly, then the window returns to the login
  screen. Cancel goes straight back without creating anything.

### Change password — `ChangePassword_View`, `ChangePasswordController`, `PasswordChange`

- Styled to match the login screen: the side panel draws the `Users` table
  with the `Password` column lit while either field has focus, and the
  headline shows the signed-in `username@users.db`.
- Two fields, new password and confirm, sharing one Show/Hide toggle so both
  can be checked at once. The `PasswordPolicy` rules are shown up front, not
  only after a rejected attempt.
- `ChangePasswordController` checks the two fields match, then
  `PasswordChange` saves the new hash and clears the account's
  `MustChangePassword` flag.
- Used two ways:
  - **Forced** — straight after logging in to an account that still has its
    seeded default password. No Cancel; it says the change has to happen
    before going on.
  - **Self-service** — from the "Change password" link on Home or Admin.
    Cancel returns without changing anything.
- On success the window moves on to the account's landing screen.

### Where you land — `Roles`, `Role`, `Views.openLandingView`

- `Role` is the hierarchy `USER` < `ADMIN` < `OWNER`.
- `Roles` is the result of a login: status, username, role, and whether the
  password must be changed. It's also the signed-in account passed into every
  screen after login, so each one knows who's using it without re-reading the
  database. `isAdmin()` is true for ADMIN and OWNER; `isOwner()` only for
  OWNER.
- After login (and after any forced password change): ADMIN/OWNER →
  `Admin_View`, everyone else → `Home_View`.
- Both landing screens end their side panel with an account block
  (`Views.accountBlock`): the username, "Admin" or "Owner" beside it for
  admins, then **Change password** and **Log out** links.

### Passwords — `Argon2PasswordHasher`, `PasswordPolicy`

- Passwords are hashed with Argon2id using BouncyCastle's pure-Java
  implementation — no native library to load, which keeps Windows simple.
- The stored form carries its own cost settings:
  `$argon2id$v=19$m=<memoryKB>,t=<iterations>,p=<parallelism>$<salt>$<hash>`,
  so the costs can be raised later without breaking existing hashes.
- `PasswordPolicy`: at least 8 characters, with at least one digit and one
  symbol (anything that isn't a letter or digit).

### Account storage — `Database`

- `data/users.db`. `Database.users()` creates and updates it the first time a
  screen asks, then shares it, so moving between screens doesn't re-run the
  setup. A failed setup isn't remembered; the next screen tries again.
- On setup it:
  - creates the `Users` table if missing;
  - adds the `Name` and `MustChangePassword` columns to tables made before
    they existed (`CREATE TABLE IF NOT EXISTS` won't change an existing
    table);
  - seeds `admin` / `secret` (ADMIN) and `owner` / `changeme` (OWNER), both
    flagged to change their password on first login. `owner` is the root
    account the software owner uses to manage admins;
  - hashes any leftover plaintext passwords from before Argon2id, leaving
    already-hashed ones alone.

### User management — `UserManagement`

- `listUsers()` — every account, ordered by username, as `UserSummary`
  (username, role, must-change-password). An empty list on a database error,
  which is logged.
- `deleteUser(actor, username)` — only admins may delete; an ADMIN can delete
  USER and ADMIN accounts, but only an OWNER can delete an OWNER. Returns
  `OK`, `FORBIDDEN`, `NOT_FOUND` or `ERROR`.
- No screen uses these yet (see TODO.md).

---

## 2. Database Manager (admins) — `Admin_View`

Where data comes into the program. The side panel holds **Open database**,
what's open, **Relationships** (when there are links), the table list, and
the account block. The main area shows one of: the start screen, a table's
rows, the relationship view, or two tables joined along one link.

- **Start screen.** Nothing is open yet, so importing comes first.
- **Import list.** Dropping CSV, TSV, JSON or JSON Lines files anywhere on
  the window, or picking them with Open database → Import, adds them to a
  list instead of importing straight away, so a dataset can be put together
  a few files at a time. The list shows each file's size and the table it
  becomes, with **Remove** on each file and **Add files…**, **Clear** and
  **Import N files** below. The same file isn't added twice ("orders.csv is
  already in the list."). While the list has files, **Files to import (N)**
  in the side panel brings it back. The list is locked while an import runs;
  it's emptied when the import succeeds and kept when it fails, so a bad file
  can be removed and the rest imported.
- **Import files.** Import turns everything in the list into one new dataset
  (§3), which then opens on the relationship view. While a big file imports,
  the status bar shows how far it's got ("Reading orders.csv (1.3 GB): 38%,
  3,020,000 rows so far…", then "Looking for links…" and "Saving orders…").
  The import runs on its own, so tables can still be browsed meanwhile;
  starting another import cancels it. When it's done, the status bar sums it up,
  e.g. "Imported 3 tables into customers-orders-products.db, now a dataset
  users can see. Saved 2 links, and found 1 possible link that wasn't saved."
  If an import fails, the readers' own messages ("x.csv is empty.") are
  shown as-is.
- **Open a database.** Drop a SQLite file (`.db`, `.sqlite`, `.sqlite3`) on
  the window, or pick one: the built-in sample, any dataset (newest first),
  or any file. Everything opens read-only ("Opened shop.db (read-only)"). If
  opening fails, whatever was open before stays open. File pickers start in
  the folder of the last file opened this session.
- **Tables.** Click one to see its first 200 rows; the headline says when
  there are more.
- **Relationships.** A diagram of how the tables connect (§5), then every
  link as a clickable line, then anything not linked or not saved. For links
  from an import this session, each says how well the values matched
  ("Every value matched" / "97% of values matched").
- **Add a link by hand** (`LinkEditor`) — for columns that belong together
  but are named differently, which the automatic check (§4) can't tell apart
  from a coincidence (`orders.cust_no` → `customers.customer_id`). Only for
  datasets, so the sample and opened files are never changed.
  - **Add link…** in the Relationships view opens a dialog with four lists:
    table and column, "points at", table and column. A possible link from
    the import has **Save this link…**, which opens it filled in.
  - Once all four are picked, the values are checked in the background: "2
    of 3 distinct cust_no values (66%) are in customers.customer_id. The rest
    will show as rows that point at nothing." Save stays off if the column
    it points at has repeated values (a link needs something like an id),
    if the column already has a link, or if a column points at itself. When
    nothing matches, it warns that the values may be stored differently
    (007 as text, 7 as a number).
  - Saving makes it a real foreign key. SQLite can't add one to an existing
    table, so the table is rebuilt with it, keeping its rows, indexes and
    triggers; views that use it keep working. If the column pointed at isn't
    a key yet, it gets a UNIQUE index, as SQLite requires. It's one
    transaction, so a failure leaves the dataset as it was. If the rebuild
    left a lot of free space, the file is compacted (`VACUUM`) afterwards.
    A big table takes a while; the status bar says what's happening.
- **Joined view.** Clicking a link shows the child table's rows next to the
  row each one points at, e.g. "On customer_id. Showing 200 of 1,200 rows. 3
  rows point at a customers row that isn't there." Child rows with no match
  still show, with the parent columns empty.
- **Share with users** — copies the open database (the sample included)
  into `data/imports/`, making it a dataset.
- **Remove from datasets** — deletes the open dataset's file after a
  confirmation. Users stop seeing it next time their list refreshes.

---

## 3. Importing data — `DataImporter`, `CsvReader`, `JsonReader`

`DataImporter` turns the dropped files into a new SQLite database in
`data/imports/`. Every database there is a *dataset*.

- **What can be imported:** `.csv`, `.tsv`, `.json`, `.jsonl` / `.ndjson`.
- **One table per CSV file**; a JSON file may hold several.
- **Table names** come from file names: "Order Items (2024).csv" →
  `order_items_2024` (lowercase letters, digits, underscores). Two files that
  would clash (`orders.csv`, `Orders.CSV`) become `orders` and `orders_2`.
- **Dataset file name** is made from its tables so users can tell datasets
  apart: `customers.db`, `customers-orders-products.db`, or
  `customers-orders-and-3-more.db`. If the name is taken: `-2`, `-3`, …
- **Column types** are worked out from the values: `INTEGER` if every value
  is a whole number, `REAL` if every value is a number, otherwise `TEXT`. A
  number with a leading zero (a zip code, "007") keeps the column `TEXT` so
  the zero isn't lost.
- **Primary key:** the column other tables point at; otherwise the table's
  own id column (`id`, or `customer_id` in `customers`) if it's unique;
  otherwise none.
- **Links:** `RelationshipFinder` (§4) suggests them. STRONG and LIKELY
  links are saved as real foreign keys; POSSIBLE ones are reported but not
  saved, since they rest on matching values alone.

### How big files fit — `StagedTable`

CSV files have no size limit: an 8-million-row, 31-column file (1.3 GB)
imports in about 3½ minutes and needs no more than a few hundred MB of memory.

- **Streamed.** Rows are read one at a time and written, 5,000 at a time,
  into a scratch database (`import-….stage` next to the new dataset). A
  second thread parses the file while the first writes to SQLite, since each
  takes about half the time.
- **Learned on the way.** While rows go by, `StagedTable` works out each
  column's type, counts its empty values, and keeps a sample: the first
  20,000 rows are checked for repeats (a repeat means the column can't be a
  key) and up to 1,000 distinct values are kept.
- **Checked by SQLite.** Keys and matching values (§4) are counted with SQL
  on the scratch tables, not in Java, and only for the column pairs the
  samples haven't already ruled out.
- **Copied into typed tables.** Once the links are known, each table is
  created with its types, primary key and foreign keys and filled from its
  scratch copy in one `INSERT … SELECT`.
- **Nothing left behind.** The scratch file is always deleted, and the
  half-built dataset (`import-….db.partial`) is deleted if anything fails or
  the import is cancelled. While it runs, an import needs free disk space of
  about twice the files' size.

### CSV — `CsvReader`

- RFC 4180: quoted fields can hold the delimiter, line breaks and doubled
  quotes. The first row is the header.
- Handles what real exports throw at it: a UTF-8 BOM; Windows-1252 files (the
  usual Excel export) when the bytes aren't valid UTF-8; comma, semicolon or
  tab delimiters (whichever appears most in the header line); blank lines;
  and rows with too few or too many fields.
- Empty fields become `NULL`.
- Header cells are trimmed; blank ones become `column_N`, and repeats get
  `_2`, `_3`, … (compared case-insensitively, as SQL does).
- Any size: the file is read once up front to check its encoding (a stray
  Windows-1252 byte can be anywhere in it), then parsed row by row.
- A quoted value that's never closed is reported with the line it starts on
  ("orders.csv has a quoted value starting on line 3 that's never closed.")
  rather than swallowing the rest of the file.

### JSON — `JsonReader`

- Gives tables in the same shape as `CsvReader`.
- Files over 200 MB are refused, since the whole document is parsed in
  memory; the message suggests saving large data as CSV instead.
- What becomes a table:
  - an **array of objects** — one table named after the file, one row per
    object;
  - an **object holding arrays of objects**
    (`{"customers": [...], "orders": [...]}`) — one table per array, named
    after its key. With only one such array (`{"data": [...], "meta": {...}}`)
    the table is named after the file;
  - **any other object** — a one-row table;
  - **JSON Lines** — one object per line, one table.
- Nested objects are flattened into columns (`address.city` →
  `address_city`); nested arrays are kept as JSON text in one cell.
- Columns are every key seen, in first-seen order; a row missing a key gets
  `NULL` there.
- Numbers are kept exactly as written (`007.50` isn't turned into `7.5`
  before the column type is decided).
- Its own small JSON parser (RFC 8259). Errors name the place, e.g.
  "unexpected ',' (line 3, column 14)". Nesting deeper than 500 levels is
  refused rather than risking a crash.

---

## 4. Finding links between files — `RelationshipFinder`

Works out which column in one file points at a key in another (e.g.
`orders.customer_id` → `customers.customer_id`).

- A **key** is a column with a value in every row and no repeats.
- A link from column C to key K needs two kinds of evidence:
  - **values** — nearly all of C's distinct values appear in K;
  - **names** — C is named after K or after K's table (`customer_id` →
    `customers.customer_id`, or `customer_id` → `customers.id`).
- Strength:
  - **STRONG** — names agree and every value matches.
  - **LIKELY** — names agree and 90%+ of values match. Below 90%, matching
    names are treated as coincidence.
  - **POSSIBLE** — values alone. Needs at least 10 distinct values, and
    never for a plain integer key, since small ids (1, 2, 3…) overlap by
    coincidence far too often.
- Values are compared after tidying: trimmed, and numbers written one way
  ("007", "7.0" and "7" all match). SQLite does the comparing, using a
  `key_value` function registered from Java so both sides tidy values the
  same way.
- To keep big tables quick, most column/key pairs are ruled out from the
  samples `StagedTable` kept (§3) before anything is counted: a column with a
  missing value or an early repeat can't be a key, and a values-only link is
  only checked if every sampled value is in the key. A key's values are
  indexed only when a link or primary key needs them, and only once.
- Names are compared loosely ("Customer ID" = `customer_id`), with
  good-enough singulars for table names (categories, addresses, order_items).
- Two tables that share a unique column (`users.user_id` and
  `profiles.user_id`) would be found in both directions. The one kept points
  at the table that looks like the key's owner: the column is named as that
  table's own id, or failing that, it's the table's first column.
- Results are ranked strongest first, then by match rate.

---

## 5. Relationship diagram — `RelationshipDiagram`

Draws a database's foreign keys in Admin's Relationships view.

- One box per table, listing only the columns that take part in a link, and
  a curve from each referencing column to the key it points at.
- Reads left to right, owner to owned: tables that others point at sit on
  the left; tables pointing at them sit to the right.
- Tables in each column are ordered to cut down crossing lines (start
  alphabetically, then repeatedly sort by the average position of linked
  tables, sweeping left-to-right and back).
- A table that points at itself loops out past the left edge of its box.
- Each curve has a wider invisible copy so it's easy to hover and click;
  clicking opens the joined view for that link.

---

## 6. Datasets — `Datasets`, `SampleDatabase`

- A dataset is any SQLite file in `data/imports/`. Admins put them there
  (import or Share with users); users only ever read them.
- `Datasets` lists them newest first, with readable names:
  `customers-orders-products.db` shows as "customers, orders, products", and
  an older timestamped import (`import-20260922-183919.db`) as "Import of Sep
  22, 2026 18:39".
- `SampleDatabase` is a small generic shop — customers, suppliers, products,
  orders and order lines — to practise on. It's written to `data/sample.db`
  the first time it's needed, from a fixed random seed so every install gets
  the same data. It's built in a temporary file and moved into place at the
  end, so an interrupted build never leaves a half-filled sample.

---

## 7. Explore and chart without SQL (users) — `Home_View`, `QueryBuilder`, `ChartMaker`

Everything here is read-only, and **users never see or type SQL**: the admin
handles the data (import, relationships, accounts), and users point and click.
Their clicks build a request that `QueryBuilder` turns into a SELECT behind
the scenes.

### Side panel

- The dataset list (the built-in sample is always there), refreshed from
  `data/imports/` and keeping the open dataset selected if it still exists.
- For the open dataset, its tables and their columns as a tree. **Click a
  table** to see it; that also resets the controls below.
- Opening a dataset lists its tables and shows the first one. Status bar:
  "Viewing customers, orders (read-only)".

### Explore controls

- **Add columns from…** — one checkbox per link leaving the table (from the
  dataset's foreign keys, which the importer worked out). Ticking
  "customers (by customer_id)" on `orders` adds the customer's columns as
  `customers.city`, `customers.email`, … via a LEFT JOIN, so every order still
  shows. The linked key itself is left out, since it only repeats
  `customer_id`. Hidden when the table has no links. Two links to the same
  table are told apart as `people (rider_id).name`; a table linked to itself
  shows as `parent people.name`.
- **Filter** — pick a column, then *is*, *is not*, *contains*, *is more
  than*, *is less than*, *is empty* or *is not empty*, type a value and
  press **Add filter** (or Enter). Each filter becomes a chip
  ("customers.city is Boston ✕"); click it to remove it. All filters must
  match.
  - A plain number ("5", "-2.5") is compared as a number; anything else,
    including "02134", as text. Quotes, `%` and `_` in values are escaped, so
    whatever is typed is only ever a value.
  - *is not* keeps empty cells; *is empty* means NULL or blank.
- **Sort by** — a column plus **A → Z, 1 → 9** or **Z → A, 9 → 1**;
  "Original order" turns it off.
- Unticking a link drops any filter or sort that used its columns.
- The **Rows** tab shows up to 500 rows; the count above the grid says when
  there are more ("Showing 500 of 1,532 rows").
- The generated query still goes through `DatabaseBrowser`'s single-SELECT
  check and 30-second timeout, like everything else (§8).

### Chart tab

- Chart types: **bar**, **line**, **pie**, **scatter**.
- Bar, line and pie read as a sentence, e.g. "Sum of total by city": pick
  what to group by, then what to measure — **count of rows**, or the
  **sum / average / minimum / maximum** of a number column.
- Scatter plots one number column (X) against another (Y), row by row. If X
  is text or Y is missing, number columns are swapped in.
- For a new result, the choices are kept if those columns are still there;
  otherwise it groups by the first text column and measures the first number
  column.
- A number column is one where every non-empty value is a plain decimal
  ("12", "-3.5", ".5", "1e6" — not "NaN" or "0x1p3").
- **Charts cover the whole result**, not just the 500 rows shown: SQLite does
  the grouping by wrapping the query as a subquery.
- Bars and slices come biggest first; lines run in X order. Bars start from
  zero so lengths compare honestly; lines and points zoom to the data.
- Pies show the 10 biggest slices plus an "Other" slice; slices must be
  positive.
- There's a limit on how many groups or points are fetched, and a line under
  the chart says when there were more: "Average of total by city: showing
  the 40 biggest of 212 groups."
- Empty values show as "(empty)". The number 1 and the text "1" group
  separately in SQLite, so a repeated label gets " (2)".

---

## 8. Read-only database access — `DatabaseBrowser`

Everything Admin and Home read goes through this.

- Every connection opens with SQLite's read-only flag, so a mistyped path
  fails instead of creating a file, and no query can change the data.
- Each connection gets a 64 MB page cache instead of SQLite's 2 MB, which is
  what keeps multi-million-row datasets usable: counting a join's unmatched
  rows on 8 million rows went from over 90 seconds to about 20.
- Lists tables (sorted, leaving out SQLite's own `sqlite_*` tables) and
  single-column foreign keys (multi-column ones are left out — the diagram
  and joins work one column at a time). Both are read once per open
  database.
- Previews a table's first rows plus its total row count. Table names are
  checked against the list and quoted, never pasted into SQL raw.
- Joins a child table to its parent along one foreign key (a LEFT JOIN, so
  unmatched child rows still show), with columns named `table.column` and a
  count of unmatched rows.
- Runs a checked, single SELECT (the one `QueryBuilder` writes for §7, and
  the chart queries) with a 30-second timeout.
- Every cell is turned into display text.

---

## 9. Shared screen pieces

- **`BackgroundWork`** — runs database reads off the UI thread, one at a
  time, so a slow read never freezes the window. Starting new work replaces
  whatever was still running; the older result is ignored.
- **`ResultTable`** — the dense rows grid used by both Admin and Home, one
  column per result column, with `NULL` shown muted so it reads differently
  from text.
- **`ViewText`** — shared status wording: "1 file" / "3 files"; "No rows",
  "1 row", "All 12 rows", "Showing 200 of 1,532 rows"; and failure messages
  that say what failed plus the likely cause and fix, read from SQLite's
  error codes. Kept static so tests can check it without a window.
- **`Views`** — small helpers every screen repeats: add stylesheets,
  show/hide a node (and take a hidden one out of the layout), set and colour
  the status bar, the login-style key/value grid cells, switch screens
  (logging instead of crashing if a screen can't be built), the role-based
  landing screen, and the account block.
- **`PasswordVisibilityToggle`** — pairs a masked password field with a
  plain-text copy and a Show/Hide button. The two share their text, so code
  keeps using the original field. One toggle can control several fields
  (new + confirm password).
- **`StatusLabelAlignment`** — keeps a status message centred while it fits
  on one line, and left-aligns it once it wraps (centred multi-line text
  looks ragged). Two things that were learned the hard way:
  - It waits one frame before measuring, because before the screen is shown
    the label's font isn't resolved yet and measurements are wrong.
  - It decides "does it wrap?" by comparing the text's natural width with
    the label's max width. Comparing heights looked reasonable but wasn't
    reliable — wrapped and unwrapped text can report slightly different line
    heights even for one line.
- **`SchemaDiagram`** — the drawing of the app's own `Users` table and its
  Role values on the login and change-password side panels, with the focused
  field's row lit up.
- **Styles** — `theme.css` (shared palette, side panel, buttons, links,
  status bar), `login.css` (login, create account, change password),
  `home.css` (Admin and Home, the rows grid, the relationship diagram).

---

## Running from the IDE

`./mvnw javafx:run` already passes the JVM options below (set in `pom.xml`'s
`javafx-maven-plugin`). An IntelliJ Run configuration doesn't inherit them, so
add them under Edit Configurations → VM options:

```
--enable-native-access=javafx.graphics
--enable-native-access=ALL-UNNAMED
--sun-misc-unsafe-memory-access=allow
```

- The first lets JavaFX load its native window and graphics libraries
  (JEP 472).
- The second covers native loads from the unnamed/classpath module (e.g.
  sqlite-jdbc under Surefire, which runs off the classpath).
- The third silences the Marlin "Unsafe" warning on JDK 24+ with JavaFX 21.
