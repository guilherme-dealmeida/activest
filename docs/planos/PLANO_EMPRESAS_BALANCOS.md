# PrimeVest — Plano de Emissores (Empresas e FIIs), Balanços e Indicadores

> Fase: **planejamento concluído** (26–27/09/2026) · nada implementado · não executar sem ordem explícita
> Todas as decisões tomadas (seção 9). Andamento na seção 7.1. Versão consolidada em 27/09/2026: substitui os adendos das rodadas
> anteriores. Branch de trabalho: `TRACK_01-ajustes_crud_inicial`.
> Escopo seguinte, que consome este: **preço teto** (Graham, Bazin, fluxo de caixa descontado).

---

## 0. Resumo

**Objetivo:** cadastrar os emissores da B3 — companhias abertas e FIIs —, ligar cada um aos ativos do catálogo
(PETR3, PETR4, PETR3F, PETR4F → Petrobras; HGLG11 → Pátria Log), importar balanços, informes e proventos por
integração, e calcular indicadores atuais e históricos.

**Tipos de ativo que viram emissor:** ACAO (inclui units e fracionários) e FII. BDR (balanço estrangeiro),
ETF, Tesouro, renda fixa e cripto ficam de fora.

**Princípios**

1. **Guardar o dado da fonte, calcular o indicador.** Balanço, informe e preço são gravados; indicador é
   calculado na leitura, em serviço e DTO. A única coisa derivada gravada é a série trimestral das empresas
   (montar trimestre e 12 meses a partir do ITR/DFP não é trivial).
2. **Ou é migration, ou é arquivo.** Dado de referência e curado (catálogo, emissores, ligações, mapa de
   contas) entra por migration de seed. Dado em volume vindo de fonte oficial (balanços, preços, avisos de
   proventos) entra por arquivos em `carga/data/`, processados pela carga inicial. Sem tabela auxiliar de seed e sem
   coluna que guarda estado de processo — o arquivo é o estado.
3. **Integração, não importação.** Tudo vem de fontes públicas buscadas pela própria API. Nada depende de
   pasta fora do projeto. Importação de arquivo do usuário (extrato da B3) é outro escopo (PENDENCIAS, 16).

---

## 1. Fontes (todas verificadas em 26–27/09/2026)

Os testes de rede foram feitos pela máquina do Guilherme (a VM do Cowork e o contêiner de nuvem recebem 403 do
proxy para esses domínios; o contêiner da API acessa normalmente).

### 1.1 CVM — companhias abertas

Portal `https://dados.cvm.gov.br/dados/CIA_ABERTA/`. CSV, separador `;`, **ISO-8859-1** (mesmo charset que o
`CvmFundRegistry` já trata). Gratuito, sem token, sem limite; atualizado toda madrugada.

| Conjunto | Caminho | Desde | Tamanho | Uso |
|---|---|---|---|---|
| Cadastro | `CAD/DADOS/cad_cia_aberta.csv` | — | 1,5 MB, 2.678 linhas | CNPJ, código CVM, razão social, setor CVM, situação, mercado, categoria |
| FCA | `DOC/FCA/DADOS/fca_cia_aberta_{ano}.zip` | 2010 | ~380 KB | `valor_mobiliario`: **ticker ↔ CNPJ**, composição de units, segmento de listagem; `geral`: mês de fim do exercício, site |
| DFP (anual) | `DOC/DFP/DADOS/dfp_cia_aberta_{ano}.zip` | 2010 | ~12 MB | balanço anual |
| ITR (trimestral) | `DOC/ITR/DADOS/itr_cia_aberta_{ano}.zip` | 2011 | ~30 MB | 1º a 3º trimestres |

Dentro do DFP/ITR, um CSV por demonstração, consolidado (`_con`) e individual (`_ind`). **Usados:** BPA, BPP,
DRE, DFC_MI, DFC_MD, DVA (consolidados; individual só se a empresa não publicar consolidado) e
`composicao_capital`. O DMPL (100 MB no ITR) não é necessário.

**Colunas:** `CNPJ_CIA; DT_REFER; VERSAO; DENOM_CIA; CD_CVM; GRUPO_DFP; MOEDA; ESCALA_MOEDA; ORDEM_EXERC;
DT_INI_EXERC; DT_FIM_EXERC; CD_CONTA; DS_CONTA; VL_CONTA; ST_CONTA_FIXA`

- BPA/BPP não têm `DT_INI_EXERC` (posição numa data) — as colunas seguintes deslocam uma posição.
- `CD_CVM` com zeros à esquerda (`009512`). `ESCALA_MOEDA` `MIL` (quase todas) ou `UNIDADE` — aplicar na leitura.
- `ORDEM_EXERC`: usar só `ÚLTIMO` (`PENÚLTIMO` é o comparativo do ano anterior).
- `ST_CONTA_FIXA = S`: conta padronizada; `N`: conta livre da empresa — **não usar**.
- O arquivo traz só a **versão mais recente** de cada documento; versão maior no mesmo `DT_REFER` = reapresentação
  → substituir.

**Períodos (conferido com a Petrobras):**

- DRE no ITR: coluna de 3 meses e coluna acumulada no ano. DFC e DVA no ITR: só acumulado.
- 4º trimestre não existe como documento: **4T = DFP anual − ITR acumulado de 9 meses**.
- 11 empresas não fecham o exercício em dezembro (meses 2, 3, 6, 9): trimestre identificado por `DT_FIM_EXERC`,
  nunca por "1T/2T/3T" do calendário.

**Layouts de plano de contas** (identificados pelo nome da conta 3.01 do DRE):

| Layout | Conta 3.01 | Empresas (DFP 2025) |
|---|---|---|
| COMERCIAL | Receita de Venda de Bens e/ou Serviços | 426 |
| BANCO | Receitas da/de Intermediação Financeira | 13 |
| SEGURADORA | Receitas das Atividades Seguradoras/Resseguradoras | 2 |

**Mapa de contas inicial** (tabela `cvm_account_map`, semeada por migration — métrica nova = linha nova +
reprocessar os arquivos de `carga/data/cvm`, sem baixar de novo). **Substituído na F5** pelas regras por descrição —
ver a nota logo abaixo da tabela:

| Métrica | COMERCIAL | BANCO | Uso |
|---|---|---|---|
| RECEITA | 3.01 | 3.01 | histórico |
| EBIT | 3.05 | — | FCD (próximo escopo) |
| LUCRO_LIQUIDO | 3.11 | 3.09 | — |
| LUCRO_CONTROLADORA | 3.11.01 | 3.09.01 | LPA, P/L, ROE, payout |
| ATIVO_TOTAL | 1 | 1 | — |
| CAIXA | 1.01.01 + 1.01.02 | — | dívida líquida (próximo escopo) |
| DIVIDA_BRUTA | 2.01.04 + 2.02.01 | — | dívida líquida (próximo escopo) |
| PL | 2.03 | 2.08 | — |
| PL_NAO_CONTROLADORES | 2.03.09 | 2.08.09 | PL_CONTROLADORA = PL − este |
| FCO | 6.01 | 6.01 | FCD (próximo escopo) |
| JCP_DECLARADO | 7.08.04.01 | 7.09.04.01 | payout |
| DIVIDENDOS_DECLARADOS | 7.08.04.02 | 7.09.04.02 | payout |
| LPA_CVM (ON / PN) | 3.99.01.01 / 3.99.01.02 | idem | só validação |

Seguradora: mapa próprio levantado na execução (2 empresas). Capex fica de fora (conta livre no DFC).

> **Execução (F5, 28/09/2026) — o mapa por código não vale.** No mesmo DFP 2025 o lucro líquido é a conta 3.09
> no Itaú, 3.11 no BB e 3.13 no IRB, e o PL é 2.08, 2.07 e 2.03; o BB mudou de 3.09/2.08 (até 2015) para
> 3.11/2.07 (2020). O que se mantém é a **descrição padronizada da CVM** no nível certo da árvore: "Lucro/Prejuízo
> Consolidado do Período" (e a filha "Atribuído a Sócios da Empresa Controladora"), "Patrimônio Líquido
> Consolidado" (e "Participação dos Acionistas Não Controladores"), "Resultado Antes do Resultado Financeiro e dos
> Tributos" (EBIT), "Remuneração de Capitais Próprios" › JCP e Dividendos na DVA. Receita (3.01), ativo total (1),
> FCO (6.01) e caixa e dívida do layout comercial continuam por código. As regras ficam em código
> (`FinancialMetricRules`); métrica nova = regra nova + `POST /admin/cvm/import?force=true` (sem rede). Sem
> `cvm_account_map`. Seguradora: as mesmas regras servem (IRB informa "Atribuído à controladora" = 0 sem ter
> minoritários — lucro zero nas duas filhas = lucro inteiro da controladora). LPA por classe (`3.99.01.xx`) vem
> com `ST_CONTA_FIXA = N` e não leva a escala MIL.

**Armadilhas:**

- **Composição do capital só existe de 2020 em diante** (DFP e ITR). Antes, a quantidade vem do histórico de
  capital do **Formulário de Referência** (FRE, `capital_social`, capital integralizado), sem tesouraria — ver
  7.1, F5.
- **Quantidade de ações em escala errada.** `composicao_capital` não informa escala: Itaú 11.026.869 (são
  11 bilhões), Axia 2.915.428. No DFP 2025, 277 de 666 empresas têm total < 20 milhões. Regra: comparar
  lucro controladora ÷ ações com o LPA por classe informado (3.99.01.01/02); se bater com fator 1.000, aplica;
  sem LPA, compara com o trimestre anterior; o que não fechar fica `REVISAR`.
