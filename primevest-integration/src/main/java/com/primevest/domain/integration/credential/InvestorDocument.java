package com.primevest.domain.integration.credential;

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
        String digitos = documentNumber == null ? "" : documentNumber.replaceAll("\\D", "");
        if (!cpfValido(digitos)) {
            throw new IllegalArgumentException("Informe um CPF válido.");
        }
        documentNumber = digitos;
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

    static boolean cpfValido(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, 9) == d.charAt(9) - '0' && digito(d, 10) == d.charAt(10) - '0';
    }

    private static int digito(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
