import { ReactNode } from 'react';
import { IntegrationProvider } from '@/types';

/**
 * O que é próprio de cada fonte no formulário de conexão (docs/planos/PLANO_INTEGRACOES.md, 10.1): o passo a passo, a
 * observação e o texto da autorização. É o único ponto da tela de Integrações individualizado por fonte; o resto do
 * formulário depende só do tipo de credencial.
 */
export interface SourceHelp {
  title: string;
  steps: ReactNode[];
  note?: ReactNode;
  consent: ReactNode;
}

const strong = (text: string) => <span className="text-white">{text}</span>;

export const SOURCE_HELP: Record<IntegrationProvider, SourceHelp> = {
  BINANCE: {
    title: 'Como criar a chave na Binance',
    steps: [
      <>No app ou site da Binance, abra {strong('Perfil › Gerenciamento de API')} e crie uma chave.</>,
      <>Deixe marcada {strong('apenas “Enable Reading”')}. O Activest recusa chave que negocia, saca ou transfere.</>,
      <>Restringir a chave a um IP é opcional e mais seguro.</>,
      <>Copie a chave (API Key) e o segredo (Secret Key) para os campos abaixo.</>,
    ],
    note: <>Entram as compras, vendas e conversões de cripto e as compras e vendas de ações e ETFs americanos da
      Binance Stocks (em dólar, com o câmbio do dia).</>,
    consent: <>Autorizo o Activest a {strong('ler')} na minha conta da Binance os saldos, negociações, conversões,
      compras, depósitos e as ordens de ações e ETFs, para importar as minhas operações. A leitura continua até eu
      desconectar; ao desconectar, a chave é apagada na hora.</>,
  },
  B3: {
    title: 'Como funciona',
    steps: [
      <>Confira o CPF (o do seu cadastro) e conecte.</>,
      <>No cartão da conexão, abra {strong('Autorizar na B3')}, entre com o seu login da Área do Investidor e autorize
        o Activest.</>,
      <>Volte e sincronize. Você confere as compras e vendas antes de entrarem na carteira.</>,
    ],
    note: <>Entram as negociações de ações, FIIs, ETFs e BDRs. Proventos e eventos do papel a B3 só confere: a carteira
      já os calcula pelo catálogo. A B3 publica o dia anterior a partir das 8h e aceita uma consulta por dia.</>,
    consent: <>Autorizo o Activest a {strong('ler')} na Área do Investidor da B3 as minhas negociações, movimentações,
      posição e proventos, para importar as compras e vendas e conferir a carteira. A leitura continua até eu
      desconectar aqui ou revogar na B3; ao desconectar, a credencial da conexão é apagada na hora.</>,
  },
};
