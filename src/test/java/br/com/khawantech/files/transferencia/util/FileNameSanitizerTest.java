package br.com.khawantech.files.transferencia.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FileNameSanitizerTest {

    @Test
    void testPathTraversalPrevention() {
        assertEquals("etc_passwd_malicious.txt", FileNameSanitizer.sanitize("../../etc/passwd/../malicious.txt"));
        assertEquals("windows_system32_file.txt", FileNameSanitizer.sanitize("..\\..\\windows\\system32\\file.txt"));
    }

    @Test
    void testDangerousCharactersReplacement() {
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file<>:\"|?*name.txt"));
        assertEquals("my_file.pdf", FileNameSanitizer.sanitize("my/file.pdf"));
    }

    @Test
    void testAbsolutePathRemoval() {

        assertEquals("Users_file.txt", FileNameSanitizer.sanitize("C:\\Users\\file.txt"));
        assertEquals("home_user_file.txt", FileNameSanitizer.sanitize("/home/user/file.txt"));
    }

    @Test
    void testMaxLengthEnforcement() {
        String longName = "a".repeat(300) + ".txt";
        String sanitized = FileNameSanitizer.sanitize(longName);
        assertTrue(sanitized.length() <= 255);
        assertTrue(sanitized.endsWith(".txt"));
    }

    @Test
    void testNormalFileNames() {
        assertEquals("document.pdf", FileNameSanitizer.sanitize("document.pdf"));
        assertEquals("my-photo_2024.jpg", FileNameSanitizer.sanitize("my-photo_2024.jpg"));
        assertEquals("Report (Final).docx", FileNameSanitizer.sanitize("Report (Final).docx"));
    }

    @Test
    void testEmptyOrNullInput() {
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize(null));
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize(""));
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize("   "));
    }

    @Test
    void testOnlyInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize("<>:\"|?*"));
    }

    @Test
    void testIsValidMethod() {
        assertTrue(FileNameSanitizer.isValid("normal-file.txt"));
        assertFalse(FileNameSanitizer.isValid("../../etc/passwd"));
        assertFalse(FileNameSanitizer.isValid("file<>.txt"));
        assertFalse(FileNameSanitizer.isValid("C:\\file.txt"));
        assertFalse(FileNameSanitizer.isValid("a".repeat(300)));
    }

    @Test
    void testMultipleUnderscoresRemoval() {
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file___name.txt"));
        assertEquals("test_file_.pdf", FileNameSanitizer.sanitize("___test___file___.pdf"));
    }

    @Test
    void testUnicodeCharacters() {
        assertEquals("arquivo-português.txt", FileNameSanitizer.sanitize("arquivo-português.txt"));
        assertEquals("文档.pdf", FileNameSanitizer.sanitize("文档.pdf"));
        assertEquals("Документ.docx", FileNameSanitizer.sanitize("Документ.docx"));
    }

    @Test
    void testEdgeCases() {
        assertEquals("file.txt", FileNameSanitizer.sanitize("   file.txt   "));
        assertEquals("my.file.with.dots.txt", FileNameSanitizer.sanitize("my.file.with.dots.txt"));
        assertEquals("file", FileNameSanitizer.sanitize("file"));
    }

    @Test
    void testMixedAttacks() {
        assertEquals("etc_passwd", FileNameSanitizer.sanitize("C:\\\\..\\\\..\\\\etc/passwd"));
        assertEquals("script_malicious_script_.txt", FileNameSanitizer.sanitize("../../../<script>malicious</script>.txt"));
    }
}
