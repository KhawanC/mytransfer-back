package br.com.khawantech.files.transferencia.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    private final ArquivoService arquivoService;
    private final SessaoService sessaoService;
    private final MinioService minioService;
    private final DownloadTokenService downloadTokenService;

    /**
     * Endpoint para gerar token temporário de download.
     * Requer autenticação JWT.
     */
    @GetMapping("/download/{arquivoId}")
    public ResponseEntity<TokenResponse> gerarTokenDownload(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        
        try {
            log.info("Gerando token de download para arquivo: {} pelo usuário: {}", arquivoId, user.getId());
            
            // Buscar arquivo e validar permissões
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, user.getId());

            // Gerar token temporário
            String token = downloadTokenService.gerarToken(arquivoId, user.getId());
            
            return ResponseEntity.ok(new TokenResponse(token));

        } catch (Exception e) {
            log.error("Erro ao gerar token de download: {}", arquivoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint público para download de arquivo usando token temporário.
     * Não requer autenticação JWT - usa token de uso único.
     */
    @GetMapping("/d/{token}")
    public ResponseEntity<InputStreamResource> downloadComToken(@PathVariable String token) {
        
        try {
            // Validar e consumir token
            String[] tokenData = downloadTokenService.validarEConsumirToken(token);
            if (tokenData == null) {
                log.warn("Tentativa de download com token inválido: {}", token);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String arquivoId = tokenData[0];
            String usuarioId = tokenData[1];

            log.info("Download iniciado via token para arquivo: {} pelo usuário: {}", arquivoId, usuarioId);
            
            // Buscar arquivo e validar permissões
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

            // Obter dados do arquivo do MinIO
            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivo.getCaminhoMinio());

            // Preparar headers para download
            HttpHeaders headers = new HttpHeaders();
            
            // Definir Content-Type
            String contentType = arquivo.getTipoMime();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.setContentType(MediaType.parseMediaType(contentType));
            
            // Definir tamanho
            headers.setContentLength(arquivo.getTamanhoBytes());
            
            // Forçar download com nome original do arquivo
            String encodedFileName = URLEncoder.encode(arquivo.getNomeOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename*=UTF-8''" + encodedFileName);
            
            // Cache control - não cachear arquivos privados
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

    /**
     * Endpoint para preview/visualização de arquivo no navegador usando token.
     * Útil para imagens, PDFs, etc.
     */
    @GetMapping("/p/{token}")
    public ResponseEntity<InputStreamResource> previewComToken(@PathVariable String token) {
        
        try {
            // Validar e consumir token
            String[] tokenData = downloadTokenService.validarEConsumirToken(token);
            if (tokenData == null) {
                log.warn("Tentativa de preview com token inválido: {}", token);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String arquivoId = tokenData[0];
            String usuarioId = tokenData[1];

            log.info("Preview iniciado via token para arquivo: {} pelo usuário: {}", arquivoId, usuarioId);
            
            // Buscar arquivo e validar permissões
            Arquivo arquivo = arquivoService.buscarArquivoPorId(arquivoId);
            Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
            sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

            // Obter dados do arquivo do MinIO
            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivo.getCaminhoMinio());

            // Preparar headers para preview
            HttpHeaders headers = new HttpHeaders();
            
            String contentType = arquivo.getTipoMime();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(arquivo.getTamanhoBytes());
            
            // Para preview, usar inline ao invés de attachment
            String encodedFileName = URLEncoder.encode(arquivo.getNomeOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "inline; filename*=UTF-8''" + encodedFileName);
            
            // Cache de 1 hora para previews
            headers.setCacheControl("private, max-age=3600");

            log.info("Preview concluído com sucesso via token");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(arquivoData.inputStream()));

        } catch (Exception e) {
            log.error("Erro ao fazer preview com token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public record TokenResponse(String token) {}
}
