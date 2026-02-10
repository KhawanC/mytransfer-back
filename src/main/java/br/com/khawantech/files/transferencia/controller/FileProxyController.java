package br.com.khawantech.files.transferencia.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.service.ArquivoService;
import br.com.khawantech.files.transferencia.service.DownloadTokenService;
import br.com.khawantech.files.transferencia.service.MinioService;
import br.com.khawantech.files.transferencia.service.SessaoService;
import br.com.khawantech.files.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProxyController {

    private static final Set<String> PREVIEW_MIME_ALLOWLIST = Set.of(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "application/pdf"
    );

    private static final Set<String> SCRIPTABLE_MIME_DENYLIST = Set.of(
        "text/html",
        "application/xhtml+xml",
        "image/svg+xml"
    );

    private final ArquivoService arquivoService;
    private final SessaoService sessaoService;
    private final MinioService minioService;
    private final DownloadTokenService downloadTokenService;

    @GetMapping("/download/{arquivoId}")
    public ResponseEntity<TokenResponse> gerarTokenDownload(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        
        try {
            log.info("Gerando token de download para arquivo: {} pelo usuário: {}", arquivoId, user.getId());
            
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, user.getId());

            if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            String token = downloadTokenService.gerarToken(arquivoId, user.getId());
            
            return ResponseEntity.ok(new TokenResponse(token));

        } catch (Exception e) {
            log.error("Erro ao gerar token de download: {}", arquivoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/d/{token}")
    public ResponseEntity<InputStreamResource> downloadComToken(@PathVariable String token) {
        
        try {
            String[] tokenData = downloadTokenService.validarEConsumirToken(token);
            if (tokenData == null) {
                log.warn("Tentativa de download com token inválido");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String arquivoId = tokenData[0];
            String usuarioId = tokenData[1];

            log.info("Download iniciado via token para arquivo: {} pelo usuário: {}", arquivoId, usuarioId);
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

            if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus()) || arquivo.getCaminhoMinio() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivo.getCaminhoMinio());

            HttpHeaders headers = new HttpHeaders();
            
            String contentType = arquivo.getTipoMime();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            String normalized = normalizeMime(contentType);
            if (SCRIPTABLE_MIME_DENYLIST.contains(normalized)) {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            } else {
                headers.setContentType(safeParseMediaType(contentType));
            }
            
            headers.setContentLength(arquivo.getTamanhoBytes());
            
            String encodedFileName = URLEncoder.encode(arquivo.getNomeOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename*=UTF-8''" + encodedFileName);

            headers.set("X-Content-Type-Options", "nosniff");
            
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            log.info("Download proxy concluído com sucesso: {}", arquivoId);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(arquivoData.inputStream()));

        } catch (Exception e) {
            log.error("Erro ao fazer download com token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/p/{token}")
    public ResponseEntity<InputStreamResource> previewComToken(@PathVariable String token) {
        
        try {
            String[] tokenData = downloadTokenService.validarEConsumirToken(token);
            if (tokenData == null) {
                log.warn("Tentativa de preview com token inválido");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String arquivoId = tokenData[0];
            String usuarioId = tokenData[1];

            log.info("Preview iniciado via token para arquivo: {} pelo usuário: {}", arquivoId, usuarioId);
            
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

            if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus()) || arquivo.getCaminhoMinio() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivo.getCaminhoMinio());

            HttpHeaders headers = new HttpHeaders();
            
            String contentType = arquivo.getTipoMime();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            String normalized = normalizeMime(contentType);
            boolean allowInline = PREVIEW_MIME_ALLOWLIST.contains(normalized);
            if (allowInline) {
                headers.setContentType(safeParseMediaType(contentType));
            } else {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            headers.setContentLength(arquivo.getTamanhoBytes());
            
            String encodedFileName = URLEncoder.encode(arquivo.getNomeOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                (allowInline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedFileName);
            
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Content-Security-Policy",
                "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox");

            headers.setCacheControl("no-store");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            log.info("Preview concluído com sucesso via token");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(arquivoData.inputStream()));

        } catch (Exception e) {
            log.error("Erro ao fazer preview com token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "application/octet-stream";
        }
        return mime.strip().toLowerCase(Locale.ROOT);
    }

    private static MediaType safeParseMediaType(String mime) {
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record TokenResponse(String token) {}
}
