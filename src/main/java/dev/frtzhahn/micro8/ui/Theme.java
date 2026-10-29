package dev.frtzhahn.micro8.ui;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/**
 * Palette and fonts for canvas-drawn views and generated HTML. Mirrors the looked-up colors at the top of workbench.css.
 * Neutral monochrome base with GitHub (Primer dark) role colors: blue = active now, green = output/success,
 * yellow = memory address, red = fault.
 */
final class Theme {

    static final Color BG = Color.web("#0f0f11");
    static final Color SURFACE = Color.web("#151517");
    static final Color HOVER = Color.web("#222226");
    static final Color LINE = Color.web("#242428");
    static final Color LINE_STRONG = Color.web("#34343a");
    static final Color TEXT = Color.web("#ececee");
    static final Color MUTED = Color.web("#8b8b93");
    static final Color FAINT = Color.web("#55555c");

    static final Color BLUE = Color.web("#4493f8");
    static final Color BLUE_EMPH = Color.web("#1f6feb");
    static final Color BLUE_SOFT = Color.web("#388bfd26");
    static final Color GREEN = Color.web("#3fb950");
    static final Color GREEN_SOFT = Color.web("#2ea04326");
    static final Color GREEN_OFF = Color.web("#14241a");
    static final Color YELLOW = Color.web("#d29922");
    static final Color RED = Color.web("#f85149");
    static final Color RED_SOFT = Color.web("#f8514926");
    static final Color PURPLE = Color.web("#d2a8ff");

    static final String SANS = "System";
    static final String MONO = "JetBrains Mono";

    private static final List<String> BUNDLED_FONTS = List.of("JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf");

    private Theme() {
    }

    /** Registers the bundled JetBrains Mono faces; call once before any node or canvas uses {@link #MONO}. */
    static void loadFonts() {
        for (String file : BUNDLED_FONTS) {
            try (InputStream in = Theme.class.getResourceAsStream("fonts/" + file)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to load bundled font " + file, ex);
            }
        }
    }

    /** CSS color string for embedding the palette in generated HTML. */
    static String css(Color color) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255),
                color.getOpacity());
    }

    /** Adds or removes a style class without duplicating it. */
    static void toggleClass(Node node, String styleClass, boolean on) {
        if (on) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }
}
