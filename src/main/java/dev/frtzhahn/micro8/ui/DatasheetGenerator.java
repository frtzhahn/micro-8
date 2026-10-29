package dev.frtzhahn.micro8.ui;

import dev.frtzhahn.micro8.cpu.ControlSignals;
import dev.frtzhahn.micro8.cpu.ControlSignals.SignalSpec;
import dev.frtzhahn.micro8.db.InstructionDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a self-contained HTML ISA and microcode datasheet from SQLite state, styled with the shared {@link Theme}.
 */
public final class DatasheetGenerator {

    private static final String[] FLAG_LABELS = {
            "Z=0, C=0 (flag 0)",
            "Z=1, C=0 (flag 1)",
            "Z=0, C=1 (flag 2)",
            "Z=1, C=1 (flag 3)"
    };

    private DatasheetGenerator() {
    }

    public static String generateHtml(List<InstructionDef> instructions, int[][][] controlStore) {
        List<InstructionDef> sorted = new ArrayList<>(instructions == null ? List.of() : instructions);
        sorted.sort(Comparator.comparingInt(def -> def.opcode() & 0xFF));

        StringBuilder html = new StringBuilder(8192);
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <title>MICRO-8 ISA Datasheet</title>
                <style>
                    :root { /*PALETTE*/ }
                    body {
                        background-color: var(--bg);
                        color: var(--text);
                        font-family: "JetBrains Mono", "Cascadia Mono", Consolas, monospace;
                        font-size: 12px;
                        margin: 0 0 14px 0;
                    }
                    h1, h2 {
                        font-family: "Segoe UI", system-ui, sans-serif;
                        font-weight: bold;
                    }
                    h1 {
                        font-size: 14px;
                        margin: 0 0 4px 0;
                    }
                    h2 {
                        color: var(--muted);
                        font-size: 12px;
                        margin: 18px 0 8px 0;
                    }
                    .subtitle {
                        color: var(--muted);
                        margin-bottom: 12px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 14px;
                    }
                    th {
                        color: var(--faint);
                        font-weight: normal;
                        text-align: left;
                        padding: 6px 8px;
                        border-bottom: 1px solid var(--line);
                    }
                    td {
                        padding: 6px 8px;
                        border-bottom: 1px solid var(--line);
                        vertical-align: top;
                    }
                    .card {
                        background-color: var(--surface);
                        border: 1px solid var(--line);
                        border-radius: 8px;
                        padding: 10px 12px;
                        margin-bottom: 12px;
                    }
                    .card-header {
                        font-weight: bold;
                        margin-bottom: 6px;
                    }
                    .badge-builtin, .badge-custom, .sig-driver, .sig-consumer {
                        display: inline-block;
                        padding: 1px 7px;
                        border: 1px solid var(--line-strong);
                        border-radius: 10px;
                        font-size: 11px;
                        white-space: nowrap;
                    }
                    .badge-builtin {
                        color: var(--muted);
                    }
                    .badge-custom {
                        color: var(--purple);
                        border-color: var(--purple);
                    }
                    .sig-driver, .sig-consumer {
                        margin-right: 4px;
                    }
                    .sig-driver {
                        color: var(--blue);
                        border-color: var(--blue);
                        background-color: var(--blue-soft);
                    }
                    .sig-consumer {
                        color: var(--green);
                        border-color: var(--green);
                        background-color: var(--green-soft);
                    }
                    .sig-idle {
                        color: var(--faint);
                    }
                    .opcode-hex {
                        color: var(--muted);
                    }
                    .mnemonic {
                        font-weight: bold;
                    }
                </style>
                </head>
                <body>
                <h1>MICRO-8 ISA datasheet</h1>
                <div class="subtitle">Byte 0 = 8-bit Opcode | Byte 1 = 8-bit Operand/Address or 0x00 Padding | 6-Phase Sequencer (T0-T5)</div>
                <h2>Instruction summary</h2>
                <table>
                    <thead>
                        <tr>
                            <th>Opcode</th>
                            <th>Mnemonic</th>
                            <th>2-Byte Encoding</th>
                            <th>Type</th>
                            <th>Description</th>
                        </tr>
                    </thead>
                    <tbody>
                """.replace("/*PALETTE*/", paletteVars()));

        for (InstructionDef def : sorted) {
            int op = def.opcode() & 0xFF;
            String hexOp = formatHex2(op);
            String formatStr = def.hasOperand() ? "Opcode + Operand (2B)" : "Opcode + 0x00 (2B)";
            String badgeClass = def.builtin() ? "badge-builtin" : "badge-custom";
            String badgeText = def.builtin() ? "Built-in" : "Custom";

            html.append("<tr>")
                    .append("<td class=\"opcode-hex\">").append(hexOp).append("</td>")
                    .append("<td class=\"mnemonic\">").append(escapeHtml(def.mnemonic())).append("</td>")
                    .append("<td>").append(formatStr).append("</td>")
                    .append("<td><span class=\"").append(badgeClass).append("\">").append(badgeText).append("</span></td>")
                    .append("<td>").append(escapeHtml(def.description())).append("</td>")
                    .append("</tr>\n");
        }

        html.append("""
                    </tbody>
                </table>
                <h2>Microcode control store (T0 - T5)</h2>
                """);

        for (InstructionDef def : sorted) {
            int op = def.opcode() & 0xFF;
            String hexOp = formatHex2(op);
            String badgeClass = def.builtin() ? "badge-builtin" : "badge-custom";
            String badgeText = def.builtin() ? "Built-in" : "Custom";

            html.append("<div class=\"card\">\n")
                    .append("<div class=\"card-header\">")
                    .append(hexOp).append(" - ").append(escapeHtml(def.mnemonic()))
                    .append(" <span class=\"").append(badgeClass).append("\">").append(badgeText).append("</span>")
                    .append(" - ").append(escapeHtml(def.description()))
                    .append("</div>\n")
                    .append("<table><thead><tr><th style=\"width:70px;\">T-State</th><th>Control Signals (32-Bit Control Word)</th></tr></thead><tbody>\n");

            for (int t = 0; t < 6; t++) {
                int w0 = getWord(controlStore, op, t, 0);
                int w1 = getWord(controlStore, op, t, 1);
                int w2 = getWord(controlStore, op, t, 2);
                int w3 = getWord(controlStore, op, t, 3);
                boolean uniformFlags = (w0 == w1) && (w0 == w2) && (w0 == w3);

                html.append("<tr><td><strong>T").append(t).append("</strong></td><td>");
                if (uniformFlags) {
                    html.append(formatSignalBadgesHtml(w0));
                } else {
                    for (int flag = 0; flag < 4; flag++) {
                        int word = getWord(controlStore, op, t, flag);
                        html.append("<div><strong>").append(FLAG_LABELS[flag]).append(":</strong> ")
                                .append(formatSignalBadgesHtml(word))
                                .append("</div>");
                    }
                }
                html.append("</td></tr>\n");
            }
            html.append("</tbody></table></div>\n");
        }

        html.append("</body></html>");
        return html.toString();
    }

    /** CSS custom properties exposing the shared {@link Theme} palette to the datasheet styles. */
    private static String paletteVars() {
        return "--bg:" + Theme.css(Theme.BG)
                + ";--surface:" + Theme.css(Theme.SURFACE)
                + ";--line:" + Theme.css(Theme.LINE)
                + ";--line-strong:" + Theme.css(Theme.LINE_STRONG)
                + ";--text:" + Theme.css(Theme.TEXT)
                + ";--muted:" + Theme.css(Theme.MUTED)
                + ";--faint:" + Theme.css(Theme.FAINT)
                + ";--blue:" + Theme.css(Theme.BLUE)
                + ";--blue-soft:" + Theme.css(Theme.BLUE_SOFT)
                + ";--green:" + Theme.css(Theme.GREEN)
                + ";--green-soft:" + Theme.css(Theme.GREEN_SOFT)
                + ";--purple:" + Theme.css(Theme.PURPLE)
                + ";";
    }

    private static int getWord(int[][][] controlStore, int opcode, int tState, int flagState) {
        if (controlStore == null
                || opcode < 0 || opcode >= controlStore.length
                || tState < 0 || tState >= controlStore[opcode].length
                || flagState < 0 || flagState >= controlStore[opcode][tState].length) {
            return 0;
        }
        return controlStore[opcode][tState][flagState];
    }

    private static String formatSignalBadgesHtml(int controlWord) {
        if (controlWord == 0) {
            return "<span class=\"sig-idle\">- (0x00000000)</span>";
        }
        StringBuilder sb = new StringBuilder();
        for (SignalSpec spec : ControlSignals.ALL_SIGNALS) {
            if ((controlWord & spec.mask()) != 0) {
                String css = spec.busDriver() ? "sig-driver" : "sig-consumer";
                sb.append("<span class=\"").append(css).append("\">")
                        .append(spec.name())
                        .append("</span>");
            }
        }
        sb.append("<span class=\"sig-idle\">(")
                .append(escapeHtml(ControlSignals.formatActiveSignals(controlWord)))
                .append(")</span>");
        return sb.toString();
    }

    private static String formatHex2(int value) {
        int v = value & 0xFF;
        char high = Character.toUpperCase(Character.forDigit((v >>> 4) & 0x0F, 16));
        char low = Character.toUpperCase(Character.forDigit(v & 0x0F, 16));
        return "0x" + high + low;
    }

    private static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
