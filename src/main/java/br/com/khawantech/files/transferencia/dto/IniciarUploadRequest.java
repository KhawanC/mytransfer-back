package br.com.khawantech.files.transferencia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "Nome do arquivo muito longo (máximo 255 caracteres)")
    @Pattern(
        regexp = "^[^<>:\"/\\\\|?*\\x00-\\x1F]+$",
        message = "Nome do arquivo contém caracteres inválidos"
    )
    private String nomeArquivo;

    @NotNull(message = "Tamanho do arquivo é obrigatório")
    @Min(value = 1, message = "Tamanho do arquivo deve ser maior que zero")
    private Long tamanhoBytes;

    @NotBlank(message = "Tipo MIME é obrigatório")
    @Pattern(
        regexp = "^[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]{0,126}/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]{0,126}$",
        message = "Tipo MIME inválido"
    )
    private String tipoMime;

    @NotBlank(message = "Hash do conteúdo é obrigatório")
    private String hashConteudo;

    @NotBlank(message = "ID da sessão é obrigatório")
    private String sessaoId;
}
