package com.activest.domain.goal;

import static com.activest.domain.goal.GoalCategory.*;
import static com.activest.domain.goal.GoalNature.*;

/**
 * Os 17 tipos do catalogo inicial (docs/planos/PLANO_METAS.md, Anexo A 4 e 5). O valor gravado em {@code goals.type}
 * e o nome da constante; o codigo de tela ("PAT-1") e o nome legivel vem daqui. O que cada tipo pede e calcula esta no
 * catalogo da app-api ({@code GoalTypeCatalog}).
 */
public enum GoalType {
    PAT_1("PAT-1", "Patrimônio-alvo", PATRIMONIO, CRESCENTE, true),
    APO_1("APO-1", "Acumular em aportes", APORTES, CRESCENTE, true),
    APO_2("APO-2", "Aporte mensal / anual", APORTES, SEQUENCIA, false),
    REN_1("REN-1", "Renda passiva", RENDA, CRESCENTE, true),
    REN_2("REN-2", "Renda cobre despesas", RENDA, CRESCENTE, true),
    REN_3("REN-3", "Renda maior que os aportes", LONGO_PRAZO, CRESCENTE, true),
    RNT_1("RNT-1", "Rentabilidade-alvo", RENTABILIDADE, CONTROLE, false),
    RNT_2("RNT-2", "Superar um índice", RENTABILIDADE, CONTROLE, false),
    ALO_1("ALO-1", "Participação de um item", ALOCACAO, CONTROLE, false),
    ALO_2("ALO-2", "Seguir a estratégia", ALOCACAO, CONTROLE, false),
    RIS_1("RIS-1", "Concentração máxima", RISCO, CONTROLE, false),
    RIS_2("RIS-2", "Queda máxima do pico", RISCO, CONTROLE, false),
    RIS_3("RIS-3", "Qualidade dos dados", RISCO, CONTROLE, false),
    ATV_1("ATV-1", "Quantidade de um ativo", ATIVO, CRESCENTE, true),
    ATV_2("ATV-2", "Valor em um ativo", ATIVO, CRESCENTE, true),
    ATV_3("ATV-3", "Proventos compram cotas", ATIVO, CRESCENTE, false),
    LP_1("LP-1", "Aposentadoria (estimativa)", LONGO_PRAZO, ESTIMATIVA, true);

    private final String code;
    private final String label;
    private final GoalCategory category;
    private final GoalNature nature;
    private final boolean projection;

    GoalType(String code, String label, GoalCategory category, GoalNature nature, boolean projection) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.nature = nature;
        this.projection = projection;
    }

    public String code() { return code; }
    public String label() { return label; }
    public GoalCategory category() { return category; }
    public GoalNature nature() { return nature; }
    public boolean projection() { return projection; }

    /** Aceita o nome da constante ou o codigo de tela. */
    public static GoalType parse(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim().toUpperCase().replace('-', '_');
        for (GoalType t : values()) {
            if (t.name().equals(v)) {
                return t;
            }
        }
        return null;
    }
}
