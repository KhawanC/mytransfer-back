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
public class SairSessaoRequest {

    @NotBlank(message = "ID da sessão é obrigatório")
    private String sessaoId;
}
