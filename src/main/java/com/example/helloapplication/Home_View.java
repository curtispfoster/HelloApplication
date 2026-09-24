package com.example.helloapplication;

import com.example.helloapplication.ChartMaker.Kind;
import com.example.helloapplication.ChartMaker.Measure;
import com.example.helloapplication.ChartMaker.Spec;
import com.example.helloapplication.DatabaseBrowser.ForeignKey;
import com.example.helloapplication.DatabaseBrowser.TablePreview;
import com.example.helloapplication.DatabaseBrowser.TableRef;
import com.example.helloapplication.Datasets.Dataset;
import com.example.helloapplication.QueryBuilder.Filter;
import com.example.helloapplication.QueryBuilder.Op;
import com.example.helloapplication.QueryBuilder.Request;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.helloapplication.ViewText.describeCount;
import static com.example.helloapplication.ViewText.describeFailure;
import static com.example.helloapplication.ViewText.plural;

public class Home_View {

    private static final Logger LOGGER = Logger.getLogger(Home_View.class.getName());

    private static final int PREVIEW_LIMIT = 500;
    private static final String SAMPLE_NAME = "Sample: a small shop";
    private static final String NO_SORT = "Original order";
    private static final String ASCENDING = "A → Z, 1 → 9";
    private static final String DESCENDING = "Z → A, 9 → 1";

    private record SchemaItem(String table, String column) {
        @Override
        public String toString() {
            return column == null ? table : column;
        }
    }

    private record Contents(DatabaseBrowser browser, Map<String, List<String>> tables, List<ForeignKey> links) {}

    private final Roles user;
    private final BackgroundWork work = new BackgroundWork("home-query");
    private final BackgroundWork chartWork = new BackgroundWork("home-chart");

    private Stage stage;
    private DatabaseBrowser browser;
    private Dataset openDataset;
    private Map<String, List<String>> tableColumns = Map.of();
    private List<ForeignKey> foreignKeys = List.of();
    private Request request;
    private boolean updatingControls;
    private String lastQuery;
    private TablePreview lastResult;
    private List<String> numericColumns = List.of();
    private boolean updatingChoices;

    private final ListView<Dataset> datasetList = new ListView<>();
    private final TreeView<SchemaItem> schemaTree = new TreeView<>(new TreeItem<>());
    private final Label tablesCaption = new Label("TABLES");
    private final Label treeHint = new Label("Click a table to see it.");
    private final Label headline = new Label();
    private final Label subtitle = new Label();
    private final MenuButton linksButton = new MenuButton();
    private final ComboBox<String> filterColumn = new ComboBox<>();
    private final ComboBox<Op> filterOp = new ComboBox<>();
    private final TextField filterValue = new TextField();
    private final Button addFilter = new Button("Add filter");
    private final FlowPane filterChips = new FlowPane(6, 6);
    private final ComboBox<String> sortColumn = new ComboBox<>();
    private final ComboBox<String> sortOrder = new ComboBox<>();
    private final Label rowCount = new Label();
    private final TableView<List<String>> tableView = ResultTable.create("No rows to show.");
    private final TabPane resultTabs = new TabPane();
    private final Tab chartTab = new Tab("Chart");
    private final ComboBox<Kind> kindBox = new ComboBox<>();
    private final ComboBox<String> xBox = new ComboBox<>();
    private final ComboBox<Measure> measureBox = new ComboBox<>();
    private final ComboBox<String> yBox = new ComboBox<>();
    private final Label xLabel = new Label();
    private final Label yLabel = new Label();
    private final StackPane chartHolder = new StackPane();
    private final Label chartSummary = new Label();
    private final VBox workArea = new VBox(14);
    private final Label emptyMessage = new Label();
    private final HBox emptyActions = new HBox(10);
    private final VBox emptyState = new VBox(16, emptyMessage, emptyActions);
    private final Label status = new Label("Pick a dataset to start");

