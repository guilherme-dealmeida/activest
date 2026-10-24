# PrimeVest — Plano de Nomes Internos em Inglês

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **Encerrado em 22/10/2026** (E1a–E4 implementadas e conferidas pelo Guilherme no mesmo dia). Levantamento completo conferido contra o código em 22/10/2026
> (seção 3). **Diretriz do Guilherme (22/10/2026, seção 2)**: nomes em inglês, valores de registro como estão — ela
> substitui as decisões N1–N14 propostas antes. Etapas E1a–E4 (seção 5).
> Origem: passo 4 do ESCOPO "Criar menu Tarefas no Primevest" (D16 do `docs/planos/PLANO_TAREFAS.md`): a tela continua
> em português, e o que é interno (rotas, API, parâmetros, configuração, classes, valores gravados) segue a linguagem do
> sistema, em inglês. O escopo de Tarefas já nasceu assim; este plano trata do que já existia. Os valores gravados
> saíram pela diretriz de 22/10/2026.

---

## 1. Objetivo

Trocar para inglês os nomes internos que hoje estão em português, sem mudar nada do que o usuário lê na tela.

## 2. Critério (diretriz do Guilherme, 22/10/2026)

**Nome é em inglês; valor de registro fica como está.**

| Entra (é nome) | Fica (é valor, ou é lido por pessoas) |
|---|---|
| URLs: rotas do front, parâmetros de URL e os valores fixos que só existem na URL (aba, base, filtro), rotas e parâmetros da API | Valores gravados no banco (enums, códigos, categorias): podem ser em português ou inglês conforme o tipo de registro; muitos fazem mais sentido em português (ex.: data com). **Nada é regravado** |
| Classes (inclusive as classes enum), páginas, componentes, métodos (inclusive os de teste), campos e variáveis | Valores de enum que trafegam na API (`ABAIXO_DO_IDEAL`, `SEM_CURVA`, `1A`/`5A`…) e na mensagem do Redis (`PROVENTOS`, `FECHAMENTO`) |
| Chaves de configuração, variáveis de ambiente e nomes de canal do Redis | Estrutura que já está em inglês (tabelas e colunas); `data_com` fica (termo do mercado, mesmo critério da data com como valor) |
| Nome de constraint em português (`ck_user_avatars_tipo`) — é estrutura | Tela, mensagens, logs, comentários, documentação, nomes de arquivo das migrations já aplicadas (o Flyway confere), nomes que vêm de fonte externa (colunas da CVM, campos da B3) |

Para enum novo: a classe nasce em inglês; os valores, em português ou inglês, conforme o tipo de registro (Tarefas, por
exemplo, nasceu com valores em inglês — D2 do `PLANO_TAREFAS.md`).

## 3. Levantamento completo (conferido em 22/10/2026)

Mudou desde a estrutura básica: o escopo de Tarefas levou os horários para o banco, então `primevest.tesouro.*`,
`primevest.cripto-historico.*`, `primevest.cotahist-diario.*` e `primevest.abertura.*` **não existem mais**, e o
`ProventosScheduler` virou `DividendsSyncTask`. Entraram itens que a estrutura básica não tinha: `/cvm/proventos` e
`fnet=todos` na adm-api, `primevest.preco-teto.*` e `primevest.emissoras.dado-antigo.*`, `PRIMEVEST_SENHA`, a origem
da mensagem "catálogo mudou", ~30 enums gravados além das notificações, códigos de contrato da API, os valores de
período `1A`/`5A`, os parâmetros da listagem de ativos e a coluna `data_com`.

Os nomes em inglês abaixo são **propostas**; o Guilherme ajusta na revisão. Grupos marcados **fora** ficam só como
registro do levantamento (seção 2).

### G1 — Rotas do front

| Hoje | Proposta | Consumidores |
|---|---|---|
| `/rentabilidade` | `/performance` | Sidebar, Header, Dashboard (2 links) |
| `/ativos`, `/ativos/:ticker` | `/assets`, `/assets/:ticker` | Sidebar, Header, `TickerLink`, `IssuerHeader`, `IssuerListingTable`, `IssuerPage`; **links gravados nas notificações** (`AdminNotices`: `/ativos/{ticker}`) |
| `/emissoras`, `/emissoras/:ticker` (já redirecionam para `/ativos`) | passam a redirecionar para `/assets` | links antigos |
| `/cadastro` | `/signup` | `LoginPage`, README |
| `/esqueci-senha` | `/forgot-password` | `LoginPage` |
| `/termos`, `/privacidade` | `/terms`, `/privacy` | Login, Cadastro, Configurações, `TermsGate`, `LegalPage` (e o `kind="termos"`/`"privacidade"` → `terms`/`privacy`) |

