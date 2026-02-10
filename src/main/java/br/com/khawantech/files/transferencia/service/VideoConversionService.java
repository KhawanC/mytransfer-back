package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import br.com.khawantech.files.transferencia.dto.VideoConversionEvent;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import br.com.khawantech.files.transferencia.exception.EspacoSessaoInsuficienteException;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.service.video.VideoConversionProfile;
import br.com.khawantech.files.transferencia.service.video.VideoConversionStrategy;
import br.com.khawantech.files.transferencia.service.video.VideoStrategyResolver;
import br.com.khawantech.files.transferencia.util.FileNameSanitizer;
import br.com.khawantech.files.user.entity.User;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoConversionService {

    private final ArquivoRepository arquivoRepository;
    private final MinioService minioService;
    private final MinioClient minioClient;
    private final SessaoService sessaoService;
    private final WebSocketNotificationService notificationService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final VideoStrategyResolver videoStrategyResolver;

    @Value("${ffmpeg.bin-dir:/usr/bin}")
    private String ffmpegBinDir;

    public boolean isVideoConversivel(String mimeType) {
        return FormatoVideo.isVideoLike(mimeType);
    }

    public List<FormatoVideo> getFormatosDisponiveis(String mimeType) {
        if (!FormatoVideo.isVideoLike(mimeType)) {
            return List.of();
        }
        return FormatoVideo.getFormatosDisponiveis(mimeType);
    }

    @Transactional
    public void converterVideo(String arquivoId, String formatoDestino, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        validarConversao(arquivo, formatoDestino, solicitante);

        VideoConversionEvent event = VideoConversionEvent.builder()
            .arquivoId(arquivoId)
            .sessaoId(arquivo.getSessaoId())
            .formatoDestino(formatoDestino)
            .solicitanteId(solicitante.getId())
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_VIDEO_CONVERSION,
            event
        );

        log.info("Conversão de vídeo solicitada: arquivo={}, formato={}", arquivoId, formatoDestino);
    }

    private void validarConversao(Arquivo arquivo, String formatoDestino, User solicitante) {
        if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus())) {
            throw new ConversaoNaoSuportadaException("Arquivo ainda não está completo");
        }

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            throw new ConversaoNaoSuportadaException("Este arquivo não suporta conversão");
        }

        FormatoVideo formato = FormatoVideo.fromApiValue(formatoDestino)
            .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de conversão não suportado"));

        List<FormatoVideo> formatosDisponiveis = getFormatosDisponiveis(arquivo.getTipoMime());
        if (!formatosDisponiveis.contains(formato)) {
            throw new ConversaoNaoSuportadaException("Formato de conversão não disponível para este arquivo");
        }

        Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, solicitante.getId());

        if (!sessaoService.podeAdicionarArquivo(arquivo.getSessaoId())) {
            throw new EspacoSessaoInsuficienteException("Sessão atingiu o limite de arquivos");
        }
    }

    @Transactional
    public void processarConversao(VideoConversionEvent event) {
        Path tempInputPath = null;
        Path tempOutputPath = null;

        try {
            log.info("Iniciando processamento de conversão de vídeo: {}", event);

            Arquivo arquivoOriginal = arquivoRepository.findById(event.getArquivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo original não encontrado"));

            FormatoVideo formatoDestino = FormatoVideo.fromApiValue(event.getFormatoDestino())
                .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de conversão não suportado"));

            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivoOriginal.getCaminhoMinio());

            tempInputPath = Files.createTempFile("video_input_", "." + detectarExtensaoEntrada(arquivoOriginal));
            try (InputStream inputStream = arquivoData.inputStream()) {
                Files.copy(inputStream, tempInputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            tempOutputPath = Files.createTempFile("video_output_", "." + formatoDestino.getExtension());

            executarConversao(tempInputPath, tempOutputPath, formatoDestino);

            long tamanhoBytes = Files.size(tempOutputPath);
            Arquivo arquivoConvertido = criarArquivoConvertido(arquivoOriginal, formatoDestino, tamanhoBytes);

            String caminhoMinio = String.format("%s/%s/%s",
                arquivoConvertido.getSessaoId(),
                arquivoConvertido.getId(),
                arquivoConvertido.getNomeOriginal()
            );

            try (InputStream outputStream = Files.newInputStream(tempOutputPath)) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(properties.getMinioBucket())
                        .object(caminhoMinio)
                        .stream(outputStream, tamanhoBytes, -1)
                        .contentType(formatoDestino.getMimeType())
                        .build()
                );
            }

            arquivoConvertido.setCaminhoMinio(caminhoMinio);
            arquivoConvertido.setStatus(StatusArquivo.COMPLETO);
            arquivoRepository.save(arquivoConvertido);

            notificationService.notificarConversaoConcluida(arquivoConvertido.getSessaoId(), arquivoConvertido);

            log.info("Conversão de vídeo concluída com sucesso: arquivoOriginal={}, arquivoNovo={}, formato={}",
                event.getArquivoId(), arquivoConvertido.getId(), formatoDestino.apiValue());

        } catch (Exception e) {
            log.error("Erro ao processar conversão de vídeo: {}", event, e);
            throw new RuntimeException("Falha na conversão de vídeo: " + e.getMessage(), e);
        } finally {
            limparArquivosTemporarios(tempInputPath, tempOutputPath);
        }
    }

    private void executarConversao(Path inputPath, Path outputPath, FormatoVideo formatoDestino) {
        VideoConversionStrategy strategy = videoStrategyResolver.resolve(formatoDestino);
        VideoConversionProfile profile = strategy.profile();

        UrlOutput output = UrlOutput.toPath(outputPath)
            .setFormat(profile.format());

        List<String> args = profile.args();
        if (args.size() % 2 != 0) {
            throw new IllegalStateException("Perfil de conversão inválido: argumentos precisam ser pares (opção, valor)");
        }
        for (int i = 0; i < args.size(); i += 2) {
            output.addArguments(args.get(i), args.get(i + 1));
        }

        FFmpeg.atPath(Path.of(ffmpegBinDir))
            .addInput(UrlInput.fromPath(inputPath))
            .addOutput(output)
            .setOverwriteOutput(true)
            .execute();
    }

    private String detectarExtensaoEntrada(Arquivo arquivo) {
        return FormatoVideo.fromMimeType(arquivo.getTipoMime())
            .map(FormatoVideo::getExtension)
            .orElseGet(() -> {
                int lastDot = arquivo.getNomeOriginal() != null ? arquivo.getNomeOriginal().lastIndexOf('.') : -1;
                if (lastDot > 0 && lastDot < arquivo.getNomeOriginal().length() - 1) {
                    return arquivo.getNomeOriginal().substring(lastDot + 1);
                }
                return "bin";
            });
    }

    private Arquivo criarArquivoConvertido(Arquivo original, FormatoVideo formatoDestino, long tamanhoBytes) {
        String novoNome = gerarNomeArquivoConvertido(original.getNomeOriginal(), formatoDestino);

        Arquivo arquivoConvertido = Arquivo.builder()
            .id(UUID.randomUUID().toString())
            .sessaoId(original.getSessaoId())
            .nomeOriginal(novoNome)
            .tamanhoBytes(tamanhoBytes)
            .tipoMime(formatoDestino.getMimeType())
            .status(StatusArquivo.PROCESSANDO)
            .remetenteId(original.getRemetenteId())
            .conversivel(false)
            .arquivoOriginalId(original.getId())
            .formatoConvertido(formatoDestino.apiValue())
            .totalChunks(1)
            .chunksRecebidos(1)
            .progressoUpload(100.0)
            .build();

        return arquivoRepository.save(arquivoConvertido);
    }

    private String gerarNomeArquivoConvertido(String nomeOriginal, FormatoVideo formatoDestino) {
        int lastDot = nomeOriginal != null ? nomeOriginal.lastIndexOf('.') : -1;
        String nomeBase = (nomeOriginal != null && lastDot > 0) ? nomeOriginal.substring(0, lastDot) : (nomeOriginal == null ? "arquivo" : nomeOriginal);
        String novoNome = nomeBase + "_converted." + formatoDestino.getExtension();
        return FileNameSanitizer.sanitize(novoNome);
    }

    private void limparArquivosTemporarios(Path... paths) {
        for (Path path : paths) {
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    log.warn("Erro ao deletar arquivo temporário: {}", path, e);
                }
            }
        }
    }
}
