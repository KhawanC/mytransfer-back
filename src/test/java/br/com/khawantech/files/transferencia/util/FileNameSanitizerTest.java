package br.com.khawantech.files.transferencia.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileNameSanitizerTest {

    @Test
    void testPathTraversalPrevention() {
        // Tentativas de path traversal devem ser removidas
        // O sanitizador substitui / por _ e remove ../
        assertEquals("etc_passwd_malicious.txt", FileNameSanitizer.sanitize("../../etc/passwd/../malicious.txt"));
        assertEquals("windows_system32_file.txt", FileNameSanitizer.sanitize("..\\..\\windows\\system32\\file.txt"));
    }

    @Test
    void testDangerousCharactersReplacement() {
        // Caracteres perigosos devem ser substituídos por underscore
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file<>:\"|?*name.txt"));
        assertEquals("my_file.pdf", FileNameSanitizer.sanitize("my/file.pdf"));
    }

    @Test
    void testAbsolutePathRemoval() {
        // Caminhos absolutos devem ter slashes e drive letters removidos
        assertEquals("Users_file.txt", FileNameSanitizer.sanitize("C:\\Users\\file.txt"));
        assertEquals("home_user_file.txt", FileNameSanitizer.sanitize("/home/user/file.txt"));
    }

    @Test
    void testMaxLengthEnforcement() {
        // Nome muito longo deve ser truncado
        String longName = "a".repeat(300) + ".txt";
        String sanitized = FileNameSanitizer.sanitize(longName);
        assertTrue(sanitized.length() <= 255);
        assertTrue(sanitized.endsWith(".txt")); // Preserva extensão
    }

    @Test
    void testNormalFileNames() {
        // Nomes normais devem permanecer inalterados
        assertEquals("document.pdf", FileNameSanitizer.sanitize("document.pdf"));
        assertEquals("my-photo_2024.jpg", FileNameSanitizer.sanitize("my-photo_2024.jpg"));
        assertEquals("Report (Final).docx", FileNameSanitizer.sanitize("Report (Final).docx"));
    }

    @Test
    void testEmptyOrNullInput() {
        // Entrada vazia ou null deve lançar exceção
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize(null));
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize(""));
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize("   "));
    }

    @Test
    void testOnlyInvalidCharacters() {
        // Nome contendo apenas caracteres inválidos deve lançar exceção
        assertThrows(IllegalArgumentException.class, () -> FileNameSanitizer.sanitize("<>:\"|?*"));
    }

    @Test
    void testIsValidMethod() {
        // Testar método de validação
        assertTrue(FileNameSanitizer.isValid("normal-file.txt"));
        assertFalse(FileNameSanitizer.isValid("../../etc/passwd"));
        assertFalse(FileNameSanitizer.isValid("file<>.txt"));
        assertFalse(FileNameSanitizer.isValid("C:\\file.txt"));
        assertFalse(FileNameSanitizer.isValid("a".repeat(300)));
    }

    @Test
    void testMultipleUnderscoresRemoval() {
        // Múltiplos underscores devem ser consolidados
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file___name.txt"));
        // underscores no final são removidos (exceto antes da extensão)
        assertEquals("test_file_.pdf", FileNameSanitizer.sanitize("___test___file___.pdf"));
    }

    @Test
    void testUnicodeCharacters() {
        // Caracteres Unicode válidos devem ser preservados
        assertEquals("arquivo-português.txt", FileNameSanitizer.sanitize("arquivo-português.txt"));
        assertEquals("文档.pdf", FileNameSanitizer.sanitize("文档.pdf"));
        assertEquals("Документ.docx", FileNameSanitizer.sanitize("Документ.docx"));
    }

    @Test
    void testEdgeCases() {
        // Casos extremos
        assertEquals("file.txt", FileNameSanitizer.sanitize("   file.txt   "));
        assertEquals("my.file.with.dots.txt", FileNameSanitizer.sanitize("my.file.with.dots.txt"));
        assertEquals("file", FileNameSanitizer.sanitize("file"));
    }

    @Test
    void testMixedAttacks() {
        // Ataques combinados
        assertEquals("etc_passwd", FileNameSanitizer.sanitize("C:\\..\\..\\etc/passwd"));
        // <script> tags são transformados em underscores
        assertEquals("script_malicious_script_.txt", FileNameSanitizer.sanitize("../../../<script>malicious</script>.txt"));
    }
}
