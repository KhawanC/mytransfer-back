package br.com.khawantech.files.transferencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversaoRequest {

    @NotBlank(message = "Formato de destino é obrigatório")
    @Pattern(regexp = "JPEG|JPG|PNG|BMP|WEBP|SVG|TIFF|ICO", message = "Formato não suportado")
    private String formato;
}
