package br.com.khawantech.files.transferencia.dto;

import java.io.Serializable;

import br.com.khawantech.files.transferencia.entity.StatusSessao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessaoAtualizadaEvent implements Serializable {

    private String sessaoId;
    private StatusSessao statusAnterior;
    private StatusSessao statusNovo;
    private String usuarioConvidadoId;
    private String usuarioConvidadoPendenteId;
    private String nomeUsuarioConvidadoPendente;
    private int totalArquivos;
    private String motivo;
}
