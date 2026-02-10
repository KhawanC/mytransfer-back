package br.com.khawantech.files.assinatura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanoAssinaturaResponse {
    private String id;
    private String nome;
    private int precoCentavos;
    private int duracaoDias;
}
