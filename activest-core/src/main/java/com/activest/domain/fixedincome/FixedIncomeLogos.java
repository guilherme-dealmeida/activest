package com.activest.domain.fixedincome;

import com.activest.domain.asset.AssetLogos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Logos das emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, Adendo A): a pasta
 * {@code carga/data/logos/rf/}, um arquivo por raiz de CNPJ ({@code 30680829.svg}). Mesmo principio dos logos do
 * catalogo ({@link AssetLogos}): o arquivo e o estado, sem coluna no banco; quem baixa e a adm-api
 * ({@code FixedIncomeLogoService}), a app-api so le.
 */
@Component
public class FixedIncomeLogos {

    /** Subpasta de {@code activest.logos.dir}. */
    public static final String SUBDIR = "rf";

    private final AssetLogos folder;

    public FixedIncomeLogos(@Value("${activest.logos.dir:../carga/data/logos}") String logosDir) {
        this.folder = new AssetLogos(Path.of(logosDir).resolve(SUBDIR).toString());
    }

    public Path dir() {
        return folder.dir();
    }

    /** A raiz tem arquivo de logo. */
    public boolean hasLogo(String cnpjRoot) {
        return valid(cnpjRoot) && folder.hasLogo(cnpjRoot);
    }

    /** O arquivo, para a API servir. */
    public Optional<AssetLogos.LogoFile> find(String cnpjRoot) {
        return valid(cnpjRoot) ? folder.find(cnpjRoot) : Optional.empty();
    }

    /** Quem gravou nesta JVM avisa (o indice muda na hora). */
    public void stored(String cnpjRoot, String fileName) {
        folder.stored(cnpjRoot, fileName);
    }

    /** Arquivos da mesma raiz com outra extensao (sobra de troca de fonte). */
    public List<Path> otherFiles(String cnpjRoot, String keep) {
        return folder.otherFiles(cnpjRoot, keep);
    }

    public int reindex() {
        return folder.reindex();
    }

    /** So 8 digitos: o nome do arquivo vem da raiz, nunca de texto livre. */
    public static boolean valid(String cnpjRoot) {
        return cnpjRoot != null && cnpjRoot.matches("\\d{8}");
    }
}
