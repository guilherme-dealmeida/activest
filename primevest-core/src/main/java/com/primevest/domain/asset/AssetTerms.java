package com.primevest.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Parâmetros públicos de um título, 1:1 com {@link Asset}.
 *
 * <p>Hoje só o Tesouro Direto preenche isto. "Tesouro IPCA+ 2029" vence em
 * 15/05/2029 e remunera IPCA mais spread para qualquer investidor: é
 * característica do papel, e por isso fica no catálogo. Um CDB não tem nada
 * disso — cada aplicação negocia o seu, e essa informação mora em
 * {@code FixedIncomeNote}.</p>
 *
 * <p>Tabela separada, e não colunas em {@code assets}, porque 130 dos 1249
 * ativos têm parâmetros e os outros 1119 carregavam quatro colunas nulas.</p>
 *
 * <p>A taxa NÃO está aqui de propósito. No Tesouro ela é do dia da compra, não
 * do título: dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
 * conforme quando compraram. Taxa é da nota.</p>
 */
@Entity
@Table(name = "asset_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTerms {

    /**
     * Sem id próprio: a chave é o ativo. Um BIGSERIAL aqui só abriria espaço
     * para duas linhas de parâmetros para o mesmo papel.
     */
    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /** Como remunera. Ver {@link RateType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", length = 20)
    private RateType rateType;

    /** Índice de remuneração. Nulo quando prefixado. Ver {@link Indexer}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Indexer indexer;
}
