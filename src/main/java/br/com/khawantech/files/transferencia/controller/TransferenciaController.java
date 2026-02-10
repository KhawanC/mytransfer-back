package br.com.khawantech.files.transferencia.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.khawantech.files.transferencia.dto.AprovarEntradaRequest;
import br.com.khawantech.files.transferencia.dto.ArquivoResponse;
import br.com.khawantech.files.transferencia.dto.ChatHistoricoResponse;
import br.com.khawantech.files.transferencia.dto.EntrarSessaoRequest;
import br.com.khawantech.files.transferencia.dto.EnviarChunkRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoDetalhadoResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoUploadResponse;
import br.com.khawantech.files.transferencia.dto.RejeitarEntradaRequest;
import br.com.khawantech.files.transferencia.dto.SairSessaoRequest;
import br.com.khawantech.files.transferencia.dto.SessaoEstatisticasResponse;
import br.com.khawantech.files.transferencia.dto.SessaoLimitesResponse;
import br.com.khawantech.files.transferencia.dto.SessaoResponse;
import br.com.khawantech.files.transferencia.dto.UploadPendenteResponse;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.service.ArquivoService;
import br.com.khawantech.files.transferencia.service.ChatService;
import br.com.khawantech.files.transferencia.service.SessaoService;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import br.com.khawantech.files.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/transferencia")
@RequiredArgsConstructor
public class TransferenciaController {

    private final SessaoService sessaoService;
    private final ArquivoService arquivoService;
    private final ChatService chatService;
    private final WebSocketNotificationService notificationService;

    @GetMapping("/sessoes")
    public ResponseEntity<List<SessaoResponse>> listarSessoes(@AuthenticationPrincipal User user) {
        log.info("REST: Listando sessões do usuário: {}", user.getId());
        List<SessaoResponse> sessoes = sessaoService.listarSessoesUsuario(user.getId());
        return ResponseEntity.ok(sessoes);
    }