- **LPA total 3.99 não significa nada** (é a soma das linhas de baixo: Itaú 16,12 = 4,05 + 4,05 + 4,01 +
  4,01). Usar só o LPA por classe, e só para conferência (a empresa usa média ponderada de ações; diferença
  pequena é normal: Itaú 4,07 calculado × 4,05 informado).
- **FCA incompleto para tickers:** Klabin só com KLBN11. Units em texto livre ("1 KLBN3 + 4 KLBN4",
  "1 ON / 2 PN", "1 ON + 1 PN").
- **FCA do ano é só de quem já entregou:** ler os três últimos anos, mais recente por CNPJ.
- **Cadastro duplicado por mercado** (Petrobras em BOLSA e BALCÃO ORGANIZADO): chave é o CNPJ.

**Volume gravado:** só as métricas do mapa, ~700 mil linhas (2010–2026). Cru seriam ~9 milhões.

**Conferência com a Petrobras (DFP 2025):** LPA = 110.129 mi ÷ 12.888.732.761 = **8,54** (a empresa informa
8,54) · VPA = (417.587 − 1.801) mi ÷ 12,89 bi = **32,26** · payout = (30.682 + 10.554) ÷ 110.129 = **37,4%**.

### 1.2 CVM — informe mensal de FII

`https://dados.cvm.gov.br/dados/FII/DOC/INF_MENSAL/DADOS/inf_mensal_fii_{ano}.zip`, **2016 a 2026**, ~1.350
fundos por mês (~700 negociados em bolsa). Uma linha por fundo por mês, com o número pronto.

| CSV | Uso |
|---|---|
| `geral` | CNPJ, nome, ISIN, administrador, tipo de gestão, `Segmento_Atuacao`, negociado em bolsa |
| `complemento` | PL, cotas emitidas, VP por cota, nº de cotistas, taxa de administração, DY do mês |
| `ativo_passivo` | composição (imóveis, CRI, LCI, caixa, cotas de FII), passivo, `Rendimentos_Distribuir` |

Exemplo HGLG11, 08/2026: PL 7,57 bi · 45,6 mi de cotas · VP/cota 165,95.

- **Segmento da CVM é fraco:** 318 dos ~674 FIIs em bolsa como "Multicategoria" (inclusive HGLG11, que é
  logística); `Mandato` vazio em 2026. **O segmento curado do catálogo é melhor** e é o que vale; o da CVM
  fica só como informação.
- **Rendimento estimado** = `Rendimentos_Distribuir ÷ Cotas_Emitidas` do mês. Contra os proventos gravados
  (2024–2025): HGLG11 23/24 meses batem, MXRF11 21/24. Sem data-com nem data de pagamento. **Serve só para
  indicador** (histórico de DY onde não houver evento real); **não** alimenta `asset_cash_events`, que gera os
  dividendos das carteiras.

### 1.3 B3 — série histórica de cotações (COTAHIST)

`https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_A{ano}.ZIP`. Oficial, gratuita, desde 1986, todos os
papéis, **com ISIN** em toda linha. Layout posicional (linha `01`; `CODBDI 02` lote padrão, `96` fracionário,
`12` FII). 2025: 89 MB zipado, 784 MB em texto. Preço **não ajustado** — casa com a quantidade de ações da
mesma data, que é o que o indicador precisa.

- Ações lote padrão: 432 tickers, ~84 mil linhas/ano; fracionário: 575 tickers, ~100 mil/ano.
- **BRAPI × B3 idênticos** (PETR4, 26/06 a 02/07/2026): as duas fontes convivem na mesma tabela.
- **ISIN de todo o catálogo:** 795/795 ações e 347/347 FIIs encontrados nos arquivos 2025–2026.

### 1.4 B3 — site (endpoints já usados pelo `B3Client`)

- `GetDetail` (por código CVM): ticker ↔ ISIN e **`industryClassification`** (setor / subsetor / segmento).
- `GetListedSupplementCompany` / `GetListedSupplementFunds`: proventos dos últimos ~12 meses; o de empresa
  traz `codeCVM` a partir da raiz do ticker.
- `GetListedCashDividends`: histórico longo de proventos **de empresa**. Para FII não existe (testado: 404 no
  proxy de fundos, vazio no de empresas). Nenhum endpoint da B3 devolve o CNPJ de FII.

### 1.5 FNET — avisos de rendimentos de FII (teste de 27/09)

HGLG11: a busca devolve 127 avisos estruturados (2017–2026) em 0,3 s. Dos 100 da primeira página, **100
obtidos**: cada download responde em ~0,1 s ou trava até o timeout; ~35% falham na 1ª tentativa e passam na
2ª/3ª; os últimos 4 passaram com timeout de 5 s e até 6 tentativas. **Os 9 meses que tinham sido completados
pelo extrato (12/2024 a 08/2025) estão todos no FNET**, com data-base e data de pagamento. Três avisos de 2017
vieram sem valor (variação antiga do XML) — tratar no parser.

**O problema é o cliente atual, não a fonte** (PENDENCIAS 14.1): timeout de 60 s sem nova tentativa, chamada
bloqueante dentro de `synchronized` numa thread virtual por evento, e nada é guardado.

**Dois formatos de XML (achado da F8, 28/09):** 10.505 dos 22.910 avisos do cache (2016 a ~2019) são do formato
antigo — `Rendimento`/`Amortizacao` direto em `InformeRendimentos`, valor em `ValorProventoCota`, ISIN e ticker da
cota em `DadosGerais` (`CodISINCota`, `CodNegociacaoCota`, às vezes com um "B" no fim: `WPLZ11B`), isenção como
`true`/`false`. O parser antigo só lia `Provento/…/ValorProvento`: eram esses os "avisos sem valor". O `FnetClient`
refeito lê os dois.

**Cache já disponível (27/09):** o script externo do Guilherme gravou **22.910 avisos em `carga/data/fnet`** (90 MB),
de 11/2016 a 09/2026, cobrindo **324 dos 345 CNPJs** de FII do catálogo. Pasta plana, `{id}.xml` — cada XML
se descreve (`CNPJFundo`, `CodISIN`, `CodNegociacao`, `DataBase`, `ValorProvento`, `DataPagamento`), então não
precisa de subpasta por CNPJ. Regras de leitura levantadas nesse conjunto:

- **Ligar pelo `CNPJFundo`, não pelo ticker.** 185 fundos aparecem com mais de um ticker ao longo do tempo:
  renomeações (LGCP11 → INLG11, EVBI11 → CPUR11, MCHY11 → MCRE11, CVBI11 → PCIP11, FEXC11 → BTCI11) e recibos
  de subscrição (APTO13, XPML14, EQIR20…).
- **Recibos e direitos (final 12 a 19) são ignorados:** o provento deles não é da cota do catálogo.
- **CNPJ às vezes vem pontuado** (4 fundos): normalizar para dígitos.
- **Alguns XMLs vêm numa linha só**: exige parser de XML de verdade (o `FnetClient.parse` já é DOM).
- **Data-base com erro de digitação na fonte**: aviso 36458 traz `2108-11-30` com pagamento em `2018-12-14`.
  Regra: data-base posterior ao pagamento é inválida → aviso fica de fora e vai para o log de revisão.
- 23 tickers (22 fundos) do catálogo não têm aviso estruturado: APXR11, APXU11, BSLT11, EGDB11, FIVN11, GCDL11,
  GSRF11, HCST11, HDOF11, HOMS11, IMMB11, INDE11, JPRD11, MMVE11, NMKS11, PABY11, RCFA11, STYI11, TRPL11/15,
  VGRH11, VVMR11, YEES11. **Conferido pelo Guilherme em 28/09: nenhum distribuiu ainda** — quando distribuírem,
  entram pela B3 na rotina diária. Não há histórico a buscar.

### 1.6 ISIN e CNPJ

| Dado | Onde mora | Por quê | Fonte | Cobertura |
|---|---|---|---|---|
| ISIN | `assets.isin` | identifica o **papel**: Petrobras tem `BRPETRACNOR9` (PETR3) e `BRPETRACNPR6` (PETR4); a B3 devolve proventos por ISIN de cada classe; no FII pós-CVM 175 o ISIN do informe pode ser o da classe (KNUQ11: `BR0EBICTF008` × negociado `BRKNUQCTF004`) | COTAHIST | 100% |
| CNPJ de empresa | `issuers.cnpj` | é do emissor | cadastro/FCA | 100% das ligadas |
| CNPJ de FII | `issuers.cnpj` | é do fundo | **seed**, conferido contra FNET e informe (abaixo) | 347/347 |

**Planilha do Guilherme (`fiis_com_isin.csv`, 27/09) — 347 tickers, conferida:**

- **CNPJ:** dos 297 que o informe da CVM resolve pelo ISIN, 295 batem. Os 2 divergentes são **erro da CVM**:
  APTO11 e XPML11 — os avisos do FNET com `CodNegociacao` APTO11/XPML11 trazem o CNPJ da planilha
  (42.432.327/0001-82 e 28.757.546/0001-00); o informe liga esses ISINs a outros fundos. Os 50 que o informe
  não resolve passam a ter CNPJ pela planilha; 324 dos 345 CNPJs têm avisos no FNET que os confirmam.
- **VGRH11 estava errado** (repetia CNPJ e ISIN do VGIR11). CNPJ correto informado pelo Guilherme em 27/09:
  **67.624.043/0001-19** — não aparece nos informes da CVM até 08/2026 nem nos avisos do FNET (fundo recente);
  a conferência automática fica para quando ele publicar.
- **TRPL11 e TRPL15** com o mesmo CNPJ: correto (duas classes do mesmo fundo).
- **ISIN da planilha não serve:** 51 de 347 diferem da B3 — ISIN antigo de antes da renomeação (BTCI11 com
  ISIN de FEXC, GARE11 de GALG, TRUE11 de OULG…), ISIN de recibo (`…R06M13`) ou digitação (TGAR11 sem o `BR`).
  O ISIN continua vindo do COTAHIST.

