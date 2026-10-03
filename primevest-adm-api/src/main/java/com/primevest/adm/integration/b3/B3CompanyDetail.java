package com.primevest.adm.integration.b3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro de uma empresa ({@code GetDetail}): de onde sai o ISIN de cada ticker
 * e a classificacao setorial da B3 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B3CompanyDetail(String issuingCompany, String tradingName, String cnpj, String codeCVM,
                              String industryClassification, List<OtherCode> otherCodes) {

    public List<OtherCode> codes() {
        return otherCodes != null ? otherCodes : List.of();
    }

    /**
     * Setor, subsetor e segmento, nessa ordem. Lista vazia quando a B3 nao
     * classifica a empresa.
     *
     * <p>A B3 devolve a virgula como ponto ("Petróleo. Gás e Biocombustíveis").
     * O ponto volta a ser virgula quando fecha uma palavra longa e a seguinte
     * comeca com maiuscula — abreviaturas como "Máq. e Equip." e
     * "Soc. Arrendamento" ficam como estao. Ponto solto entre espacos
     * ("Motores . Compressores") tambem e virgula, e ponto duplo depois de
     * abreviatura volta a ser ponto e virgula ("Serv.Méd.Hospit..Análises" ->
     * "Serv.Méd.Hospit.,Análises", como na classificacao setorial da B3).</p>
     */
    public List<String> classification() {
        List<String> out = new ArrayList<>();
        if (industryClassification == null || industryClassification.isBlank()) {
            return out;
        }
        for (String part : industryClassification.split("\\s*/\\s*")) {
            out.add(restoreCommas(part.trim()));
        }
        return out;
    }

    static String restoreCommas(String text) {
        return text.replaceAll("\\.\\.(?=\\p{L})", ".,")
                .replaceAll("\\s+\\.\\s+", ", ")
                .replaceAll("(\\p{L}{7,})\\. (?=\\p{Lu})", "$1, ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtherCode(String code, String isin) {
    }
}
