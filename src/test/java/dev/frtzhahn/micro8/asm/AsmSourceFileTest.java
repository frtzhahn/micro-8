package dev.frtzhahn.micro8.asm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsmSourceFileTest {

    @Test
    void readsValidAssemblyFileAndNormalizesCrlfToLf(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("program.asm");
        String crlfSource = "ORG 0x00\r\nLDA #10\r\nADD #20\r\nHLT\r\n";
        Files.writeString(file, crlfSource, StandardCharsets.UTF_8);

        String result = AsmSourceFile.read(file);
        assertEquals("ORG 0x00\nLDA #10\nADD #20\nHLT\n", result);
    }

    @Test
    void acceptsCaseInsensitiveAsmExtension(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("PROGRAM.ASM");
        Files.writeString(file, "NOP\nHLT\n", StandardCharsets.UTF_8);

        String result = AsmSourceFile.read(file);
        assertEquals("NOP\nHLT\n", result);
    }

    @Test
    void rejectsFilesMissingAsmExtension(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("program.txt");
        Files.writeString(textFile, "NOP\n", StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AsmSourceFile.read(textFile)
        );
        assertTrue(ex.getMessage().contains(".asm extension"));

        Path noExt = tempDir.resolve("program");
        Files.writeString(noExt, "NOP\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> AsmSourceFile.read(noExt));
    }

    @Test
    void rejectsFilesExceeding64Kb(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("oversized.asm");
        byte[] oversizedBytes = new byte[65_537];
        Arrays.fill(oversizedBytes, (byte) 'A');
        Files.write(file, oversizedBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AsmSourceFile.read(file)
        );
        assertTrue(ex.getMessage().contains("64 KB"));
    }

    @Test
    void acceptsFileAtExact64KbLimit(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("exact_limit.asm");
        byte[] exactBytes = new byte[65_536];
        Arrays.fill(exactBytes, (byte) ' ');
        Files.write(file, exactBytes);

        String result = AsmSourceFile.read(file);
        assertEquals(65_536, result.length());
    }

    @Test
    void rejectsBinaryFilesContainingNulBytes(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("binary.asm");
        byte[] binaryData = new byte[]{'N', 'O', 'P', 0x00, 'H', 'L', 'T'};
        Files.write(file, binaryData);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AsmSourceFile.read(file)
        );
        assertTrue(ex.getMessage().contains("NUL byte"));
    }

    @Test
    void rejectsFilesWithNonPrintableControlCharacters(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("control.asm");
        byte[] withBel = new byte[]{'N', 'O', 'P', 0x07, '\n'};
        Files.write(file, withBel);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AsmSourceFile.read(file)
        );
        assertTrue(ex.getMessage().contains("non-printable control character"));
    }

    @Test
    void rejectsInvalidUtf8ByteSequences(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("invalid_utf8.asm");
        byte[] invalidBytes = new byte[]{'N', 'O', 'P', (byte) 0xFF, (byte) 0xFE};
        Files.write(file, invalidBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AsmSourceFile.read(file)
        );
        assertTrue(ex.getMessage().contains("UTF-8"));
    }

    @Test
    void rejectsNullOrNonExistentPath(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> AsmSourceFile.read(null));
        Path nonExistent = tempDir.resolve("does_not_exist.asm");
        assertThrows(IllegalArgumentException.class, () -> AsmSourceFile.read(nonExistent));
    }
}
