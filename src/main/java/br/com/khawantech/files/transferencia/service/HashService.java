package br.com.khawantech.files.transferencia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@Service
public class HashService {

    public String calcularSHA256(byte[] dados) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(dados);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Erro ao calcular SHA-256: {}", e.getMessage());
            throw new RuntimeException("Algoritmo SHA-256 não disponível", e);
        }
    }

    public String calcularSHA256FromBase64(String base64Data) {
        byte[] dados = Base64.getDecoder().decode(base64Data);
        return calcularSHA256(dados);
    }

    public boolean verificarHash(byte[] dados, String hashEsperado) {
        String hashCalculado = calcularSHA256(dados);
        return hashCalculado.equalsIgnoreCase(hashEsperado);
    }

    public boolean verificarHashBase64(String base64Data, String hashEsperado) {
        String hashCalculado = calcularSHA256FromBase64(base64Data);
        return hashCalculado.equalsIgnoreCase(hashEsperado);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
