package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.dto.FormatoImagem;
import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OptimizationFacadeService {

    private static final List<Integer> NIVEIS_SUPORTADOS = List.of(25, 50, 75);
    private static final List<StatusArquivo> STATUS_OTIMIZACAO_ATIVOS = List.of(StatusArquivo.PROCESSANDO, StatusArquivo.COMPLETO);

    private final ArquivoRepository arquivoRepository;
    private final ImageOptimizationService imageOptimizationService;
    private final VideoOptimizationService videoOptimizationService;

    public List<Integer> getNiveisDisponiveis(String arquivoId) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            return List.of();
        }

        return getNiveisDisponiveis(arquivo);
    }

    public List<Integer> getNiveisDisponiveis(Arquivo arquivo) {
        if (arquivo.getTag() != null && "OTIMIZADO".equalsIgnoreCase(arquivo.getTag())) {
            return List.of();
        }

        Set<Integer> niveisJaOtimizados = getNiveisJaOtimizados(arquivo.getId());

        return NIVEIS_SUPORTADOS.stream()
            .filter(nivel -> !niveisJaOtimizados.contains(nivel))
            .toList();
    }

    public void otimizarArquivo(String arquivoId, int nivel, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        if (FormatoVideo.isVideoLike(arquivo.getTipoMime())) {
            videoOptimizationService.otimizarVideo(arquivoId, nivel, solicitante);
            return;
        }

        if (FormatoImagem.fromMimeType(arquivo.getTipoMime()).isPresent()) {
            imageOptimizationService.otimizarImagem(arquivoId, nivel, solicitante);
            return;
        }

        throw new ConversaoNaoSuportadaException("Otimização não disponível para este arquivo");
    }

    private Set<Integer> getNiveisJaOtimizados(String arquivoOriginalId) {
        return arquivoRepository.findByArquivoOriginalIdAndStatusIn(arquivoOriginalId, STATUS_OTIMIZACAO_ATIVOS).stream()
            .map(Arquivo::getOtimizacaoNivel)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
