package br.com.khawantech.files.transferencia.controller;

import br.com.khawantech.files.transferencia.dto.OtimizacaoRequest;
import br.com.khawantech.files.transferencia.service.OptimizationFacadeService;
import br.com.khawantech.files.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transferencia/arquivo")
@RequiredArgsConstructor
public class OptimizationController {

    private final OptimizationFacadeService optimizationFacadeService;

    @PostMapping("/{arquivoId}/otimizar")
    public ResponseEntity<Map<String, String>> otimizarArquivo(
            @PathVariable String arquivoId,
            @Valid @RequestBody OtimizacaoRequest request,
            @AuthenticationPrincipal User user) {

        log.info("REST: Usuário {} solicitando otimização do arquivo {} (nivel {})",
            user.getId(), arquivoId, request.getNivel());

        optimizationFacadeService.otimizarArquivo(arquivoId, request.getNivel(), user);

        return ResponseEntity.accepted()
            .body(Map.of("message", "Otimização iniciada com sucesso"));
    }

    @GetMapping("/{arquivoId}/otimizacoes-disponiveis")
    public ResponseEntity<List<Integer>> getNiveisDisponiveis(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {

        log.info("REST: Usuário {} consultando níveis de otimização para arquivo {}",
            user.getId(), arquivoId);

        return ResponseEntity.ok(optimizationFacadeService.getNiveisDisponiveis(arquivoId));
    }
}
