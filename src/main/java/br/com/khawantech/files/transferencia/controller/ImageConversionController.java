package br.com.khawantech.files.transferencia.controller;

import br.com.khawantech.files.transferencia.dto.ConversaoRequest;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.service.ConversionFacadeService;
import br.com.khawantech.files.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transferencia/arquivo")
@RequiredArgsConstructor
public class ImageConversionController {

    private final ConversionFacadeService conversionFacadeService;
    private final ArquivoRepository arquivoRepository;

    @PostMapping("/{arquivoId}/converter")
    public ResponseEntity<Map<String, String>> converterArquivo(
            @PathVariable String arquivoId,
            @Valid @RequestBody ConversaoRequest request,
            @AuthenticationPrincipal User user) {
        
        log.info("REST: Usuário {} solicitando conversão do arquivo {} para formato {}", 
            user.getId(), arquivoId, request.getFormato());

        conversionFacadeService.converterArquivo(arquivoId, request.getFormato(), user);

        return ResponseEntity.accepted()
            .body(Map.of("message", "Conversão iniciada com sucesso"));
    }

    @GetMapping("/{arquivoId}/formatos-disponiveis")
    public ResponseEntity<List<String>> getFormatosDisponiveis(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        
        log.info("REST: Usuário {} consultando formatos disponíveis para arquivo {}", 
            user.getId(), arquivoId);

        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(conversionFacadeService.getFormatosDisponiveis(arquivo));
    }
}
