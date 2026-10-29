package dev.frtzhahn.micro8.ui;

import dev.frtzhahn.micro8.cpu.ControlSignals;
import dev.frtzhahn.micro8.cpu.ControlSignals.SignalSpec;
import dev.frtzhahn.micro8.cpu.CpuSnapshot;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import static dev.frtzhahn.micro8.cpu.ControlSignals.AI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.AO;
import static dev.frtzhahn.micro8.cpu.ControlSignals.BI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.BO;
import static dev.frtzhahn.micro8.cpu.ControlSignals.BUS_DRIVER_MASK;
import static dev.frtzhahn.micro8.cpu.ControlSignals.CE;
import static dev.frtzhahn.micro8.cpu.ControlSignals.CO;
import static dev.frtzhahn.micro8.cpu.ControlSignals.EO;
import static dev.frtzhahn.micro8.cpu.ControlSignals.FI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.II;
import static dev.frtzhahn.micro8.cpu.ControlSignals.J;
import static dev.frtzhahn.micro8.cpu.ControlSignals.MI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.OI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.RI;
import static dev.frtzhahn.micro8.cpu.ControlSignals.RO;
import static dev.frtzhahn.micro8.cpu.ControlSignals.SU;

/**
 * Vector canvas rendering the MICRO-8 system bus, registers, ALU, T0-T5 sequencer, and 17 control wires.
 * Blue marks the block driving the bus, green marks blocks latching from it.
 */
public final class CpuSchematicView extends Pane {

    private static final Font FONT_TITLE = Font.font(Theme.SANS, FontWeight.BOLD, 11);
    private static final Font FONT_VALUE = Font.font(Theme.MONO, 15);
    private static final Font FONT_DETAIL = Font.font(Theme.MONO, 12);
    private static final Font FONT_CHIP = Font.font(Theme.MONO, 10);
    private static final Font FONT_PILL = Font.font(Theme.MONO, FontWeight.BOLD, 11);

    private static final double ROW_LABEL_W = 92.0;

    private final Canvas canvas = new Canvas(600, 460);
    private CpuSnapshot currentSnapshot;
    private String currentMnemonic = "NOP";

    public CpuSchematicView() {
        getChildren().add(canvas);
        widthProperty().addListener((obs, oldVal, newVal) -> resizeAndRedraw());
        heightProperty().addListener((obs, oldVal, newVal) -> resizeAndRedraw());
    }

    public void updateFromSnapshot(CpuSnapshot snapshot, String mnemonic) {
        this.currentSnapshot = snapshot;
        this.currentMnemonic = (mnemonic == null || mnemonic.isBlank()) ? "???" : mnemonic;
        draw();
    }

