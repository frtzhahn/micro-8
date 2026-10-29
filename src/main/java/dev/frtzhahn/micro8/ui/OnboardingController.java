package dev.frtzhahn.micro8.ui;

import dev.frtzhahn.micro8.db.SnapshotRepository;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

/**
 * Controller for the 4-slide annotated onboarding modal viewer persisted via SQLite app_settings.
 */
public final class OnboardingController {

    public static final int SLIDE_WIDTH = 720;
    public static final int SLIDE_HEIGHT = 420;

    public record SlideSpec(
            int slideNumber,
            String title,
            String caption,
            List<String> calloutLines
    ) {
    }

    public static final List<SlideSpec> SLIDES = List.of(
            new SlideSpec(
                    1,
                    "Slide 1 - Uniform 2-Byte ISA & 6-Phase Sequencer (T0-T5)",
                    "Every instruction occupies 2 bytes in 256B RAM (Byte 0 = Opcode, Byte 1 = Operand or 0x00 padding). "
                            + "T0-T1 perform universal fetch, T2-T3 resolve operands, and T4-T5 execute with early CLR step reset.",
                    List.of(
                            "[BYTE 0: 8-BIT OPCODE]  +  [BYTE 1: 8-BIT OPERAND / 0x00 PADDING]",
                            "Max 128 instructions in 256-byte RAM (Unsigned addresses 0x00 .. 0xFF)",
                            "T0: CO | MI           (PC drives address onto bus -> MAR latches)",
                            "T1: RO | II | CE      (RAM drives opcode onto bus -> IR latches, PC++)",
                            "T2..T5: Operand fetch, ALU execution & CLR early ring-counter reset"
                    )
            ),
            new SlideSpec(
                    2,
                    "Slide 2 - 8-Bit System Bus & 17 Control Wires (Single-Driver Law)",
                    "Only 5 lines drive the shared 8-bit bus (RO, AO, BO, EO, CO in blue). "
                            + "The other 12 lines latch data or control sequencing (green). IR holds only the opcode, so no IO wire exists.",
                    List.of(
                            "5 BUS DRIVERS (BLUE - MAX 1 ACTIVE PER T-STATE):",
                            "  RO (RAM Out) | AO (RegA Out) | BO (RegB Out) | EO (ALU Out) | CO (PC Out)",
                            "12 LATCH / SEQUENCER WIRES (GREEN):",
                            "  HLT, MI, RI, II, AI, BI, SU (2's Comp Sub), OI, CE, J, FI, CLR",
                            "Hardware Law: Asserting >1 bus driver triggers BusContentionException!"
                    )
            ),
            new SlideSpec(
                    3,
                    "Slide 3 - Time-Travel Debugging (DeltaJournal Ring Buffer)",
                    "Every register, flag, T-state, and RAM mutation is bit-packed into a zero-allocation long[10_000] ring buffer "
                            + "for deterministic Micro-Step (T-State) and Macro-Step (Instruction) reverse execution.",
                    List.of(
                            "64-BIT PACKED DELTA JOURNAL ENTRY (long[10_000]):",
                            "  [63..32: cycleCount] [31..24: tState] [23..16: target] [15..8: addr] [7..0: oldVal]",
                            "Step Micro / Undo Micro: Advance or rewind a single T-State (T0..T5)",
                            "Step Macro / Undo Macro: Advance or rewind a complete 2-byte instruction",
                            "60Hz AnimationTimer decouples the 1 kHz CPU loop from JavaFX UI updates"
                    )
            ),
            new SlideSpec(
                    4,
                    "Slide 4 - Microcode Designer, Live Datasheet & Clean-Slate Labs",
                    "Design custom opcodes in SQLite WAL with pre-commit bus-contention validation, inspect the live HTML WebView datasheet, "
                            + "save Workspace Snapshots, and grade Clean-Slate Lab Challenges.",
                    List.of(
                            "Tab 2 (Microcode): Toggle 17 control wires across T0..T5 and flag states [0..3]",
                            "Tab 3 (ISA): Dynamic dark-themed HTML datasheet rendered from SQLite",
                            "Tab 4 (Labs): Deterministic headless grading after Clean-Slate RAM/Reg wipe",
                            "Top Toolbar [Snapshots]: Save & restore code, 256B RAM, and CPU registers"
                    )
            )
    );

    @FXML
    private ImageView imgOnboardingSlide;

    @FXML
    private Label lblSlideTitle;

    @FXML
    private Label lblSlideCaption;

    @FXML
    private CheckBox chkHideOnboarding;

    @FXML
    private Button btnPrevSlide;

    @FXML
    private Label lblSlideCounter;

    @FXML
    private Button btnNextSlide;

    private SnapshotRepository snapshotRepository;
    private int currentSlideIndex;

    @FXML
    public void initialize() {
        renderCurrentSlide();
    }

