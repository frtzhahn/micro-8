package dev.frtzhahn.micro8.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

/**
 * Hardware-style 7-segment hexadecimal digit display canvas (0..F) with pure bitmask helpers.
 */
public final class SevenSegmentCanvas extends Canvas {

    public static final int SEG_A = 1;
    public static final int SEG_B = 1 << 1;
    public static final int SEG_C = 1 << 2;
    public static final int SEG_D = 1 << 3;
    public static final int SEG_E = 1 << 4;
    public static final int SEG_F = 1 << 5;
    public static final int SEG_G = 1 << 6;

    private static final int[] HEX_SEGMENT_MASKS = {
            SEG_A | SEG_B | SEG_C | SEG_D | SEG_E | SEG_F,         // 0
            SEG_B | SEG_C,                                         // 1
            SEG_A | SEG_B | SEG_G | SEG_E | SEG_D,                 // 2
            SEG_A | SEG_B | SEG_G | SEG_C | SEG_D,                 // 3
            SEG_F | SEG_G | SEG_B | SEG_C,                         // 4
            SEG_A | SEG_F | SEG_G | SEG_C | SEG_D,                 // 5
            SEG_A | SEG_F | SEG_E | SEG_D | SEG_C | SEG_G,         // 6
            SEG_A | SEG_B | SEG_C,                                 // 7
            SEG_A | SEG_B | SEG_C | SEG_D | SEG_E | SEG_F | SEG_G, // 8
            SEG_A | SEG_B | SEG_C | SEG_D | SEG_F | SEG_G,         // 9
            SEG_A | SEG_B | SEG_C | SEG_E | SEG_F | SEG_G,         // A
            SEG_F | SEG_E | SEG_G | SEG_C | SEG_D,                 // b
            SEG_A | SEG_F | SEG_E | SEG_D,                         // C
            SEG_B | SEG_C | SEG_D | SEG_E | SEG_G,                 // d
            SEG_A | SEG_F | SEG_G | SEG_E | SEG_D,                 // E
            SEG_A | SEG_F | SEG_G | SEG_E                          // F
    };

    private int currentNibble = -1;

    public SevenSegmentCanvas() {
        super(34.0, 58.0);
        setHexNibble(0);
    }

    public static int encodeHexDigit(int nibble) {
        return HEX_SEGMENT_MASKS[nibble & 0x0F];
    }

    public static int extractHighNibble(int unsignedByte) {
        return (unsignedByte >>> 4) & 0x0F;
    }

    public static int extractLowNibble(int unsignedByte) {
        return unsignedByte & 0x0F;
    }

    public static int extractBit(int unsignedByte, int bitIndex) {
        if (bitIndex < 0 || bitIndex > 7) {
            throw new IllegalArgumentException("bitIndex must be 0..7");
        }
        return ((unsignedByte & 0xFF) >>> bitIndex) & 1;
    }

    public void setHexNibble(int nibble) {
        int masked = nibble & 0x0F;
        if (masked == currentNibble) {
            return;
        }
        this.currentNibble = masked;
        draw();
    }

    public int getHexNibble() {
        return currentNibble & 0x0F;
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        int mask = encodeHexDigit(currentNibble);
        double padX = 1.0;
        double padY = 1.0;
        double thick = 5.0;
        double horizLen = w - 2 * padX - 2 * thick;
        double vertLen = (h - 2 * padY - 3 * thick) * 0.5;

        double leftX = padX;
        double rightX = w - padX - thick;
        double horizX = padX + thick;

        double topY = padY;
        double midY = padY + thick + vertLen;
        double botY = h - padY - thick;

        // a (top horizontal)
        drawSegment(gc, horizX, topY, horizLen, thick, (mask & SEG_A) != 0);
        // b (top-right vertical)
        drawSegment(gc, rightX, topY + thick, thick, vertLen, (mask & SEG_B) != 0);
        // c (bottom-right vertical)
        drawSegment(gc, rightX, midY + thick, thick, vertLen, (mask & SEG_C) != 0);
        // d (bottom horizontal)
        drawSegment(gc, horizX, botY, horizLen, thick, (mask & SEG_D) != 0);
        // e (bottom-left vertical)
        drawSegment(gc, leftX, midY + thick, thick, vertLen, (mask & SEG_E) != 0);
        // f (top-left vertical)
        drawSegment(gc, leftX, topY + thick, thick, vertLen, (mask & SEG_F) != 0);
        // g (middle horizontal)
        drawSegment(gc, horizX, midY, horizLen, thick, (mask & SEG_G) != 0);
    }

    private void drawSegment(GraphicsContext gc, double x, double y, double w, double h, boolean lit) {
        gc.setFill(lit ? Theme.GREEN : Theme.GREEN_OFF);
        gc.fillRoundRect(x, y, w, h, 3, 3);
    }
}
