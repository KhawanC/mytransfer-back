package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.dto.FormatoImagem;
import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversionFacadeService {

    private final ArquivoRepository arquivoRepository;
    private final ImageConversionService imageConversionService;
    private final VideoConversionService videoConversionService;

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

        if (FormatoVideo.isVideoLike(mimeType)) {
            return videoConversionService.getFormatosDisponiveis(mimeType).stream()
                .map(FormatoVideo::apiValue)
                .toList();
        }

        return imageConversionService.getFormatosDisponiveis(mimeType).stream()
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
}
