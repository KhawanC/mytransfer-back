package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final TransferenciaProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void inicializarBucket() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getMinioBucket()).build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.getMinioBucket()).build()
                );
                log.info("Bucket criado: {}", properties.getMinioBucket());
            }
        } catch (Exception e) {
            log.error("Erro ao inicializar bucket: {}", e.getMessage());
            throw new RuntimeException("Erro ao inicializar bucket MinIO", e);
        }
    }

    public String uploadChunk(String sessaoId, String arquivoId, int numeroChunk, String dadosBase64) {
        try {
            byte[] dados = Base64.getDecoder().decode(dadosBase64);
            String caminho = gerarCaminhoChunk(sessaoId, arquivoId, numeroChunk);

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminho)
                    .stream(new ByteArrayInputStream(dados), dados.length, -1)
                    .contentType("application/octet-stream")
                    .build()
            );

            log.debug("Chunk {} uploaded para arquivo {}", numeroChunk, arquivoId);
            return caminho;

        } catch (Exception e) {
            log.error("Erro ao fazer upload do chunk: {}", e.getMessage());
            throw new RuntimeException("Erro ao fazer upload do chunk", e);
        }
    }

    public String mergeChunks(String sessaoId, String arquivoId, String nomeArquivo, int totalChunks, String tipoMime) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            for (int i = 0; i < totalChunks; i++) {
                String caminhoCunk = gerarCaminhoChunk(sessaoId, arquivoId, i);
                
                try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(properties.getMinioBucket())
                        .object(caminhoCunk)
                        .build()
                )) {
                    inputStream.transferTo(outputStream);
                }
            }

            String caminhoFinal = gerarCaminhoArquivo(sessaoId, arquivoId, nomeArquivo);
            byte[] arquivoCompleto = outputStream.toByteArray();

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoFinal)
                    .stream(new ByteArrayInputStream(arquivoCompleto), arquivoCompleto.length, -1)
                    .contentType(tipoMime)
                    .build()
            );

            for (int i = 0; i < totalChunks; i++) {
                deleteChunk(sessaoId, arquivoId, i);
            }

            log.info("Arquivo completo criado: {}", caminhoFinal);
            return caminhoFinal;

        } catch (Exception e) {
            log.error("Erro ao fazer merge dos chunks: {}", e.getMessage());
            throw new RuntimeException("Erro ao fazer merge dos chunks", e);
        }
    }

    public String gerarUrlDownload(String caminhoMinio, int expiracaoMinutos) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .expiry(expiracaoMinutos, TimeUnit.MINUTES)
                    .build()
            );
        } catch (Exception e) {
            log.error("Erro ao gerar URL de download: {}", e.getMessage());
            throw new RuntimeException("Erro ao gerar URL de download", e);
        }
    }

    public ArquivoData obterArquivo(String caminhoMinio) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .build()
            );

            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .build()
            );

            return new ArquivoData(stream, stat.contentType(), stat.size());

        } catch (Exception e) {
            log.error("Erro ao obter arquivo do MinIO: {}", caminhoMinio, e);
            throw new RuntimeException("Erro ao obter arquivo do MinIO", e);
        }
    }

    public byte[] lerPrefixoDeChunks(String sessaoId, String arquivoId, int totalChunks, int maxBytes) {
        if (maxBytes <= 0) {
            return new byte[0];
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
            int remaining = maxBytes;

            for (int i = 0; i < totalChunks && remaining > 0; i++) {
                String caminhoChunk = gerarCaminhoChunk(sessaoId, arquivoId, i);

                try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(properties.getMinioBucket())
                        .object(caminhoChunk)
                        .build()
                )) {
                    byte[] buffer = new byte[Math.min(8192, remaining)];
                    int read;
                    while (remaining > 0 && (read = inputStream.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                        outputStream.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
            }

            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > maxBytes) {
                return Arrays.copyOf(bytes, maxBytes);
            }
            return bytes;
        } catch (Exception e) {
            log.error("Erro ao ler prefixo de chunks: {}", e.getMessage());
            throw new RuntimeException("Erro ao ler prefixo de chunks", e);
        }
    }

    public record ArquivoData(InputStream inputStream, String contentType, long size) {}

    public void deleteChunk(String sessaoId, String arquivoId, int numeroChunk) {
        try {
            String caminho = gerarCaminhoChunk(sessaoId, arquivoId, numeroChunk);
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminho)
                    .build()
            );
        } catch (Exception e) {
            log.warn("Erro ao deletar chunk: {}", e.getMessage());
        }
    }

    public void deleteArquivo(String caminhoMinio) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .object(caminhoMinio)
                    .build()
            );
            log.debug("Arquivo deletado: {}", caminhoMinio);
        } catch (Exception e) {
            log.warn("Erro ao deletar arquivo: {}", e.getMessage());
        }
    }

    public void deletarArquivosSessao(String sessaoId) {
        try {
            String prefixo = sessaoId + "/";
            Iterable<Result<io.minio.messages.Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(properties.getMinioBucket())
                    .prefix(prefixo)
                    .recursive(true)
                    .build()
            );

            for (Result<io.minio.messages.Item> result : results) {
                String objectName = result.get().objectName();
                deleteArquivo(objectName);
            }

            log.info("Arquivos da sessão {} deletados", sessaoId);
        } catch (Exception e) {
            log.error("Erro ao deletar arquivos da sessão: {}", e.getMessage());
        }
    }

    private String gerarCaminhoChunk(String sessaoId, String arquivoId, int numeroChunk) {
        return String.format("%s/%s/chunks/%d", sessaoId, arquivoId, numeroChunk);
    }

    private String gerarCaminhoArquivo(String sessaoId, String arquivoId, String nomeArquivo) {
        return String.format("%s/%s/%s", sessaoId, arquivoId, nomeArquivo);
    }
}
