package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.FormatoImagem;
import br.com.khawantech.files.transferencia.dto.ImageOptimizationEvent;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import br.com.khawantech.files.transferencia.exception.EspacoSessaoInsuficienteException;
import br.com.khawantech.files.transferencia.exception.RecursoNaoEncontradoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.util.FileNameSanitizer;
import br.com.khawantech.files.user.entity.User;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOptimizationService {

    private static final Duration IDENTIFY_TIMEOUT = Duration.ofSeconds(10);
    private static final List<StatusArquivo> STATUS_OTIMIZACAO_ATIVOS = List.of(StatusArquivo.PROCESSANDO, StatusArquivo.COMPLETO);
    private static final List<Integer> NIVEIS_SUPORTADOS = List.of(25, 50, 75);

    private final ArquivoRepository arquivoRepository;
    private final MinioService minioService;
    private final MinioClient minioClient;
    private final SessaoService sessaoService;
    private final WebSocketNotificationService notificationService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ImageMagickSupportService imageMagickSupportService;

    @Value("${imagemagick.timeout-seconds:300}")
    private Integer timeoutSeconds;

    @Value("${imagemagick.max-resolution:50000000}")
    private Long maxResolution;

    @Transactional
    public void otimizarImagem(String arquivoId, int nivel, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        validarOtimizacao(arquivo, nivel, solicitante);

        ImageOptimizationEvent event = ImageOptimizationEvent.builder()
            .arquivoId(arquivoId)
            .sessaoId(arquivo.getSessaoId())
            .nivel(nivel)
            .solicitanteId(solicitante.getId())
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_IMAGE_OPTIMIZATION,
            event
        );

        log.info("Otimização de imagem solicitada: arquivo={}, nivel={}", arquivoId, nivel);
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

        FormatoImagem.fromMimeType(arquivo.getTipoMime())
            .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato de imagem não suportado"));

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
    public void processarOtimizacao(ImageOptimizationEvent event) {
        try {
            log.info("Iniciando processamento de otimização de imagem: {}", event);

            Arquivo arquivoOriginal = arquivoRepository.findById(event.getArquivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo original não encontrado"));

            FormatoImagem formato = FormatoImagem.fromMimeType(arquivoOriginal.getTipoMime())
                .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato original não suportado"));

            byte[] imagemOriginal = obterImagemOriginal(arquivoOriginal);
            byte[] imagemOtimizada = executarOtimizacao(imagemOriginal, formato, event.getNivel());

            Arquivo arquivoOtimizado = criarArquivoOtimizado(arquivoOriginal, event.getNivel(), imagemOtimizada.length);

            String caminhoMinio = String.format("%s/%s/%s",
                arquivoOtimizado.getSessaoId(),
                arquivoOtimizado.getId(),
                arquivoOtimizado.getNomeOriginal()
            );

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .stream(new ByteArrayInputStream(imagemOtimizada), imagemOtimizada.length, -1)
                    .contentType(formato.getMimeType())
                    .build()
            );

            arquivoOtimizado.setCaminhoMinio(caminhoMinio);
            arquivoOtimizado.setStatus(StatusArquivo.COMPLETO);
            arquivoRepository.save(arquivoOtimizado);

            sessaoService.incrementarArquivosTransferidos(arquivoOtimizado.getSessaoId());

            notificationService.notificarOtimizacaoConcluida(arquivoOtimizado.getSessaoId(), arquivoOtimizado);

            log.info("Otimização de imagem concluída: arquivoOriginal={}, arquivoNovo={}, nivel={}",
                event.getArquivoId(), arquivoOtimizado.getId(), event.getNivel());

        } catch (Exception e) {
            log.error("Erro ao processar otimização de imagem: {}", event, e);
            throw new RuntimeException("Falha na otimização de imagem: " + e.getMessage(), e);
        }
    }

    private byte[] obterImagemOriginal(Arquivo arquivo) {
        MinioService.ArquivoData arquivoData = minioService.obterArquivo(arquivo.getCaminhoMinio());

        try (InputStream inputStream = arquivoData.inputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao obter imagem original do MinIO", e);
        }
    }

    private byte[] executarOtimizacao(byte[] imagemOriginal, FormatoImagem formato, int nivel) {
        Path tempInputPath = null;
        Path tempOutputPath = null;

        try {
            String magickFormato = toMagickFormat(formato);

            if (!imageMagickSupportService.supportsRead(magickFormato)) {
                throw new ConversaoNaoSuportadaException("ImageMagick não suporta leitura do formato " + magickFormato);
            }
            if (!imageMagickSupportService.supportsWrite(magickFormato)) {
                throw new ConversaoNaoSuportadaException("ImageMagick não suporta escrita do formato " + magickFormato);
            }

            tempInputPath = Files.createTempFile("input_opt_", "." + formato.getExtension());
            tempOutputPath = Files.createTempFile("output_opt_", "." + formato.getExtension());

            Files.write(tempInputPath, imagemOriginal);

            String formatoDetectado = detectarFormatoMagick(tempInputPath);
            if (formatoDetectado == null || formatoDetectado.isBlank()) {
                throw new ConversaoNaoSuportadaException("Arquivo de imagem inválido ou não decodificável pelo ImageMagick");
            }

            String detectadoUpper = formatoDetectado.trim().toUpperCase(Locale.ROOT);
            if (!detectadoUpper.equals(magickFormato.toUpperCase(Locale.ROOT))) {
                throw new ConversaoNaoSuportadaException(
                    "Conteúdo do arquivo não corresponde ao tipo informado (esperado " + magickFormato + ", detectado " + detectadoUpper + ")"
                );
            }

            ConvertCmd cmd = new ConvertCmd();
            cmd.setSearchPath("/usr/bin");

            IMOperation op = new IMOperation();
            op.addImage(tempInputPath.toString());
            op.define("limit:time=" + timeoutSeconds);
            op.define("limit:pixels=" + maxResolution);
            op.quality((double) resolveQuality(nivel));
            op.addImage(magickFormato.toLowerCase() + ":" + tempOutputPath.toString());

            cmd.run(op);

            return Files.readAllBytes(tempOutputPath);

        } catch (Exception e) {
            log.error("Erro ao executar otimização ImageMagick", e);
            throw new RuntimeException("Falha na otimização ImageMagick: " + e.getMessage(), e);
        } finally {
            limparArquivosTemporarios(tempInputPath, tempOutputPath);
        }
    }

    private String detectarFormatoMagick(Path inputPath) {
        try {
            String magick = Files.isExecutable(Path.of("/usr/bin/magick")) ? "/usr/bin/magick" : "magick";
            ProcessBuilder processBuilder = new ProcessBuilder(
                magick,
                "identify",
                "-format",
                "%m",
                inputPath.toString()
            );
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(IDENTIFY_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private int resolveQuality(int nivel) {
        return switch (nivel) {
            case 25 -> 85;
            case 50 -> 70;
            case 75 -> 55;
            default -> 75;
        };
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
        if (nomeOriginal == null) {
            return "bin";
        }
        int lastDot = nomeOriginal.lastIndexOf('.');
        if (lastDot > 0 && lastDot < nomeOriginal.length() - 1) {
            return nomeOriginal.substring(lastDot + 1);
        }
        return "bin";
    }

    private String toMagickFormat(FormatoImagem formatoImagem) {
        if (formatoImagem == null) {
            return "";
        }
        if (FormatoImagem.JPG.equals(formatoImagem) || FormatoImagem.JPEG.equals(formatoImagem)) {
            return "JPEG";
        }
        if (FormatoImagem.TIFF.equals(formatoImagem)) {
            return "TIFF";
        }
        return formatoImagem.name();
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