Decisão prática: **o CNPJ de todos os FIIs vai como literal no seed de `issuers`** (M3), a partir da planilha
conferida. A integração só cria emissor para FII que entrar no catálogo depois, usando o informe pelo ISIN e,
quando houver aviso no FNET, conferindo por `CodNegociacao` → `CNPJFundo`.

---

## 2. Carga e armazenamento

### 2.1 O que entra por migration e o que entra por arquivo

| Natureza | Num banco vazio | Conteúdo |
|---|---|---|
| Referência / curado | **migration de seed** | tipos, categorias, ativos (já existem); **`issuers`** com CNPJ, código CVM, nomes, setor B3, **segmento curado do FII** e os 50 CNPJs de exceção; ligação ativo ↔ emissor (`issuer_id`, `share_class`, `isin`); composição de units; `cvm_account_map` |
| Volume de fonte oficial | **arquivos em `carga/data/`** + carga inicial | `carga/data/cvm` (cadastro, FCA, DFP, ITR, informe FII), `carga/data/b3` (COTAHIST), `carga/data/fnet/{id}.xml` (avisos) |

- Seed de `issuers` é **gerado a partir do banco** depois que F1–F3 rodarem uma vez e a curadoria for conferida
  (mesmo caminho dos V4/V5/V15, `generate_seeds.js`). A integração continua rodando depois: atualiza nome e setor
  B3, cria emissor novo. Curadoria nova (segmento de FII novo) entra numa migration seguinte, como já é com ativo
  novo. A importação **nunca sobrescreve** o segmento curado.
- **O arquivo é o estado:** existe em `carga/data/` → não baixa de novo. Importação idempotente (upsert), então
  reprocessar é sempre seguro. Incremental: lista remota × arquivos locais.
- **Cache inicial do FNET:** ✅ já em `carga/data/fnet` (22.910 avisos, 27/09).

### 2.2 Onde ficam os arquivos (D14: guardar tudo)

`E:\repos\primevest\carga\data\{cvm,b3,fnet}` — pasta do projeto, montada no contêiner da API (bind mount, igual a
`carga/data/logos`). A pasta `/data/` inteira está no `.gitignore` (27/09). Não fica no PostgreSQL nem no disco do Docker Desktop (C:).

| Conjunto | Tamanho aproximado |
|---|---|
| COTAHIST 2010–2026 | ~1,3 GB |
| ITR 2011–2026 | ~450 MB |
| DFP 2010–2026 | ~200 MB |
| FCA, cadastro, informe FII | ~40 MB |
| Avisos FNET (22.910 XMLs hoje) | 90 MB |

Total ~2 GB. Guardar tudo é o que permite reconstruir o banco sem rede.

### 2.3 Colunas de estado que saem de `assets`

| Coluna | Por que sai | O que substitui |
|---|---|---|
| `cnpj` | é do emissor | `issuers.cnpj` |
| `cash_history_loaded_at` | estado de processo no catálogo | arquivos em `carga/data/fnet` e carga idempotente |
| `logo_checked_at` | idem | arquivo em `carga/data/logos`; subida da API não procura nada; cron mensal procura só quem não tem arquivo; ativo novo é procurado na criação |
| `logo_path` | redundante com o arquivo | `AssetLogoService` indexa `carga/data/logos` na subida (ticker → arquivo, em memória), atualiza ao gravar; `PortfolioService` e `DividendService` usam `hasLogo(ticker)`; DTOs mantêm o booleano. Troca de logo apaga o arquivo antigo do mesmo ticker com outra extensão |

Hoje: 1.838 ativos com logo possível, 1.451 arquivos em `carga/data/logos`, ~1.020 sem logo.

---

## 3. Modelo de dados

Tudo **global** (sem `user_id`), como `assets`.

```
issuers                              -- emissor: companhia aberta ou FII, 1 por CNPJ
  id BIGSERIAL PK
  kind VARCHAR(10) NOT NULL           -- COMPANY / FII
  cnpj VARCHAR(14) UNIQUE NOT NULL    -- só dígitos
  cvm_code INTEGER UNIQUE NULL        -- só COMPANY
  legal_name, trade_name VARCHAR
  registration_status VARCHAR(20)     -- ATIVO / CANCELADA
  website VARCHAR
  -- COMPANY
  cvm_sector VARCHAR                  -- SETOR_ATIV da CVM
  b3_sector, b3_subsector, b3_segment VARCHAR    -- GetDetail
  registration_category VARCHAR(20)   -- Categoria A / B
  listing_segment VARCHAR(60)         -- Novo Mercado, N2...
  fiscal_year_end_month SMALLINT
  statement_layout VARCHAR(20)        -- COMERCIAL / BANCO / SEGURADORA
  -- FII
  segment VARCHAR                     -- curado (vem do seed; importação não sobrescreve)
  cvm_segment VARCHAR                 -- Segmento_Atuacao do informe, só informação
  administrator_name VARCHAR, administrator_cnpj VARCHAR(14), management_type VARCHAR(20)
  source_updated_at, created_at, updated_at TIMESTAMP

assets (colunas novas)
  issuer_id BIGINT FK issuers NULL
  share_class VARCHAR(10) NULL        -- ON, PN, PNA, PNB, PNC, PND, UNIT (ACAO)
  issuer_link_source VARCHAR(10) NULL -- FCA / PREFIXO / B3 / ISIN / MANUAL (MANUAL nunca é sobrescrito)

unit_compositions                     -- KLBN11 = 1 ON + 4 PN
  asset_id FK assets, share_class VARCHAR(10), quantity INTEGER, PK(asset_id, share_class)

cvm_account_map                       -- NAO criada (F5: regras por descricao, 1.1)
  layout, metric, account_code, sign SMALLINT, PK(layout, metric, account_code)

cvm_documents                         -- 1 por DFP/ITR entregue
  id BIGSERIAL PK
  issuer_id FK, doc_type VARCHAR(3), reference_date DATE, version SMALLINT, cvm_doc_id INTEGER, received_at DATE
  UNIQUE(issuer_id, doc_type, reference_date)

cvm_statement_values                  -- métricas do mapa, em reais (escala aplicada)
  document_id FK ON DELETE CASCADE, metric VARCHAR(40), period_start DATE NULL, period_end DATE, value NUMERIC(22,2)
  PK(document_id, metric, period_end, period_start)

share_counts                          -- composicao_capital, 1 por documento
  document_id PK FK, on_total, pn_total, on_treasury, pn_treasury BIGINT
  scale_factor INTEGER, scale_status VARCHAR(10)   -- OK / REVISAR / MANUAL

company_quarters                      -- série trimestral derivada, recalculada a cada importação (V33, F6)
  issuer_id FK, period_end DATE, PK(issuer_id, period_end), fiscal_year_start DATE
  revenue, ebit, net_income, net_income_parent, fco, dividends_declared  × _q (trimestre isolado) e _ttm (12 meses)
  -- dividends_declared = dividendos + JCP da DVA
  equity, equity_parent, total_assets, cash, gross_debt NUMERIC               -- posição
  shares_total, shares_outstanding BIGINT (unidades; nulas em REVISAR), source_doc_id FK, computed_at TIMESTAMP

fii_monthly                           -- informe mensal, cru (V34, F7)
  issuer_id FK, reference_month DATE (1º dia), PK(issuer_id, reference_month), version INTEGER
  net_assets, total_assets, shares_issued, nav_per_share NUMERIC, shareholders INTEGER   -- complemento
  admin_fee_month, dividend_yield_month, amortization_month NUMERIC  -- fração do mês (0,0068 = 0,68%)
  liquidity_total, total_invested, real_estate_total, real_estate_companies,            -- ativo/passivo
  receivables_total, fii_shares_total, liabilities_total, income_to_distribute NUMERIC
  -- NUMERIC sem precisão: o informe é digitado e traz extremos (DY de 8,7e7 em 2022)

asset_price_history (coluna nova)
  source VARCHAR(15) NOT NULL DEFAULT 'BRAPI'   -- BRAPI / B3_COTAHIST; a BRAPI não sobrescreve dia gravado pela B3

asset_categories
  consolidada (4.2 da F4): some a coluna segment; unicidade (asset_type_id, name)
```

Índices: `assets(issuer_id)`, `cvm_documents(issuer_id, reference_date)`, `company_quarters(period_end)`.
Pacotes: `domain/issuer` (Issuer, CompanyQuarter, FiiMonthly, IndicatorService) e `integration/cvm`
(cliente e parsers). `CvmFundRegistry` passa a ler `issuers` em vez de manter o arquivo em memória.

### 3.1 Migrations, na ordem

| # | Migration | Fase |
|---|---|---|
| M1 | cria `issuers`, colunas em `assets`, `unit_compositions` | F1 |
| M2 | `asset_price_history.source` | F2 |
| M3 | **seed de `issuers` + ligações + units** (gerado do banco) | F4 |
| M4 | consolidação de `asset_categories` e remoção de `segment` (seção 4.2) | F4 |
| M5 | **V32**: `cvm_documents`, `cvm_statement_values`, `share_counts` (sem `cvm_account_map`; `company_quarters` foi para a F6) | F5 |
| M5b | **V33**: `company_quarters` | F6 |
| M6 | **V34**: `fii_monthly` | F7 |
| — | **V35**: `AMORTIZACAO RF` da B3 → `AMORTIZACAO` sem duplicar o FNET (PENDENCIAS 18.11 d) | F9+ |
| — | **V36**: `share_counts.scale_check` aceita `EVENTO` (PENDENCIAS 18.11 a) | F9+ |
| M7 | **V37**: remove `cnpj`, `cash_history_loaded_at`, `logo_checked_at`, `logo_path` de `assets` | F10 |

