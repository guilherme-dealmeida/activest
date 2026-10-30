# Activest — Plano de Ativos Internacionais (cadastro de Stocks, REITs e ETFs internacionais e categorias)

> Fase: **Implementation concluída** (F1–F7), 29/10/2026 — branch `TRACK_01-ativos_internacionais`; Testing, Review e
> commit sugerido na seção 9 (Andamento). Etapas F1–F7 da seção 11.7, uma fechada antes da outra. Discovery, Impact e Planning encerrados (seções 7, 10 e 11).
> Origem: ESCOPO "Ativos internacionais Activest" (27/10/2026). Texto-base: *"Já abordamos este tema algumas vezes, mas
> não evoluímos com o cadastro de ativos de ETFs internacionais, REITs e Stocks. O levantamento é sempre muito custoso
> [...] temos que buscar fontes para extrair as informações que precisamos. Seria informações dos tickers, categorias,
> preços atuais e históricos, entre outros."*
> **Reformulado pelo Guilherme em 28/10/2026:** *"vamos tirar os preços atuais e históricos da jogada, será um escopo de
> cadastro de ativo e categoria"*. O levantamento de preço feito antes da reformulação fica no Anexo A, como insumo do
> escopo futuro de preços.
> Pré-requisito já entregue: Cadastro de moedas (`docs/planos/PLANO_CADASTRO_MOEDAS.md`, 26/10/2026).
> Fontes: conferidas na web em 28/10/2026 (busca ligada pelo Guilherme nesse dia), com links na seção 4.5.

---

## 1. Objetivo

Os tipos `STOCK`, `REIT` e `ETF_INTERNACIONAL`, que existem desde o início e estão **vazios**, passam a ter **catálogo**
— ticker, nome, tipo, **categoria**, moeda, país e bolsa — criado e mantido por **integração** com fontes oficiais e
gratuitas (regra "informação de catálogo vem de integração, não de extrato"), e as **categorias** desses três tipos.
Com isso o usuário consegue lançar transações desses ativos, e a Alocação/Estratégia passa a enxergá-los por tipo e
categoria.

## 2. Fora do escopo

- **Preço atual e série histórica** (reformulação de 28/10/2026). Os três tipos continuam `price_source = MANUAL`:
  posição valorizada pelo custo (`AppreciationUpdater` sem preço não valoriza) e período indisponível na Rentabilidade
  (PENDENCIAS 29.7 continua aberta para esses tipos). Levantamento guardado no Anexo A.
- Proventos, desdobramentos e benchmark S&P 500 — dependem de fonte de preço/eventos (mesmo escopo futuro).
- Escopo "ETFs americanos" (recadastro da Binance e importação de operações em USD — PENDENCIAS 35.7, 37.5).
- Integração com corretoras do exterior (Avenue, Nomad, Inter Global, XP Internacional, IBKR): o usuário lança à mão.
- Página do ativo internacional na tela de Ativos (indicadores, demonstrativos, gráfico) — ver D14.

## 3. Descrição consolidada (o que já foi pedido e o que este escopo cobre)

| # | Onde nasceu | O que existe hoje | Neste escopo |
|---|---|---|---|
| O1 | Tipos de ativo (V2) | `STOCK`, `REIT`, `ETF_INTERNACIONAL`, **0 ativos, 0 categorias** | ✅ Catálogo e categorias |
| O2 | Moedas — PENDENCIAS 38.1 | Moeda no ativo, transação, provento, PTAX e tributos do exterior (V9/V10) testados só com o fictício `ZZUSD` | ✅ Ativos reais em USD para lançar transação (a prova real da transação em dólar) |
| O3 | Metas / Alocação — região | `asset_types.region = EXTERIOR` | ✅ Ativos passam a existir nessas visões (pelo custo) |
| O4 | Rentabilidade — 29.7, 29.3 | Tipo `MANUAL` sem série | ❌ Escopo de preços |
| O5 | Proventos do exterior — V10 | Retenção e IR prontos, sem eventos | ❌ Escopo de preços/eventos |
| O6 | Telas que supõem reais — 38.8 | Gráfico, indicadores | ❌ Sem preço, não se aplica ainda |

## 4. Discovery — o que foi levantado (27/10 e 28/10/2026)

### 4.1 Estado atual no banco (DEV, 27/10/2026)

| Tipo | `price_source` | Moeda / região padrão | Ativos | Categorias | Posições |
|---|---|---|---|---|---|
| `STOCK` | `MANUAL` | USD / EXTERIOR | 0 | 0 | 0 |
| `REIT` | `MANUAL` | USD / EXTERIOR | 0 | 0 | 0 |
| `ETF_INTERNACIONAL` | `MANUAL` | USD / EXTERIOR | 0 | 0 | 0 |
| `BDR` (referência) | `BRAPI` | BRL / EXTERIOR | 892 | 20 | 0 |
| `ETF` (B3, referência) | `BRAPI` | BRL / BRASIL | 219 | 9 | 1 |

Nenhum usuário tem ativo internacional; não há dado a migrar. Há 1 usuário no sistema (`SIGNUP_ENABLED=false`).

### 4.2 Estado atual no código (só o que toca o cadastro)

| Peça | Situação | Lacuna para o escopo |
|---|---|---|
| `assets` (V1 + V9) | `ticker` **único no catálogo** (`uk_assets_ticker`), `name`, `isin`, `price_source_ref`, `currency_code`, `reference_currency_code`, `country_code`, `asset_category_id` (um nível), `active` | Sem bolsa, sem identificador estável do exterior (CIK, FIGI). Colisão de ticker (4.3) |
| `asset_categories` | Um nível, único por `(asset_type_id, name)`; segundo nível mora no emissor (`issuers`) | Categorias novas dos três tipos (D6–D8); estrangeiro não tem emissor |
| Catálogo por integração | Já existe o modelo dos **fundos** (`FUND_QUOTES`: cadastro da CVM vira ativo em tempo de execução, nome curto, "vivo") e a **conferência do catálogo** (`CATALOG_AUDIT`: ticker novo/sumido/renomeado vira aviso ao administrador) | Tarefa de cadastro do exterior (D11) |
| Busca de ativos (`/api/v1/assets/?query=`) | Por ticker/nome, teto 50, `query` obrigatória; fundos com busca própria; **ativo inativo não aparece** (B3: 9 ações inativas desde a V14 — plano BRAPI, F3) | Ticker repetido entre tipos (D3); lançamento retroativo de ativo inativo (D11) — hoje o mesmo limite vale para o nacional |
| Lançamento | Ativo USD já aceito (câmbio PTAX padrão, tributos do exterior — escopo de moedas) | Nada além do ativo existir |
| Preço | `MANUAL`: sem valorização | Fora (seção 2) |

### 4.3 Colisão de ticker — achado importante

`uk_assets_ticker` é único no catálogo inteiro. Tickers da B3 têm dígito e nunca colidem com os dos EUA, mas as **222
criptos têm ticker só de letras**, e várias têm o mesmo símbolo de empresas e ETFs americanos. **São homônimos, não
cadastro errado** — as criptos estão certas, cada mercado tem a sua lista e as duas se cruzam:

| Ticker | Cripto no catálogo | Nos EUA |
|---|---|---|
| META | MetaDAO | Meta Platforms |
| CVX | Convex Finance | Chevron |
| LTC | Litecoin | LTC Properties (REIT) |
| DASH | Dash | DoorDash |
| STX | Stacks | Seagate |
| LIT | Lighter | Global X Lithium (ETF) |

Outros (a conferir na lista oficial): `MET`, `BDX`, `TEL`, `FLR`, `SNX`, `SUN`, `BP`, `A`, `U`, `S`, `M`, `AI`, `AR`,
`CAKE`.

**Direção do Guilherme (28/10/2026):** tirar a unicidade do ticker ou estendê-la com o tipo de ativo. O que hoje supõe
ticker único (insumo do Impact):

| Onde | O que muda com `(ticker, tipo)` |
|---|---|
| `AssetRepository.findByTicker*` — 23 chamadas em 13 classes (transações, proventos, metas, integrações B3/Binance, importação, fundos, tarefas de emissor e unit) | Pode devolver mais de um: cada chamada passa a usar o tipo, ou o id |
| API por ticker: `/api/v1/assets/{ticker}` e `/{ticker}/prices`, `/indicators`, `/company-data`, `/logo`, `/quote`, `/defaults/{ticker}`, `/asset/{ticker}` | Ambígua para `META`: precisa do tipo ou do id |
| Rotas do front: `/assets/:ticker`, `/ceiling-prices/:ticker` | Idem (endereço publicado continua valendo — REGRAS 9.4) |
| Logos `carga/data/logos/{TICKER}.{ext}` | `META.svg` da cripto e da ação seriam o mesmo arquivo |
| Busca e rótulos do front (`assetLabel`) | Mostrar o tipo quando o ticker repete |
| Importação (B3, Binance), conferência do catálogo | Já sabem o tipo de origem; passam a usá-lo na busca |

### 4.4 O que a B3 já nos dá: os BDRs como referência

`docs/brapi/BDRs_BRAPI_2026-09-23.xlsx` (892 BDRs): **668 BDRs de ação, 223 de ETF, 1 unit**. As 20 categorias de BDR do
catálogo (setores estilo TradingView vindos da BRAPI) têm de 8 a 126 ativos (Finanças 126, Serviços de tecnologia 108,
Tecnologia eletrônica 63…).

- **REIT não aparece como setor**: Prologis, Realty Income, Equinix, American Tower, Public Storage, Welltower… estão
  em "Finanças". Pelo nome, pelo menos 25 BDRs de ação são REITs.
- O BDR é a referência natural de categoria da mesma empresa lá fora: quem compra AAPL34 e AAPL deveria ver a mesma
  categoria (D6).
- Os 223 BDRs de ETF estão todos numa categoria só ("ETFs no exterior"); o ticker de origem nem sempre é o prefixo do
  BDR (`BEEM39` → EEM, mas não é regra).

### 4.5 Fontes de cadastro — conferidas na web em 28/10/2026

**[C]** = lido na página citada; **[I]** = inferência. Os arquivos da Nasdaq e da SEC não abrem pelo shell desta sessão
(proxy); foram lidos pela busca, que corta o texto — há cabeçalhos e primeiras linhas reais, não a contagem exata.

