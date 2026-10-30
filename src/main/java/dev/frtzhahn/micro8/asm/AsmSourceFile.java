package dev.frtzhahn.micro8.asm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AsmSourceFile {

    public static final long MAX_FILE_SIZE = 65_536L;

    private AsmSourceFile() {
    }

    public static String read(Path path) throws IOException, IllegalArgumentException {
        if (path == null) {
            throw new IllegalArgumentException("File path must not be null");
        }
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            throw new IllegalArgumentException("Invalid file path: " + path);
        }
        String fileName = fileNamePath.toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".asm")) {
            throw new IllegalArgumentException("File must have a .asm extension: " + fileName);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a regular file: " + path);
        }

        long size = Files.size(path);
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File exceeds maximum allowed size of 64 KB (" + size + " bytes): " + fileName
            );
        }

        byte[] bytes = Files.readAllBytes(path);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                throw new IllegalArgumentException(
                        "File contains binary NUL byte (0x00) at index " + i + ": " + fileName
                );
            }
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("File contains invalid UTF-8 encoding: " + fileName, ex);
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            if (c < 32 || c == 127 || Character.isISOControl(c)) {
                throw new IllegalArgumentException(
                        "File contains non-printable control character (0x"
                                + Integer.toHexString(c) + ") at index " + i + ": " + fileName
                );
            }
        }

        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
