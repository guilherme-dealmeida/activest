# PRIMEVEST — PENDENCIAS EM ABERTO

Levantado em 15/09/2026. Secao 1 resolvida no mesmo dia. Cada item foi conferido
no codigo antes de entrar aqui.
Ultima atualizacao: 17/10/2026 — secao 32 (listagem de emissoras implementada). No mesmo dia: secao 31 (novos indicadores de acoes, escopo futuro saido da listagem de
emissoras, docs/planos/PLANO_LISTAGEM_EMISSORAS.md). No mesmo dia: secao 29 (rentabilidade comparativa, docs/planos/PLANO_RENTABILIDADE.md); 20.3, 23.3 e
24.5 resolvidos. No mesmo dia, "Mudancas de tela" (cabecalho, menu, Dashboard, popover da previa da venda,
links do Rebalanceamento): secao 30. Antes: 16/10/2026 — secao 28 (emissoras e distribuidoras de renda fixa, escopo finalizado; 28.1 e 28.5 no fechamento;
docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md); 4.3 e 27.1 resolvidos, 27.2 atualizado. Antes: 15/10/2026 — secao 27 (venda de renda fixa, docs/planos/PLANO_VENDA_RENDA_FIXA.md; 27.3 a 27.5
na implementacao); 26.7 e 26.8 reescritos (a liquidacao automatica sai). Antes: 13/10/2026 — secao 13 (central de avisos) e itens 10.4, 13.2 e 25.1 resolvidos pelas
notificacoes (docs/planos/PLANO_NOTIFICACOES.md) e secao 26. Antes: 10/10/2026 — secao 25 (preco teto pelo modelo
PRECO TETO V2, docs/planos/PLANO_PRECO_TETO.md) e 18.11 c (payout passa a vir da DFC). Antes: 05/10/2026 (noite) — 2.1, 2.2, 4.1, 4.2, 10.2, 10.3, 10.4, 23.1 e 23.2 resolvidos
pela valorizacao da renda fixa (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md) e secao 24. Antes, no mesmo dia: 5.1 resolvido e secao 23 (indices de mercado,
docs/planos/PLANO_INDICES_MERCADO.md). No mesmo dia: secao 22 (cadastro de usuario,
docs/planos/PLANO_CADASTRO_USUARIO.md); 9.4 e 16.9 resolvidos. Antes: 03/10/2026 — secao 21 (pilha de teste primevest-teste,
docs/planos/PLANO_PRIMEVEST_TESTE.md). Antes: 29/09/2026 — secao 20 (ambiente de carga inicial,
docs/planos/PLANO_CARGA_INICIAL.md) e 18.10 d resolvido. Antes: 28/09/2026 (noite) — 11.6
resolvido e secao 19 (tela de Emissoras, docs/planos/PLANO_TELA_EMISSORES.md). Antes, no mesmo dia: itens 14.1
(resolvido), 18.9 b e 18.10 (F7 e F8). Antes: 27/09/2026 — secao 18 (docs/planos/PLANO_EMPRESAS_BALANCOS.md:
emissores, balancos e indicadores); itens 11.1, 11.2, 11.6 e 14.1 encaminhados
para o plano. Antes: 25/09/2026 — secao 16 (extrato da B3 pelo PrimeVest, saiu
do plano de cripto). Antes, no mesmo dia: secao 15 (cripto sem valor em reais,
docs/planos/PLANO_CRIPTO_BINANCE.md). Antes, no mesmo dia: itens 3.4 e 3.5 baixados (extrato de proventos
da B3, V24). Antes: 24/09/2026 — itens 3.5 a 3.8 (IR retido nos proventos,
docs/planos/PLANO_IR_PROVENTOS.md). Antes, no mesmo dia: item 3.4 (provento de acao sem
data de pagamento) e secao 14 (rotinas: proventos sob demanda
travando as threads e fechamento no meio do pregao), vista na carga do
extrato B3. Antes, no mesmo dia: execucao do docs/planos/PLANO_BRAPI.md: baixados 3.2,
6.1, 6.2, 6.4, 11.3 e 11.4; 11.1 atualizado. Antes, no mesmo dia: secao 13
(central de avisos, adiada do docs/planos/PLANO_BRAPI.md). Antes: 18/09/2026 — itens 9.1 a 9.8, a secao 12 (lint) e o
segundo reset das migrations.

## 1. AUTORIZACAO — RESOLVIDO em 15/09/2026

Os quatro itens foram corrigidos. O criterio seguido e o do
GoalService.findOwnedGoal: registro de outro dono responde "nao encontrado",
nunca "sem permissao" — dizer "sem permissao" confirmaria que o id existe, e um
id que existe e justamente o que um atacante procura.

Uma diferenca proposital em relacao ao GoalService: onde o dono esta a um join
de distancia, o filtro foi para dentro da consulta em vez de virar comparacao
depois de carregar. A linha de outro usuario nao chega a sair do banco.

**1.1** FEITO — TransactionService
TransactionRepository.findOwnedById filtra por
transacao -> posicao -> carteira -> usuario, com os mesmos JOIN FETCH da
listagem. getTransactionById, updateTransaction e deleteTransaction
passam por findOwnedTransaction.

**1.2** FEITO — CeilingPriceService
CeilingPriceRepository.findByIdAndUserId. updateCeilingPrice e
deleteCeilingPrice passam por findOwnedCeilingPrice. O deleteById solto
deixou de existir.

**1.3** FEITO — DividendService
DividendRepository.findByAssetIdAndOwner navega
provento -> posicao -> carteira -> usuario.
O metodo antigo findByAssetIdOrderByExDateDesc foi REMOVIDO do
repositorio: era a consulta vazante, e deixa-la la e convite para alguem
chamar de novo sem querer.

**1.4** FEITO — varredura completa dos services. Nada mais no mesmo padrao:
  - StrategyService.deleteStrategyItem ja usava
    findByUserIdAndAssetTypeId — escopado.
  - GoalService ja tinha o findOwnedGoal — era o modelo.
  - PortfolioService, DividendService.getDividendSummary e
    UserService.updateUser sempre partem do userId autenticado.
  - PortfolioService.recalculatePortfolio(portfolioId) foi removido
    em 17/09/2026 (V28): os totais da carteira nao sao mais gravados.
  - UserService.getUserById(id) aceita id arbitrario, mas o
    UserController so expoe /me e sempre passa o id autenticado.
    Vale trocar a assinatura para deixar isso explicito.

PROVA: montei dois usuarios com posicao, transacao, provento e preco teto no
MESMO ativo e rodei as tres consultas novas. Cada um ve so o proprio; o outro
ve zero. No caso dos proventos, a consulta antiga devolvia os 3 registros
(R$ 1.074) para qualquer um, e a nova devolve R$ 50 para A e R$ 25 para B.

PENDENTE, descoberto no caminho: ver item 3.3.

## 2. CALCULO — valorizacao de renda fixa da sempre zero

**2.1** FEITO em 05/10/2026 (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md): Tesouro pelo PU oficial
(marcacao a mercado, como cotacao) e renda fixa privada pela curva contratada (FixedIncomeCurve). Texto original:
PortfolioService.appreciation: sem cotacao, a valorizacao e a gravada
em portfolio_assets.appreciation, que nada preenche ainda.

Renda fixa nao tem cotacao, entao o valor atual e custo + zero. Resultado: currentValue == totalInvested, valorizacao
zero, rentabilidade zero. Nao e um bug de arredondamento — e a ausencia
do calculo.

O que falta e marcar a curva do titulo: pegar a taxa contratada da nota
(fixed_income_notes.indexer_rate) e o tempo decorrido desde a compra.
Prefixado resolve so com isso. Pos-fixado e hibrido dependem do item 5.1,
a serie historica do indice — sem CDI e IPCA nao da para saber quanto o
papel rendeu.
ATUALIZADO em 05/10/2026: a serie existe (5.1). market_index_values tem CDI, Selic,
IPCA, IGP-M, INPC e TR desde 2010, e o MarketIndexCalculator (core) ja acumula
"% do indice" e "indice + spread" entre duas datas. Falta o escopo da valorizacao:
curva do titulo por nota, pro rata do mes corrente (IPCA ainda nao divulgado:
projecao ou ultimo publicado), calendario de feriados (23.2) e o aviso da adm-api
quando um indice muda (23.1). Deve gravar pela FixedIncomeSettlement (10.3).

**2.2** FEITO em 05/10/2026: titulo sem curva (TR, dados faltando) mostra RENT. % "—" e o motivo no (i).
Texto original: Enquanto 2.1 nao existe, o total da carteira subestima o patrimonio.
Vale decidir se a tela mostra "—" em vez de 0,00% para renda fixa, para
nao parecer que o titulo nao rendeu nada.

## 3. PROVENTOS — DividendSyncScheduler (substituido em 24/09/2026 pela rotina de proventos da B3: ProventosSyncService + ProventosScheduler — PLANO_BRAPI F5)

**3.1** Linha 129: o total do provento e calculado com pa.getTotalShares(), a
posicao de HOJE, e nao a posicao que existia na data-com. Quem comprou
depois do ex-date recebe provento que nao teve direito; quem vendeu antes
deixa de receber o que teve.
O certo e reconstruir a posicao na data lendo as transacoes ate o exDate
— a mesma logica cronologica de PositionCalculator.
(Ja feito: DividendRecalculator.sharesOn usa PositionCalculator.sharesOn.)

**3.2** FEITO em 24/09/2026 — lastDatePrior E a data-com (conferido na resposta
real; a data ex e o pregao seguinte). A fonte deixou de ser a BrAPI
(dividendos la sao pagos): e o site da B3, que usa o mesmo campo.

**3.3** Provento nao tem dono proprio. A tabela dividends nao tem user_id: o
dono e alcancado pela posicao (portfolio_asset_id), e essa coluna e
OPCIONAL — existe para registrar provento de ativo que saiu da carteira,
e a FK e ON DELETE SET NULL.
Resultado: provento com posicao nula nao pertence a ninguem.
A consulta nova do item 1.3 os exclui, que e o comportamento seguro —
mostra-los seria mostra-los a todos. Mas eles ficam invisiveis para o
proprio dono tambem.
Duas saidas: dar user_id a dividends, ou impedir que a posicao seja
desfeita sem o provento ir junto. A primeira parece melhor, porque o
provento e um fato do usuario, nao da posicao.
Impacto hoje: nenhum, a base nao tem provento orfao. Mas a primeira
exclusao de posicao cria um.

**3.4** FEITO em 25/09/2026 — Provento de acao sem data de pagamento (24/09/2026)
Resolvido pelo extrato de proventos da Area do Investidor
(POST /admin/proventos/extrato, V24): 22 datas completadas com origem
EXTRATO_B3 e as 2as parcelas de JCP da CMIG4 (data-com 25/03/2025 e
23/06/2025) com pagamento DEDUZIDO em 30/12/2026, ainda a receber. A
origem fica em asset_cash_events.payment_date_source e aparece no hover;
fonte oficial que trouxer a data depois a substitui. Historico abaixo.
24 dos 203 proventos da carteira estao com payment_date nulo. Todos sao
de acao (BBAS3, CMIG4, SAPR4, CXSE3, BRBI11), com data-com entre
dez/2024 e ago/2025. FII nao tem o problema: o FNET traz o pagamento.
- Causa: esses eventos vieram so de B3_HISTORICO
  (GetListedCashDividends), que nao tem campo de pagamento. A consulta
  que traz o pagamento (GetListedSupplementCompany) cobre so ~12 meses;
  o saveCashEvent completa a data quando outra fonte a traz, mas evento
  com mais de um ano nunca volta nela. Ex.: asset_cash_events 266,
  BBAS3 JCP data-com 11/12/2024 -> dividends 61.