    public void setSnapshotRepository(SnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
        if (snapshotRepository != null && chkHideOnboarding != null) {
            try {
                chkHideOnboarding.setSelected(snapshotRepository.isOnboardingHidden());
            } catch (SQLException ignored) {
                chkHideOnboarding.setSelected(false);
            }
        }
    }

    @FXML
    public void handlePrevSlide() {
        if (currentSlideIndex > 0) {
            currentSlideIndex--;
            renderCurrentSlide();
        }
    }

    @FXML
    public void handleNextSlide() {
        if (currentSlideIndex < SLIDES.size() - 1) {
            currentSlideIndex++;
            renderCurrentSlide();
        }
    }

    @FXML
    public void handleToggleHideOnboarding() {
        if (snapshotRepository == null || chkHideOnboarding == null) {
            return;
        }
        try {
            snapshotRepository.setOnboardingHidden(chkHideOnboarding.isSelected());
        } catch (SQLException ignored) {
        }
    }

    public int getCurrentSlideIndex() {
        return currentSlideIndex;
    }

    private void renderCurrentSlide() {
        SlideSpec spec = SLIDES.get(currentSlideIndex);
        if (lblSlideTitle != null) {
            lblSlideTitle.setText(spec.title());
        }
        if (lblSlideCaption != null) {
            lblSlideCaption.setText(spec.caption());
        }
        if (lblSlideCounter != null) {
            lblSlideCounter.setText(spec.slideNumber() + " / " + SLIDES.size());
        }
        if (btnPrevSlide != null) {
            btnPrevSlide.setDisable(currentSlideIndex == 0);
        }
        if (btnNextSlide != null) {
            btnNextSlide.setDisable(currentSlideIndex == SLIDES.size() - 1);
        }
        if (imgOnboardingSlide != null) {
            imgOnboardingSlide.setImage(loadOrRenderSlideImage(spec));
        }
    }

    static Image loadOrRenderSlideImage(SlideSpec spec) {
        String resourcePath = "/dev/frtzhahn/micro8/ui/slides/slide-" + spec.slideNumber() + ".png";
        try (InputStream in = OnboardingController.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                return new Image(in);
            }
        } catch (IOException ignored) {
        }

        if (Platform.isFxApplicationThread()) {
            Canvas canvas = new Canvas(SLIDE_WIDTH, SLIDE_HEIGHT);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Theme.SURFACE);
            gc.fillRect(0, 0, SLIDE_WIDTH, SLIDE_HEIGHT);

            gc.setTextBaseline(VPos.CENTER);
            gc.setFill(Theme.TEXT);
            gc.setFont(Font.font(Theme.SANS, FontWeight.BOLD, 16));
            gc.fillText(spec.title(), 28, 40);

            List<String> lines = spec.calloutLines();
            double y = 76;
            for (int i = 0; i < lines.size(); i++) {
                boolean header = i == 0;
                gc.setFill(header ? Theme.BLUE_SOFT : Theme.BG);
                gc.fillRoundRect(28, y, SLIDE_WIDTH - 56, 46, 12, 12);
                gc.setStroke(header ? Theme.BLUE : Theme.LINE_STRONG);
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(28.5, y + 0.5, SLIDE_WIDTH - 57, 45, 12, 12);

                gc.setFill(header ? Theme.BLUE : Theme.TEXT);
                gc.setFont(Font.font(Theme.MONO, 13));
                gc.fillText(lines.get(i), 44, y + 23);
                y += 58;
            }

            WritableImage image = new WritableImage(SLIDE_WIDTH, SLIDE_HEIGHT);
            canvas.snapshot(null, image);
            return image;
        }

        WritableImage fallback = new WritableImage(SLIDE_WIDTH, SLIDE_HEIGHT);
        PixelWriter pw = fallback.getPixelWriter();
        Color bg = Theme.SURFACE;
        for (int y = 0; y < SLIDE_HEIGHT; y++) {
            for (int x = 0; x < SLIDE_WIDTH; x++) {
                pw.setColor(x, y, bg);
            }
        }
        return fallback;
    }

    public static void showModal(Window owner, SnapshotRepository snapshotRepository) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    OnboardingController.class.getResource("/dev/frtzhahn/micro8/ui/OnboardingDialog.fxml")
            );
            Parent root = loader.load();
            OnboardingController controller = loader.getController();
            controller.setSnapshotRepository(snapshotRepository);

            Scene scene = new Scene(root, 760, 580);
            URL cssUrl = OnboardingController.class.getResource("/dev/frtzhahn/micro8/ui/workbench.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage dialog = new Stage();
            dialog.setTitle("MICRO-8 Quickstart Tour");
            if (owner != null) {
                dialog.initOwner(owner);
                dialog.initModality(Modality.WINDOW_MODAL);
            }
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load OnboardingDialog.fxml", ex);
        }
    }
}
