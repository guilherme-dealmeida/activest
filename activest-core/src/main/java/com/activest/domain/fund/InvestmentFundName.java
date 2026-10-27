package com.activest.domain.fund;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Denominação social ou comercial de um fundo, atual ou anterior (cad_fi_hist) — só para a busca (V12). */
@Entity
@Table(name = "investment_fund_names")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentFundName {

    public enum Kind { LEGAL, TRADE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false, length = 250)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
