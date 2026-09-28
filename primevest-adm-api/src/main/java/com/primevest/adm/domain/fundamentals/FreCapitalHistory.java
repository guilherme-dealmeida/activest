package com.primevest.adm.domain.fundamentals;

import com.primevest.adm.integration.cvm.CvmFreCapital;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Capital integralizado de cada companhia ao longo do tempo, a partir dos FREs de
 * todos os anos.
 *
 * <p><b>Cada FRE vale pela data em que foi entregue.</b> O formulario lista os
 * eventos de capital com a quantidade de acoes, mas a quantidade acompanha o
 * formulario, nao o evento: no FRE 2025 da Petrobras o aumento aprovado em
 * 02/04/2014 aparece com 12.888.732.761 acoes (a quantidade depois do
 * cancelamento de acoes em tesouraria), enquanto o FRE 2015 mostra 13.044.496.930
 * para o mesmo periodo. Por isso a quantidade numa data sai do FRE de referencia
 * mais proxima dessa data — e, dentro dele, do ultimo evento aprovado ate ela.
 * Do mesmo FRE, vale a versao mais alta.</p>
 */
public final class FreCapitalHistory {

    /** cnpj -> data de referencia do FRE -> data de aprovacao -> evento. */
    private final Map<String, TreeMap<LocalDate, TreeMap<LocalDate, CvmFreCapital.Event>>> byCnpj = new HashMap<>();
    /** cnpj -> data de referencia do FRE -> versao guardada. */
    private final Map<String, Map<LocalDate, Integer>> versions = new HashMap<>();

    public FreCapitalHistory(Collection<CvmFreCapital.Event> events) {
        for (CvmFreCapital.Event e : events) {
            Map<LocalDate, Integer> v = versions.computeIfAbsent(e.cnpj(), k -> new HashMap<>());
            Integer kept = v.get(e.referenceDate());
            TreeMap<LocalDate, TreeMap<LocalDate, CvmFreCapital.Event>> forms =
                    byCnpj.computeIfAbsent(e.cnpj(), k -> new TreeMap<>());
            if (kept == null || e.version() > kept) {
                v.put(e.referenceDate(), e.version());
                forms.put(e.referenceDate(), new TreeMap<>());
            } else if (e.version() < kept) {
                continue;
            }
            forms.get(e.referenceDate()).put(e.approvalDate(), e);
        }
    }

    public static FreCapitalHistory empty() {
        return new FreCapitalHistory(List.of());
    }

    /**
     * O capital integralizado em {@code date}: do FRE de referencia mais proxima
     * (empate: o posterior), o ultimo evento aprovado ate a data. Nulo se nenhum
     * FRE da companhia tem evento ate la.
     */
    public CvmFreCapital.Event at(String cnpj, LocalDate date) {
        TreeMap<LocalDate, TreeMap<LocalDate, CvmFreCapital.Event>> forms = byCnpj.get(cnpj);
        if (forms == null) {
            return null;
        }
        List<LocalDate> byDistance = forms.keySet().stream()
                .sorted(Comparator.<LocalDate>comparingLong(ref -> Math.abs(ChronoUnit.DAYS.between(ref, date)))
                        .thenComparing(Comparator.reverseOrder()))
                .toList();
        for (LocalDate ref : byDistance) {
            Map.Entry<LocalDate, CvmFreCapital.Event> e = forms.get(ref).floorEntry(date);
            if (e != null) {
                return e.getValue();
            }
        }
        return null;
    }

    public int companies() {
        return byCnpj.size();
    }
}
