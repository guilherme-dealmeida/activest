package com.primevest.domain.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Troca de distribuidora de uma compra de Tesouro com venda ligada (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 5.1):
 * a cascata (vendas que consumiram a compra, compras que essas vendas consumiram...) para o modal "quais transacoes
 * quer mover de [antiga] para [nova]".
 */
public final class DistributorChangeDTOs {

    private DistributorChangeDTOs() {
    }

    /**
     * Um item da cascata.
     *
     * @param type    BUY (compra = titulo) ou SELL (a venda inteira, mesmo repartida entre titulos)
     * @param id      compra: o id da transacao; venda: o id do grupo da venda
     * @param locked  a compra que esta sendo editada (vai sempre)
     */
    public record Item(String type, UUID id, LocalDate date, BigDecimal shares, BigDecimal pricePerShare,
                       BigDecimal totalAmount, boolean locked) {
    }

    /** O que o modal mostra: de onde, para onde e os itens (todos marcados por padrao). */
    public record Preview(Long fromId, String fromName, Long toId, String toName, String ticker, List<Item> items) {
    }

    /**
     * Confirmar a troca: a compra editada (com os demais campos do formulario) e os itens marcados.
     *
     * @param purchase      o formulario da compra, com a distribuidora nova
     * @param purchaseIds   compras (transacoes) que mudam junto; a editada entra mesmo que nao venha
     * @param saleGroupIds  vendas (grupos) que mudam junto
     */
    public record Request(TransactionCreateDTO purchase, List<UUID> purchaseIds, List<UUID> saleGroupIds) {
    }
}
