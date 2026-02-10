package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.FormatoImagem;
import br.com.khawantech.files.transferencia.dto.ImageConversionEvent;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageConversionService {

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

    public boolean isImagemConversivel(String mimeType) {
        return FormatoImagem.fromMimeType(mimeType)
            .map(this::toMagickFormat)
            .map(imageMagickSupportService::supportsRead)
            .orElse(false);
    }

    public List<FormatoImagem> getFormatosDisponiveis(String mimeType) {
        return FormatoImagem.getFormatosDisponiveis(mimeType).stream()
            .filter(formato -> imageMagickSupportService.supportsWrite(toMagickFormat(formato)))
            .toList();
    }

    @Transactional
    public void converterImagem(String arquivoId, String formatoDestino, User solicitante) {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));

        validarConversao(arquivo, formatoDestino, solicitante);

        ImageConversionEvent event = ImageConversionEvent.builder()
            .arquivoId(arquivoId)
            .sessaoId(arquivo.getSessaoId())
            .formatoDestino(formatoDestino)
            .solicitanteId(solicitante.getId())
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_IMAGE_CONVERSION,
            event
        );

        log.info("Conversão de imagem solicitada: arquivo={}, formato={}", arquivoId, formatoDestino);
    }

    private void validarConversao(Arquivo arquivo, String formatoDestino, User solicitante) {
        if (!StatusArquivo.COMPLETO.equals(arquivo.getStatus())) {
            throw new ConversaoNaoSuportadaException("Arquivo ainda não está completo");
        }

        if (!Boolean.TRUE.equals(arquivo.getConversivel())) {
            throw new ConversaoNaoSuportadaException("Este arquivo não suporta conversão");
        }

        FormatoImagem formato = FormatoImagem.valueOf(formatoDestino);
        List<FormatoImagem> formatosDisponiveis = arquivo.getFormatosDisponiveis();
        
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
    public void processarConversao(ImageConversionEvent event) {
        try {
            log.info("Iniciando processamento de conversão: {}", event);

            Arquivo arquivoOriginal = arquivoRepository.findById(event.getArquivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo original não encontrado"));

            FormatoImagem formatoDestino = FormatoImagem.valueOf(event.getFormatoDestino());

            byte[] imagemOriginal = obterImagemOriginal(arquivoOriginal);

            byte[] imagemConvertida = executarConversao(imagemOriginal, arquivoOriginal.getTipoMime(), formatoDestino);

            Arquivo arquivoConvertido = criarArquivoConvertido(arquivoOriginal, formatoDestino, imagemConvertida.length);

            String caminhoMinio = String.format("%s/%s/%s",
                arquivoConvertido.getSessaoId(),
                arquivoConvertido.getId(),
                arquivoConvertido.getNomeOriginal()
            );

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .stream(new ByteArrayInputStream(imagemConvertida), imagemConvertida.length, -1)
                    .contentType(formatoDestino.getMimeType())
                    .build()
            );

            arquivoConvertido.setCaminhoMinio(caminhoMinio);
            arquivoConvertido.setStatus(StatusArquivo.COMPLETO);
            arquivoRepository.save(arquivoConvertido);

            notificationService.notificarConversaoConcluida(arquivoConvertido.getSessaoId(), arquivoConvertido);

            log.info("Conversão concluída com sucesso: arquivoOriginal={}, arquivoNovo={}, formato={}",
                event.getArquivoId(), arquivoConvertido.getId(), formatoDestino);

        } catch (Exception e) {
            log.error("Erro ao processar conversão de imagem: {}", event, e);
            throw new RuntimeException("Falha na conversão de imagem: " + e.getMessage(), e);
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

    private byte[] executarConversao(byte[] imagemOriginal, String mimeTypeOriginal, FormatoImagem formatoDestino) {
        Path tempInputPath = null;
        Path tempOutputPath = null;

        try {
            FormatoImagem formatoOriginal = FormatoImagem.fromMimeType(mimeTypeOriginal)
                .orElseThrow(() -> new ConversaoNaoSuportadaException("Formato original não suportado"));

            String magickFormatoOriginal = toMagickFormat(formatoOriginal);
            String magickFormatoDestino = toMagickFormat(formatoDestino);

            if (!imageMagickSupportService.supportsRead(magickFormatoOriginal)) {
                throw new ConversaoNaoSuportadaException("ImageMagick não suporta leitura do formato " + magickFormatoOriginal);
            }
            if (!imageMagickSupportService.supportsWrite(magickFormatoDestino)) {
                throw new ConversaoNaoSuportadaException("ImageMagick não suporta escrita do formato " + magickFormatoDestino);
            }

            tempInputPath = Files.createTempFile("input_", "." + formatoOriginal.getExtension());
            tempOutputPath = Files.createTempFile("output_", "." + formatoDestino.getExtension());

            Files.write(tempInputPath, imagemOriginal);

            ConvertCmd cmd = new ConvertCmd();
            cmd.setSearchPath("/usr/bin");
            
            IMOperation op = new IMOperation();
            op.addImage(tempInputPath.toString());
            
            op.define("limit:time=" + timeoutSeconds);
            op.define("limit:pixels=" + maxResolution);
            
            op.addImage(magickFormatoDestino.toLowerCase() + ":" + tempOutputPath.toString());

            cmd.run(op);

            return Files.readAllBytes(tempOutputPath);

        } catch (Exception e) {
            log.error("Erro ao executar conversão ImageMagick", e);
            throw new RuntimeException("Falha na conversão ImageMagick: " + e.getMessage(), e);
        } finally {
            limparArquivosTemporarios(tempInputPath, tempOutputPath);
        }
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

    private Arquivo criarArquivoConvertido(Arquivo original, FormatoImagem formatoDestino, long tamanhoBytes) {
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

    private String gerarNomeArquivoConvertido(String nomeOriginal, FormatoImagem formatoDestino) {
        int lastDot = nomeOriginal.lastIndexOf('.');
        String nomeBase = lastDot > 0 ? nomeOriginal.substring(0, lastDot) : nomeOriginal;
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
