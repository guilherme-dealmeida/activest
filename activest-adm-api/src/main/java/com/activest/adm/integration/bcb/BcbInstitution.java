package com.activest.adm.integration.bcb;

/**
 * Uma instituicao do cadastro do IF.data do BCB ({@code IfDataCadastro}), viva ou morta
 * (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4.1).
 *
 * @param cnpjRoot     raiz do CNPJ (8 digitos) — o {@code CodInst} das instituicoes individuais
 * @param name         razao social como o BCB escreve
 * @param segment      segmento (Banco Multiplo, Cooperativa de Credito, Sociedade Corretora de TVM...)
 * @param situation    A (ativa), I (inativa), LE (liquidacao extrajudicial), LO (liquidacao ordinaria), F (falencia)
 * @param groupCode    conglomerado prudencial (C0084693), nulo quando nao ha
 * @param leaderRoot   raiz do CNPJ da lider do conglomerado
 */
public record BcbInstitution(String cnpjRoot, String name, String segment, String situation, String groupCode,
                             String leaderRoot) {
}
