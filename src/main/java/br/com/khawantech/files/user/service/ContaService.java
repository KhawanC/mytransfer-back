package br.com.khawantech.files.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.assinatura.repository.AssinaturaRepository;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.repository.SessaoRepository;
import br.com.khawantech.files.transferencia.service.SessaoCleanupService;
import br.com.khawantech.files.transferencia.service.SessaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaService {

    private final UserService userService;
    private final SessaoRepository sessaoRepository;
    private final SessaoCleanupService sessaoCleanupService;
    private final SessaoService sessaoService;
    private final AssinaturaRepository assinaturaRepository;

    @Transactional
    public void excluirConta(String usuarioId) {
        List<Sessao> sessoes = sessaoRepository.findSessoesDoUsuario(usuarioId);

        for (Sessao sessao : sessoes) {
            if (usuarioId.equals(sessao.getUsuarioCriadorId())) {
                sessaoCleanupService.removerSessaoCompleta(sessao);
            } else {
                sessaoService.removerParticipacaoUsuario(sessao.getId(), usuarioId);
            }
        }

        assinaturaRepository.findByUsuarioId(usuarioId)
            .forEach(assinaturaRepository::delete);

        userService.findById(usuarioId).ifPresent(userService::delete);

        log.info("Conta removida: {}", usuarioId);
    }
}
