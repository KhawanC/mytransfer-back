package br.com.khawantech.files.transferencia.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sessoes")
public class Sessao implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String hashConexao;

    @Indexed
    private String usuarioCriadorId;

    @Indexed
    private String usuarioConvidadoId;

    // Campos para sistema de aprovação
    private String usuarioConvidadoPendenteId;
    
    private String nomeUsuarioConvidadoPendente;

    @Indexed
    @Builder.Default
    private StatusSessao status = StatusSessao.AGUARDANDO;

    @Builder.Default
    private int totalArquivosTransferidos = 0;

    @CreatedDate
    private Instant criadaEm;

    private Instant expiraEm;

    private Instant encerradaEm;

    @LastModifiedDate
    private Instant atualizadaEm;

    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public void generateHashConexao() {
        if (this.hashConexao == null) {
            // Gera um código numérico de 8 dígitos (10000000 a 99999999)
            int codigo = 10000000 + new java.util.Random().nextInt(90000000);
            this.hashConexao = String.valueOf(codigo);
        }
    }

    public boolean podeReceberArquivos(int limiteArquivos) {
        return this.totalArquivosTransferidos < limiteArquivos;
    }

    public boolean estaAtiva() {
        return this.status == StatusSessao.ATIVA && 
               this.expiraEm != null && 
               Instant.now().isBefore(this.expiraEm);
    }

    public boolean estaAguardando() {
        return this.status == StatusSessao.AGUARDANDO;
    }
}
