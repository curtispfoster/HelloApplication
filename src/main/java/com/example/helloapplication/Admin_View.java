package com.example.helloapplication;

import com.example.helloapplication.DataImporter.ImportResult;
import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TableRef;
import com.example.helloapplication.Datasets.Dataset;
import com.example.helloapplication.RelationshipFinder.Relationship;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.helloapplication.ViewText.describeCount;
import static com.example.helloapplication.ViewText.describeFailure;
import static com.example.helloapplication.ViewText.plural;

public class Admin_View {

    private static final Logger LOGGER = Logger.getLogger(Admin_View.class.getName());

    private static final int PREVIEW_LIMIT = 200;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private static File lastDirectory;

    private enum Mode { EMPTY, ROWS, RELATIONSHIPS }

    private final Roles actor;
    private final BackgroundWork work = new BackgroundWork("database-manager");
    // Separate, so browsing while a big import runs doesn't cancel it. Starting another import does.
    private final BackgroundWork importWork = new BackgroundWork("data-import");
    private int importNumber;

    private Stage stage;
    private DatabaseBrowser browser;
    private List<TableRef> tables = List.of();
    private List<ForeignKey> foreignKeys = List.of();
    private ImportResult importInfo;

    private final Label sourceLabel = new Label();
    private final Hyperlink datasetLink = new Hyperlink();
    private final Button relationshipsButton = new Button();
    private final ListView<TableRef> tableList = new ListView<>();
    private final Hyperlink backLink = new Hyperlink("Back to relationships");
    private final Label headline = new Label();
    private final Label rowCount = new Label();
    private final VBox header = new VBox(2, backLink, headline, rowCount);
    private final TableView<List<String>> tableView = ResultTable.create("This table has no rows.");
    private final VBox relationshipView = new VBox(22);
    private final ScrollPane relationshipScroll = new ScrollPane(relationshipView);
    private final Label emptyMessage = new Label();
    private final HBox emptyActions = new HBox(10);
    private final VBox emptyState = new VBox(16, emptyMessage, emptyActions);
    private final Label dropMessage = new Label();
    private final StackPane dropOverlay = new StackPane(dropMessage);
    private final Label status = new Label("No database open");

    public Admin_View(Roles actor) {
        this.actor = actor;
    }

    public void show(Stage primaryStage) {
        this.stage = primaryStage;

        BorderPane main = new BorderPane(buildContent());
        main.getStyleClass().add("home-main");
        main.setBottom(buildStatusBar());
        HBox.setHgrow(main, Priority.ALWAYS);

        dropOverlay.getStyleClass().add("drop-overlay");
        dropMessage.getStyleClass().add("drop-message");
        dropOverlay.setMouseTransparent(true);
        dropOverlay.setVisible(false);

        StackPane root = new StackPane(new HBox(buildSidePanel(), main), dropOverlay);
        root.setPrefSize(1100, 640);

        Scene scene = new Scene(root);
        Views.addStylesheets(scene, "theme.css", "home.css");
        enableFileDrop(scene);

        primaryStage.setTitle("Database Manager (admin)");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
    }

    // ---- layout ----