    public Home_View(Roles user) {
        this.user = user;
    }

    public void show(Stage primaryStage) {
        this.stage = primaryStage;

        BorderPane main = new BorderPane(buildContent());
        main.getStyleClass().add("home-main");
        main.setBottom(buildStatusBar());
        HBox.setHgrow(main, Priority.ALWAYS);

        HBox root = new HBox(buildSidePanel(), main);
        root.setPrefSize(1180, 720);

        Scene scene = new Scene(root);
        Views.addStylesheets(scene, "theme.css", "home.css");

        primaryStage.setTitle("Database Manager");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();

        refreshDatasets();
    }

    // ---- layout ----

    private VBox buildSidePanel() {
        Label wordmark = new Label("Database Manager");
        wordmark.getStyleClass().add("wordmark");

        Label datasetsCaption = new Label("DATASETS");
        datasetsCaption.getStyleClass().add("panel-section");
        Hyperlink refresh = new Hyperlink("Refresh");
        refresh.getStyleClass().add("panel-link");
        refresh.setOnAction(e -> refreshDatasets());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox datasetsHeader = new HBox(datasetsCaption, spacer, refresh);
        datasetsHeader.setAlignment(Pos.BASELINE_LEFT);

        datasetList.getStyleClass().addAll("table-list", "dataset-list");
        datasetList.setPlaceholder(new Label(""));
        datasetList.setCellFactory(list -> new ListCell<>() {
            {
                setPrefWidth(0); // follow the list's width, so long names end in "…" instead of scrolling sideways
            }

            @Override
            protected void updateItem(Dataset dataset, boolean empty) {
                super.updateItem(dataset, empty);
                setText(empty || dataset == null ? null : dataset.name());
                setTooltip(empty || dataset == null ? null : new Tooltip(dataset.name()));
            }
        });
        datasetList.setPrefHeight(170);
        datasetList.setMinHeight(90);
        datasetList.getSelectionModel().selectedItemProperty().addListener((obs, was, dataset) -> {
            if (dataset != null && !dataset.equals(openDataset)) {
                openDataset(dataset);
            }
        });

        tablesCaption.getStyleClass().add("panel-section");
        treeHint.getStyleClass().add("panel-caption");
        treeHint.setWrapText(true);
        treeHint.setMinHeight(Region.USE_PREF_SIZE);
        schemaTree.getStyleClass().addAll("table-list", "schema-tree");
        schemaTree.setShowRoot(false);
        schemaTree.getSelectionModel().selectedItemProperty().addListener((obs, was, item) -> {
            if (item != null && item.getValue() != null && item.getValue().column() == null) {
                showTable(item.getValue().table());
            }
        });
        VBox.setVgrow(schemaTree, Priority.ALWAYS);
        Views.show(tablesCaption, false);
        Views.show(treeHint, false);

        VBox panel = new VBox(12, wordmark, datasetsHeader, datasetList,
                new VBox(4, tablesCaption, treeHint), schemaTree,
                Views.accountBlock(stage, user));
        panel.getStyleClass().addAll("side-panel", "home-panel");
        panel.setPrefWidth(260);
        panel.setMinWidth(260);
        return panel;
    }

    private StackPane buildContent() {
        headline.getStyleClass().add("home-headline");
        subtitle.getStyleClass().add("home-muted");

        rowCount.getStyleClass().add("home-muted");
        VBox rowsPane = new VBox(8, rowCount, tableView);
        rowsPane.getStyleClass().add("result-pane");
        VBox.setVgrow(tableView, Priority.ALWAYS);
        Tab rowsTab = new Tab("Rows", rowsPane);

        chartTab.setContent(buildChartPane());
        resultTabs.getTabs().addAll(rowsTab, chartTab);
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        resultTabs.getStyleClass().add("result-tabs");
        resultTabs.getSelectionModel().selectedItemProperty().addListener((obs, was, tab) -> {
            if (tab == chartTab) {
                drawChart();
            }
        });
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        workArea.getChildren().setAll(new VBox(2, headline, subtitle), buildExploreBar(), resultTabs);
        workArea.getStyleClass().add("home-content");

        emptyMessage.getStyleClass().add("home-empty");
        emptyMessage.setWrapText(true);
        emptyMessage.setMaxWidth(460);
        emptyMessage.setAlignment(Pos.CENTER);
        emptyMessage.setTextAlignment(TextAlignment.CENTER);
        emptyActions.setAlignment(Pos.CENTER);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.getStyleClass().add("home-content");
        showEmptyState("Loading datasets…");

        return new StackPane(workArea, emptyState);
    }

