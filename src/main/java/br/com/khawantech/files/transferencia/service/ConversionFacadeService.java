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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversionFacadeService {

    private final ArquivoRepository arquivoRepository;
    private final ImageConversionService imageConversionService;
    private final VideoConversionService videoConversionService;

    private static final List<StatusArquivo> STATUS_CONVERSAO_ATIVOS = List.of(StatusArquivo.PROCESSANDO, StatusArquivo.COMPLETO);

    public boolean isConversivel(String mimeType) {
        return imageConversionService.isImagemConversivel(mimeType) || videoConversionService.isVideoConversivel(mimeType);
    }

    public List<String> getFormatosDisponiveis(String arquivoId) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            return List.of();
        }

        return getFormatosDisponiveis(arquivo);
    }

    public List<String> getFormatosDisponiveis(Arquivo arquivo) {
        String mimeType = arquivo.getTipoMime();

        Set<String> formatosConvertidos = getFormatosJaConvertidos(arquivo.getId());

        if (FormatoVideo.isVideoLike(mimeType)) {
            return videoConversionService.getFormatosDisponiveis(mimeType).stream()
                .filter(formato -> !formatosConvertidos.contains(formato.apiValue().toUpperCase(Locale.ROOT)))
                .map(FormatoVideo::apiValue)
                .toList();
        }

        return imageConversionService.getFormatosDisponiveis(mimeType).stream()
            .filter(formato -> !formatosConvertidos.contains(formato.name()))
            .map(Enum::name)
            .toList();
    }

    public void converterArquivo(String arquivoId, String formatoDestino, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        if (FormatoVideo.isVideoLike(arquivo.getTipoMime())) {
            if (FormatoVideo.fromApiValue(formatoDestino).isEmpty()) {
                throw new ConversaoNaoSuportadaException("Formato de conversão não disponível para este arquivo");
            }
            videoConversionService.converterVideo(arquivoId, formatoDestino, solicitante);
            return;
        }

        if (FormatoImagem.fromMimeType(arquivo.getTipoMime()).isPresent()) {
            try {
                FormatoImagem.valueOf(formatoDestino);
            } catch (IllegalArgumentException e) {
                throw new ConversaoNaoSuportadaException("Formato de conversão não disponível para este arquivo");
            }
        }

        imageConversionService.converterImagem(arquivoId, formatoDestino, solicitante);
    }

    private Set<String> getFormatosJaConvertidos(String arquivoOriginalId) {
        return arquivoRepository.findByArquivoOriginalIdAndStatusIn(arquivoOriginalId, STATUS_CONVERSAO_ATIVOS).stream()
            .map(Arquivo::getFormatoConvertido)
            .filter(Objects::nonNull)
            .map(valor -> valor.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }
}