- Impacto: so na tela (coluna PAGAMENTO vazia). O unico calculo que usa
  a data e a reducao de custo de AMORTIZACAO/RESTITUICAO_CAPITAL, e
  PositionCalculator ja cai para a data-com quando ela falta; nenhum dos
  24 e desses tipos. Parcelas iguais sem pagamento (CMIG4) parecem
  duplicadas, mas sao parcelas (seq 1 e 2, V22).
- Paliativo (escopo de filtros de dividendos): mostrar "—" com a
  explicacao no hover.
- Solucao de verdade exige outra fonte: dividendos da BRAPI so em plano
  pago (docs/planos/PLANO_BRAPI.md, D4) e o lancamento manual foi retirado
  (proventos sao somente leitura). Nao estimar a data: sem fonte, fica
  nula.

**3.5** FEITO em 25/09/2026 (para o que esta no extrato) — IR retido:
rendimentos que a integracao nao traz (24/09/2026)
Os 3 rendimentos de PETR4 (data-com 22/12/2025, bruto 0,45, 0,45 e
0,26) entraram pelo extrato como eventos EXTRATO_B3, junto com 18
rendimentos de FII sem aviso no FNET. Rendimento futuro desse tipo so
entra com um extrato novo: a integracao continua sem essa fonte.
Historico abaixo.
O extrato da B3 mostra "Rendimento" (atualizacao pela Selic) de PETR4
em 20/02/2026 (R$ 0,35) e 20/03/2026 (R$ 0,35 e R$ 0,21) que nao
existem em asset_cash_events: a B3 nao os publica nos dados da
empresa. So existem no extrato do investidor.
Caminho: importar o Excel de Movimentacao da Area do Investidor
(docs/planos/PLANO_IR_PROVENTOS.md, secao 9) — escopo futuro.

**3.6** IR retido: prazo real do "rendimento" de acoes (24/09/2026)
A atualizacao pela Selic segue a tabela regressiva de renda fixa, que
conta da data de inicio da correcao — dado que nao vem na fonte. A V23
usa 22,5% fixo (ate 180 dias). Erra se um dia o pagamento passar de
180 dias do inicio da correcao.

**3.7** IR retido: 10% sobre dividendo > R$ 50 mil/mes (Lei 15.270/2025)
Retencao sobre o total do mes quando a mesma empresa paga mais de
R$ 50 mil a mesma PF; o aprovado ate 31/12/2025 segue isento. Exige
somar por empresa pagadora no mes. Hoje dividendo e isento sempre.

**3.8** IR retido: fora das regras da V23 (24/09/2026)
- Perfil do investidor: tudo assume PF residente (PJ, isento e nao
  residente tem outras aliquotas).
- ETF, BDR, Stock, REIT: sem regra (liquido nulo; somas usam o bruto)
  ate existir provento desses tipos.
- IRPFM (imposto minimo acima de R$ 600 mil/ano): e da declaracao
  anual, nao da fonte.

## 4. RENDA FIXA — o que ficou pela metade na reestruturacao

**4.1** FEITO em 05/10/2026: o icone Comprar/Vender da Carteira ja lancava no titulo da linha; a Nova
Transacao ganhou o seletor do titulo na venda de Renda Fixa. Texto original:
Varios lancamentos no mesmo titulo. A tabela ja aguenta: transactions
aponta para a nota, e portfolio_assets tem unicidade por
(carteira, ativo, titulo). Falta o seletor no frontend — hoje o modal so
cria titulo novo, nunca reaproveita um existente.

**4.2** FEITO em 05/10/2026 (com o 4.1): venda de renda fixa pela Nova Transacao com o titulo escolhido,
e "Resgatar tudo" com o saldo na curva. Texto original:
CONSEQUENCIA DIRETA DE 4.1: vender renda fixa esta BLOQUEADO.
TransactionService.resolveNote recusa venda sem noteId, porque criar
titulo novo numa venda geraria posicao negativa de um papel nunca
comprado. Sem o seletor do 4.1, nao ha como informar o noteId.
Foi escolha consciente: erro claro em vez de dado errado. Mas some assim
que 4.1 existir.

**4.3** RESOLVIDO em 16/10/2026 (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md): emissora e distribuidora sairam do
texto livre para o cadastro fixed_income_issuers (BCB + CVM, por raiz de CNPJ), com grupos (conglomerado prudencial).
Texto original:
Emissor e texto livre. Virar tabela de emissores quando houver uso o
bastante para saber quais nomes voce realmente digita — antes disso a
lista seria chute.

**4.4** Quantidade x preco unitario nao descreve bem renda fixa. Aplicar
R$ 10.000 num CDB nao tem "quantidade" nem "preco": tem valor. Hoje se
resolve com quantidade 1 e preco 10.000, o que funciona mas mente sobre o
que o campo significa. Decisao em aberto desde a conversa sobre o modelo.

## 5. INDICES — serie historica

**5.1** FEITO em 05/10/2026 (docs/planos/PLANO_INDICES_MERCADO.md): V43
(market_indices + market_index_values), carga 2010+ pelo bootstrap ou
POST /api/v1/admin/indices, rotina das 10:00 com recuperacao na subida e leitura
em GET /api/v1/indices. Entraram tambem meta Selic, dolar PTAX, Ibovespa e IFIX
(D1). O que ficou esta na secao 23. Texto original:
Cadastrar CDI, SELIC, IPCA, IGP-M, INPC e TR. Voce disse que faria em
branch separada.
Fonte: API SGS do Banco Central. Codigos das series:
  CDI .......... 12      SELIC ........ 11      SELIC meta ... 432
  IPCA ......... 433     INPC ......... 188     IGP-M ........ 189
  TR ........... 226
Periodicidade: CDI e SELIC diarios; IPCA, INPC e IGP-M mensais.
ATENCAO: api.bcb.gov.br esta bloqueado pela politica de egress da sua
organizacao — testei e volta 000. A coleta tera de rodar da sua maquina
ou de um ambiente com saida liberada.
(05/10/2026: confirmado — o bloqueio e so do ambiente do assistente; na sua maquina
e nos conteineres o SGS e o site da B3 respondem.)

**5.2** Sem 5.1, o item 2.1 so resolve prefixado. (05/10/2026: com 5.1 feito, o 2.1
resolve tambem pos-fixado e hibrido.)

## 6. CATALOGO DE ATIVOS

**6.1** FEITO em 24/09/2026 — V16 (12 categorias) e V17 (219 ETFs, com a cotacao
de 23/09/2026). BRAX ficou de fora de proposito (cadastro sujo na BRAPI).
Junto entraram os BDRs (V18, 892) e as criptomoedas (V19, 227).

**6.2** FEITO em 24/09/2026 — V13 troca os nomes pelos da BRAPI (98 linhas; o
resto ja batia ou a BRAPI devolve o proprio ticker). AALR3 e
"ALLIANÇA SAÚDE E PARTICIPAÇÕES S.A.".

**6.3** O catalogo e global — assets nao tem user_id, de proposito: um ativo e o
mesmo para todo mundo. Continua valendo. Mas quando entrar cadastro de
ativo pelo usuario, essa decisao precisa ser revisitada, porque foi
exatamente por essa porta que "CDB BTG 2027" virava ativo global.

**6.4** FEITO em 24/09/2026 — quem desliga e migration, pelo criterio da secao
2.1 do PLANO_BRAPI (fora da lista oficial E cotacao 404): V14 desligou 9.
A conferencia semanal do catalogo (segunda 07:00) aponta no log os novos
candidatos; a decisao continua manual. Inativo sai da busca.

## 7. CARTEIRA — refatoracao adiada

**7.1** Dois caminhos ate o ativo. portfolio_assets tem asset_id E note_id, e a
nota tambem tem asset_id. Nada impede que divirjam. Aceito na hora da
mudanca, para nao misturar duas refatoracoes; voce disse que o Portfolio
seria revisto.

**7.2** A regra "renda fixa exige titulo" vive na aplicacao, nao no banco. Um
CHECK precisaria enxergar assets.asset_type_id a partir de transactions,
o que so trigger faz. Enquanto nao houver teste (item 8), nada guarda
essa regra alem do codigo.

**7.3** FEITO em 17/09/2026 (V28) — cotas, preco medio e custo da posicao e os
totais da carteira deixaram de ser gravados; saem das transacoes via
PositionCalculator. Sairam recalculatePortfolioAsset,
recalculateAllPortfolioAssets, POST /transactions/recalculate-all e
PortfolioService.recalculatePortfolio. Tabela associativa entre posicao
e transacao foi descartada: transactions.portfolio_asset_id ja e a
ligacao, e uma segunda seria outra fonte para o mesmo fato.

**7.4** Corretagem fora do custo. transactions.brokerage_fee e gravado, mas
PositionCalculator nao o soma ao custo nem ao preco medio. Pela regra da
Receita, corretagem e taxas compoem o preco medio de compra.

**7.5** Venda maior que a posicao e aceita: o calculo zera cotas e custo sem
aviso. Vale recusar na criacao/edicao da transacao, conferindo as cotas
na data da venda com PositionCalculator.sharesOn.

**7.6** dividends.total_amount ainda e gravado e depende das cotas na data-com
(por isso DividendRecalculator.recalculate continua rodando a cada
mudanca de transacao). Poderia virar calculo: amount_per_share x
sharesOn(ex_date).

## 8. TESTES

**8.1** Nao existe teste nenhum: src/test esta vazio, e o build roda com
-Dmaven.test.skip=true. Voce adiou explicitamente a fundacao.
Quando voltar: Testcontainers com PostgreSQL, porque metade do que
importa aqui e comportamento de banco — as constraints de coerencia de
taxa, o NULLS NOT DISTINCT da posicao, o recalculo cronologico.
Um teste que roda em H2 nao cobre nada disso.

**8.2** Primeiro alvo natural: os itens 1 (autorizacao) e 3.1 (posicao na
data-com). Sao bugs que um teste pega e que voltam sozinhos sem teste.

## 9. DOCUMENTACAO E LIMPEZA

**9.1** Claude.md — ATUALIZADO em 18/09/2026, em duas rodadas.
Primeira: a contagem real de migrations, a linha da V29 na tabela, as
mascaras novas do modal de transacao, a troca da imagem do front para
node:24 e a nota do WinNAT na porta 8080.
Segunda: a V30 (auditoria de datas) — o banco fica hoje na versao 30,
com 30 arquivos em db/migration; o "22" continua sendo o numero do
reset, nao o total. Entraram tambem a secao de lint, o eslint.config.js
e a pasta .claude/ na estrutura, e a secao nova "Como o Claude executa
comandos aqui", que documenta a ponte via Claude Code como servidor MCP.
README.md tambem foi corrigido na mesma data: prometia JUnit e Mockito
(nao existem), citava @react-oauth/google (o login e por Google Identity
Services), pedia GOOGLE_CLIENT_SECRET (o fluxo de ID token dispensa),
mandava criar o banco com o nome errado e nao mencionava o Docker, que e
o caminho recomendado.
Os quatro pontos originais deste item (secao "Coluna custom em assets",
asset_terms e fixed_income_notes fora da lista de Tabelas, secao Flyway
velha, ausencia da separacao catalogo / titulo) ja estavam resolvidos em
revisoes anteriores — reconferir se voltar a divergir.