A conferir na E4: se algum e-mail (`MailTemplates`, `APP_URL`) aponta para uma rota em português.

### G2 — Parâmetros de URL (nomes e valores)

| Tela | Hoje | Proposta |
|---|---|---|
| Rentabilidade | `tipo`, `categoria`, `ativo`, `reserva=sem`, `base=liquido`, `periodo`, `de`, `ate`, `indices` | `type`, `category`, `position`, `reservation=without`, `basis=net`, `range`, `from`, `to`, `benchmarks` (os mesmos nomes da API da app-api) |
| Ativo (gráfico de cotação) | `periodo`, `de`, `ate` | `range`, `from`, `to` |
| Ativos (listagem) | `aba=fiis` (padrão `acoes`); por aba `acoes.*`/`fiis.*`: `ord`, `pag`, `qtd`, `nome`, `setor`, `subsetor`, `segmento`, colunas `preco`, `patrimonio` | `tab=fiis` (padrão `stocks`); `stocks.*`/`fiis.*`: `sort`, `page`, `size`, `name`, `sector`, `subsector`, `segment`, `price`, `equity` (as demais colunas são códigos de indicador e ficam) |
| Proventos | `aba=a-receber` | `tab=to-receive` |
| Estratégia | `aba=reserva` | `tab=reservation` |
| Períodos (Rentabilidade, Ativo, Índices) | valores `1A`, `5A` | ficam: são valores do enum de período da API (`?range=1A`) |
| Navegador | `localStorage` `primevest-rentabilidade-benchmarks` | `primevest-performance-benchmarks` (ou só apagar, se a tela não lê mais a seleção guardada — conferir na E4) |

Já em inglês: `ticker`, `position`, `settle`, `date` (Transações), `tab`/`execution` (Tarefas).

### G3 — API da adm-api (`/api/v1/admin/*`)

| Hoje | Proposta | Observação |
|---|---|---|
| `POST /proventos` (`fnet=todos`) | `/dividends` (`fnet=all`) | |
| `POST /proventos/levantamento` | `/dividends/payment-date-survey` | a tarefa já se chama `PAYMENT_DATE_SURVEY` |
| `POST /proventos/fnet-status` | `/dividends/fnet-status` | |
| `POST /cvm/proventos` | `/cvm/payment-dates` | tarefa `CVM_PAYMENT_DATES`; faltava na estrutura básica |
| `POST /tesouro` | `/treasury` | |
| `POST /cripto/historico` | `/crypto/history` | |
| `POST /b3/cotahist/diario` | `/b3/cotahist/daily` | |
| métodos do `AdminController` (`proventos`, `proventosLevantamento`, `cvmProventos`, `tesouro`, `cotahistDiario`) | `dividends`, `paymentDateSurvey`, `cvmPaymentDates`, `treasury`, `cotahistDaily` | |

Consumidores: `carga/scripts/admin.mjs` (texto de uso), `docs/ADM_TASKS.md`, `docs/SCHEDULE_TASKS.md`, README,
`Claude.md`, planos (estes só como histórico, ficam). As rotas são renomeadas sem manter o nome
antigo: o único consumidor é o `admin.mjs`, atualizado junto.

### G4 — Valores de contrato da API (front ↔ app-api) e JSON — **fora** (são valores de enum, seção 2)

