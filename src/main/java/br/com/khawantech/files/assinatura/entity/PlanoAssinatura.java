package br.com.khawantech.files.assinatura.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "planos_assinatura")
public class PlanoAssinatura {

    @Id
    private String id;

    private String nome;

    private int precoCentavos;

    private int duracaoDias;
}
