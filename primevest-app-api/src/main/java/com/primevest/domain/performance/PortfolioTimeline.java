package com.primevest.domain.performance;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A carteira dia a dia (docs/planos/PLANO_RENTABILIDADE.md, 4.1 e R1-R4): um eixo de pregoes da B3 (mais hoje, se hoje
 * nao for pregao) e, por posicao, o valor ao fim de cada dia, os aportes, as retiradas e os proventos. Nada e gravado;
 * quem monta e o {@link PortfolioTimelineBuilder}. Valores em reais, {@code double} (o arredondamento e so na saida).
 *
 * <p>Imutavel depois de montada: pode ser reaproveitada entre leituras (cache do builder).</p>
 */
public final class PortfolioTimeline {

    /** D3 (17/10/2026): pregoes sem negocio aceitos com o ultimo fechamento. */
    public static final int MAX_STALE_DAYS = 5;

    private final List<LocalDate> dates;
    private final List<PositionSeries> positions;
    private final LocalDate today;

    public PortfolioTimeline(List<LocalDate> dates, List<PositionSeries> positions, LocalDate today) {
        this.dates = List.copyOf(dates);
        this.positions = List.copyOf(positions);
        this.today = today;
    }

    public static PortfolioTimeline empty(LocalDate today) {
        return new PortfolioTimeline(List.of(), List.of(), today);
    }

    public List<LocalDate> dates() {
        return dates;
    }

    public List<PositionSeries> positions() {
        return positions;
    }

    public LocalDate today() {
        return today;
    }

    public boolean isEmpty() {
        return dates.isEmpty() || positions.isEmpty();
    }

    /** Indice do primeiro dia do eixo em ou depois de {@code date}; {@code size()} se nenhum. */
    public int indexOnOrAfter(LocalDate date) {
        int i = Collections.binarySearch(dates, date);
        return i >= 0 ? i : -i - 1;
    }

    /** Indice do ultimo dia do eixo em ou antes de {@code date}; -1 se nenhum. */
    public int indexOnOrBefore(LocalDate date) {
        int i = Collections.binarySearch(dates, date);
        return i >= 0 ? i : -i - 2;
    }

    /** Um provento da posicao: dia do eixo em que entra (o pregao ex), pagamento e valores. */
    public record Distribution(int index, LocalDate exDate, LocalDate paymentDate, double gross, double net) {
    }

    /**
     * Uma posicao (ativo, ou titulo na renda fixa) dia a dia, alinhada ao eixo.
     *
     * @param gross      valor bruto ao fim do dia
     * @param net        valor liquido estimado ao fim do dia (igual ao bruto fora da renda fixa e do Tesouro)
     * @param buy        aportes do dia (comeco do dia)
     * @param sellGross  retiradas do dia, bruto (fim do dia)
     * @param sellNet    retiradas do dia, liquido recebido
     * @param distGross  proventos com pregao ex no dia, bruto
     * @param distNet    idem, liquido de IR retido
     * @param held       tinha saldo ao fim do dia
     * @param status     situacao do preco no dia (so importa com saldo)
     * @param priceDate  data do preco usado (no STALE, o ultimo negocio)
     * @param basisSensitive bruto e liquido diferem (renda fixa, Tesouro, provento com IR)
     */
    public record PositionSeries(UUID id, Long assetId, String ticker, String name, String typeName,
                                 String typeDescription, Long categoryId, String categoryName, String issuerGroup,
                                 String noteLabel, boolean reservation, boolean basisSensitive,
                                 double[] gross, double[] net, double[] buy, double[] sellGross, double[] sellNet,
                                 double[] distGross, double[] distNet, boolean[] held, PriceStatus[] status,
                                 LocalDate[] priceDate, List<Distribution> distributions) {

        public double value(int i, PerformanceBasis basis) {
            return basis == PerformanceBasis.GROSS ? gross[i] : net[i];
        }

        public double sell(int i, PerformanceBasis basis) {
            return basis == PerformanceBasis.GROSS ? sellGross[i] : sellNet[i];
        }

        public double dist(int i, PerformanceBasis basis) {
            return basis == PerformanceBasis.GROSS ? distGross[i] : distNet[i];
        }

        /** A posicao pesa no dia: tinha saldo ontem ou hoje, ou teve movimento. */
        public boolean exposed(int i) {
            return held[i] || (i > 0 && held[i - 1]) || buy[i] != 0 || sellGross[i] != 0;
        }

        /** Primeiro e ultimo dia com exposicao; {-1,-1} se nunca. */
        public int[] span() {
            int first = -1;
            int last = -1;
            for (int i = 0; i < held.length; i++) {
                if (exposed(i)) {
                    if (first < 0) {
                        first = i;
                    }
                    last = i;
                }
            }
            return new int[]{first, last};
        }
    }
}
