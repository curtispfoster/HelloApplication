# Connecting SQLite to the JavaFX Login View

The login screen does not open the database. Data flows one way:

`LoginView` → `LoginController` → `AuthService` → `Database` → `data/users.db`

Admin user management uses the same file:

`AdminView` → `AdminController` → `UserRepository` → `Database` → `data/users.db`

Use the **Xerial SQLite JDBC** driver. No Microsoft Access install, no ODBC.

## Requirements

- Java 11 or later
- Maven
- `org.xerial:sqlite-jdbc` (3.49.1.0 or current)

## 1. Database file and schema

Keep the file **outside** `target/` so Maven rebuilds do not delete it:

`data/users.db`

SQLite creates the file automatically on first connect if you also create the table from Java.

```sql
CREATE TABLE IF NOT EXISTS Users (
    UserID   INTEGER PRIMARY KEY AUTOINCREMENT,
    Username TEXT    NOT NULL UNIQUE,
    Password TEXT    NOT NULL,
    Role     TEXT    NOT NULL DEFAULT 'USER'
);

INSERT OR IGNORE INTO Users (Username, Password, Role)
VALUES ('admin', 'secret', 'ADMIN');
```

| Column     | Type    | Notes                         |
|------------|---------|-------------------------------|
| `UserID`   | INTEGER | Primary key                   |
| `Username` | TEXT    | Email or username, unique     |
| `Password` | TEXT    | Plain text for learning only  |
| `Role`     | TEXT    | `ADMIN` or `USER`             |

You can inspect the file later with [DB Browser for SQLite](https://sqlitebrowser.org/).

## 2. Add the driver

In `pom.xml`:

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.49.1.0</version>
</dependency>
```

Reload Maven.

## 3. Database class

```java
package com.example.helloapplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final String url;

    public Database(Path dbFile) {
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException {
        Path parent = Path.of(url.replace("jdbc:sqlite:", "")).getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception ignored) {
                // directory may already exist
            }
        }

        String create = """
                CREATE TABLE IF NOT EXISTS Users (
                    UserID   INTEGER PRIMARY KEY AUTOINCREMENT,
                    Username TEXT    NOT NULL UNIQUE,
                    Password TEXT    NOT NULL,
                    Role     TEXT    NOT NULL DEFAULT 'USER'
                )
                """;

        String seed = """
                INSERT OR IGNORE INTO Users (Username, Password, Role)
                VALUES ('admin', 'secret', 'ADMIN')
                """;

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(create);
            st.execute(seed);
        }
    }
}
```

Example URL:

`jdbc:sqlite:C:/Users/You/IdeaProjects/helloapplication/data/users.db`

Call `database.init()` once at startup, before login.

## 4. Auth result and Authentication

Return status **and** role so the app can open Admin vs Home.

```java
package com.example.helloapplication;

public class AuthResult {
    public final AuthService.Status status;
    public final String username;
    public final String role;

    public AuthResult(AuthService.Status status, String username, String role) {
        this.status = status;
        this.username = username;
        this.role = role;
    }

    public boolean isAdmin() {
        return status == AuthService.Status.OK
                && role != null
                && role.equalsIgnoreCase("ADMIN");
    }
}
```

```java
package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    public enum Status { EMPTY, WRONG, OK, ERROR }

    private final Database database;

    public AuthService(Database database) {
        this.database = database;
    }

    public AuthResult check(String user, String password) {
        if (user == null || user.isBlank()
                || password == null || password.isBlank()) {
            return new AuthResult(Status.EMPTY, null, null);
        }

        String sql = "SELECT Username, Role FROM Users WHERE Username = ? AND Password = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.trim());
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new AuthResult(Status.WRONG, null, null);
                }
                return new AuthResult(
                        Status.OK,
                        rs.getString("Username"),
                        rs.getString("Role")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new AuthResult(Status.ERROR, null, null);
        }
    }
}
```

`try (...)` closes the connection even if the query fails.

## 5. Wire login in the view

```java
Path dbFile = Path.of("data", "users.db");
System.out.println(dbFile.toAbsolutePath());

Database database = new Database(dbFile);
database.init();

AuthService auth = new AuthService(database);
UserRepository users = new UserRepository(database);

LoginController controller = new LoginController(
        auth, usernameField, passwordField, loginStatus
);

loginButton.setOnAction(e -> {
    AuthResult result = controller.handleLogin();
    if (result == null) {
        return;
    }
    if (result.isAdmin()) {
        // stage.setScene(new Scene(new AdminView(users).getRoot()));
    } else if (result.status == AuthService.Status.OK) {
        // stage.setScene(new Scene(new HomeView().getRoot()));
    }
});

passwordField.setOnAction(e -> loginButton.fire());
```

Status messages:

| Status  | Message                                      |
|---------|----------------------------------------------|
| `EMPTY` | Email/username or password is empty.         |
| `WRONG` | Wrong email/username or password.            |
| `OK`    | Signed in.                                   |
| `ERROR` | Could not reach the database.                |

Do not tell the user which field was wrong.

Have `handleLogin()` return the `AuthResult` (or `null` if you only updated the label).

## 6. Admin view controls the user table

A normal user never opens `AdminView`. An admin can list / add / delete users through `UserRepository`.

```java
package com.example.helloapplication;

public class User {
    public final int id;
    public final String username;
    public final String role;

    public User(int id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }
}
```

```java
package com.example.helloapplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT UserID, Username, Role FROM Users ORDER BY Username";
        List<User> list = new ArrayList<>();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("UserID"),
                        rs.getString("Username"),
                        rs.getString("Role")
                ));
            }
        }
        return list;
    }

    public void insert(String username, String password, String role) throws SQLException {
        String sql = "INSERT INTO Users (Username, Password, Role) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    public void delete(int userId) throws SQLException {
        String sql = "DELETE FROM Users WHERE UserID = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}
```

Admin UI:

- `TableView<User>` columns: ID, Username, Role (never show passwords)
- Buttons: Add, Delete, Refresh
- Add opens a dialog (username, password, role) then `insert` + `findAll`
- Delete uses `UserID`, after a confirm dialog

Do not give the admin a raw SQL box. They manage **users**, not the `.db` file.

## 7. Checklist

- [ ] Maven resolved `sqlite-jdbc`
- [ ] `database.init()` runs at startup
- [ ] `data/users.db` appears after first launch
- [ ] Empty fields → `EMPTY`
- [ ] `admin` / `secret` → `OK` and Admin view
- [ ] Wrong pair → `WRONG`
- [ ] Missing folder / bad path → `ERROR`
- [ ] Admin table lists users without passwords
- [ ] Add / delete updates `Users` and refreshes the table

## Common problems

| Problem | Likely cause |
|---------|----------------|
| `unable to open database file` | `data/` folder missing, or working directory is not the module root |
| Table/column not found | `init()` not called, or different spelling |
| Always `WRONG` | Seed insert did not run, or extra spaces in the field |
| `UNIQUE constraint failed` | Username already exists |
| Driver missing | Maven not reloaded after editing `pom.xml` |

Relative paths are resolved from the **process working directory** (usually the module folder in IntelliJ), not from the `.java` file. Print `dbFile.toAbsolutePath()` while debugging.

## Do not

- Put SQL in `LoginView`, `AdminView`, or the controllers
- Build SQL with string concatenation (`+ user`)
- Store real user passwords as plain text in a finished app
- Let a `USER` role open `AdminView`

## Next steps

- Hash passwords (e.g. BCrypt) and store only the hash
- Add `update` / reset-password on `UserRepository`
- Run database calls on a background thread if the UI hitchs
- Prevent deleting the last remaining `ADMIN`