    private void resizeAndRedraw() {
        canvas.setWidth(Math.max(480.0, getWidth()));
        canvas.setHeight(Math.max(360.0, getHeight()));
        draw();
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        gc.setTextBaseline(VPos.CENTER);

        CpuSnapshot s = currentSnapshot;
        int ctrl = s == null ? 0 : s.controlWord();
        int busVal = s == null ? 0 : s.busValue();
        int mar = s == null ? 0 : s.mar();
        int ramAtMar = s == null ? 0 : Byte.toUnsignedInt(s.ramCopy()[mar & 0xFF]);
        int alu = s == null ? 0 : s.aluResult();
        boolean zFlag = s != null && s.zeroFlag();
        boolean cFlag = s != null && s.carryFlag();

        boolean busDriven = (ctrl & BUS_DRIVER_MASK) != 0;
        double busX = Math.round(w * 0.5);
        double topY = 44.0;
        double busBottomY = h - 92.0;

        // Bus spine and value pill
        gc.setStroke(busDriven ? Theme.BLUE : Theme.LINE_STRONG);
        gc.setLineWidth(3.0);
        gc.strokeLine(busX, topY - 10, busX, busBottomY);

        String busText = "BUS " + hex2(busVal) + " " + dec3(busVal);
        double pillW = 132.0;
        gc.setFill(Theme.BG);
        gc.fillRoundRect(busX - pillW / 2, 6, pillW, 24, 24, 24);
        gc.setStroke(busDriven ? Theme.BLUE : Theme.LINE_STRONG);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(busX - pillW / 2 + 0.5, 6.5, pillW - 1, 23, 24, 24);
        gc.setFont(FONT_PILL);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(busDriven ? Theme.BLUE : Theme.MUTED);
        gc.fillText(busText, busX, 18);

        double gapToBus = 56.0;
        double blockW = Math.min(210.0, busX - gapToBus - 4);
        double blockH = Math.min(58.0, (busBottomY - topY - 36.0) / 4.0);
        double rowGap = (busBottomY - topY - blockH * 4) / 3.0;
        double leftX = busX - gapToBus - blockW;
        double rightX = busX + gapToBus;

        drawBlock(gc, leftX, rowY(topY, blockH, rowGap, 0), blockW, blockH, busX, true,
                "PC", hex2(s == null ? 0 : s.pc()), dec3(s == null ? 0 : s.pc()), "CO CE J",
                (ctrl & CO) != 0, (ctrl & (CE | J)) != 0);
        drawBlock(gc, leftX, rowY(topY, blockH, rowGap, 1), blockW, blockH, busX, true,
                "MAR", hex2(mar), dec3(mar), "MI",
                false, (ctrl & MI) != 0);
        drawBlock(gc, leftX, rowY(topY, blockH, rowGap, 2), blockW, blockH, busX, true,
                "RAM @ MAR", hex2(ramAtMar), dec3(ramAtMar), "RO RI",
                (ctrl & RO) != 0, (ctrl & RI) != 0);
        drawBlock(gc, leftX, rowY(topY, blockH, rowGap, 3), blockW, blockH, busX, true,
                "IR", hex2(s == null ? 0 : s.ir()), currentMnemonic, "II",
                false, (ctrl & II) != 0);

        drawBlock(gc, rightX, rowY(topY, blockH, rowGap, 0), blockW, blockH, busX, false,
                "A", hex2(s == null ? 0 : s.regA()), dec3(s == null ? 0 : s.regA()), "AO AI",
                (ctrl & AO) != 0, (ctrl & AI) != 0);
        drawBlock(gc, rightX, rowY(topY, blockH, rowGap, 1), blockW, blockH, busX, false,
                "ALU · " + ((ctrl & SU) != 0 ? "SUB" : "ADD"), hex2(alu),
                "Z" + (zFlag ? 1 : 0) + " C" + (cFlag ? 1 : 0), "EO SU FI",
                (ctrl & EO) != 0, (ctrl & (SU | FI)) != 0);
        drawBlock(gc, rightX, rowY(topY, blockH, rowGap, 2), blockW, blockH, busX, false,
                "B", hex2(s == null ? 0 : s.regB()), dec3(s == null ? 0 : s.regB()), "BO BI",
                (ctrl & BO) != 0, (ctrl & BI) != 0);
        drawBlock(gc, rightX, rowY(topY, blockH, rowGap, 3), blockW, blockH, busX, false,
                "OUT", hex2(s == null ? 0 : s.outReg()), dec3(s == null ? 0 : s.outReg()), "OI",
                false, (ctrl & OI) != 0);

        drawSequencerRow(gc, h - 70.0, s == null ? 0 : s.tState());
        drawControlWiresRow(gc, w, h - 34.0, ctrl);
    }

    private static double rowY(double topY, double blockH, double rowGap, int row) {
        return topY + row * (blockH + rowGap);
    }

    private void drawBlock(
            GraphicsContext gc,
            double x,
            double y,
            double w,
            double h,
            double busX,
            boolean leftSide,
            String title,
            String value,
            String detail,
            String signals,
            boolean driver,
            boolean consumer
    ) {
        Color role = driver ? Theme.BLUE : (consumer ? Theme.GREEN : null);
        Color soft = driver ? Theme.BLUE_SOFT : Theme.GREEN_SOFT;
        double midY = Math.round(y + h * 0.5);
        double edgeX = leftSide ? x + w : x;

        gc.setStroke(role == null ? Theme.LINE_STRONG : role);
        gc.setLineWidth(role == null ? 1.5 : 2.0);
        gc.strokeLine(edgeX, midY, busX, midY);
        if (driver) {
            drawArrowHead(gc, busX + (leftSide ? -1.5 : 1.5), midY, leftSide, role);
        } else if (consumer) {
            drawArrowHead(gc, edgeX, midY, !leftSide, role);
        }

        gc.setFill(role == null ? Theme.SURFACE : soft);
        gc.fillRoundRect(x, y, w, h, 14, 14);
        gc.setStroke(role == null ? Theme.LINE_STRONG : role);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x + 0.5, y + 0.5, w - 1, h - 1, 14, 14);

