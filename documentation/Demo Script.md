# Demo Script — Job Fair

A 3-minute walkthrough of HelloApplication, built on the sample shop that
ships with the app. Print this page or keep it open on your phone.

## Tonight: setup checklist

- [ ] **Launch it once with Wi-Fi off:** `./mvnw clean javafx:run`. If it
      opens, the dependencies are all cached and the booth Wi-Fi doesn't
      matter.
- [ ] **Always launch through `mvnw`.** On this laptop `java -version` says
      1.8. `mvnw` finds the newer JDK, but running `java` directly won't work.
- [ ] **Know your owner password.** No default accounts exist anymore. You
      chose the owner's username and password on the first-run setup screen.
      If your old `admin` account still works, that's fine too.
- [ ] **Create a user account ahead of time.** Use "Create an account" on the
      login screen (Name / Email / Password; the email is the username). That
      screen isn't styled like the rest of the app yet, so don't show it live.
- [ ] **Open the sample once as that user,** so it's already built
      (`data/sample.db`) and opens instantly tomorrow.
- [ ] **Keep DataFest data off this laptop's screen.** The DataFest NDA
      doesn't allow showing it to anyone outside the event. Tell the story,
      demo on the sample.
- [ ] **Screen:** maximize the window, and close the terminal and file
      explorer.

## The 30-second pitch

> At DataFest my team had a big dataset split across several linked tables,
> and most of us only knew MS Access. So I built this: a desktop app where
> an admin drops the data files in, and the app **works out how the tables
> connect by itself**. Everyone else explores it by **pointing and clicking.
> Nobody needs to know SQL.** They filter, pull in columns from linked tables,
> and chart it. The app writes the SQL behind the scenes, and the data is
> read-only, so nobody can break it.
>
> It's Java 21 and JavaFX, with every screen built in code. It runs on
> SQLite, stores passwords with Argon2id, has three roles (user, admin and
> owner), and has 138 automated tests.

## The walkthrough (about 3 minutes)

### 1. Login screen

**Do:** Point at the side panel, then sign in as `admin`.

**Say:** "The side panel is the actual schema of the accounts table. Passwords
are hashed with Argon2id. The admin accounts that come built in have to pick a
new password on first login, and the error messages say exactly what went
wrong."

### 2. The admin brings data in

**Do:** Point at the message in the middle: *"Drop CSV or JSON files anywhere
on this window…"*

**Say:** "This is where the DataFest files went. You drag CSVs or JSON onto the
window, and each one becomes a table. The app then matches column names and
values, for example `orders.customer_id` to the customers table, and saves
each link it finds as a real foreign key with a confidence level. Those files
are under NDA, so I'll show you on a sample shop."

**Do:** **Open database** → **Sample database** → **Relationships**. Click
one of the lines between two tables.

**Say:** "Here's how the tables connect. Clicking a link shows the two tables
joined along it."

### 3. The admin manages accounts

**Do:** Click **Users**.

**Say:** "Admins manage accounts here. Deleting is role-guarded: an admin
can't delete an owner, nobody can delete their own account, and the last owner
can never be deleted."

### 4. The user explores, with no SQL

**Do:** **Log out**, then sign in as your user account. Click **Sample: a small
shop** under DATASETS, then click **orders** under TABLES.

**Say:** "This is what my teammates would see. No SQL anywhere."

**Do:** **Add columns from…** → tick **customers (by customer_id)**.

**Say:** "Orders only store a customer number. One click brings in the
customer's name and city, because the app already knows how the tables link.
That's a JOIN, but the user never has to know that."

**Do:** Filter: **customers.city** · **is** · type `Boston` → **Add filter**.

**Say:** "Filters show up as chips. Click the ✕ to take one away."

**Do:** Sort by **placed_on**, then **Z → A, 9 → 1**.

**Say:** "Newest Boston orders first."

### 5. Charts

**Do:** Open the **Chart** tab. Leave **Bar chart** and **Count of rows**,
and set **by** to **status**. Then switch Bar chart to **Pie chart**.

**Say:** "Charts are dropdowns too. SQLite does the grouping over every
matching row, not just the ones on screen."

**Do:** Click the **customers.city is Boston ✕** chip.

**Say:** "Take the filter away and the chart updates for all 1,200 orders."

### 6. Close

**Say:** "So the admin handles the technical side once, and everyone else just
points and clicks. Next on my list is letting the admin save ready-made views,
like 'Orders by city', that users open with one click."

## If something goes wrong

- **The app won't start:** run `./mvnw clean javafx:run` again from the
  project folder.
- **You forgot the user password:** sign in as `admin`. The Database Manager
  still shows the sample and its relationships, so do steps 2 and 3, then
  describe step 4.
- **A filter shows no rows:** remove the chip. Values must match exactly
  ("Boston", not "boston"), or use **contains**.

## Likely questions

**How does it find the relationships?**
It compares column names with table names. For example, `customer_id` points
at `customers`, and so does `CustomerID`. Then it
checks how many of the values actually exist in the other table. Every value
matching is STRONG, most matching is LIKELY, and a match on values alone is
POSSIBLE.

**How do users avoid SQL?**
Their clicks build a request, and a `QueryBuilder` class turns it into a
SELECT. Anything the user types is only ever a value: quotes and wildcards are
escaped, so typing SQL into a filter doesn't run it.

**How is the data protected?**
Datasets open in SQLite's read-only mode, and only one SELECT statement runs at
a time, with a 30-second timeout. Roles decide who reaches the Database
Manager.

**Why Argon2id?**
It's the current recommended password hash. It's deliberately slow and uses a
lot of memory, which makes guessing passwords expensive.

**Why JavaFX without FXML?**
I wanted every screen in plain Java, so I could share pieces between screens
and see exactly what each screen does in one file.

**How do you test it?**
138 JUnit tests cover importing, relationship finding, the query builder
(including awkward input like quotes and semicolons), charts, logins, password
rules and role guards.

**What was hardest?**
Working out relationships from raw CSVs, where nothing is declared: telling a
real link from two columns that just happen to share numbers.

**What's next?**
Saved views that admins set up for users, admin password resets, and
streaming import for very large files.

**Can I see the DataFest data?**
"No, it's under NDA, so I demo on a sample with the same kind of structure."