**9.2** FEITO em 18/09/2026 — _to_delete\ apagada por inteiro.
Na hora da exclusao a pasta ja estava praticamente vazia: as 33
migrations antigas, a V31 descartada e os tarballs temporarios tinham
sumido antes, e restavam dois arquivos de teste somando 34 bytes.

**9.3** Copia velha do projeto em
C:\Users\guilh\.gemini\antigravity\scratch\primevest
Esta defasada e aparece nas buscas. Apagar ou desconectar da sessao.

**9.4** FEITO em 05/10/2026 — Client ID criado pelo Guilherme (projeto primevest-510701,
apendice A de docs/planos/PLANO_CADASTRO_USUARIO.md) e login Google conferido.

**9.5** COMITADO em 18/09/2026. O acumulo das ultimas sessoes saiu do working
tree, na branch TRACK_01-ajustes_crud_inicial:
  b1fbfa9  Filtro tela de Categoria e Transacao
  672ad13  Configuracoes de uso voltados para o Claude
  7d8a150  Ajustado balanceamento estrategico
Entraram ai o segundo reset das migrations (30 arquivos -> 9), o lint do
front (eslint.config.js, typescript-eslint e as quatro correcoes), a
pasta .claude/ e a revisao de Claude.md, README.md e deste arquivo.
A regra continua valendo: nao comitar sem instrucao explicita.

**9.6** primevest-web/Dockerfile usa node:24 (Debian) em vez de node:24-alpine.
Nao foi escolha de projeto: as camadas da imagem -alpine estao
corrompidas no store local do Docker Desktop (arquivos com tamanho certo
e conteudo todo zero — "exec format error" no entrypoint, segfault no
binario do node). Remover e baixar de novo nao resolve, porque o digest
confere e o Docker reaproveita as camadas ruins; so uma purga do content
store (Troubleshoot > Clean / Purge data) devolveria a -alpine, e ela
apaga TODOS os volumes junto, inclusive o pgdata.
Custo de ficar no Debian: ~700 MB a mais na imagem de dev. Detalhes e o
comando de backup do pgdata estao no Claude.md, em Pontos de Atencao.
Voltar para -alpine e opcional e so vale se o tamanho incomodar.

**9.7** FEITO em 18/09/2026 — SEGUNDO RESET DAS MIGRATIONS.
As 30 migrations viraram 9: V1 com o schema completo e V2..V9 com os
seeds. 156 KB contra 340 KB. As antigas ficaram guardadas em
E:\repos\primevest-backups\migration-antiga-20260918-182315.

Motivo: o conjunto de 30 voltara a descrever o CAMINHO e nao o resultado
— criava colunas derrubadas depois (current_value, total_shares,
total_invested, dividends.asset_id), tornava campos anulaveis meses
adiante, e classificava os ativos em duas etapas (o seed inseria a
categoria antiga e a V23 reclassificava tudo).

VERIFICACAO, que e o que importa se houver um terceiro reset:
montou-se um db_ref aplicando as 30 antigas em ordem e um db_new com as
9 novas, e os dois foram comparados por consulta normalizada e ordenada
(nao por pg_dump bruto): 108 colunas, 53 constraints, 48 indices e 1.524
linhas de seed, identicos nos quatro eixos. Depois docker compose down -v
e subida limpa: Flyway na versao 9 e a API de pe com ddl-auto: validate,
que prova que as entidades JPA batem com o schema.

A comparacao pegou duas divergencias que a leitura dos arquivos nao
pegaria: a unicidade de asset_categories inclui segment (a V23 trocou a
constraint), e uma categoria e o par (name, segment) — CONSUMO_CICLICO
sozinho aparece em 21 segmentos, entao resolver a categoria so pelo nome
apontaria 782 acoes para a linha errada.

EFEITO COLATERAL ACEITO: a ordem relativa das colunas mudou nas tabelas
que tinham sofrido DROP COLUMN. Num CREATE TABLE novo as colunas de
auditoria ficam no fim, em vez de onde o ALTER as deixou. Nao afeta JPA
nem consulta nomeada; afeta SELECT * e a saida do pg_dump.

CORRECAO DE CONTAGEM: sao 116 categorias, nao 115. O numero errado
circulava no Claude.md e aqui.

**9.8** FEITO — a pasta "Claude outputs" dentro de src/main/java/com/primevest
nao existe mais. Continha um unico claude_desktop_config.json, copia da
configuracao do app desktop, sobra de sessao anterior.

## 10. VENDA DE ATIVOS (escopo de 17/09/2026)

**10.1** DIVIDA ACEITA: a reparticao da venda de Tesouro Direto fica GRAVADA.

No Tesouro o saldo e do ativo e a posicao e do titulo, entao uma venda
de 5 cotas pode consumir dois titulos. Cada parte virou uma transacao
presa ao titulo que ela liquida, com sale_group_id em comum.

Isso e dado derivado que fica gravado — exatamente o que a V28 tirou do
sistema quando cotas, preco medio e custo saiam de portfolio_assets. A
reparticao so continua certa enquanto TODO caminho que mexe em transacao
de Tesouro chamar o recalculo, porque uma compra lancada com data
retroativa muda de qual titulo as cotas ja vendidas saem (regra 4.2).

Hoje esse caminho e unico: TransactionService.reallocate, chamado por
criar, editar e excluir, tanto compra quanto venda. Qualquer codigo novo
que grave transacao de Tesouro por fora dele deixa a carteira errada sem
avisar.

A alternativa avaliada e descartada foi manter a venda no nivel do ativo
(uma posicao com note_id nulo) e derivar a reparticao na leitura. Ela
nao tem esta divida, mas tira a venda de cima de um titulo real e obriga
a agregar posicoes do mesmo ativo em toda leitura. A escolha foi pela
que espelha a realidade da operacao; a outra continua valendo se o
recalculo se mostrar fragil.

Primeiro teste a escrever quando o item 8 sair do papel: Exemplo 6 do
escopo — compra retroativa depois de uma venda ja repartida.

**10.2** FEITO em 05/10/2026: a curva refaz o saldo do zero a cada passagem; excluida a venda, a valorizacao
volta para a curva (RendaFixaCurvaIT). Sem curva (TR), continua como estava. Texto original:
A valorizacao de renda fixa que sobra quando uma venda e EXCLUIDA.

Vender por mais do que a posicao valia corrige a valorizacao para cima
(regras 3.1.1 e 3.1.2). Excluir essa venda depois nao devolve o valor
antigo: a posicao reabre (regra J), mas com uma valorizacao que ninguem
ganhou. Foi decisao explicita nao guardar o delta para desfazer.

Quem corrige e a rotina assincrona de rentabilidade de renda fixa, que
ainda nao existe (item 2.1). Enquanto ela nao existir, esse numero fica
de pe ate o proximo resgate.

**10.3** FEITO em 05/10/2026: a curva entra por FixedIncomeSettlement.applyCurve, depois das regras E/F/G da
escrita. Texto original:
A rotina de rentabilidade de renda fixa, quando vier, passa pelas mesmas
regras E, F e G — FixedIncomeSettlement e o unico lugar que grava
appreciation de renda fixa hoje, e o certo e ela entrar por la em vez de
escrever direto na coluna.

**10.4** FEITO em 13/10/2026 (docs/planos/PLANO_NOTIFICACOES.md, secao 6 — revoga a D8 de 05/10/2026, em que o
titulo so parava de render e ganhava o selo "Vencido"): o sistema LIQUIDA o titulo vencido na abertura do dia, com uma
venda de origem SISTEMA (selo "Automatica") na data do vencimento — RF privada pelo saldo da curva, Tesouro pelo PU
oficial do vencimento; a compra retroativa de titulo ja vencido nasce liquidada; sem valor, pendencia "informe o valor
do resgate". Texto original:
Ativos com vencimento ainda nao sao liquidados sozinhos. is_settled so
muda por venda (renda fixa), por zerar cotas (renda variavel e Tesouro)
ou pela regra J. O vencimento continua fora.

## 11. HISTORICO DE PRECO — asset_price_history

**11.1** A tabela so escreve, nunca le. ATUALIZADO em 24/09/2026: quem grava
agora e a rotina de fechamento (ClosingPriceService, 19:00), so para os
ativos EM CARTEIRA — nao mais o catalogo inteiro. Carga inicial de 3
meses por ativo novo em carteira. A leitura continua sem uso (11.2, 11.6).

**11.2** A leitura e um stub. AssetPriceHistoryService.getHistory() retorna
List.of() — corpo vazio. E GET /api/v1/assets/{ticker}/history no
AssetController e um "return ResponseEntity.ok().build()", com o
comentario "Handled by pricehistory module, would call
AssetPriceHistoryService here or redirect". Nunca foi ligado.
O frontend tambem nao chama: nao ha nenhuma referencia a history em
primevest-web/src.

**11.3** FEITO em 24/09/2026 — o fechamento usa o historico diario da BRAPI
(range=5d) e grava abertura, maxima, minima, fechamento e volume.

**11.4** FEITO em 24/09/2026 — cripto uma vez ao dia, 19:00, todos os dias,
gravando o fechamento do dia (a rodada de 30 em 30 minutos saiu).

**11.5** Dos dois metodos do AssetPriceHistoryRepository, so
findByAssetIdAndReferenceDate e usado — pelo proprio scheduler, para nao
duplicar a linha do dia. O findByAssetIdAndReferenceDateBetween..., que
seria o do grafico, esta orfao.

**11.6** Decidir: ligar a leitura (endpoint + grafico na tela de ativo) ou parar
de gravar. Hoje e custo sem retorno. Nao confundir com o item 5, que e
serie historica de INDICES (CDI, SELIC, IPCA) e serve para valorizar
renda fixa — sao coisas diferentes.
ATUALIZADO em 27/09/2026 (F2 do plano): a tabela tem a serie diaria
da B3 desde 2010 (2.228.704 pregoes de acoes e FIIs, coluna source,
V29); a BRAPI so grava o dia e nao sobrescreve pregao da B3.
ENCAMINHADO em 27/09/2026: decidido LIGAR a leitura. O
docs/planos/PLANO_EMPRESAS_BALANCOS.md grava o historico diario desde 2010 (COTAHIST
da B3, F2) e o IndicatorService le o fechamento de cada data de balanco
(F9). 11.1, 11.2 e 11.5 se resolvem na execucao do plano; o grafico de
cotacao na tela do ativo fica para o planejamento de telas.
RESOLVIDO em 28/09/2026 (docs/planos/PLANO_TELA_EMISSORES.md, B5): leitura ligada em
GET /api/v1/assets/{ticker}/prices (AssetPriceHistoryService, JdbcTemplate;
o esboco /history e o PriceHistoryDTO antigo foram substituidos), com o
grafico de cotacao da tela de Emissoras. Os metodos orfaos do
AssetPriceHistoryRepository (11.5) seguem sem uso — o servico le por SQL.

## 12. LINT DO FRONT

**12.1** RESOLVIDO em 18/09/2026 — npm run lint passou a funcionar.
Falhava desde sempre com "couldn't find an eslint.config.(js|mjs|cjs)".
Faltavam AS DUAS pecas: o ESLint 9 abandonou o .eslintrc e o projeto nao
tinha eslint.config.js; e typescript-eslint nao estava no package.json,
sem o qual o ESLint nem faz o parse de um .tsx. Entraram os dois.

**12.2** Os quatro erros do primeiro lint foram corrigidos na mesma data.
Tres cosmeticos: prefer-const em DataTable.tsx (linhas 40 e 41) e
interface vazia em LoadingSpinner.tsx.
O quarto era real: MaskedNumberInput.tsx fazia
  const inputId = id || React.useId();
