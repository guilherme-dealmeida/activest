package com.primevest.domain.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.primevest.domain.asset.Asset;
import com.primevest.domain.dividend.Dividend;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolio_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /**
     * A posicao deixou de ser por ativo e passou a ser por ativo E papel. Dois
     * CDBs de bancos diferentes sao duas linhas, cada uma com sua taxa e seu
     * vencimento — que e o ponto todo da separacao. A carteira passa a repetir
     * o mesmo ticker, e a tela precisa mostrar emissor e vencimento para que as
     * linhas se distingam.
     *
     * <p>Nulo para renda variavel. A unicidade em banco usa NULLS NOT DISTINCT
     * justamente para isso: sem ela, duas posicoes de PETR4 com note_id nulo
     * nao conflitariam e a unicidade de sempre deixaria de existir.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private FixedIncomeNote note;

    // Cotas, preco medio e custo nao sao gravados: saem das transacoes, na
    // leitura, por PositionCalculator (V28). Gravados, so ficavam certos
    // enquanto todo caminho que mexe em transacao lembrasse de recalcula-los.

    /**
     * Valorizacao da posicao. O valor atual nao e gravado: ele e sempre
     * custo + appreciation, entao guardar os dois seria ter duas fontes para o
     * mesmo numero.
     *
     * <p>Nasce em zero e nunca e nula. A tela sempre le este valor; nao o
     * calcula.</p>
     *
     * <p>Com cotacao (Acoes, FIIs, ETFs, BDRs, Cripto...): gravada por
     * {@link AppreciationUpdater} como cotas x cotacao - custo, sempre que a
     * cotacao ou as transacoes da posicao mudam. Sem cotacao (Renda Fixa,
     * Tesouro, tipos MANUAL): apurada por fora (a curva da renda fixa, quando
     * existir), e nada derivado de transacao a sobrescreve.</p>
     */
    @Column(name = "appreciation", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal appreciation = BigDecimal.ZERO;

    @Column(name = "is_reservation", nullable = false)
    private boolean isReservation;

    /**
     * Posicao liquidada: titulo que venceu ou renda fixa resgatada. Ainda sem
     * regra que a ligue; existe para as funcionalidades de vencimento e resgate.
     */
    @Column(name = "is_settled", nullable = false)
    private boolean isSettled;

    @JsonIgnore
    @OneToMany(mappedBy = "portfolioAsset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "portfolioAsset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Dividend> dividends = new ArrayList<>();
}
