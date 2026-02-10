package br.com.khawantech.files.transferencia.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.khawantech.files.auth.exception.AuthenticationException;
import br.com.khawantech.files.transferencia.dto.ChatDigitandoResponse;
import br.com.khawantech.files.transferencia.dto.ChatHistoricoResponse;
import br.com.khawantech.files.transferencia.dto.ChatMensagemRequest;
import br.com.khawantech.files.transferencia.dto.ChatMensagemResponse;
import br.com.khawantech.files.transferencia.entity.ChatLeitura;
import br.com.khawantech.files.transferencia.entity.ChatMensagem;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusSessao;
import br.com.khawantech.files.transferencia.repository.ChatLeituraRepository;
import br.com.khawantech.files.transferencia.repository.ChatMensagemRepository;
import br.com.khawantech.files.user.repository.UserRepository;

@Service
public class ChatService {

    private final SessaoService sessaoService;
    private final ChatMensagemRepository chatMensagemRepository;
    private final ChatLeituraRepository chatLeituraRepository;
    private final UserRepository userRepository;

    public ChatService(
        SessaoService sessaoService,
        ChatMensagemRepository chatMensagemRepository,
        ChatLeituraRepository chatLeituraRepository,
        UserRepository userRepository
    ) {
        this.sessaoService = sessaoService;
        this.chatMensagemRepository = chatMensagemRepository;
        this.chatLeituraRepository = chatLeituraRepository;
        this.userRepository = userRepository;
    }

    public ChatHistoricoResponse obterHistorico(String sessaoId, String usuarioId) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        List<ChatMensagemResponse> mensagens = chatMensagemRepository
            .findBySessaoIdOrderByCriadoEmAsc(sessaoId)
            .stream()
            .map(this::toResponse)
            .toList();

        var leitura = chatLeituraRepository.findBySessaoIdAndUsuarioId(sessaoId, usuarioId);
        Instant ultimoLeituraEm = leitura.map(ChatLeitura::getUltimoLeituraEm).orElse(null);

        long naoLidas = ultimoLeituraEm == null
            ? chatMensagemRepository.countBySessaoIdAndRemetenteIdNot(sessaoId, usuarioId)
            : chatMensagemRepository.countBySessaoIdAndRemetenteIdNotAndCriadoEmAfter(
                sessaoId,
                usuarioId,
                ultimoLeituraEm
            );

        return ChatHistoricoResponse.builder()
            .mensagens(mensagens)
            .ultimoLeituraEm(ultimoLeituraEm)
            .naoLidas((int) naoLidas)
            .build();
    }

    public ChatMensagemResponse enviarMensagem(ChatMensagemRequest request, String usuarioId) {
        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        if (sessao.getStatus() != StatusSessao.ATIVA) {
            throw new IllegalStateException("Chat disponível apenas em sessões ativas");
        }

        String conteudo = request.getConteudo() == null ? "" : request.getConteudo().trim();
        if (conteudo.isBlank()) {
            throw new IllegalArgumentException("Mensagem é obrigatória");
        }

        var usuario = userRepository.findById(usuarioId)
            .orElseThrow(() -> new AuthenticationException("Sessão expirada"));

        Instant agora = Instant.now();
        Instant expiraEm = sessao.getExpiraEm() != null ? sessao.getExpiraEm() : agora.plusSeconds(3600);

        ChatMensagem mensagem = new ChatMensagem();
        mensagem.setSessaoId(sessao.getId());
        mensagem.setRemetenteId(usuarioId);
        mensagem.setRemetenteNome(usuario.getName());
        mensagem.setConteudo(conteudo);
        mensagem.setCriadoEm(agora);
        mensagem.setExpiraEm(expiraEm);
        mensagem.generateId();
        chatMensagemRepository.save(mensagem);
        registrarLeitura(sessao, usuarioId, agora);

        return toResponse(mensagem);
    }

    public void registrarLeitura(String sessaoId, String usuarioId) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);
        registrarLeitura(sessao, usuarioId, Instant.now());
    }

    public ChatDigitandoResponse registrarDigitando(String sessaoId, String usuarioId, String usuarioNome, boolean digitando) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        return ChatDigitandoResponse.builder()
            .sessaoId(sessaoId)
            .usuarioId(usuarioId)
            .usuarioNome(usuarioNome)
            .digitando(digitando)
            .timestamp(Instant.now())
            .build();
    }

    private void registrarLeitura(Sessao sessao, String usuarioId, Instant leituraEm) {
        Instant expiraEm = sessao.getExpiraEm() != null ? sessao.getExpiraEm() : leituraEm.plusSeconds(3600);
        String leituraId = buildLeituraId(sessao.getId(), usuarioId);

        ChatLeitura leitura = new ChatLeitura();
        leitura.setId(leituraId);
        leitura.setSessaoId(sessao.getId());
        leitura.setUsuarioId(usuarioId);
        leitura.setUltimoLeituraEm(leituraEm);
        leitura.setExpiraEm(expiraEm);
        chatLeituraRepository.save(leitura);
    }

    private String buildLeituraId(String sessaoId, String usuarioId) {
        return sessaoId + ":" + usuarioId;
    }

    private ChatMensagemResponse toResponse(ChatMensagem mensagem) {
        return ChatMensagemResponse.builder()
            .id(mensagem.getId())
            .sessaoId(mensagem.getSessaoId())
            .remetenteId(mensagem.getRemetenteId())
            .remetenteNome(mensagem.getRemetenteNome())
            .conteudo(mensagem.getConteudo())
            .criadoEm(mensagem.getCriadoEm())
            .build();
    }
}