| Onde | Hoje | Proposta |
|---|---|---|
| Período (`PerformanceService.Range`, `PriceSeries`) | `1A`, `5A` | — (fora) |
| Preço Teto — faixa | `ABAIXO_DO_IDEAL`, `ENTRE_IDEAL_E_TETO`, `ACIMA_DO_TETO` | `BELOW_TARGET`, `BETWEEN_TARGET_AND_CEILING`, `ABOVE_CEILING` |
| Preço Teto — avisos (`CeilingPriceRules.Issue`, 20 códigos) | `ACOES_AUSENTES`, `BALANCO_DEFASADO`, `CAMBIO_AUSENTE`, `CAMPO_VAZIO`, `DESCONTO_INVALIDO`, `DESCONTO_MENOR_IGUAL_G`, `G_MAIOR_QUE_D`, `G_NEGATIVO`, `LL_TTM_BAIXO`, `LL_TTM_NEGATIVO`, `LL_TTM_ZERO`, `MARGEM_INVALIDA`, `MEDIA_INCOMPLETA`, `PRECO_DEFASADO`, `PRECO_INVALIDO`, `PREMISSA_EXTREMA`, `REAPRESENTACAO`, `TRIMESTRE_AUSENTE`, `TRIMESTRE_COM_PREJUIZO`, `TRIMESTRE_DESATUALIZADO`, `UNIDADE_INVALIDA` | `SHARES_MISSING`, `STALE_STATEMENT`, `FX_MISSING`, `EMPTY_FIELD`, `INVALID_DISCOUNT`, `DISCOUNT_NOT_ABOVE_G`, `G_ABOVE_D`, `NEGATIVE_G`, `LOW_TTM_EARNINGS`, `NEGATIVE_TTM_EARNINGS`, `ZERO_TTM_EARNINGS`, `INVALID_MARGIN`, `INCOMPLETE_AVERAGE`, `STALE_PRICE`, `INVALID_PRICE`, `EXTREME_ASSUMPTION`, `RESTATEMENT`, `MISSING_QUARTER`, `LOSS_QUARTER`, `OUTDATED_QUARTER`, `INVALID_UNIT` |
| Carteira — valorização | `SEM_CURVA` | `NO_CURVE` |
| Rentabilidade — preço do dia | `SEM_PRECO`, `ULTIMO_NEGOCIO` | `NO_PRICE`, `LAST_TRADE` |
| Notificação — ação | `INFORMAR_CUSTO` | `ENTER_COST` |
| adm-api — resultado da ligação de emissores (`IssuerLinkService`) | `EMISSOR_CRIADO`, `EMISSOR_CRIADO_SEM_INFORME`, `LIGACAO_CORRIGIDA`, `TICKER_FORA_DO_CATALOGO`, `CNPJ_INVALIDO`, `CNPJ_E_DE_COMPANHIA` | `ISSUER_CREATED`, `ISSUER_CREATED_WITHOUT_REPORT`, `LINK_CORRECTED`, `TICKER_NOT_IN_CATALOG`, `INVALID_CNPJ`, `CNPJ_IS_COMPANY` |
| adm-api — importação de balanços | `SEM_QUANTIDADE` | `NO_SHARE_COUNT` |
| JSON `dataCom` (proventos/eventos) | `dataCom` | fica (data com) |
| `/api/v1/indices` | fica (N5) | |

Fora do escopo: valores de enum ficam como estão (seção 2).

### G5 — Configuração e variável de ambiente

| Hoje | Proposta | Onde |
|---|---|---|
| `primevest.proventos.{enabled, scope, pause-millis}` | `primevest.dividends.*` | `application.yml` (app e adm), `application-test.yml`, `@ConditionalOnProperty`, `@Value` |
| `primevest.emissoras.dado-antigo.{preco-dias, balanco-meses, informe-meses}` | `primevest.issuer-page.stale-data.{price-days, statement-months, report-months}` | app-api |
| `primevest.preco-teto.{margem-seguranca, crescimento-perpetuidade}` | `primevest.ceiling-price.{safety-margin, perpetual-growth}` | app-api |
| `primevest.preco-teto.alertas.{acoes-desvio, balanco-dias, desconto-max, desconto-min, lucro-baixo, lucro-desvio, percentual-max, percentual-min, perpetuidade-max, preco-dias}` | `primevest.ceiling-price.alerts.{shares-deviation, statement-days, discount-max, discount-min, low-earnings, earnings-deviation, percent-max, percent-min, perpetual-growth-max, price-days}` | app-api |
| `PRIMEVEST_SENHA` | `PRIMEVEST_PASSWORD` | `.env`, `.env.example`, `docker-compose.yml` (serviço `tools`), `carga/scripts/lib/env.mjs`, `admin.mjs`, `docs/REGRAS_CLAUDE.md` 5.5 |

Nenhuma dessas chaves está no `.env`/compose hoje (só os padrões do `application.yml`), então a troca não exige mexer
no ambiente além da `PRIMEVEST_SENHA`.

### G6 — Redis — **entram só os nomes de canal**; os valores de `Origin` ficam

| Hoje | Proposta |
|---|---|
| canal `primevest:catalogo:ativos` | `primevest:catalog:assets` |
| canal `primevest:proventos:sob-demanda` | `primevest:dividends:on-demand` |
| `CatalogChange.Origin`: `PROVENTOS`, `FECHAMENTO`, `INDICES`, `BALANCOS` | `DIVIDENDS`, `CLOSING`, `INDICES`, `STATEMENTS` |

Os dois serviços trocam juntos (mensagem sem garantia de entrega; a rede de segurança das 21:00 cobre a janela).

### G7 — Código de produção (Java e front)