O curto-circuito so chamava o useId quando a prop id vinha vazia, entao
a quantidade de hooks do componente mudava conforme a prop — quebra da
regra de ordem dos hooks. Passou a chamar o useId incondicionalmente e
escolher depois (id ?? generatedId).
Nunca chegou a se manifestar porque id e estavel por instancia, mas o
componente sustenta CurrencyInput, PercentInput e a Quantidade do modal
de transacao — as mascaras mexidas no mesmo dia.

**12.3** RESOLVIDO em 23/09/2026 — npm run lint sem nenhum aviso (eram 35).
- src/api: respostas tipadas por generico do axios (get&lt;T>, post&lt;T>,
  put&lt;T>) em assets, transactions, goals, dividends e ceilingPrices;
  auth ganhou LoginRequest e RegisterRequest em types/index.ts,
  espelhando LoginRequestDTO e RegisterRequestDTO.
- Paginas: sairam os 'as any' de queryFn/mutationFn/mutate, que so
  existiam porque a API devolvia any.
- catch (LoginPage, useGoogleSignIn): err: unknown, lendo so
  response.data.message (nao o apiMessage, que tambem exibiria o
  'error' do Spring, ex.: "Unauthorized").
- Tooltips/legendas do Recharts (StrategyPage, AllocationPieChart,
  AllocationDetailedCharts): interfaces proprias da fatia.
- useSortable: acesso dinamico via Record&lt;string, unknown>.
- DataTable.tsx removido: nenhuma tela o usava.
- react-refresh/only-export-components: cada contexto virou
  XContext.ts (contexto, tipos e hook) + XProvider.tsx (componente).
  Os imports de useAuth/useTheme/useToast nao mudaram; so o main.tsx
  passou a importar os providers de ./context/XProvider.
Se o Vite do container reclamar de context/XContext.tsx inexistente,
e cache da resolucao antiga: docker restart primevest-web.

**12.4** Nao ha lint no backend. Nenhum Checkstyle, SpotBugs ou formatador no
pom.xml. Decidir se vale, e se vale, antes ou depois do item 8.

## 13. CENTRAL DE AVISOS (adiada do docs/planos/PLANO_BRAPI.md em 24/09/2026) — FEITO em 13/10/2026

Implementada como a caixa de **Notificacoes** (docs/planos/PLANO_NOTIFICACOES.md): tabela `notifications` (V48), estado
por pessoa em `notification_states`, sino no rodape do menu, tela `/notifications`. A 13.2 entrou com ela
(`stock_event_costs`, V51, "Informar custo" e o icone na Carteira). O que ficou de fora esta na secao 26. Texto
original:

Hoje o unico aviso do sistema e o toast (canto superior direito, some em 4-8s).
Serve para responder a um clique, nao para algo que uma rotina da madrugada
descobriu. O docs/planos/PLANO_BRAPI.md (secao F8) desenhou uma central de avisos; ficou
para depois. Enquanto ela nao existir, as rotinas so escrevem no log.

**13.1** Central de avisos
- Tabela notices: user_id (nulo = aviso do sistema), kind, severity
  (INFO/ATENCAO), title, message, ref_type + ref_id (ativo, posicao ou
  evento), status (ABERTO/RESOLVIDO/DISPENSADO), created_at,
  resolved_at. Um aviso aberto por kind + referencia: a rotina nao
  repete.
- API: GET /api/v1/notices?status=ABERTO e PATCH /api/v1/notices/{id}.
- Tela: sino no Header com o numero de abertos; painel lateral com a
  lista; cada aviso com a acao que o resolve ("Ver ativo",
  "Dispensar", "Informar custo").
- Quem vai usar: conferencia semanal do catalogo (ticker novo, sumido,
  renomeado — PLANO_BRAPI B23), bonificacao sem custo (13.2), rotina
  de proventos (rotulo desconhecido da B3, fonte fora do ar).

**13.2** Bonificacao sem custo informado pela B3
DECIDIDO (24/09/2026): as acoes novas entram na hora com custo ZERO
PROVISORIO, para a quantidade ja ficar certa. Isso vale desde a F6 do
PLANO_BRAPI, com ou sem a central.
O que depende da central (13.1):
- aviso "PETR4 - bonificacao de 10% em 12/03/2027 sem custo atribuido.
  Informe o valor por acao (esta no fato relevante da empresa)";
- icone de atencao ao lado do preco medio da posicao na Carteira, com o
  mesmo texto no balao (i);
- "Informar custo": um campo; o valor vai para stock_event_costs
  (posicao, evento, custo por acao) — dado digitado pelo usuario nao
  vai para o catalogo global —, o preco medio e recalculado e o aviso
  fecha sozinho.
Conferido em 24/09/2026 (A12): o campo remarks da B3 veio vazio em
todas as bonificacoes reais vistas (MGLU 2025, ITSA 2025, BBAS 1996) —
o custo atribuido nao vem da B3; asset_stock_events.assigned_cost fica
nulo ate a central existir.
FEITO em 24/09/2026: custo zero provisorio no calculo
(CorporateActionApplier) e WARN no log quando a rotina grava uma
bonificacao que pega alguma posicao.

## 14. ROTINAS EXTERNAS — achados da carga do extrato B3 (24/09/2026)

Os dois apareceram na carga de 88 transacoes pela API (feita por scripts
avulsos, fora do repositorio). Nenhum foi corrigido.

**14.1** Rotinas travando: proventos sob demanda prendem as threads da API
Cada transacao dispara uma busca de proventos em paralelo, e essas
buscas esperam na fila sem liberar as threads da API. Com muitos
lancamentos seguidos, como nesta carga, outras rotinas param junto,
inclusive o fechamento.
- Onde: ProventosOnDemandListener abre um Thread.ofVirtual() por
  PositionChangedEvent, e ProventosSyncService.syncAssetOnDemand entra
  num synchronized (this). No Java 21 a thread virtual que espera um
  monitor (ou que bloqueia dentro dele, como o Mono.block do
  FnetClient) PRENDE a thread portadora. Com ~70 threads na fila,
  todas as portadoras ficaram presas.
- Visto: POST /api/v1/admin/closing respondeu 202, mas a thread
  "admin-fechamento" nunca chegou a rodar (dump de threads: sem pilha).
  Precisou de docker compose restart api; o fechamento saiu pela
  recuperacao na subida e os proventos por /admin/proventos
  (23 emissores, 0 falhas, ~50 min por causa dos timeouts do FNET).
- Correcao possivel: fila unica com um executor de uma thread e
  deduplicacao por emissor (varias transacoes do mesmo ativo viram uma
  busca), em vez de uma thread por evento; ou trocar o synchronized por
  ReentrantLock, que nao prende a portadora (resolve o travamento, mas
  nao o acumulo). O JDK 24 (JEP 491) elimina o pinning do synchronized,
  mas nao o acumulo de threads esperando.
- ENCAMINHADO em 27/09/2026 para o docs/planos/PLANO_EMPRESAS_BALANCOS.md, F8:
  fila unica com um executor, deduplicada por fundo, sem synchronized;
  FNET com timeout de 5 s e ate 6 tentativas; avisos gravados em
  carga/data/fnet e nunca baixados de novo. Teste de 27/09 com HGLG11: 100 de
  100 avisos obtidos, inclusive os 9 meses (12/2024 a 08/2025) que
  tinham sido completados pelo extrato — o FNET tinha o dado, o cliente
  e que falhava.
- RESOLVIDO em 28/09/2026 (F8): sem synchronized; pedido sob demanda
  numa fila unica (um executor, sem repetir ativo) no lugar de uma
  thread virtual por evento; ReentrantLock por emissor; FnetClient com
  cache em carga/data/fnet (indice local por CNPJ; a carga nao chama o
  FNET), download com timeout de 5 s, 6 tentativas, 3 simultaneos. Ver docs/planos/PLANO_EMPRESAS_BALANCOS.md, 7.1 (F8).

**14.2** Fechamento no meio do pregao grava o dia ainda aberto
Rodado durante o horario de negociacao, ele grava o pregao de hoje
ainda aberto e usa essa cotacao como preco atual.
- Onde: ClosingPriceService.runB3Closing -> saveCandles grava todos os
  candles que a BRAPI devolve, inclusive o de hoje, e poe o ultimo em
  assets.current_price. Nada filtra o dia corrente.
- Visto: recuperacao na subida as 11:18 de 24/09 gravou o pregao de
  24/09 parcial nos 23 ativos da carteira (ex.: PETR4 50,04 contra
  49,60 no fechamento de 23/09). A rodada das 19:00 regrava o dia com o
  valor final, entao o erro dura ate la.
- Afeta as tres entradas: recuperacao na subida, POST /admin/closing e
  qualquer rodada fora do horario.
- Correcao possivel: antes de CLOSING_TIME, descartar o candle de hoje
  (mesma regra de lastExpectedClosing), tanto no historico quanto no
  current_price.

## 15. CRIPTO — OPERACOES SEM VALOR EM REAIS (docs/planos/PLANO_CRIPTO_BINANCE.md, D5, 25/09/2026)

