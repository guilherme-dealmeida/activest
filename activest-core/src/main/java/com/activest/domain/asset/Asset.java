package com.activest.domain.asset;

import com.activest.domain.issuer.Issuer;
import com.activest.domain.issuer.IssuerLinkSource;
import com.activest.domain.issuer.ShareClass;
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

    /**
     * Único por tipo entre os papéis listados (V13, {@code uk_assets_ticker_type}): o mesmo ticker pode existir em
     * tipos diferentes (META cripto e META ação) e, no mesmo tipo, num papel deslistado e num vivo (P2). Busca por
     * ticker sem o tipo não é segura — use {@link AssetResolver}.
     */
    @Column(nullable = false)
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
     * Moeda em que o ativo é cotado — a primária (V9, D2). {@link #currentPrice} e o histórico de preço estão nela
     * (D11); a conversão para a moeda base é do {@code ExchangeRateService}. Vem do tipo no cadastro do catálogo e não
     * é escolhida pelo usuário (D25).
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = com.activest.domain.currency.Currency.BRL;

    /** Moeda que o ativo acompanha sem ser cotado nela: stablecoins (USDT, USDC → USD), D17. */
    @Column(name = "reference_currency_code", length = 3)
    private String referenceCurrencyCode;

    /** País de origem (ISO 3166) para a retenção do exterior; nulo = o país da moeda. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

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

    /** Data de saída da bolsa (V13, D11): só no papel inativo; parte da unicidade do ticker (P2). */
    @Column(name = "delisted_on")
    private java.time.LocalDate delistedOn;

    /**
     * Prova da inativação (V14, I5): {@link DelistingSource}. MANUAL é a decisão do administrador — a conferência nunca a
     * refaz. Nula no papel inativado antes da F2.
     */
    @Column(name = "delisting_source", length = 12)
    @Enumerated(EnumType.STRING)
    private DelistingSource delistingSource;

    /**
     * Id do ativo no provedor de cotacao, quando difere do ticker. Hoje so
     * cripto: o ticker e BTC, a CoinGecko entende {@code bitcoin} (V19).
     */
    @Column(name = "price_source_ref")
    private String priceSourceRef;

    /** ISIN do papel. A B3 devolve proventos por ISIN; preenchido pela rotina de proventos (V20). */
    private String isin;

    // CNPJ, estado do historico de proventos e do logo sairam na F10 (V37): o CNPJ e do
    // emissor (issuers.cnpj), o historico se reconhece pelo que esta gravado e em
    // carga/data/b3/proventos, o logo pelo arquivo em carga/data/logos (AssetLogos).

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
