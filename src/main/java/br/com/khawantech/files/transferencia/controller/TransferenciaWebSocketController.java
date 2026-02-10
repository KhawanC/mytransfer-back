package br.com.khawantech.files.transferencia.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import br.com.khawantech.files.transferencia.dto.AprovarEntradaRequest;
import br.com.khawantech.files.transferencia.dto.ChatDigitandoRequest;
import br.com.khawantech.files.transferencia.dto.ChatDigitandoResponse;
import br.com.khawantech.files.transferencia.dto.ChatMensagemRequest;
import br.com.khawantech.files.transferencia.dto.ChatMensagemResponse;
import br.com.khawantech.files.transferencia.dto.EncerrarSessaoRequest;
import br.com.khawantech.files.transferencia.dto.EntrarSessaoRequest;
import br.com.khawantech.files.transferencia.dto.EnviarChunkRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoUploadResponse;
import br.com.khawantech.files.transferencia.dto.RejeitarEntradaRequest;
import br.com.khawantech.files.transferencia.dto.SairSessaoRequest;
import br.com.khawantech.files.transferencia.dto.SessaoResponse;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.service.ArquivoService;
import br.com.khawantech.files.transferencia.service.ChatService;
import br.com.khawantech.files.transferencia.service.SessaoService;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import br.com.khawantech.files.transferencia.util.FileNameSanitizer;
import br.com.khawantech.files.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TransferenciaWebSocketController {

    private final SessaoService sessaoService;
    private final ArquivoService arquivoService;
    private final ChatService chatService;
    private final WebSocketNotificationService notificationService;

    @MessageMapping("/sessao/criar")
    @SendToUser("/queue/sessao")
    public SessaoResponse criarSessao(SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Criando sessão para usuário: {}", usuarioId);

        SessaoResponse response = sessaoService.criarSessao(usuarioId);
        return response;
    }

    @MessageMapping("/sessao/entrar")
    @SendToUser("/queue/sessao")
    public SessaoResponse entrarSessao(@Payload EntrarSessaoRequest request,
                                        SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Usuário {} entrando na sessão com hash: {}", usuarioId, request.getHashConexao());

        SessaoResponse response = sessaoService.entrarSessao(request.getHashConexao(), usuarioId);

        return response;
    }

    @MessageMapping("/sessao/aprovar")
    @SendToUser("/queue/sessao")
    public SessaoResponse aprovarEntrada(@Payload AprovarEntradaRequest request,
                                          SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Usuário {} aprovando entrada na sessão {}", usuarioId, request.getSessaoId());

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        SessaoResponse response = sessaoService.aprovarEntrada(request.getSessaoId(), usuarioId);

        return response;
    }

    @MessageMapping("/sessao/rejeitar")
    @SendToUser("/queue/sessao")
    public void rejeitarEntrada(@Payload RejeitarEntradaRequest request,
                                SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Usuário {} rejeitando entrada na sessão {}", usuarioId, request.getSessaoId());

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        sessaoService.rejeitarEntrada(request.getSessaoId(), usuarioId);
    }

    @MessageMapping("/sessao/sair")
    @SendToUser("/queue/sessao")
    public void sairDaSessao(@Payload SairSessaoRequest request,
                             SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Usuário convidado {} saindo da sessão {}", usuarioId, request.getSessaoId());

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        sessaoService.sairDaSessao(request.getSessaoId(), usuarioId);
    }

    @MessageMapping("/sessao/encerrar")
    @SendToUser("/queue/sessao")
    public void encerrarSessao(@Payload EncerrarSessaoRequest request,
                               SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Usuário {} encerrando sessão: {}", usuarioId, request.getSessaoId());

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        sessaoService.encerrarSessao(request.getSessaoId(), usuarioId);
        notificationService.notificarSessaoEncerrada(request.getSessaoId(), "Sessão encerrada pelo usuário");
    }

    @MessageMapping("/arquivo/iniciar")
    @SendToUser("/queue/upload")
    public IniciarUploadResponse iniciarUpload(@Payload IniciarUploadRequest request,
                                                SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        log.info("WebSocket: Iniciando upload para usuário: {} na sessão: {}", usuarioId, request.getSessaoId());

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        String nomeOriginal = request.getNomeArquivo();
        String nomeSanitizado = FileNameSanitizer.sanitize(nomeOriginal);
        request.setNomeArquivo(nomeSanitizado);

        IniciarUploadResponse response = arquivoService.iniciarUpload(request, usuarioId);

        if (!response.isArquivoDuplicado()) {
            notificationService.notificarUploadIniciado(
                request.getSessaoId(),
                response.getArquivoId(),
                response.getNomeArquivo()
            );
        }

        return response;
    }

    @MessageMapping("/arquivo/chunk")
    @SendToUser("/queue/progresso")
    public ProgressoUploadResponse enviarChunk(@Payload EnviarChunkRequest request,
                                                SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarSessaoAtiva(sessao);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        log.debug("WebSocket Chunk: Usuário {} enviando chunk {} do arquivo {} na sessão {}", 
                 usuarioId, request.getNumeroChunk(), request.getArquivoId(), request.getSessaoId());

        ProgressoUploadResponse response = arquivoService.processarChunk(request, usuarioId);

        notificationService.notificarProgresso(request.getSessaoId(), response);

        if (response.isCompleto()) {
            notificationService.notificarUploadCompleto(
                request.getSessaoId(),
                response.getArquivoId(),
                response.getNomeArquivo()
            );

            if (response.getUrlDownload() != null) {
                notificationService.notificarArquivoDisponivel(
                    request.getSessaoId(),
                    response.getArquivoId(),
                    response.getNomeArquivo(),
                    response.getUrlDownload(),
                    false
                );
            }
        }

        return response;
    }

    @MessageMapping("/chat/enviar")
    public void enviarMensagemChat(@Payload ChatMensagemRequest request,
                                   SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);

        ChatMensagemResponse response = chatService.enviarMensagem(request, usuarioId);
        notificationService.notificarChatMensagem(request.getSessaoId(), response);
    }

    @MessageMapping("/chat/digitando")
    public void enviarDigitando(@Payload ChatDigitandoRequest request,
                                SimpMessageHeaderAccessor headerAccessor) {
        String usuarioId = getUsuarioId(headerAccessor);
        String usuarioNome = getUsuarioNome(headerAccessor);

        ChatDigitandoResponse response = chatService.registrarDigitando(
            request.getSessaoId(),
            usuarioId,
            usuarioNome,
            request.isDigitando()
        );

        notificationService.notificarChatDigitando(request.getSessaoId(), response);
    }

    private String getUsuarioId(SimpMessageHeaderAccessor headerAccessor) {
        var sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userObj = sessionAttributes.get("user");
            if (userObj instanceof User user) {
                return user.getId();
            }

            Object userIdObj = sessionAttributes.get("userId");
            if (userIdObj != null) {
                return userIdObj.toString();
            }
        }

        Principal principal = headerAccessor.getUser();
        if (principal != null) {
            return principal.getName();
        }

        throw new RuntimeException("Usuário não autenticado");
    }

    private String getUsuarioNome(SimpMessageHeaderAccessor headerAccessor) {
        var sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userObj = sessionAttributes.get("user");
            if (userObj instanceof User user) {
                return user.getName();
            }
        }

        return "Usuário";
    }
}
