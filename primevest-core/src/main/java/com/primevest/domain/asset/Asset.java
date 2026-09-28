package com.primevest.domain.asset;

import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerLinkSource;
import com.primevest.domain.issuer.ShareClass;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Catálogo de ativos: o que o instrumento É, sem nada sobre uma compra.
 *
 * <p>Vencimento e remuneração saíram daqui. Os que são públicos e do papel
 * (Tesouro Direto) foram para {@link AssetTerms}; os que são de cada aplicação
 * (CDB, LCI, debênture) foram para {@code FixedIncomeNote}, que pertence ao
 * usuário. Enquanto moravam nesta tabela, registrar dois CDBs de bancos
 * diferentes exigia dois ativos — num catálogo global, visível na busca de
 * todo mundo.</p>
 */
@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_category_id")
    private AssetCategory assetCategory;

    @Column(name = "current_price", precision = 18, scale = 8)
    private BigDecimal currentPrice;

    @Column(name = "price_updated_at")
    private LocalDateTime priceUpdatedAt;

    /**
     * Parâmetros públicos do papel, quando existem. Nulo para renda variável e
     * para os produtos genéricos de renda fixa (CDB, LCI...), cujo prazo e taxa
     * são de cada aplicação.
     *
     * <p>Lado inverso de um {@code @OneToOne}: o Hibernate precisa saber se é
     * nulo, então carrega junto mesmo marcado LAZY. Por isso as consultas que
     * devolvem listas usam {@code LEFT JOIN FETCH a.terms} — sem isso é uma
     * consulta extra por linha.</p>
     *
     * <p>Sem cascade e sem orphanRemoval de proposito. Com eles, salvar um
     * ativo destacado cujo campo {@code terms} viesse nulo — o scheduler de
     * precos salva ativo o tempo todo — apagaria a linha de asset_terms. Os
     * 130 vencimentos do Tesouro nao dependem de ninguem lembrar de carregar
     * a associacao antes de um save.</p>
     */
    @OneToOne(mappedBy = "asset", fetch = FetchType.LAZY)
    private AssetTerms terms;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Id do ativo no provedor de cotacao, quando difere do ticker. Hoje so
     * cripto: o ticker e BTC, a CoinGecko entende {@code bitcoin} (V19).
     */
    @Column(name = "price_source_ref")
    private String priceSourceRef;

    /** ISIN do papel. A B3 devolve proventos por ISIN; preenchido pela rotina de proventos (V20). */
    private String isin;

    /** CNPJ do fundo (so FII): o FNET busca o historico de proventos por ele (V20). */
    private String cnpj;

    /** Quando o historico longo de proventos foi carregado. Nulo = nunca (V20). */
    @Column(name = "cash_history_loaded_at")
    private LocalDateTime cashHistoryLoadedAt;

    /** Arquivo do logo em data/logos. Nulo = sem logo (V21). */
    @Column(name = "logo_path")
    private String logoPath;

    /** Ultima procura do logo, achando ou nao. Nulo = nunca procurado (V21). */
    @Column(name = "logo_checked_at")
    private LocalDateTime logoCheckedAt;

    /**
     * Emissor do papel: companhia aberta ou FII (V28). Nulo ate a ligacao (F3)
     * e nos tipos sem emissor (BDR, ETF, Tesouro, renda fixa, cripto).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id")
    private Issuer issuer;

    /** Classe da acao (V28). Nula fora de ACAO. */
    @Enumerated(EnumType.STRING)
    @Column(name = "share_class", length = 10)
    private ShareClass shareClass;

    /** Como a ligacao com o emissor foi feita (V28). MANUAL nunca e sobrescrito. */
    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_link_source", length = 10)
    private IssuerLinkSource issuerLinkSource;

}
