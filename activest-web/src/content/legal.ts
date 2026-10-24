/**
 * Termos de Uso e Política de Privacidade (docs/planos/PLANO_CADASTRO_USUARIO.md, D9).
 *
 * TEXTO FICTÍCIO E PROVISÓRIO: existe para o fluxo de aceite funcionar de ponta a ponta. Antes da
 * produção, o texto definitivo entra aqui E a versão muda nos dois lados — TERMS_VERSION abaixo e
 * LegalTerms.CURRENT_VERSION no activest-core. Trocar a versão faz quem aceitou a anterior ver o
 * modal de novo aceite (D19).
 */
export const TERMS_VERSION = '2026-10-provisorio';

export interface LegalSection {
  title: string;
  paragraphs: string[];
}

export interface LegalDocument {
  title: string;
  updatedAt: string;
  sections: LegalSection[];
}

export const PROVISIONAL_NOTICE =
  'Texto provisório, fictício, usado durante o desenvolvimento. Será substituído pelo texto definitivo antes da produção.';

export const TERMS: LegalDocument = {
  title: 'Termos de Uso',
  updatedAt: '05/10/2026',
  sections: [
    {
      title: '1. O que é o Activest',
      paragraphs: [
        'O Activest é uma ferramenta de organização de investimentos pessoais: registra transações, calcula posição, alocação, proventos e acompanha uma estratégia definida pelo próprio usuário.',
        'O Activest não é corretora, não executa ordens e não faz recomendação de investimento. As informações exibidas servem de apoio à decisão, que é sempre do usuário.',
      ],
    },
    {
      title: '2. Conta e acesso',
      paragraphs: [
        'Para usar o Activest é preciso criar uma conta com e-mail confirmado ou entrar com uma conta Google. O usuário é responsável por manter a senha em sigilo e pelos acessos feitos com ela.',
        'O usuário pode excluir a própria conta a qualquer momento em Configurações. A exclusão é definitiva e apaga os dados ligados à conta.',
      ],
    },
    {
      title: '3. Dados de mercado e integrações',
      paragraphs: [
        'Cotações, proventos e dados de empresas vêm de fontes públicas e de terceiros e podem conter atrasos ou erros. O Activest não garante a exatidão dessas informações.',
        'Integrações com corretoras e com a B3 só leem dados, e somente depois da autorização expressa do usuário, que pode desconectá-las a qualquer momento.',
      ],
    },
    {
      title: '4. Uso adequado',
      paragraphs: [
        'É proibido usar o Activest para fins ilícitos, tentar acessar dados de outros usuários ou interferir no funcionamento do serviço.',
      ],
    },
    {
      title: '5. Alterações destes termos',
      paragraphs: [
        'Estes termos podem mudar. Quando a mudança for relevante, o usuário será avisado na próxima entrada e precisará aceitar a nova versão para continuar usando o Activest.',
      ],
    },
  ],
};

export const PRIVACY: LegalDocument = {
  title: 'Política de Privacidade',
  updatedAt: '05/10/2026',
  sections: [
    {
      title: '1. Dados que coletamos',
      paragraphs: [
        'Cadastro: nome, e-mail, celular (opcional) e CPF (opcional). Com o login pelo Google, também o identificador da conta Google e a foto do perfil.',
        'Uso: transações, posições, estratégia, metas e preços-teto que o próprio usuário registra ou importa pelas integrações que autorizar.',
      ],
    },
    {
      title: '2. Para que usamos',
      paragraphs: [
        'Para identificar o usuário, proteger o acesso à conta (códigos de verificação por e-mail) e calcular as informações da carteira.',
        'O CPF é usado somente para as integrações que dependem dele, como a Área do Investidor da B3, e nunca é exibido por inteiro.',
      ],
    },
    {
      title: '3. Como protegemos',
      paragraphs: [
        'Senhas são guardadas apenas como hash. CPF e credenciais de integração são cifrados. Os códigos de verificação valem por poucos minutos e não ficam guardados em claro.',
      ],
    },
    {
      title: '4. Compartilhamento',
      paragraphs: [
        'O Activest não vende dados pessoais. Os dados só são enviados a terceiros quando necessário para o funcionamento do serviço (por exemplo, o envio de e-mails) ou por obrigação legal.',
      ],
    },
    {
      title: '5. Seus direitos',
      paragraphs: [
        'O usuário pode consultar e corrigir os próprios dados em Configurações e excluir a conta a qualquer momento, o que apaga definitivamente os dados ligados a ela.',
      ],
    },
  ],
};