Numeração a partir de V28. Migrations antigas não são editadas (checksum do Flyway): num banco novo V3/V5/V15…
rodam com `segment` ainda existindo, e M4 roda depois delas.

---

## 4. Ligação ativo ↔ emissor e categorias

### 4.1 Ligação

**Ações** — em ordem, parando no primeiro que resolver:

1. FCA `valor_mobiliario`, mercado Bolsa: ticker → CNPJ. Classe pelo `Valor_Mobiliario`. 490 tickers no FCA 2026.
2. Raiz do ticker: KLBN11 ligado → KLBN3/KLBN4 também. Classe pelo número: 3 ON · 4 PN · 5 PNA · 6 PNB ·
   7 PNC · 8 PND · 11 UNIT.
3. B3 `GetListedSupplementCompany` pela raiz → `codeCVM`.
4. Manual (admin), gravado como `MANUAL`.

**Fracionário** (`F`): herda emissor e classe do ticker sem o `F`. **Units:** composição do FCA por regex dos
três formatos vistos; o resto, manual.

**FII:** CNPJ pelo seed (planilha conferida, 1.6); ISIN pelo COTAHIST. FII novo depois do seed: informe pelo
ISIN, conferido pelo FNET.

**Empresas cadastradas (D2):** situação ATIVO e mercado BOLSA (~345) + as ligadas a algum ativo do catálogo,
mesmo canceladas. **FIIs:** os ligados a ativos do catálogo (347).

Relatório de ativos sem emissor no `CatalogAuditService`. Ao ligar uma ação, a categoria pode ser preenchida
pelo setor B3 do emissor (ação nova não precisa de categoria manual).

### 4.2 Categorias sem `segment` (D11)

Hoje a categoria é o par `(name, segment)`: `BENS_INDUSTRIAIS` tem 17 linhas com ids diferentes. M4, numa
transação:

1. Para cada `(asset_type_id, name)`, escolhe a sobrevivente (menor id).
2. Reaponta `assets.asset_category_id` das duplicadas para a sobrevivente.
3. Apaga as duplicadas (só `assets` referencia `asset_categories`).
4. Troca a unicidade `(asset_type_id, name, segment)` por `(asset_type_id, name)` e apaga a coluna.

Resultado (V31, 27/09): 165 → 80 categorias (ACAO 80 → 11, FII 15 → 5, ETF 12 → 9, BDR 22 → 20, CRIPTO 15 → 14;
renda fixa e Tesouro sem mudança). O
segmento de ação passa a vir da B3 no emissor; o de FII fica no emissor pelo seed (M3, que roda antes); o de ETF,
BDR e cripto não tinha informação. Teste da migration: contagem de ativos por categoria antes e depois.
Código afetado: `AssetCategory.segment`, `AssetCategoryResponseDTO`, `types/index.ts`, `Claude.md`. Alocação não
muda (agrupa por `getDisplayName()`).

---

## 5. Indicadores

**Nada é gravado.** `IndicatorService` calcula na leitura e devolve DTO; cache Redis curto por
(ticker, indicador, dia). Válvula de escape, só se medir lentidão ou quando vier tela de ranking: tabela anual
recalculada depois do fechamento.

### 5.1 Ações

`ações` = ações em circulação do emissor (total − tesouraria, todas as classes) na data do balanço.

| Indicador | Fórmula | Nível |
|---|---|---|
| LPA | lucro controladora 12m ÷ ações | emissor (mesmo para todas as classes — D7) |
| VPA | PL controladora ÷ ações | emissor |
| P/L | preço ÷ LPA | ticker |
| P/VP | preço ÷ VPA | ticker |
| ROE | lucro controladora 12m ÷ PL controladora médio (início e fim — D4) | emissor |
| Payout | (dividendos + JCP declarados 12m, DVA) ÷ lucro controladora 12m | emissor |
| DY | proventos por ação com data-com nos 12 meses ÷ preço | ticker (D5) |

- Unit: LPA e VPA × quantidade de ações da unit (KLBN11 = 5); DY pelos proventos da própria unit.
- Fracionário: preço do lote padrão (D8).
- Lucro 12m negativo: P/L nulo com motivo.
- Bancos e seguradoras: mesmos indicadores; os de dívida ficam nulos (D9).
- Desdobramento/grupamento depois da data do balanço (`asset_stock_events`): ajusta a quantidade de ações no
  cálculo do atual.

**Série trimestral (`company_quarters`):** DRE do 1T–3T pela coluna de 3 meses; DFC e DVA por diferença de
acumulados; 4T = DFP − 9 meses; balanço pela posição; 12 meses = soma dos 4 últimos trimestres (faltando um,
nulo — não estima).

### 5.2 FII

| Indicador | Fórmula |
|---|---|
| P/VP | preço ÷ VP por cota do mês |
| DY | rendimentos com data-com nos 12 meses ÷ preço (evento real; estimativa da CVM só onde não houver evento — 1.2) |
| VP por cota | do informe |
| Nº de cotistas | do informe |
| % imóveis × papel | imóveis ÷ total investido; papel (CRI, LCI, títulos) ÷ total investido |
| Alavancagem | passivo ÷ ativo |

### 5.3 Atual, histórico e média (referência: tela "Histórico de Indicadores", 27/09)

- **Ponto anual:** ação — balanço do DFP + fechamento do último pregão do ano; FII — informe de dezembro +
  mesmo preço. DY do ano = proventos com data-com no ano ÷ esse preço.
- **Atual:** último trimestre (ação) ou último mês (FII) + `assets.current_price`.
- **Série trimestral:** disponível no serviço.
- **Média da empresa:** média simples dos pontos anuais **incluindo o Atual** (PETR4: 195,28 ÷ 11 = 17,75%);
  pontos nulos ficam fora.
- **Sem provento não é sem dado (28/09, F8):** ativo sem nenhum evento no período — os 23 FIIs que ainda não
  distribuíram (1.5), ação que não pagou no ano — tem **DY = 0** e proventos por cota = 0, não nulo; entra na média
  da empresa como 0. Nulo fica para o que falta de fato: sem preço, sem informe mensal (VGRH11, sem informe até
  08/2026 — VP por cota, P/VP e cotistas nulos com motivo), sem balanço. Nenhum cálculo pode dividir por um total
  de proventos zero (payout usa lucro no denominador; DY usa preço). A F9 cobre isso com teste.
- **Fora do escopo:** média do setor e remoção de anomalias.

```
IndicatorHistoryDTO
  ticker, indicator, unit (PERCENT, RATIO, BRL)
  current        : IndicatorPointDTO
  companyAverage : BigDecimal
  points         : List<IndicatorPointDTO>
IndicatorPointDTO
  label ("2010"… / "Atual"), periodEnd, value (nulo quando não se aplica), note (motivo do nulo)
```

### 5.4 API

Desde 28/09/2026 o backend tem dois serviços (docs/planos/PLANO_ADM_API.md): as leituras (`GET`) são da **app-api (8080)**,
que a tela usa; as rotinas `/api/v1/admin/*` são da **adm-api (8081)**.

```
# app-api (8080) — F9
GET  /api/v1/issuers/{id}                                     cadastro + tickers ligados
GET  /api/v1/issuers/{id}/quarters?from=                      série trimestral (COMPANY)
GET  /api/v1/issuers/{id}/months?from=                        informe mensal (FII)
GET  /api/v1/assets/{ticker}/indicators                       indicadores atuais
GET  /api/v1/assets/{ticker}/indicators/{indicador}/history   IndicatorHistoryDTO; ?period=YEAR|QUARTER
# adm-api (8081)
PUT  /api/v1/admin/assets/{id}/issuer                         ligação manual
POST /api/v1/admin/bootstrap                                  carga inicial (6.3)
POST /api/v1/admin/cvm/import?from=&to=&force=                balanços DFP/ITR (F5); force reprocessa sem baixar
POST /api/v1/admin/cvm/quarters                               recalcula company_quarters (F6; a carga já faz ao terminar)
POST /api/v1/admin/cvm/fii-monthly?from=&to=&force=           informe mensal de FII 2016+ (F7)
POST /api/v1/admin/b3/cotahist?years=                         reprocessar preços
POST /api/v1/admin/proventos/all                              reprocessar proventos
```

---

## 6. Rotinas

### 6.1 Importadores

- **CVM** (`CvmOpenDataClient`, padrão do `CvmFundRegistry`): baixa para `carga/data/cvm` o que não existe ou
  mudou (`If-Modified-Since` só no ano corrente); lê em streaming (`ZipInputStream`), só os CSVs usados, só
  `ÚLTIMO`, só contas do mapa. Ordem: cadastro → FCA (3 anos) → DFP/ITR → ações com validação de escala →
  `company_quarters`. Informe de FII pelo mesmo cliente.
- **COTAHIST:** baixa para `carga/data/b3`, lê em streaming, grava ACAO (lote padrão e fracionário) e FII em
  `asset_price_history` com `source = B3_COTAHIST`, casando ticker por ISIN (resolve renomeados); preenche
  `assets.isin`. Gráfico de cotação longo (futuro) ajusta desdobramento na leitura; indicador usa sem ajuste.
- **Proventos de ações:** B3 `GetListedCashDividends` para todas as empresas ligadas (1 chamada/s).
- **FNET refeito** (resolve PENDENCIAS 14.1): timeout de 5 s, até 6 tentativas com espera crescente, 2–3
  downloads simultâneos; **fila única** com um executor, deduplicada por fundo, no lugar de uma thread virtual
  por evento; sem `synchronized`; XML gravado em `carga/data/fnet/{id}.xml` e nunca baixado de novo;
  regras de leitura da seção 1.5 (ligar por CNPJ, ignorar recibos, normalizar CNPJ, descartar data-base inválida).
