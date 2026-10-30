package com.activest.domain.asset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Acha um ativo pelo ticker depois da V13, em que o ticker deixou de ser único no catálogo
 * (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D3, P2):
 *
 * <ul>
 *   <li>o mesmo ticker existe em tipos diferentes — META é cripto (MetaDAO) e ação (Meta Platforms) —, então a busca
 *       leva o tipo, ou os tipos que a fonte conhece (B3 → Ação/FII/ETF/BDR/Tesouro; Binance → Cripto);</li>
 *   <li>no mesmo tipo, um ticker pode ter sido reaproveitado: o papel que saiu da bolsa fica com o ticker que tinha e
 *       com a data de saída. Sem data, vale o listado; com a data de uma transação, vale o que estava listado naquele
 *       dia (P2).</li>
 * </ul>
 *
 * <p>O ticker é procurado como veio e, sem resultado, em maiúsculas (as telas e as fontes nem sempre normalizam).</p>
 */
@Component
@RequiredArgsConstructor
public class AssetResolver {

    private final AssetRepository assets;

    /** O ativo do tipo com este ticker: o listado; sem listado, o de saída mais recente. */
    @Transactional(readOnly = true)
    public Optional<Asset> find(String ticker, String typeName) {
        return find(ticker, typeName, null);
    }

    /** O ativo do tipo com este ticker que estava listado na data (P2); data nula = o listado hoje. */
    @Transactional(readOnly = true)
    public Optional<Asset> find(String ticker, String typeName, LocalDate date) {
        if (typeName == null || typeName.isBlank()) {
            return Optional.empty();
        }
        return pick(candidates(ticker, Set.of(typeName.strip().toUpperCase())), date);
    }

    /**
     * O ativo com este ticker num dos tipos dados (os tipos de uma fonte). Vazio se o ticker estiver listado em mais de
     * um desses tipos — a fonte não tem como dizer qual.
     */
    @Transactional(readOnly = true)
    public Optional<Asset> findInTypes(String ticker, Collection<String> typeNames) {
        return single(candidates(ticker, typeNames), null);
    }

    /**
     * Sem o tipo: só quando o ticker não se repete entre os tipos (rotas e telas da B3, que nunca repetem — I3). Ticker
     * que existe em mais de um tipo devolve vazio: quem chama pede o tipo.
     */
    @Transactional(readOnly = true)
    public Optional<Asset> findUnique(String ticker) {
        return single(candidates(ticker, null), null);
    }

    /** Com o tipo, o par (ticker, tipo); sem o tipo, só o ticker que não se repete (rotas com o tipo opcional). */
    @Transactional(readOnly = true)
    public Optional<Asset> findTypedOrUnique(String ticker, String typeName) {
        return typeName != null && !typeName.isBlank() ? find(ticker, typeName) : findUnique(ticker);
    }

    /** Pelo id (quem já tem o ativo — uma proposta de importação, uma meta). */
    @Transactional(readOnly = true)
    public Optional<Asset> byId(Long id) {
        return id == null ? Optional.empty() : assets.findById(id);
    }

    /**
     * O ativo de uma transação ou pedido da tela (I2): com o tipo, o par (ticker, tipo) na data; sem o tipo, só se o
     * ticker não se repetir entre os tipos. A mensagem diz se faltou o tipo ou se o ativo não existe.
     */
    @Transactional(readOnly = true)
    public Asset require(String ticker, String typeName, LocalDate date) {
        boolean typed = typeName != null && !typeName.isBlank();
        Optional<Asset> a = typed ? find(ticker, typeName, date) : findUnique(ticker);
        return a.orElseThrow(() -> new com.activest.exception.BusinessException(!typed && isAmbiguous(ticker)
                ? "O ticker " + ticker + " existe em mais de um tipo de ativo: informe o tipo."
                : "Ativo não encontrado no catálogo: " + ticker + ". Selecione um ativo existente na busca."));
    }

    /** O ticker existe em mais de um tipo? (Para a API responder "informe o tipo" em vez de "não encontrado".) */
    @Transactional(readOnly = true)
    public boolean isAmbiguous(String ticker) {
        return candidates(ticker, null).stream().map(a -> a.getAssetType().getName()).distinct().count() > 1;
    }

    private List<Asset> candidates(String ticker, Collection<String> typeNames) {
        if (ticker == null || ticker.isBlank()) {
            return List.of();
        }
        String t = ticker.strip();
        List<Asset> found = query(t, typeNames);
        if (found.isEmpty() && !t.equals(t.toUpperCase())) {
            found = query(t.toUpperCase(), typeNames);
        }
        return found;
    }

    private List<Asset> query(String ticker, Collection<String> typeNames) {
        return typeNames == null ? assets.findAllByTicker(ticker) : assets.findAllByTickerInTypes(ticker, typeNames);
    }

    /** Um só tipo entre os candidatos; mais de um tipo = ambíguo (vazio). */
    private static Optional<Asset> single(List<Asset> cs, LocalDate date) {
        Set<Long> types = cs.stream().map(a -> a.getAssetType().getId()).collect(Collectors.toSet());
        return types.size() > 1 ? Optional.empty() : pick(cs, date);
    }

    /**
     * Entre os candidatos de um tipo (P2): com data, o deslistado que ainda estava na bolsa naquele dia (o de saída mais
     * próxima, não anterior à data); sem ele, o listado; sem listado, o de saída mais recente.
     */
    static Optional<Asset> pick(List<Asset> cs, LocalDate date) {
        if (cs.isEmpty()) {
            return Optional.empty();
        }
        if (date != null) {
            Optional<Asset> then = cs.stream()
                    .filter(a -> a.getDelistedOn() != null && !date.isAfter(a.getDelistedOn()))
                    .min(Comparator.comparing(Asset::getDelistedOn));
            if (then.isPresent()) {
                return then;
            }
        }
        Optional<Asset> listed = cs.stream().filter(a -> a.getDelistedOn() == null).findFirst();
        return listed.isPresent() ? listed
                : cs.stream().max(Comparator.comparing(Asset::getDelistedOn));
    }
}
