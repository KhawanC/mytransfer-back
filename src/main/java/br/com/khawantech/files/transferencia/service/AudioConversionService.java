package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.AudioConversionEvent;
import br.com.khawantech.files.transferencia.dto.FormatoAudio;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import br.com.khawantech.files.transferencia.exception.EspacoSessaoInsuficienteException;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioConversionService {

    private final ArquivoRepository arquivoRepository;
    private final MinioService minioService;
    private final MinioClient minioClient;
    private final SessaoService sessaoService;
    private final WebSocketNotificationService notificationService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ffmpeg.bin-dir:/usr/bin}")
    private String ffmpegBinDir;

    private static final List<StatusArquivo> STATUS_CONVERSAO_ATIVOS = List.of(StatusArquivo.PROCESSANDO, StatusArquivo.COMPLETO);

    public boolean isAudioConversivel(String mimeType) {
        return FormatoAudio.isAudioConversivel(mimeType);
    }

    public List<FormatoAudio> getFormatosDisponiveis(String mimeType) {
        if (!FormatoAudio.isAudioConversivel(mimeType)) {
            return List.of();
        }
        return FormatoAudio.getFormatosDisponiveis(mimeType);
    }

    @Transactional
    public void converterAudio(String arquivoId, String formatoDestino, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        validarConversao(arquivo, formatoDestino, solicitante);

        AudioConversionEvent event = AudioConversionEvent.builder()
            .arquivoId(arquivoId)
            .sessaoId(arquivo.getSessaoId())
            .formatoDestino(formatoDestino)
            .solicitanteId(solicitante.getId())
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_AUDIO_CONVERSION,
            event
        );

        log.info("Conversão de áudio solicitada: arquivo={}, formato={}", arquivoId, formatoDestino);
    }

    private void validarConversao(Arquivo arquivo, String formatoDestino, User solicitante) {
        if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus())) {
            throw new ConversaoNaoSuportadaException("Arquivo ainda não está completo");
        }

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            throw new ConversaoNaoSuportadaException("Este arquivo não suporta conversão");
        }

        FormatoAudio formato = FormatoAudio.fromApiValue(formatoDestino)
            .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de conversão não suportado"));

        FormatoAudio.fromMimeType(arquivo.getTipoMime()).ifPresent(formatoAtual -> {
            if (formatoAtual == formato) {
                throw new ConversaoNaoSuportadaException("Arquivo já está no formato solicitado");
            }
        });

        if (isConversaoDuplicada(arquivo.getId(), formatoDestino)) {
            throw new ConversaoNaoSuportadaException("Arquivo já foi convertido para este formato");
        }

        List<FormatoAudio> formatosDisponiveis = getFormatosDisponiveisFiltrados(arquivo);
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
    public void processarConversao(AudioConversionEvent event) {
        Path tempInputPath = null;
        Path tempOutputPath = null;

        try {
            log.info("Iniciando processamento de conversão de áudio: {}", event);

            Arquivo arquivoOriginal = arquivoRepository.findById(event.getArquivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo original não encontrado"));

            FormatoAudio formatoDestino = FormatoAudio.fromApiValue(event.getFormatoDestino())
                .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de conversão não suportado"));

            Arquivo arquivoConvertido = criarArquivoConvertido(arquivoOriginal, formatoDestino, arquivoOriginal.getTamanhoBytes());
            notificationService.notificarArquivoProcessando(arquivoConvertido.getSessaoId(), arquivoConvertido);

            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivoOriginal.getCaminhoMinio());

            tempInputPath = Files.createTempFile("audio_input_", "." + detectarExtensaoEntrada(arquivoOriginal));
            try (InputStream inputStream = arquivoData.inputStream()) {
                Files.copy(inputStream, tempInputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            tempOutputPath = Files.createTempFile("audio_output_", "." + formatoDestino.getExtension());

            executarConversao(tempInputPath, tempOutputPath, formatoDestino);

            long tamanhoBytes = Files.size(tempOutputPath);
            arquivoConvertido.setTamanhoBytes(tamanhoBytes);

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

            sessaoService.incrementarArquivosTransferidos(arquivoConvertido.getSessaoId());

            notificationService.notificarConversaoConcluida(arquivoConvertido.getSessaoId(), arquivoConvertido);

            log.info("Conversão de áudio concluída com sucesso: arquivoOriginal={}, arquivoNovo={}, formato={}",
                event.getArquivoId(), arquivoConvertido.getId(), formatoDestino.name());

        } catch (Exception e) {
            log.error("Erro ao processar conversão de áudio: {}", event, e);
            throw new RuntimeException("Falha na conversão de áudio: " + e.getMessage(), e);
        } finally {
            limparArquivosTemporarios(tempInputPath, tempOutputPath);
        }
    }

    private void executarConversao(Path inputPath, Path outputPath, FormatoAudio formatoDestino) {
        UrlOutput output = UrlOutput.toPath(outputPath);

        output.addArguments("-c:a", formatoDestino.getFfmpegCodec());

        if (formatoDestino == FormatoAudio.MP3) {
            output.addArguments("-b:a", "320k");
        } else if (formatoDestino == FormatoAudio.AAC || formatoDestino == FormatoAudio.M4A) {
            output.addArguments("-b:a", "256k");
        } else if (formatoDestino == FormatoAudio.OGG) {
            output.addArguments("-q:a", "6");
        } else if (formatoDestino == FormatoAudio.OPUS || formatoDestino == FormatoAudio.WEBM) {
            output.addArguments("-b:a", "128k");
        }

        FFmpeg.atPath(Path.of(ffmpegBinDir))
            .addInput(UrlInput.fromPath(inputPath))
            .addOutput(output)
            .setOverwriteOutput(true)
            .execute();
    }

    private String detectarExtensaoEntrada(Arquivo arquivo) {
        return FormatoAudio.fromMimeType(arquivo.getTipoMime())
            .map(FormatoAudio::getExtension)
            .orElseGet(() -> {
                int lastDot = arquivo.getNomeOriginal() != null ? arquivo.getNomeOriginal().lastIndexOf('.') : -1;
                if (lastDot > 0 && lastDot < arquivo.getNomeOriginal().length() - 1) {
                    return arquivo.getNomeOriginal().substring(lastDot + 1);
                }
                return "bin";
            });
    }

    private Arquivo criarArquivoConvertido(Arquivo original, FormatoAudio formatoDestino, long tamanhoBytes) {
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
            .formatoConvertido(formatoDestino.name())
            .totalChunks(1)
            .chunksRecebidos(1)
            .progressoUpload(100.0)
            .build();

        return arquivoRepository.save(arquivoConvertido);
    }

    private String gerarNomeArquivoConvertido(String nomeOriginal, FormatoAudio formatoDestino) {
        int lastDot = nomeOriginal != null ? nomeOriginal.lastIndexOf('.') : -1;
        String nomeBase = (nomeOriginal != null && lastDot > 0) ? nomeOriginal.substring(0, lastDot) : (nomeOriginal == null ? "arquivo" : nomeOriginal);
        String novoNome = nomeBase + "_converted." + formatoDestino.getExtension();
        return FileNameSanitizer.sanitize(novoNome);
    }

    private List<FormatoAudio> getFormatosDisponiveisFiltrados(Arquivo arquivo) {
        Set<String> formatosConvertidos = getFormatosConvertidos(arquivo.getId());

        return getFormatosDisponiveis(arquivo.getTipoMime()).stream()
            .filter(formato -> !formatosConvertidos.contains(formato.name().toUpperCase(Locale.ROOT)))
            .toList();
    }

    private Set<String> getFormatosConvertidos(String arquivoOriginalId) {
        return arquivoRepository.findByArquivoOriginalIdAndStatusIn(arquivoOriginalId, STATUS_CONVERSAO_ATIVOS).stream()
            .map(Arquivo::getFormatoConvertido)
            .filter(Objects::nonNull)
            .map(valor -> valor.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    private boolean isConversaoDuplicada(String arquivoOriginalId, String formatoDestino) {
        return arquivoRepository.existsByArquivoOriginalIdAndFormatoConvertidoAndStatusIn(
            arquivoOriginalId,
            formatoDestino,
            STATUS_CONVERSAO_ATIVOS
        );
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
