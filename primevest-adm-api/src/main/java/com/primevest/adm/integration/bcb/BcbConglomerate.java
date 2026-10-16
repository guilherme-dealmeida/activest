package com.primevest.adm.integration.bcb;

/**
 * Um conglomerado prudencial do IF.data (linha {@code C0...} do cadastro): codigo, nome ("NU PAGAMENTOS -
 * PRUDENCIAL") e a raiz do CNPJ da lider.
 */
public record BcbConglomerate(String code, String name, String leaderRoot) {
}