A primeira versao da importacao de cripto so grava operacoes que trazem o valor
em reais: compra ou venda contra BRL (par BTCBRL, Convert BRL -> BTC, "Comprar
Cripto" com real). O PrimeVest guarda preco em reais, e essas operacoes ja
trazem o numero pronto.

Ficam de fora, listadas como "nao importadas" na pre-visualizacao e no
historico da sincronizacao (import_run_items), sem serem descartadas:

**15.1** Cripto contra cripto (pares como ETHBTC, Convert BTC -> ETH).
 Precisa virar duas transacoes — venda de um, compra do outro — com o
 valor em reais buscado no momento da troca (candle de 1 minuto do par
 em BRL, /api/v3/klines).

**15.2** Pares contra stablecoin (BTCUSDT, BTCUSDC). USDT e USDC sao cripto no
 catalogo (Stablecoins, V19), entao e o mesmo caso do 15.1: venda da
 stablecoin + compra do ativo, valoradas em reais.

**15.3** Taxa cobrada em outro ativo (ex.: desconto pagando a taxa em BNB).
 A quantidade liquida do ativo comprado nao muda, mas a taxa em reais
 precisa do preco do BNB no momento. Regra R8 do plano.

**15.4** Saldo sem movimento de origem (na conta de teste: resíduo de USDC).
 Nao ha operacao para importar; a posicao no PrimeVest fica diferente do
 saldo da corretora e o aviso de divergencia (D9) aponta isso.

Quando tratar: depois da primeira versao estavel, ou quando aparecer um usuario
(ou conta) que opere fora de BRL. O ponto comum dos tres primeiros itens e um
servico de "valor em reais no instante T" — resolver uma vez, servir a todos.

## 16. INTEGRACAO COM A B3 (docs/planos/PLANO_INTEGRACAO_B3.md; antes "importacao de extrato da B3", 25/09/2026)

**Situacao em 03/10/2026:** F1 a F8 do PLANO_INTEGRACAO_B3 executadas. A integracao
e pela API da Area do Investidor (nao por arquivo): modulo primevest-integration,
conector da B3 com as regras B1-B10, testado contra o simulador com os extratos
reais (pilha primevest-teste). O que falta para a B3 de verdade e o que ficou de fora:

**16.4** Contrato com a B3 (Pacote de Acesso: client_id, segredo, certificado .p12,
 link de opt-in) e conferir os contratos inferidos — Negociacao v2, Eventos
 Provisionados v2 e Autorizacao (plano, 6.3): muda configuracao (b3i.paths.*)
 e o mapeamento de campos, nao a arquitetura. Com o certificado, montar o .p12
 no conteiner da app-api (volume) e apontar B3I_CERT_P12_PATH.

**16.5** Venda de Tesouro Direto pela B3: nao importada. O PrimeVest reparte a
 venda entre os titulos (FIFO) e refaz essas linhas a cada realocacao, sem
 lugar para a origem da fonte. Hoje aparece em "nao importadas" com o motivo e
 e lancada pela tela. Resolver = o alocador do Tesouro carregar a origem.

**16.6** Renda fixa privada (CDB...) pela B3: nao importada. A Movimentacao nao
 traz taxa, indice nem o valor de resgate (o vencimento vem com valor zero).
 Depende da Posicao de renda fixa (plano, F7).

**16.7** Reconectar depois de desconectar (ou de revogar na B3) reimporta tudo
 como novo: o external_id e unico por conexao, e a deteccao de duplicata so
 olha lancamento MANUAL. Vale tambem para a Binance. Resolver = a conexao nova
 enxergar as importadas da conexao encerrada da mesma fonte e do mesmo usuario.

**16.8** Posicao da B3 nao exportada: a divergencia (6.7, conferencia C6) fica
 desligada no simulador. Exportar Extratos > Posicao para carga/mocks/ liga.

**16.9** RESOLVIDO em 05/10/2026 (docs/planos/PLANO_CADASTRO_USUARIO.md, D20): a conexao com a
 B3 usa o CPF do perfil do usuario, que e unico entre usuarios — ninguem conecta o CPF
 de outra pessoa sem que ela ja o tenha no cadastro. O CPF do perfil nao muda com
 conexao viva. (Confirmar com a B3 se o opt-in aceita um identificador de retorno
 deixou de ser necessario para este risco.)

Historico (25/09/2026):

Era a F7 do plano de cripto: um conector de arquivo, com o extrato da B3 como
primeiro layout, para provar que o pipeline de importacao e generico. Saiu de
la porque a B3 nao tem cripto e as regras da F2 (CryptoImportRules) sao de
cripto — exigem ativo do tipo CRIPTO, cotacao em reais e taxa no proprio ativo.

Vira escopo proprio, a planejar:

**16.1** FEITO em 03/10/2026 pela API, nao por arquivo: docs/planos/PLANO_INTEGRACAO_B3.md
 (a carga de 24/09/2026 foi feita por scripts avulsos, ja descartados).

**16.2** Regras proprias por tipo: acoes/FIIs/ETFs/BDRs (por cota), Tesouro
 (titulo por compra, venda FIFO), renda fixa (nota com emissor, taxa e
 vencimento). Reaproveitar a infraestrutura do plano de cripto:
 integration_runs, pre-visualizacao, origin/external_id em transactions.

**16.3** Mesmo caminho para outras instituicoes sem API (um layout de arquivo
 por instituicao).

## 17. INTEGRACAO CRIPTO — ENCERRADA ATE HAVER OUTRA CORRETORA (docs/planos/PLANO_CRIPTO_BINANCE.md, 26/09/2026)

  F1 a F6 concluidas. A integracao fica parada ate o usuario ter conta em
  outra corretora de cripto para testar.

**17.1** Conferir na pratica as partes da F6 que mudam dados. Sincronizar,
 decidir duplicatas, restaurar e excluir uma importada (o aviso) so
 foram testadas nos testes automatizados; na conta real nao rodaram.
 O mais seguro para comecar: clicar em Sincronizar na tela de
 Integracoes. Deve vir 0 novas, e a tela se atualiza sozinha ate
 terminar.

**17.2** F7 — segunda corretora cripto: a proxima etapa do plano. Valida se a
 interface generica (SourceConnector, ImportedMovement, ImportPipeline)
 aguenta uma fonte que nao foi desenhada junto.

## 18. EMISSORES, BALANCOS E INDICADORES (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 27/09/2026)

Planejamento concluido, nada implementado. Substitui o escopo "preco medio /
preco teto", que vira o proximo e consome este. Fases F1 a F11 no plano.

**18.1** Remocoes de assets que entraram no escopo (F10, migration M7):
 cnpj (vai para issuers.cnpj), cash_history_loaded_at, logo_checked_at
 e logo_path. Motivo comum: estado de processo gravado no catalogo. O
 estado passa a ser o arquivo (carga/data/fnet, carga/data/logos). O logo passa a
 ser servido por um indice em memoria de carga/data/logos.

**18.2** FEITO em 27/09/2026 (V31): asset_categories sem a coluna segment; as
 linhas duplicadas por (tipo, nome) consolidadas e os ativos
 reapontados (165 -> 80 categorias, contagem de ativos por categoria
 identica antes e depois). O segmento curado de FII foi para
 issuers.segment no seed V30.

**18.3** Principio de carga decidido: ou migration de seed (referencia e
 curadoria), ou arquivo em carga/data/ (volume de fonte oficial). Sem tabela
 auxiliar de seed, sem coluna de controle de processo.

**18.4** FEITO em 27/09/2026: 22.910 avisos do FNET em carga/data/fnet (90 MB,
 11/2016 a 09/2026, 324 dos 345 CNPJs de FII). /data/ inteira no
 .gitignore desde 27/09/2026.

**18.5** CNPJ dos FIIs: planilha fiis_com_isin.csv (27/09/2026) conferida e
 adotada para o seed (plano, 1.6); VGRH11 corrigido para
 67.624.043/0001-19. Curadoria conferida (planilha corrigida de
 27/09/2026) e gravada no seed V30: 369 companhias, 346 FIIs, 1.139
 ligacoes, 36 linhas de composicao de units.

**18.6** Relacao com a secao 16 (extrato da B3): os proventos de FII com mais
 de 12 meses passam a vir da integracao (FNET refeito). O extrato deixa
 de ser necessario para isso e fica so para o escopo de importacoes.

**18.7** Balancos (F5, 28/09/2026): quantidade de acoes sem escala conferida
 (share_counts.scale_status = 'REVISAR') em 119 de 19.349 documentos —
 erro na propria fonte (Grupo Mateus 06/2022 com um digito a mais; Azul
 03/2026 com 54 trilhoes) e grupamento no meio (Alphaville, CBA). O
 indicador nao usa essas linhas. Correcao manual prevista no modelo
 (scale_status MANUAL), sem tela nem endpoint ainda: decidir na F9 se
 vale, olhando quantos ativos do catalogo ficam sem indicador por isso.
 Antes de 2020 a quantidade vem do FRE sem tesouraria (1-3% a mais de
 acoes no LPA/VPA historico dessas datas).

**18.8** Serie trimestral (F6, 28/09/2026): erro de preenchimento na fonte
 passa para o trimestre derivado. Ex.: Cemig ITR 09/2021 com lucro da
 controladora 0 (o total foi para nao controladores) -> 3T21 zero e
 4T21 = ano inteiro. Em 565 de 4.561 exercicios a soma dos 4
 trimestres difere do anual do DFP (a maioria reapresentacao, 334
 dentro de 5%). O 12 meses no fim do exercicio usa o anual; nos
 outros trimestres, a soma. Sem correcao: decidir na F9 se o
 indicador trimestral precisa de conferencia (ex.: LPA informado).

**18.9** Separacao em modulos (docs/planos/PLANO_ADM_API.md, 28/09/2026): app-api (8080,
 carteira) e adm-api (8081, catalogo) conversam pelo Redis pub/sub
 ("catalogo mudou" e "preciso do historico"), sem garantia de entrega.
 a) A rede de seguranca e o recalculo de TODAS as posicoes as 21:00
    (PositionRefreshScheduler). Barato hoje (um usuario); com muitos
    usuarios, trocar por algo incremental (ex.: fila persistente ou
    created_at em asset_cash_events para recalcular so o que mudou).
 b) Proventos sob demanda (transacao -> adm-api busca o historico):
    REVISTO na F8 (28/09/2026) e mantido: com o catalogo sincronizado
    todo dia quase sempre termina sem chamada, mas cobre ETF/BDR sem
    emissor e ativo novo antes das 20:00. Agora entra numa fila unica.
 c) O publicador da app-api ("preciso do historico") nao tem teste com
    Redis de verdade: os testes rodam com as mensagens desligadas. O
    caminho foi conferido no ambiente publicando no canal pelo
    redis-cli; o disparo a partir de uma transacao real nao foi testado
    no ambiente (mexeria na carteira do Guilherme).
 d) Classe do core alterada recompila e reinicia os dois conteineres
    (~30-50 s); a app-api compila tambem a adm-api (dependencia so de
    teste, entra no -am).

**18.10** Proventos do catalogo (F8, 28/09/2026):
 a) FNET instavel: a busca por CNPJ leva 3 a 10 s e, no dia da carga,
    deu timeout e HTTP 520 em varios fundos. Decisao (28/09): a carga
    de FII usa so carga/data/fnet (indice local pelo CNPJFundo), sem chamar o
    FNET. 23 tickers (22 fundos) do catalogo nao tem aviso em disco:
    conferido pelo Guilherme em 28/09 — nenhum distribuiu ainda. Nao e
    pendencia: quando distribuirem, entram pela B3 na rotina diaria.
 b) Aviso com data-base depois do pagamento fica de fora (21 na
    primeira carga; ex.: 36458 VISC11 com 2108-11-30, 20789 VISC11
    28/12/2017 x pagamento 15/12/2017). Sem tela de revisao: so o log.
 c) Recarga de desenvolvimento: o hot reload (devtools) reinicia a
    adm-api mas nao para a thread da rodada em curso — ela segue ate o
    fim falhando cada emissor ("Could not open JPA EntityManager").
    Inofensivo (nao grava nada), mas polui o log. So em dev.
 d) [RESOLVIDO 29/09/2026 — docs/planos/PLANO_CARGA_INICIAL.md, D15/P4] FNET e a
    fonte principal de FII e a busca por CNPJ roda na rotina de proventos
    quando a B3 mostra provento mais novo que o ultimo aviso gravado (no
    maximo uma vez por dia por fundo). Texto original:
    Depois do historico, o FII fica so com a B3 (12 meses). Aviso novo
    do FNET (depois de 27/09/2026) precisa de busca explicita: endpoint
    POST /api/v1/admin/proventos/fnet?cnpj= ou rotina semanal. Nao
    feito — o FnetClient ja tem a busca (incomeNoticeIds) e o download
    com cache (incomeNotices); falta so o disparo. Conferido com o
    Guilherme em 28/09. A F10 tirou cash_history_loaded_at de assets: o
    historico do FII e lido do disco toda rodada (so o que falta), entao
    um aviso novo baixado para carga/data/fnet entra sozinho na rodada seguinte.

