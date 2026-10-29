package dev.frtzhahn.micro8.ui;

import dev.frtzhahn.micro8.asm.AssembledProgram;
import dev.frtzhahn.micro8.asm.Assembler;
import dev.frtzhahn.micro8.asm.SymbolEntry;
import dev.frtzhahn.micro8.asm.SyntaxError;
import dev.frtzhahn.micro8.cpu.ControlSignals;
import dev.frtzhahn.micro8.cpu.ControlSignals.SignalSpec;
import dev.frtzhahn.micro8.cpu.CpuClockRunner;
import dev.frtzhahn.micro8.cpu.CpuCore;
import dev.frtzhahn.micro8.cpu.CpuSnapshot;
import dev.frtzhahn.micro8.cpu.TraceRow;
import dev.frtzhahn.micro8.db.BusContentionException;
import dev.frtzhahn.micro8.db.DatabaseManager;
import dev.frtzhahn.micro8.db.InstructionDef;
import dev.frtzhahn.micro8.db.IsaRepository;
import dev.frtzhahn.micro8.db.SnapshotRepository;
import dev.frtzhahn.micro8.db.WorkspaceSnapshot;
import dev.frtzhahn.micro8.lab.LabChallenge;
import dev.frtzhahn.micro8.lab.LabGradeReport;
import dev.frtzhahn.micro8.lab.LabGrader;
import dev.frtzhahn.micro8.lab.LabRepository;
import dev.frtzhahn.micro8.lab.LabTestResult;
import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainWorkbenchController {

    private static final int MAX_TRACE_DRAIN_PER_FRAME = 64;
    private static final int MAX_TRACE_TABLE_ROWS = 512;

    private static final String DEFAULT_SAMPLE_PROGRAM = """
            ; MICRO-8 Sample Program: Add two unsigned bytes -> '*' (0x2A / 42)
            START:
                LDA NUM_A
                ADD NUM_B
                OUT
                HLT

                ORG 0xF0
            NUM_A:
                DB 24
            NUM_B:
                DB 18
            """;

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<COMMENT>;[^\\r\\n]*|//[^\\r\\n]*)"
                    + "|(?<LABEL>\\b[A-Za-z_][A-Za-z0-9_]*:)"
                    + "|(?<DIRECTIVE>\\b(?i:ORG|DB)\\b)"
                    + "|(?<NUMBER>\\b(?:0[xX][0-9A-Fa-f]+|0[bB][01]+|\\$[0-9A-Fa-f]+|\\d+)\\b)"
                    + "|(?<WORD>\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
    );

    @FXML
    private Button btnAssemble;

    @FXML
    private Button btnRun;

    @FXML
    private Button btnPause;

    @FXML
    private Button btnStepMicro;

    @FXML
    private Button btnStepMacro;

    @FXML
    private Button btnUndoMicro;

    @FXML
    private Button btnUndoMacro;

    @FXML
    private Button btnReset;

    @FXML
    private Slider sldClockHz;

    @FXML
    private Label lblClockHz;

    @FXML
    private Label lblCycleCount;

    @FXML
    private Button btnSnapshots;

    @FXML
    private Button btnHelpOnboarding;

    @FXML
    private StackPane paneCodeHost;

    @FXML
    private TableView<SymbolEntry> tblSymbolTable;

    @FXML
    private Label lblAsmDiagnostics;

    @FXML
    private StackPane paneSchematicHost;

    @FXML
    private Label lblBusValue;

    @FXML
    private Label lblPc;

    @FXML
    private Label lblRegA;

    @FXML
    private Label lblMar;

    @FXML
    private Label lblAlu;

    @FXML
    private Label lblFlags;

    @FXML
    private Label lblRegB;

    @FXML
    private Label lblIr;

    @FXML
    private SevenSegmentCanvas segOutHigh;

    @FXML
    private SevenSegmentCanvas segOutLow;

    @FXML
    private Label lblOutDec;

    @FXML
    private Label lblOutHex;

    @FXML
    private Label lblOutAscii;

    @FXML
    private StackPane paneBinaryLedsHost;

    @FXML
    private StackPane paneRamGridHost;

    @FXML
    private Label lblTState;

    @FXML
    private MenuButton btnSelectInstruction;

    @FXML
    private TextField txtOpcodeHex;

    @FXML
    private TextField txtMnemonic;

    @FXML
    private CheckBox chkHasOperand;

    @FXML
    private TextField txtInstrDesc;

    @FXML
    private ToggleButton btnFlagAll;

    @FXML
    private ToggleButton btnFlag0;

    @FXML
    private ToggleButton btnFlag1;

    @FXML
    private ToggleButton btnFlag2;

    @FXML
    private ToggleButton btnFlag3;

    @FXML
    private TableView<MicrocodeStepRow> tblMicrocode;

    @FXML
    private Label lblMicrocodeValidation;

    @FXML
    private Button btnSaveMicrocode;

    @FXML
    private Button btnResetDefaultIsa;

    @FXML
    private Button btnRefreshDatasheet;

    @FXML
    private WebView webDatasheet;

    @FXML
    private Button btnLoadLabStarter;

    @FXML
    private Button btnRunLabGrader;

    @FXML
    private TableView<LabChallenge> tblLabs;

    @FXML
    private Label lblLabReport;

    @FXML
    private TableView<LabTestResult> tblLabResults;

    @FXML
    private Button btnClearRam;

    @FXML
    private Button btnLoadHex;

    @FXML
    private TableView<TraceRow> tblTrace;

    @FXML
    private Button btnClearTrace;

    private CodeArea codeAsmEditor;
    private CpuSchematicView schematicView;
    private RamHexGridView ramGridView;
    private final Circle[] binaryLedCircles = new Circle[8];
    private final ObservableList<TraceRow> traceItems = FXCollections.observableArrayList();
    private final ObservableList<MicrocodeStepRow> microcodeRows = FXCollections.observableArrayList();
    private final ObservableList<LabChallenge> labItems = FXCollections.observableArrayList();
    private final ObservableList<LabTestResult> labResultItems = FXCollections.observableArrayList();
    private final int[][] workingMicrocode = new int[6][4];
    private AnimationTimer uiTimer;

    private DatabaseManager dbManager;
    private IsaRepository isaRepository;
    private SnapshotRepository snapshotRepository;
    private LabRepository labRepository;
    private LabGrader labGrader;
    private final CpuCore cpuCore = new CpuCore();
    private final CpuClockRunner clockRunner = new CpuClockRunner(cpuCore);
    private final Assembler assembler = new Assembler();

    private byte[] lastAssembledRam = new byte[256];
    private boolean programAssembled;
    private int[] addressToSourceLine = new int[256];
    private int highlightedParagraph = -1;

    @FXML
    public void initialize() {
        Theme.loadFonts();
        Arrays.fill(addressToSourceLine, -1);

        if (sldClockHz != null && lblClockHz != null) {
            sldClockHz.valueProperty().addListener((obs, oldVal, newVal) -> {
                int hz = Math.max(1, newVal.intValue());
                lblClockHz.setText(hz + " Hz");
                clockRunner.setFrequencyHz(hz);
            });
        }

        initSymbolTable();
        initCodeEditor();
        initBinaryLeds();
        initRamHexGrid();
        initTraceTable();
        initMicrocodeDesigner();
        initLabWorkbench();
        initSchematicView();
        initKeyboardShortcuts();
        startUiRefreshTimer();
        syncTransportControls();

        for (TableView<?> table : Arrays.asList(tblSymbolTable, tblTrace, tblLabs, tblLabResults)) {
            if (table != null) {
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            }
        }
    }

    private void initKeyboardShortcuts() {
        if (btnRun == null) {
            return;
        }
        if (btnRun.getScene() != null) {
            installKeyboardShortcuts(btnRun.getScene());
        }
        btnRun.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                installKeyboardShortcuts(newScene);
            }
        });
    }

    private void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                handleAssemble();
                event.consume();
            } else if (event.getCode() == KeyCode.F6) {
                handleStepMicro();
                event.consume();
            } else if (event.getCode() == KeyCode.F7) {
                handleStepMacro();
                event.consume();
            } else if (event.getCode() == KeyCode.F8) {
                if (clockRunner.isRunning()) {
                    handlePause();
                } else {
                    handleRun();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.F9) {
                handleUndoMicro();
                event.consume();
            } else if (event.getCode() == KeyCode.F10) {
                handleUndoMacro();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.R) {
                handleReset();
                event.consume();
            }
        });
    }

    private void initSymbolTable() {
        if (tblSymbolTable == null) {
            return;
        }
        TableColumn<SymbolEntry, String> colLabel = new TableColumn<>("Label");
        colLabel.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().label()));
        colLabel.setPrefWidth(150.0);

        TableColumn<SymbolEntry, String> colAddr = new TableColumn<>("Address");
        colAddr.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().addressHex()));
        colAddr.setPrefWidth(100.0);

        tblSymbolTable.getColumns().setAll(List.of(colLabel, colAddr));
    }

    private void initCodeEditor() {
        if (paneCodeHost == null) {
            return;
        }
        codeAsmEditor = new CodeArea();
        codeAsmEditor.getStyleClass().add("code-editor");
        // Inline style: RichTextFX paragraph graphics do not pick up descendant selectors from the scene stylesheet
        String lineNumberStyle = "-fx-text-fill: " + Theme.css(Theme.FAINT) + "; -fx-font-family: '" + Theme.MONO
                + "'; -fx-font-size: 13px; -fx-min-width: 40px; -fx-pref-width: 40px;"
                + " -fx-alignment: center-right; -fx-padding: 0 14px 0 0;";
        codeAsmEditor.setParagraphGraphicFactory(line -> {
            Label number = new Label(Integer.toString(line + 1));
            number.setStyle(lineNumberStyle);
            return number;
        });
        codeAsmEditor.textProperty().addListener((obs, oldText, newText) ->
                codeAsmEditor.setStyleSpans(0, computeHighlighting(newText))
        );
        codeAsmEditor.replaceText(DEFAULT_SAMPLE_PROGRAM);

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeAsmEditor);
        paneCodeHost.getChildren().setAll(scrollPane);
    }

    private void initBinaryLeds() {
        if (paneBinaryLedsHost == null) {
            return;
        }
        HBox strip = new HBox(10.0);
        strip.setAlignment(Pos.CENTER_LEFT);
        for (int bit = 7; bit >= 0; bit--) {
            Circle led = new Circle(6.0);
            led.getStyleClass().add("led");
            binaryLedCircles[bit] = led;

            Label bitLbl = new Label(Integer.toString(bit));
            bitLbl.getStyleClass().add("led__label");

            VBox col = new VBox(4.0, led, bitLbl);
            col.setAlignment(Pos.CENTER);
            strip.getChildren().add(col);
        }
        paneBinaryLedsHost.getChildren().setAll(strip);
    }

    private void initRamHexGrid() {
        if (paneRamGridHost == null) {
            return;
        }
        ramGridView = new RamHexGridView();
        ramGridView.setCellPokeHandler((addr, val) -> {
            if (clockRunner.isRunning()) {
                return;
            }
            CpuSnapshot snap;
            synchronized (cpuCore) {
                cpuCore.writeRam(addr, val);
                snap = cpuCore.createSnapshot();
            }
            renderSnapshot(snap);
        });
        paneRamGridHost.getChildren().setAll(ramGridView);
    }

    private void initTraceTable() {
        if (tblTrace == null) {
            return;
        }
        TableColumn<TraceRow, String> colCycle = new TableColumn<>("Cycle");
        colCycle.setCellValueFactory(c -> new SimpleStringProperty(String.format("%05d", c.getValue().cycleCount())));
        colCycle.setPrefWidth(72.0);

        TableColumn<TraceRow, String> colTState = new TableColumn<>("T");
        colTState.setCellValueFactory(c -> new SimpleStringProperty("T" + c.getValue().tState()));
        colTState.setPrefWidth(48.0);

        TableColumn<TraceRow, String> colPc = new TableColumn<>("PC");
        colPc.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().pc())));
        colPc.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colOp = new TableColumn<>("Instr");
        colOp.setCellValueFactory(c -> new SimpleStringProperty(
                hex2(c.getValue().opcode()) + " " + c.getValue().mnemonic()
        ));
        colOp.setPrefWidth(100.0);

        TableColumn<TraceRow, String> colSignals = new TableColumn<>("Signals");
        colSignals.setCellValueFactory(c -> new SimpleStringProperty(
                ControlSignals.formatActiveSignals(c.getValue().controlWord()).replace(" | ", " ")
        ));
        colSignals.setPrefWidth(150.0);

        TableColumn<TraceRow, String> colBus = new TableColumn<>("Bus");
        colBus.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().busValue())));
        colBus.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colRegA = new TableColumn<>("A");
        colRegA.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().regA())));
        colRegA.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colRegB = new TableColumn<>("B");
        colRegB.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().regB())));
        colRegB.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colMar = new TableColumn<>("MAR");
        colMar.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().mar())));
        colMar.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colFlags = new TableColumn<>("Flags");
        colFlags.setCellValueFactory(c -> new SimpleStringProperty(
                "Z" + (c.getValue().zeroFlag() ? 1 : 0) + " C" + (c.getValue().carryFlag() ? 1 : 0)
        ));
        colFlags.setPrefWidth(72.0);

        TableColumn<TraceRow, String> colOutHex = new TableColumn<>("Out");
        colOutHex.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().outHex()));
        colOutHex.setPrefWidth(64.0);

        TableColumn<TraceRow, String> colOutDec = new TableColumn<>("Dec");
        colOutDec.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().outDecimal()));
        colOutDec.setPrefWidth(56.0);

        TableColumn<TraceRow, String> colOutAsc = new TableColumn<>("ASCII");
        colOutAsc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().outAscii()));
        colOutAsc.setPrefWidth(56.0);

        tblTrace.getColumns().setAll(List.of(
                colCycle, colTState, colPc, colOp, colSignals, colBus, colRegA, colRegB, colMar, colFlags,
                colOutHex, colOutDec, colOutAsc
        ));
        tblTrace.setItems(traceItems);
    }

    private void initMicrocodeDesigner() {
        int fetchT0 = ControlSignals.CO | ControlSignals.MI;
        int fetchT1 = ControlSignals.RO | ControlSignals.II | ControlSignals.CE;
        int defaultT2 = ControlSignals.CE | ControlSignals.CLR;
        for (int flag = 0; flag < 4; flag++) {
            workingMicrocode[0][flag] = fetchT0;
            workingMicrocode[1][flag] = fetchT1;
            workingMicrocode[2][flag] = defaultT2;
            workingMicrocode[3][flag] = 0;
            workingMicrocode[4][flag] = 0;
            workingMicrocode[5][flag] = 0;
        }
        microcodeRows.clear();
        for (int t = 0; t < 6; t++) {
            microcodeRows.add(new MicrocodeStepRow(t, workingMicrocode[t][0]));
        }

        if (tblMicrocode == null) {
            return;
        }

        List<SignalSpec> driverSpecs = ControlSignals.ALL_SIGNALS.stream()
                .filter(SignalSpec::busDriver)
                .toList();
        List<SignalSpec> consumerSpecs = ControlSignals.ALL_SIGNALS.stream()
                .filter(s -> !s.busDriver())
                .toList();

        TableColumn<MicrocodeStepRow, String> colPhase = new TableColumn<>("Phase");
        colPhase.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTStateLabel()));
        colPhase.setPrefWidth(56.0);

        TableColumn<MicrocodeStepRow, String> colDrivers = new TableColumn<>("Bus driver (max 1)");
        colDrivers.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHexWord()));
        colDrivers.setCellFactory(col -> createSignalCheckboxCell(driverSpecs, true));
        colDrivers.setPrefWidth(250.0);

        TableColumn<MicrocodeStepRow, String> colConsumers = new TableColumn<>("Latch and control signals");
        colConsumers.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHexWord()));
        colConsumers.setCellFactory(col -> createSignalCheckboxCell(consumerSpecs, false));
        colConsumers.setPrefWidth(480.0);

        TableColumn<MicrocodeStepRow, String> colWord = new TableColumn<>("Control word");
        colWord.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHexWord()));
        colWord.setPrefWidth(105.0);

        tblMicrocode.getColumns().setAll(List.of(colPhase, colDrivers, colConsumers, colWord));
        tblMicrocode.setItems(microcodeRows);
        updateLiveMicrocodeValidation();
    }

    private TableCell<MicrocodeStepRow, String> createSignalCheckboxCell(List<SignalSpec> specs, boolean isDriverGroup) {
        return new TableCell<>() {
            private final HBox box = new HBox(6.0);
            private final List<CheckBox> checkBoxes = new ArrayList<>(specs.size());
            private boolean updating;

            {
                box.setAlignment(Pos.CENTER_LEFT);
                for (SignalSpec spec : specs) {
                    CheckBox cb = new CheckBox(spec.name());
                    cb.setOnAction(e -> {
                        if (updating) {
                            return;
                        }
                        MicrocodeStepRow row = getTableRow() == null ? null : getTableRow().getItem();
                        if (row == null) {
                            return;
                        }
                        row.setSignal(spec.mask(), cb.isSelected());
                        applyRowToWorkingMatrix(row.getTState(), row.getControlWord());
                        if (tblMicrocode != null) {
                            tblMicrocode.refresh();
                        }
                        updateLiveMicrocodeValidation();
                    });
                    checkBoxes.add(cb);
                    box.getChildren().add(cb);
                }
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                MicrocodeStepRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                updating = true;
                boolean contention = row.hasBusContention();
                for (int i = 0; i < specs.size(); i++) {
                    SignalSpec spec = specs.get(i);
                    CheckBox cb = checkBoxes.get(i);
                    boolean active = row.isSignalSet(spec.mask());
                    cb.setSelected(active);
                    Theme.toggleClass(cb, "signal--fault", active && isDriverGroup && contention);
                    Theme.toggleClass(cb, "signal--driver", active && isDriverGroup && !contention);
                    Theme.toggleClass(cb, "signal--consumer", active && !isDriverGroup);
                }
                updating = false;
                setGraphic(box);
            }
        };
    }

    private void applyRowToWorkingMatrix(int tState, int controlWord) {
        int selectedFlag = getSelectedFlagVariant();
        if (selectedFlag < 0) {
            for (int flag = 0; flag < 4; flag++) {
                workingMicrocode[tState][flag] = controlWord;
            }
        } else {
            workingMicrocode[tState][selectedFlag] = controlWord;
        }
    }

    private int getSelectedFlagVariant() {
        if (btnFlag0 != null && btnFlag0.isSelected()) {
            return 0;
        }
        if (btnFlag1 != null && btnFlag1.isSelected()) {
            return 1;
        }
        if (btnFlag2 != null && btnFlag2.isSelected()) {
            return 2;
        }
        if (btnFlag3 != null && btnFlag3.isSelected()) {
            return 3;
        }
        return -1;
    }

    @FXML
    public void handleFlagVariantChanged() {
        int flag = Math.max(0, getSelectedFlagVariant());
        for (int t = 0; t < 6; t++) {
            if (t < microcodeRows.size()) {
                microcodeRows.get(t).setControlWord(workingMicrocode[t][flag]);
            }
        }
        if (tblMicrocode != null) {
            tblMicrocode.refresh();
        }
        updateLiveMicrocodeValidation();
    }

    @FXML
    public void handleSaveMicrocode() {
        clockRunner.pause();
        if (isaRepository == null) {
            showMicrocodeValidationError("Database repository is not initialized.");
            return;
        }
        try {
            int opcode = parseHexOpcodeField();
            String mnemonic = txtMnemonic == null ? "" : txtMnemonic.getText().trim().toUpperCase(Locale.ROOT);
            if (mnemonic.isEmpty()) {
                throw new IllegalArgumentException("Mnemonic must not be empty.");
            }
            boolean hasOperand = chkHasOperand != null && chkHasOperand.isSelected();
            String desc = txtInstrDesc == null ? "" : txtInstrDesc.getText().trim();

            boolean builtin = false;
            for (InstructionDef existing : isaRepository.listInstructions()) {
                if ((existing.opcode() & 0xFF) == opcode) {
                    builtin = existing.builtin();
                    break;
                }
            }

            InstructionDef def = new InstructionDef(opcode, mnemonic, hasOperand, builtin, desc);
            isaRepository.saveInstructionMicrocode(def, workingMicrocode);

            reloadIsaDefinitionsIfAvailable();
            rebuildInstructionMenu();
            handleRefreshDatasheet();
            if (codeAsmEditor != null) {
                codeAsmEditor.setStyleSpans(0, computeHighlighting(codeAsmEditor.getText()));
            }
            String msg = "Saved instruction " + hex2(opcode) + " (" + mnemonic + ") and recompiled control store.";
            showMicrocodeValidationSuccess(msg);
            showDiagnosticsSuccess(msg);
        } catch (BusContentionException | IllegalArgumentException | SQLException ex) {
            showMicrocodeValidationError(ex.getMessage());
            showDiagnosticsError(ex.getMessage());
        }
    }

    @FXML
    public void handleResetDefaultIsa() {
        clockRunner.pause();
        if (isaRepository == null) {
            return;
        }
        try {
            isaRepository.resetToDefaultIsa();
            reloadIsaDefinitionsIfAvailable();
            rebuildInstructionMenu();
            loadInstructionIntoDesigner(0x20);
            handleRefreshDatasheet();
            if (codeAsmEditor != null) {
                codeAsmEditor.setStyleSpans(0, computeHighlighting(codeAsmEditor.getText()));
            }
            String msg = "Reset SQLite control store to default 11 built-in instructions.";
            showMicrocodeValidationSuccess(msg);
            showDiagnosticsSuccess(msg);
        } catch (SQLException | BusContentionException ex) {
            showMicrocodeValidationError("Reset failed: " + ex.getMessage());
            showDiagnosticsError("Reset failed: " + ex.getMessage());
        }
    }

    @FXML
    public void handleRefreshDatasheet() {
        if (isaRepository == null || webDatasheet == null) {
            return;
        }
        try {
            List<InstructionDef> instructions = isaRepository.listInstructions();
            int[][][] store = isaRepository.compileControlStore();
            String html = DatasheetGenerator.generateHtml(instructions, store);
            webDatasheet.getEngine().loadContent(html, "text/html");
        } catch (SQLException | BusContentionException ex) {
            showDiagnosticsError("Datasheet generation failed: " + ex.getMessage());
        }
    }

    private void rebuildInstructionMenu() {
        if (btnSelectInstruction == null || isaRepository == null) {
            return;
        }
        try {
            List<InstructionDef> instructions = isaRepository.listInstructions();
            btnSelectInstruction.getItems().clear();
            for (InstructionDef def : instructions) {
                String label = hex2(def.opcode()) + " - " + def.mnemonic()
                        + (def.builtin() ? " (Built-in)" : " (Custom)");
                MenuItem item = new MenuItem(label);
                item.setOnAction(e -> loadInstructionIntoDesigner(def.opcode()));
                btnSelectInstruction.getItems().add(item);
            }
            MenuItem customItem = new MenuItem("New Custom Instruction (0x60)");
            customItem.setOnAction(e -> prepareNewCustomInstruction(0x60));
            btnSelectInstruction.getItems().add(customItem);
        } catch (SQLException ex) {
            showDiagnosticsError("Failed to list instructions: " + ex.getMessage());
        }
    }

    private void loadInstructionIntoDesigner(int opcode) {
        if (isaRepository == null) {
            return;
        }
        int masked = opcode & 0xFF;
        try {
            InstructionDef matched = null;
            for (InstructionDef def : isaRepository.listInstructions()) {
                if ((def.opcode() & 0xFF) == masked) {
                    matched = def;
                    break;
                }
            }
            int[][] steps = isaRepository.loadInstructionSteps(masked);
            for (int t = 0; t < 6; t++) {
                System.arraycopy(steps[t], 0, workingMicrocode[t], 0, 4);
            }
            if (matched != null) {
                if (txtOpcodeHex != null) {
                    txtOpcodeHex.setText(hex2(matched.opcode()));
                }
                if (txtMnemonic != null) {
                    txtMnemonic.setText(matched.mnemonic());
                }
                if (chkHasOperand != null) {
                    chkHasOperand.setSelected(matched.hasOperand());
                }
                if (txtInstrDesc != null) {
                    txtInstrDesc.setText(matched.description());
                }
            }
            handleFlagVariantChanged();
        } catch (SQLException ex) {
            showMicrocodeValidationError("Failed to load instruction steps: " + ex.getMessage());
        }
    }

    private void prepareNewCustomInstruction(int defaultOpcode) {
        int fetchT0 = ControlSignals.CO | ControlSignals.MI;
        int fetchT1 = ControlSignals.RO | ControlSignals.II | ControlSignals.CE;
        int defaultT2 = ControlSignals.CE | ControlSignals.CLR;
        for (int flag = 0; flag < 4; flag++) {
            workingMicrocode[0][flag] = fetchT0;
            workingMicrocode[1][flag] = fetchT1;
            workingMicrocode[2][flag] = defaultT2;
            workingMicrocode[3][flag] = 0;
            workingMicrocode[4][flag] = 0;
            workingMicrocode[5][flag] = 0;
        }
        if (txtOpcodeHex != null) {
            txtOpcodeHex.setText(hex2(defaultOpcode));
        }
        if (txtMnemonic != null) {
            txtMnemonic.setText("DEC");
        }
        if (chkHasOperand != null) {
            chkHasOperand.setSelected(false);
        }
        if (txtInstrDesc != null) {
            txtInstrDesc.setText("Custom student microcoded instruction");
        }
        handleFlagVariantChanged();
    }

    private int parseHexOpcodeField() {
        String raw = txtOpcodeHex == null ? "" : txtOpcodeHex.getText().trim();
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        } else if (raw.startsWith("$")) {
            raw = raw.substring(1);
        }
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Opcode hex value must not be empty.");
        }
        try {
            int val = Integer.parseInt(raw, 16);
            if (val < 0 || val > 0xFF) {
                throw new IllegalArgumentException("Opcode must be between 0x00 and 0xFF.");
            }
            return val & 0xFF;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid hex opcode: '" + raw + "' (expected 0x00..0xFF).");
        }
    }

    private void updateLiveMicrocodeValidation() {
        for (int t = 0; t < 6; t++) {
            for (int flag = 0; flag < 4; flag++) {
                int word = workingMicrocode[t][flag];
                if (ControlSignals.hasBusContention(word)) {
                    List<String> drivers = ControlSignals.ALL_SIGNALS.stream()
                            .filter(s -> s.busDriver() && (word & s.mask()) != 0)
                            .map(SignalSpec::name)
                            .toList();
                    showMicrocodeValidationError(
                            "Bus contention in T" + t + " (flagState=" + flag + "): multiple bus drivers active ["
                                    + String.join(", ", drivers) + "]"
                    );
                    return;
                }
            }
        }
        showMicrocodeValidationSuccess("No bus contention");
    }

    private void showMicrocodeValidationSuccess(String msg) {
        setStatus(lblMicrocodeValidation, msg, false);
    }

    private void showMicrocodeValidationError(String msg) {
        setStatus(lblMicrocodeValidation, msg, true);
    }

    /** Shows a message on a status label, switching its icon and color between ok and error. */
    private static void setStatus(Label label, String msg, boolean error) {
        if (label == null) {
            return;
        }
        label.setText(msg);
        Theme.toggleClass(label, "status--error", error);
        if (label.getGraphic() instanceof FontIcon icon) {
            icon.setIconLiteral(error ? "fth-alert-circle" : "fth-check-circle");
        }
    }

    private void initSchematicView() {
        if (paneSchematicHost == null) {
            return;
        }
        schematicView = new CpuSchematicView();
        paneSchematicHost.getChildren().setAll(schematicView);
        synchronized (cpuCore) {
            renderSnapshot(cpuCore.createSnapshot());
        }
    }

    private void startUiRefreshTimer() {
        uiTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                CpuSnapshot snap = cpuCore.consumeLatestSnapshot();
                if (snap != null) {
                    renderSnapshot(snap);
                }
                drainTraceQueueBounded();
                syncTransportControls();
            }
        };
        uiTimer.start();
    }

    private void drainTraceQueueBounded() {
        int drained = 0;
        while (drained < MAX_TRACE_DRAIN_PER_FRAME) {
            TraceRow row = cpuCore.getTraceQueue().poll();
            if (row == null) {
                break;
            }
            traceItems.add(row);
            drained++;
        }
        if (drained > 0) {
            if (traceItems.size() > MAX_TRACE_TABLE_ROWS) {
                traceItems.remove(0, traceItems.size() - MAX_TRACE_TABLE_ROWS);
            }
            if (tblTrace != null && !traceItems.isEmpty()) {
                tblTrace.scrollTo(traceItems.size() - 1);
                tblTrace.getSelectionModel().selectLast();
            }
        }
    }

    @FXML
    public void handleAssemble() {
        clockRunner.pause();
        if (codeAsmEditor == null) {
            return;
        }
        reloadIsaDefinitionsIfAvailable();

        AssembledProgram program = assembler.assemble(codeAsmEditor.getText());
        if (!program.isSuccess()) {
            SyntaxError first = program.errors().getFirst();
            showDiagnosticsError(first.format() + " (" + program.errors().size() + " error(s))");
            highlightEditorLine(first.lineNumber());
            syncTransportControls();
            return;
        }

        this.addressToSourceLine = program.addressToSourceLine();
        this.lastAssembledRam = Arrays.copyOf(program.ramImage(), 256);
        this.programAssembled = true;

        CpuSnapshot snap;
        synchronized (cpuCore) {
            cpuCore.wipeCleanSlate();
            cpuCore.loadProgramBytes(0, lastAssembledRam);
            snap = cpuCore.createSnapshot();
        }
        traceItems.clear();

        if (tblSymbolTable != null) {
            tblSymbolTable.setItems(FXCollections.observableArrayList(program.symbolTable()));
        }

        showDiagnosticsSuccess("Assembled " + program.emittedByteCount() + " bytes · " + program.symbolTable().size() + " symbols");
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleRun() {
        if (!programAssembled) {
            handleAssemble();
            if (!programAssembled) {
                return;
            }
        }
        clockRunner.start();
        syncTransportControls();
    }

    @FXML
    public void handlePause() {
        clockRunner.pause();
        syncTransportControls();
    }

    @FXML
    public void handleStepMicro() {
        clockRunner.pause();
        if (!programAssembled) {
            handleAssemble();
            if (!programAssembled) {
                return;
            }
        }
        CpuSnapshot snap;
        synchronized (cpuCore) {
            cpuCore.stepMicro();
            snap = cpuCore.createSnapshot();
        }
        drainTraceQueueBounded();
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleStepMacro() {
        clockRunner.pause();
        if (!programAssembled) {
            handleAssemble();
            if (!programAssembled) {
                return;
            }
        }
        CpuSnapshot snap;
        synchronized (cpuCore) {
            cpuCore.stepMacro();
            snap = cpuCore.createSnapshot();
        }
        drainTraceQueueBounded();
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleUndoMicro() {
        clockRunner.pause();
        drainTraceQueueBounded();
        CpuSnapshot snap;
        int newCycle;
        synchronized (cpuCore) {
            cpuCore.undoMicroStep();
            newCycle = cpuCore.getCycleCount();
            snap = cpuCore.createSnapshot();
        }
        RamHexGridView.pruneTraceRowsAfterCycle(traceItems, newCycle);
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleUndoMacro() {
        clockRunner.pause();
        drainTraceQueueBounded();
        CpuSnapshot snap;
        int newCycle;
        synchronized (cpuCore) {
            cpuCore.undoMacroStep();
            newCycle = cpuCore.getCycleCount();
            snap = cpuCore.createSnapshot();
        }
        RamHexGridView.pruneTraceRowsAfterCycle(traceItems, newCycle);
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleReset() {
        clockRunner.pause();
        CpuSnapshot snap;
        synchronized (cpuCore) {
            cpuCore.wipeCleanSlate();
            if (programAssembled) {
                cpuCore.loadProgramBytes(0, lastAssembledRam);
            }
            snap = cpuCore.createSnapshot();
        }
        traceItems.clear();
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleClearRam() {
        clockRunner.pause();
        CpuSnapshot snap;
        synchronized (cpuCore) {
            cpuCore.wipeCleanSlate();
            snap = cpuCore.createSnapshot();
        }
        Arrays.fill(lastAssembledRam, (byte) 0);
        programAssembled = false;
        traceItems.clear();
        showDiagnosticsSuccess("Cleared all 256 bytes of RAM and reset registers.");
        renderSnapshot(snap);
        syncTransportControls();
    }

    @FXML
    public void handleLoadHex() {
        clockRunner.pause();
        TextInputDialog dialog = new TextInputDialog("10 F0 20 F1 E0 00 F0 00");
        dialog.setTitle("Load Hex Bytes into RAM");
        dialog.setHeaderText("Enter space-separated hex bytes (00..FF) starting at 0x00");
        dialog.setContentText("Hex bytes:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(raw -> {
            try {
                byte[] parsedRam = RamHexGridView.parseHexDump(raw);
                this.lastAssembledRam = Arrays.copyOf(parsedRam, 256);
                this.programAssembled = true;
                CpuSnapshot snap;
                synchronized (cpuCore) {
                    cpuCore.wipeCleanSlate();
                    cpuCore.loadProgramBytes(0, lastAssembledRam);
                    snap = cpuCore.createSnapshot();
                }
                traceItems.clear();
                showDiagnosticsSuccess("Loaded raw hex image into RAM.");
                renderSnapshot(snap);
                syncTransportControls();
            } catch (IllegalArgumentException ex) {
                showDiagnosticsError("Hex load error: " + ex.getMessage());
            }
        });
    }

    @FXML
    public void handleClearTrace() {
        cpuCore.getTraceQueue().clear();
        traceItems.clear();
    }

    private void renderSnapshot(CpuSnapshot snap) {
        if (snap == null) {
            return;
        }
        if (lblCycleCount != null) {
            lblCycleCount.setText(String.format("%05d", snap.cycleCount()));
        }
        if (lblBusValue != null) {
            lblBusValue.setText(hex2(snap.busValue()));
        }
        if (lblTState != null) {
            lblTState.setText("T" + snap.tState());
        }
        if (lblFlags != null) {
            lblFlags.setText("Z" + (snap.zeroFlag() ? 1 : 0) + " C" + (snap.carryFlag() ? 1 : 0));
        }
        if (lblPc != null) {
            lblPc.setText(hex2(snap.pc()));
        }
        if (lblMar != null) {
            lblMar.setText(hex2(snap.mar()));
        }
        if (lblIr != null) {
            lblIr.setText(hex2(snap.ir()));
        }
        if (lblRegA != null) {
            lblRegA.setText(hex2(snap.regA()));
        }
        if (lblRegB != null) {
            lblRegB.setText(hex2(snap.regB()));
        }
        if (lblAlu != null) {
            lblAlu.setText(hex2(snap.aluResult()));
        }

        int out = snap.outReg();
        if (segOutHigh != null) {
            segOutHigh.setHexNibble(SevenSegmentCanvas.extractHighNibble(out));
        }
        if (segOutLow != null) {
            segOutLow.setHexNibble(SevenSegmentCanvas.extractLowNibble(out));
        }
        if (lblOutDec != null) {
            lblOutDec.setText(String.format("%03d", out));
        }
        if (lblOutHex != null) {
            lblOutHex.setText(hex2(out));
        }
        if (lblOutAscii != null) {
            lblOutAscii.setText((out >= 0x20 && out <= 0x7E) ? ("'" + (char) out + "'") : "'.'");
        }

        for (int bit = 0; bit < 8; bit++) {
            Circle led = binaryLedCircles[bit];
            if (led != null) {
                Theme.toggleClass(led, "led--on", SevenSegmentCanvas.extractBit(out, bit) == 1);
            }
        }

        if (ramGridView != null) {
            ramGridView.updateFromRamAndPointers(snap.ramCopy(), snap.pc(), snap.mar());
        }
        if (schematicView != null) {
            schematicView.updateFromSnapshot(snap, cpuCore.getMnemonic(snap.ir()));
        }
        highlightSourceLineForPc(snap.pc());
    }

    private void syncTransportControls() {
        boolean running = clockRunner.isRunning();
        boolean halted;
        boolean canUndo;
        synchronized (cpuCore) {
            halted = cpuCore.isHalted();
            canUndo = !cpuCore.getJournal().isEmpty();
        }

        if (codeAsmEditor != null) {
            codeAsmEditor.setEditable(!running);
        }
        if (btnRun != null) {
            btnRun.setDisable(running || halted);
        }
        if (btnPause != null) {
            btnPause.setDisable(!running);
        }
        if (btnStepMicro != null) {
            btnStepMicro.setDisable(running || halted);
        }
        if (btnStepMacro != null) {
            btnStepMacro.setDisable(running || halted);
        }
        if (btnUndoMicro != null) {
            btnUndoMicro.setDisable(running || !canUndo);
        }
        if (btnUndoMacro != null) {
            btnUndoMacro.setDisable(running || !canUndo);
        }
    }

    public void highlightSourceLineForPc(int pc) {
        if (codeAsmEditor == null) {
            return;
        }
        int lineNumber = addressToSourceLine[pc & 0xFF];
        highlightEditorLine(lineNumber);
    }

    private void highlightEditorLine(int oneBasedLine) {
        if (codeAsmEditor == null) {
            return;
        }
        int paragraphCount = codeAsmEditor.getParagraphs().size();
        if (highlightedParagraph >= 0 && highlightedParagraph < paragraphCount) {
            codeAsmEditor.setParagraphStyle(highlightedParagraph, Collections.emptyList());
        }
        int targetIdx = oneBasedLine - 1;
        if (targetIdx >= 0 && targetIdx < paragraphCount) {
            codeAsmEditor.setParagraphStyle(targetIdx, List.of("asm__active-line"));
            highlightedParagraph = targetIdx;
        } else {
            highlightedParagraph = -1;
        }
    }

    private void showDiagnosticsSuccess(String msg) {
        setStatus(lblAsmDiagnostics, msg, false);
    }

    private void showDiagnosticsError(String msg) {
        setStatus(lblAsmDiagnostics, msg, true);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        if (text == null || text.isEmpty()) {
            spansBuilder.add(Collections.emptyList(), 0);
            return spansBuilder.create();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        int lastKwEnd = 0;
        while (matcher.find()) {
            String styleClass = null;
            if (matcher.group("COMMENT") != null) {
                styleClass = "asm__comment";
            } else if (matcher.group("LABEL") != null) {
                styleClass = "asm__label";
            } else if (matcher.group("DIRECTIVE") != null) {
                styleClass = "asm__directive";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "asm__number";
            } else if (matcher.group("WORD") != null) {
                String upperWord = matcher.group("WORD").toUpperCase(Locale.ROOT);
                if (assembler.getInstructionMap().containsKey(upperWord)) {
                    styleClass = "asm__mnemonic";
                }
            }

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(
                    styleClass == null ? Collections.emptyList() : Collections.singleton(styleClass),
                    matcher.end() - matcher.start()
            );
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void reloadIsaDefinitionsIfAvailable() {
        if (isaRepository == null) {
            return;
        }
        try {
            List<InstructionDef> defs = isaRepository.listInstructions();
            assembler.updateInstructions(defs);
            int[][][] compiled = isaRepository.compileControlStore();
            synchronized (cpuCore) {
                cpuCore.setControlStore(compiled);
                for (InstructionDef def : defs) {
                    cpuCore.setMnemonic(def.opcode(), def.mnemonic());
                }
            }
        } catch (SQLException ex) {
            showDiagnosticsError("Database error loading ISA: " + ex.getMessage());
        }
    }

    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        if (dbManager != null) {
            this.isaRepository = new IsaRepository(dbManager);
            this.snapshotRepository = new SnapshotRepository(dbManager);
            this.labRepository = new LabRepository(dbManager);
            this.labGrader = new LabGrader(this.isaRepository, this.labRepository);
            try {
                this.labRepository.initializeSchemaAndSeed();
            } catch (SQLException ex) {
                showDiagnosticsError("Schema initialization error: " + ex.getMessage());
            }
            reloadIsaDefinitionsIfAvailable();
            rebuildInstructionMenu();
            loadInstructionIntoDesigner(0x20);
            handleRefreshDatasheet();
            refreshLabTable(1);
            handleAssemble();
        }
    }

    private void initLabWorkbench() {
        if (tblLabs != null) {
            TableColumn<LabChallenge, String> colId = new TableColumn<>("Lab #");
            colId.setCellValueFactory(c -> new SimpleStringProperty("Lab #" + c.getValue().labId()));
            colId.setPrefWidth(72.0);

            TableColumn<LabChallenge, String> colTitle = new TableColumn<>("Challenge Title");
            colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().title()));
            colTitle.setPrefWidth(420.0);

            TableColumn<LabChallenge, String> colStatus = new TableColumn<>("Status");
            colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().statusText()));
            colStatus.setPrefWidth(135.0);

            tblLabs.getColumns().setAll(List.of(colId, colTitle, colStatus));
            tblLabs.setItems(labItems);
            tblLabs.getSelectionModel().selectedItemProperty().addListener((obs, oldLab, newLab) -> {
                if (newLab != null) {
                    showLabReportSuccess(newLab.title() + " - " + newLab.description());
                }
            });
        }

        if (tblLabResults != null) {
            TableColumn<LabTestResult, String> colOrder = new TableColumn<>("Case #");
            colOrder.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().caseOrder()));
            colOrder.setPrefWidth(64.0);

            TableColumn<LabTestResult, String> colDesc = new TableColumn<>("Description");
            colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
            colDesc.setPrefWidth(190.0);

            TableColumn<LabTestResult, String> colExp = new TableColumn<>("Expected");
            colExp.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().expectedSummary()));
            colExp.setPrefWidth(145.0);

            TableColumn<LabTestResult, String> colAct = new TableColumn<>("Actual");
            colAct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().actualSummary()));
            colAct.setPrefWidth(145.0);

            TableColumn<LabTestResult, String> colCycles = new TableColumn<>("Cycles");
            colCycles.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().cyclesExecuted())));
            colCycles.setPrefWidth(68.0);

            TableColumn<LabTestResult, String> colDiag = new TableColumn<>("Result");
            colDiag.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().statusLabel() + " - " + c.getValue().diagnostic()
            ));
            colDiag.setPrefWidth(210.0);

            tblLabResults.getColumns().setAll(List.of(colOrder, colDesc, colExp, colAct, colCycles, colDiag));
            tblLabResults.setItems(labResultItems);
        }
    }

    @FXML
    public void handleLoadLabStarter() {
        clockRunner.pause();
        LabChallenge selected = tblLabs == null ? null : tblLabs.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showLabReportError("Select a Lab Challenge from the table first.");
            return;
        }
        boolean proceed = promptSaveSnapshotBeforeDestructiveAction(
                "Loading starter code for " + selected.title()
                        + " will wipe all 256 bytes of RAM, registers, flags, and DeltaJournal."
        );
        if (!proceed) {
            return;
        }
        if (codeAsmEditor != null) {
            codeAsmEditor.replaceText(selected.starterCode());
        }
        LabGrader.wipeToCleanSlate(cpuCore);
        Arrays.fill(lastAssembledRam, (byte) 0);
        programAssembled = false;
        traceItems.clear();
        labResultItems.clear();

        CpuSnapshot snap;
        synchronized (cpuCore) {
            snap = cpuCore.createSnapshot();
        }
        renderSnapshot(snap);
        syncTransportControls();
        showLabReportSuccess("Loaded starter code for " + selected.title() + " on a Clean-Slate CPU.");
        showDiagnosticsSuccess("Clean-Slate initialized for " + selected.title() + ".");
    }

    @FXML
    public void handleRunLabGrader() {
        clockRunner.pause();
        if (labGrader == null || labRepository == null) {
            showLabReportError("Lab grader is not initialized.");
            return;
        }
        LabChallenge selected = tblLabs == null ? null : tblLabs.getSelectionModel().getSelectedItem();
        if (selected == null && !labItems.isEmpty()) {
            selected = labItems.getFirst();
        }
        if (selected == null) {
            showLabReportError("Select a Lab Challenge from the table first.");
            return;
        }

        boolean proceed = promptSaveSnapshotBeforeDestructiveAction(
                "Grading " + selected.title()
                        + " wipes all 256 bytes of RAM, registers, flags, and DeltaJournal before running deterministic test cases."
        );
        if (!proceed) {
            return;
        }

        String source = codeAsmEditor == null ? "" : codeAsmEditor.getText();
        try {
            LabGradeReport report = labGrader.grade(selected, source, cpuCore);
            labResultItems.setAll(report.caseResults());
            refreshLabTable(selected.labId());

            traceItems.clear();
            CpuSnapshot snap;
            synchronized (cpuCore) {
                snap = cpuCore.createSnapshot();
            }
            renderSnapshot(snap);
            syncTransportControls();

            if (report.allPassed()) {
                showLabReportSuccess(report.summaryMessage());
                showDiagnosticsSuccess(report.summaryMessage());
            } else {
                showLabReportError(report.summaryMessage());
                showDiagnosticsError(report.summaryMessage());
            }
        } catch (SQLException ex) {
            showLabReportError("Lab grading error: " + ex.getMessage());
        }
    }

    private void refreshLabTable(int selectLabId) {
        if (labRepository == null) {
            return;
        }
        try {
            List<LabChallenge> labs = labRepository.listLabs();
            labItems.setAll(labs);
            if (tblLabs != null && !labs.isEmpty()) {
                for (LabChallenge lab : labs) {
                    if (lab.labId() == selectLabId) {
                        tblLabs.getSelectionModel().select(lab);
                        break;
                    }
                }
            }
        } catch (SQLException ex) {
            showDiagnosticsError("Failed to load labs: " + ex.getMessage());
        }
    }

    private void showLabReportSuccess(String msg) {
        setStatus(lblLabReport, msg, false);
    }

    private void showLabReportError(String msg) {
        setStatus(lblLabReport, msg, true);
    }

    @FXML
    public void handleHelpOnboarding() {
        clockRunner.pause();
        Window owner = btnHelpOnboarding == null || btnHelpOnboarding.getScene() == null
                ? null
                : btnHelpOnboarding.getScene().getWindow();
        OnboardingController.showModal(owner, snapshotRepository);
    }

    @FXML
    public void handleSnapshots() {
        clockRunner.pause();
        if (snapshotRepository == null) {
            showDiagnosticsError("Snapshot repository is not initialized.");
            return;
        }

        Window owner = btnSnapshots == null || btnSnapshots.getScene() == null
                ? null
                : btnSnapshots.getScene().getWindow();

        Stage dialog = new Stage();
        dialog.setTitle("MICRO-8 Workspace Snapshots");
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        ObservableList<WorkspaceSnapshot> snapshotItems = FXCollections.observableArrayList();
        refreshSnapshotList(snapshotItems);

        TextField txtName = new TextField("checkpoint-1");
        txtName.setPrefWidth(220.0);
        Button btnSave = new Button("Save Snapshot");
        Label lblStatus = new Label("Save or restore full editor code, 256B RAM, and CPU registers.");
        lblStatus.getStyleClass().addAll("status", "status--boxed");
        lblStatus.setMaxWidth(Double.MAX_VALUE);

        TableView<WorkspaceSnapshot> tblSnapshots = new TableView<>(snapshotItems);
        tblSnapshots.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<WorkspaceSnapshot, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colName.setPrefWidth(150.0);

        TableColumn<WorkspaceSnapshot, String> colCreated = new TableColumn<>("Saved At (UTC)");
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt()));
        colCreated.setPrefWidth(195.0);

        TableColumn<WorkspaceSnapshot, String> colPc = new TableColumn<>("PC");
        colPc.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().pc())));
        colPc.setPrefWidth(60.0);

        TableColumn<WorkspaceSnapshot, String> colRegA = new TableColumn<>("RegA");
        colRegA.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().regA())));
        colRegA.setPrefWidth(60.0);

        TableColumn<WorkspaceSnapshot, String> colOut = new TableColumn<>("OutReg");
        colOut.setCellValueFactory(c -> new SimpleStringProperty(hex2(c.getValue().outReg())));
        colOut.setPrefWidth(68.0);

        TableColumn<WorkspaceSnapshot, String> colCycles = new TableColumn<>("Cycles");
        colCycles.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().cycleCount())));
        colCycles.setPrefWidth(72.0);

        tblSnapshots.getColumns().setAll(List.of(colName, colCreated, colPc, colRegA, colOut, colCycles));
        VBox.setVgrow(tblSnapshots, Priority.ALWAYS);

        btnSave.setOnAction(e -> {
            String rawName = txtName.getText() == null ? "" : txtName.getText().trim();
            if (rawName.isEmpty()) {
                lblStatus.setText("Snapshot name must not be empty.");
                return;
            }
            try {
                boolean allowOverwrite = false;
                if (snapshotRepository.existsByName(rawName)) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Overwrite Workspace Snapshot");
                    confirm.setHeaderText("Snapshot '" + rawName + "' already exists.");
                    confirm.setContentText("Do you want to overwrite the existing snapshot?");
                    Optional<ButtonType> choice = confirm.showAndWait();
                    if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                        return;
                    }
                    allowOverwrite = true;
                }
                String asmText = codeAsmEditor == null ? "" : codeAsmEditor.getText();
                WorkspaceSnapshot captured = snapshotRepository.captureFromCpu(rawName, asmText, cpuCore);
                snapshotRepository.saveSnapshot(captured, allowOverwrite);
                refreshSnapshotList(snapshotItems);
                String msg = "Saved workspace snapshot '" + rawName + "'.";
                lblStatus.setText(msg);
                showDiagnosticsSuccess(msg);
            } catch (SQLException | IllegalArgumentException | IllegalStateException ex) {
                lblStatus.setText("Save error: " + ex.getMessage());
            }
        });

        Button btnRestore = new Button("Restore Selected");
        btnRestore.setOnAction(e -> {
            WorkspaceSnapshot selected = tblSnapshots.getSelectionModel().getSelectedItem();
            if (selected == null) {
                lblStatus.setText("Select a snapshot from the table to restore.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Restore Workspace Snapshot");
            confirm.setHeaderText("Restore snapshot '" + selected.name() + "'?");
            confirm.setContentText("This will replace the current assembly editor code, 256B RAM, and CPU registers.");
            Optional<ButtonType> choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                return;
            }

            if (codeAsmEditor != null) {
                codeAsmEditor.replaceText(selected.assemblySource());
            }
            snapshotRepository.restoreIntoCpu(selected, cpuCore);
            this.lastAssembledRam = selected.ramBlob();
            this.programAssembled = true;
            traceItems.clear();

            CpuSnapshot snap;
            synchronized (cpuCore) {
                snap = cpuCore.createSnapshot();
            }
            renderSnapshot(snap);
            syncTransportControls();
            String msg = "Restored workspace snapshot '" + selected.name() + "'.";
            lblStatus.setText(msg);
            showDiagnosticsSuccess(msg);
        });

        Button btnDelete = new Button("Delete Selected");
        btnDelete.setOnAction(e -> {
            WorkspaceSnapshot selected = tblSnapshots.getSelectionModel().getSelectedItem();
            if (selected == null) {
                lblStatus.setText("Select a snapshot from the table to delete.");
                return;
            }
            try {
                snapshotRepository.deleteSnapshot(selected.name());
                refreshSnapshotList(snapshotItems);
                lblStatus.setText("Deleted snapshot '" + selected.name() + "'.");
            } catch (SQLException ex) {
                lblStatus.setText("Delete error: " + ex.getMessage());
            }
        });

        HBox topRow = new HBox(8.0, new Label("Snapshot Name:"), txtName, btnSave);
        topRow.setAlignment(Pos.CENTER_LEFT);

        HBox bottomRow = new HBox(8.0, btnRestore, btnDelete);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10.0, topRow, tblSnapshots, lblStatus, bottomRow);
        root.setPadding(new Insets(12.0));
        root.getStyleClass().add("workbench");

        Scene scene = new Scene(root, 640.0, 420.0);
        URL cssUrl = getClass().getResource("/dev/frtzhahn/micro8/ui/workbench.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    public boolean promptSaveSnapshotBeforeDestructiveAction(String contextMessage) {
        if (snapshotRepository == null) {
            return true;
        }
        ButtonType btnSaveAndContinue = new ButtonType("Save Snapshot & Continue", ButtonBar.ButtonData.YES);
        ButtonType btnContinueWithoutSave = new ButtonType("Continue Without Saving", ButtonBar.ButtonData.NO);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert prompt = new Alert(
                Alert.AlertType.CONFIRMATION,
                contextMessage,
                btnSaveAndContinue,
                btnContinueWithoutSave,
                btnCancel
        );
        prompt.setTitle("Clean-Slate Workspace Protection");
        prompt.setHeaderText("Save a Workspace Snapshot before wiping RAM and registers?");

        Optional<ButtonType> result = prompt.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) {
            return false;
        }
        if (result.get() == btnSaveAndContinue) {
            String autoName = "pre-lab-" + System.currentTimeMillis();
            try {
                String asm = codeAsmEditor == null ? "" : codeAsmEditor.getText();
                WorkspaceSnapshot snap = snapshotRepository.captureFromCpu(autoName, asm, cpuCore);
                snapshotRepository.saveSnapshot(snap, true);
                showDiagnosticsSuccess("Saved automatic snapshot '" + autoName + "'.");
            } catch (SQLException ex) {
                showDiagnosticsError("Failed to save snapshot: " + ex.getMessage());
                return false;
            }
        }
        return true;
    }

    private void refreshSnapshotList(ObservableList<WorkspaceSnapshot> target) {
        if (snapshotRepository == null) {
            return;
        }
        try {
            target.setAll(snapshotRepository.listSnapshots());
        } catch (SQLException ex) {
            showDiagnosticsError("Failed to list snapshots: " + ex.getMessage());
        }
    }

    public SnapshotRepository getSnapshotRepository() {
        return snapshotRepository;
    }

    public void shutdown() {
        if (uiTimer != null) {
            uiTimer.stop();
        }
        clockRunner.shutdown();
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    public CodeArea getCodeAsmEditor() {
        return codeAsmEditor;
    }

    public CpuCore getCpuCore() {
        return cpuCore;
    }

    public CpuClockRunner getClockRunner() {
        return clockRunner;
    }

    public Assembler getAssembler() {
        return assembler;
    }

    private static String hex2(int value) {
        int v = value & 0xFF;
        char high = Character.toUpperCase(Character.forDigit((v >>> 4) & 0x0F, 16));
        char low = Character.toUpperCase(Character.forDigit(v & 0x0F, 16));
        return "0x" + high + low;
    }
}