    private VBox buildExploreBar() {
        linksButton.getStyleClass().add("chart-choice");
        linksButton.setMnemonicParsing(false);

        filterColumn.setPromptText("Pick a column");
        filterColumn.setPrefWidth(170);
        filterOp.getItems().setAll(Op.values());
        filterOp.setValue(Op.IS);
        filterValue.setPromptText("Value");
        filterValue.setPrefWidth(150);
        filterValue.setOnAction(e -> addFilter());
        addFilter.getStyleClass().add("secondary-button");
        addFilter.setOnAction(e -> addFilter());
        filterColumn.valueProperty().addListener((obs, was, now) -> updateFilterInputs());
        filterOp.valueProperty().addListener((obs, was, now) -> updateFilterInputs());
        filterValue.textProperty().addListener((obs, was, now) -> updateFilterInputs());

        sortColumn.setPrefWidth(170);
        sortOrder.getItems().setAll(ASCENDING, DESCENDING);
        sortOrder.setValue(ASCENDING);
        sortColumn.valueProperty().addListener((obs, was, now) -> applySort());
        sortOrder.valueProperty().addListener((obs, was, now) -> applySort());

        for (Control control : List.of(filterColumn, filterOp, filterValue, sortColumn, sortOrder)) {
            control.getStyleClass().add("chart-choice");
        }
        Label filterLabel = new Label("Filter");
        Label sortLabel = new Label("Sort by");
        filterLabel.getStyleClass().add("home-muted");
        sortLabel.getStyleClass().add("home-muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(10, linksButton, spacer, sortLabel, sortColumn, sortOrder);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox filterRow = new HBox(8, filterLabel, filterColumn, filterOp, filterValue, addFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        Views.show(filterChips, false);

        return new VBox(8, topRow, filterRow, filterChips);
    }

    private VBox buildChartPane() {
        kindBox.getItems().setAll(Kind.values());
        kindBox.setValue(Kind.BAR);
        measureBox.getItems().setAll(Measure.values());
        measureBox.setValue(Measure.COUNT);
        // Registered before the redraw listeners, so a scatter plot is drawn with number columns already picked.
        kindBox.valueProperty().addListener((obs, was, kind) -> {
            if (kind == Kind.SCATTER) {
                pickNumbersForScatter();
            }
        });
        for (ComboBox<?> box : List.of(kindBox, xBox, measureBox, yBox)) {
            box.getStyleClass().add("chart-choice");
            box.valueProperty().addListener((obs, was, now) -> {
                updateChoiceVisibility();
                if (!updatingChoices) {
                    drawChart();
                }
            });
        }
        xBox.setPrefWidth(170);
        yBox.setPrefWidth(170);
        xLabel.getStyleClass().add("home-muted");
        yLabel.getStyleClass().add("home-muted");

        HBox controls = new HBox(10, kindBox, xLabel, xBox, measureBox, yLabel, yBox);
        controls.setAlignment(Pos.CENTER_LEFT);
        updateChoiceVisibility();

        chartHolder.getStyleClass().add("chart-holder");
        VBox.setVgrow(chartHolder, Priority.ALWAYS);
        chartSummary.getStyleClass().add("home-muted");
        chartSummary.setWrapText(true);

        VBox pane = new VBox(10, controls, chartHolder, chartSummary);
        pane.getStyleClass().add("result-pane");
        return pane;
    }

    private void updateChoiceVisibility() {
        boolean scatter = kindBox.getValue() == Kind.SCATTER;
        xLabel.setText(scatter ? "X axis" : "by");
        yLabel.setText("Y axis");
        Views.show(measureBox, !scatter);
        Views.show(yLabel, scatter);
        Views.show(yBox, scatter || measureBox.getValue() != Measure.COUNT);
        if (kindBox.getParent() instanceof HBox controls) {
            controls.getChildren().setAll(scatter
                    ? List.of(kindBox, xLabel, xBox, yLabel, yBox)
                    : List.of(kindBox, measureBox, yLabel, yBox, xLabel, xBox));
        }
    }

    private Label buildStatusBar() {
        status.getStyleClass().add("status-bar");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        return status;
    }

    private void showEmptyState(String message, Button... actions) {
        emptyMessage.setText(message);
        emptyActions.getChildren().setAll(actions);
        Views.show(emptyActions, actions.length > 0);
        Views.show(emptyState, true);
        Views.show(workArea, false);
    }

    private void showWorkArea() {
        Views.show(emptyState, false);
        Views.show(workArea, true);
    }

    // ---- datasets ----

    private void refreshDatasets() {
        work.run(Datasets::list,
                datasets -> {
                    List<Dataset> items = new ArrayList<>(datasets);
                    items.add(new Dataset(SampleDatabase.DEFAULT_FILE, SAMPLE_NAME, null));
                    datasetList.getItems().setAll(items);

                    if (openDataset != null && items.contains(openDataset)) {
                        datasetList.getSelectionModel().select(openDataset);
                        setStatus(openedMessage(openDataset.name()), null);
                    } else {
                        if (openDataset != null) {
                            setStatus(openDataset.name() + " was removed by an admin.", "status-error");
                        } else {
                            setStatus(plural(datasets.size(), "dataset") + " available", null);
                        }
                        closeDataset();
                        showStartState(datasets.isEmpty());
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not list datasets", error);
                    setStatus("Couldn't read the list of datasets in " + DataImporter.DEFAULT_DIRECTORY + ".",
                            "status-error");
                });
    }

    private void showStartState(boolean noImports) {
        if (noImports) {
            Button sample = new Button("Open the sample");
            sample.getStyleClass().add("primary-button");
            sample.setOnAction(e -> datasetList.getSelectionModel().select(datasetList.getItems().getLast()));
            showEmptyState("No datasets have been imported yet. An admin adds them by dropping CSV or JSON "
                    + "files on the Database Manager. Until then, practise on the sample: a small shop with "
                    + "customers, products and orders.", sample);
        } else {
            showEmptyState("Pick a dataset on the left to filter, sort and chart it.");
        }
    }

    private void closeDataset() {
        work.cancel();
        chartWork.cancel();
        openDataset = null;
        browser = null;
        tableColumns = Map.of();
        foreignKeys = List.of();
        request = null;
        lastQuery = null;
        lastResult = null;
        datasetList.getSelectionModel().clearSelection();
        schemaTree.getRoot().getChildren().clear();
        Views.show(tablesCaption, false);
        Views.show(treeHint, false);
    }

    private void openDataset(Dataset dataset) {
        setStatus("Opening " + dataset.name() + "…", null);
        boolean sample = dataset.file().equals(SampleDatabase.DEFAULT_FILE);
        work.run(() -> {
                    Path file = sample ? SampleDatabase.ensure() : dataset.file();
                    DatabaseBrowser candidate = DatabaseBrowser.sqlite(file);
                    Map<String, List<String>> tables = new LinkedHashMap<>();
                    for (TableRef table : candidate.listTables()) {
                        tables.put(table.name(), candidate.listColumns(table));
                    }
                    return new Contents(candidate, tables, candidate.listForeignKeys());
                },
                contents -> {
                    openDataset = dataset;
                    browser = contents.browser();
                    tableColumns = contents.tables();
                    foreignKeys = contents.links();
                    request = null;
                    lastQuery = null;
                    lastResult = null;
                    fillSchemaTree(contents.tables());
                    headline.setText(dataset.name());
                    subtitle.setText(plural(contents.tables().size(), "table") + " · read-only");
                    showWorkArea();
                    if (contents.tables().isEmpty()) {
                        clearResults("This dataset has no tables.");
                        setStatus(openedMessage(dataset.name()), null);
                    } else {
                        showTable(contents.tables().keySet().iterator().next());
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not open " + dataset.name(), error);
                    setStatus(describeFailure("open " + dataset.name(), error), "status-error");
                    datasetList.getSelectionModel().select(openDataset);
                });
    }

    private void fillSchemaTree(Map<String, List<String>> tables) {
        List<TreeItem<SchemaItem>> items = new ArrayList<>();
        tables.forEach((table, columns) -> {
            TreeItem<SchemaItem> tableItem = new TreeItem<>(new SchemaItem(table, null));
            for (String column : columns) {
                tableItem.getChildren().add(new TreeItem<>(new SchemaItem(table, column)));
            }
            items.add(tableItem);
        });
        schemaTree.getRoot().getChildren().setAll(items);
        Views.show(tablesCaption, true);
        Views.show(treeHint, true);
    }

    // ---- queries ----

    // Every click on the controls builds a new Request and QueryBuilder writes the SQL, so users never see any.

    private void showTable(String table) {
        applyRequest(Request.of(table));
    }

    private void applyRequest(Request next) {
        request = next;
        refreshControls();
        runQuery();
    }

    private List<ForeignKey> linksFrom(String table) {
        return foreignKeys.stream().filter(key -> key.child().name().equals(table)).toList();
    }

    private void refreshControls() {
        updatingControls = true;
        try {
            List<ForeignKey> available = linksFrom(request.table());
            linksButton.getItems().clear();
            for (ForeignKey key : available) {
                CheckMenuItem item = new CheckMenuItem(key.parent().name() + " (by " + key.childColumn() + ")");
                item.setMnemonicParsing(false); // column names often have underscores
                item.setSelected(request.links().contains(key));
                item.setOnAction(e -> toggleLink(key, item.isSelected()));
                linksButton.getItems().add(item);
            }
            linksButton.setText(request.links().isEmpty()
                    ? "Add columns from…"
                    : "Columns from " + plural(request.links().size(), "linked table"));
            Views.show(linksButton, !available.isEmpty());

            List<String> columns = QueryBuilder.columns(request, tableColumns);
            String picked = filterColumn.getValue();
            filterColumn.getItems().setAll(columns);
            filterColumn.setValue(columns.contains(picked) ? picked : null);

            List<String> sortChoices = new ArrayList<>(List.of(NO_SORT));
            sortChoices.addAll(columns);
            sortColumn.getItems().setAll(sortChoices);
            sortColumn.setValue(request.sortColumn() == null ? NO_SORT : request.sortColumn());
            sortOrder.setValue(request.descending() ? DESCENDING : ASCENDING);
            Views.show(sortOrder, request.sortColumn() != null);

            filterChips.getChildren().clear();
            for (Filter filter : request.filters()) {
                Button chip = new Button(filter + "   ✕");
                chip.setMnemonicParsing(false);
                chip.getStyleClass().add("filter-chip");
                chip.setTooltip(new Tooltip("Remove this filter"));
                chip.setOnAction(e -> removeFilter(filter));
                filterChips.getChildren().add(chip);
            }
            Views.show(filterChips, !request.filters().isEmpty());
        } finally {
            updatingControls = false;
        }
        updateFilterInputs();
    }

    private void updateFilterInputs() {
        Op op = filterOp.getValue();
        boolean needsValue = op == null || op.needsValue();
        filterValue.setDisable(!needsValue);
        addFilter.setDisable(request == null || filterColumn.getValue() == null || op == null
                || (needsValue && filterValue.getText().isBlank()));
    }

    private void toggleLink(ForeignKey key, boolean selected) {
        List<ForeignKey> links = linksFrom(request.table()).stream()
                .filter(k -> k.equals(key) ? selected : request.links().contains(k))
                .toList();
        // Unlinking a table takes its columns away, so drop any filter or sort that used them.
        List<String> columns = QueryBuilder.columns(
                new Request(request.table(), links, List.of(), null, false), tableColumns);
        List<Filter> filters = request.filters().stream().filter(f -> columns.contains(f.column())).toList();
        String sort = columns.contains(request.sortColumn()) ? request.sortColumn() : null;
        applyRequest(new Request(request.table(), links, filters, sort, sort != null && request.descending()));
    }

    private void addFilter() {
        String column = filterColumn.getValue();
        Op op = filterOp.getValue();
        if (request == null || column == null || op == null || (op.needsValue() && filterValue.getText().isBlank())) {
            return;
        }
        List<Filter> filters = new ArrayList<>(request.filters());
        filters.add(new Filter(column, op, op.needsValue() ? filterValue.getText().strip() : ""));
        filterValue.clear();
        applyRequest(new Request(request.table(), request.links(), filters, request.sortColumn(), request.descending()));
    }

    private void removeFilter(Filter filter) {
        List<Filter> filters = new ArrayList<>(request.filters());
        filters.remove(filter);
        applyRequest(new Request(request.table(), request.links(), filters, request.sortColumn(), request.descending()));
    }

    private void applySort() {
        if (request == null || updatingControls) {
            return;
        }
        String column = sortColumn.getValue();
        String sort = column == null || column.equals(NO_SORT) ? null : column;
        applyRequest(new Request(request.table(), request.links(), request.filters(), sort,
                DESCENDING.equals(sortOrder.getValue())));
    }

    private void runQuery() {
        DatabaseBrowser source = browser;
        if (source == null || request == null) {
            return;
        }
        String sql = QueryBuilder.sql(request, tableColumns);
        setStatus("Loading rows…", null);
        chartWork.cancel();
        work.run(() -> source.query(sql, PREVIEW_LIMIT),
                result -> {
                    lastQuery = sql;
                    lastResult = result;
                    numericColumns = ChartMaker.numericColumns(result);
                    ResultTable.fill(tableView, result);
                    rowCount.setText(describeCount(result.rows().size(), result.totalRows())
                            + (result.rows().size() < result.totalRows() ? ". Charts use every row." : ""));
                    updateChartChoices(result.columns());
                    if (resultTabs.getSelectionModel().getSelectedItem() == chartTab) {
                        drawChart();
                    }
                    setStatus(openedMessage(openDataset.name()), null);
                },
                error -> {
                    if (!(error instanceof IllegalArgumentException)) { // a refused query isn't worth a stack trace
                        LOGGER.log(Level.INFO, "Query failed", error);
                    }
                    setStatus(describeFailure("show these rows", error), "status-error");
                });
    }

    private void clearResults(String message) {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        rowCount.setText(message);
        chartHolder.getChildren().clear();
        chartSummary.setText("");
    }

    // ---- charts ----

    private void updateChartChoices(List<String> columns) {
        updatingChoices = true;
        try {
            String x = xBox.getValue();
            String y = yBox.getValue();
            xBox.getItems().setAll(columns);
            yBox.getItems().setAll(numericColumns);

            if (x == null || !columns.contains(x)) {
                x = columns.stream().filter(c -> !numericColumns.contains(c)).findFirst()
                        .orElse(columns.isEmpty() ? null : columns.get(0));
            }
            if (y == null || !numericColumns.contains(y)) {
                y = numericColumns.stream().filter(c -> !c.equals(xBox.getValue())).findFirst()
                        .orElse(numericColumns.isEmpty() ? null : numericColumns.get(0));
            }
            xBox.setValue(x);
            yBox.setValue(y);
        } finally {
            updatingChoices = false;
        }
    }

    private void pickNumbersForScatter() {
        updatingChoices = true;
        try {
            if (!numericColumns.contains(xBox.getValue()) && !numericColumns.isEmpty()) {
                xBox.setValue(numericColumns.get(0));
            }
            if (!numericColumns.contains(yBox.getValue()) || Objects.equals(yBox.getValue(), xBox.getValue())) {
                numericColumns.stream().filter(c -> !c.equals(xBox.getValue())).findFirst().ifPresent(yBox::setValue);
            }
        } finally {
            updatingChoices = false;
        }
    }

    private void drawChart() {
        if (lastResult == null || browser == null
                || resultTabs.getSelectionModel().getSelectedItem() != chartTab) {
            return;
        }
        Spec spec = new Spec(kindBox.getValue(), xBox.getValue(), measureBox.getValue(), yBox.getValue());
        String problem = ChartMaker.problem(spec, lastResult.columns(), numericColumns);
        if (problem != null) {
            showChartMessage(problem);
            return;
        }

        DatabaseBrowser source = browser;
        String sql = ChartMaker.sql(lastQuery, spec);
        boolean numericX = numericColumns.contains(spec.x());
        chartSummary.setText("Drawing…");
        chartWork.run(() -> source.query(sql, ChartMaker.limit(spec.kind())),
                data -> {
                    chartHolder.getChildren().setAll(ChartMaker.build(spec, data, numericX));
                    chartSummary.setText(ChartMaker.describe(spec, data.rows().size(), data.totalRows()));
                },
                error -> {
                    LOGGER.log(Level.INFO, "Chart query failed", error);
                    showChartMessage(describeFailure("draw the chart", error));
                });
    }

    private void showChartMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("home-empty");
        label.setWrapText(true);
        chartHolder.getChildren().setAll(label);
        chartSummary.setText("");
    }

    private void setStatus(String text, String styleClass) {
        Views.setStatus(status, text, styleClass);
    }

    // ---- wording (static, so tests can check it without a window) ----

    static String openedMessage(String datasetName) {
        return "Viewing " + datasetName + " (read-only)";
    }

    static String starterQuery(String table) {
        return "SELECT * FROM " + sqlName(table);
    }

    static String sqlName(String name) {
        boolean plain = name.matches("[A-Za-z_][A-Za-z0-9_]*")
                && !SQL_KEYWORDS.contains(name.toUpperCase(Locale.ROOT));
        return plain ? name : DatabaseBrowser.quoteIdentifier(name, "\"");
    }

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "ADD", "ALL", "ALTER", "AND", "AS", "ASC", "BETWEEN", "BY", "CASE", "CAST", "CHECK", "COLLATE",
            "COLUMN", "CONSTRAINT", "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END", "ESCAPE", "EXCEPT", "EXISTS", "FILTER",
            "FOREIGN", "FROM", "FULL", "GLOB", "GROUP", "GROUPS", "HAVING", "IN", "INDEX", "INNER", "INSERT",
            "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT", "MATCH", "NATURAL", "NOT",
            "NOTNULL", "NULL", "OFFSET", "ON", "OR", "ORDER", "OUTER", "OVER", "PRIMARY", "RANGE", "REFERENCES",
            "REGEXP", "REPLACE", "RIGHT", "ROW", "ROWS", "SELECT", "SET", "TABLE", "THEN", "TO", "TRANSACTION",
            "UNION", "UNIQUE", "UPDATE", "USING", "VALUES", "VIEW", "WHEN", "WHERE", "WINDOW", "WITH");
}