**18.11** Indicadores (F9, 28/09/2026):
 a) RESOLVIDO em 28/09 (decisao do Guilherme): 13 de 307 companhias
    ligadas ficavam sem LPA, VPA, P/L e P/VP no Atual (quantidade em
    REVISAR no ultimo balanco). Duas regras:
    - escala: terceira passada, EVENTO (V36) — quantidade do balanco
      validado anterior x grupamentos/desdobramentos/bonificacoes
      registrados entre as datas (ate o balanco; se nao fechar, ate a
      entrega do documento). Na base real: 12 documentos (119 -> 107
      REVISAR), entre eles Viver, Espacolaser, AZ Energia e AgroGalaxy;
    - indicador: sem evento no meio, vale a quantidade do ultimo balanco
      conferido, com o motivo "quantidade de acoes do balanco de ...".
    Continuam nulos (evento no meio que nao explica o numero): AZEV4 e
    IFCM3. Raizen, CIABRASF, IMC e Revee usam o balanco anterior — o
    numero novo pode ser aumento de capital real; conferir se entrarem na
    carteira. Correcao manual (MANUAL) segue sem endpoint.
 b) 18.8 revisto: o trimestral usa o 12 meses da serie como esta; erro
    da fonte passa para o ponto do trimestre. Sem conferencia extra.
 c) Payout com dividendos negativos na DVA (269 trimestres, ex.: Klabin
    06/2026 -191 mi): nulo com motivo. Vem da serie trimestral (F6) —
    reapresentacao entre documentos. SUPERADO em 10/10/2026: o payout
    passou a ser dividendos + JCP PAGOS (DFC, V45) ÷ lucro — a DVA zerava
    o payout da Vale e de 167 de 357 companhias no DFP 2025
    (docs/planos/PLANO_PRECO_TETO.md, 1.5 e D4). Pago negativo na DFC
    (reapresentacao) segue nulo com motivo.
 d) RESOLVIDO em 28/09 (decisao do Guilherme): "AMORTIZACAO RF" e o
    rotulo da B3 para amortizacao de cota de fundo — a B3 nunca manda
    "AMORTIZACAO" puro, entao sem o mapeamento toda amortizacao nova do
    dia a dia se perdia. Mapeado para AMORTIZACAO; a B3 usa o valor ja
    gravado pelo FNET (tolerancia 1e-6) e completa em vez de duplicar.
    V35 nos 103 gravados: 87 linhas eram a mesma amortizacao do FNET
    (sairam, pagamento/aprovacao completados no FNET) e 16 so da B3
    (RTEL16 e VVMR11) ganharam o tipo. Nenhuma posicao tinha esses fundos.
    Visto junto: RTEL16 tem o rendimento de 0,45 de 02/04/2026 duas vezes
    (seq 1 e 2, mesmo related_to) — repetido na propria B3, mantido.
 e) Sem cache Redis nos indicadores: 20 a 70 ms por chamada. O cache do
    plano (5) fica para a tela de ranking.

## 19. TELA DE EMISSORAS (docs/planos/PLANO_TELA_EMISSORES.md, 28/09/2026)

**19.1** Historico de proventos da B3 faltando em 17 acoes com provento recente
 (achado na conferencia da tela, 28/09): ABEV3, ALPK3, AUAU3, CURY3,
 ENJU3, EQMA3B, HBRE3, KLBN3, KLBN4, KLBN11, MRSA3B/5B/6B, OFSA3, OPCT3,
 RENT4, WEST3 — so tem eventos "B3" (12 meses), nenhum "B3_HISTORICO".
 carga/data/b3/proventos/KLBN.json tem 2 bytes ("[]"): a B3 respondeu vazio
 para o emissor e, pela regra da F10 (arquivo = estado), a busca nao se
 repete. Efeito: DY historico = 0 nesses anos (KLBN4: 0 de 2010 a 2024,
 media 0,74%), o que e falso. Investigar o pedido ao
 GetListedCashDividends desses emissores (nome de pregao? codigo?) e
 tratar resposta vazia como "tentar de novo", nao como historico vazio.
 Escopo do catalogo (adm-api), fora da tela.
**19.2** Layout em celular: o menu lateral fica sempre aberto e espreme o
 conteudo em 375 px (vale para todas as telas, nao so Emissoras). A tela
 de Emissoras em si nao estoura a largura (sem rolagem horizontal da
 pagina; tabelas e grafico trimestral rolam por dentro).
**19.3** Link do ticker so em Carteira, Dividendos e Preco Teto (D5). Outras
 telas com ticker (Transacoes, Estrategia) ficaram de fora do escopo.

## 20. AMBIENTE DE CARGA INICIAL (docs/planos/PLANO_CARGA_INICIAL.md, 29/09/2026)

**20.1** [RESOLVIDO 02/10/2026 - D14, PLANO secao 11] Levantamento manual em
 carga/data/levantamento/datas-pagamento.json (8.933 linhas, 98% com data).
 Texto original: Data de pagamento de acao antes de 2022 (D14). O historico longo da B3
 nao traz o campo; os relatorios de proventos da CVM so existem de 2022
 em diante; o resto da CVM e texto livre (Aviso aos Acionistas, PDF). A
 decidir depois da medicao (carga/scripts/sql/datas_pagamento.sql): ler os
 avisos em texto livre (amostra primeiro) ou a BRAPI Startup
 (R$ 119,99/mes; o plano gratuito recusa dividendos).
 Medicao de 30/09/2026 (banco de desenvolvimento, depois da carga da CVM):
 acoes sem data de pagamento — 2010 a 2021: ~17 mil eventos (0 a 0,6% com
 data); 2022: 1.363 de 1.808 (o relatorio da CVM so comeca no fim de
 2022); 2023: 416 de 1.793; 2024: 466 de 1.870; 2025: 402 de 2.257;
 2026: 22 de 1.185. FII: ~100% em todos os anos (FNET).
**20.2** Relatorio de proventos parcelado com parcela "a definir": fica sem data
 ate a B3 (12 meses) ou uma reapresentacao do relatorio trazer.
**20.3** RESOLVIDO em 17/10/2026 (docs/planos/PLANO_RENTABILIDADE.md, 3.3): CryptoHistoryService grava os ultimos 365 dias da
 CoinGecko (somados a carga/data/coingecko/historico, que guarda o que sai da janela) e o BTC/BRL da Binance desde
 13/10/2020 (carga/data/binance/klines), rotina 19:30 e etapa do bootstrap. Texto original: Historico de preco de cripto (CoinGecko) nao e refeito num ambiente
 novo: o grafico comeca na data da carga. Fora do escopo da carga.
**20.4** Bucket: o push e manual (docker compose --profile carga run --rm data
 push) na maquina de quem tem a chave de escrita. Se ficar esquecido, o
 que so a origem tinha (avisos novos do FNET, 12 meses da B3, relatorios
 da CVM) fica so na carga/data/ local ate o proximo push.
**20.5** Importacao de extrato: desde a V38 so confere (JA_EXISTE, SEM_DATA,
 SEM_EVENTO). Importar a carteira por arquivo continua na secao 16.
**20.6** Relatorios de proventos da CVM (2022+) que nao completaram data: ~400
 eventos de acao por ano de 2023 a 2025 seguem sem data, e 113 dos 3.412
 relatorios nao foram lidos (formato fora do padrao). A olhar caso a caso:
 valor da B3 diferente do relatorio (arredondamento, JCP bruto x liquido),
 ISIN do evento diferente, parcelas "a definir", empresa que nao entregou
 o relatorio.
**20.7** Testes de integracao da adm-api gravam na carga/data/ real do projeto
 (primevest.data.dir padrao ../data): o ProventosCatalogoIT passou a usar
 target/it-data-proventos; os demais ITs ainda usam a carga/data/ real.
**20.8** Proventos de acao A DEFINIR antigos (D17, decidido em 02/10/2026): 145
 linhas do levantamento (data-com 2010 a 2022) sem data de pagamento em
 nenhuma fonte gratuita. Ficam sem data, SEM PRAZO, ate existir fonte
 oficial paga (B3 Up2Data e/ou BRAPI). Os A DEFINIR recentes (17 linhas,
 2025-2026) nao entram aqui: a B3 12 meses e a CVM preenchem nas rotinas.
**20.9** Provento sem data de pagamento (D16, decidido em 02/10/2026): em toda
 situacao aparece como "data nao informada" para o usuario. [FEITO
 02/10/2026 na tela de Dividendos, junto da L-b do PLANO, secao 11]

## 21. PILHA DE TESTE (docs/planos/PLANO_PRIMEVEST_TESTE.md, 03/10/2026)

Achados da primeira carga inicial completa num banco novo (primevest-teste), comparada com o dev.

**21.1** Emissor que o bootstrap nao recria: o FII BFC (CNPJ 31.145.875/0001-05)
 existe no dev (created_at 27/09/2026, 95 meses em fii_monthly, nenhum ativo
 ligado) e nao no banco novo — issuers 1.197 x 1.198 e fii_monthly 42.720 x
 42.815. A descobrir por qual caminho ele entrou no dev (importacao de
 emissores de 27/09 com outro criterio, curadoria manual) e se deve existir.
**21.2** Etapa de emissores do bootstrap: classified=0,
 classificationFailures=12 (classificacao setorial pela B3). Nao comparado com
 o dev; conferir quais 12 e se o setor/segmento B3 deles ficou vazio.
**21.3** Busca de avisos no FNET por CNPJ sem resposta (timeout de 20 s, 3
 tentativas) para CXRI11 e VILG11 durante a carga: a B3 ficou de reserva
 para os dois. A rotina de proventos tenta de novo quando ligada.

## 22. CADASTRO DE USUARIO (docs/planos/PLANO_CADASTRO_USUARIO.md, 05/10/2026)

**22.1** Termos de Uso e Politica de Privacidade com texto FICTICIO
 (primevest-web/src/content/legal.ts). Antes da producao: texto definitivo e
 versao nova nos dois lados (TERMS_VERSION no front, LegalTerms.CURRENT_VERSION no
 core) — quem aceitou a provisoria passa pelo modal de novo aceite.
**22.2** Producao: CAPTCHA no cadastro e no "esqueci a senha" (ex.: Cloudflare
 Turnstile); SMTP real com dominio proprio (SPF/DKIM) no lugar do Mailpit; origens
 do dominio no Client ID do Google; publicar a tela de consentimento (hoje em modo
 Teste: so entram as contas da lista de usuarios de teste), com logo e as URLs
 dos textos definitivos.
**22.3** SMS e WhatsApp ficaram fora (D8). Se voltarem: nova implementacao de
 envio ao lado do SmtpAccountMailer e verificacao do celular (phone_verified_at).
**22.4** Limites por IP contam o X-Forwarded-For; atras de um proxy de producao,
 conferir que ele sobrescreve o cabecalho (senao o cliente escolhe o proprio IP).
**22.5** FEITO em 05/10/2026 (adendo): foto de perfil enviada pelo usuario, com recorte
 no navegador, guardada no banco (user_avatars, V42) e usada em Configuracoes e no menu.
**22.6** npm audit no primevest-web aponta 10 vulnerabilidades (3 moderadas, 7
 altas) em dependencias ja existentes antes deste escopo; nao tratadas aqui.

## 23. INDICES DE MERCADO (docs/planos/PLANO_INDICES_MERCADO.md, 05/10/2026)

**23.1** FEITO em 05/10/2026: CatalogChange INDICES (plano da renda fixa, R5).
A adm-api nao avisa a app-api quando um indice muda (D8). O resultado da
 carga ja devolve, por indice, a menor data que entrou ou mudou (changedFrom):
 o escopo da valorizacao da renda fixa cria o canal (ex.: primevest:catalogo:indices)
 e revaloriza as notas daquele indexador.
**23.2** FEITO em 05/10/2026: BusinessCalendar (core), conferido contra o CDI 2010-2026.
Calendario de feriados / dias uteis futuros (D9): necessario para o pro rata
 do IPCA+ no mes corrente e para projecao de CDI. Fica no escopo da valorizacao.
**23.3** FEITO em 05/10/2026 (D7 revista pelo Guilherme): menu e tela Indices, um card
 por indice com periodo e grafico (plano, secao 10). A comparacao da carteira com CDI, IPCA, Ibovespa e IFIX
 saiu em 17/10/2026 (tela Rentabilidade, secao 29).
