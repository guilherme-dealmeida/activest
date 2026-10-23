package com.activest.adm.task;

import com.activest.adm.domain.issuer.IssuerLinkService;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.issuer.ShareClass;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Curadoria: composição de uma unit, {@code COMPOSITION} = {@code ON=1;PNA=2} (D4). */
@Component
@RequiredArgsConstructor
public class UnitCompositionTask implements TaskHandler {

    private final IssuerLinkService service;
    private final AssetRepository assets;

    @Override
    public String type() {
        return "UNIT_COMPOSITION";
    }

    @Override
    public void run(TaskRunContext ctx) {
        String ticker = ctx.requireText("TICKER").toUpperCase();
        Long assetId = assets.findByTicker(ticker)
            .orElseThrow(() -> new IllegalArgumentException("Ativo nao encontrado: " + ticker)).getId();
        Map<ShareClass, Integer> composition = parse(ctx.requireText("COMPOSITION"));
        service.setUnitComposition(assetId, composition);
        ctx.summary(ticker + " = " + format(composition));
    }

    public static Map<ShareClass, Integer> parse(String text) {
        Map<ShareClass, Integer> map = new EnumMap<>(ShareClass.class);
        for (String part : text.split(";")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String[] kv = p.split("=");
            try {
                map.put(ShareClass.valueOf(kv[0].strip().toUpperCase()), Integer.parseInt(kv[1].strip()));
            } catch (RuntimeException e) {
                throw new TaskInputException("COMPOSITION: use CLASSE=quantidade separados por ; (ex.: ON=1;PNA=2).");
            }
        }
        if (map.isEmpty()) {
            throw new TaskInputException("COMPOSITION vazia.");
        }
        return map;
    }

    public static String format(Map<ShareClass, Integer> composition) {
        return composition.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(";"));
    }
}
