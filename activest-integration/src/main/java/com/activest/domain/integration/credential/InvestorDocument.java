package com.activest.domain.integration.credential;

/**
 * O CPF do investidor numa fonte que identifica o usuario pelo documento — a B3
 * (docs/planos/PLANO_INTEGRACAO_B3.md, G1 e D10).
 *
 * <p>Nao e segredo de acesso: a API da B3 usa a credencial da plataforma (client_credentials +
 * certificado, no {@code .env}) e consulta pelo CPF. Mesmo assim e dado pessoal, e vai cifrado
 * para o banco como qualquer credencial. Guarda so os digitos, com os digitos verificadores
 * conferidos.</p>
 */
public record InvestorDocument(String documentNumber) implements SourceCredentials {

    public InvestorDocument {
        String digits = documentNumber == null ? "" : documentNumber.replaceAll("\\D", "");
        if (!validCpf(digits)) {
            throw new IllegalArgumentException("Informe um CPF válido.");
        }
        documentNumber = digits;
    }

    /** Os 2 ultimos digitos: o que a tela mostra ("***.***.***-01"). */
    @Override
    public String fingerprint() {
        return documentNumber.substring(documentNumber.length() - 2);
    }

    @Override
    public String toString() {
        return "InvestorDocument[documentNumber=*********" + fingerprint() + "]";
    }

    static boolean validCpf(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digit(d, 9) == d.charAt(9) - '0' && digit(d, 10) == d.charAt(10) - '0';
    }

    private static int digit(String d, int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
