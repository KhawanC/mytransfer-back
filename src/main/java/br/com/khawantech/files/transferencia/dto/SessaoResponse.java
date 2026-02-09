package br.com.khawantech.files.transferencia.dto;

import java.time.Instant;

import br.com.khawantech.files.transferencia.entity.StatusSessao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessaoResponse {

    private String id;
    private String hashConexao;
    private String qrCodeBase64;
    private StatusSessao status;
    private String usuarioCriadorId;
    private String usuarioConvidadoId;
    private String usuarioConvidadoPendenteId;
    private String nomeUsuarioConvidadoPendente;
    private int totalArquivosTransferidos;
    private Instant criadaEm;
    private Instant expiraEm;
    private Instant hashExpiraEm;
    
    // Flags de permissão calculadas pelo backend
    private boolean podeUpload;
    private boolean podeEncerrar;
    private boolean estaAtiva;
}
