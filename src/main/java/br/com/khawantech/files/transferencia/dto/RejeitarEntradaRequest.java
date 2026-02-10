package br.com.khawantech.files.transferencia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejeitarEntradaRequest {

    @NotBlank(message = "ID da sessão é obrigatório")
    private String sessaoId;

    @NotBlank(message = "ID do usuário é obrigatório")
    private String usuarioId;
}