| Tipo | Hoje | Proposta |
|---|---|---|
| Classes | `ProventosCatalogService`, `ProventosOnDemandListener`, `ProventosConferencia` (records `Pagamento`, `Resultado`), `FnetProvento`, `CvmProventosReport`, `TesouroPriceClient`, `TesouroSalesClient` | `DividendCatalogService`, `DividendOnDemandListener`, `DividendReconciliation` (`Payment`, `Result`), `FnetDividend`, `CvmDividendReport`, `TreasuryPriceClient`, `TreasurySalesClient` |
| Métodos e campos (exemplos) | `definirSenha`, `exigirCadastroLigado`, `handleParametro`, `Valores`/`resolveValores`, `lerSaldos`, `lerMovimento`, `lerCursores`, `buscouHoje`, `semDuplicatas`, `conferirPagamentos`, `mesmoAtivo`, `proventosConferencia` | equivalentes em inglês |
| Variáveis locais | ~60 na API (`hoje`, `resposta`, `titulo`, `tipo`, `soma`, `carteiraAte`…) e ~60 no front (`senhaAtual`, `tituloSomenteLeitura`, `notasTesouro`, `emissorasDoTipo`…) | idem |

### G8 — Testes

| Tipo | Hoje | Proposta |
|---|---|---|
| Classes (33) | `AberturaDoDiaIT`, `AlocacaoIT`, `CadastroDesligadoIT`, `CadastroUsuarioIT`, `CarteiraIT`, `CorrecaoProventosIT`, `DashboardResumoIT`, `EmissorasRendaFixaIT`, `EstrategiaIT`, `EventosSocietariosIT`, `FechamentoIT`, `IndicadoresIT`, `IntegracoesIT`, `IntegracoesB3IT`, `IntegracoesConfirmacaoIT`, `IntegracoesFilaIT`, `IntegracoesNotificacoesIT`, `ListagemEmissorasIT`, `NotificacoesIT`, `PrecoTetoIT`, `ProventosIT`, `ProventosAReceberIT`, `ProventosCatalogoIT`, `ProventosConferenciaIT`, `ProventosIrIT`, `RendaFixaCurvaIT`, `RentabilidadeIT`, `TelaEmissorasIT`, `TransacoesIT`, `CvmProventosReportTest`, `B3RespostasReaisTest`, `CvmRespostasReaisTest`, `PositionCalculatorEventosTest` | `DailyOpeningIT`, `AllocationIT`, `SignupDisabledIT`, `SignupIT`, `PortfolioIT`, `DividendCorrectionIT`, `DashboardSummaryIT`, `FixedIncomeIssuersIT`, `StrategyIT`, `CorporateEventsIT`, `ClosingIT`, `IndicatorsIT`, `IntegrationsIT`, `IntegrationsB3IT`, `IntegrationsConfirmationIT`, `IntegrationsQueueIT`, `IntegrationsNotificationsIT`, `IssuerListingIT`, `NotificationsIT`, `CeilingPriceIT`, `DividendsIT`, `DividendsToReceiveIT`, `DividendCatalogIT`, `DividendReconciliationIT`, `DividendTaxIT`, `FixedIncomeCurveIT`, `PerformanceIT`, `IssuerPageIT`, `TransactionsIT`, `CvmDividendReportTest`, `B3RealResponsesTest`, `CvmRealResponsesTest`, `PositionCalculatorEventsTest` |
| Bases e apoio | `FuncionalBase`, `CatalogoE2EBase`, `ContaSintetica`, `ContaSinteticaB3`; helpers `usuarioComCarteira`, `lancarComSucesso`, `lancarEsperandoErro`, `marcarComoReserva`, `carteiraDe`, `limparTesouro`… | `FunctionalBase`, `CatalogE2EBase`, `SyntheticAccount`, `SyntheticAccountB3`; helpers em inglês |
| Nomes dos métodos de teste | ~450 em português (`vendaSemPosicao`, `proventoNasceComIr`…) | em inglês (método é nome) |

`docs/TESTES_FUNCIONAIS.md` e o `Claude.md` citam as classes: atualizados junto.

### G9 — Valores gravados no banco (enum + CHECK) — **fora** (seção 2), só registro; entra apenas a constraint `ck_user_avatars_tipo`

