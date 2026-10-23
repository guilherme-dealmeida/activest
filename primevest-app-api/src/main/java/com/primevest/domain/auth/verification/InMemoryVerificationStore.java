package com.primevest.domain.auth.verification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link VerificationStore} em memória, para os testes (que rodam sem Redis). Não serve para mais de
 * uma instância da API nem sobrevive a reinício — por isso não é a opção da aplicação.
 */
public class InMemoryVerificationStore implements VerificationStore {

    private record Entry(String value, Instant expiresAt) {}

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryVerificationStore(Clock clock) {
        this.clock = clock;
    }

    private Optional<Entry> live(String key) {
        Entry e = data.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(e.expiresAt())) {
            data.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e);
    }

    @Override
    public Optional<String> get(String key) {
        return live(key).map(Entry::value);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        data.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public synchronized long increment(String key, Duration ttl) {
        long created = live(key).map(e -> Long.parseLong(e.value()) + 1).orElse(1L);
        Instant expires = live(key).map(Entry::expiresAt).orElse(clock.instant().plus(ttl));
        data.put(key, new Entry(String.valueOf(created), expires));
        return created;
    }

    @Override
    public long count(String key) {
        return live(key).map(e -> Long.parseLong(e.value())).orElse(0L);
    }

    /** Testes: esquece tudo entre um cenário e outro. */
    public void clear() {
        data.clear();
    }
}
