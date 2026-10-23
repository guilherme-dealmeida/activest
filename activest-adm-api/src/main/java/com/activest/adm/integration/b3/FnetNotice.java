package com.activest.adm.integration.b3;

import java.util.List;

/**
 * Um "Aviso aos Cotistas - Estruturado" do FNET: o CNPJ do fundo que publicou (so digitos,
 * 14 posicoes; nulo se o XML nao trouxer) e os proventos do aviso.
 */
public record FnetNotice(String fundCnpj, List<FnetDividend> dividends) {
}