- Testes: recortes reais dos arquivos (Petrobras, Itaú, Klabin, empresa com exercício em fevereiro, HGLG11,
  avisos FNET de 2017) como fixtures, no padrão do `B3Client`.

### 6.2 Agendamentos

| Rotina | Quando |
|---|---|
| CVM (DFP, ITR, FCA, cadastro, informe FII) do ano corrente e do anterior | diário, 07:30 (`CvmStatementsScheduler`; informe FII desde a F7) |
| Preço diário | BRAPI, como hoje (19:00) |
| Proventos: B3 12 meses de todos os emissores ligados + avisos FNET novos | diário, 20:00 (`ProventosScheduler`) |
| Logos | mensal, só ativos sem arquivo |

### 6.3 Carga inicial num banco vazio (D13: manual)

`POST /api/v1/admin/bootstrap` (`BootstrapService`, F11) — roda em ordem, é retomável (toda etapa é idempotente e
o que já está em `carga/data/` não é baixado de novo; etapa que falha é registrada e a seguinte roda assim mesmo) e não
dispara sozinho ao subir o compose. Uma carga por vez.

| Etapa (ordem de execução) | Com `carga/data/` vazia | Com `carga/data/` cheia |
|---|---|---|
| 0. Emissores (cadastro, FCA e informe da CVM): completa o seed com as companhias e os FIIs negociados fora do catálogo; não mexe no segmento curado nem nas ligações | ~40 MB | só leitura local (+ classificação B3 das ~12 companhias que a B3 não responde) |
| 1. COTAHIST 2010+ (preços e ISIN, que a etapa 2 usa) | ~1,3 GB de download | só leitura local |
| 2. Proventos: 12 meses da B3 (1 chamada/s por emissor), eventos societários e histórico longo | + histórico de ações da B3 (~2 chamadas/emissor) e avisos do FNET (horas, ~23 mil downloads — hoje sem busca automática, 18.10 d) | histórico de ações de `carga/data/b3/proventos` e do FII de `carga/data/fnet`; só os 12 meses vão à B3 |
| 3. Balanços DFP/ITR 2010+, FRE, escala (inclusive `EVENTO`, que usa os eventos da etapa 2) e `company_quarters` | ~700 MB | só leitura local |
| 4. Informe mensal de FII 2016+ | ~40 MB | só leitura local |

Ligações e units vêm do seed (M3). A etapa 0 (incluída em 28/09, depois do ensaio) deixa o banco novo com os mesmos
emissores de quem já rodou a rotina de emissores — no ensaio, sem ela, eram 715 contra 1.198 (483 FIIs fora do catálogo)
e o informe só dos fundos do catálogo (25.538 contra 42.815 meses). Passo a passo para outra máquina: `README.md`.

---

## 7. Fases (ordem de execução)

> **Onde cada fase é feita (desde 28/09/2026, docs/planos/PLANO_ADM_API.md):** F7, F8, F10 e F11 são carga e rotina de
> catálogo — **adm-api** (`com.primevest.adm.*`), grava só catálogo e avisa a carteira pelo Redis ("catálogo
> mudou"). F9 é leitura para a tela — **app-api** (`IndicatorService` e endpoints; lê `company_quarters`,
> `fii_monthly`, eventos e preços). Entidades e migrations novas vão para o **primevest-core**. Na F8, a busca
> sob demanda de proventos ("preciso do histórico", app → adm) deve ser revista: com todas as empresas
> carregadas desde 2010 ela provavelmente deixa de ser necessária.

| Fase | Entrega | Depende |
|---|---|---|
| **F1** | M1 + importação de emissores: empresas (cadastro, FCA, setor B3 pelo `GetDetail`) e FIIs (informe `geral`) | — |
| **F2** | M2 + importador COTAHIST 2010+ em `asset_price_history` + ISIN de todo o catálogo | — |
| **F3** | Ligação ativo ↔ emissor (4.1): ações, fracionários, units, FIIs por ISIN; relatório de não ligados; ligação manual | F1, F2 |
| **F4** | Conferência da curadoria com o Guilherme → **M3** (seed de `issuers`, ligações, units, segmento curado de FII, 50 CNPJs de exceção) → **M4** (categorias sem `segment`) | F3 |
| **F5** | M5 + balanços DFP/ITR 2010+, quantidade de ações com validação de escala | F4 |
| **F6** | Série trimestral `company_quarters` | F5 |
| **F7** | M6 + informe mensal de FII 2016+ em `fii_monthly` | F4 |
| **F8** | Proventos: ações de todas as empresas (B3) + FNET refeito com cache em `carga/data/fnet` (cópia inicial dos XMLs do Guilherme) | F4 |
| **F9** | `IndicatorService`, DTOs e endpoints — ações e FII: atual, anual, trimestral, média da empresa | F6, F7, F8 |
| **F10** | Limpeza de `assets`: logo por arquivo (`AssetLogoService`, cron), FNET/`CvmFundRegistry` lendo `issuers.cnpj`, M7 | F8 |
| **F11** | Bootstrap manual (6.3) + ensaio de banco vazio (num banco à parte, ou depois de backup — `down -v` apaga as transações carregadas da carteira; subir, bootstrap, conferir contagens) + `Claude.md` e `docs/PENDENCIAS.md` atualizados | F1–F10 |

### 7.1 Andamento

