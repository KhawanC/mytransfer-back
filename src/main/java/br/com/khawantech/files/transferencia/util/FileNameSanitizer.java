package br.com.khawantech.files.transferencia.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileNameSanitizer {

    private static final int MAX_FILENAME_LENGTH = 255;
    
    private static final String DANGEROUS_CHARS_REGEX = "[<>:\"/\\\\|?*\\x00-\\x1F]";
    
    private static final String PATH_TRAVERSAL_REGEX = "\\.\\.[\\\\/]";


    public static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Nome do arquivo não pode ser vazio");
        }

        String sanitized = fileName.trim();
        

        sanitized = sanitized.replaceAll(PATH_TRAVERSAL_REGEX, "");
        
        sanitized = sanitized.replaceAll("^[A-Za-z]:", "");
        sanitized = sanitized.replaceAll("^[\\\\/]+", "");

        sanitized = sanitized.replaceAll(DANGEROUS_CHARS_REGEX, "_");
        
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
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
        
        if (sanitized.isBlank()) {
            log.warn("Nome do arquivo resultou em string vazia após sanitização: {}", fileName);
            throw new IllegalArgumentException("Nome do arquivo contém apenas caracteres inválidos");
        }
        
        if (!sanitized.equals(fileName)) {
            log.debug("Nome do arquivo sanitizado: '{}' -> '{}'", fileName, sanitized);
        }
        
        return sanitized;
    }

    public static boolean isValid(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        
        if (fileName.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        
        if (fileName.matches(".*" + PATH_TRAVERSAL_REGEX + ".*")) {
            return false;
        }
        
        if (fileName.matches(".*" + DANGEROUS_CHARS_REGEX + ".*")) {
            return false;
        }
        
        if (fileName.matches("^[A-Za-z]:.*") || fileName.matches("^[\\\\/].*")) {
            return false;
        }
        
        return true;
    }
}