| Tabela.coluna | Hoje | Proposta | Front usa |
|---|---|---|---|
| `notifications.kind` (25) | `PROVENTO_PAGO`, `EVENTO_APLICADO`, `BONIFICACAO_SEM_CUSTO`, `RF_VENCIDO`, `RF_LIQUIDADA`, `RF_LIQUIDACAO_SEM_VALOR`, `RF_LIQUIDACAO_DIVERGENTE`, `RF_DISTRIBUIDORA_NAO_LISTADA`, `PRECO_ABAIXO_IDEAL`, `PRECO_TETO_PADRAO_MUDOU`, `INTEGRACAO_IMPORTOU`, `INTEGRACAO_AGUARDANDO`, `INTEGRACAO_DIVERGENCIA`, `INTEGRACAO_CONEXAO`, `INTEGRACAO_FALHOU`, `POSICAO_TICKER`, `CATALOGO_TICKER_NOVO`, `CATALOGO_TICKER_SUMIDO`, `CATALOGO_TICKER_RENOMEADO`, `CATALOGO_SEM_COTACAO`, `CATALOGO_INDICE_ATRASADO`, `CATALOGO_ROTULO_DESCONHECIDO`, `CATALOGO_TESOURO_SEM_TITULO`, `ROTINA_FALHOU`, `FONTE_INDISPONIVEL` | `DIVIDEND_PAID`, `CORPORATE_EVENT_APPLIED`, `BONUS_WITHOUT_COST`, `FI_MATURED`, `FI_SETTLED`, `FI_SETTLEMENT_WITHOUT_AMOUNT`, `FI_SETTLEMENT_MISMATCH`, `FI_DISTRIBUTOR_UNLISTED`, `PRICE_BELOW_TARGET`, `CEILING_PRICE_DEFAULTS_CHANGED`, `INTEGRATION_IMPORTED`, `INTEGRATION_AWAITING`, `INTEGRATION_MISMATCH`, `INTEGRATION_CONNECTION`, `INTEGRATION_FAILED`, `POSITION_TICKER`, `CATALOG_NEW_TICKER`, `CATALOG_MISSING_TICKER`, `CATALOG_RENAMED_TICKER`, `CATALOG_NO_QUOTE`, `CATALOG_LATE_INDEX`, `CATALOG_UNKNOWN_LABEL`, `CATALOG_TREASURY_WITHOUT_BOND`, `TASK_FAILED`, `SOURCE_UNAVAILABLE` | não |
| `notifications.category` | `PROVENTOS`, `EVENTOS`, `RENDA_FIXA`, `PRECO_TETO`, `INTEGRACOES`, `CARTEIRA`, `SISTEMA` | `DIVIDENDS`, `CORPORATE_EVENTS`, `FIXED_INCOME`, `CEILING_PRICE`, `INTEGRATIONS`, `PORTFOLIO`, `SYSTEM` | sim |
| `notifications.severity` | `INFO`, `ATENCAO` | `INFO`, `WARNING` | sim |
| `notifications.status` | `NAO_RESOLVIDA`, `RESOLVIDA`, `DISPENSADA` | `UNRESOLVED`, `RESOLVED`, `DISMISSED` | sim |
| `dividends.type`, `asset_cash_events.type`, `withholding_tax_rules.dividend_type` | `DIVIDENDO`, `JCP`, `RENDIMENTO`, `AMORTIZACAO`, `RESTITUICAO_CAPITAL` | `DIVIDEND`, `JCP`, `INCOME`, `AMORTIZATION`, `CAPITAL_RETURN` | sim |
| `asset_cash_events.source` | `B3`, `B3_HISTORICO`, `FNET` | `B3`, `B3_HISTORY`, `FNET` | |
| `asset_cash_events.payment_date_source` | `CVM`, `LEVANTAMENTO` | `CVM`, `SURVEY` | |
| `asset_stock_events.type` | `DESDOBRAMENTO`, `GRUPAMENTO`, `BONIFICACAO` | `SPLIT`, `REVERSE_SPLIT`, `BONUS` | |
| `transactions.origin` | `MANUAL`, `AUTO`, `SISTEMA` | `MANUAL`, `AUTO`, `SYSTEM` | sim |
| `asset_terms.rate_type`, `fixed_income_notes.rate_type` | `PREFIXADO`, `POS_FIXADO`, `HIBRIDO` | `FIXED`, `FLOATING`, `HYBRID` | sim |
| `fixed_income_issuers.kind` | `BANCO`, `FINANCEIRA`, `COOPERATIVA`, `CORRETORA_DISTRIBUIDORA`, `SECURITIZADORA`, `COMPANHIA`, `TESOURO`, `NAO_LISTADA` | `BANK`, `FINANCE_COMPANY`, `CREDIT_UNION`, `BROKER_DEALER`, `SECURITIZER`, `COMPANY`, `TREASURY`, `UNLISTED` | |
| `fixed_income_issuers.guarantor` / `.sale_rule` | `NENHUM` / `PROPORCIONAL`, `UEPS`, `PEPS` | `NONE` / `PRO_RATA`, `LIFO`, `FIFO` | sim |
| `fixed_income_groups.name_source` | `MANUAL`, `AUTOMATICO` | `MANUAL`, `AUTOMATIC` | |
| `assets.issuer_link_source` | `PREFIXO` (demais em inglês/siglas) | `PREFIX` | |
| `issuers.statement_layout` | `COMERCIAL`, `BANCO`, `SEGURADORA` | `COMMERCIAL`, `BANK`, `INSURER` | |
| `share_counts.source` / `.scale_status` / `.scale_check` | `COMPOSICAO` / `REVISAR` / `VIZINHO`, `EVENTO` | `COMPOSITION` / `REVIEW` / `NEIGHBOR`, `EVENT` | |
| `market_indices.periodicity` / `.unit` | `DIARIA`, `MENSAL` / `PCT_DIA`, `PCT_MES`, `PCT_PERIODO`, `PCT_ANO`, `PONTOS` | `DAILY`, `MONTHLY` / `PCT_DAY`, `PCT_MONTH`, `PCT_PERIOD`, `PCT_YEAR`, `POINTS` | sim |
| `benchmarks.category` / `.kind` | `RENDA_FIXA`, `JUROS`, `INFLACAO`, `ACOES_BR`, `FII`, `ACOES_EUA`, `CRIPTO` / `INDICE`, `ATIVO` | `FIXED_INCOME`, `INTEREST_RATES`, `INFLATION`, `STOCKS_BR`, `FII`, `STOCKS_US`, `CRYPTO` / `INDEX`, `ASSET` | sim |
| `ceiling_price_quarters.origin` / `.unit` | `PADRAO`, `INFORMADO` / `UNIDADE`, `MIL`, `MILHAO`, `BILHAO` | `DEFAULT`, `USER` / `UNIT`, `THOUSAND`, `MILLION`, `BILLION` | sim |
| Colunas | `asset_cash_events.data_com`, `asset_stock_events.data_com` | ficam | sim (`dataCom`) |
| Constraint | `ck_user_avatars_tipo` | `ck_user_avatars_content_type` | |