| Fase | Estado |
|---|---|
| F1 | **Implementada em 27/09/2026.** V28 (`issuers`, `unit_compositions`, colunas em `assets`); pacote `integration/cvm` (`CvmOpenDataClient` com cache em `carga/data/cvm`, leitores de cadastro, FCA e informe de FII); `domain/issuer` (entidades, `IssuerImportService`); `POST /api/v1/admin/issuers/import`; `./data` inteira montada no contêiner (`/app/data`). Testes: recortes reais da CVM em `src/test/resources/cvm`, cliente com servidor HTTP local, `IssuerImportIT`. Bateria completa verde (350 + 143). Diferença do texto: entram **todos os FIIs negociados em bolsa** no informe (não só os do catálogo), porque a ligação por ISIN é da F2/F3; o seed (M3) leva só os ligados |
| F1 — carga real (27/09) | **367 companhias** (todas ATIVO) e **786 FIIs**; classificação B3 em 355 companhias — 12 sem resposta da B3 (JBS, EDP, concessionárias de capital fechado e afins). Segunda rodada: 0 novos, nada rebaixado (304 da CVM), idempotente. Ajuste feito: a B3 também manda "Motores . Compressores" (ponto solto) → vírgula. **Achado para a F3:** 41 dos 345 CNPJs de FII do catálogo não entraram porque a CVM os marca como **não negociados em bolsa** (`Mercado_Negociacao_Bolsa = N`; PABY11, KISU11, ROOF11, ARRI11…) — o campo não é confiável. Estão todos no informe; a F3 cria esses emissores a partir da lista de CNPJs conferida (1.6), e o seed (M3) os fixa |
| F2 | **Implementada em 27/09/2026.** V29 (`asset_price_history.source`: `BRAPI`, `COINGECKO`, `B3_COTAHIST`; linhas de cripto marcadas `COINGECKO`); `PublicFileCache` (cache de arquivos compartilhado, usado pela CVM e pela B3); `CotahistQuote` (layout posicional, fator de cotação) e `B3HistoricalQuotesClient` (`carga/data/b3/cotahist`); `HistoricalQuotesImportService` (ACAO lote padrão e fracionário + FII, upsert em lote, ISIN do catálogo); `ClosingPriceService` não sobrescreve dia `B3_COTAHIST`; `POST /api/v1/admin/b3/cotahist?from=&to=`. Regras levantadas na carga: anos lidos do mais recente para o mais antigo (o ano novo ensina o ISIN atual, os antigos acham o ticker de antes da renomeação); só mercado à vista casa pelo ISIN (o fracionário tem o mesmo ISIN do lote padrão) e o fracionário nunca é destino dessa busca (o catálogo tem fracionários sem o lote padrão, como CALI3F sem CALI3 — sem a regra, o pregão do CALI3 caía no CALI3F); linha achada pelo ISIN nunca passa por cima da do próprio ticker no mesmo dia. Testes: linhas reais de 24–25/09/2026, fator 1.000, ticker antigo pelo ISIN, BRAPI × B3 no `FechamentoIT` |
| F2 — carga real (27/09) | **2.228.704 pregões** de 2010-01-04 a 2026-09-25, ~12 min (download de ~1,3 GB incluso); **todos** os 786 ACAO ativos, 9 inativos e 347 FIIs com série; **ISIN em 1.142 de 1.142** ações e FIIs, todos com a raiz do ticker. PETR4: 37,32 em 04/01/2010 e 47,99 em 25/09/2026. Linhas pelo ISIN (renomeações): de 483 (2024) a 4.057 (2013) por ano. Preço abaixo de R$ 0,01 é real (ex.: CBEE3 em 2016, cotada por lote de mil antes de grupamento). Rodada de novo depois da regra do fracionário (pregões de fracionário apagados e recarregados): mesmos 2.228.704 pregões, 0 ISIN alterado — idempotente, e sem rede para os anos fechados |
| F3 | **Implementada em 27/09/2026.** `IssuerLinkService`: ações pelo FCA (tipo do valor mobiliário e sigla PNA/PNB…), pela raiz do ticker e pela B3 (`GetListedSupplementCompany` → código CVM); fracionário segue o lote padrão; composição da unit pelo texto do FCA (`UnitCompositionText`, 9 formatos reais); FIIs pelo ISIN → informe da CVM, criando o emissor mesmo quando a CVM marca "fora de bolsa"; lista curada (`POST /api/v1/admin/issuers/fii-cnpjs`, liga como `MANUAL`); ligação manual (`PUT /api/v1/admin/assets/{id}/issuer`); `POST /api/v1/admin/issuers/link`. `MANUAL` nunca é sobrescrito; a gravação é por SQL nas três colunas (não regrava o ativo). Ajuste na classificação da B3: ponto duplo depois de abreviatura ("Serv.Méd.Hospit..Análises") volta a ser ",". Testes: `UnitCompositionTextTest`, `IssuerLinkIT` |
| F3 — carga real (27/09) | **Ações: 789 de 795 ligadas** — FCA 725, raiz 19, B3 45; sem emissor: FSPE11F, FSRF11F, FSTU11F, PSVM11F (fracionários de FI-Infra/valores que não são ação de companhia) e WDCN3/WDCN3F. **16 units com composição**; BPAC11 e ONCO11 (e os F) sem texto no FCA. Setor B3 = categoria atual em todas as ações classificadas. **FIIs: 347 de 347** — 297 pelo ISIN, depois a lista curada (`fiis_cnpj_curado.json`, VGRH11 corrigido): 294 confirmados, 29 ligados, 21 emissores criados (fundos "fora de bolsa"), VGRH11 criado sem informe, e **2 correções reais** (SPTW11 e TRXF11: o ISIN do informe apontava outro fundo). Planilha de conferência: `docs/emissores/Curadoria_Emissores_2026-09-27.xlsx` |
| F3 — curadoria (27/09) | Planilha conferida: `Curadoria_Emissores_2026-09-27_corrigida.xlsx`. Código: a passada da B3 cria a companhia pelo cadastro da CVM quando o código CVM ainda não é emissor (companhia fora do critério D2); `PUT /api/v1/admin/assets/{id}/issuer` aceita `issuerId` **ou** `cnpj` e `shareClass` nula (o que não é ação); `PUT /api/v1/admin/assets/{id}/unit-composition` (composição manual, marca `MANUAL`). Aplicado: **WDCN3/WDCN3F → Livetech da Bahia** (CNPJ 05.917.486/0001-40, CVM 25895, pela B3); **BPAC11/BPAC11F = 1 ON + 2 PNA** (MANUAL); **ONCO11/ONCO11F** sem classe e sem composição — bônus de subscrição (ISIN BRONCON01OR0), emissor Oncoclínicas mantido (MANUAL). A mesma passada também achou **PSVM11F → Porto Sudeste V.M.** (CVM 23523; a planilha pedia manter sem emissor — Guilherme aprovou a ligação em 27/09; ativo inativo, fica como unit sem composição). Sem emissor: FSPE11F, FSRF11F, FSTU11F. Emissores: 369 companhias, 829 FIIs. Demais itens sem correção (SPTW11/TRXF11 MANUAL, ligações por raiz/B3, setor B3 × categoria, segmento curado de FII) |
| F4 | **Implementada em 27/09/2026.** **V30** (M3, `V30__seed_issuers.sql`, 1.937 linhas, gerado do banco por `carga/scripts/gerar_seed_emissores.mjs`): 369 companhias (todas as importadas) e 346 FIIs (os ligados ao catálogo; TRPL11 e TRPL15 são o mesmo fundo) com CNPJ literal e **segmento curado** em `issuers.segment` (vindo de `asset_categories.segment`; no fundo com duas cotas vale o da cota 11); 1.139 ligações (792 ações + 347 FIIs) com classe, origem (`FCA`/`PREFIXO`/`B3`/`ISIN`/`MANUAL`) e ISIN; 36 linhas de composição de 18 units. Chave natural (CNPJ, ticker); no banco que já tem emissores, `ON CONFLICT` só grava o segmento curado e completa a classificação B3 que faltar. **V31** (M4): uma categoria por `(tipo, name)` — ativos das duplicadas para a de menor id —, sai `segment`, unicidade `(asset_type_id, name)`; 165 → 80. Código: `AssetCategory.segment`, `AssetCategoryResponseDTO.segment` e `types/index.ts` sem o campo. Antes do seed, a rotina de emissores rodou de novo e classificou Livetech e Porto Sudeste pela B3; seguem sem classificação 12 companhias que a B3 não responde, nenhuma ligada ao catálogo. Verificação: `SchemaTest` num banco vazio reproduz os mesmos números (715 emissores, 1.139 ligações, 36 linhas de unit, HGLG11 → Imóveis Logísticos, PETR4 → Petrobras PN com ISIN); no banco de desenvolvimento a V30/V31 aplicaram em 0,25 s e a contagem de ativos por (tipo, categoria) ficou idêntica antes e depois |
| F5 | **Implementada em 28/09/2026.** **V32** (`cvm_documents`, `cvm_statement_values`, `share_counts`). Integração: `CvmStatements` (DFP/ITR: só `ÚLTIMO`, até o 4º nível, versão mais alta, consolidado e individual, composição do capital; filtra as companhias emissoras na leitura), `CvmFreCapital` (histórico de capital do FRE, novo conjunto `carga/data/cvm/fre`), `CvmZip` passou a `ZipFile` (acesso direto: o ITR tem ~500 MB descompactado). Domínio (`domain/fundamentals`): `FinancialMetric` (14 métricas), `FinancialMetricRules` (**por descrição**, 1.1), `ShareScale` (escala 1 ou 1.000 da composição: FRE, depois LPA), `FreCapitalHistory` (quantidade numa data = FRE de referência mais próxima — a quantidade acompanha o formulário, não o evento: no FRE 2025 o aumento de 2014 da Petrobras aparece com 12,89 bi, no FRE 2015 com 13,04 bi), `FinancialStatementImportService` (idempotente por versão, `force`; passada final pelo **vizinho**: REVISAR resolvido pelo documento validado mais próximo, anterior ou seguinte). `POST /api/v1/admin/cvm/import`, `CvmStatementsScheduler` (07:30, ano corrente e anterior). Testes: recortes reais (DFP 2025 de Petrobras, Itaú, BB, IRB, Klabin, Telebras, Vivara e Camil; ITR 2026; DFP e FRE 2015) — `FinancialMetricRulesTest`, `ShareScaleTest`, `FreCapitalHistoryTest`, `FinancialStatementImportIT` |
| F5 — carga real (28/09) | Download: DFP 2010–2026, ITR 2011–2026 e FRE 2010–2026 (~710 MB em `carga/data/cvm`: DFP 168, ITR 406, FRE 133). Carga completa em **5 min**: **19.349 documentos** (4.937 DFP, 14.412 ITR) das **369 companhias**, 293 mil valores; layout gravado em todas (348 comercial, 19 banco, 2 seguradora). Lucro da controladora, PL e receita em **100%** dos documentos; DVA falta em 147 (0,8%); 4.153 individuais (D6). Quantidade de ações: composição 9.242 (8.608 pelo FRE, 399 pelo LPA, 117 pelo vizinho, **118 REVISAR**), FRE 9.534 (6.743 confirmados pelo LPA, 2.717 sem LPA que confirme, 73 pelo vizinho, **1 REVISAR**), 573 sem quantidade (companhia sem FRE antes de 2020). REVISAR restante: erro na fonte (Grupo Mateus 06/2022: 22.093.460.790, um dígito a mais; Azul 03/2026: 54 trilhões) e grupamento no meio (Alphaville, CBA). Conferência da Petrobras 2025 (1.1): **LPA 8,54, VPA 32,26, payout 37,4%** — iguais. LPA calculado × informado nos DFP: 82% dentro de 10%, 90,5% dentro de 2x; dos 375 fora, 100 são sinal trocado no LPA informado e 58 LPA em outra escala (a fonte), ~217 diferenças moderadas em *small caps* antigas (fator 10 entre FRE e LPA, grupamentos) — o LPA informado só confere, o indicador (F9) usa lucro ÷ quantidade |
| F6 | **Implementada em 28/09/2026.** **V33** (`company_quarters`). `QuarterSeries` (regra pura, só datas e durações — serve ao exercício de março a fevereiro e ao primeiro exercício incompleto): trimestre isolado = período do documento que cabe num trimestre (coluna de 3 meses do ITR, ou o acumulado do 1T), senão acumulado − acumulado do documento do trimestre anterior (80 a 100 dias antes) com o mesmo início de exercício — daí o 4T (DFP − 9 meses), a DFC e a DVA; 12 meses = anual do DFP, senão soma dos 4 trimestres consecutivos, todos presentes; posição do próprio balanço; PL da controladora = PL − não controladores; DFP vale sobre ITR na mesma data. `CompanyQuarterService` recalcula tudo numa transação (lê os valores em uma consulta, companhia por companhia) ao fim de `cvm/import` e em `POST /api/v1/admin/cvm/quarters`. Entidade `CompanyQuarter` e repositório para a F9. Colunas a mais que o plano: `ebit`, `net_income` e `fco` também em 12 meses, `dividends_declared_q`, `shares_total`, `fiscal_year_start`. Endpoint de leitura `GET /issuers/{id}/quarters` fica na F9, com os outros. Testes: `QuarterSeriesTest` (ano civil, ITR faltando, Camil, posição), `FinancialStatementImportIT.serieTrimestral` (Petrobras 2025–2026 dos recortes) |
| Separação em módulos (28/09) | Entre a F6 e a F7, o backend virou `primevest-core` + `primevest-app-api` (8080, a carteira) + `primevest-adm-api` (8081, o catálogo) — docs/planos/PLANO_ADM_API.md. Todo o código das F1–F6 (emissores, COTAHIST, balanços, série trimestral, `/admin/*`) está na adm-api; entidades (`Issuer`, `CompanyQuarter`...) e migrations no core. Sem mudança de regra nem de tabela: a bateria da F6 (374 + 158) passou igual, agora 376 + 161 com os testes das mensagens |
| F6 — carga real (28/09) | **19.344 linhas** (19.349 documentos − 5 datas com DFP e ITR) das 369 companhias em **3,4 s**. Lucro da controladora do trimestre em 18.977 (98%): os 367 nulos são DFP sem o ITR de 9 meses — 249 de 2010 (o ITR começa em 2011) e 114 de companhia recém-listada; 4 com início de exercício diferente. 12 meses em 18.215 (94%); ações em 18.652. Petrobras (conferida com o divulgado): 2T24 −2,61 bi, 4T24 −17,04 bi, 2024 = 36,61 bi; 2025 = 110,13 bi. LPA do trimestre (lucro ÷ ações) × LPA de 3 meses informado no ITR: 81% dentro de 10% em 11.240 trimestres — mesma taxa do anual (F5). **Soma dos 4 trimestres × anual do DFP:** igual em 3.996 de 4.561 exercícios; nos 565 restantes o ITR seguinte reapresentou o trimestre (334 dentro de 5%) ou a fonte tem erro (Cemig 09/2021: lucro da controladora 0 e não controladores 1,1 mi num lucro de 2,8 bi — o 4T de 2021 sai com o ano inteiro). Por isso o 12 meses no DFP usa o anual, não a soma |
| F7 | **Implementada em 28/09/2026 (adm-api).** **V34** (`fii_monthly`, no core, com `FiiMonthly` e repositório para a F9). `CvmFiiMonthly` lê as partes **complemento** (PL, ativo, cotas, VP por cota, cotistas, taxa de administração, DY e amortização do mês) e **ativo/passivo** (liquidez, total investido, imóveis, imóveis via SPE, recebíveis = CRI + CRI/CRA + LH + LCI + LCI/LCA + LIG, cotas de FII, passivo, rendimentos a distribuir) do mesmo zip do informe (`carga/data/cvm/fii`), junta por CNPJ e mês e fica com a maior versão de cada parte. `FiiMonthlyImportService`: só fundos emissores, idempotente por versão, `force`. `POST /api/v1/admin/cvm/fii-monthly?from=&to=&force=`; rotina das 07:30 (`CvmStatementsScheduler`) roda balanços e informe, um não impede o outro. Diferenças do modelo: taxas como **fração do mês** (`*_month`, não `_pct`), `version`, e as colunas da carteira separadas (`real_estate_companies`, `receivables_total`, `fii_shares_total`, `liquidity_total`, `total_invested`) no lugar de `securities_total`/`cash_total` — a F9 compõe "imóveis × papel". `NUMERIC` sem precisão: o informe é digitado e traz extremos (DY de 8,7e7, cotas de 6,8e14). Testes: recorte real de 2026 (HGLG11 e 00.613.094/0001-74, parte ativo/passivo e complemento), `FiiMonthlyImportIT` (junção, versão, idempotência, reapresentação) |
| F7 — carga real (28/09) | Download de 2016 a 2024 (2025 e 2026 já estavam em `carga/data/cvm/fii`). **42.815 meses** de **828 fundos** em **10 s**; segunda rodada: 0 novos, 42.815 iguais. Os dados abertos começam em **09/2016** (1 fundo; 44 em 10/2016, 129 em 01/2017). Dos 346 FIIs do catálogo, **345 com informe** — falta só o VGRH11 (fundo novo, 1.6). HGLG11 08/2026: PL 7,57 bi, 45,6 mi de cotas, VP 165,95, 608.345 cotistas, DY do mês 0,70%, imóveis 7,06 bi, passivo 1,28 bi — iguais ao arquivo |
| F8 | **Implementada em 28/09/2026 (adm-api).** **Escopo:** a rodada de proventos (20:00, `ProventosCatalogService`) cobre todo ativo ativo ligado a emissor + o que já esteve em carteira (654 emissores); `primevest.proventos.scope=HELD` volta ao escopo antigo (é o dos testes de ponta a ponta da carteira). **Histórico longo** uma vez por ativo, até completar: ações pelo `GetListedCashDividends` (1 chamada/s); FII **só pelo que está em `carga/data/fnet`** — nenhuma chamada ao FNET (decisão de 28/09 com o Guilherme: os XMLs foram montados pelo FNET em 27/09 e não há aviso mais novo; a busca por fundo levava 3 a 10 s, travava e custou ~1 min por fundo na primeira tentativa de carga). **`FnetClient` refeito:** cache `carga/data/fnet/{id}.xml` (lido do disco, nunca baixado de novo), download com timeout de 5 s, até 6 tentativas com espera crescente, 3 simultâneos num executor próprio; busca por CNPJ com timeout de 20 s e 3 tentativas; **índice local** `CNPJFundo` → avisos, montado da pasta `carga/data/fnet` (`cachedNoticeIds`, `reindex`); **lê os dois formatos de XML** (1.5 — o antigo era ignorado). Regras de 1.5 no serviço: aviso ligado pelo CNPJ do emissor (`issuers.cnpj`; aviso de outro CNPJ fica de fora), recibo (final 12–19) fora, data-base depois do pagamento fora com log; fundo com uma cota leva o aviso mesmo com ticker antigo; valor igual ao de outra fonte com outras casas (B3 1,04 × FNET 1,03999999900318) completa o evento em vez de duplicar. **Concorrência (PENDENCIAS 14.1):** sem `synchronized`; rodada completa não roda duas vezes; pedido sob demanda numa fila única (um executor, sem repetir ativo); o mesmo emissor nunca por dois caminhos (`ReentrantLock` por grupo). **Sob demanda revisto:** mantido — quase sempre termina sem chamada (emissor sincronizado nas últimas 6 h, histórico carregado), mas cobre ETF/BDR sem emissor e ativo novo antes das 20:00. **Decisão:** depois do histórico o dia a dia do FII é só a B3 (12 meses, com pagamento). Aviso novo do FNET entra por busca explícita — endpoint por CNPJ ou rotina semanal, ainda não feitos (PENDENCIAS 18.10 d); o `FnetClient` já tem a busca e o download com cache. Testes: `FnetClientTest` (avisos reais nos dois formatos, CNPJ pontuado, `WPLZ11B`, recibo, data-base 2108, cache), `ProventosCatalogoIT` (catálogo sem posição, FNET pelo CNPJ só do disco — a busca não é chamada —, B3 fora, idempotência, sem duplicar B3 × FNET) |
| F8 — carga real (28/09) | Primeira tentativa (12:04–12:50 BRT) com busca no FNET por fundo: parada — a busca respondeu mal no dia (3 a 10 s, timeouts, HTTP 520), ~1 min por fundo sem resposta. Rodada final, só com o disco para FII: 12:59–13:22 BRT, **654 emissores, 0 falhas**, 29.900 eventos em dinheiro e 545 societários novos, 1.133 ativos enviados para a carteira. Resultado: `asset_cash_events` **54.565** (B3_HISTORICO 31.618, FNET 17.500, B3 5.426, EXTRATO_B3 21) contra 1.153 antes; `asset_stock_events` 1.053 (antes 20); **histórico completo em 785 de 785 ações e 347 de 347 FIIs** ligados; índice do FNET com 22.911 avisos de 324 fundos, nenhum baixado; 61 avisos com data-base depois do pagamento descartados (log); 103 eventos com rótulo que o `DividendType` não mapeia (gravados, sem gerar provento — conferir na F9). Os 23 tickers sem aviso em disco não distribuíram ainda (1.5): zero eventos, sem falha |
| F9 | **Implementada em 28/09/2026 (app-api).** `IndicatorRules` (fórmulas puras, 5.1–5.3), `IndicatorService` (lê `company_quarters`, `fii_monthly`, proventos, eventos societários e preços por SQL e calcula na leitura — nada gravado), `IndicatorController` (`GET /assets/{ticker}/indicators` e `/indicators/{indicador}/history?period=YEAR|QUARTER`), `IssuerController` (`GET /issuers/{id}`, `/quarters`, `/months`). Ação: LPA, VPA, P/L, P/VP, ROE (PL médio de início e fim — balanço de 12 meses antes, ±20 dias), payout (DVA), DY. FII: P/VP, DY, VP por cota, cotistas, imóveis (imóveis + via SPE) e papel (recebíveis) sobre o total investido, alavancagem (passivo ÷ ativo). **Atual:** último balanço/informe com `current_price` (senão o último fechamento); quantidade de ações ajustada pelos desdobramentos, grupamentos e bonificações posteriores ao balanço. **Ano:** DFP com o fechamento do último pregão até a data do balanço; FII, informe de dezembro a partir de **2017** (primeiro ano inteiro de informe e de FNET). **DY:** dividendo + JCP + rendimento do próprio ticker (sem amortização nem restituição de capital), provento anterior a desdobramento ajustado; **sem provento = 0**. Fracionário usa preço, eventos e proventos do lote padrão; unit multiplica LPA/VPA pela composição. Percentual em pontos (37,44), R$ com 4 casas, razão com 2. Decisões: a estimativa de rendimento do informe (1.2) **não é usada** — FNET e B3 cobrem 2016+ e fundo sem evento é fundo que não distribuiu (23 tickers conferidos); payout com dividendos negativos na DVA fica nulo com motivo (269 de ~9.700 trimestres, reapresentação na fonte — ex.: Klabin 06/2026); **sem cache Redis** por enquanto — 20 a 70 ms por chamada depois do aquecimento (a primeira, ~1,5 s), cache fica para quando houver tela de ranking. Testes: `IndicatorRulesTest` (Petrobras 2025 e HGLG11 08/2026 reais, nulos com motivo, zero, unit, desdobramento, média), `IndicadoresIT` (ponta a ponta, fracionário, FII sem informe, erros, emissor) |
| Decisões pós-F9 (28/09) | Com o Guilherme. **Amortização da B3:** o rótulo `AMORTIZACAO RF` (a B3 nunca manda `AMORTIZACAO` puro) passa a ser amortização; a B3 usa o valor já gravado pelo FNET e completa em vez de duplicar. V35 nos 103 gravados: 87 eram a mesma amortização do FNET (saíram) e 16 só da B3 (RTEL16, VVMR11) ganharam o tipo. **Quantidade de ações em revisão:** terceira passada da escala, `EVENTO` (V36, `resolveByStockEvents`) — quantidade do balanço validado anterior × grupamentos/desdobramentos/bonificações registrados entre as datas, primeiro até o balanço e depois até a entrega do documento (a companhia informa já grupado quando o grupamento sai entre o fim do trimestre e a entrega); no indicador, sem evento no meio, vale a quantidade do último balanço conferido, com o motivo. Base real: 12 documentos resolvidos pelo evento (REVISAR 119 → 107); das 13 companhias, só AZEV4 e IFCM3 seguem sem LPA (evento no meio que não explica o número). Testes: `ProventosCatalogoIT.amortizacaoRf`, `FinancialStatementImportIT.grupamentoEntreBalancos`, `IndicadoresIT.quantidadeEmRevisao` |
| F9 — conferência (28/09) | Com a base real: **PETR4 2025** LPA 8,5446, VPA 32,26, payout 37,44%, ROE 28,17% — iguais ao conferido (1.1); DY 2015–2017 = 0 (não pagou), 2022 = 68,4%. Atual (ITR 06/2026, R$ 47,99): P/L 4,64, P/VP 1,29, ROE 30,31%, DY 7,67%. **HGLG11** atual: P/VP 0,89, DY 9,02%, VP 165,95, 608.345 cotistas, imóveis 97,8%, alavancagem 14,5%; DY 2017–2025 entre 4,4% e 10,0%. **SBSP3** (desdobramento 1:5 em 04/2026) coerente: LPA 2,29, P/L 11,75. **APXR11** (não distribuiu): DY 0, demais pelo informe. **VGRH11**: DY 0, resto nulo "sem informe mensal". **13 de 307** companhias ligadas estão sem quantidade de ações no último balanço (escala em REVISAR, 18.7) — LPA/VPA/P/L/P/VP nulos com motivo; ex.: AZTE3 |
| F10 | **Implementada em 28/09/2026.** **V37** (M7): `assets` sem `cnpj`, `cash_history_loaded_at`, `logo_path` e `logo_checked_at`. **Logos:** o arquivo `carga/data/logos/{TICKER}.{ext}` é o estado — `AssetLogos` (core) indexa a pasta em memória (relê a cada 60 s: a app-api lê o que a adm-api grava) e dá o `hasLogo` dos DTOs (`logoFlag`); `AssetLogoService` busca só quem não tem arquivo (rotina mensal), tudo de novo em `POST /admin/logos`, e o ativo que entra numa carteira no pedido "preciso do histórico"; a subida da API não procura nada; busca sem resultado não apaga o arquivo que existe; troca de logo apaga o de outra extensão. **Histórico de proventos sem coluna:** ação — a resposta inteira da B3 fica em `carga/data/b3/proventos/{EMISSOR}.json`, pedida uma vez por emissor e aplicada ao ativo que ainda não tem evento `B3_HISTORICO` (sem arquivo e sem evento → busca; com arquivo → só disco); FII — o índice de `carga/data/fnet` contra os avisos gravados, lido do disco a cada rodada (aviso novo que cair na pasta entra sozinho). **CNPJ do fundo:** só `issuers.cnpj`; `CvmFundRegistry` removido (todo FII do catálogo tem emissor desde a F3). Testes: `AssetLogosTest`, `ProventosCatalogoIT.historicoDaEmpresaEmDisco` (baixa uma vez, relê do arquivo sem a B3), mocks da carteira sem o registro da CVM; bateria **392 + 180** verde |
| F10 — base real (28/09) | V35–V37 aplicadas no banco de desenvolvimento. Logos: 1.451 arquivos, nome = ticker em 100% (nenhum `logo_path` divergente antes da V37); `hasLogo` dos 225 proventos da tela igual à presença do arquivo. Proventos (rodada completa): 654 emissores, 0 falhas, 0 eventos novos; os **52 emissores** sem nenhum `B3_HISTORICO` (ações que nunca pagaram, sobretudo) buscaram o histórico uma vez e ganharam o arquivo; na rodada seguinte (interrompida pelo hot reload em ~85% dos emissores) nenhum histórico pedido à B3, 0 falhas. Log do FNET só em INFO quando entra evento, baixa ou falha (a releitura diária do disco fica em DEBUG) |
| F11 | **Implementada em 28/09/2026.** `BootstrapService` + `POST /api/v1/admin/bootstrap` (6.3): COTAHIST → proventos → balanços (com série trimestral e escala `EVENTO`) → informe de FII, em ordem; etapa que falha fica registrada e a seguinte roda; uma carga por vez; nunca dispara ao subir. Testes: `BootstrapServiceTest` (ordem, falha não para) |
| F11 — ensaio de banco vazio (28/09) | Banco à parte (`primevest_ensaio`, mesmo PostgreSQL) com uma adm-api própria (porta 8091, sem rotinas agendadas e sem Redis, `carga/data/` real), sem tocar o banco de desenvolvimento. Flyway V1–V37 em **2 s**; seed: 2.625 ativos, 715 emissores, 1.139 ligações, 36 linhas de unit. Carga: **47 min** — COTAHIST 9,5 min (2,23 mi de pregões), proventos 32 min (653 emissores; o histórico das ações ainda não estava em disco e foi baixado **uma vez**: `carga/data/b3/proventos` com 307 arquivos, 3,8 MB — o próximo banco novo não pede histórico à B3), balanços 4,5 min (19.349 documentos, 202 quantidades pelo vizinho e 12 pelo evento), informe 6 s. 9 emissores com falha de rede na B3 — rodar a rotina de novo completou (0 falhas): **retomável** conferido. Contagens contra o banco de desenvolvimento: `cvm_documents` 19.349 = 19.349, `company_quarters` 19.344 = 19.344 (soma de lucro 12 meses e de ações idênticas), REVISAR 107 = 107, `asset_stock_events` 1.053 = 1.053, `fii_monthly` dos fundos do catálogo 25.538 = 25.538 (soma do VP por cota idêntica), `asset_cash_events` 54.469 × 54.478 (o de desenvolvimento tem 21 do extrato de usuário; a janela de 12 meses da B3 andou), preços 2.229.815 × 2.229.826 (11 fechamentos BRAPI/CoinGecko). Diferença esperada: o banco novo tem só os emissores do seed (715 × 1.198) e o informe só dos fundos do catálogo (25.538 × 42.815). Lição: processo longo lançado pela sessão remota morre quando a ligação cai — a instância do ensaio foi relançada desligada da sessão (por um `subir.cmd` avulso). Banco de ensaio apagado ao fim |