**23.4** Poupanca (SGS 195, rendimento por dia de aniversario) ficou de fora (D1).
 Entra so com seed numa migration e a regra de aniversario no calculador.
**23.5** Historico antes de 2010: POST /api/v1/admin/indices?from=2000 (ou outro ano)
 estende sem mudar nada; nao foi pedido.
**23.6** FEITO em 05/10/2026 (bucket conferido: bcb 136 arquivos, b3/indices 34, iguais ao local).
 data push (Guilherme): as pastas novas carga/data/bcb e carga/data/b3/indices
 sobem para o bucket no proximo "docker compose --profile carga run --rm data push"
 (o push leva toda pasta de primeiro nivel; nenhuma mudanca no script).
**23.7** Reserva conhecida, sem codigo: IBGE SIDRA (tabela 1737) para IPCA/INPC se o SGS
 atrasar a publicacao (D4). Respondeu da sua maquina em 05/10/2026.

## 24. VALORIZACAO DA RENDA FIXA (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, 05/10/2026)

**24.1** Cupons do Tesouro com juros semestrais (IPCA+, Prefixado e IGP-M+ com Juros Semestrais) e o pagamento
 mensal do Renda+/Educa+ apos a conversao: o PU cai na data e o dinheiro pago nao aparece (D13). Nenhum titulo da
 carteira paga hoje.
**24.2** TR e poupanca sem curva (D9).
**24.3** IR, IOF e taxas: valores brutos (regra do projeto). Tambem nao ha valor liquido estimado.
**24.4** O IPCA+ privado usa o mes distribuido pelos dias uteis (D6); o metodo do aniversario (dia 15) daria
 diferenca pequena, so na renda fixa privada (o Tesouro vem pelo preco).
**24.5** RESOLVIDO em 17/10/2026: rentabilidade historica da carteira e comparacao com CDI/IPCA/Ibovespa (secao 29).
**24.6** Duas subidas seguidas da adm-api (hot reload) podem baixar o CSV do Tesouro ao mesmo tempo e uma falhar no
 ".part"; a proxima rodada completa. Sem efeito com uma instancia so.

## 25. PRECO TETO (docs/planos/PLANO_PRECO_TETO.md, 10/10/2026)

**25.1** FEITO em 13/10/2026 (docs/planos/PLANO_NOTIFICACOES.md, 5.5): `alert_enabled` voltou (V49, default false)
 com a coluna "Alerta" na lista; aviso quando o preco atual fica abaixo do ideal, rearme ao voltar acima, no maximo um
 por ativo por dia. Texto original: alerta de preco (ex.: "VALE3 abaixo do preco ideal"): a coluna alert_enabled, que
 nunca funcionou, saiu na V46 (D10). Um aviso de verdade e escopo de notificacoes proprio.
**25.2** Historico de versoes do calculo (D9 b): hoje um calculo por acao, sobrescrito a cada "Salvar". Versoes
 seriam uma tabela a mais. Historico do teto por trimestre com os padroes da epoca (D9 c) tambem fica para depois.
**25.3** So acoes com balanco na CVM (D1). FII, BDR e ETF ficam fora; um calculo 100% digitado (sem padroes) para
 BDR seria barato com a estrutura atual.
**25.4** Modelo mais rigoroso (FCD por FCFE, Graham, Bazin): a observacao do pedido de 10/10 deixa para depois.
**25.5** Testes E2E da tela (Playwright, "caminho 3"): escopo futuro do Guilherme.
**25.6** Horario de publicacao do COTAHIST diario na B3 nao e documentado: a rotina tenta 21:30 e 08:30 e completa o
 que faltar; o feriado so responde 404.
**25.7** Payout pela DFC: companhia sem a demonstracao de financiamento (6.03) fica com payout nulo; companhia que
 paga dividendo a controladores e nao controladores numa conta so (sem separar) entra inteira.

## 26. NOTIFICACOES (docs/planos/PLANO_NOTIFICACOES.md, 13/10/2026)

**26.1** Arquivamento automatico por tempo: decidido que existe, prazo em aberto (L3). Hoje so arquivar manual.
**26.2** Envio por e-mail e preferencias por tipo em Configuracoes: escopo proprio (L12). O envio de e-mail da conta
 (SmtpAccountMailer) ja existe e pode ser reaproveitado.