        double titleY = y + 17;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(FONT_TITLE);
        gc.setFill(Theme.MUTED);
        gc.fillText(title, x + 12, titleY);

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(FONT_CHIP);
        gc.setFill(role == null ? Theme.FAINT : role);
        gc.fillText(signals, x + w - 12, titleY);

        double valueY = y + h - 17;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(FONT_VALUE);
        gc.setFill(Theme.TEXT);
        gc.fillText(value, x + 12, valueY);

        gc.setFont(FONT_DETAIL);
        gc.setFill(Theme.FAINT);
        gc.fillText(detail, x + 12 + 46, valueY);
    }

    /** Small filled triangle whose tip sits at (tipX, y), pointing left or right. */
    private static void drawArrowHead(GraphicsContext gc, double tipX, double y, boolean pointsRight, Color color) {
        double back = pointsRight ? tipX - 7 : tipX + 7;
        gc.setFill(color);
        gc.fillPolygon(new double[]{tipX, back, back}, new double[]{y, y - 4.5, y + 4.5}, 3);
    }

    private void drawSequencerRow(GraphicsContext gc, double y, int activeTState) {
        drawRowLabel(gc, "Sequencer", y);
        double pillW = 38.0;
        double pillH = 22.0;
        double gap = 6.0;
        gc.setFont(FONT_PILL);
        gc.setTextAlign(TextAlignment.CENTER);

        for (int t = 0; t < 6; t++) {
            double px = ROW_LABEL_W + t * (pillW + gap);
            boolean active = t == activeTState;
            if (active) {
                gc.setFill(Theme.BLUE_EMPH);
                gc.fillRoundRect(px, y - pillH / 2, pillW, pillH, pillH, pillH);
            } else {
                gc.setStroke(Theme.LINE);
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(px + 0.5, y - pillH / 2 + 0.5, pillW - 1, pillH - 1, pillH, pillH);
            }
            gc.setFill(active ? Color.WHITE : Theme.FAINT);
            gc.fillText("T" + t, px + pillW / 2, y);
        }
    }

    private void drawControlWiresRow(GraphicsContext gc, double width, double y, int ctrl) {
        drawRowLabel(gc, "Control word", y);
        int count = ControlSignals.ALL_SIGNALS.size();
        double gap = 4.0;
        double chipW = Math.min(36.0, (width - ROW_LABEL_W - (count - 1) * gap) / count);
        double chipH = 20.0;
        gc.setFont(FONT_CHIP);
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = 0; i < count; i++) {
            SignalSpec spec = ControlSignals.ALL_SIGNALS.get(i);
            double bx = ROW_LABEL_W + i * (chipW + gap);
            boolean active = (ctrl & spec.mask()) != 0;
            Color role = spec.busDriver() ? Theme.BLUE : Theme.GREEN;

            if (active) {
                gc.setFill(spec.busDriver() ? Theme.BLUE_SOFT : Theme.GREEN_SOFT);
                gc.fillRoundRect(bx, y - chipH / 2, chipW, chipH, chipH, chipH);
            }
            gc.setStroke(active ? role : Theme.LINE);
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(bx + 0.5, y - chipH / 2 + 0.5, chipW - 1, chipH - 1, chipH, chipH);
            gc.setFill(active ? role : Theme.FAINT);
            gc.fillText(spec.name(), bx + chipW / 2, y);
        }
    }

    private static void drawRowLabel(GraphicsContext gc, String text, double y) {
        gc.setFont(FONT_TITLE);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Theme.FAINT);
        gc.fillText(text, 0, y);
    }

    private static String dec3(int value) {
        return String.format("%03d", value & 0xFF);
    }

    private static String hex2(int value) {
        int v = value & 0xFF;
        char high = Character.toUpperCase(Character.forDigit((v >>> 4) & 0x0F, 16));
        char low = Character.toUpperCase(Character.forDigit(v & 0x0F, 16));
        return "0x" + high + low;
    }
}