Fora deste escopo: **telas** (página do emissor, "Histórico de Indicadores" — planejamento próprio) e **preço
teto** (próximo escopo, consome a F9).

---

## 8. O que está pendente fora das decisões

| Item | Quem | Quando |
|---|---|---|
| ~~Copiar os XMLs do FNET para `carga/data/fnet`~~ | Guilherme | ✅ 27/09 (22.910 avisos) |
| ~~CNPJ dos FIIs~~ | Guilherme | ✅ 27/09 (planilha conferida, 1.6) |
| ~~CNPJ do VGRH11~~ | Guilherme | ✅ 27/09: 67.624.043/0001-19 |
| ~~`/data/` no `.gitignore`~~ | — | ✅ 27/09 |
| ~~Conferir a curadoria (ligações de ações, units, segmentos de FII) antes de gerar o seed~~ | Guilherme | ✅ 27/09 (planilha corrigida, aplicada) |

---

## 9. Decisões (todas tomadas em 27/09/2026)

| # | Decisão |
|---|---|
| D1 | Histórico desde **2010** (DFP 2010, ITR 2011) — máximo dos dados abertos e início do IFRS. FII desde 2016 (início do informe) |
| D2 | Empresas: ATIVO + BOLSA (~345) + as ligadas a ativos do catálogo, mesmo canceladas |
| D3 | Preço **diário desde 2010**: B3 (COTAHIST) para o histórico, BRAPI para o dia a dia |
| D4 | ROE com PL médio; atual, histórico e média da empresa como na tela de referência |
| D5 | **Proventos de todas as empresas e FIIs nesta fase**; DY por ticker; payout pela DVA |
| D6 | Sempre consolidado; individual só quando não houver consolidado |
| D7 | Mesmo LPA para todas as classes |
| D8 | Fracionário usa o preço do lote padrão |
| D9 | Bancos e seguradoras com mapa próprio; indicadores de dívida nulos |
| D10 | **FII entra neste escopo**, com emissor genérico (`issuers.kind`) |
| D11 | **Segmento sai da categoria em todos os tipos** (ação e FII para o emissor; ETF, BDR e cripto sem) |
| D12 | Sem tabela auxiliar de seed: curadoria e CNPJs de exceção vão no seed de `issuers` |
| D13 | Carga inicial **manual**, por endpoint de admin |
| D14 | **Guardar todos os arquivos** baixados (~2 GB em `carga/data/`) |
| D15 | `logo_checked_at` e `logo_path` saem; o arquivo em `carga/data/logos` é o estado |
| — | Média do setor e remoção de anomalias fora do escopo |

---

## 10. Arquivos fora do repositório

Uma pasta avulsa `cvm` — amostras do levantamento (cadastro, DFP 2025, ITR 2026, FCA 2026, informes FII
2024–2026, COTAHIST 2025 e 2026). `%TEMP%\fnet` — teste do FNET com HGLG11 (`log.txt` com cada tentativa). `%TEMP%\fnet_idx.txt` — índice
(id; CNPJ; tickers; ISINs; data-base) dos 22.910 avisos, usado na conferência de 27/09.
Podem ser apagados; a aplicação baixa os próprios.
