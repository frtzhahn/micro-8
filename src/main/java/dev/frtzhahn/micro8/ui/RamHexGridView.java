package dev.frtzhahn.micro8.ui;

import dev.frtzhahn.micro8.cpu.TraceRow;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Diff-updated 16x16 RAM Hex Grid (256 bytes) highlighting current PC (blue) and MAR (yellow).
 */
public final class RamHexGridView extends GridPane {

    @FunctionalInterface
    public interface CellPokeHandler {
        void onPoke(int address, int unsignedValue);
    }

    private final Label[] cellLabels = new Label[256];
    private final int[] renderedValues = new int[256];
    private int highlightedPc = -1;
    private int highlightedMar = -1;
    private CellPokeHandler pokeHandler;

    public RamHexGridView() {
        setHgap(2.0);
        setVgap(2.0);
        Arrays.fill(renderedValues, -1);
        buildGrid();
    }

    public void setCellPokeHandler(CellPokeHandler pokeHandler) {
        this.pokeHandler = pokeHandler;
    }

    public void updateFromRamAndPointers(byte[] ramBytes, int pc, int mar) {
        int maskedPc = pc & 0xFF;
        int maskedMar = mar & 0xFF;

        if (ramBytes != null && ramBytes.length >= 256) {
            for (int addr = 0; addr < 256; addr++) {
                int val = Byte.toUnsignedInt(ramBytes[addr]);
                if (renderedValues[addr] != val) {
                    renderedValues[addr] = val;
                    cellLabels[addr].setText(hex2Plain(val));
                    Theme.toggleClass(cellLabels[addr], "ram-cell--zero", val == 0);
                }
            }
        }

        if (highlightedPc != maskedPc) {
            if (highlightedPc >= 0 && highlightedPc < 256) {
                cellLabels[highlightedPc].getStyleClass().remove("ram-cell--pc");
            }
            cellLabels[maskedPc].getStyleClass().add("ram-cell--pc");
            highlightedPc = maskedPc;
        }

        if (highlightedMar != maskedMar) {
            if (highlightedMar >= 0 && highlightedMar < 256) {
                cellLabels[highlightedMar].getStyleClass().remove("ram-cell--mar");
            }
            cellLabels[maskedMar].getStyleClass().add("ram-cell--mar");
            highlightedMar = maskedMar;
        }
    }

    public static byte[] parseHexDump(String rawHex) {
        byte[] result = new byte[256];
        if (rawHex == null || rawHex.isBlank()) {
            return result;
        }
        String[] tokens = rawHex.trim().split("[\\s,;]+");
        int addr = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (addr >= 256) {
                throw new IllegalArgumentException("Hex dump exceeds 256-byte RAM capacity (0xFF)");
            }
            int byteVal = parseSingleHexByte(token);
            result[addr++] = (byte) (byteVal & 0xFF);
        }
        return result;
    }

    public static int parseSingleHexByte(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Empty hex byte token");
        }
        String clean = token.trim();
        if (clean.startsWith("0x") || clean.startsWith("0X")) {
            clean = clean.substring(2);
        } else if (clean.startsWith("$")) {
            clean = clean.substring(1);
        }
        int val;
        try {
            val = Integer.parseInt(clean, 16);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid hex byte '" + token + "'", ex);
        }
        if (val < 0 || val > 255) {
            throw new IllegalArgumentException("Hex byte out of unsigned range 0x00..0xFF: " + token);
        }
        return val & 0xFF;
    }

    public static void pruneTraceRowsAfterCycle(List<TraceRow> rows, int maxCycleCount) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        rows.removeIf(row -> row.cycleCount() > maxCycleCount);
    }

    public static List<String> formatRowAddresses() {
        List<String> rows = new ArrayList<>(16);
        for (int r = 0; r < 16; r++) {
            rows.add(hex2Plain(r << 4) + ":");
        }
        return rows;
    }

    private void buildGrid() {
        for (int col = 0; col < 16; col++) {
            Label colHdr = new Label(Integer.toHexString(col).toUpperCase(Locale.ROOT));
            colHdr.getStyleClass().add("ram-grid__header");
            colHdr.setMinWidth(20.0);
            colHdr.setMaxWidth(Double.MAX_VALUE);
            colHdr.setAlignment(Pos.CENTER);
            add(colHdr, col + 1, 0);
        }

        for (int row = 0; row < 16; row++) {
            Label rowHdr = new Label(hex2Plain(row << 4));
            rowHdr.getStyleClass().add("ram-grid__header");
            rowHdr.setMinWidth(24.0);
            add(rowHdr, 0, row + 1);

            for (int col = 0; col < 16; col++) {
                int addr = (row << 4) | col;
                Label cell = new Label("00");
                cell.getStyleClass().addAll("ram-cell", "ram-cell--zero");
                cell.setMinWidth(20.0);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setAlignment(Pos.CENTER);
                cell.setOnMouseClicked(evt -> {
                    if (evt.getClickCount() >= 1 && pokeHandler != null) {
                        promptCellPoke(addr);
                    }
                });
                cellLabels[addr] = cell;
                renderedValues[addr] = 0;
                add(cell, col + 1, row + 1);
            }
        }
    }

    private void promptCellPoke(int address) {
        int currentVal = renderedValues[address] < 0 ? 0 : renderedValues[address];
        TextInputDialog dialog = new TextInputDialog("0x" + hex2Plain(currentVal));
        dialog.setTitle("Poke RAM Byte");
        dialog.setHeaderText("Edit RAM[0x" + hex2Plain(address) + "]");
        dialog.setContentText("Hex value (0x00..0xFF):");
        Optional<String> res = dialog.showAndWait();
        res.ifPresent(input -> {
            try {
                int parsed = parseSingleHexByte(input);
                pokeHandler.onPoke(address, parsed);
            } catch (IllegalArgumentException ignored) {
                // ignore malformed user input
            }
        });
    }

    public static String hex2Plain(int value) {
        int v = value & 0xFF;
        char high = Character.toUpperCase(Character.forDigit((v >>> 4) & 0x0F, 16));
        char low = Character.toUpperCase(Character.forDigit(v & 0x0F, 16));
        return "" + high + low;
    }
}
