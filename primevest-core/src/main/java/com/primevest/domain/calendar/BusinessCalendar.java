package com.primevest.domain.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dias uteis do mercado brasileiro (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R2; D7): segunda a sexta, menos os
 * feriados nacionais — o calendario da ANBIMA, que e o do CDI.
 *
 * <p>Feriados calculados, sem tabela: fixos (1/1, 21/4, 1/5, 7/9, 12/10, 2/11, 15/11, 25/12 e 20/11 desde 2024,
 * Lei 14.759/2023) e moveis pela Pascoa (Carnaval segunda e terca, Sexta-feira Santa, Corpus Christi). Conferido em
 * 05/10/2026 contra todas as datas do CDI de 2010 a 2026: nenhuma divergencia.</p>
 */
public final class BusinessCalendar {

    private static final Map<Integer, Set<LocalDate>> HOLIDAYS = new ConcurrentHashMap<>();

    private BusinessCalendar() {
    }

    public static boolean isBusinessDay(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays(d.getYear()).contains(d);
    }

    /** Dias uteis em {@code [from, to]}, inclusive nos dois extremos. */
    public static int businessDaysBetween(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isBusinessDay(d)) {
                n++;
            }
        }
        return n;
    }

    /** Dias uteis do mes de {@code anyDay}. */
    public static int businessDaysInMonth(LocalDate anyDay) {
        LocalDate first = anyDay.withDayOfMonth(1);
        return businessDaysBetween(first, first.withDayOfMonth(first.lengthOfMonth()));
    }

    public static Set<LocalDate> holidays(int year) {
        return HOLIDAYS.computeIfAbsent(year, BusinessCalendar::compute);
    }

    private static Set<LocalDate> compute(int year) {
        Set<LocalDate> s = new HashSet<>();
        s.add(LocalDate.of(year, Month.JANUARY, 1));
        s.add(LocalDate.of(year, Month.APRIL, 21));
        s.add(LocalDate.of(year, Month.MAY, 1));
        s.add(LocalDate.of(year, Month.SEPTEMBER, 7));
        s.add(LocalDate.of(year, Month.OCTOBER, 12));
        s.add(LocalDate.of(year, Month.NOVEMBER, 2));
        s.add(LocalDate.of(year, Month.NOVEMBER, 15));
        if (year >= 2024) {
            s.add(LocalDate.of(year, Month.NOVEMBER, 20));
        }
        s.add(LocalDate.of(year, Month.DECEMBER, 25));
        LocalDate easter = easter(year);
        s.add(easter.minusDays(48)); // Carnaval, segunda
        s.add(easter.minusDays(47)); // Carnaval, terca
        s.add(easter.minusDays(2));  // Sexta-feira Santa
        s.add(easter.plusDays(60));  // Corpus Christi
        return Set.copyOf(s);
    }

    /** Domingo de Pascoa (algoritmo de Meeus/Jones/Butcher, calendario gregoriano). */
    static LocalDate easter(int y) {
        int a = y % 19;
        int b = y / 100;
        int c = y % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }
}