Cada linha leva: migration que solta o CHECK, regrava as linhas (`UPDATE`) e recria o CHECK; o enum Java no core; as
consultas nativas/JPQL com o valor literal; o tipo do front e os mapas de rótulo (a tela continua mostrando o rótulo
em português); os SQLs de teste e os de `carga/scripts/sql/`. Já em inglês: tarefas, integrações, metas, tema,
transações (`BUY`/`SELL`), emissores (`COMPANY`/`FII`).

### G10 — Links gravados

As notificações já gravadas guardam o link da ação (`/ativos/{ticker}`, ver G1). Registro não é regravado: o
redirecionamento da rota antiga resolve, e as notificações novas já nascem com `/assets/...`.

### G11 — Dados de catálogo — **fora** (valor de registro)

`asset_types.name` (`ACAO`, `RENDA_FIXA`, `TESOURO_DIRETO`, `FUNDO_INVESTIMENTO`, `ETF_INTERNACIONAL`, `CRIPTO`,
`OUTROS`) e `asset_categories.name` (~60, ex.: `ACOES_BRASIL`, `ORACULOS`, `BENS_INDUSTRIAIS`). É dado: as migrations
e o código resolvem por `name`, a tela mostra `description`. Trocar exige migration de dados e revisão de todo literal
(`"RENDA_FIXA"`, `"TESOURO_DIRETO"`) sem ganho para quem lê o código.

### G12 — Carga e pilha de teste — **fora** deste escopo (só a `PRIMEVEST_SENHA` entra, G5)

Scripts (`conferencia/`, `levantamento/`, `gerar_seed_*.mjs`, `b3investidor/conferir.mjs`, `simulador/`), pastas de
`carga/data/` espelhadas no bucket R2 (`levantamento/`, `tesouro/`, `b3/proventos/`, `cvm/proventos/`,
`b3/cotahist/diario/`, `coingecko/historico/`, `fnet/status/`), perfil `carga` do compose e a pilha `primevest-teste`
(`.env.teste`, `docker-compose.teste.yml`). Renomear pasta de dados exige reorganizar o bucket (o `data push` nunca
apaga, então duplicaria tudo).

## 4. Decisões

A diretriz do Guilherme (22/10/2026, seção 2) substitui as perguntas N1–N14 propostas no mesmo dia. Como ficou cada uma:

