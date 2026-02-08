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
public class EnviarChunkRequest {

    @NotBlank(message = "ID do arquivo é obrigatório")
    private String arquivoId;

    @NotNull(message = "Número do chunk é obrigatório")
    @Min(value = 0, message = "Número do chunk deve ser maior ou igual a zero")
    private Integer numeroChunk;

    @NotBlank(message = "Hash do chunk é obrigatório")
    private String hashChunk;

    @NotBlank(message = "Dados do chunk são obrigatórios")
    private String dadosBase64;

    @NotBlank(message = "ID da sessão é obrigatório")
    private String sessaoId;
}
