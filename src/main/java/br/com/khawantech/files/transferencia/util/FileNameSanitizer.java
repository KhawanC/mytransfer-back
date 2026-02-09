package br.com.khawantech.files.transferencia.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilitário para sanitização segura de nomes de arquivos.
 * Previne ataques de path traversal e injection de caracteres especiais.
 */
@Slf4j
public class FileNameSanitizer {

    private static final int MAX_FILENAME_LENGTH = 255;
    
    // Caracteres perigosos no Windows e Unix: < > : " / \ | ? * e caracteres de controle (0x00-0x1F)
    private static final String DANGEROUS_CHARS_REGEX = "[<>:\"/\\\\|?*\\x00-\\x1F]";
    
    // Path traversal patterns
    private static final String PATH_TRAVERSAL_REGEX = "\\.\\.[\\\\/]";

    /**
     * Sanitiza o nome do arquivo removendo caracteres perigosos e prevenindo path traversal.
     * 
     * @param fileName nome original do arquivo
     * @return nome sanitizado seguro para uso
     * @throws IllegalArgumentException se o nome for null, vazio ou resultar em string vazia após sanitização
     */
    public static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Nome do arquivo não pode ser vazio");
        }

        String sanitized = fileName.trim();
        
        // Remover path traversal attempts (../ ou ..\)
        sanitized = sanitized.replaceAll(PATH_TRAVERSAL_REGEX, "");
        
        // Remover qualquer caminho absoluto
        sanitized = sanitized.replaceAll("^[A-Za-z]:", ""); // Windows drive letters
        sanitized = sanitized.replaceAll("^[\\\\/]+", ""); // Leading slashes
        
        // Substituir caracteres perigosos por underscore
        sanitized = sanitized.replaceAll(DANGEROUS_CHARS_REGEX, "_");
        
        // Remover múltiplos underscores consecutivos
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        // Remover underscore no início e fim
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // Limitar tamanho (preservando extensão se possível)
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            int lastDotIndex = sanitized.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < sanitized.length() - 1) {
                String extension = sanitized.substring(lastDotIndex);
                String name = sanitized.substring(0, lastDotIndex);
                
                int maxNameLength = MAX_FILENAME_LENGTH - extension.length();
                if (maxNameLength > 0) {
                    sanitized = name.substring(0, Math.min(name.length(), maxNameLength)) + extension;
                } else {
                    sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
                }
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
            }
        }
        
        // Validar que ainda temos um nome válido
        if (sanitized.isBlank()) {
            log.warn("Nome do arquivo resultou em string vazia após sanitização: {}", fileName);
            throw new IllegalArgumentException("Nome do arquivo contém apenas caracteres inválidos");
        }
        
        // Log se houve mudança significativa
        if (!sanitized.equals(fileName)) {
            log.debug("Nome do arquivo sanitizado: '{}' -> '{}'", fileName, sanitized);
        }
        
        return sanitized;
    }

    /**
     * Valida se o nome do arquivo é seguro sem modificá-lo.
     * 
     * @param fileName nome do arquivo para validar
     * @return true se o nome é seguro, false caso contrário
     */
    public static boolean isValid(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        
        if (fileName.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        
        // Verificar path traversal
        if (fileName.matches(".*" + PATH_TRAVERSAL_REGEX + ".*")) {
            return false;
        }
        
        // Verificar caracteres perigosos
        if (fileName.matches(".*" + DANGEROUS_CHARS_REGEX + ".*")) {
            return false;
        }
        
        // Verificar caminhos absolutos
        if (fileName.matches("^[A-Za-z]:.*") || fileName.matches("^[\\\\/].*")) {
            return false;
        }
        
        return true;
    }
}
