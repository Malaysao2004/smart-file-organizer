package com.smartorganizer.controller;

import com.smartorganizer.model.Category;
import com.smartorganizer.model.DuplicateGroup;
import com.smartorganizer.model.DuplicateRow;
import com.smartorganizer.model.FileInfo;
import com.smartorganizer.model.ScanResult;
import com.smartorganizer.service.DuplicateFinder;
import com.smartorganizer.service.FileDeleter;
import com.smartorganizer.service.FileOrganizer;
import com.smartorganizer.service.FileScanner;
import com.smartorganizer.util.FormatUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String CSS =
            Objects.requireNonNull(MainController.class.getResource("/css/style.css")).toExternalForm();

    private final Stage stage;
    private final FileScanner scanner = new FileScanner();
    private final DuplicateFinder duplicateFinder = new DuplicateFinder();
    private final FileOrganizer organizer = new FileOrganizer();
    private final FileDeleter deleter = new FileDeleter();

    // ---- state ----
    private final ObservableList<FileInfo> allFiles = FXCollections.observableArrayList();
    private final FilteredList<FileInfo> filteredFiles = new FilteredList<>(allFiles, f -> true);
    private final ObservableList<DuplicateRow> duplicateRows = FXCollections.observableArrayList();
    private List<DuplicateGroup> duplicateGroups = List.of();
    private boolean duplicatesChecked = false;
    private Path rootFolder;
    private int foldersScanned;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty hasFolder = new SimpleBooleanProperty(false);

    // ---- UI ----
    private final BorderPane root = new BorderPane();
    private final List<Node> pages = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();

    private final Button selectBtn = styled("Select Folder", "btn-primary");
    private final Button rescanBtn = styled("Rescan", "btn-secondary");
    private final Button organizeBtn = styled("Organize Files", "btn-success");
    private final Button findDupBtn = styled("Find Duplicates", "btn-primary");
    private final Button deleteBtn = styled("Delete Selected", "btn-danger");

    private final Label pathLabel = new Label("No folder selected");
    private final Label statusLabel = new Label("Ready");
    private final Label countLabel = new Label("Files scanned: 0");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final TextArea logArea = new TextArea();

    private final Label totalFilesValue = new Label("0");
    private final Label foldersValue = new Label("0");
    private final Label dupValue = new Label("0");
    private final Label dupSub = new Label("Not checked yet");
    private final Label sizeValue = new Label("0 B");
    private final Map<Category, Label> categoryLabels = new EnumMap<>(Category.class);
    private final Label emptyState =
            new Label("No folder scanned yet.\nClick \"Select Folder\" to get started.");

    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final Label resultLabel = new Label("Showing 0 of 0 files");
    private final Label dupInfoLabel = new Label("Click \"Find Duplicates\" to start.");

    public MainController(Stage stage) {
        this.stage = stage;
        buildUi();
        showPage(0);
        log("Application started.");
    }

    public Parent getRoot() {
        return root;
    }

    // =====================================================================
    //  UI construction
    // =====================================================================

    private void buildUi() {
        root.getStyleClass().add("app-root");
        BorderPane content = new BorderPane();
        content.setTop(buildHeader());
        StackPane stack = new StackPane(buildDashboardPage(), buildFilesPage(),
                buildDuplicatesPage(), buildLogPage());
        pages.addAll(stack.getChildren());
        content.setCenter(stack);
        content.setBottom(buildStatusBar());
        root.setLeft(buildSidebar());
        root.setCenter(content);
    }

    private VBox buildSidebar() {
        Label title = new Label("Smart File\nOrganizer");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("& Duplicate Finder");
        subtitle.getStyleClass().add("app-subtitle");
        VBox box = new VBox(6, title, subtitle, new Region());
        ((Region) box.getChildren().get(2)).setMinHeight(18);

        String[] names = {"Dashboard", "Files", "Duplicates", "Activity Log"};
        for (int i = 0; i < names.length; i++) {
            Button b = new Button(names[i]);
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            final int index = i;
            b.setOnAction(e -> showPage(index));
            navButtons.add(b);
            box.getChildren().add(b);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label version = new Label("v1.0.0");
        version.getStyleClass().add("version-label");
        box.getChildren().addAll(spacer, version);
        box.getStyleClass().add("sidebar");
        box.setPrefWidth(210);
        return box;
    }

    private HBox buildHeader() {
        pathLabel.getStyleClass().add("path-label");
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        selectBtn.setOnAction(e -> chooseFolder());
        rescanBtn.setOnAction(e -> startScan());
        organizeBtn.setOnAction(e -> organizeFiles());
        selectBtn.disableProperty().bind(busy);
        rescanBtn.disableProperty().bind(busy.or(hasFolder.not()));
        organizeBtn.disableProperty().bind(busy.or(Bindings.isEmpty(allFiles)));

        HBox header = new HBox(10, selectBtn, rescanBtn, pathLabel, organizeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private HBox buildStatusBar() {
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        countLabel.getStyleClass().add("muted");
        progressBar.setPrefWidth(220);
        HBox bar = new HBox(16, statusLabel, countLabel, progressBar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private VBox buildDashboardPage() {
        HBox cards = new HBox(16,
                card("TOTAL FILES", totalFilesValue, null),
                card("FOLDERS SCANNED", foldersValue, null),
                card("DUPLICATE FILES", dupValue, dupSub),
                card("TOTAL STORAGE", sizeValue, null));

        FlowPane cats = new FlowPane(12, 12);
        for (Category c : Category.values()) {
            Label name = new Label(c.getDisplayName().toUpperCase(Locale.ROOT));
            name.getStyleClass().add("card-title");
            Label count = new Label("0");
            count.getStyleClass().add("mini-value");
            categoryLabels.put(c, count);
            VBox mini = new VBox(4, name, count);
            mini.getStyleClass().add("mini-card");
            mini.setPrefWidth(150);
            cats.getChildren().add(mini);
        }

        emptyState.getStyleClass().add("empty-state");
        emptyState.setMaxWidth(Double.MAX_VALUE);
        emptyState.visibleProperty().bind(Bindings.isEmpty(allFiles));
        emptyState.managedProperty().bind(emptyState.visibleProperty());

        VBox page = new VBox(20, pageTitle("Dashboard"), cards, sectionTitle("Files by category"),
                cats, emptyState);
        page.setPadding(new Insets(24));
        return page;
    }

    private VBox buildFilesPage() {
        searchField.setPromptText("Search by file name...");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        categoryBox.getItems().add("All");
        for (Category c : Category.values()) {
            categoryBox.getItems().add(c.getDisplayName());
        }
        categoryBox.setValue("All");
        resultLabel.getStyleClass().add("muted");

        Runnable applyFilter = () -> {
            String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String cat = categoryBox.getValue();
            filteredFiles.setPredicate(f ->
                    (q.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(q))
                            && ("All".equals(cat) || f.category().getDisplayName().equals(cat)));
        };
        searchField.textProperty().addListener((o, a, b) -> applyFilter.run());
        categoryBox.valueProperty().addListener((o, a, b) -> applyFilter.run());
        filteredFiles.addListener((ListChangeListener<FileInfo>) c ->
                resultLabel.setText("Showing " + filteredFiles.size() + " of " + allFiles.size() + " files"));

        HBox bar = new HBox(12, searchField, categoryBox, resultLabel);
        bar.setAlignment(Pos.CENTER_LEFT);

        TableView<FileInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No files to show. Select a folder and scan it."));

        TableColumn<FileInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        nameCol.setPrefWidth(260);

        TableColumn<FileInfo, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().category().getDisplayName()));
        catCol.setPrefWidth(110);

        TableColumn<FileInfo, Number> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new ReadOnlyLongWrapper(c.getValue().size()));
        sizeCol.setCellFactory(col -> new TableCell<FileInfo, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : FormatUtil.formatSize(value.longValue()));
            }
        });
        sizeCol.setPrefWidth(90);

        TableColumn<FileInfo, LocalDateTime> modCol = new TableColumn<>("Modified");
        modCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().lastModified()));
        modCol.setCellFactory(col -> new TableCell<FileInfo, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : FormatUtil.formatDate(value));
            }
        });
        modCol.setPrefWidth(140);

        TableColumn<FileInfo, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path().toString()));
        pathCol.setPrefWidth(400);

        table.getColumns().add(nameCol);
        table.getColumns().add(catCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(modCol);
        table.getColumns().add(pathCol);

        SortedList<FileInfo> sorted = new SortedList<>(filteredFiles);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(16, pageTitle("Files"), bar, table);
        page.setPadding(new Insets(24));
        return page;
    }

    private VBox buildDuplicatesPage() {
        findDupBtn.setOnAction(e -> findDuplicates());
        deleteBtn.setOnAction(e -> deleteSelected());
        findDupBtn.disableProperty().bind(busy.or(Bindings.isEmpty(allFiles)));
        deleteBtn.disableProperty().bind(busy.or(Bindings.isEmpty(duplicateRows)));
        dupInfoLabel.getStyleClass().add("muted");

        HBox bar = new HBox(12, findDupBtn, deleteBtn, dupInfoLabel);
        bar.setAlignment(Pos.CENTER_LEFT);

        TableView<DuplicateRow> table = new TableView<>(duplicateRows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No duplicates to show. Scan a folder, then click \"Find Duplicates\"."));
        table.setRowFactory(tv -> new TableRow<DuplicateRow>() {
            @Override
            protected void updateItem(DuplicateRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("group-odd", "group-even");
                if (!empty && item != null) {
                    getStyleClass().add(item.getGroupNumber() % 2 == 0 ? "group-even" : "group-odd");
                }
            }
        });

        TableColumn<DuplicateRow, Boolean> selCol = new TableColumn<>("Delete?");
        selCol.setCellValueFactory(c -> c.getValue().selectedProperty());
        selCol.setCellFactory(CheckBoxTableCell.forTableColumn(selCol));
        selCol.setEditable(true);
        selCol.setPrefWidth(70);

        TableColumn<DuplicateRow, String> groupCol = new TableColumn<>("Group");
        groupCol.setCellValueFactory(c -> new ReadOnlyStringWrapper("#" + c.getValue().getGroupNumber()));
        groupCol.setPrefWidth(70);

        TableColumn<DuplicateRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getFile().name()));
        nameCol.setPrefWidth(240);

        TableColumn<DuplicateRow, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(FormatUtil.formatSize(c.getValue().getFile().size())));
        sizeCol.setPrefWidth(90);

        TableColumn<DuplicateRow, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getFile().path().toString()));
        pathCol.setPrefWidth(440);

        table.getColumns().add(selCol);
        table.getColumns().add(groupCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(pathCol);
        table.getColumns().forEach(c -> c.setSortable(false));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(16, pageTitle("Duplicate Files"), bar, table);
        page.setPadding(new Insets(24));
        return page;
    }

    private VBox buildLogPage() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox page = new VBox(16, pageTitle("Activity Log"), logArea);
        page.setPadding(new Insets(24));
        return page;
    }

    private void showPage(int index) {
        for (int i = 0; i < pages.size(); i++) {
            boolean on = i == index;
            pages.get(i).setVisible(on);
            pages.get(i).setManaged(on);
            Button b = navButtons.get(i);
            b.getStyleClass().remove("active");
            if (on) {
                b.getStyleClass().add("active");
            }
        }
    }

    // ---- small UI helpers ----

    private static Button styled(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("app-button", styleClass);
        return b;
    }

    private static Label pageTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-title");
        return l;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private static VBox card(String title, Label value, Label sub) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        value.getStyleClass().add("card-value");
        VBox box = new VBox(6, t, value);
        if (sub != null) {
            sub.getStyleClass().add("card-sub");
            box.getChildren().add(sub);
        }
        box.getStyleClass().add("card");
        box.setPrefWidth(200);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select a folder to scan");
        if (rootFolder != null && Files.isDirectory(rootFolder)) {
            chooser.setInitialDirectory(rootFolder.toFile());
        }
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        Path path = dir.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            log("ERROR: Invalid or inaccessible folder: " + path);
            showError("Invalid folder", "This folder does not exist or cannot be read:\n" + path);
            return;
        }
        rootFolder = path;
        hasFolder.set(true);
        pathLabel.setText(path.toString());
        log("Folder selected: " + path);
        startScan();
    }

    private void startScan() {
        if (rootFolder == null) {
            return;
        }
        if (!Files.isDirectory(rootFolder) || !Files.isReadable(rootFolder)) {
            log("ERROR: Folder no longer accessible: " + rootFolder);
            showError("Invalid folder", "Folder is not accessible:\n" + rootFolder);
            return;
        }
        final Path folder = rootFolder;
        log("Scan started: " + folder);
        allFiles.clear();
        duplicateRows.clear();
        duplicateGroups = List.of();
        duplicatesChecked = false;
        updateDashboard();

        Task<ScanResult> task = new Task<>() {
            @Override
            protected ScanResult call() throws Exception {
                updateProgress(-1, 1);
                updateMessage("Scanning " + folder + " ...");
                return scanner.scan(folder, n -> {
                    updateMessage("Scanning... " + n + " files found");
                    if (n % 50 == 0) {
                        Platform.runLater(() -> countLabel.setText("Files scanned: " + n));
                    }
                }, MainController.this::log, this::isCancelled);
            }
        };
        runTask(task, this::onScanDone, "Scan failed");
    }

    private void onScanDone(ScanResult result) {
        allFiles.setAll(result.files());
        foldersScanned = result.foldersScanned();
        int n = result.files().size();
        countLabel.setText("Files scanned: " + n);
        log("Scan completed: " + n + " files in " + foldersScanned + " folders"
                + (result.skippedCount() > 0 ? " (" + result.skippedCount() + " skipped)" : ""));
        statusLabel.setText("Scan completed");
        updateDashboard();
        if (n == 0) {
            if (result.skippedCount() > 0) {
                showError("Cannot read folder", "No files could be read (permission denied?). See Activity Log.");
            } else {
                log("Folder is empty.");
                info("Empty folder", "No files were found in the selected folder.");
            }
        }
    }

    private void findDuplicates() {
        if (allFiles.isEmpty()) {
            info("Nothing to check", "Scan a folder first.");
            return;
        }
        log("Duplicate detection started.");
        final List<FileInfo> snapshot = List.copyOf(allFiles);
        Task<List<DuplicateGroup>> task = new Task<>() {
            @Override
            protected List<DuplicateGroup> call() {
                updateProgress(-1, 1);
                updateMessage("Grouping files by size...");
                return duplicateFinder.find(snapshot, p -> {
                    updateProgress(p, 1.0);
                    updateMessage("Hashing (SHA-256)... " + (int) (p * 100) + "%");
                }, MainController.this::log, this::isCancelled);
            }
        };
        runTask(task, groups -> {
            showDuplicates(groups);
            log("Duplicate detection completed: " + groups.size() + " group(s) found.");
            statusLabel.setText("Duplicate detection completed");
            showPage(2);
        }, "Duplicate detection failed");
    }

    private void showDuplicates(List<DuplicateGroup> groups) {
        duplicateGroups = groups;
        duplicatesChecked = true;
        List<DuplicateRow> rows = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            for (FileInfo f : groups.get(i).files()) {
                rows.add(new DuplicateRow(i + 1, f));
            }
        }
        duplicateRows.setAll(rows);
        int extras = groups.stream().mapToInt(g -> g.files().size() - 1).sum();
        long wasted = groups.stream().mapToLong(DuplicateGroup::wastedBytes).sum();
        dupInfoLabel.setText(groups.isEmpty()
                ? "No duplicate files found."
                : groups.size() + " groups, " + extras + " extra copies, "
                + FormatUtil.formatSize(wasted) + " reclaimable. Tick files to delete (keep at least one per group).");
        updateDashboard();
    }

    private void deleteSelected() {
        List<DuplicateRow> selected = duplicateRows.stream().filter(DuplicateRow::isSelected).toList();
        if (selected.isEmpty()) {
            info("Nothing selected", "Tick the files you want to delete first.");
            return;
        }
        long bytes = selected.stream().mapToLong(r -> r.getFile().size()).sum();
        Map<Integer, Long> perGroup = selected.stream()
                .collect(Collectors.groupingBy(DuplicateRow::getGroupNumber, Collectors.counting()));
        int fullGroups = 0;
        for (int i = 0; i < duplicateGroups.size(); i++) {
            if (perGroup.getOrDefault(i + 1, 0L) == duplicateGroups.get(i).files().size()) {
                fullGroups++;
            }
        }
        String msg = "Delete " + selected.size() + " selected file(s) (" + FormatUtil.formatSize(bytes)
                + ")?\n\nFiles are deleted PERMANENTLY (not moved to Recycle Bin).";
        if (fullGroups > 0) {
            msg += "\n\nWARNING: in " + fullGroups + " group(s) you selected ALL copies. No copy will remain!";
        }
        if (!confirm("Confirm delete", msg, "Delete")) {
            return;
        }
        final List<FileInfo> files = selected.stream().map(DuplicateRow::getFile).toList();
        Task<FileDeleter.Result> task = new Task<>() {
            @Override
            protected FileDeleter.Result call() {
                updateProgress(-1, 1);
                updateMessage("Deleting " + files.size() + " file(s)...");
                return deleter.delete(files, MainController.this::log);
            }
        };
        runTask(task, r -> {
            Set<Path> deleted = new HashSet<>(r.deleted());
            allFiles.removeIf(f -> deleted.contains(f.path()));
            List<DuplicateGroup> remaining = new ArrayList<>();
            for (DuplicateGroup g : duplicateGroups) {
                List<FileInfo> left = g.files().stream().filter(f -> !deleted.contains(f.path())).toList();
                if (left.size() > 1) {
                    remaining.add(new DuplicateGroup(g.hash(), left));
                }
            }
            showDuplicates(remaining);
            log("Delete completed: " + r.deleted().size() + " deleted ("
                    + FormatUtil.formatSize(r.freedBytes()) + " freed), " + r.failed() + " failed.");
            statusLabel.setText("Delete completed");
            if (r.failed() > 0) {
                showError("Some files were not deleted",
                        r.failed() + " file(s) could not be deleted. See Activity Log.");
            }
        }, "Delete failed");
    }

    private void organizeFiles() {
        if (allFiles.isEmpty() || rootFolder == null) {
            info("Nothing to organize", "Scan a folder first.");
            return;
        }
        final Path folder = rootFolder;
        int toMove = organizer.countToMove(folder, allFiles);
        if (toMove == 0) {
            info("Nothing to organize", "All files are already inside their category folders.");
            return;
        }
        String msg = "Move " + toMove + " of " + allFiles.size() + " files into category folders inside:\n"
                + folder + "\n\n(Images, Documents, Videos, Music, Archives, Applications, Others)\n"
                + "Name conflicts get a suffix like \"file (1).txt\".";
        if (!confirm("Organize files", msg, "Move Files")) {
            return;
        }
        log("Organization started: " + toMove + " file(s) to move.");
        final List<FileInfo> snapshot = List.copyOf(allFiles);
        Task<FileOrganizer.Result> task = new Task<>() {
            @Override
            protected FileOrganizer.Result call() {
                updateMessage("Organizing files...");
                return organizer.organize(folder, snapshot, MainController.this::log,
                        p -> updateProgress(p, 1.0), this::isCancelled);
            }
        };
        runTask(task, r -> {
            log("Organization completed: moved " + r.moved() + ", skipped " + r.skipped()
                    + ", failed " + r.failed() + ".");
            if (r.failed() > 0) {
                showError("Some files were not moved",
                        r.failed() + " file(s) could not be moved. See Activity Log.");
            }
            startScan();
        }, "Organization failed");
    }

    // =====================================================================
    //  Background task runner, dashboard, log, dialogs
    // =====================================================================

    private <T> void runTask(Task<T> task, java.util.function.Consumer<T> onSuccess, String failureText) {
        busy.set(true);
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            finishTask();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            finishTask();
            Throwable ex = task.getException();
            String message = ex == null ? "Unknown error" : String.valueOf(ex.getMessage());
            log("ERROR: " + failureText + ": " + message);
            showError(failureText, message);
        });
        Thread worker = new Thread(task, "sfo-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishTask() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        statusLabel.setText("Ready");
        busy.set(false);
    }

    private void updateDashboard() {
        totalFilesValue.setText(String.valueOf(allFiles.size()));
        foldersValue.setText(String.valueOf(foldersScanned));
        sizeValue.setText(FormatUtil.formatSize(allFiles.stream().mapToLong(FileInfo::size).sum()));
        int extras = duplicateGroups.stream().mapToInt(g -> g.files().size() - 1).sum();
        long wasted = duplicateGroups.stream().mapToLong(DuplicateGroup::wastedBytes).sum();
        dupValue.setText(String.valueOf(extras));
        dupSub.setText(!duplicatesChecked ? "Not checked yet"
                : duplicateGroups.size() + " groups, " + FormatUtil.formatSize(wasted) + " wasted");
        Map<Category, Long> counts = allFiles.stream()
                .collect(Collectors.groupingBy(FileInfo::category, Collectors.counting()));
        for (Category c : Category.values()) {
            categoryLabels.get(c).setText(String.valueOf(counts.getOrDefault(c, 0L)));
        }
        resultLabel.setText("Showing " + filteredFiles.size() + " of " + allFiles.size() + " files");
    }

    /** Thread-safe: can be called from background threads. */
    private void log(String message) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + message + System.lineSeparator();
        Platform.runLater(() -> logArea.appendText(line));
    }

    private void info(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(CSS);
        alert.showAndWait();
    }

    private boolean confirm(String title, String message, String okLabel) {
        ButtonType ok = new ButtonType(okLabel, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(CSS);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ok;
    }
}