**26.3** Metas: escopo proprio, que vai usar a central (kind novo em NotificationKind).
**26.4** Exposicao sem token nas duas APIs (levantado em 12/10, detalhado em 13/10; escopo proprio). Conferido no
 ambiente DEV, sem token:
 a) Rotinas: os 19 endpoints de /api/v1/admin (adm-api) exigem token E is_admin (403 para os demais); o front nao chama
    a adm-api. Isso esta certo.
 b) Swagger ABERTO nas duas APIs (nao so na adm-api): GET /api-docs responde 200 na adm-api (7,4 KB: as 19 rotas de
    administrador, parametros e corpos) e na app-api (64 KB: o contrato inteiro da API do usuario); /swagger-ui.html
    redireciona para a tela do Swagger. Nao entrega dado nem executa nada (as chamadas continuam pedindo token), mas
    entrega o mapa da API a quem alcancar a porta. Configurado por permitAll em AdmSecurityConfig e SecurityConfig;
    nenhum perfil (nem application-prod.yml) desliga o springdoc.
 c) /actuator: o permitAll existe nas duas, mas o spring-boot-starter-actuator NAO esta em nenhum pom — responde 404.
    E configuracao morta: se o actuator entrar um dia, ja nasce aberto (no Spring Boot 3 so o health e exposto por
    padrao, mas qualquer management.endpoints.web.exposure.include a mais tambem ficaria sem token).
 d) Portas publicadas em 0.0.0.0 no docker-compose (alcancaveis pela rede local, se o firewall do Windows deixar):
    8080 (app-api), 8081 (adm-api), 5433 (PostgreSQL com usuario/senha padrao postgres/postgres), 6379 (Redis sem
    senha — quem alcanca publica "catalogo mudou"/"preciso do historico" nos canais), 8025/1025 (Mailpit, com os
    e-mails de codigo). So o mock da B3 (8090) esta em 127.0.0.1. O 0.0.0.0 foi posto para forcar IPv4 (comentario do
    web), nao para abrir a rede.
 Caminhos possiveis (decidir no escopo): Swagger so no perfil dev (springdoc.api-docs.enabled=false e
 springdoc.swagger-ui.enabled=false no prod/docker) ou atras de token/is_admin; tirar /actuator/** do permitAll (ou
 liberar so /actuator/health quando o actuator entrar); portas em 127.0.0.1 (DB, Redis, Mailpit e adm-api no minimo);
 senha do Postgres e do Redis vindas do .env; em producao a adm-api nem precisa de porta publicada (o script chega pela
 rede do compose).
**26.5** Rotulo de provento desconhecido (CATALOGO_ROTULO_DESCONHECIDO): o mapeamento e codigo (DividendType), entao a
 mensagem nao se resolve sozinha — o administrador dispensa depois de mapear.
**26.6** CVM e FII mensal indisponivel por ano (warn de FinancialStatementImportService/FiiMonthlyImportService) nao
 gera mensagem: o ano corrente sem arquivo e normal e encheria a caixa. So a falha da rotina inteira vira mensagem.
**26.7** Integracao da B3 com renda fixa e Tesouro (16.5/16.6): quando importar vencimento/resgate, o importado tem que
 casar com a venda lancada pelo usuario (duplicata) ou, se ela ainda nao existe, com o titulo vencido aguardando
 liquidacao (secao 27) — em vez de somar a ela. Reescrito em 15/10/2026: a venda automatica deixa de existir.
**26.8** Liquidacao automatica do titulo vencido: REVOGADA pelo escopo de venda de renda fixa (secao 27,
 docs/planos/PLANO_VENDA_RENDA_FIXA.md, 15/10/2026). O titulo vencido para de render e a rotina de abertura do dia so
 notifica ("vencido, lance a liquidacao"); a mensagem se resolve quando a venda total for lancada. O
 MaturitySettlementService deixa de gravar venda — feito em 15/10/2026 (pendencia RF_VENCIDO). As liquidacoes automaticas
 ja gravadas desde 13/10 o Guilherme corrige a mao.
**26.9** Liquidacao da RF privada espera a curva chegar ao vencimento (o CDI da vespera publicado); ela roda de novo a
 cada "indices mudaram", entao o atraso e de no maximo um dia util.
**26.10** Tela de notificacoes em tempo real (SSE/WebSocket): por enquanto consulta a cada 60 s (L4).

## 27. VENDA DE RENDA FIXA (docs/planos/PLANO_VENDA_RENDA_FIXA.md, 15/10/2026)

**27.1** RESOLVIDO em 16/10/2026 (plano de emissoras, secao 5): a regra de saida e a sale_rule da distribuidora;
 trocar a distribuidora de um titulo de RF refaz os resgates pela regra nova (aviso na tela quando muda); no Tesouro,
 modal da cascata, tudo ou nada. Texto original:
 Troca de distribuidora (escopo de emissoras de renda fixa): a regra de saida dos lotes (PROPORCIONAL, UEPS,
 PEPS) passa a ser atributo da distribuidora. O modal ja decidido no levantamento de emissoras ("quais transacoes quer
 mover de [antiga] para [nova]") tem que refazer os lotes pela regra da distribuidora nova: as vendas ligadas mantem o
 liquido digitado e recalculam bruto, IR e IOF; "a conta fecha" passa a incluir esse recalculo. Ajustar o escopo de
 emissoras quando a venda de renda fixa terminar.
**27.2** Regra de saida por instituicao (desde 16/10/2026 e a coluna fixed_income_issuers.sale_rule da distribuidora;
 so a Nu Financeira esta com UEPS) — so o Nubank (RDB/caixinha) esta provado (UEPS, conferido no centavo contra o
 extrato diario da Caixinha Turbo em 10/10/2026). PicPay (cofrinho), Inter e as demais seguem PROPORCIONAL ate haver
 extrato que prove outra regra; o teste e o mesmo: comparar valor investido, rendimento e IR do extrato depois de
 cada resgate com o que cada regra preve.
**27.3** Tesouro — rendimento negativo numa parte da venda (titulo comprado mais caro que o PU de venda): a
 implementacao de 15/10/2026 da imposto zero nessa parte e NAO compensa com as outras partes da mesma venda (plano,
 4.4). Conferir se a Receita/B3 compensam dentro da mesma operacao e, se sim, ajustar o TreasuryAllocator.taxRow.
 Conferido em 15/10/2026: a IN RFB 1.585/2015, art. 46, par. 1o, tributa a "diferenca positiva entre o valor da
 alienacao, liquido do IOF, e o valor da aplicacao financeira" (titulos publicos entram pelo art. 45, par. unico), e
 a IN nao preve compensacao de perda em titulo de renda fixa comprado direto — a unica compensacao de pessoa fisica
 e a de cotas de fundos (art. 15). Cada compra do Tesouro e uma aplicacao: imposto zero na parte com perda, sem
 compensar, bate com a norma. Fica aberto so o caso de a B3 calcular diferente na pratica (conferir num informe).
**27.4** Tesouro — taxa de custodia da B3: 0,20% a.a. sobre o valor dos titulos, provisionada por dia (pro rata)
 desde a liquidacao da compra (D+1) e cobrada so em evento de saida — venda antecipada e vencimento (deduzida do valor
 recebido) e pagamento de cupom (deduzida dos juros). Tesouro Selic: isento ate R$ 10.000,00 de estoque por CPF,
 incide so sobre o excedente. Renda+ e Educa+: regras proprias. Vigente desde 31/12/2024 (Oficio Circular B3
 014/2024-VPC, fim da cobranca semestral; pagina de tarifas da B3, conferidos em 15/10/2026). O PrimeVest nao a considera (nem na venda, nem no liquido estimado da Carteira). O usuario pode
 lancar a taxa no campo Taxas da venda, que entra no liquido. Decidir se vale calcular.
**27.5** RESOLVIDO em 15/10/2026. Venda parcial de renda fixa acima do liquido disponivel lancada "mesmo assim"
 (R9 -> D4): o excesso e rendimento que a curva nao previu e agora paga IR e IOF — gross-up pelas aliquotas dos lotes,
 como na liquidacao total (R8); o ajuste (bruto - saldo da curva) fica em settlement_adjustment e sobra o centavo da
 regra F. Testes: FixedIncomeLotsTest.acimaDoDisponivel e TransacoesIT.resgateParcialDeixaCentavo.

## 28. EMISSORAS E DISTRIBUIDORAS DE RENDA FIXA (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 16/10/2026)

**28.1** PARCIAL em 16/10/2026 (plano de emissoras, Adendo A): logos por raiz de CNPJ do Open Finance Brasil e do
 logos-bancos-br (443 arquivos em carga/data/logos/rf). Fica aberto o que nenhuma fonte cobriu — 207 grupos e 844
 empresas sem grupo (quase todas cooperativas, companhias de debenture e securitizadoras) — na planilha
 docs/emissores/Logos_Emissoras_RF_2026-10-16.xlsx, para busca manual do Guilherme (colunas amarelas: ticker da
 controladora ou URL do logo oficial). Preenchida, o logo vai para carga/data/logos/rf/{raiz do CNPJ} (ou o ticker
 para fixed_income_groups.logo_ticker, numa migration de curadoria) e sobe no data push. Texto original:
 Logo pelo site da empresa (caminho 2): o BCB e a CVM informam o site; icone pelo dominio com qualidade
 variavel (testado em 15/10/2026: Nubank 256 px, Opea 192 px, Sicredi 16 px). Hoje o logo da linha de RF e o do
 ticker curado no grupo (fixed_income_groups.logo_ticker), senao "RF".
**28.2** Compra de Tesouro importada da B3: a distribuidora e casada pelo NOME do participante (participantName);
 sem correspondencia fica "Instituicao nao listada" e o usuario recebe a notificacao RF_DISTRIBUIDORA_NAO_LISTADA.
 Casar pelo CNPJ (participantDocumentNumber da API oficial) quando a integracao trouxer o campo. Idem para RF quando
 a importacao de RF existir (16.5/16.6).
**28.3** Cooperativas por sistema (Sicoob, Sicredi, Unicred, pela central): nao tem conglomerado prudencial no BCB,
 entao cada cooperativa e um grupo dela mesma (ou nenhum). Agrupar pelo sistema exigiria curadoria ou outra fonte.
**28.4** Regra de saida das demais distribuidoras: ver 27.2.
**28.5** O produto do titulo esta em dois lugares (fixed_income_notes.asset_id e portfolio_assets.asset_id). Desde a
 V56 a FK composta (note_id, asset_id) os mantem iguais (trocar o produto do titulo leva a posicao; trocar so a posicao
 e recusado). Remover a duplicacao — o titulo ler o produto da posicao — mexe na regra H, nas buscas por ativo e nos
 testes; fica para quando houver outro motivo para mexer nessas pecas.

## 29. RENTABILIDADE (docs/planos/PLANO_RENTABILIDADE.md, 17/10/2026)

**29.1** Indice composto pela estrategia (L7): benchmark ponderado pelo Ideal Estrategico (ex.: 40% IFIX + 30% IBOV
 + 30% CDI). Precisa de metodologia propria (rebalanceamento do indice, reserva).
**29.2** Indices ANBIMA (IMA-B, IMA-B 5, IMA-B 5+, IRF-M), IBrX e IDIV: fonte nova; nao entraram (levantamento, item 3).
**29.3** BTC antes de 13/10/2020 (D2): so por BTCUSDT x PTAX, que seria serie reconstruida — fora. S&P 500 oficial
 (em dolar e convertido) tambem: hoje o proxy e o IVVB11.
**29.4** Historico da marca de reserva (D9): "Sem reserva" usa a marca de hoje para toda a historia. Guardar quando a
 posicao virou reserva seria uma tabela a mais.
**29.5** Selecao de benchmarks gravada no usuario (D10): hoje URL + localStorage; Metas pode precisar.
**29.6** Comparacao em base equivalente com a carteira liquida (L10): um "CDI liquido" exigiria aplicar a tabela de
 IR/IOF a um investimento hipotetico com os mesmos fluxos. Hoje a tela avisa e oferece a base bruta.
**29.7** Tipos de preco MANUAL (Stock, REIT, ETF internacional, Fundos, Outros) nao tem serie: posicao deles deixa o
 periodo indisponivel na carteira consolidada (R10). Precisa de fonte de preco por tipo.
**29.8** Desdobramento de BDR/ETF que o catalogo nao conhece (os eventos sao buscados so para ativo ligado a emissor)
 distorce a serie no dia do evento — o mesmo problema ja afeta o valor de hoje.
**29.9** Cripto sem par em reais na Binance (so o BTC usa a Binance): fora da janela de 365 dias da CoinGecko, so o
 que estiver no arquivo de carga/data/coingecko/historico. A rotina das 19:30 soma a janela ao arquivo todo dia.
**29.10** Testes E2E da tela (Playwright, "caminho 3"): escopo futuro do Guilherme.

## 30. MUDANCAS DE TELA (17/10/2026)

**30.1** RESOLVIDO (17/10/2026): Aportes do Dashboard passaram a ser compras - o que as vendas devolveram (liquido na
 renda fixa e no Tesouro, valor da venda nos demais) nos ultimos 12 meses; media = total / 12.
**30.4** RESOLVIDO (17/10/2026): testes de integracao dos numeros novos do Dashboard (DashboardResumoIT: aportes
 compras - vendas, janela de 12 meses com o dia de corte, aporte negativo, valor liquido com e sem renda fixa;
 ProventosAReceberIT.ultimosDozeMeses) e conferencia no tema claro do menu do usuario e do popover da previa da venda.
**30.3** RESOLVIDO (17/10/2026): Carteira com VALORIZACAO (R$ + %) e RENTABILIDADE (classica com proventos + TWR
 embaixo); o total da coluna deixou de misturar contas (linhas sem proventos, total com).
**30.2** RESOLVIDO (17/10/2026): toda rentabilidade do Dashboard em base bruta (cards, Top 5 e grafico); a tela
 Rentabilidade abre em bruto e 12M.

## 31. NOVOS INDICADORES DE ACOES (escopo futuro, aberto em 17/10/2026)

Saiu do levantamento da listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, A3). Escopo proprio, ainda
sem plano. Todos os indicadores abaixo ganham historico desde 2010 e o modal com sparkline, como os de hoje.

31.1 Patrimonio liquido como cartao na pagina da emissora (acao: PL da controladora; FII: PL do informe). A listagem
ja calcula o valor.

31.2 Indicadores com os dados que ja estao na base (so calculo): P/Receita (PSR), Margem Liquida, Margem EBIT, P/EBIT,
EV/EBIT, P/Ativo, Giro Ativos, ROA, Divida Liquida / EBIT, Divida Bruta / Patrimonio, Patrimonio / Ativos,
Passivos / Ativos (ativo total - PL), CAGR Receitas 5 anos e CAGR Lucros 5 anos (vazio com lucro negativo no inicio ou
no fim).

31.3 Indicadores que pedem contas novas dos arquivos da CVM ja baixados (carga/data/cvm, 2010-2026) e o reprocessamento
dos balancos (~7 min, sem download): Margem Bruta (DRE 3.03), Margem EBITDA, EV/EBITDA, P/EBITDA e Divida Liquida /
EBITDA (depreciacao e amortizacao da DVA 7.04.01), Liquidez Corrente, P/Cap. Giro e P/Ativo Circ. Liq. (BPA 1.01 e
BPP 2.01), ROIC (IR/CSLL e resultado antes dos tributos, DRE 3.07 e 3.08).

31.4 Bancos (17) e seguradoras (2) nao tem EBIT, caixa e divida no formato comercial: indicadores de EBIT/EBITDA,
divida, margens e capital de giro ficam "nao se aplica" para eles.

31.5 Quando existirem, os indicadores novos podem virar colunas e filtros da listagem de emissoras.

## 32. LISTAGEM DE EMISSORAS (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 17/10/2026)

Escopo implementado em 17/10/2026. Fica registrado:

32.1 Fracionarios que so existem no mercado fracionario (17 em 17/10/2026: EKTR3F, FRIO3F, GPAR3F, SNSY3F...) ficam na
lista: o catalogo nao tem o lote padrao deles, entao nao ha linha duplicada (D9 tira so o fracionario cujo lote padrao
esta na lista). Confirmado pelo Guilherme em 17/10/2026. Se o catalogo passar a ter o lote padrao, a linha do
fracionario sai sozinha.

32.2 Indicadores estranhos que a lista ordenada deixa a vista vem dos balancos importados, nao da listagem (a pagina
da emissora mostra o mesmo). Diagnostico de 17/10/2026:
  a) TIMS3 (P/L ~3.311): o DFP 2025 da TIM (documento 17209) entrou com todas as contas zeradas (lucro, PL, receita,
     ativo). O lucro de 12 meses dos trimestres seguintes e montado a partir do anual, entao 1T26 e 2T26 ficaram com
     R$ 19 mi e R$ 13 mi em vez de ~R$ 3 bi. O ITR de 30/09/2025 tambem so tem o individual (consolidated = false).
     Mais uma companhia tem o DFP 2025 com PL zerado. A investigar na importacao da CVM (adm-api): versao do
     documento, demonstracao consolidada x individual, ou o arquivo da CVM veio assim.
  b) AGXY3 (P/L ~0,01): ultimo balanco de 30/09/2025 (marca de dado antigo) com lucro de 12 meses de R$ 1,2 bi para
     16,7 mi acoes — ganho nao recorrente de empresa em recuperacao judicial; o numero e o do balanco.

32.3 Os indicadores novos e o PL na pagina da emissora estao na secao 31.

## ORDEM SUGERIDA

O item 1 saiu. O 5.1 (indices) saiu em 05/10/2026; o proximo passo e o 2.1 — a razao de
existir da renda fixa no sistema: saber quanto ela rendeu (com 23.1 e 23.2 juntos).

O 4.1 vem junto ou logo depois, porque enquanto ele nao existir voce nao
consegue vender um titulo (4.2).

O 3.1 pode esperar ate ter provento de verdade cadastrado.

O 8 nao tem hora certa, mas cada item acima fica mais barato se vier depois dele.

O 11 foi decidido em 27/09/2026 (ligar a leitura, secao 18). Texto original: o 11 e barato de decidir e caro de adiar: cada pregao acumula ~1.264 linhas que
ninguem le. Decidir entre ligar a leitura e parar de gravar vale mais que a
implementacao em si.

O 12.3 nao tem pressa e nao bloqueia nada, mas e o tipo de divida que so cresce:
cada `any` novo entra sem resistencia enquanto a regra for aviso.

Com o reset de 18/09/2026 o conjunto de migrations voltou a descrever o
resultado. Ele so continua assim se as proximas mudancas de schema forem poucas
e grandes, em vez de muitas e pequenas — um terceiro reset seria sintoma de que
a regra de nao editar migration aplicada esta sendo usada como desculpa para
empilhar ALTER.
