package br.com.khawantech.files.transferencia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessaoLimitesResponse {
    private Integer maxArquivos;
    private long maxTamanhoMb;
    private int duracaoMinutos;
    private String userType;
    private boolean arquivosIlimitados;
}