| # | Assunto | Resolução (22/10/2026) |
|---|---|---|
| N1 | Grupos que entram | G1, G2, G3, G5, G7, G8 e os nomes de canal do G6 (+ a constraint do G9). Fora: G4, valores do G6, G9, G11, G12 |
| N2 | Rotas e parâmetros antigos | Continuam aceitos sem prazo: rota antiga redireciona (como `/emissoras`) e a URL antiga é traduzida na chegada (`replace`) |
| N3 | Valores gravados no banco | **Não se traduzem** (diretriz) |
| N4 | Ordem por grupo, bateria verde | Sim (seção 5) |
| N5 | Critério do que fica | O da seção 2 |
| N6 | `data_com` | Fica |
| N7 | Rotas da adm-api | Renomeadas para inglês, sem o nome antigo; `admin.mjs` junto |
| N8 | Nomes dos métodos de teste | Em inglês (método é nome) |
| N9 | Identificadores no código de produção | Em inglês |
| N10 | Links gravados nas notificações | Não se regravam: o redirecionamento resolve |
| N11 | Carga e pilha de teste | Fora, exceto a `PRIMEVEST_SENHA` |
| N12 | `PRIMEVEST_SENHA` | → `PRIMEVEST_PASSWORD`; o Claude renomeia também no `.env` (e no `.env.teste`, quando existir; só o nome — o valor não é lido nem alterado) |
| N13 | Períodos `1A`/`5A` | Ficam (valor de enum) |
| N14 | Nomes propostos | Os da seção 3, ajustáveis na revisão |

## 5. Etapas

Cada etapa termina com a bateria verde: `mvn verify` (core, integration, app-api, adm-api), lint, `tsc -b` e build do
front, `docker compose up -d --build`, conferência do comportamento e um `grep` dos nomes antigos que comprove que não
sobrou nenhum fora do que fica (seção 2). Documentação (`Claude.md` — inclusive a convenção da seção 2 —,
`docs/PENDENCIAS.md`, README, `docs/ADM_TASKS.md`, `docs/SCHEDULE_TASKS.md`, `docs/TESTES_FUNCIONAIS.md` e o andamento
deste plano) atualizada na própria etapa.

| Etapa | O quê | Grupos | Risco | Validação específica |
|---|---|---|---|---|
| **E1a** | Código de produção: classes, métodos, campos e variáveis em inglês (API e front) | G7 | baixo: nenhum contrato muda | bateria |
| **E1b** | Testes: classes, bases, helpers e nomes de método em inglês | G8 | baixo | contagem de testes igual à de antes |
| **E2** | Configuração, variável de ambiente, nomes de canal do Redis e a constraint `ck_user_avatars_tipo` (migration de `RENAME CONSTRAINT`) | G5, G6 (canais), G9 (constraint) | médio: chave esquecida cai no padrão em silêncio; os dois serviços sobem juntos por causa dos canais | teste que sobe o contexto com cada chave nova; `admin.mjs` com `PRIMEVEST_PASSWORD`; no ambiente: lançar transação → pedido sob demanda → "catálogo mudou" no log dos dois serviços |
| **E3** | Rotas da adm-api e `admin.mjs` | G3 | baixo | cada rota nova pelo `admin.mjs` (as leves; `bootstrap`/`fnet=all` só a resposta 202 e a rodada na tela de Tarefas) |
| **E4** | Rotas e parâmetros do front, redirecionamento e tradução da URL antiga, link novo das notificações (`AdminNotices`) | G1, G2, G10 | médio: links salvos e da própria tela | navegador: cada rota nova, cada rota e parâmetro antigo chegando traduzido, cards do Dashboard, link de notificação antiga e nova; conferência visual do Guilherme |

Pontos de parada: o fim de cada etapa.

## 6. Riscos

- **Literais espalhados.** Rotas e parâmetros aparecem em links montados no front, nos `navigate`, no backend
  (`AdminNotices`) e na documentação. Mitigação: `grep` antes (lista de ocorrências) e depois (zero fora da seção 2).
- **Renomear sem mudar comportamento.** E1 é só renomeação; qualquer diferença de contagem de testes ou de resultado é
  erro da etapa.
- **Chave de configuração com padrão.** Uma chave renomeada só no `application.yml` e não no `@Value` (ou o contrário)
  passa a usar o padrão sem erro: o teste da E2 sobe o contexto conferindo os valores.
- **Subida conjunta.** E2 (canais do Redis) exige app-api e adm-api da mesma versão: `docker compose up -d --build` de
  tudo.

## 7. Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Levantamento completo e plano | ✓ | revisa |
| Diretriz (seção 2) | aplica | decidiu (22/10/2026) |
| Implementação E1a–E4 (com ordem explícita, uma etapa por vez) | ✓ | — |
| Conferência visual das rotas e links (E4) | prepara | confere no navegador |

## 8. Andamento

- 22/10/2026 — Estrutura básica criada a partir do levantamento do `docs/planos/PLANO_TAREFAS.md` (seção 4). Retomada em
  escopo próprio.
- 22/10/2026 — ETAPA Planejamento: levantamento completo conferido contra o código (seção 3, G1–G12), critério do que
  fica (seção 2), decisões N1–N14 e etapas E1–E8.
