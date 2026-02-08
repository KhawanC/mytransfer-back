package br.com.khawantech.files.transferencia.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class QRCodeService {

    private static final int QR_CODE_SIZE = 300;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public String gerarQRCodeBase64(String hashConexao) {
        try {
            String url = frontendUrl + "/transfer/" + hashConexao;
            
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] qrCodeBytes = outputStream.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(qrCodeBytes);

            log.debug("QR Code gerado para hash: {}", hashConexao);
            return "data:image/png;base64," + base64;

        } catch (WriterException | IOException e) {
            log.error("Erro ao gerar QR Code: {}", e.getMessage());
            throw new RuntimeException("Erro ao gerar QR Code", e);
        }
    }
}
