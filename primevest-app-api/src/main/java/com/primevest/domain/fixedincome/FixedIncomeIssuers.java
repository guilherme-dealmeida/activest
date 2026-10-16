package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetCategory;
import com.primevest.domain.asset.AssetLogos;
import com.primevest.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O cadastro de emissoras e distribuidoras visto pela carteira (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R4–R7):
 * busca dos seletores, validacao do que o usuario escolheu e o casamento do nome que a B3 informa (L21).
 */
@Service
@Transactional(readOnly = true)
public class FixedIncomeIssuers {

    /** Papel no titulo. */
    public enum Role { ISSUER, DISTRIBUTOR }

    static final Set<FixedIncomeIssuerKind> DISTRIBUTOR_KINDS = EnumSet.of(
            FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA, FixedIncomeIssuerKind.BANCO,
            FixedIncomeIssuerKind.FINANCEIRA, FixedIncomeIssuerKind.COOPERATIVA);

    private final FixedIncomeIssuerRepository repository;
    private final AssetLogos logos;

    public FixedIncomeIssuers(FixedIncomeIssuerRepository repository, AssetLogos logos) {
        this.repository = repository;
        this.logos = logos;
    }

    /**
     * Busca do seletor. Emissora: os tipos aceitos pelo produto (R4); distribuidora: R5. "Instituicao nao listada"
     * sempre por ultimo.
     */
    public List<FixedIncomeIssuerDTO> search(Role role, Asset asset, String q, int limit) {
        Set<FixedIncomeIssuerKind> kinds = role == Role.DISTRIBUTOR ? DISTRIBUTOR_KINDS : issuerKinds(asset);
        String term = q == null ? "" : q.trim().toLowerCase();
        List<FixedIncomeIssuerDTO> out = new ArrayList<>();
        if (!kinds.isEmpty()) {
            repository.search(kinds, term, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                    .forEach(i -> out.add(toDTO(i)));
        }
        if (!(role == Role.ISSUER && isTreasury(asset))) {
            notListed().ifPresent(n -> out.add(toDTO(n)));
        }
        return out;
    }

    /** A emissora escolhida para um titulo novo do produto: existe e o tipo e aceito (R4); o Tesouro e fixo (R6). */
    public FixedIncomeIssuer issuerFor(Asset asset, Long issuerId) {
        if (isTreasury(asset)) {
            return treasury();
        }
        if (issuerId == null) {
            throw new BusinessException("Informe a emissora do título.");
        }
        FixedIncomeIssuer issuer = repository.findById(issuerId)
                .orElseThrow(() -> new BusinessException("Emissora não encontrada no cadastro."));
        if (issuer.getKind() != FixedIncomeIssuerKind.NAO_LISTADA && !issuerKinds(asset).contains(issuer.getKind())) {
            throw new BusinessException(issuer.getSelectorLabel() + " não emite " + productName(asset) + ".");
        }
        return issuer;
    }

    /** A distribuidora escolhida: existe e pode distribuir (R5). */
    public FixedIncomeIssuer distributor(Long distributorId) {
        if (distributorId == null) {
            throw new BusinessException("Informe a distribuidora do título (onde ele foi comprado).");
        }
        FixedIncomeIssuer d = repository.findById(distributorId)
                .orElseThrow(() -> new BusinessException("Distribuidora não encontrada no cadastro."));
        if (d.getKind() != FixedIncomeIssuerKind.NAO_LISTADA && !DISTRIBUTOR_KINDS.contains(d.getKind())) {
            throw new BusinessException(d.getSelectorLabel() + " não é distribuidora de títulos.");
        }
        return d;
    }

    public FixedIncomeIssuer treasury() {
        return repository.findFirstByKind(FixedIncomeIssuerKind.TESOURO)
                .orElseThrow(() -> new IllegalStateException("Linha fixa Tesouro Nacional ausente (V54)"));
    }

    public Optional<FixedIncomeIssuer> notListed() {
        return repository.findFirstByKind(FixedIncomeIssuerKind.NAO_LISTADA);
    }

    public FixedIncomeIssuer notListedOrFail() {
        return notListed().orElseThrow(() -> new IllegalStateException("Linha fixa nao listada ausente (V54)"));
    }

    /**
     * L21: a instituicao que a B3 informa ("XP INVESTIMENTOS CCTVM S/A"), casada pelo nome com uma distribuidora do
     * cadastro — razao social ou nome comercial, sem acento, pontuacao e sigla societaria. Sem correspondencia unica,
     * vazio (quem chama usa a "nao listada" e avisa).
     */
    public Optional<FixedIncomeIssuer> distributorByName(String name) {
        String key = nameKey(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        List<FixedIncomeIssuer> hits = repository.search(DISTRIBUTOR_KINDS, "", PageRequest.of(0, 100000)).stream()
                .filter(i -> key.equals(nameKey(i.getLegalName())) || key.equals(nameKey(i.getTradeName())))
                .toList();
        if (hits.size() == 1) {
            return Optional.of(hits.get(0));
        }
        // mais de uma: a viva, se for uma so
        List<FixedIncomeIssuer> alive = hits.stream().filter(i -> !i.isDead()).toList();
        return alive.size() == 1 ? Optional.of(alive.get(0)) : Optional.empty();
    }

    /**
     * Emissora e distribuidora de um titulo para as telas: ids, nome de exibicao (R10), razao social (a dica), regra de
     * saida da distribuidora, codigo do papel, logo do grupo da emissora (D5) e o fundo que garante (R11).
     */
    public record NoteInfo(Long issuerId, String issuerName, String issuerLegalName, String issuerKind,
                           Long distributorId, String distributorName, String distributorLegalName,
                           String distributorSaleRule, String paperCode, String issuerLogoTicker, String guarantee) {
        public static final NoteInfo NONE = new NoteInfo(null, null, null, null, null, null, null, null, null, null,
                null);
    }

    public NoteInfo info(FixedIncomeNote note) {
        if (note == null) {
            return NoteInfo.NONE;
        }
        FixedIncomeIssuer i = note.getIssuer();
        FixedIncomeIssuer d = note.getDistributor();
        String logo = null;
        if (i != null && i.getGroup() != null && i.getGroup().getLogoTicker() != null
                && logos.hasLogo(i.getGroup().getLogoTicker())) {
            logo = i.getGroup().getLogoTicker();
        }
        String guarantee = null;
        AssetCategory c = note.getAsset() != null ? note.getAsset().getAssetCategory() : null;
        if (i != null && c != null && c.isGuaranteed() && i.getGuarantor() != null
                && i.getGuarantor() != Guarantor.NENHUM) {
            guarantee = i.getGuarantor().name();
        }
        return new NoteInfo(i != null ? i.getId() : null, i != null ? i.getDisplayName() : null,
                i != null ? i.getLegalName() : null, i != null ? i.getKind().name() : null,
                d != null ? d.getId() : null, d != null ? d.getDisplayName() : null, d != null ? d.getLegalName() : null,
                d != null && d.getSaleRule() != null ? d.getSaleRule().name() : null, note.getPaperCode(), logo,
                guarantee);
    }

    /** Codigo do papel como o banco guarda (R8): maiusculo, sem espacos nem pontuacao; vazio vira nulo. */
    public static String normalizePaperCode(String code) {
        if (code == null) {
            return null;
        }
        String c = code.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (c.isEmpty()) {
            return null;
        }
        if (c.length() > 30) {
            throw new BusinessException("Código do papel com mais de 30 caracteres.");
        }
        return c;
    }

    static String nameKey(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase()
                .replaceAll("[^A-Z0-9]+", " ").trim();
        s = s.replaceAll("\\b(S A|SA|LTDA|EIRELI|ME)$", "").trim();
        return s.replaceAll("\\s+", " ");
    }

    public FixedIncomeIssuerDTO toDTO(FixedIncomeIssuer i) {
        if (i == null) {
            return null;
        }
        FixedIncomeGroup g = i.getGroup();
        String logo = g != null && g.getLogoTicker() != null && logos.hasLogo(g.getLogoTicker()) ? g.getLogoTicker() : null;
        return new FixedIncomeIssuerDTO(i.getId(), i.getKind().name(), i.getSelectorLabel(), i.getDisplayName(),
                i.getLegalName(), g != null ? g.getName() : null, i.getStatus(), i.isDead(),
                i.getGuarantor() != null ? i.getGuarantor().name() : null,
                i.getSaleRule() != null ? i.getSaleRule().name() : null, i.getCnpjRoot(), logo);
    }

    private Set<FixedIncomeIssuerKind> issuerKinds(Asset asset) {
        if (isTreasury(asset)) {
            return EnumSet.of(FixedIncomeIssuerKind.TESOURO);
        }
        AssetCategory c = asset != null ? asset.getAssetCategory() : null;
        List<FixedIncomeIssuerKind> kinds = c != null ? c.getAcceptedIssuerKinds() : List.of();
        return kinds.isEmpty() ? EnumSet.noneOf(FixedIncomeIssuerKind.class) : EnumSet.copyOf(kinds);
    }

    private static boolean isTreasury(Asset asset) {
        return asset != null && asset.getAssetType() != null && asset.getAssetType().isTreasury();
    }

    private static String productName(Asset asset) {
        return asset == null ? "esse produto" : asset.getTicker();
    }
}
