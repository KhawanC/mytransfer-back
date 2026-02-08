package br.com.khawantech.files.transferencia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IniciarUploadRequest {

    @NotBlank(message = "Nome do arquivo é obrigatório")
    private String nomeArquivo;

    @NotNull(message = "Tamanho do arquivo é obrigatório")
    @Min(value = 1, message = "Tamanho do arquivo deve ser maior que zero")
    private Long tamanhoBytes;

    @NotBlank(message = "Tipo MIME é obrigatório")
    private String tipoMime;

    @NotBlank(message = "Hash do conteúdo é obrigatório")
    private String hashConteudo;

    @NotBlank(message = "ID da sessão é obrigatório")
    private String sessaoId;
}