- 22/10/2026 — Diretriz do Guilherme: valores de registro não se traduzem (podem fazer mais sentido em português, como
  a data com); URLs, classes, páginas e métodos seguem o padrão inglês; valor de enum em português ou inglês conforme o
  tipo de registro, com a classe enum em inglês. N1–N14 resolvidas por ela (seção 4); saem do escopo os valores de
  contrato da API (G4), os valores da mensagem do Redis e os valores gravados (G9); etapas refeitas (E1a–E4).
- 22/10/2026 — Guilherme de acordo com o plano revisado, inclusive as três interpretações da diretriz: chaves de
  configuração e nomes de canal do Redis são nomes (vão para inglês); `ck_user_avatars_tipo` é renomeada; carga e pilha
  de teste fora, exceto a `PRIMEVEST_SENHA`. Planejamento concluído; execução só com ordem explícita.
- 22/10/2026 — Execução (ordem do Guilherme: "pode executar o plano").
  - **E1a/E1b** (fechadas juntas: os testes chamam os membros renomeados do código, e as bases e helpers de teste são
    usados por todas as classes): 7 classes de produção e o pacote `adm/integration/tesouro` → `treasury`; ~420 nomes
    de método, campo e variável no Java de produção, ~1.600 nos testes (classes, bases, helpers, métodos), ~260 no
    front. Ferramentas de renomeação que só tocam código (Java: fora de string e comentário; front: só nós
    `Identifier` do AST do TypeScript, nunca texto de JSX). Ficaram de propósito: `data_com`/`dataCom`/`dataBase`,
    `NoticeStatus` do FNET (gravado em `carga/data/fnet/status`), `B3Investidor*` (nome do produto da B3), os valores de
    enum e as constantes que guardam valor gravado (`CeilingPriceQuarter.PADRAO/INFORMADO`). Um teste dependia da
    ordem de execução (`FixedIncomeIssuersIT$Selector.distributor` via o "Banco Seletor" de outro teste): a ordem
    mudou com os nomes novos, e o teste passou a usar nomes próprios. Bateria: as mesmas contagens de antes (core 210;
    integration 103; adm-api 127 + 82; app-api 210 + 263), verdes.
  - **E2**: `treasury.*` (era `tesouro.*`, achado na execução), `primevest.dividends.*`, `primevest.issuer-page.stale-data.*`,
    `primevest.ceiling-price.*` (e `.alerts.*`); `PRIMEVEST_PASSWORD` (`.env` renomeado só no nome; `.env.teste` não
    existe); canais `primevest:catalog:assets` e `primevest:dividends:on-demand`; V66 renomeia `ck_user_avatars_tipo`
    → `ck_user_avatars_content_type`, e a V67 as duas que a conferência do banco achou depois (`ck_user_avatars_tamanho` →
    `ck_user_avatars_size`, `ck_users_cpf_completo` → `ck_users_cpf_complete`); o `SchemaTest` confere. Bateria verde.
  - **E3**: `/dividends`, `/dividends/payment-date-survey`, `/dividends/fnet-status`, `/cvm/payment-dates`, `/treasury`,
    `/crypto/history`, `/b3/cotahist/daily`, `fnet=all`; `admin.mjs` e docs. A pasta de recurso de teste `tesouro` →
    `treasury`.
  - **E4**: rotas `/performance`, `/assets`, `/signup`, `/forgot-password`, `/terms`, `/privacy`; parâmetros `tab`,
    `range`, `from`, `to`, `type`, `category`, `position`, `reservation=without`, `basis=net`, `benchmarks` e, na
    listagem, `stocks.*`/`fiis.*` com `sort`, `page`, `size`, `name`, `sector`, `subsector`, `segment`, `price`,
    `equity`. Endereço antigo (rota ou parâmetro) é traduzido na chegada (`utils/legacyUrl.ts`, no `App`); as
    notificações novas já nascem com `/assets/...`.
  - **Conferência no ambiente** (22/10/2026): V67 aplicada; canais novos ativos no Redis (`PUBSUB CHANNELS`) e um
    `PUBLISH` no `primevest:dividends:on-demand` chegou à app-api; rotas novas da adm-api respondem 202 (antigas 404);
    resposta do disparo com chaves `task`/`result` (eram `rotina`/`resultado`, achado na execução; os valores
    continuam em português); no navegador, endereços antigos redirecionam para os novos, e Ativos (FIIs ordenados por
    nome), Rentabilidade (`range`/`type`/`reservation`/`basis`/`benchmarks` nas chamadas) e Estratégia
    (`tab=reservation`) abrem certo. Falta a conferência visual do Guilherme (E4).
- 22/10/2026 — Conferência visual da E4 pelo Guilherme: tudo certo. `/indices` fica: já é inglês (plural de *index*,
  como a tabela `market_indices` e a API `/api/v1/indices`); só o rótulo da tela é "Índices". **Escopo encerrado.**
