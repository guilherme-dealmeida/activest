package com.primevest.domain.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Previa de uma venda de renda fixa ou de Tesouro, sem gravar (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 5): o que
 * o formulario mostra enquanto o usuario digita, e o que dispara o alerta da venda acima do disponivel (R9).
 *
 * @param gross          bruto que sai do titulo (renda fixa: calculado do liquido; Tesouro: cotas x PU)
 * @param net            liquido (renda fixa: o digitado; Tesouro: bruto - IR - IOF - taxa)
 * @param adjustment     so na liquidacao total da renda fixa: bruto menos o saldo da curva
 * @param availableNet   liquido disponivel estimado na data, antes da venda
 * @param grossBefore    saldo bruto na data, antes da venda
 * @param aboveAvailable venda parcial acima do disponivel: a tela pergunta se e o resgate total
 * @param saleRule       a regra de saida usada (PROPORCIONAL, UEPS, PEPS)
 * @param status         OK, MATURED ou UNSUPPORTED (sem curva: bruto = liquido, sem imposto)
 * @param reason         motivo, quando UNSUPPORTED
 * @param lots           os lotes na data, antes da venda
 */
public record SalePreviewDTO(
    BigDecimal gross,
    BigDecimal iof,
    BigDecimal ir,
    BigDecimal net,
    BigDecimal adjustment,
    BigDecimal availableNet,
    BigDecimal grossBefore,
    boolean aboveAvailable,
    String saleRule,
    String status,
    String reason,
    List<LotDTO> lots
) {

    /** Um lote (aporte) com o imposto que pagaria se resgatado na data. */
    public record LotDTO(LocalDate date, long days, BigDecimal applied, BigDecimal balance, BigDecimal income,
                         BigDecimal iofRate, BigDecimal irRate, BigDecimal iof, BigDecimal ir, BigDecimal net) {
    }
}
