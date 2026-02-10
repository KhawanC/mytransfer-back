package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import br.com.khawantech.files.transferencia.dto.VideoOptimizationEvent;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoOptimizationService {

    private static final List<StatusArquivo> STATUS_OTIMIZACAO_ATIVOS = List.of(StatusArquivo.PROCESSANDO, StatusArquivo.COMPLETO);
    private static final List<Integer> NIVEIS_SUPORTADOS = List.of(25, 50, 75);

    private final ArquivoRepository arquivoRepository;
    private final MinioService minioService;
    private final MinioClient minioClient;
    private final SessaoService sessaoService;
    private final WebSocketNotificationService notificationService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ffmpeg.bin-dir:/usr/bin}")
    private String ffmpegBinDir;

    @Transactional
    public void otimizarVideo(String arquivoId, int nivel, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        validarOtimizacao(arquivo, nivel, solicitante);

        VideoOptimizationEvent event = VideoOptimizationEvent.builder()
            .arquivoId(arquivoId)
            .sessaoId(arquivo.getSessaoId())
            .nivel(nivel)
            .solicitanteId(solicitante.getId())
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_VIDEO_OPTIMIZATION,
            event
        );

        log.info("Otimização de vídeo solicitada: arquivo={}, nivel={}", arquivoId, nivel);
    }

    private void validarOtimizacao(Arquivo arquivo, int nivel, User solicitante) {
        if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus())) {
            throw new ConversaoNaoSuportadaException("Arquivo ainda não está completo");
        }

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            throw new ConversaoNaoSuportadaException("Este arquivo não suporta otimização");
        }

        if (arquivo.getTag() != null && "OTIMIZADO".equalsIgnoreCase(arquivo.getTag())) {
            throw new ConversaoNaoSuportadaException("Arquivo já é uma otimização");
        }

        if (!NIVEIS_SUPORTADOS.contains(nivel)) {
            throw new ConversaoNaoSuportadaException("Nível de otimização inválido");
        }

        FormatoVideo.fromMimeType(arquivo.getTipoMime())
            .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de vídeo não suportado"));

        if (isOtimizacaoDuplicada(arquivo.getId(), nivel)) {
            throw new ConversaoNaoSuportadaException("Arquivo já foi otimizado neste nível");
        }

        Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, solicitante.getId());

        if (!sessaoService.podeAdicionarArquivo(arquivo.getSessaoId())) {
            throw new EspacoSessaoInsuficienteException("Sessão atingiu o limite de arquivos");
        }
    }

    @Transactional
    public void processarOtimizacao(VideoOptimizationEvent event) {
        Path tempInputPath = null;
        Path tempOutputPath = null;

        try {
            log.info("Iniciando processamento de otimização de vídeo: {}", event);

            Arquivo arquivoOriginal = arquivoRepository.findById(event.getArquivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo original não encontrado"));

            FormatoVideo formato = FormatoVideo.fromMimeType(arquivoOriginal.getTipoMime())
                .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de vídeo não suportado"));

            MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivoOriginal.getCaminhoMinio());

            tempInputPath = Files.createTempFile("video_opt_input_", "." + detectarExtensaoEntrada(arquivoOriginal));
            try (InputStream inputStream = arquivoData.inputStream()) {
                Files.copy(inputStream, tempInputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            tempOutputPath = Files.createTempFile("video_opt_output_", "." + formato.getExtension());

            executarOtimizacao(tempInputPath, tempOutputPath, formato, event.getNivel());

            long tamanhoBytes = Files.size(tempOutputPath);
            Arquivo arquivoOtimizado = criarArquivoOtimizado(arquivoOriginal, event.getNivel(), tamanhoBytes);

            String caminhoMinio = String.format("%s/%s/%s",
                arquivoOtimizado.getSessaoId(),
                arquivoOtimizado.getId(),
                arquivoOtimizado.getNomeOriginal()
            );

            try (InputStream outputStream = Files.newInputStream(tempOutputPath)) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(properties.getMinioBucket())
                        .object(caminhoMinio)
                        .stream(outputStream, tamanhoBytes, -1)
                        .contentType(formato.getMimeType())
                        .build()
                );
            }

            arquivoOtimizado.setCaminhoMinio(caminhoMinio);
            arquivoOtimizado.setStatus(StatusArquivo.COMPLETO);
            arquivoRepository.save(arquivoOtimizado);

            sessaoService.incrementarArquivosTransferidos(arquivoOtimizado.getSessaoId());

            notificationService.notificarOtimizacaoConcluida(arquivoOtimizado.getSessaoId(), arquivoOtimizado);

            log.info("Otimização de vídeo concluída: arquivoOriginal={}, arquivoNovo={}, nivel={}",
                event.getArquivoId(), arquivoOtimizado.getId(), event.getNivel());

        } catch (Exception e) {
            log.error("Erro ao processar otimização de vídeo: {}", event, e);
            throw new RuntimeException("Falha na otimização de vídeo: " + e.getMessage(), e);
        } finally {
            limparArquivosTemporarios(tempInputPath, tempOutputPath);
        }
    }

    private void executarOtimizacao(Path inputPath, Path outputPath, FormatoVideo formato, int nivel) {
        UrlOutput output = UrlOutput.toPath(outputPath)
            .setFormat(formato.getFfmpegFormat());

        List<String> args = resolveArgs(formato, nivel);
        if (args.size() % 2 != 0) {
            throw new IllegalStateException("Perfil de otimização inválido: argumentos precisam ser pares (opção, valor)");
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

    private List<String> resolveArgs(FormatoVideo formato, int nivel) {
        if (formato == FormatoVideo.GIF) {
            int quality = switch (nivel) {
                case 25 -> 8;
                case 50 -> 15;
                case 75 -> 25;
                default -> 15;
            };
            return List.of("-q:v", String.valueOf(quality));
        }

        int crf = switch (nivel) {
            case 25 -> 26;
            case 50 -> 30;
            case 75 -> 34;
            default -> 30;
        };

        return List.of("-crf", String.valueOf(crf), "-preset", "medium");
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

    private Arquivo criarArquivoOtimizado(Arquivo original, int nivel, long tamanhoBytes) {
        String novoNome = gerarNomeArquivoOtimizado(original.getNomeOriginal(), nivel);

        Arquivo arquivoOtimizado = Arquivo.builder()
            .id(UUID.randomUUID().toString())
            .sessaoId(original.getSessaoId())
            .nomeOriginal(novoNome)
            .tamanhoBytes(tamanhoBytes)
            .tipoMime(original.getTipoMime())
            .status(StatusArquivo.PROCESSANDO)
            .remetenteId(original.getRemetenteId())
            .conversivel(false)
            .arquivoOriginalId(original.getId())
            .tag("OTIMIZADO")
            .otimizacaoNivel(nivel)
            .tamanhoOriginalBytes(original.getTamanhoBytes())
            .totalChunks(1)
            .chunksRecebidos(1)
            .progressoUpload(100.0)
            .build();

        return arquivoRepository.save(arquivoOtimizado);
    }

    private String gerarNomeArquivoOtimizado(String nomeOriginal, int nivel) {
        int lastDot = nomeOriginal != null ? nomeOriginal.lastIndexOf('.') : -1;
        String nomeBase = (nomeOriginal != null && lastDot > 0) ? nomeOriginal.substring(0, lastDot) : (nomeOriginal == null ? "arquivo" : nomeOriginal);
        String novoNome = nomeBase + "_optimized_" + nivel + "." + obterExtensao(nomeOriginal);
        return FileNameSanitizer.sanitize(novoNome);
    }

    private String obterExtensao(String nomeOriginal) {
        int lastDot = nomeOriginal != null ? nomeOriginal.lastIndexOf('.') : -1;
        if (lastDot > 0 && lastDot < nomeOriginal.length() - 1) {
            return nomeOriginal.substring(lastDot + 1);
        }
        return "bin";
    }

    private void limparArquivosTemporarios(Path tempInputPath, Path tempOutputPath) {
        try {
            if (tempInputPath != null) Files.deleteIfExists(tempInputPath);
        } catch (Exception e) {
            log.warn("Erro ao deletar arquivo temporário: {}", e.getMessage());
        }
        try {
            if (tempOutputPath != null) Files.deleteIfExists(tempOutputPath);
        } catch (Exception e) {
            log.warn("Erro ao deletar arquivo temporário: {}", e.getMessage());
        }
    }

    private boolean isOtimizacaoDuplicada(String arquivoOriginalId, int nivel) {
        return arquivoRepository.existsByArquivoOriginalIdAndOtimizacaoNivelAndStatusIn(
            arquivoOriginalId,
            nivel,
            STATUS_OTIMIZACAO_ATIVOS
        );
    }
}