    @PostMapping("/sessao")
    public ResponseEntity<SessaoResponse> criarSessao(@AuthenticationPrincipal User user) {
        log.info("REST: Criando sessão para usuário: {}", user.getId());
        SessaoResponse response = sessaoService.criarSessao(user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessao/entrar")
    public ResponseEntity<SessaoResponse> entrarSessao(
            @Valid @RequestBody EntrarSessaoRequest request,
            @AuthenticationPrincipal User user) {
        log.info("REST: Usuário {} entrando na sessão", user.getId());
        SessaoResponse response = sessaoService.entrarSessao(request.getHashConexao(), user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessao/aprovar")
    public ResponseEntity<SessaoResponse> aprovarEntrada(
            @Valid @RequestBody AprovarEntradaRequest request,
            @AuthenticationPrincipal User user) {
        log.info("REST: Usuário {} aprovando entrada na sessão {}", user.getId(), request.getSessaoId());
        SessaoResponse response = sessaoService.aprovarEntrada(request.getSessaoId(), user.getId(), request.getUsuarioId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessao/rejeitar")
    public ResponseEntity<Void> rejeitarEntrada(
            @Valid @RequestBody RejeitarEntradaRequest request,
            @AuthenticationPrincipal User user) {
        log.info("REST: Usuário {} rejeitando entrada na sessão {}", user.getId(), request.getSessaoId());
        sessaoService.rejeitarEntrada(request.getSessaoId(), user.getId(), request.getUsuarioId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessao/sair")
    public ResponseEntity<Void> sairDaSessao(
            @Valid @RequestBody SairSessaoRequest request,
            @AuthenticationPrincipal User user) {
        log.info("REST: Usuário convidado {} saindo da sessão {}", user.getId(), request.getSessaoId());
        sessaoService.sairDaSessao(request.getSessaoId(), user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessao/{sessaoId}")
    public ResponseEntity<SessaoResponse> buscarSessao(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, user.getId());

        String usuarioConvidadoId = sessao.getUsuarioConvidadoId();
        if (usuarioConvidadoId == null && sessao.getUsuariosConvidadosIds() != null && !sessao.getUsuariosConvidadosIds().isEmpty()) {
            usuarioConvidadoId = sessao.getUsuariosConvidadosIds().get(0);
        }

        String usuarioConvidadoPendenteId = sessao.getUsuarioConvidadoPendenteId();
        String nomeUsuarioConvidadoPendente = sessao.getNomeUsuarioConvidadoPendente();
        if (usuarioConvidadoPendenteId == null && sessao.getUsuariosPendentes() != null && !sessao.getUsuariosPendentes().isEmpty()) {
            var pendente = sessao.getUsuariosPendentes().get(0);
            usuarioConvidadoPendenteId = pendente.getUsuarioId();
            nomeUsuarioConvidadoPendente = pendente.getNomeUsuario();
        }

        return ResponseEntity.ok(SessaoResponse.builder()
            .id(sessao.getId())
            .hashConexao(sessao.getHashConexao())
            .status(sessao.getStatus())
            .usuarioCriadorId(sessao.getUsuarioCriadorId())
            .usuarioConvidadoId(usuarioConvidadoId)
            .usuarioConvidadoPendenteId(usuarioConvidadoPendenteId)
            .nomeUsuarioConvidadoPendente(nomeUsuarioConvidadoPendente)
            .usuariosConvidadosIds(sessao.getUsuariosConvidadosIds() != null
                ? List.copyOf(sessao.getUsuariosConvidadosIds())
                : List.of())
            .usuariosPendentes(sessao.getUsuariosPendentes() != null
                ? sessao.getUsuariosPendentes().stream()
                    .map(pendente -> br.com.khawantech.files.transferencia.dto.PendenteEntradaResponse.builder()
                        .usuarioId(pendente.getUsuarioId())
                        .nomeUsuario(pendente.getNomeUsuario())
                        .solicitadoEm(pendente.getSolicitadoEm())
                        .build())
                    .toList()
                : List.of())
            .totalArquivosTransferidos(sessao.getTotalArquivosTransferidos())
            .criadaEm(sessao.getCriadaEm())
            .expiraEm(sessao.getExpiraEm())
            .hashExpiraEm(sessao.getHashExpiraEm())
            .podeUpload(sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.ATIVA ||
                       sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.AGUARDANDO)
            .podeEncerrar(sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.ATIVA || 
                         sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.AGUARDANDO || 
                         sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.AGUARDANDO_APROVACAO)
            .estaAtiva(sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.ATIVA || 
                      sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.AGUARDANDO || 
                      sessao.getStatus() == br.com.khawantech.files.transferencia.entity.StatusSessao.AGUARDANDO_APROVACAO)
            .build());
    }

    @GetMapping("/sessao/{sessaoId}/estatisticas")
    public ResponseEntity<SessaoEstatisticasResponse> obterEstatisticasSessao(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Usuário {} consultando estatísticas da sessão {}", user.getId(), sessaoId);
        
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, user.getId());
        
        SessaoEstatisticasResponse estatisticas = sessaoService.obterEstatisticasSessao(sessaoId);
        return ResponseEntity.ok(estatisticas);
    }

    @DeleteMapping("/sessao/{sessaoId}")
    public ResponseEntity<Void> encerrarSessao(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Encerrando sessão {} pelo usuário {}", sessaoId, user.getId());
        sessaoService.encerrarSessao(sessaoId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/arquivo/upload")
    public ResponseEntity<IniciarUploadResponse> iniciarUpload(
            @Valid @RequestBody IniciarUploadRequest request,
            @AuthenticationPrincipal User user) {
        log.info("REST: Iniciando upload para sessão {}", request.getSessaoId());
        IniciarUploadResponse response = arquivoService.iniciarUpload(request, user.getId());
        
        if (!response.isArquivoDuplicado()) {
            notificationService.notificarUploadIniciado(
                request.getSessaoId(),
                response.getArquivoId(),
                response.getNomeArquivo()
            );
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/arquivo/chunk")
    public ResponseEntity<ProgressoUploadResponse> enviarChunk(
            @Valid @RequestBody EnviarChunkRequest request,
            @AuthenticationPrincipal User user) {
        ProgressoUploadResponse response = arquivoService.processarChunk(request, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessao/{sessaoId}/arquivos")
    public ResponseEntity<List<ArquivoResponse>> listarArquivos(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        List<ArquivoResponse> arquivos = arquivoService.listarArquivosSessao(sessaoId, user.getId());
        return ResponseEntity.ok(arquivos);
    }

    @GetMapping("/arquivo/{arquivoId}/progresso")
    public ResponseEntity<ProgressoDetalhadoResponse> getProgressoDetalhado(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Consultando progresso detalhado do arquivo {}", arquivoId);
        ProgressoDetalhadoResponse response = arquivoService.getProgressoDetalhado(arquivoId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessao/{sessaoId}/uploads-pendentes")
    public ResponseEntity<List<UploadPendenteResponse>> listarUploadsPendentes(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Listando uploads pendentes da sessão {} para usuário {}", sessaoId, user.getId());
        List<UploadPendenteResponse> uploads = arquivoService.buscarUploadsPendentes(sessaoId, user.getId());
        return ResponseEntity.ok(uploads);
    }

    @GetMapping("/arquivo/{arquivoId}/download")
    public ResponseEntity<DownloadResponse> gerarDownload(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        String url = arquivoService.gerarUrlDownload(arquivoId, user.getId());
        return ResponseEntity.ok(new DownloadResponse(arquivoId, url));
    }

    @GetMapping("/sessao/{sessaoId}/limites")
    public ResponseEntity<SessaoLimitesResponse> buscarLimitesSessao(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Buscando limites da sessão {}", sessaoId);
        SessaoLimitesResponse limites = sessaoService.buscarLimitesSessao(sessaoId);
        return ResponseEntity.ok(limites);
    }

    @DeleteMapping("/arquivo/{arquivoId}")
    public ResponseEntity<Void> excluirArquivo(
            @PathVariable String arquivoId,
            @AuthenticationPrincipal User user) {
        log.info("REST: Excluindo arquivo {} pelo usuário {}", arquivoId, user.getId());
        arquivoService.excluirArquivo(arquivoId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessao/{sessaoId}/chat/historico")
    public ResponseEntity<ChatHistoricoResponse> obterHistoricoChat(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        ChatHistoricoResponse response = chatService.obterHistorico(sessaoId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessao/{sessaoId}/chat/leitura")
    public ResponseEntity<Void> registrarLeituraChat(
            @PathVariable String sessaoId,
            @AuthenticationPrincipal User user) {
        chatService.registrarLeitura(sessaoId, user.getId());
        return ResponseEntity.noContent().build();
    }

    public record DownloadResponse(String arquivoId, String urlDownload) {}
}
