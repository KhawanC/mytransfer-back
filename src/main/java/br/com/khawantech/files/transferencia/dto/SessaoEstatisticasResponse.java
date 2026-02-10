package br.com.khawantech.files.transferencia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessaoEstatisticasResponse {

    private Integer quantidadeArquivos;
    private Integer limiteArquivos;
    private Long tamanhoTotalBytes;
    private Long limiteTamanhoBytes;
    private Integer espacoDisponivel;
}