Todas as fontes de cadastro abaixo são **oficiais, gratuitas e sem restrição de exibição** (o problema de licença das
fontes de preço — Anexo A — não se aplica aqui): SEC (dado público, sem chave), OpenFIGI ("for any purpose, commercial
or non-commercial") e Nareit (publicação aberta). **Exceção:** o Nasdaq Trader é "personal and non-commercial use
only" ([termos](http://www.nasdaqtrader.com/Trader.aspx?id=CopyDisclaimMain)) — ver D15.

| Fonte | Acesso | O que traz [C] | Limites [C] | Papel no cadastro |
|---|---|---|---|---|
| **Nasdaq Trader** — `https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt`, `otherlisted.txt`, `nasdaqtraded.txt` ([definições](https://nasdaqtrader.com/Trader.aspx?id=SymbolDirDefs)) | Público, sem chave; atualizado ao longo do dia | `Symbol\|Security Name\|Market Category\|Test Issue\|Financial Status\|Round Lot Size\|ETF\|NextShares` e `ACT Symbol\|Security Name\|Exchange\|CQS Symbol\|ETF\|Round Lot Size\|Test Issue\|NASDAQ Symbol`; bolsa (N = NYSE, A = NYSE American, P = NYSE Arca, Z = Cboe BZX, V = IEX); **flag ETF**; test issue | Só os vivos; sem setor nem REIT | **Universo** e Stock × ETF |
| **SEC EDGAR** — `https://www.sec.gov/files/company_tickers_exchange.json` (`cik, name, ticker, exchange`), `https://data.sec.gov/submissions/CIK##########.json`, bulk `submissions.zip` ([APIs](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)) | "do not require any authentication or API keys"; User-Agent com contato; 10 req/s | **CIK** (chave estável da empresa), **SIC** (`6798` = Real Estate Investment Trusts), estado/país, `formerNames`, site | Sem histórico de ticker; ETF sem SIC útil | REIT (sinal 1), país, setor por SIC |
| **OpenFIGI v3** — `https://api.openfigi.com/v3/mapping` ([docs](https://www.openfigi.com/api/documentation), [termos](https://www.openfigi.com/docs/terms-of-service)) | Chave gratuita opcional | `figi, compositeFIGI, ticker, exchCode, securityType, securityType2…`; `securityType` tem **"REIT", "ETP", "Common Stock", "ADR"** | 25 req/min (10 itens) sem chave; 250 req/min (100 itens) com chave. Só papéis vivos (deslistado não é achado) | **Tipo do papel** (REIT, ETP, ADR, MLP, ação comum — prova na D5), **compositeFIGI** (chave estável do papel) |
| **Nareit** — PDF "Constituent Companies of the FTSE Nareit All REITs Index" ([31/08/2026](https://www.reit.com/sites/default/files/returns/FNUSIC2026.pdf)) e [diretório](https://reit.com/investing/reit-directory) | Público; PDF, sem CSV | Empresa, ticker e **setor de propriedade**; 180 REITs (143 equity, 37 mortgage) | Atualização periódica | **Categoria do REIT** e REIT (sinal 3) |
| Nasdaq.com screener | Não documentado; bloqueado a robôs; "Download CSV" manual na página | `sector, industry, country, marketCap…` | Frágil; termos não verificados | Só apoio manual ao levantamento |
| Tiingo `supported_tickers.zip`, Alpha Vantage `LISTING_STATUS`, Massive `reference/tickers` | Chave / termos de uso pessoal | Deslistados (`endDate`, `delistingDate`, `delisted_utc`) | Restrição de exibição | Descartadas como fonte do cadastro |

Setores de propriedade da Nareit [C]: Office, Industrial, Retail (Shopping Centers, Regional Malls, Free Standing),
Residential (Apartments, Manufactured Homes, Single Family Homes), Diversified, Lodging/Resorts, Self Storage, Health
Care, Timberland, Telecommunications, Data Centers, Gaming, Specialty e Mortgage (Home Financing, Commercial Financing).

Achados de detalhe [C]:

- **SIC 6798 sozinho erra**: EQIX, AMT, PLD, O, IRM, CCI, SPG, VICI estão em 6798, mas a CoreCivic (CXW) continua em
  6798 depois de deixar de ser REIT em 2021 ([10-K](https://www.sec.gov/Archives/edgar/data/1070985/000156459022005663/cxw-10k_20211231.htm)).
  Regra proposta: SIC 6798 **e** (Nareit **ou** OpenFIGI `REIT`) — D5.
- **Formato de classe e preferencial diverge**: Nasdaq Trader `BF.B` e `ABR$E`/`ABRpE`/`ABR-E`; SEC `BRK-B`; OpenFIGI
  no padrão Bloomberg (`BRK/B` [I]). Precisa de forma canônica (D10).
- **Categoria de ETF**: nenhuma fonte gratuita e oficial pronta. Opções: regra pelo nome (como a B2 dos ETFs da B3) ou
  derivar da composição pelo N-PORT da SEC (trimestral, só fundos da Lei de 1940 — commodities e cripto ficam de fora).
- **Setor de ação**: a classificação GICS é licenciada; a da BRAPI/TradingView (a dos BDRs) não tem fonte gratuita para
  papel americano. A fonte oficial e livre é o **SIC** da SEC, que precisa de tabela de equivalência para as categorias.
- **ISIN/CUSIP**: nenhuma fonte gratuita devolve (o CUSIP é licenciado). Chaves estáveis gratuitas: **CIK** (empresa) e
  **compositeFIGI** (papel).
- **Deslistados**: Nasdaq Trader e SEC trazem só os vivos. A fonte oficial de saída de bolsa é o **Form 25** da SEC
  (aviso de retirada de listagem, desde 2006): ~1.900 a 3.500 por ano, **de todas as classes** (ações, preferenciais,
  notas, warrants, ETFs) — [sec-api, contagem por ano](https://sec-api.io/datasets/form-25nse-files). O formulário traz
  emissor, bolsa e classe, mas **não traz o ticker** ([exemplo](https://www.sec.gov/Archives/edgar/data/795212/000114036123027789/brhc20053729_25.htm)):
  o ticker antigo teria de ser deduzido. Fontes com lista pronta de deslistados (EODHD, FMP, Alpha Vantage, Tiingo) são
  pagas ou de uso pessoal.
- **Quantos deslistados (ordem de grandeza)**: o número de empresas operacionais domésticas listadas fica perto de
  3.600 desde 2013, com entradas compensando saídas ([Ritter/UF](https://site.warrington.ufl.edu/ritter/files/number-of-listed-firms-on-US-exchanges.pdf));
  mais de 60% das saídas são por fusão/aquisição ([WFE](https://focus.world-exchanges.org/articles/global-delisting-trends)).
  ETFs: 196 fechados nos EUA em 2024 ([ETFGI via The Daily Upside](https://www.thedailyupside.com/etf/industry-news-etf/etf-launches-are-outpacing-closures-can-the-momentum-last/)).
  Estimativa [I]: algumas centenas de ações e ~150–200 ETFs saindo por ano — **da ordem de 5 a 8 mil papéis desde 2010**.

### 4.6 Volume (fontes recentes)

- Ações: 3.657 empresas operacionais domésticas na NYSE e Nasdaq + 1.515 estrangeiras no fim de 2025
  ([Ritter/UF](https://site.warrington.ufl.edu/ritter/files/number-of-listed-firms-on-US-exchanges.pdf)) [C].
- ETFs: mais de 5.000 negociados em maio/2026
  ([Morningstar](https://www.morningstar.com/business/insights/blog/etf-spaghetti-cannon)) [C].
- REITs: 180 no FTSE Nareit All REITs (31/08/2026) [C].
- Total da ordem de **10 mil papéis**, menor que os 55 mil fundos da CVM; o arquivo da Nasdaq também traz warrants,
  rights, units e preferenciais, que pedem regra de inclusão (D4).

### 4.7 Riscos já visíveis

1. **Ticker** repetido com cripto (4.3) e formatos de classe diferentes entre fontes — casar por CIK/compositeFIGI.
2. **REIT por SIC** tem falso positivo (CoreCivic) — classificação cruzada.
3. **Categoria de ETF por regra de nome** erra em fundo de nome genérico — revisão na planilha (D12).
4. **Troca de ticker** no exterior (FB → META): sem o compositeFIGI/CIK, viraria ativo novo e o antigo "sumido".
5. **Sem preço**: o usuário vê a posição internacional pelo custo; a tela precisa deixar isso claro (já existe o rótulo
   das Metas para tipos `MANUAL`, L2).
6. **Volume na busca**: ~10 mil papéis a mais na busca de ativos, com nomes parecidos entre ações e ETFs.

## 5. Requisitos (rascunho para o Impact)

| # | Requisito |
|---|---|
| R1 | Catálogo dos três tipos criado e mantido por integração (tarefa na tela de Tarefas), sem seed manual de ativo |
| R2 | Cada ativo com tipo, categoria, moeda (USD), país, bolsa e identificador estável (CIK/compositeFIGI) |
| R3 | Categorias de um nível para os três tipos, com a origem da classificação registrada (fonte ou curadoria) |
| R4 | Curadoria do administrador nunca sobrescrita pela rotina (como `issuer_link_source = MANUAL`) |
| R5 | Ticker repetido entre tipos aceito (D3), com busca e telas que mostram o tipo |
| R6 | Papel que sai da bolsa fica inativo (nunca apagado — pode ter posição); ticker novo, sumido ou renomeado vira aviso ao administrador, como a conferência da B3 |
| R7 | Fonte parada vira rodada `FAILED` + aviso `FONTE_DESATUALIZADA`, como Tesouro e câmbio |
| R8 | Levantamento em planilha para revisão antes de gravar (D12), como `docs/brapi/` |
| R9 | Data de saída da bolsa no ativo; inativo na busca da Nova transação com a marca "deslistado", aceitando lançamento até essa data — **internacional e nacional** (D11) |
| R10 | Deslistados desde o corte (D2) cadastrados com o ticker que tinham, tipo, categoria e data de saída; o que não fechar vira pendência do escopo |

## 6. Recomendações do Discovery

1. Cadastro com **fontes oficiais e livres** (D15): SEC (universo, CIK, SIC, país, deslistados), OpenFIGI
   (REIT, ADR, compositeFIGI) e Nareit (REIT e setor de propriedade).
2. Tratar o catálogo internacional como os **fundos**: criado por integração, mantido por tarefa, inativo em vez de apagado.
3. Resolver o **ticker repetido** antes de qualquer cadastro (D3) — é a mudança de maior alcance.
4. **Ações pelas mesmas 20 categorias dos BDRs**, pela ordem: categoria do BDR da mesma empresa → equivalência SIC →
   curadoria na planilha.
5. Começar só pelos **EUA em USD**, com bolsa e país no ativo para outras bolsas depois.

## 7. Decisões para o Guilherme (Discovery)

Renumeradas na reformulação de 28/10/2026 (as antigas D7 preço, D8 histórico, D9 proventos, D10 fonte de preço, D13
benchmark e D16 licença saíram com o preço; "ETFs americanos" e corretoras viraram "fora do escopo"; a conferência das
fontes foi resolvida em 28/10). Respostas do Guilherme em 7.1; o que segue aberto em 7.2.

### 7.1 Respostas do Guilherme (28/10/2026)

| # | Decisão | Situação |
|---|---|---|
| **D1** | Mercados: só EUA (NYSE, Nasdaq, NYSE American, NYSE Arca, Cboe), USD | ✅ Aprovada |
| **D3** | Ticker repetido: unicidade `(ticker, tipo de ativo)` | ✅ Aprovada |
| **D4** | Instrumentos: ação comum, ADR, **preferencial** (na categoria do setor da empresa) e **MLP** → `STOCK`; ação de SPAC → `STOCK`; REIT (equity e hipotecas) → `REIT`; ETF/ETP → `ETF_INTERNACIONAL`. **Fora**: units de SPAC, warrants, rights e test issues | ✅ Aprovada (units, warrants e rights fora após a explicação — ver nota abaixo) |
| **D6** | Categorias de Stock: as 20 dos BDRs (BDR da mesma empresa → equivalência SIC → curadoria) | ✅ Aprovada **por enquanto** — revisitar com a planilha (D12) |
| **D7** | Categorias de REIT: setores da Nareit agrupados | ✅ Aprovada **por enquanto** — idem |
| **D8** | Categorias de ETF: temas por regra do nome | ✅ Aprovada **por enquanto** — idem |
| **D9** | Gravar bolsa, **CIK** (número da empresa na SEC) e **compositeFIGI** (código do papel na Bloomberg/OpenFIGI): troca de ticker (FB → META) vira renomeação do mesmo ativo, com aviso ao administrador | ✅ Aprovada |
| **D10** | Classe dentro do ticker: ponto para classe (`BRK.B`), hífen para preferencial (`ABR-E`); `share_class` fica vazia (os valores dela são da B3); busca aceita as outras grafias | ✅ Aprovada |
| **D11** | Ativo inativo aparece **só na busca da Nova transação**, com a marca "deslistado", aceitando lançamento até a **data de saída** (coluna nova). **Vale também para o nacional**, que hoje esconde o inativo da busca (9 ações da B3) e por isso não aceita lançamento retroativo | ✅ Aprovada — as duas coisas passam a acontecer |
| **D12** | Levantamento em planilha para revisão antes de gravar | ✅ Aprovada — é onde D6, D7 e D8 são conferidas |
| **D13** | Logos: BDR da mesma empresa → **logo.dev** gratuito com atribuição ("Logos by Logo.dev" visível no app; arquivo guardado em `carga/data/logos`, como hoje) | ✅ Aprovada |
| **D14** | Onde aparece: Nova transação, Carteira, Alocação, Estratégia; fora da tela de Ativos | ✅ Aprovada |
| **D2** | Deslistados com o ticker apurado, **corte 2010**, pelo caminho da SEC (Form 25 → CIK → `dei:TradingSymbol`; Series & Class/N-CEN para ETFs); lacunas que não fecharem na prova do Impact viram pendência do escopo | ✅ Aprovada ("Sim, corte 2010") |
| **D15** | Universo só com fontes livres: SEC + OpenFIGI; Nasdaq Trader apenas como conferência | ✅ Aprovada |
| **D5** | REIT: **o OpenFIGI decide**; SEC e Nareit só conferem (divergência numa aba da planilha, informativa); deslistado (que o OpenFIGI não acha): REIT só com código SEC 6798 **e** lista Nareit do ano, senão `STOCK` | ✅ Aprovada |

Nota da D4 (o que cada um é): **preferencial** americana é quase renda fixa (valor de face US$ 25, dividendo fixo,
recomprável), mas entra como ação, como pediu o Guilherme. **Unit** listada nos EUA é quase sempre de SPAC (1 ação +
fração de warrant, que se separam em semanas) — não é o TAEE11; o equivalente permanente são as **MLPs** (EPD, ET), que
entram. **Warrant** = bônus de subscrição (direito de comprar a ação a preço fixo até uma data); **right** = direito de
subscrição temporário — nenhum dos dois existe no catálogo nacional.

Nota da D11 (insumo do Impact): a data de saída do nacional pode vir do último pregão do papel no COTAHIST, que já é
baixado; a dos EUA, do Form 25 da SEC (D2).

### 7.2 Em aberto

Nenhuma. Registro da prova que sustentou a D5:

**D5 — prova.** Prova feita em 28/10/2026 no OpenFIGI (Bloomberg), pela máquina do
Guilherme:

| Papel | OpenFIGI | SEC (código) | Correto |
|---|---|---|---|
| Prologis, Realty Income, Equinix, American Tower, Weyerhaeuser | REIT | 6798 | REIT ✔ |
| Annaly, Starwood Property (REITs de hipotecas) | REIT | — | REIT ✔ |
| CoreCivic, GEO Group (deixaram de ser REIT) | Common Stock | 6798 (CoreCivic, desatualizado) | Ação ✔ |
| Apple | Common Stock | — | Ação ✔ |

O OpenFIGI acertou os 10; o código da SEC errou a CoreCivic. Recomendação, sem precisar de decisão caso a caso:

1. **Quem decide é o OpenFIGI** (mantido pela Bloomberg, atualizado quando a empresa muda de status).
2. Nareit e SEC servem só de **conferência**: a divergência aparece numa aba da planilha, para informação, sem bloquear.
3. Papel que o OpenFIGI não encontra (é o caso dos **deslistados** — TWTR não é achado): vale o código da SEC
   **e** a lista da Nareit daquele ano; sem os dois, `STOCK`.

O mesmo OpenFIGI também classifica o resto da D4: SPY, GLD e IBIT = `ETP`; TSM = `ADR`; EPD = `MLP`; BRK/B = ação
comum — o mapeamento para os três tipos sai direto do campo.

## 8. Próximos passos

1. Respostas das decisões do Planning (11.10): P1–P4.
2. Aprovação explícita do plano para começar a Implementation (etapa 4), pela F1 (REGRAS 2.2 e 2.3).

## 9. Andamento

| Data | Etapa | O que aconteceu |
|---|---|---|
| 27/10/2026 | Discovery | Levantamento do banco (tipos vazios), do código, dos BDRs como referência e das fontes candidatas (sem teste: busca web desligada na organização). Colisão de ticker com as criptos achada |
| 27/10/2026 | Discovery | REGRAS 5.6 removida a pedido do Guilherme; busca web refeita e recusada de novo (configuração da organização). Criptos e papéis americanos esclarecidos como homônimos |
| 28/10/2026 | Discovery | Busca web ligada pelo Guilherme; fontes conferidas com links. D3 (antiga D4): direção do Guilherme `(ticker, tipo)` ou sem unicidade — mapeado o que depende do ticker único |
| 28/10/2026 | Discovery | **Reformulação do Guilherme**: escopo só de cadastro de ativo e categoria; preço atual e histórico fora. Plano refeito; levantamento de preço movido para o Anexo A; decisões renumeradas D1–D14 |
| 28/10/2026 | Discovery | Respostas: D1, D3 `(ticker, tipo)`, D12 e D14 aprovadas; D6–D8 aprovadas por enquanto. Perguntas respondidas e decisões revistas: D2 (ordem de grandeza dos deslistados e Form 25 da SEC, sem ticker), D4 (preferenciais e MLPs entram; units de SPAC, warrants e rights fora), D5 e D9 explicadas, D10 (classe no ticker), D11 (inativo na busca da Nova transação, nacional junto), D13 (logo.dev) |
| 28/10/2026 | Discovery | Respostas: D4 (units, warrants e rights fora; preferenciais e MLPs entram), D9, D10, D11 (inativo na busca da Nova transação, nacional junto, data de saída), D13 (logo.dev) aprovadas. D2: Guilherme exige apurar os tickers dos deslistados — caminho pela SEC (Form 25 → CIK → `dei:TradingSymbol`; Series & Class/N-CEN para ETFs) levantado, lacunas listadas. D5: prova no OpenFIGI (10 de 10 certos; SEC errou a CoreCivic) — recomendação OpenFIGI decide. D15 nova: termos do Nasdaq Trader (uso pessoal) |
| 28/10/2026 | Discovery | D2 aprovada com corte em 2010 (caminho da SEC; lacunas viram pendência) e D15 aprovada (SEC + OpenFIGI; Nasdaq Trader só conferência). Em aberto só a D5 |
| 28/10/2026 | Discovery | D5 aprovada (OpenFIGI decide). **Discovery encerrado**; Guilherme mandou seguir para o Impact |
| 28/10/2026 | Impact | Prova das fontes pela máquina do Guilherme (SEC, OpenFIGI, Nareit, Nasdaq Trader para conferência; amostra de deslistados 2011–2022); inventário do que supõe ticker único (23 buscas, `toMap` do `IssuerLinkService`, logos das criptos com o mesmo nome, 5 metas); riscos e dependências; decisões I1–I7 abertas |
| 28/10/2026 | Impact | Respostas: I1, I7 e I5 (9 inativas pelo COTAHIST) aprovadas. Revistas: I2–I4 pela sugestão do Guilherme (ticker + tipo no payload e na rota; id só na meta), I5 (inativação automática por prova formal: CVM, Form 25, sumiço + sem negócio), I6 (colunas no `assets`). D15: termos do Nasdaq Trader explicados |
| 28/10/2026 | Impact | I2–I4 e I5 (inativação automática) aprovadas; "não grava nada no catálogo" apagado da documentação (PLANO_BRAPI, Claude.md) — código e seed mudam na Implementation. I6 revista: uso das colunas do `assets` por tipo conferido no banco; recomendação de tabela 1:1 |
| 28/10/2026 | Impact | I6 aprovada (tabela 1:1); pendência 41.1 registrada (colunas de um tipo só no `assets`). **Impact encerrado** |
| 28/10/2026 | Planning | Plano da seção 11: V13, regras C1–C11, fontes e cache, tarefas `FOREIGN_CATALOG`/`FOREIGN_DELISTED`, API, front, etapas F1–F7, critérios, Quem faz; decisões P1–P4 abertas |
| 28/10/2026 | Planning | Perguntas do Guilherme: passo a passo das contas (11.11); P2 esclarecida (a data decide); P4 revista com a regra de atribuição do logo.dev (projeto de empresa = comercial; link em página sem login) e a contagem de logos |
| 28/10/2026 | Planning | Logos: a carga para guardar é pela API de self-hosting com a **chave secreta** (não pelo CDN — fair use); passo a passo corrigido (`LOGO_DEV_SECRET_KEY`); busca documentada por domínio; manutenção depende de manter um plano (opções da P4) |
| 28/10/2026 | Planning | P1 e P3 confirmadas; P4 trocada: o Guilherme busca os logos — gerado `docs/internacional/ativos_internacionais.csv` (lista da bolsa + tipo pelo OpenFIGI) |
| 28/10/2026 | Planning | CSV gerado: 12.193 papéis (6.131 ETF, 5.879 Stock, 183 REIT); chave do OpenFIGI no `.env` |
| 29/10/2026 | Planning | Logos do exterior colocados pelo Guilherme (10.592 arquivos); conferência por conteúdo: ETF = logo da gestora (614 imagens para 5.150), 38 imagens genéricas em Stock, ~313 ETFs com gestora diferente a conferir; regras L1/L2 para a F6 |
| 29/10/2026 | Planning | P4: diretrizes do Guilherme para os logos (BDR prevalece → genéricas apagadas → gestora errada substituída → sem logo recebe o da gestora → resto sem logo), em ordem; aguardando a ordem |
| 29/10/2026 | Planning | P4: etapa 4 também para ação (logo da própria empresa); etapa 3 — lista de gestoras sem logo no acervo (`logos_gestoras_faltantes.csv`, 151 gestoras / 323 ETFs) para o Guilherme baixar |
| 29/10/2026 | Planning | P4: simulação das etapas 1–5 (prévia `logos_relatorio_previa.csv`); 191 logos genéricos da BRAPI descartados na etapa 1; etapa 3 conservadora (98 "a conferir"); aguardando a ordem |
| 29/10/2026 | Planning | P4 executada: logos do exterior arrumados nas 5 etapas (relatório `logos_relatorio.csv`); conferido no disco |
| 29/10/2026 | Implementation | **F1 fechada** (ticker por tipo): V13 (`uk_assets_ticker_type` com `delisted_on`, metas por `assetId`); `AssetResolver` no lugar do `findByTicker` (transação e prévia pelo par ticker + tipo na data, importação pelos tipos da fonte, proventos, renda fixa, fundos, tarefas da adm-api); mapas e SQL da adm-api restritos aos tipos da B3; metas pelo id do ativo (campo `CATALOG_ASSET`); rotas `/assets/{tipo}/{ticker}`, `/logo` e `/prices`; logos do exterior lidos da subpasta do tipo (a rotina de logos não toca neles); front com o tipo no payload, no logo e no campo de ativo da meta. Bateria: `mvn clean verify` 1.129 testes verdes (homônimos `ZZUSD`/`ZZHOM`), front tsc + lint + vitest (150) verdes; V13 aplicada no banco de desenvolvimento (5 metas convertidas) |
| 29/10/2026 | Implementation | **F2 fechada** (data de saída e inativação no nacional): V14 (`delisting_source`: CVM, FORM_25, LIST_GONE, MANUAL); `CATALOG_AUDIT` passa a inativar com prova (registro cancelado na CVM, menos migração; fora da lista da BRAPI + 30 pregões sem negócio no COTAHIST, sem ser renomeação) e a dar a data das inativas sem data; aviso `CATALOGO_TICKER_INATIVADO` (também a quem tem posição); desfazer por `PUT /api/v1/admin/assets/{id}/activation` (grava `MANUAL`, nunca refeito); busca da Nova transação com inativos (`includeDelisted`, marca "deslistado em"), data limitada à saída e recusa depois dela. Nota do banco de desenvolvimento: as 9 inativas fracionárias têm negócio no COTAHIST até 28/10/2026 — pela regra aprovada, a data de saída delas sai como a do último negócio |
| 29/10/2026 | Implementation | **F3 fechada** (estrutura do exterior): V15 (`foreign_listings` 1:1, origens com `LIST` para o tipo vindo da própria lista da bolsa; categorias STOCK 19, REIT 12, ETF 9); clientes Nasdaq Trader (fotografia diária), SEC (tickers, índice de formulários, cadastro, Series & Class; `User-Agent` com o e-mail do `MAIL_FROM`; 8 pedidos/s), OpenFIGI (lote e ritmo pela chave, cache por ticker) e Nareit (PDF lido com o PDFBox), testados com arquivos reais gravados. Bateria F2+F3: `mvn clean verify` 1.140 testes verdes |
| 29/10/2026 | Implementation | **F4 fechada** (catálogo vivo dos EUA): V16 (`FOREIGN_CATALOG`, segunda 06:30, `WRITE` nasce falso); regras C1–C5, C7 dos EUA e C9 na adm-api (`ForeignRules`, `ForeignCatalogService`, tabela SIC → setor versionada); ficha da SEC enxuta por CIK; prévia JSON + planilha de revisão e conversor da curadoria (`carga/scripts/levantamento/internacional_*.py`); rota `/api/v1/admin/foreign-catalog`. Achado na rodada real: preferenciais da Nasdaq não têm `$` no símbolo — entram pelo nome. Primeira rodada: 12.180 papéis (5.862 Stock, 183 REIT, 6.135 ETF), 1.053 fora, 16 sem categoria; gravados 12.164 no banco de desenvolvimento (segunda rodada: nada a mudar). Detalhes na 11.13. Planilha `carga/data/levantamento/internacional/catalogo-revisao.xlsx` aguarda a revisão do Guilherme (D12); a curadoria se aplica sobre o que já foi gravado. Bateria: `mvn clean verify` 1.149 testes verdes (`ForeignRulesTest`, `ForeignCatalogIT`) |
| 29/10/2026 | Implementation | **F5 fechada** (deslistados dos EUA desde 2010): V17 (`FOREIGN_DELISTED`, sem agenda, `FROM_YEAR` 2010, `WRITE` nasce falso); Form 25 da SEC (classe, bolsa e data de efeito; formulário do emissor pelo HTML) → CIK → ticker pelo `dei:TradingSymbol` do XBRL e por padrões no texto, em paralelo (6 linhas) no ritmo de 8 pedidos/s; fundo pelo Series & Class; tipo FUND → ETF, SIC 6798 + Nareit → REIT, resto Stock; empresa ainda viva fora; curadoria por acesso. Gravados 5.906 deslistados com `FORM_25`; cobertura do ticker Stock 86%, REIT 100%, ETF 37% (lacunas na PENDENCIAS 41.4). Seção 11.15 |
| 29/10/2026 | Implementation | **F6 fechada** (logos e categoria): logo do acervo copiado para o ativo no catálogo (genérico da gestora no ETF); 12.180 ativos vivos todos com categoria (16 pela curadoria, `curadoria.json`); cobertura de logo Stock 5.512/5.862, REIT 182/183, ETF 6.014/6.135. Lentidão das telas achada no teste (reindexação dos logos em 10,8 s a cada consulta) e corrigida no `AssetLogos` |
| 29/10/2026 | Implementation | **F7 fechada** (conferência no navegador pelo Guilherme, nos dois temas): lançamentos de IAU, PLD, AAPL, META (Stock e cripto homônima) e EEMD deslistado com PTAX; data depois da saída recusada; Carteira e Alocação certas |
| 29/10/2026 | Testing / Review | Bateria completa `mvn -o clean verify` verde (13 min): core 255, integration 103, adm-api 155 + 99 de integração, app-api 237 + 307 de integração; front 150 testes (vitest) e `tsc` sem erro. Revisão das mudanças (sem segredo no código nem na documentação; `.env.example` só com o nome da chave). **Escopo encerrado**; commit sugerido ao Guilherme |

---

## 10. Impact — análise de impacto (28/10/2026)

### 10.1 Resumo das decisões que entram no Impact

Catálogo de `STOCK`, `REIT` e `ETF_INTERNACIONAL` dos EUA (D1), vivos e deslistados desde 2010 com o ticker apurado
(D2, D4), criado por integração com SEC + OpenFIGI (D15), tipo pelo OpenFIGI (D5), categorias de um nível (D6–D8),
bolsa/CIK/compositeFIGI no ativo (D9), classe no ticker (D10), ticker único por **tipo** (D3), inativo na busca da Nova
transação até a data de saída — **nacional junto** (D11), planilha de revisão (D12), logos BDR → logo.dev (D13), fora
da tela de Ativos (D14). Sem preço.

### 10.2 Prova das fontes (POC) — chamadas reais pela máquina do Guilherme, 28/10/2026

| Prova | Resultado |
|---|---|
| **Nasdaq Trader** (`nasdaqtraded.txt`, conferência) | 13.250 papéis sem test issue: **5.772 ETFs**, ~5.900 ações/ADRs, 543 preferenciais, 463 warrants, 277 units, 141 rights, 147 notas (classificação pelo nome, aproximada). Bolsas: Nasdaq 5.609, NYSE 2.886, NYSE Arca 2.746, Cboe 1.677, NYSE American 310 |
| **SEC `company_tickers_exchange.json`** | 10.429 linhas: Nasdaq 4.359, NYSE 3.276, **OTC 2.574** (fora de D1), Cboe 44, sem bolsa 164. Acha **93%** dos não-ETFs do Nasdaq Trader (6.947 de 7.478) |
| **SEC `company_tickers_mf.json`** (fundos) | 28.616 linhas. Somado ao arquivo acima, acha **4.780 de 5.772 ETFs (83%)**. Os **992 que faltam** são quase todos lançamentos recentes (Corgi 217, Innovator 177, Leverage Shares 56…): o arquivo da SEC atrasa em relação à bolsa |
| **OpenFIGI — tipo do papel** | 10 de 10 certos na D5; SPY/GLD/IBIT = `ETP`, TSM = `ADR`, EPD = `MLP`, BRK/B = ação comum. Ticker reaproveitado confirmado: `FB` hoje é um ETF da ProShares. Deslistado (TWTR) não é achado |
| **OpenFIGI — delimitar o universo** | Filtro por tipo devolve também balcão e papéis sem listagem (ação comum "US": 20.002; ADR: 2.498; ETP: 6.903). O código de bolsa do FIGI é o local de negociação (ETP em "UP" 5.991 e em "UF" 5.988), **não a bolsa de listagem**: o OpenFIGI classifica, mas não delimita quem é listado |
| **Nareit** | PDFs anuais "Constituent Companies of the FTSE Nareit All REITs Index" de **2006 a 2026**, com **empresa, ticker, setor e subsetor** ([2012](https://www.reit.com/sites/default/files/returns/FNUSIC2012.pdf), [2026](https://www.reit.com/sites/default/files/returns/FNUSIC2026.pdf)) — servem à D5 (lista do ano) e dão o ticker de REIT deslistado (D2) |
| **Deslistados — Form 25-NSE** (índice trimestral `form.idx`) | Por trimestre, sem as bolsas: 138 (2011T2), 195 (2013T2), 158 (2016T2), 249 (2018T2), 377 (2022T4) — **de todas as classes**; boa parte é nota, preferencial, warrant e produto estruturado, filtráveis pela classe do formulário (`descriptionClassSecurity`) |
| **Deslistados — ticker pelo XBRL (`dei:TradingSymbol`)** | Amostra de 8 por trimestre: **2022: 8 de 8**; 2016: 4 de 5 ações; 2011–2013: **0 de 5** ações (o campo quase não era preenchido antes da obrigatoriedade de 2019) |
| **Deslistados — ticker pelo texto do 10-K/10-Q** ("under the symbol…") | Amostra de ações deslistadas: **2011: 6 de 8** e **2013: 6 de 8** somando XBRL e texto (o XBRL sozinho: 1 e 2). Erram: Heinz, Volt, Technology Research, China Botanic. Às vezes vêm candidatos a mais (SulphCo: SUF e SLPH; Wilmington: WL e lixo) — vão para a planilha. O anexo da bolsa no Form 25 não traz ticker (conferido no do Twitter) |
| **logo.dev** | Não testado: exige criar conta (token gratuito) |


Leitura da prova:

- **Universo vivo**: a SEC sozinha (D15) cobre bem as ações (93%), mas deixa de fora **17% dos ETFs** — os mais novos,
  até o arquivo dela atualizar. O OpenFIGI classifica, mas não diz quem é listado. Fechar esse buraco exige uma fonte
  de lista de bolsa (Nasdaq Trader) ou aceitar o atraso — decisão **I1**.
- **Deslistados**: o caminho da D2 funciona, com qualidade que cai com a idade: de 2019 em diante o ticker vem pronto
  no XBRL; antes, vem do texto do relatório ("under the symbol…"), com acerto alto mas não total e às vezes com mais de
  um candidato — esses vão para a planilha. REITs deslistados têm uma segunda fonte (PDFs anuais da Nareit, com ticker).
  Volume de chamadas estimado: ~3 a 5 por papel × 5–8 mil papéis ≈ 20 a 40 mil chamadas à SEC, ~1 hora no limite de
  10 por segundo, **uma vez** (carga), com os arquivos guardados em `carga/data/sec` como as demais fontes.
- **ETFs deslistados**: não têm XBRL de capa (o trust arquiva por todos os fundos); o ticker vem das fontes de fundos
  da SEC (Series & Class 2010+, N-CEN 2018+) — não provado nesta rodada; segue como risco R-D2 até a prova no começo da
  Implementation.

### 10.3 Componentes afetados

**Banco (core, migration nova — V13):**

| Objeto | Mudança |
|---|---|
| `assets` | `uk_assets_ticker` (único por ticker) → **único por `(ticker, asset_type_id)`** (D3). Colunas novas: bolsa, `cik`, `composite_figi`, identificador de série da SEC para ETF (I6), **data de saída** (D11), origem da classificação (fonte × curadoria, R3/R4) |
| `asset_categories` | Categorias novas dos três tipos (D6–D8, ~40 linhas) — seed de referência, como a V2 |
| `task_types`, `tasks`, atributos | Tipo e tarefa nova do catálogo do exterior (D11 — semanal, com Run) |
| `goals.params` | **5 das 20 metas** guardam ticker nos parâmetros (`GoalTypeCatalog`): com ticker repetido entre tipos, passam a precisar do tipo ou do id (I4) |
| 9 ações inativas da B3 (V14) | Recebem a data de saída (I5) |

**Backend — o ticker deixa de identificar o ativo sozinho (D3).** Inventário do que supõe ticker único:

| Onde | O que acontece se `META` existir como cripto e como ação |
|---|---|
| `TransactionCreateDTO.ticker` + `TransactionService` (5 buscas) | A transação é criada pelo ticker: ficaria ambígua. Precisa do id do ativo (I2) |
| Integrações: `ImportPipeline`, `IntegrationService` (3), `DividendReconciliation`, `ImportRules.apply(m, findByTicker)` | Importação da Binance procura `META` e pode achar a ação: tem de procurar **no tipo da fonte** (B3 → tipos da B3; Binance → `CRIPTO`) |
| `IssuerLinkService` (adm-api) | Monta o mapa ticker → ativo com `Collectors.toMap`: **quebra com exceção** quando houver dois `META` |
| `HistoricalQuotesImportService` (COTAHIST), `CatalogAuditService` | Mapas por ticker com o catálogo inteiro: passam a olhar só os tipos da B3 |
| `GoalService`, `GoalFactsLoader`, `GoalTypeCatalog` | Meta por ativo guarda e procura o ticker (I4) |
| `DividendService`, `AssetService.findByTickerWithTerms` (`GET /assets/{ticker}`), `FixedIncomeIssuerController`, `FundQuoteService`, tarefas de emissor/unit | Busca por ticker: B3/RF/fundo não colidem, mas a API genérica precisa do tipo ou do id (I3) |
| `AssetLogos` (core) + `carga/data/logos/{TICKER}.{ext}` | **Já existem** `META.png`, `CVX.png`, `LTC.png`, `DASH.png`, `STX.png`, `LIT.png`, `MET.png`, `BDX.png`, `TEL.png` — das criptos. O logo da ação sobrescreveria o da cripto (I7) |
| `AssetRepository` (busca) | `searchByTypeAndTickerOrName` passa a trazer inativo com a marca e a data de saída (D11); as demais buscas seguem sem inativo |
| Validação do lançamento (`TransactionService`/`PositionGuard`) | Data da transação ≤ data de saída do ativo (D11), nacional e exterior |

**adm-api — integração nova:** clientes SEC (listas, `form.idx`, `submissions`, documentos dos filings, fundos), OpenFIGI,
Nareit (PDF, com o PDFBox que já existe) e logo.dev; serviço do catálogo do exterior (universo, tipo, categoria, renomeação
por CIK/FIGI, inativação com data); carga dos deslistados 2010+; geração da planilha (D12); curadoria do administrador
nunca sobrescrita; avisos ao administrador (ticker novo, sumido, renomeado — os mesmos tipos de notificação do catálogo
da B3); `application.yml`, `.env` (chave do OpenFIGI e token do logo.dev, opcionais/novos) e o `User-Agent` com
contato que a SEC exige (a partir do `MAIL_FROM`).

**Front:** Nova transação (enviar o id do ativo; marca "deslistado"; data limitada à saída), rótulo com o tipo quando o
ticker repete, 21 pontos que agrupam/filtram por ticker (Dividendos, Transações, buscas locais) passam ao id, `AssetLogo`
pelo id/tipo, crédito "Logos by Logo.dev" (D13), Carteira/Alocação/Estratégia com os tipos novos (já existem na tela,
hoje vazios), posição pelo custo com o rótulo de "sem cotação" (como nas Metas, L2).

**Carga e scripts:** etapa nova no `bootstrap` (catálogo do exterior e deslistados), rotina no `admin.mjs`, espelho de
`carga/data/sec`, `carga/data/openfigi` e `carga/data/nareit` no bucket; `carga/scripts/primevest/importar_carteira.mjs` e
as conferências que procuram por ticker.

**Testes:** `SchemaTest`, `AssetRepositoryTest`, testes funcionais com o fictício `ZZUSD` (passa a ter homônimo cripto
para provar a D3), testes de importação B3/Binance com ticker repetido, testes do front (Vitest) dos filtros por id.

**Documentação:** `Claude.md` (tabelas, catálogo, integrações), `docs/PENDENCIAS.md` (29.7 e 38.1 continuam; lacunas da D2),
`README.md`.

### 10.4 Dependências

1. **Contas** (Guilherme): token do logo.dev (gratuito); chave do OpenFIGI (gratuita, opcional — sem ela, 25 consultas de
   10 papéis por minuto: ~40 minutos para 10 mil papéis; com ela, segundos).
2. **Rede** dos contêineres para `sec.gov`, `data.sec.gov`, `api.openfigi.com`, `reit.com` e `img.logo.dev` — a mesma da
   máquina do Guilherme, onde a prova rodou.
3. **Moedas** (entregue): ativo USD, câmbio e tributos já funcionam.
4. **Ordem**: a mudança da unicidade do ticker (D3) e o ajuste das 23 buscas vêm **antes** de qualquer ativo do exterior
   entrar; senão a primeira ação `META` quebra a ligação de emissores e confunde a importação da Binance.

### 10.5 Riscos e efeitos colaterais

| # | Risco | Efeito | Mitigação |
|---|---|---|---|
| R-1 | Regressão nas 23 buscas por ticker | Transação/importação no ativo errado | Busca por id ou por tipo; teste com homônimo `ZZUSD` |
| R-2 | `toMap` com chave repetida (`IssuerLinkService`) | Tarefa de emissores cai | Restringir aos tipos da B3 |
| R-3 | SEC atrasa ETFs novos (17% hoje) | ETF recém-lançado não aparece na busca | I1 |
| R-D2 | Ticker de deslistado antes de 2019 vem de texto; ETF deslistado sem prova | Ticker errado ou ausente | Planilha com candidatos; prova dos fundos da SEC no início da Implementation; o que sobrar vira pendência (D2) |
| R-5 | SEC recusa sem `User-Agent` com contato ou acima de 10/s | Rodada `FAILED` | Contato do `MAIL_FROM`, ritmo controlado, cache em disco |
| R-6 | Logo da ação sobrescreve o da cripto | Logo trocado na tela | I7 |
| R-7 | Metas com ticker | Meta aponta para o ativo errado | I4 |
| R-8 | Termos do Nasdaq Trader (uso pessoal) se ele virar fonte | Uso fora dos termos se o Activest abrir | I1 |
| R-9 | Volume: +~10 mil vivos +5–8 mil deslistados na tabela `assets` (hoje 57 mil com os fundos) | Busca sem índice (`LIKE '%…%'`) | Já convive com 55 mil fundos; medir no Testing |
| R-10 | Lançamento retroativo no nacional (D11) muda uma regra que já existe | Inativo da B3 volta a aceitar lançamento até a data de saída | É o pedido; documentar em `Claude.md` |

### 10.6 Decisões novas (Impact)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **I1** | Universo vivo: a SEC deixa de fora 17% dos ETFs (os mais novos) | (a) Nasdaq Trader como lista de descoberta (ticker novo entra pela lista da bolsa, confirmado no OpenFIGI) — revê a D15 e vale enquanto o uso for pessoal, com pendência se o Activest abrir; (b) só SEC: ETF novo entra com atraso, quando a SEC publicar; (c) SEC + o usuário pede a inclusão de um ticker, que o OpenFIGI confirma | **(a)** — a descoberta precisa de uma lista de bolsa; a pendência registra a troca caso o Activest abra |
| **I2** | Como a Nova transação identifica o ativo | (a) id do ativo escolhido na lista (ticker só para exibição); (b) ticker + tipo | **(a)** — o modal já escolhe o ativo de uma lista |
| **I3** | API que recebe ticker (`/assets/{ticker}`, logo, proventos) | (a) por id para uso geral, por ticker só nas telas da B3 (Ativos, Preço Teto), onde não há repetição; (b) ticker + tipo em todas | **(a)** |
| **I4** | Metas que guardam ticker (5 hoje) | (a) passam a guardar o id do ativo (a migration converte as 5); (b) ticker + tipo | **(a)** |
| **I5** | Data de saída do nacional (D11) | (a) as 9 inativas recebem o último pregão do COTAHIST; daqui para frente, inativar continua sendo decisão do administrador; (b) a conferência passa a inativar sozinha | *Revista em 10.7.3 e aprovada em 10.7.5: inativação automática por prova formal (a "regra" citada aqui não existia)* |
| **I6** | Chave estável do ETF (D9) | O CIK de ETF é do **trust** (o iShares Trust tem centenas de ETFs): (a) para ETF, gravar também o identificador de série da SEC (`S000…`); (b) só o compositeFIGI | **(a)** — o FIGI não existe para ETF deslistado |
| **I7** | Logos com ticker repetido | (a) logos do exterior numa subpasta por tipo (`logos/STOCK/META.png`), os atuais ficam onde estão; (b) renomear todos os arquivos para o id | **(a)** — não mexe nos 2.322 arquivos existentes |

### 10.7 Respostas do Guilherme (28/10/2026) e revisões

| # | Situação |
|---|---|
| **I1** | ✅ Aprovada — Nasdaq Trader como lista de descoberta, confirmado no OpenFIGI; pendência registrada (termos, abaixo) |
| **I7** | ✅ Aprovada — logos do exterior em subpasta por tipo |
| **I5 (1ª parte)** | ✅ Aprovada — as 9 inativas da B3 recebem a data do último pregão do COTAHIST |
| **I2, I3, I4** | 🔄 Revistas pela sugestão do Guilherme (tipo no payload e na rota) — ver 10.7.2 |
| **I5 (2ª parte)** | 🔄 Revista — o Guilherme quer a inativação **automática** — ver 10.7.3 |
| **I6** | ❓ Pergunta: onde grava — ver 10.7.4 |

#### 10.7.1 Termos do Nasdaq Trader (pergunta sobre a D15)

O texto dos termos ([Nasdaq Trader](http://www.nasdaqtrader.com/Trader.aspx?id=CopyDisclaimMain)): a cópia é permitida
"for personal and non-commercial use only"; o conteúdo "may not be copied, reproduced, transmitted, displayed […]
distributed"; "any other use […] requires the prior written consent of Nasdaq and may require a fee"; e "Nasdaq stock
symbols are proprietary to Nasdaq, Inc.". O que conta não é como cada usuário usa o Activest, e sim **quem baixa o
arquivo e para quem o mostra**: quem baixa é o Activest, e ele exibe a lista a cada usuário — isso é o Activest
distribuir o conteúdo a terceiros, mesmo que cada um faça autogestão individual. Enquanto o único usuário é o
Guilherme, cabe no "personal". Com outros usuários, o caminho é o consentimento por escrito da Nasdaq ou depender só da
SEC (aceitando o atraso dos ETFs novos). Os arquivos da SEC não têm essa restrição. (Leitura dos termos, não parecer
jurídico.) Vira pendência do escopo, ligada ao `SIGNUP_ENABLED`.

#### 10.7.2 Tipo no payload e na rota (sugestão do Guilherme para I2–I4) — avaliação

A sugestão faz sentido: se a unicidade é `(ticker, tipo)`, o par **é a chave natural** do ativo, e usá-lo deixa
payload, rota e log legíveis (`STOCK/META` diz o que é; `asset 18342` não diz). Também casa com o fluxo atual: a Nova
transação já escolhe o tipo antes do ativo (`TransactionCreateDTO`: "o usuário escolhe o TIPO de ativo, que restringe o
autocomplete"), e cada importação já sabe o tipo da fonte (B3 → tipos da B3; Binance → `CRIPTO`). E é o mesmo desenho
da I7 (`logos/STOCK/META.png`).

O único ponto em que o id é melhor é a **referência guardada**: ticker muda (FB → META, D9) e o catálogo renomeia o
ativo; o que estiver gravado como `STOCK/FB` deixa de achar o ativo, o que estiver gravado como id continua certo. Hoje
isso só acontece nas **metas** (as transações e as posições já apontam para o ativo pelo id).

Recomendação revista:

| Onde | Como identifica | Exemplo |
|---|---|---|
| **Payloads** (Nova transação, importações, filtros) | **ticker + nome do tipo** | `{"ticker": "META", "assetType": "STOCK", …}` |
| **Rotas da API** genéricas (ativo, logo, proventos) | **um nível com o tipo**, como na I7 | `/api/v1/assets/STOCK/META`, `/api/v1/assets/CRIPTO/META/logo` |
| Rotas e telas da B3 (Ativos, Preço Teto) | Continuam só com o ticker (não há repetição entre os tipos da B3); o endereço publicado não muda (REGRAS 9.4) | `/assets/PETR4` |
| **Referência gravada** (metas) | **id do ativo** (a migration converte as 5 metas), exibida como ticker + tipo | — |

Sobre o nome do tipo na rota: é o `asset_types.name` como está (`ACAO`, `STOCK`, `CRIPTO`) — valor de registro, que
pela REGRAS 9.2 trafega como está, sem tradução; a rota aceita maiúsculas e minúsculas.

#### 10.7.3 Inativação automática (I5, 2ª parte) — como o mercado formaliza a saída

**"A regra atual do nacional"** é a decisão **B23** do plano da BRAPI (24/09/2026): a conferência do catálogo "não
grava nada no catálogo: cada diferença vira uma linha no log", porque a central de avisos ainda não existia (B26). Não
é regra de negócio: foi provisória, e desde 13/10 os avisos existem (Notificações). Pode ser revista aqui.

Como a saída é formalizada, nas fontes que o Activest já usa ou vai usar:

| Mercado | Documento formal | Onde está | O que traz |
|---|---|---|---|
| Brasil — companhia | **Cancelamento de registro na CVM** | `cad_cia_aberta.csv` (já baixado em `carga/data/cvm/cad`) | `SIT = CANCELADA`, `DT_CANCEL`, `MOTIVO_CANCEL`. Desde 2010: 666 cancelamentos — voluntário 401, de ofício 137, incorporação 63, **migração de registro (Res. 60) 47 — que não é saída de bolsa** |
| Brasil — ticker (uma classe, FII, ETF, BDR) | O ticker deixa de ser negociado | COTAHIST diário (já baixado) e a lista de tickers da BRAPI (conferência) | Último pregão com negócio |
| EUA | **Form 25 / 25-NSE** (a bolsa comunica a retirada à SEC; vale 10 dias depois) | Índice do EDGAR (`form.idx`) | Empresa (CIK), bolsa, classe, data |
| EUA — ticker | O ticker some da lista da bolsa | Nasdaq Trader (I1) | Dia em que sumiu |

Proposta (I5 revista): a tarefa **inativa sozinha** quando houver **prova formal** — CVM cancelada (menos "migração de
registro") ou Form 25 do papel — **ou** quando o ticker sumiu da lista oficial **e** está sem negócio no COTAHIST há N
pregões (sugestão: 30) sem ser renomeação. Data de saída: último pregão (Brasil) ou data efetiva do Form 25 (EUA).
Suspensão de negociação (ex.: "SUSPENSO(A) – DECISÃO ADM", 8 hoje) **não** inativa. O administrador recebe aviso do que
foi inativado, com a prova; continua podendo desfazer (curadoria, nunca sobrescrita).

#### 10.7.4 Onde gravar o identificador de série do ETF (I6)

No **cadastro do ativo** (`assets`), junto das colunas da D9: `cik` (empresa ou trust na SEC), `composite_figi`
(papel na Bloomberg/OpenFIGI) e `sec_series_id` (série do fundo na SEC, `S000…`, só ETF). O ETF fica identificado por
**série** (única por ETF, viva ou deslistada); a ação, por CIK + compositeFIGI. Alternativa: uma tabela de
identificadores (`ativo, esquema, valor`), mais flexível para ISIN/CUSIP no futuro, mas um JOIN a mais em toda
conciliação. Recomendação: **colunas no `assets`**, como o `isin`, que já está lá.

#### 10.7.5 Respostas do Guilherme (28/10/2026, segunda rodada)

| # | Situação |
|---|---|
| **I2, I3, I4** | ✅ Aprovadas como em 10.7.2: payload com ticker + nome do tipo; rotas genéricas com um nível do tipo (`/api/v1/assets/STOCK/META`); telas da B3 só com o ticker; metas guardam o id |
| **I5 (2ª parte)** | ✅ Aprovada como em 10.7.3: inativação **automática** por prova formal (CVM cancelada, menos migração de registro; Form 25) ou por sumiço da lista + 30 pregões sem negócio, sem ser renomeação; suspensão não inativa; aviso ao administrador com a prova, que pode desfazer |
| **"Não grava nada no catálogo"** | O Guilherme: **essa regra não existe — apagar.** Feito na documentação (28/10/2026): `PLANO_BRAPI.md` (B23 e F3, marcados como revogados) e `Claude.md` (linha da `CATALOG_AUDIT`). O mesmo texto está no **código** e no seed — Javadoc do `CatalogAuditService`, `ClosingPriceService`, `BrApiHistory`, descrição da tarefa `CATALOG_AUDIT` na V2 ("Não altera o catálogo") e o teste `renomeNaoTroca` em `docs/TESTES_FUNCIONAIS.md` —, que muda na Implementation (etapas 1–3 não alteram código; a descrição da tarefa sai por migration nova, já que migration aplicada não se edita) |
| **I6** | 🔄 Revista pela pergunta do Guilherme — tabela própria em vez de colunas no `assets` (10.7.6) |

#### 10.7.6 I6 revista — colunas de um tipo só no `assets` (conferido no banco, 28/10/2026)

Uso de cada coluna do `assets` por tipo de ativo (linhas preenchidas):

| Coluna | Usada por | Situação |
|---|---|---|
| `ticker`, `name`, `active`, `asset_type_id`, `asset_category_id`, `currency_code` | todos | Genéricas |
| `current_price`, `price_updated_at` | quem tem fonte de preço | Genéricas |
| `isin` | Ação 795, FII 347, ETF 159, BDR 892 | Só os tipos da B3 |
| `issuer_id`, `issuer_link_source` | Ação 792, FII 347 | Só Ação e FII (emissor da CVM) |
| `share_class` | Ação 779 | **Só Ação** |
| `price_source_ref` | Cripto 227 (id da CoinGecko) | **Só Cripto** |
| `reference_currency_code` | Cripto 2 (stablecoins) | **Só Cripto** |
| `country_code` | ninguém (nasceu na V9 para o exterior) | Genérica, ainda vazia |

E o banco já tem o outro padrão, **tabela 1:1 com o `assets` para os dados de um tipo**: `asset_terms` (Tesouro Direto,
130 linhas) e `investment_funds` (fundos, chave = `asset_id`). Ou seja, hoje convivem os dois jeitos.

Opções para os dados do exterior (bolsa, CIK, compositeFIGI, série da SEC, tipo do OpenFIGI, origem da classificação):

| Opção | Prós | Contras |
|---|---|---|
| (a) Colunas no `assets` | Sem JOIN | Mais 5–6 colunas vazias em ~57 mil linhas que não são do exterior — o problema que levou a `asset_terms` |
| (b) **Tabela 1:1** própria (chave = `asset_id`), como `investment_funds` | Colunas tipadas, `NOT NULL`/`UNIQUE` onde cabe (CIK + série, compositeFIGI), mesmo padrão dos fundos e do Tesouro | Um JOIN quando precisa desses dados (só a tarefa do catálogo e a conciliação) |
| (c) Tabela genérica de atributos (`ativo, chave, valor`) | Atributo novo sem migration | Sem tipo, sem unicidade por identificador, consultas mais difíceis; é o que se usa para atributos de tarefa (`task_attributes`), não para dado de catálogo que precisa ser único e casado |

Recomendação: **(b)**, tabela 1:1 dos papéis listados no exterior. A **data de saída** (D11) fica no `assets`, porque
vale também para o nacional. Mover as colunas de um tipo só que já existem (`share_class`, `price_source_ref`,
`reference_currency_code`, emissor) para tabelas próprias é arrumação fora do escopo: se o Guilherme quiser, vira
pendência.

#### 10.7.7 Fechamento do Impact (28/10/2026)

| # | Situação |
|---|---|
| **I6** | ✅ Aprovada — **tabela 1:1** dos papéis do exterior (padrão de `investment_funds`); data de saída no `assets` |
| Arrumação das colunas de um tipo só | Registrada como pendência a pedido do Guilherme: `docs/PENDENCIAS.md` **41.1** |

**Impact encerrado** (I1–I7 aprovadas). O Guilherme mandou seguir para o Planning.

---

## 11. Planning — solução técnica (28/10/2026)

Nomes novos em inglês (REGRAS 9); valores de registro como os demais (`STOCK`, `CATALOGO_*`). Nenhum código alterado
até a aprovação explícita (REGRAS 2.2).

### 11.1 Modelo de dados — `V13__foreign_assets.sql`

| Objeto | Mudança |
|---|---|
| `assets` | `uk_assets_ticker` → **`uk_assets_ticker_type UNIQUE (ticker, asset_type_id)`** (D3); a regra para ticker reaproveitado é a **P2** |
| `assets` | `delisted_on DATE` (data de saída, D11) e `delisting_source VARCHAR(12)` (`CVM`, `FORM_25`, `LIST_GONE`, `MANUAL`) — a prova da inativação (I5). `CHECK (delisted_on IS NULL OR active = false)`. Nulas: nenhum default a deduzir (REGRAS 8.1); as 9 inativas da B3 recebem a data na primeira rodada da conferência (I5), não na migration |
| **`foreign_listings`** (nova, 1:1, PK `asset_id` → `assets` `ON DELETE CASCADE`) | `exchange` (`NYSE`, `NASDAQ`, `NYSE_AMERICAN`, `NYSE_ARCA`, `CBOE`, `IEX`; nula no deslistado sem bolsa conhecida), `cik VARCHAR(10)`, `sec_series_id VARCHAR(10)` (só ETF, I6), `composite_figi VARCHAR(12)`, `figi_security_type VARCHAR(40)` (o tipo do OpenFIGI), `sic_code VARCHAR(4)`, `type_source` (`OPENFIGI`, `SEC_NAREIT`, `CURATION`), `category_source` (`BDR`, `SIC`, `NAREIT`, `ETF_RULE`, `CURATION`), `ticker_source` (`LIST`, `XBRL`, `NAREIT`, `FILING_TEXT`, `FUND_SERIES`, `CURATION`), `first_seen_on`, `last_seen_on`. Únicos: `composite_figi` (quando houver) e `sec_series_id` (quando houver) |
| `asset_categories` | Seed das categorias (D6–D8), `ON CONFLICT DO UPDATE` como a V2: **STOCK** — os 19 setores dos BDRs (os 20 menos "ETFs no exterior"); **REIT** — Residencial, Industrial/logística, Escritórios, Varejo, Data centers, Torres/telecom, Saúde, Self storage, Hotéis/lazer, Diversificados, Especiais (gaming, timber, outros), Hipotecas (mREIT) — 12; **ETF_INTERNACIONAL** — Ações EUA, Ações internacionais, Renda fixa, Imobiliário, Ouro/commodities, Cripto, Dividendos/renda, Alavancados/inversos, Outros/estratégia — 9 |
| `goals.params` | As metas com `ticker` passam a `assetId` (I4): conversão pelo ticker, que hoje é único — feita **antes** de trocar a unicidade, na mesma migration |
| `task_types`, `tasks`, atributos | Tipos e tarefas `FOREIGN_CATALOG` e `FOREIGN_DELISTED` (11.4); descrição da `CATALOG_AUDIT` sem o "Não altera o catálogo" (UPDATE na V13 — migration aplicada não se edita) |

### 11.2 Regras

| # | Regra |
|---|---|
| **C1** — Universo vivo | Tickers da lista do Nasdaq Trader (I1), sem test issue, nas bolsas de D1. Cada um vai ao OpenFIGI (`TICKER` + `exchCode: US`, classe no formato Bloomberg `BRK/B`): sem resposta, fica fora (aparece na planilha, aba Excluídos) |
| **C2** — Tipo (D4, D5) | Pelo `securityType` do OpenFIGI: `Common Stock`, `ADR`, `MLP`, `Ltd Part`, `Royalty Trst`, `Tracking Stk`, `NY Reg Shrs`, `GDR`, `Preference` → `STOCK`; `REIT` → `REIT`; `ETP` → `ETF_INTERNACIONAL`; `Closed-End Fund` → **P1**; `Equity WRT`, `Right`, `Unit` (SPAC) → fora. Nareit e SEC (SIC 6798) só conferem: divergência na aba "Divergências REIT" |
| **C3** — Ticker (D10) | Classe com ponto (`BRK.B`), preferencial com hífen e a série (`ABR-E`). A busca aceita `BRK-B`, `BRK/B`, `ABRpE`, `ABR$E` |
| **C4** — Categoria (D6–D8) | STOCK: categoria do BDR da mesma empresa (casada por nome normalizado e conferida na planilha) → tabela de equivalência SIC → categoria (arquivo versionado, revisado na planilha) → curadoria. REIT: setor da Nareit do ano mais recente em que aparece → grupo (12) → curadoria. ETF: regra por palavras do nome, em ordem (alavancado/inverso primeiro; depois cripto, ouro/commodities, renda fixa, imobiliário, dividendos, internacionais, EUA; o resto em Outros) → curadoria |
| **C5** — Identidade e renomeação (D9, I6) | O ativo vivo é casado pelo `composite_figi`; ETF também pela `sec_series_id`; ação também pelo `cik`. Mesmo FIGI com ticker novo = **renomeação do mesmo ativo** (`assets.ticker` troca) + aviso `CATALOGO_TICKER_RENOMEADO` |
| **C6** — Deslistados 2010+ (D2) | Form 25/25-NSE do índice do EDGAR desde 2010T1, só as classes de capital (ação, ADS, units de MLP, cotas de fundo/ETF — pelo texto da classe). Ticker, nesta ordem: `dei:TradingSymbol` dos dois últimos 10-K/10-Q/20-F antes da saída → PDFs da Nareit (REIT) → texto do relatório ("under the symbol…") → fundos da SEC (Series & Class 2010+, N-CEN 2018+) para ETF → curadoria. Data de saída: efetivação do Form 25 (10 dias após o arquivamento, ou a do anexo). Tipo: SIC 6798 **e** Nareit do ano → `REIT` (D5); fundo → `ETF_INTERNACIONAL`; resto → `STOCK`. O que não fechar fica na planilha e, no fim, vira pendência (D2) |
| **C7** — Inativação automática (I5) | **Brasil** (na `CATALOG_AUDIT`): emissor com `SIT = CANCELADA` na CVM, menos "Migração de Registro" → inativa os tickers dele; ticker fora da lista da BRAPI **e** 30 pregões sem negócio no COTAHIST, sem ser renomeação → inativa. Data: último pregão no COTAHIST. **EUA** (na `FOREIGN_CATALOG`): Form 25 do papel, ou ausente da lista há 30 dias sem renomeação. Suspensão não inativa. Aviso novo ao administrador `CATALOGO_TICKER_INATIVADO`, com a prova; desfazer pela API de curadoria (11.5) grava `MANUAL` e a tarefa não refaz |
| **C8** — Busca e lançamento (D11) | Só o autocomplete da Nova transação traz inativo, com `delistedOn`; transação com data **depois** da saída é recusada (422), no nacional e no exterior |
| **C9** — Curadoria (D12, R4) | Planilha gerada pela tarefa (abas: Stocks, REITs, ETFs, Deslistados, Categorias, Colisões, Divergências REIT, Excluídos, e colunas "Curadoria" para tipo, categoria, ticker e exclusão). O Guilherme edita; um script converte para `carga/data/levantamento/internacional/curadoria.json` (padrão do levantamento das datas de pagamento); a tarefa aplica com origem `CURATION`, que nunca é sobrescrita |
| **C10** — Logos (D13, I7) | `carga/data/logos/{TIPO}/{TICKER}.{ext}` para os três tipos: primeiro o arquivo do BDR da mesma empresa, depois o logo.dev (token do `.env`); os 2.322 atuais ficam na raiz. **Baixados pela API de self-hosting do logo.dev** (`GET /v2/brands/logo`, chave secreta no servidor; o documento mostra a busca por **domínio** da empresa — busca por ticker não confirmada, a provar na F6 com a chave; domínio pelo site da empresa no cadastro da SEC) e servidos do disco pela app-api, como os logos de hoje (o arquivo é o estado; a tarefa `ASSET_LOGOS` só busca quem não tem arquivo) — o navegador nunca chama o logo.dev; guardar é permitido em todos os planos ("Every plan can self-host the images that the Logo.dev API returns"), guardando o `etag` e baixando de novo só quando ele muda. Guardar não dispensa o crédito do plano gratuito em projeto comercial (P4) |
| **C11** — Preço | Fora (seção 2): os três tipos seguem `MANUAL`; posição pelo custo, com o rótulo "sem cotação" já usado nas Metas |

### 11.3 Fontes, cache e configuração

| Fonte | Cache em `carga/data` (espelhado no bucket) | Configuração |
|---|---|---|
| Nasdaq Trader | `nasdaqtrader/{data}.txt` — uma fotografia por rodada (vira histórico próprio de quem sumiu) | `nasdaqtrader.url` |
| SEC | `sec/full-index/{ano}/QTR{n}/form.idx`, `sec/submissions/`, `sec/filings/{cik}/{acesso}/`, `sec/funds/` (Series & Class, N-CEN); arquivo em disco não é baixado de novo | `sec.base-url`; `User-Agent` "Activest/1.0 <contato>" com o e-mail do `MAIL_FROM`; ritmo ≤ 8 req/s |
| OpenFIGI | `openfigi/{figi}.json` | `OPENFIGI_API_KEY` (opcional) |
| Nareit | `nareit/FNUSIC{ano}.pdf` (2010–2026), lido com o PDFBox | `nareit.url` |
| logo.dev | `logos/{TIPO}/` | `LOGO_DEV_TOKEN` |

### 11.4 Tarefas (adm-api)

| Tarefa | Quando | O que faz |
|---|---|---|
| `FOREIGN_CATALOG` | **P3** (proposta: segunda, 06:30) + Run | C1–C5, C7 (EUA), C9, C10; atributo `WRITE` (BOOLEAN, padrão falso nas primeiras rodadas: gera a planilha sem gravar — o portão da D12) |
| `FOREIGN_DELISTED` | Run manual e etapa nova do `bootstrap` | C6 de `FROM_YEAR` (2010) a `TO_YEAR`; mesma planilha e o mesmo `WRITE`. A partir daí os Form 25 novos entram pela `FOREIGN_CATALOG` |
| `CATALOG_AUDIT` (já existe) | segunda, 07:00 | Passa a inativar (C7, Brasil) e a preencher a data das 9 inativas |

### 11.5 API

| Serviço | Mudança |
|---|---|
| app-api — transação | `TransactionCreateDTO` + `assetType` (nome do tipo, I2). Sem tipo: aceito só se o ticker for único no catálogo; senão 400 "informe o tipo" |
| app-api — ativos | `GET /api/v1/assets/{type}/{ticker}`, `/{type}/{ticker}/logo`, `/{type}/{ticker}/prices` (I3); `/assets/{ticker}` continua para o ticker único (telas da B3); `AssetResponseDTO` + `delistedOn`; busca da Nova transação com inativos (`includeDelisted=true`) |
| app-api — metas | `params.assetId` no lugar de `params.ticker` (I4); a tela continua mostrando ticker + tipo |
| app-api — integrações | Busca do ativo pelo par (ticker, tipos da fonte): B3 → Ação/FII/ETF/BDR; Binance → Cripto |
| adm-api — curadoria | `PUT /api/v1/admin/assets/{id}/activation` (`{active, delistedOn}`, grava `MANUAL` — desfazer a C7) |
| adm-api — catálogo | `IssuerLinkService`, COTAHIST e conferência da B3 com os mapas restritos aos tipos da B3 (R-2) |

### 11.6 Front

- Nova transação: envia `assetType`; autocomplete com a marca "deslistado em dd/mm/aaaa" e a data limitada à saída.
- Rótulo com o tipo quando o ticker se repete (`assetLabel`); os 21 pontos que agrupam/filtram por ticker passam a
  usar tipo + ticker.
- `AssetLogo` por tipo + ticker; crédito do logo.dev (P4).
- Carteira, Alocação e Estratégia: os três tipos já existem nas telas; posição pelo custo com "sem cotação".
- Notificações: o aviso `CATALOGO_TICKER_INATIVADO` com a prova e o atalho para desfazer.

### 11.7 Etapas de implementação (cada uma fechada — implementada, testada, bateria verde — antes da próxima, REGRAS 2.3)

| # | Etapa | Entrega | Prova |
|---|---|---|---|
| **F1** | Ticker por tipo (D3, I2–I4, R-1, R-2) | Unicidade `(ticker, tipo)`, 23 buscas, integrações por tipo da fonte, metas por id, API e front com o tipo, logos por subpasta (só a leitura) | Teste com homônimo: `ZZUSD` ação e `ZZUSD` cripto; importação Binance acha a cripto; ligação de emissores roda |
| **F2** | Data de saída e inativação no nacional (D11, I5) | `delisted_on`, busca da Nova transação com inativo, recusa depois da saída, `CATALOG_AUDIT` inativando (CVM, COTAHIST), data das 9, aviso e desfazer | Lançamento retroativo numa das 9; nada inativado por suspensão |
| **F3** | Estrutura do exterior | `foreign_listings`, categorias, tarefas, configuração, clientes SEC/OpenFIGI/Nasdaq Trader/Nareit com cache | Testes dos clientes com arquivos reais gravados |
| **F4** | Catálogo vivo (C1–C5, C7 EUA, C9) | `FOREIGN_CATALOG` com `WRITE=false` → **planilha** para o Guilherme → curadoria → `WRITE=true` | Planilha revisada; ~10 mil papéis gravados; renomeação por FIGI |
| **F5** | Deslistados 2010+ (C6) | Prova das fontes de fundos (ETF deslistado) primeiro; `FOREIGN_DELISTED` → planilha → curadoria → grava | Cobertura por ano e tipo na planilha; lacunas para a pendência |
| **F6** | Logos (C10) | Cópia dos BDRs + logo.dev, crédito na tela | Logos sem sobrescrever os das criptos |
| **F7** | Carga, documentação e conferência | `bootstrap`, `admin.mjs`, bucket, `Claude.md`, `PENDENCIAS`, `README`; Testing (bateria + lint + navegador, nos dois temas — REGRAS 4.3), Review, Commit sugerido | Roteiro de conferência no navegador |

### 11.8 Critérios de aceitação

1. `META` cripto e `META` ação convivem: busca, transação, importação da Binance, metas e logos acham cada um.
2. As 23 buscas por ticker e os mapas da adm-api não quebram com ticker repetido (teste com homônimo).
3. Catálogo vivo dos EUA com Stock, REIT e ETF, cada um com categoria, bolsa e FIGI (CIK na ação, série no ETF), sem
   warrant, right, unit de SPAC e test issue.
4. REIT pela regra da D5 (Prologis, Annaly = REIT; CoreCivic = Stock).
5. Renomeação por FIGI troca o ticker do mesmo ativo e avisa o administrador.
6. Deslistados 2010+ cadastrados inativos, com ticker, tipo, categoria e data de saída; cobertura medida; lacunas na
   pendência do escopo.
7. Inativação automática no nacional e no exterior só com prova formal ou sumiço + 30 pregões/dias; suspensão não
   inativa; o administrador desfaz.
8. Ativo deslistado aparece só na Nova transação, com a marca e a data, e recusa lançamento depois da saída.
9. Curadoria da planilha aplicada e nunca sobrescrita pela tarefa.
10. Logos do exterior por tipo, sem sobrescrever os atuais; crédito do logo.dev visível.
11. Carteira/Alocação/Estratégia mostram os três tipos pelo custo, com "sem cotação"; câmbio pela PTAX (já existente).
12. Bateria verde (backend, front, lint) e conferência no navegador nos dois temas.

### 11.9 Quem faz

| O quê | Quem |
|---|---|
| Implementação, testes, carga, documentação, roteiro de conferência | Claude |
| Chave do OpenFIGI no `.env` — ✅ feito em 28/10/2026. Logos do exterior: o Guilherme busca e coloca os arquivos em `carga/data/logos/{TIPO}/`, a partir do CSV (11.12) | Guilherme |
| Revisar a planilha (F4 e F5) e preencher a curadoria | Guilherme |
| Conferência no navegador (caminho 3) | Guilherme, com o roteiro |
| Commit e PR | Guilherme (REGRAS 3.1) |

### 11.10 Decisões do Planning

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **P1** | Fundos fechados listados (closed-end funds — ~400, ex.: PIMCO PDI, Nuveen): não foram discutidos na D4. São fundos com cotas negociadas em bolsa, como o FII no Brasil | (a) `ETF_INTERNACIONAL`, nas categorias de tema (renda fixa, ações…); (b) `STOCK`; (c) fora | **(a)** — o tema descreve o que o fundo tem; o setor (Stock) não se aplica a fundo |
| **P2** | Ticker reaproveitado **no mesmo tipo** (esclarecido em 28/10/2026: sim, é a data da transação que decide entre o deslistado e o vivo — data até a saída do antigo → o antigo; depois → o vivo; a Nova transação mostra os dois, o antigo com "deslistado em …") (ação deslistada em 2015 e outra ação nova com o mesmo ticker) — a unicidade `(ticker, tipo)` não deixaria cadastrar a deslistada | (a) unicidade só entre os **ativos vivos** (índice parcial); os deslistados com o ticker que tinham; a transação com ticker + tipo é resolvida pela data (o papel que estava listado naquele dia), e a rota `/assets/STOCK/XYZ` devolve o vivo (ou, sem vivo, o de saída mais recente); (b) o deslistado ganha sufixo no ticker (`XYZ.2015`); (c) unicidade total e o deslistado repetido fica de fora | **(a)** — mantém o ticker real e a D2; a data da transação desfaz a dúvida |
| **P3** | Horário da `FOREIGN_CATALOG` | (a) segunda, 06:30 (antes da conferência da B3, 07:00); (b) diária | **(a)** — D11 pediu semanal; a lista da bolsa muda pouco por semana |
| **P4** | Crédito do logo.dev (revista em 28/10/2026, com a regra de atribuição lida — [logo.dev](https://www.logo.dev/docs/platform/attribution)) | A atribuição só é exigida de projeto **comercial** no plano gratuito; "a project is commercial if a company or an organization owns it" — inclusive ferramenta interna e projeto sem receita. O link "Logos provided by Logo.dev" tem de estar numa página **aberta sem login** e visível (rodapé, página "sobre"); tela atrás do login não vale. Opções: (a) se o Activest for da empresa: o link no rodapé da **tela de login** do Activest; (b) plano pago (US$ 33/mês), sem link; (c) se for projeto pessoal seu: sem link | Depende de quem é dono do Activest; sendo da empresa, **(a)** |

### 11.11 Passo a passo das contas (Guilherme)

Só são usadas a partir da F4 (OpenFIGI) e da F6 (logo.dev); F1–F3 não precisam delas. As chaves ficam só no `.env`
(REGRAS 6.5) — não vão para o chat nem para o git; o `.env.example` passa a documentar as duas variáveis na
Implementation.

**logo.dev (chave secreta — corrigido em 28/10/2026):**
1. Criar conta gratuita (plano Community) em https://www.logo.dev.
2. No painel da conta, copiar a **chave secreta** (`sk_…`). *Correção:* a primeira versão deste passo pedia a chave
   publicável; mas guardar os logos exige baixá-los pela API `GET /v2/brands/logo` com a chave secreta, do servidor —
   baixar do CDN de imagens por script "is not fair use" ([self-hosting](https://www.logo.dev/docs/platform/self-hosting),
   [fair use](https://www.logo.dev/docs/platform/fair-use)).
3. No `E:\repos\activest\.env`, acrescentar: `LOGO_DEV_SECRET_KEY=<a chave secreta>`.

**OpenFIGI (opcional):**
1. Em https://www.openfigi.com/api, botão "API KEY SIGN UP" (cadastro em `openfigi.com/user/signup`); é gratuita e sem
   limite diário, semanal ou mensal.
2. Na área da conta, copiar a API key.
3. No `.env`, acrescentar: `OPENFIGI_API_KEY=<a chave>`.

Sem a chave do OpenFIGI tudo funciona, só mais devagar: a primeira carga dos ~10 mil papéis leva ~40 minutos em vez de
segundos (as rodadas semanais são pequenas).

**Quantos logos (P4):** ~10 mil papéis vivos (≈5,9 mil ações/ADRs/preferenciais — a preferencial usa o logo da empresa
— e ≈5,8 mil ETFs); ~440 já vêm dos BDRs (362 de ação, 81 de ETF). Ficam ~9,5 mil buscas no logo.dev **uma vez** e, depois,
algumas dezenas por semana (tickers novos) — dentro das 500 mil por mês do plano gratuito. Deslistados **sem logo** (as
iniciais, como hoje): o ticker pode ter sido reaproveitado e o logo.dev devolveria o logo da empresa nova (o `FB`
hoje é um ETF).

**Manutenção dos logos (pergunta do Guilherme, 28/10/2026):** a busca de logos **novos** depende de continuar num plano
do logo.dev — tickers novos toda semana (dezenas) e a troca de logo de uma empresa (pelo `etag`). Sem plano: os
arquivos já baixados continuam no disco (os termos não dizem se podem ser mantidos depois de cancelar — perguntar ao
suporte se essa opção for considerada) e os papéis novos ficam com as iniciais, como já acontece com quem não tem logo;
nada quebra. Opções para a P4: (a) plano gratuito contínuo, com o link na tela de login se o Activest for da empresa;
(b) plano pago contínuo (US$ 33/mês), sem link; (c) usar só para a carga inicial e depois parar — depende da resposta
do suporte sobre manter os arquivos.

### 11.12 Respostas do Guilherme (28/10/2026)

| # | Situação |
|---|---|
| **P1** | ✅ Fundos fechados listados → `ETF_INTERNACIONAL`, nas categorias de tema |
| **P2** | ✅ Esclarecida e aceita: unicidade `(ticker, tipo)` entre os vivos; a data da transação decide entre o deslistado e o vivo |
| **P3** | ✅ `FOREIGN_CATALOG` às segundas, 06:30 |
| **P4** | 🔄 **Abordagem trocada pelo Guilherme:** ele mesmo busca os logos. O Activest **não** integra com o logo.dev (sai a `LOGO_DEV_SECRET_KEY`, o crédito na tela de login e a parte do logo.dev da C10/F6). Entrega: `docs/internacional/ativos_internacionais.csv` com `ticker,tipo,nome,bolsa` dos papéis vivos (D1, D4, C1–C3, P1) |

Consequência na C10/F6: os logos do exterior entram como **arquivos** colocados pelo Guilherme em
`carga/data/logos/{TIPO}/{TICKER}.{ext}` (I7) — o mesmo modelo de hoje, em que o arquivo é o estado; a cópia dos logos dos
BDRs da mesma empresa continua automática. Papel sem arquivo mostra as iniciais.

**O CSV gerado (28/10/2026):** `docs/internacional/ativos_internacionais.csv`, **12.193 papéis vivos** — `ETF_INTERNACIONAL`
6.131 (5.762 pela marca de ETF da bolsa, 352 fundos fechados pela P1, 17 ETP), `STOCK` 5.879 (inclui 375 ADRs, 24 MLPs e
~490 preferenciais), `REIT` 183 (OpenFIGI). Bolsas: NASDAQ 4.831, NYSE Arca 2.746, NYSE 2.649, Cboe BZX 1.677, NYSE
American 290. Fora: 463 warrants, 247 units de SPAC, 141 rights, 170 notas/títulos de dívida, 13 papéis estruturados
(corporate/tangible equity units, ZONES, títulos da TVA) e 1 warrant pelo OpenFIGI. As preferenciais não foram achadas
no OpenFIGI pelo formato do ticker e ficaram `STOCK` pelo nome (D4: preferencial é ação) — o formato da preferencial no
OpenFIGI é conferido na F4. Ticker no formato da C3 (`BRK.B`, `ABR-E`). É uma fotografia de 28/10/2026, não substitui a
tarefa da F4. A chave do OpenFIGI foi colocada no `.env` pelo Guilherme no mesmo dia (`OPENFIGI_API_KEY`).

**Logos colocados pelo Guilherme (29/10/2026)** em `carga/data/logos/{ETF_INTERNACIONAL,STOCK,REIT}/` — conferido por
conteúdo (hash) contra o CSV:

| Tipo | Arquivos | Imagens distintas | Do CSV sem logo | Observação |
|---|---|---|---|---|
| ETF_INTERNACIONAL | 5.150 (40 MB) | **614** | 981 | O logo do ETF é o da **gestora**: iShares 433 tickers com o mesmo arquivo, First Trust 266, Invesco 253, State Street/SPDR 182, ProShares 160, Direxion 127, Innovator 125, Vanguard 108… |
| STOCK | 5.260 (48 MB) | 5.058 | 619 | Repetição legítima: grupo econômico (Brookfield: BAM, BEP, BIP…) e preferenciais da mesma empresa (AGNC, Brighthouse, CHS). **38 tickers em 5 grupos** com a mesma imagem para empresas sem relação (SPACs e small caps) — imagem genérica da fonte, não logo |
| REIT | 182 (1,4 MB) | 179 | 1 | NexPoint (3) e Blackstone (2), mesmo grupo |

Regras para a F6 (C10): (1) o arquivo do Guilherme vale; a cópia do logo do BDR da mesma empresa só preenche quem não
tem arquivo (o "BDR → ativo internacional" continua); (2) a imagem genérica (mesmo arquivo em 3+ empresas sem relação)
é tratada como **sem logo** (iniciais) — L1; (3) ~313 ETFs têm o logo de uma gestora diferente da do nome — parte é só
grafia (FT/First Trust, SPDR/State Street, REX/T-REX) ou gestora correta (Tri-Continental é da Columbia), parte parece
logo de dono anterior do ticker (FNG "Leverage Shares" e FLRT "Pacer" com o logo da AdvisorShares) — vão para a aba
"Logos a conferir" da planilha (D12) — L2.

**P4 — diretrizes do Guilherme para os logos do exterior (29/10/2026), aplicadas em ordem; cada ativo resolvido numa
etapa não segue para a próxima.** Os arquivos baixados por ele têm backup; manipular é autorizado.

1. **BDR prevalece:** ativo do exterior que corresponde a um BDR (mesma empresa ou mesmo ETF) recebe o logo do BDR,
   **substituindo** o arquivo existente (o arquivo antigo do mesmo ticker é removido; a extensão passa a ser a do BDR).
2. **Imagens genéricas** (mesmo arquivo em 3+ empresas sem relação; 38 tickers) são **apagadas**, se não foram
   substituídas na etapa 1.
3. **Logo de outra gestora** (ETF com o logo de uma gestora diferente da do nome, descontadas as grafias e os casos
   corretos) é **substituído** pelo logo da gestora certa.
4. **Sem logo:** ETF cuja gestora é identificável e tem logo no acervo recebe o logo da gestora.
5. **O que sobrar fica sem logo** (iniciais).

Cada ação vai para um relatório (`docs/internacional/logos_relatorio.csv`: ticker, tipo, etapa, ação, origem). Aguarda a
ordem do Guilherme.

Complementos (29/10/2026): **etapa 4 vale também para ação** — papel sem logo recebe o da própria empresa quando outro
papel dela tem (preferencial da JPM → logo da JPM). **Etapa 3, gestora sem logo no acervo:** gestora identificada pelo
nome e pelo cadastro de fundos da SEC (Series & Class 2026, `Entity Name`, descontados os trusts de aluguel — Tidal, ETF
Series Solutions…); a lista está em `docs/internacional/logos_gestoras_faltantes.csv` (151 gestoras, 323 ETFs — 238 com
logo de outra gestora e 981 sem logo foram analisados; 84 e 812 deles se resolvem com logos que já estão no acervo). As
gestoras conhecidas da lista que o Guilherme baixar entram na etapa 3/4; o que não vier: o logo errado é apagado e o
ETF fica sem logo (etapa 5).

**Simulação (29/10/2026, nada alterado)** — com os 17 logos de gestora que o Guilherme colocou em
`carga/data/logos/ETF_INTERNACIONAL/logos_gestoras/`; prévia em `docs/internacional/logos_relatorio_previa.csv`:

| Etapa | Resultado |
|---|---|
| 1 — BDR | 587 substituídos + 2 criados. **191 dos 836 logos de BDR são a imagem genérica da BRAPI** (o mesmo arquivo em empresas sem relação) e foram descartados — senão trocariam logos bons por genéricos. Preferenciais não entram nesta etapa (o nome não é o da empresa) |
| 2 — genéricas | 38 apagados |
| 3 — gestora errada | 64 substituídos pelo logo da gestora certa; **98 mantidos "a conferir"**: só se troca quando a gestora certa é identificada com segurança (marca conhecida no nome, cadastro da SEC com o nome do fundo batendo — o cadastro guarda o fundo antigo de ticker reaproveitado, ex.: WINC e LEND — ou logo da gestora conferido); nenhum apagado por dúvida. Ex. mantido: VIXY com o logo da ProShares (está certo; o nome não diz a gestora) |
| 4 — sem logo | 1.164 criados (ETF pela gestora; preferencial pelo logo da empresa, ex.: JPM-C ← JPM) |
| 5 — sem logo | 473 (Stock 355, ETF 117, REIT 1) |

Resultado: com logo Stock 5.524/5.879, REIT 182/183, ETF 6.014/6.131.

**Executado em 29/10/2026** com o "de acordo" do Guilherme — mesmos números da simulação; relatório definitivo em
`docs/internacional/logos_relatorio.csv` (a prévia foi apagada). A primeira execução parou no início da etapa 2 (o
script lia o hash de arquivos que a etapa 1 já tinha trocado); foi retomada da etapa 2 usando a lista da simulação,
sem refazer a etapa 1. Conferência no disco: nenhum ticker com dois arquivos, nenhum "sem logo" com arquivo, nenhum
arquivo fora do CSV. Os 98 "mantido – a conferir" vão para a planilha de revisão (D12).

### 11.13 F4 — como ficou (29/10/2026)

**Tarefa `FOREIGN_CATALOG`** (V16: segunda, 06:30; atributo `WRITE`, nasce falso; rota `POST /api/v1/admin/foreign-catalog[?write=true]`,
`node carga/scripts/admin.mjs foreign-catalog`). Toda rodada grava a prévia `carga/data/levantamento/internacional/catalogo-previa.json`;
só com `WRITE=true` o catálogo muda (uma transação; avisos depois do commit).

| Regra | Como ficou |
|---|---|
| C1 | Fotografia do dia do Nasdaq Trader, bolsas da D1, sem test issue. Fora pelo nome: warrant, right, unit de SPAC, nota/título de dívida, papel estruturado (STRATS, CorTS, units de capital). ETN não cai na regra da nota: o OpenFIGI decide (ETP = ETF) |
| C2 | Marca de ETF da bolsa → ETF; preferencial pelo símbolo (`$`, NYSE) **ou pelo nome** (Nasdaq: AGNCL, ACGLO — "Depositary Shares", "Cumulative", "Preferred") → STOCK (`type_source = LIST`); senão o `securityType` do OpenFIGI. OpenFIGI com chave: 100 tickers por pedido, cache por ticker de 6 dias |
| C3 | `BRK.B` e `ABR-E` no catálogo; `BRK/B` no OpenFIGI; `BRK-B` e `ABR-PE` na SEC (a preferencial acha a empresa pelo papel principal) |
| C4 | STOCK: BDR da mesma empresa (nome normalizado; 723) → tabela SIC versionada `activest-adm-api/src/main/resources/foreign/sic-categories.csv` (436 códigos → 19 setores; código fora da tabela vai pelo setor mais comum do grupo; empresa de investimento sem SIC na SEC → Finanças; 5.125) → curadoria. Onde há BDR e SIC, os dois concordam em 87% (aba Categorias mostra as discordâncias para revisar a tabela). REIT: setor da Nareit do ano mais recente (2023–2026). ETF: palavras do nome, em ordem |
| C5 | FIGI composto → série da SEC (ETF) → (ticker, tipo). Renomeação troca o ticker do mesmo ativo e abre `CATALOGO_TICKER_RENOMEADO` com chave `EUA:` (a conferência da B3 não fecha esses avisos). O tipo de um ativo gravado só muda pela curadoria |
| C7 (EUA) | Ativo fora da lista: Form 25/25-NSE da empresa arquivado a partir de 45 dias antes do último dia visto → `FORM_25` (saída = arquivamento + 10 dias); senão, 30 dias fora → `LIST_GONE` (saída = último dia visto). `MANUAL` nunca é refeito. ETF só pelo sumiço (o Form 25 do trust não diz qual série) |
| C9 | Planilha `catalogo-revisao.xlsx` gerada por `carga/scripts/levantamento/internacional_planilha.py` (abas Resumo, Categorias, Stocks, REITs, ETFs, Deslistados, Colisões, Divergências REIT, Excluídos, Logos a conferir; colunas amarelas "CURADORIA" com listas de tipo/categoria; a curadoria já feita volta ao gerar de novo). `internacional_curadoria.py` confere e grava `curadoria.json`; a tarefa aplica com `CURATION` (tipo, categoria, ticker, exclusão), também sobre ativo já gravado |
| Ficha da SEC | `sec/profiles/{cik}.json` (SIC, nome, tickers, Form 25 recentes) — o cadastro completo é apagado depois de lido; 90 dias de validade. A primeira carga baixa ~5.500 fichas (~35 min, 8 pedidos/s no máximo); depois a rodada leva ~4 min |

**Primeira rodada (29/10/2026, banco de desenvolvimento):** lista com 13.233 papéis → **12.180 no catálogo** (Stock 5.862, REIT 183,
ETF 6.135) e 1.053 fora (428 warrants, 255 units de SPAC, 172 notas, 126 rights, 43 units e 1 warrant pelo OpenFIGI, 17
estruturados, 11 sem FIGI). Categoria do ETF: Ações EUA 1.260, Outros/estratégia 1.248 (buffers e defined outcome, a maior parte),
Renda fixa 1.089, Alavancados 843, Internacionais 696, Dividendos 663, Cripto 149, Ouro/commodities 112, Imobiliário 75. 16 sem
categoria (bancos sem registro na SEC, 2 REITs fora da Nareit) — esperam a curadoria. 14 divergências de REIT (CoreCivic e as
OPs da Empire State com SIC 6798; o OpenFIGI decide), 70 colisões de ticker com cripto. Contra o CSV de 28/10 (12.193): 136 a
menos eram as preferenciais da Nasdaq (corrigido: entram pelo nome) e os estruturados; BRK.A não está na lista do dia.

**Gravado com `WRITE=true` no banco de desenvolvimento** (12.164 ativos, USD, país US, `foreign_listings` 1:1); a segunda rodada não
mudou nada (0 novos, 0 atualizados). A revisão da planilha pelo Guilherme (D12) continua valendo: a curadoria aplicada depois
troca tipo, categoria e ticker dos já gravados, com a origem `CURATION`.

### 11.14 Roteiro de conferência no navegador (F7 — Guilherme, nos dois temas)

Com as APIs no ar (`docker compose up -d`), logado como administrador, em `http://localhost:5173`. Repetir os passos 1–6 no
tema claro e no escuro (menu do usuário → Tema).

| # | Onde | O que fazer | O que deve aparecer |
|---|---|---|---|
| 1 | Transações → Nova transação | Tipo **Stocks**, digitar `AAPL` | Apple Inc. com o logo; moeda USD; preço vazio (sem cotação, C11) |
| 2 | Nova transação | Tipo **Criptomoedas**, `META`; depois tipo **Stocks**, `META` | Dois ativos diferentes, cada um com o seu logo (D3) |
| 3 | Nova transação | Tipo **Stocks**, buscar um deslistado da planilha `deslistados-revisao.xlsx` (aba Deslistados) | Aparece depois dos vivos, em âmbar, "deslistado em dd/mm/aaaa"; o campo Data não passa da saída; salvar com data depois da saída → mensagem "saiu da bolsa em ..." |
| 4 | Nova transação | Tipo **ETFs Internacionais** `VOO`; **REITs** `PLD`; um preferencial `JPM-C` (Stocks) | Os três com logo; JPM-C com o logo do JPMorgan |
| 5 | Carteira / Alocação / Estratégia | Lançar uma compra de `AAPL` e abrir as telas | O ativo no tipo Stocks, avaliado pelo custo (o tipo não tem cotação, C11); Alocação com o tipo Stocks |
| 6 | Metas → nova meta de ativo | Escolher tipo + ticker (`META` ação) | O campo mostra ticker e tipo; a meta acompanha o ativo certo |
| 7 | Tarefas (`/tasks`) | Achar `FOREIGN_CATALOG` e `FOREIGN_DELISTED` | Atributos `WRITE` (e `FROM_YEAR`/`TO_YEAR`); a primeira com horário de segunda 06:30; Run com `WRITE` falso termina `SUCCEEDED` com o resumo |
| 8 | Notificações | Depois de uma inativação (ou desfazendo uma: `node carga/scripts/admin.mjs assets/<id>/activation --method PUT --body '{"active":true}'`) | Aviso "inativado — saiu da bolsa" com a prova; ao reativar, o aviso fica resolvido |
| 9 | Planilhas | `python carga/scripts/levantamento/internacional_planilha.py` e `internacional_deslistados.py planilha` | Abrem no Excel com as abas da 11.13; colunas amarelas com listas de tipo e categoria |

### 11.15 F5 e F6 — como ficaram (29/10/2026)

**F5 — tarefa `FOREIGN_DELISTED`** (V17, só Run e etapa do `bootstrap`; rota `POST /api/v1/admin/foreign-delisted?from&to[&write=true]`).

| Passo | Como ficou |
|---|---|
| Índice | Por trimestre, o `form.idx` completo é baixado, as linhas de Form 25/25-NSE ficam em `sec/form25/{ano}QTR{n}.idx` e o resto é apagado; o 25-NSE aparece também no CIK da bolsa — vale o emissor do XML |
| Form 25 | `Form25.parse`: 25-NSE pelo XML (`descriptionClassSecurity`, bolsa, emissor) e o anexo da bolsa ("at the opening of business on ...") para a data de saída; o 25 da empresa pelo HTML ("(Description of class of securities)"); sem anexo, arquivamento + 10 dias. Um Form 25 de fundo com várias séries vira uma linha por série (`acesso#n`) |
| Escopo | Só capital nas bolsas da D1: ação, ADS, unit de MLP, cota de fundo (nota, preferencial, warrant e right fora). Empresa ainda no catálogo vivo (troca de bolsa) fora; da mesma empresa fica a última saída |
| Ticker | `DelistedTickers`: `dei:TradingSymbol` dos 5 últimos formulários antes da saída (XBRL inline, até no 8-K; antes de 2019 a instância XBRL pelo `index.json`), depois o texto ("under the symbol ...", "(NYSE: XYZ)"); fundo pelo próprio Form 25 ("(XYZ)") ou pelas séries da SEC (2023+); REIT pela Nareit. Guardado por número de acesso em `sec/delisted/` (o não achado volta a ser procurado em 180 dias) |
| Tipo e categoria | Fundo = ETF; SIC 6798 e Nareit do ano = REIT; resto STOCK. Categoria pelas regras da C4 |
| Curadoria | `curadoria-deslistados.json` por número de acesso, gerado de `deslistados-revisao.xlsx` por `internacional_deslistados.py curadoria` |

**Rodada no DEV (2010–2026):** 15.340 Form 25 → 7.281 fora (5.673 classe fora do capital, 780 empresa ainda listada, 664
saída anterior da mesma empresa, 164 bolsa fora da D1) → **8.257 papéis de capital**, **6.061 com ticker (73%)**:

| Tipo | Papéis | Com ticker |
|---|---|---|
| STOCK | 6.012 | 5.159 (86%) |
| REIT | 112 | 112 (100%) |
| ETF_INTERNACIONAL | 2.133 | 790 (37%) |

Por ano, a cobertura vai de ~55% (2014) a ~88–91% (2024–2025): o XBRL inline (2019+) traz o ticker em quase todo
formulário. **Gravados 5.906 inativos** (`FORM_25`); 120 com ticker e sem categoria e 2.196 sem ticker ficam na planilha
e no PENDENCIAS 41.4 (D2). Prova das fontes de fundos: só 2023+ tem séries publicadas pela SEC — o ETF deslistado antes
disso só tem ticker quando o próprio Form 25 o traz.

**F6 — logos.** O papel novo da `FOREIGN_CATALOG` recebe o logo do BDR da mesma empresa quando não tem arquivo (a
preferencial, o da própria empresa); a imagem genérica da BRAPI (o mesmo conteúdo em 3+ arquivos) não é copiada.
Cobertura dos vivos: Stock 5.512/5.862, REIT 182/183, ETF 6.014/6.135. Deslistado fica sem logo (iniciais). **Achado no
teste do Guilherme:** as telas com logo travavam ~10 s por minuto — o índice da app-api fazia um `stat` por arquivo das
subpastas (~11 mil, na pasta montada do Docker no Windows); corrigido em `AssetLogos` (filtro pela extensão, releitura
sem bloquear os outros pedidos): 10,8 s → 0,1 s.

**Conferência no navegador (Guilherme, 29/10/2026, os dois temas):** lançamentos de ETF internacional (IAU), REIT (PLD),
Stock (AAPL, META), cripto homônima (META) e deslistado retroativo (EEMD em 2022, saída 29/10/2024) gravados no ativo
certo, com a PTAX da data; EEMD depois da saída recusado; Carteira e Alocação com os tipos novos pelo custo.

---

## Anexo A — Levantamento de preço (28/10/2026), fora do escopo

Guardado como insumo do escopo futuro de preços. **[C]** = lido na página citada.

**Licença é o achado principal**: toda fonte de preço com plano gratuito ou "individual" proíbe mostrar o dado a outra
pessoa — Tiingo: "you may not display or share the data with another person or organization"
([pricing](https://www.tiingo.com/about/pricing)); Massive (ex-Polygon): plano Business "as soon as anyone other than
you can see the data" ([KB](https://massive.com/knowledge-base/article/which-plan-do-i-need-to-show-massive-data-in-my-app));
Yahoo proíbe coleta automatizada e redistribuição ([termos](https://legal.yahoo.com/xw/en/yahoo/terms/otos/index.html));
Stooq exige chave via captcha desde mar/2026 ([pandas-datareader #1012](https://github.com/pydata/pandas-datareader/issues/1012)).
BRAPI não cobre EUA.

| Fonte | Lote diário | Desde 2010 | Não ajustado + fatores | Proventos / desdobramentos | Gratuito | Menor pago (uso próprio) | Exibir a terceiros |
|---|---|---|---|---|---|---|---|
| Tiingo | Não | Sim (30+ anos) | Sim | Sim / Sim | 50 req/h, 1.000/dia, 500 tickers/mês | US$ 30 | US$ 250/mês |
| Massive (ex-Polygon) | **Sim** (grouped daily) | Só no plano de US$ 199 | Sim | Sim / Sim | 5 req/min, 2 anos, dia só após meia-noite ET | US$ 29 | US$ 2.499/mês |
| EODHD ([bulk](https://eodhd.com/financial-apis/bulk-api-eod-splits-dividends)) | **Sim** (preço, splits, proventos) | Desde 1962, com deslistados | Sim | Sim / Sim | 20/dia, 1 ano | **US$ 19,99** | US$ 2.499/mês |
| Twelve Data | Não | Sim | Sim | Só no plano pago | 8/min, 800/dia | US$ 79 | US$ 499/mês |
| Alpaca | Multi-símbolo | Só desde 2016 | Sim | Sim (com ganho de capital) | 200/min (conta) | US$ 99 | Não permite |
| Alpha Vantage / FMP / Finnhub | — | Pago | — | — | 25/dia · 250/dia · sem histórico | US$ 19–49 | Contrato |

Combinações viáveis: **A** (só o Guilherme, US$ 0) Massive grátis para o fechamento do catálogo + Tiingo grátis para a
série e os eventos dos papéis em carteira; **B** (só o Guilherme, US$ 19,99/mês) EODHD para tudo; **C** (outros
usuários, US$ 250/mês) Tiingo com redistribuição. S&P 500: índice licenciado (FRED só 10 anos,
"Pre-Approval Required" — [SP500](https://fred.stlouisfed.org/series/SP500)); alternativa: SPY como benchmark de ativo.
Logos: Clearbit desligado em 08/12/2025; logo.dev gratuito com atribuição ([pricing](https://www.logo.dev/pricing)).
