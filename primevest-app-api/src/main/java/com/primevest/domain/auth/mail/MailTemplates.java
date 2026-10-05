package com.primevest.domain.auth.mail;

import com.primevest.domain.auth.verification.VerificationPurpose;
import org.springframework.web.util.HtmlUtils;

/**
 * Textos dos e-mails da conta: assunto, versão texto e HTML simples no visual do PrimeVest (fundo
 * escuro, dourado). Tudo em português; nenhum link com token — o código é digitado na tela.
 */
final class MailTemplates {

    record Mail(String subject, String text, String html) {}

    private MailTemplates() {
    }

    static Mail code(VerificationPurpose purpose, String name, String code, long minutes, String appUrl) {
        String assunto;
        String intro;
        switch (purpose) {
            case SIGNUP -> {
                assunto = "Seu código de cadastro no PrimeVest";
                intro = "Use o código abaixo para confirmar o seu e-mail e concluir o cadastro.";
            }
            case RESET_PASSWORD -> {
                assunto = "Código para redefinir a sua senha do PrimeVest";
                intro = "Recebemos um pedido para redefinir a sua senha. Use o código abaixo na tela do PrimeVest.";
            }
            case CHANGE_EMAIL -> {
                assunto = "Confirme o seu novo e-mail no PrimeVest";
                intro = "Use o código abaixo para confirmar este endereço como o novo e-mail da sua conta.";
            }
            case DELETE_ACCOUNT -> {
                assunto = "Código para excluir a sua conta do PrimeVest";
                intro = "Recebemos um pedido para EXCLUIR DEFINITIVAMENTE a sua conta e todos os seus dados. "
                    + "Só digite o código abaixo se foi você quem pediu.";
            }
            default -> throw new IllegalArgumentException(purpose.name());
        }
        String rodape = "O código vale " + minutes + " minutos. Se você não pediu, ignore este e-mail: "
            + "nada muda na sua conta.";
        String texto = saudacao(name) + "\n\n" + intro + "\n\n    " + code + "\n\n" + rodape + "\n\n" + assinatura(appUrl);
        String html = moldura(assunto,
            p(saudacao(name)) + p(intro)
                + "<p style=\"margin:24px 0;text-align:center\"><span style=\"display:inline-block;padding:14px 22px;"
                + "font-size:28px;letter-spacing:8px;font-weight:700;color:#000000;background:#c9a84c;border-radius:8px;"
                + "font-family:Consolas,Menlo,monospace\">" + code + "</span></p>"
                + p("<span style=\"color:#9ca3af\">" + esc(rodape) + "</span>"),
            appUrl);
        return new Mail(assunto, texto, html);
    }

    static Mail notice(AccountNotice notice, String name, String appUrl) {
        String assunto;
        String corpo;
        switch (notice) {
            case ACCOUNT_EXISTS -> {
                assunto = "Você já tem uma conta no PrimeVest";
                corpo = "Alguém (provavelmente você) tentou criar uma conta no PrimeVest com este e-mail, mas ele já "
                    + "está cadastrado. Para entrar, use a tela de login; se esqueceu a senha, use \"Esqueci a senha\". "
                    + "Se não foi você, ignore este e-mail.";
            }
            case PASSWORD_CHANGED -> {
                assunto = "A sua senha do PrimeVest foi alterada";
                corpo = "A senha da sua conta acabou de ser alterada, e as outras sessões abertas foram encerradas. "
                    + "Se não foi você, use \"Esqueci a senha\" na tela de login agora mesmo.";
            }
            case EMAIL_CHANGED -> {
                assunto = "O e-mail da sua conta do PrimeVest foi alterado";
                corpo = "O e-mail de acesso da sua conta foi trocado por outro endereço, e este deixa de ser usado "
                    + "para entrar. Se não foi você, responda a quem administra o PrimeVest imediatamente.";
            }
            case ACCOUNT_DELETED -> {
                assunto = "A sua conta do PrimeVest foi excluída";
                corpo = "A sua conta e todos os dados ligados a ela (carteira, transações, estratégia, metas e "
                    + "integrações) foram excluídos definitivamente.";
            }
            default -> throw new IllegalArgumentException(notice.name());
        }
        String texto = saudacao(name) + "\n\n" + corpo + "\n\n" + assinatura(appUrl);
        return new Mail(assunto, texto, moldura(assunto, p(saudacao(name)) + p(corpo), appUrl));
    }

    private static String saudacao(String name) {
        return name == null || name.isBlank() ? "Olá," : "Olá, " + name.trim().split("\\s+")[0] + ",";
    }

    private static String assinatura(String appUrl) {
        return "PrimeVest — Disciplina leva à liberdade\n" + appUrl;
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 14px;line-height:1.55\">" + html + "</p>";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }

    private static String moldura(String titulo, String conteudo, String appUrl) {
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><title>" + esc(titulo) + "</title></head>"
            + "<body style=\"margin:0;padding:24px;background:#000000;font-family:Inter,Segoe UI,Arial,sans-serif;color:#e5e7eb\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;"
            + "background:#111111;border:1px solid #2a2a2a;border-radius:12px\"><tr><td style=\"padding:28px 32px\">"
            + "<p style=\"margin:0 0 20px;font-size:22px;font-weight:700;color:#c9a84c\">Prime<span style=\"color:#ffffff\">Vest</span></p>"
            + conteudo
            + "<p style=\"margin:24px 0 0;font-size:12px;color:#6b7280\">PrimeVest — Disciplina leva à liberdade · "
            + "<a href=\"" + esc(appUrl) + "\" style=\"color:#c9a84c\">" + esc(appUrl) + "</a></p>"
            + "</td></tr></table></td></tr></table></body></html>";
    }
}
