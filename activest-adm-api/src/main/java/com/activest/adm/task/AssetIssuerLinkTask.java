package com.activest.adm.task;

import com.activest.adm.domain.issuer.IssuerLinkService;
import com.activest.domain.asset.AssetResolver;
import com.activest.domain.asset.AssetTypeNames;
import com.activest.domain.issuer.ShareClass;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Curadoria: liga um ativo a um emissor pelo CNPJ, com a classe da ação; sem CNPJ, desfaz (D4). Ticker ou emissor que
 * não existe: {@link IllegalArgumentException} (o endpoint {@code /assets/{id}/issuer} responde 404).
 */
@Component
@RequiredArgsConstructor
public class AssetIssuerLinkTask implements TaskHandler {

    private final IssuerLinkService service;
    private final AssetResolver assets;

    @Override
    public String type() {
        return "ASSET_ISSUER_LINK";
    }

    @Override
    public void run(TaskRunContext ctx) {
        String ticker = ctx.requireText("TICKER").toUpperCase();
        // Emissor é empresa ou fundo listado na B3: o ticker é procurado nesses tipos (V13)
        Long assetId = assets.findInTypes(ticker, AssetTypeNames.B3_LISTED)
            .orElseThrow(() -> new IllegalArgumentException("Ativo nao encontrado: " + ticker)).getId();
        String cnpj = ctx.text("ISSUER_CNPJ");
        ShareClass shareClass = shareClass(ctx.text("SHARE_CLASS"));
        service.setManual(assetId, null, cnpj, shareClass);
        ctx.summary(cnpj == null ? ticker + ": ligação desfeita"
            : ticker + " ligado ao CNPJ " + cnpj + (shareClass == null ? "" : " (" + shareClass + ")"));
    }

    static ShareClass shareClass(String text) {
        if (text == null) {
            return null;
        }
        try {
            return ShareClass.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TaskInputException("SHARE_CLASS: use ON, PN, PNA, PNB, PNC, PND ou UNIT.");
        }
    }
}
