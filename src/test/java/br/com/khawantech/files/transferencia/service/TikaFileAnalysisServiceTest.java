package br.com.khawantech.files.transferencia.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TikaFileAnalysisServiceTest {

    @Test
    void deveDetectarMimePngPorMagicBytes() {
        byte[] pngHeader = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };

        TikaFileAnalysisService service = new TikaFileAnalysisService();
        TikaFileAnalysisService.AnaliseTikaResponse response = service.analisar(pngHeader);

        assertEquals("image/png", response.tipoMimeDetectado());
        assertNotNull(response.metadados());
    }
}