    private VBox buildSidePanel() {
        Label wordmark = new Label("Database Manager");
        wordmark.getStyleClass().add("wordmark");

        Button openButton = new Button("Open database");
        openButton.getStyleClass().add("primary-button");
        openButton.setMaxWidth(Double.MAX_VALUE);
        Menu datasetsMenu = new Menu("Datasets");
        ContextMenu openMenu = new ContextMenu(
                menuItem("Import CSV or JSON files…", this::chooseDataFiles),
                datasetsMenu,
                new SeparatorMenuItem(),
                menuItem("Sample database", this::openSample),
                menuItem("SQLite file…", this::chooseSqliteFile));
        openMenu.getStyleClass().add("open-menu");
        openButton.setOnAction(e -> {
            fillDatasetsMenu(datasetsMenu);
            openMenu.show(openButton, Side.BOTTOM, 0, 4);
        });

        sourceLabel.getStyleClass().add("panel-caption");
        sourceLabel.setWrapText(true);
        sourceLabel.setMinHeight(Region.USE_PREF_SIZE);
        Views.show(sourceLabel, false);
        datasetLink.getStyleClass().add("panel-link");
        Views.show(datasetLink, false);

        relationshipsButton.getStyleClass().add("relationships-button");
        relationshipsButton.setMaxWidth(Double.MAX_VALUE);
        relationshipsButton.setOnAction(e -> {
            tableList.getSelectionModel().clearSelection();
            showRelationships();
        });
        Views.show(relationshipsButton, false);

        tableList.getStyleClass().add("table-list");
        tableList.setPlaceholder(new Label(""));
        tableList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TableRef table, boolean empty) {
                super.updateItem(table, empty);
                setText(empty || table == null ? null : table.displayName());
            }
        });
        tableList.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, table) -> {
                    if (table != null) {
                        loadPreview(table);
                    }
                });
        VBox.setVgrow(tableList, Priority.ALWAYS);

        VBox panel = new VBox(14, wordmark, openButton, new VBox(4, sourceLabel, datasetLink),
                relationshipsButton, tableList, Views.accountBlock(stage, actor));
        panel.getStyleClass().addAll("side-panel", "home-panel");
        panel.setPrefWidth(250);
        panel.setMinWidth(250);
        return panel;
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void fillDatasetsMenu(Menu menu) {
        menu.getItems().clear();
        try {
            for (Dataset dataset : Datasets.list()) {
                menu.getItems().add(menuItem(dataset.name(),
                        () -> openDatabase(DatabaseBrowser.sqlite(dataset.file()), null)));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not list datasets", e);
        }
        if (menu.getItems().isEmpty()) {
            MenuItem none = new MenuItem("None yet: import some files");
            none.setDisable(true);
            menu.getItems().add(none);
        }
    }

    private VBox buildContent() {
        headline.getStyleClass().add("home-headline");
        rowCount.getStyleClass().add("home-muted");
        rowCount.setWrapText(true);
        backLink.getStyleClass().add("back-link");
        backLink.setOnAction(e -> showRelationships());

        relationshipView.getStyleClass().add("relationship-view");
        relationshipScroll.getStyleClass().add("relationship-scroll");
        relationshipScroll.setFitToWidth(true);

        emptyMessage.getStyleClass().add("home-empty");
        emptyMessage.setWrapText(true);
        emptyMessage.setMaxWidth(460);
        emptyMessage.setAlignment(Pos.CENTER);
        emptyMessage.setTextAlignment(TextAlignment.CENTER);
        emptyActions.setAlignment(Pos.CENTER);
        emptyState.setAlignment(Pos.CENTER);
        showStartState();

        StackPane body = new StackPane(tableView, relationshipScroll, emptyState);
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox content = new VBox(18, header, body);
        content.getStyleClass().add("home-content");
        return content;
    }

    private void showStartState() {
        Button importFiles = actionButton("Import CSV or JSON files", "primary-button", this::chooseDataFiles);
        Button sample = actionButton("Open sample database", "secondary-button", this::openSample);
        Button file = actionButton("Open SQLite file", "secondary-button", this::chooseSqliteFile);
        showEmptyState("Drop CSV or JSON files anywhere on this window to import them as a dataset. "
                + "The links between the files are worked out for you, and users can query and chart "
                + "every dataset from their Home screen.", importFiles, sample, file);
    }

    private Button actionButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Label buildStatusBar() {
        status.getStyleClass().add("status-bar");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        return status;
    }

    private void setMode(Mode mode) {
        Views.show(emptyState, mode == Mode.EMPTY);
        Views.show(header, mode != Mode.EMPTY);
        Views.show(tableView, mode == Mode.ROWS);
        Views.show(relationshipScroll, mode == Mode.RELATIONSHIPS);
        relationshipsButton.pseudoClassStateChanged(SELECTED, mode == Mode.RELATIONSHIPS);
    }

    // ---- drag and drop ----

    private void enableFileDrop(Scene scene) {
        scene.setOnDragOver(e -> {
            DroppedFiles files = DroppedFiles.of(e.getDragboard());
            if (!files.isEmpty()) {
                e.acceptTransferModes(TransferMode.COPY);
                dropMessage.setText(files.data().isEmpty()
                        ? "Drop to open " + files.databases().get(0).getFileName()
                        : "Drop to import " + plural(files.data().size(), "file")
                        + " as a dataset for users");
                dropOverlay.setVisible(true);
            }
            e.consume();
        });
        scene.setOnDragExited(e -> dropOverlay.setVisible(false));
        scene.setOnDragDropped(e -> {
            dropOverlay.setVisible(false);
            DroppedFiles files = DroppedFiles.of(e.getDragboard());
            if (!files.data().isEmpty()) {
                importFiles(files.data());
            } else if (!files.databases().isEmpty()) {
                openDatabase(DatabaseBrowser.sqlite(files.databases().get(0)), null);
            }
            e.setDropCompleted(!files.isEmpty());
            e.consume();
        });
    }

    record DroppedFiles(List<Path> data, List<Path> databases) {
        static DroppedFiles of(Dragboard board) {
            return board.hasFiles() ? of(board.getFiles()) : new DroppedFiles(List.of(), List.of());
        }

        static DroppedFiles of(List<File> files) {
            List<Path> data = new ArrayList<>();
            List<Path> databases = new ArrayList<>();
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                if (DataImporter.canImport(file.toPath())) {
                    data.add(file.toPath());
                } else if (Datasets.isDatabaseName(file.getName())) {
                    databases.add(file.toPath());
                }
            }
            return new DroppedFiles(data, databases);
        }

        boolean isEmpty() {
            return data.isEmpty() && databases.isEmpty();
        }
    }

    // ---- opening ----

    private void openSample() {
        setStatus("Preparing the sample database…", null);
        work.run(SampleDatabase::ensure,
                file -> openDatabase(DatabaseBrowser.sqlite(file), null),
                error -> {
                    LOGGER.log(Level.WARNING, "Could not create the sample database", error);
                    setStatus("Couldn't create the sample database in the data folder. "
                            + "Check that the app can write there.", "status-error");
                });
    }

    private void chooseSqliteFile() {
        FileChooser chooser = fileChooser("Open SQLite file",
                new FileChooser.ExtensionFilter("SQLite databases", "*.db", "*.sqlite", "*.sqlite3"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            lastDirectory = file.getParentFile();
            openDatabase(DatabaseBrowser.sqlite(file.toPath()), null);
        }
    }

    private void chooseDataFiles() {
        FileChooser chooser = fileChooser("Import CSV or JSON files", new FileChooser.ExtensionFilter(
                "CSV and JSON files", "*.csv", "*.tsv", "*.json", "*.jsonl", "*.ndjson"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            lastDirectory = files.get(0).getParentFile();
            List<Path> data = DroppedFiles.of(files).data();
            if (data.isEmpty()) {
                setStatus("Pick CSV, TSV, JSON or JSON Lines files to import.", "status-error");
            } else {
                importFiles(data);
            }
        }
    }

    private FileChooser fileChooser(String title, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(filter, new FileChooser.ExtensionFilter("All files", "*.*"));
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        return chooser;
    }

    private void importFiles(List<Path> files) {
        int number = ++importNumber;
        setStatus("Importing " + plural(files.size(), "file") + " and looking for links between them…", null);
        // A big import runs for minutes, so it reports as it goes; a newer import's messages replace an older one's.
        DataImporter.Progress progress = message -> Platform.runLater(() -> {
            if (number == importNumber) {
                setStatus(message, null);
            }
        });
        importWork.run(() -> DataImporter.importFiles(files, DataImporter.DEFAULT_DIRECTORY, progress),
                result -> openDatabase(DatabaseBrowser.sqlite(result.database()), result),
                error -> {
                    LOGGER.log(Level.WARNING, "Import failed", error);
                    setStatus(describeImportFailure(error), "status-error");
                });
    }

    private record Contents(List<TableRef> tables, List<ForeignKey> foreignKeys) {}

    private void openDatabase(DatabaseBrowser candidate, ImportResult imported) {
        String name = candidate.description();
        if (imported == null) {
            setStatus("Opening " + name + "…", null);
        }

        work.run(() -> new Contents(candidate.listTables(), foreignKeysOrNone(candidate)),
                contents -> {
                    browser = candidate;
                    importInfo = imported;
                    tables = contents.tables();
                    foreignKeys = contents.foreignKeys();
                    sourceLabel.setText(isDataset(candidate.file()) ? name + " (dataset users can see)" : name);
                    Views.show(sourceLabel, true);
                    updateDatasetLink();
                    relationshipsButton.setText("Relationships (" + foreignKeys.size() + ")");
                    // After an import the view also lists possible links and unlinked files, so keep it reachable.
                    Views.show(relationshipsButton, !foreignKeys.isEmpty() || imported != null);
                    tableList.getItems().setAll(tables);

                    if (tables.isEmpty()) {
                        showEmptyState("This database has no tables.");
                        setStatus(openedMessage(candidate), null);
                    } else if (imported != null) {
                        showRelationships();
                    } else {
                        tableList.getSelectionModel().selectFirst();
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not open " + name, error);
                    setStatus(describeFailure("open " + name, error), "status-error");
                });
    }

    private static List<ForeignKey> foreignKeysOrNone(DatabaseBrowser source) {
        try {
            return source.listForeignKeys();
        } catch (SQLException e) {
            LOGGER.log(Level.INFO, "Could not list foreign keys of " + source.description(), e);
            return List.of();
        }
    }

    // ---- datasets ----

    static boolean isDataset(Path file) {
        Path folder = DataImporter.DEFAULT_DIRECTORY.toAbsolutePath().normalize();
        Path parent = file.toAbsolutePath().normalize().getParent();
        return folder.equals(parent);
    }

    private void updateDatasetLink() {
        if (browser == null) {
            Views.show(datasetLink, false);
            return;
        }
        boolean dataset = isDataset(browser.file());
        datasetLink.setText(dataset ? "Remove from datasets" : "Share with users");
        datasetLink.setOnAction(e -> {
            if (dataset) {
                removeDataset();
            } else {
                shareWithUsers();
            }
        });
        Views.show(datasetLink, true);
    }

    private void shareWithUsers() {
        DatabaseBrowser source = browser;
        setStatus("Sharing " + source.description() + " with users…", null);
        work.run(() -> {
                    Files.createDirectories(DataImporter.DEFAULT_DIRECTORY);
                    String fileName = source.file().getFileName().toString();
                    int dot = fileName.lastIndexOf('.');
                    Path target = DataImporter.uniqueFile(DataImporter.DEFAULT_DIRECTORY,
                            dot > 0 ? fileName.substring(0, dot) : fileName, dot > 0 ? fileName.substring(dot) : ".db");
                    Files.copy(source.file(), target);
                    return target;
                },
                target -> {
                    openDatabase(DatabaseBrowser.sqlite(target), null);
                    setStatus("Shared as " + Datasets.displayName(target) + ". Users can now query and chart it.",
                            "status-ok");
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not share " + source.description(), error);
                    setStatus("Couldn't share " + source.description() + ". Check that the app can write to "
                            + DataImporter.DEFAULT_DIRECTORY + ".", "status-error");
                });
    }

    private void removeDataset() {
        Path file = browser.file();
        String name = Datasets.displayName(file);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Users won't be able to query or chart \"" + name + "\" any more. The database file "
                        + file.getFileName() + " will be deleted. The files it was imported from aren't touched.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Remove dataset");
        confirm.setHeaderText("Remove " + name + "?");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Remove");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        work.cancel();
        try {
            Files.delete(file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not delete " + file, e);
            setStatus("Couldn't remove " + name + ". A user may have it open; try again in a moment.",
                    "status-error");
            return;
        }
        browser = null;
        importInfo = null;
        tables = List.of();
        foreignKeys = List.of();
        tableList.getItems().clear();
        Views.show(sourceLabel, false);
        Views.show(relationshipsButton, false);
        updateDatasetLink();
        showStartState();
        setStatus("Removed " + name + ".", "status-ok");
    }

    // ---- rows ----

    private void loadPreview(TableRef table) {
        DatabaseBrowser source = browser;
        String name = table.displayName();
        setStatus("Loading " + name + "…", null);

        work.run(() -> source.preview(table, PREVIEW_LIMIT),
                preview -> {
                    Views.show(backLink, false);
                    showRows(name, describeCount(preview.rows().size(), preview.totalRows()), preview);
                    setStatus(openedMessage(source), null);
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not read table " + name, error);
                    setStatus(describeFailure("read " + name, error), "status-error");
                });
    }

    private void loadJoin(ForeignKey key) {
        DatabaseBrowser source = browser;
        String title = key.child().displayName() + " joined to " + key.parent().displayName();
        setStatus("Loading " + title + "…", null);

        work.run(() -> source.previewJoin(key, PREVIEW_LIMIT),
                join -> {
                    Views.show(backLink, !foreignKeys.isEmpty());
                    showRows(title, describeJoin(key, join.preview().rows().size(), join.preview().totalRows(),
                            join.unmatchedRows()), join.preview());
                    setStatus(openedMessage(source), null);
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not join " + title, error);
                    setStatus(describeFailure("load " + title, error), "status-error");
                });
    }

    private void showRows(String title, String summary, DatabaseBrowser.TablePreview preview) {
        headline.setText(title);
        rowCount.setText(summary);
        ResultTable.fill(tableView, preview);
        setMode(Mode.ROWS);
    }

    // ---- relationships ----

    private void showRelationships() {
        work.cancel();
        Views.show(backLink, false);
        headline.setText("Relationships");
        Set<TableRef> linked = new LinkedHashSet<>();
        for (ForeignKey key : foreignKeys) {
            linked.add(key.child());
            linked.add(key.parent());
        }
        relationshipView.getChildren().clear();
        if (foreignKeys.isEmpty()) {
            rowCount.setText("No links were found between these tables. Pick a table on the left to see its rows.");
        } else {
            rowCount.setText(plural(foreignKeys.size(), "link") + " between " + plural(linked.size(), "table")
                    + ". Click a link to see the two tables' rows side by side.");
            relationshipView.getChildren().add(RelationshipDiagram.build(foreignKeys, this::loadJoin));

            VBox links = new VBox(8, sectionTitle("Links"));
            for (ForeignKey key : foreignKeys) {
                Hyperlink link = new Hyperlink(key.child().displayName() + "." + key.childColumn() + " points at "
                        + key.parent().displayName() + "." + key.parentColumn());
                link.setOnAction(e -> loadJoin(key));
                Label detail = new Label(matchDetail(key));
                detail.getStyleClass().add("home-muted");
                HBox row = new HBox(12, link, detail);
                row.setAlignment(Pos.BASELINE_LEFT);
                links.getChildren().add(row);
            }
            relationshipView.getChildren().add(links);
        }

        if (importInfo != null && !importInfo.notSaved().isEmpty()) {
            VBox possible = new VBox(8, sectionTitle("Possible links, not saved"),
                    mutedText("These columns share values, but their names don't suggest a link, "
                            + "so they weren't saved. They may be a coincidence."));
            for (Relationship r : importInfo.notSaved()) {
                possible.getChildren().add(new Label(r.childTable() + "." + r.childColumn() + " has the same values as "
                        + r.parentTable() + "." + r.parentColumn()));
            }
            relationshipView.getChildren().add(possible);
        }

        List<String> unlinked = tables.stream().filter(t -> !linked.contains(t)).map(TableRef::displayName).toList();
        if (!unlinked.isEmpty()) {
            relationshipView.getChildren().add(new VBox(8, sectionTitle("Not linked to any other table"),
                    mutedText(String.join(", ", unlinked))));
        }

        setMode(Mode.RELATIONSHIPS);
        if (importInfo != null) {
            setStatus(describeImport(importInfo), "status-ok");
        } else if (browser != null) {
            setStatus(openedMessage(browser), null);
        }
    }

    private String matchDetail(ForeignKey key) {
        if (importInfo == null) {
            return "";
        }
        for (Relationship r : importInfo.saved()) {
            if (r.childTable().equals(key.child().name()) && r.childColumn().equals(key.childColumn())) {
                return describeMatch(r);
            }
        }
        return "";
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label mutedText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("home-muted");
        label.setWrapText(true);
        return label;
    }

    // ---- shared ----

    private void showEmptyState(String message, Button... actions) {
        emptyMessage.setText(message);
        emptyActions.getChildren().setAll(actions);
        Views.show(emptyActions, actions.length > 0);
        setMode(Mode.EMPTY);
    }

    private void setStatus(String text, String styleClass) {
        Views.setStatus(status, text, styleClass);
    }

    // ---- wording (static, so tests can check it without a window) ----

    static String openedMessage(DatabaseBrowser source) {
        return "Opened " + source.description() + " (read-only)";
    }

    static String describeImport(ImportResult result) {
        StringBuilder text = new StringBuilder("Imported ")
                .append(plural(result.tables().size(), "table"))
                .append(" into ").append(result.database().getFileName())
                .append(", now a dataset users can see. ");
        if (result.saved().isEmpty() && result.notSaved().isEmpty()) {
            return text.append(result.tables().size() == 1 ? "" : "No links between them were found.")
                    .toString().strip();
        }
        text.append("Saved ").append(plural(result.saved().size(), "link"));
        if (!result.notSaved().isEmpty()) {
            text.append(", and found ").append(plural(result.notSaved().size(), "possible link"))
                    .append(result.notSaved().size() == 1 ? " that wasn't saved" : " that weren't saved");
        }
        return text.append(".").toString();
    }

    static String describeMatch(Relationship r) {
        if (r.matchedValues() == r.distinctValues()) {
            return "Every value matched";
        }
        return String.format("%d%% of values matched", (int) Math.floor(r.matchRate() * 100));
    }

    static String describeJoin(ForeignKey key, int shown, long total, long unmatched) {
        String on = key.childColumn().equals(key.parentColumn())
                ? key.childColumn() : key.childColumn() + " = " + key.parentColumn();
        String text = "On " + on + ". " + describeCount(shown, total) + ".";
        if (unmatched > 0) {
            text += " " + plural(unmatched, "row") + (unmatched == 1 ? " points" : " point")
                    + " at a " + key.parent().displayName() + " row that isn't there.";
        }
        return text;
    }

    static String describeImportFailure(Throwable error) {
        if (error instanceof IOException && error.getMessage() != null) {
            return "Couldn't import the files. " + error.getMessage();
        }
        if (error instanceof SQLException && error.getMessage() != null
                && (error.getMessage().contains("SQLITE_FULL") || error.getMessage().contains("disk is full"))) {
            return "Couldn't import the files. The disk is full: an import needs free space of about twice the"
                    + " files' size while it runs.";
        }
        return "Couldn't import the files. Check that they're readable and not open in another program.";
    }
}
