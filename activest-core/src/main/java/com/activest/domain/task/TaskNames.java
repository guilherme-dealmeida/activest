package com.activest.domain.task;

import java.util.regex.Pattern;

/**
 * Regra de nome de tipo, tarefa e atributo (passo 1e): maiúsculo, sem acento, sem espaço, sem ç — palavras separadas
 * por {@code _}. A mesma regra está nos CHECKs da V61.
 */
public final class TaskNames {

    private static final Pattern NAME = Pattern.compile("^[A-Z0-9]+(_[A-Z0-9]+)*$");

    public static final int MAX_LENGTH = 80;

    private TaskNames() {
    }

    public static boolean isValid(String name) {
        return name != null && name.length() <= MAX_LENGTH && NAME.matcher(name).matches();
    }
}
