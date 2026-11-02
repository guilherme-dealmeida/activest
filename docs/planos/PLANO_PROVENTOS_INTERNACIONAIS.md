# Plano — Proventos para ativos internacionais Activest

> Aberto em 01/11/2026 (POR ESCOPO). **Escopo encerrado em 02/11/2026** (validado e comitado pelo Guilherme). Encerra as 3 partes da branch. Parte 3 de 3 da branch
> `TRACK_01-ativos_internacionais_precos_proventos` (1: `PLANO_PRECOS_INTERNACIONAIS.md`; 2:
> `PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md`).

## 1. Objetivo

Popular o catálogo (`asset_cash_events`) e os arquivos de `carga/data` com os proventos dos ativos internacionais (Stock,
REIT, ETF internacional), **no mínimo 2 anos** (o mesmo período do preço histórico), de preferência pelo Massive gratuito
ou por outra fonte gratuita (descrição do Guilherme).

## 2. Discovery

### 2.1 O que já existe

| Peça | Situação |
|---|---|
| Provento do usuário em moeda estrangeira | **Pronto** desde o escopo de moedas (V10, `ForeignDividendCalculator`): valor por cota na moeda × PTAX compra do pagamento, retenção do país de origem (`foreign_tax_rules`, `WITHHOLDING_FOREIGN`), IR brasileiro estimado, líquido. `DividendRecalculator` gera os proventos da carteira a partir do catálogo |
| Catálogo de proventos | `asset_cash_events` (tipo DIVIDENDO/JCP/RENDIMENTO/AMORTIZACAO/RESTITUICAO_CAPITAL; `data_com`, `payment_date`, `approved_on`, `value_per_share`, `label`, `source` só B3, B3_HISTORICO, FNET). **Nenhum evento do exterior** |
| Aviso à carteira | `CatalogChange.PROVENTOS` → a app-api gera os proventos das posições e a notificação de provento novo |
| Desdobramentos | `asset_stock_events` (DESDOBRAMENTO, GRUPAMENTO, BONIFICACAO; `factor`, `data_com`) — só B3 |

### 2.2 Prova no Massive gratuito (01/11/2026, 7 pedidos, nada gravado)

| Pedido | Resultado |
|---|---|
| `GET /v3/reference/dividends?ticker=O` | HTTP 200: `cash_amount`, `currency`, `declaration_date`, `ex_dividend_date`, `record_date`, `pay_date`, `frequency`, `dividend_type`, `id` — inclusive o anunciado e ainda não pago (O, ex 30/10/2026, paga 13/11) |
| `ticker=IAU` | Vazio (o trust de ouro não paga — correto) |
| Profundidade | **O em 2012 e KO em 2010 vieram no plano gratuito**: o limite de 2 anos do preço não vale aqui |
| Mercado inteiro por data (`ex_dividend_date.gte/lte`, `limit=1000`, `next_url`) | 01–05/03/2025: mais de 1.000 eventos (página cheia), 96% em USD, tipos CD (normal) e SC (especial); vêm também OTC e outras moedas (JPY, CAD, EUR, CHF) |
| `GET /v3/reference/splits` | HTTP 200 também no gratuito (`execution_date`, `split_from`, `split_to`), inclusive os futuros |

Volume estimado (a medir na carga): ~1.000+ eventos a cada poucos dias → **2 anos ≈ 300 páginas ≈ 1h** a 5 pedidos/min;
**desde 2010 ≈ 2.000+ páginas ≈ 7–8h** (uma vez só; depois, a rotina pega só a janela recente).

## 3. Decisões em aberto (Discovery)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Fonte | (a) Massive gratuito (`/v3/reference/dividends`), mesmo cliente e chave; (b) outra | **(a)** — prova feita, gratuito, inclusive histórico |
| D2 | Período da carga | (a) 2 anos (nov/2024 →, ~1h); (b) **desde 2010** (~7–8h, uma rodada longa, pode ficar rodando à noite); (c) 2 anos agora e 2010 depois | **(b)** — gratuito e igual à B3; os proventos anteriores a nov/2024 ficam prontos para quando o preço até 2010 vier |
| D3 | O que entra | (a) só tickers do catálogo (vivos e deslistados — o deslistado só com data-com até a saída) e só na moeda do ativo (USD); os de outra moeda são contados e ignorados; (b) tudo | **(a)** |
| D4 | Tipo | (a) CD e SC → DIVIDENDO, com o tipo da fonte no `label` (CD, SC, LT, ST); (b) tipo novo por categoria | **(a)** — no Brasil tudo é "dividendo do exterior"; o `label` guarda a origem |
| D5 | Data-com | (a) último pregão **antes** da ex-date (quem tem a cota no fechamento dele recebe; o pregão vem da série `MASSIVE`, senão o dia útil anterior); (b) a record date; (c) a própria ex-date | **(a)** — é a semântica da data-com da B3 |
| D6 | Pagamento e aprovação | `payment_date` = `pay_date`; `approved_on` = `declaration_date` | Como proposto |
| D7 | Arquivos | (a) cada página da fonte guardada em `carga/data/massive/dividends/AAAA/AAAA-MM-….json.gz` e no bucket; (b) só o banco | **(a)** — regra de manter os registros nos arquivos |
| D8 | Rotina contínua | (a) tarefa diária (ter–sáb, depois do preço) relendo a janela de ex-date de −30 a +90 dias (pega os anunciados e as correções); carga inicial por tarefa manual com período; (b) só manual | **(a)** |
| D9 | Desdobramentos/grupamentos do exterior (mudam a quantidade de cotas e, com ela, o provento) | (a) neste escopo, pelo `/v3/reference/splits` gratuito, em `asset_stock_events`; (b) escopo próprio | **(a)** — mesma fonte e mesma rotina; provento por cota precisa da quantidade certa |
| D10 | Prova na carteira | O IAU não paga provento; a prova fica nos testes e na conferência de um pagador no catálogo (ex.: O, KO) | Como proposto |

**01/11/2026 — Guilherme aprovou D1–D10 pela recomendação** ("Concordo com todas as recomendações"). Teste em
conjunto: o Claude pede o cadastro de ativos pagadores e os dois conferem. Observação dele: apagou do Cloudflare as pastas
do bucket antigo `primevest-data` (limite de 10 GB de upload), por precaução com o volume novo.

## 4. Impact

| Onde | O que muda | Risco se esquecido |
|---|---|---|
| Banco (migration nova) | `MASSIVE` em `ck_asset_cash_events_source`; tarefas novas (diária e carga) | Gravação recusada |
| core — `CashEventSource` | Valor `MASSIVE` | Leitura quebra |
| core — `CashEventWriter` | Hoje casa o evento por (ativo, tipo, data-com, **valor**, parcela): uma correção de valor da fonte viraria **outro** evento. Para o Massive, casar pelo `source_ref` (o `id` do evento) e atualizar valor/datas | **Alto**: provento duplicado na carteira |
| adm-api — `MassiveClient` | Rotas `/v3/reference/dividends` e `/v3/reference/splits` paginadas (`next_url`), no mesmo ritmo de 12 s; cada página guardada em `carga/data/massive/dividends/` e `…/splits/` (D7) | — |
| adm-api — serviço + tarefas | Casamento pelo ticker com o papel listado na data-com (mesma regra `listedOn` da parte 2, deslistado até a saída); data-com = último pregão `MASSIVE` antes da ex-date (D5); só USD (D3); `label` = tipo da fonte (D4); desdobramento → `asset_stock_events` (`split_to/split_from` > 1 → DESDOBRAMENTO com fator (razão − 1) × 100; < 1 → GRUPAMENTO com a razão); aviso `CatalogChange.PROVENTOS` com os ativos que mudaram | Data-com errada = provento para quem não tinha a cota |
| app-api | **Sem mudança**: `DividendRecalculator` + `ForeignDividendCalculator` já geram o provento em dólar (PTAX compra, retenção de 30% dos EUA, IR estimado) e a notificação; `PositionCalculator` já aplica `asset_stock_events` às cotas; o gráfico da tela de Ativos já ajusta por desdobramento | — |
| Docs | `Claude.md`, `README.md`, `SCHEDULE_TASKS`, `ADM_TASKS`, `ACTIVEST_DB`, PENDENCIAS (43.2 fecha) | — |

| Risco | Tratamento |
|---|---|
| Fonte devolve o valor já ajustado por desdobramento posterior | Conferir na prova conjunta com um pagador que desdobrou; se vier ajustado, desfazer pelo fator dos desdobramentos posteriores |
| Volume da carga desde 2010 (~2.000+ páginas, ~7–8h) | Tarefa com período e Stop; páginas em disco não voltam à rede; pode rodar por anos |
| Bucket com limite de 10 GB de upload | Páginas compactadas (`.json.gz`); conferir o tamanho antes do `data push` |
| Retenção de 30% para todos os EUA | É a regra cadastrada (V9); tratados como o REIT/ETF que retêm diferente ficam para outro escopo (pendência se aparecer) |

## 5. Planning

### 5.1 Peças

| Módulo | Peça |
|---|---|
| core | `V21__foreign_dividends_tasks.sql` (fonte `MASSIVE` no check; tipos e tarefas `FOREIGN_DIVIDENDS` — diária ter–sáb 03:30, recuperação na subida, `oncePerDay` — e `FOREIGN_DIVIDENDS_HISTORY` — só Run, `FROM_DATE` 2010-01-01, `TO_DATE` vazio = +90 dias); `CashEventSource.MASSIVE`; `CashEventWriter` casando por `source_ref` quando a fonte é `MASSIVE` |
| adm-api | `ForeignEventsProvider` (interface: proventos e desdobramentos por período de data, já na grafia do catálogo) e a implementação no `MassiveClient`; `ForeignCorporateEventsService` (casamento, data-com, gravação, aviso à carteira); `ForeignDividendsTask` e `ForeignDividendsHistoryTask`; rotas `/admin/foreign-dividends[?from&to]`; atalhos no `admin.mjs` |
| testes | Cliente (paginação, arquivo em disco, filtro de moeda), serviço (data-com pelo pregão anterior, deslistado até a saída, correção de valor sem duplicar, desdobramento e grupamento), tarefas, `SchemaTest`/`TaskControllerIT` |

### 5.2 Critérios de aceitação

| # | Critério |
|---|---|
| CA1 | Bateria completa verde |
| CA2 | Carga de 2010 até +90 dias concluída, com as páginas em `carga/data/massive/dividends/` e `…/splits/` e no bucket |
| CA3 | Conferência no catálogo de pagadores conhecidos (ex.: O mensal, KO trimestral): data-com = pregão anterior à ex-date, pagamento e valor iguais aos da fonte |
| CA4 | **Prova conjunta**: o Guilherme lança na carteira os ativos pagadores que o Claude pedir; o provento aparece em Dividendos com o valor em dólar, a PTAX, a retenção de 30% e o líquido, e na Carteira (coluna Proventos com a segunda linha em dólar) |
| CA5 | Rodar de novo não duplica nada; um desdobramento muda as cotas da posição a partir da data-com |

### 5.3 Etapas

| # | Etapa |
|---|---|
| E1 | Migration, enums e `CashEventWriter` |
| E2 | Fonte (proventos e desdobramentos no `MassiveClient`) |
| E3 | Serviço, tarefas, rotas e atalhos |
| E4 | Testing: bateria, carga desde 2010, conferência do catálogo e prova conjunta (CA4) |
| E5 | Review, documentação e `data push` |

### 5.4 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| E1–E3, testes, carga, conferência do catálogo, documentação, `data push` | ✅ | |
| Lançar os ativos pagadores pedidos e conferir Dividendos e Carteira (CA4) | | ✅ |
| Git | sugere a mensagem | ✅ |

## 6. Resultado da carga (01–02/11/2026)

| Item | Resultado |
|---|---|
| Execução 164 (`FOREIGN_DIVIDENDS_HISTORY`, 2010-01 a 2027-01) | SUCCEEDED, 20:24 → 04:06 (7h42), 205 meses, 2.279 páginas |
| Lidos na fonte | 1.974.546 proventos (1.635.296 de tickers fora do catálogo — OTC, fundos, papéis antes de entrar —; 1.620 de papéis do catálogo em outra moeda) e 19.613 desdobramentos |
| Gravado | **337.630 proventos** de **9.143 ativos** (data-com de 01/2010 a 01/2027, inclusive anunciados) e **5.527 desdobramentos/grupamentos** de 3.272 ativos |
| Conferência | O: data-com 29/10/2026 (ex 30/10), paga 13/11, US$ 0,2715 — igual à fonte; KO: data-com 14/09, paga 01/10, US$ 0,53 |
| Arquivos | `carga/data/massive/dividends/` (100 MB) e `…/splits/` (1,6 MB), um arquivo por mês; enviados ao bucket |

**Nota de implementação:** a correção sem duplicar ficou no serviço (casamento pelo `source_ref` com gravação em lote por
JDBC), em vez de mexer no `CashEventWriter` — mesmo efeito, sem tocar no caminho da B3.

### 6.1 Prova conjunta (CA4, 02/11/2026)

Guilherme lançou O e KO (10 cotas cada, 01/09/2026). Telas de Dividendos conferidas por ele; números conferidos no banco:

| Ativo | Data-com | Pagamento | Por cota | PTAX compra | Bruto | Retenção 30% | IR estimado | Líquido |
|---|---|---|---|---|---|---|---|---|
| KO | 14/09 | 01/10 | US$ 0,53 → R$ 2,759869 | 5,2073 (01/10) | R$ 27,59 | R$ 8,28 | R$ 0,00 | **R$ 19,31** |
| O | 29/09 | 15/10 | US$ 0,2715 → R$ 1,37148225 | 5,0515 (15/10) | R$ 13,71 | R$ 4,11 | R$ 0,00 | **R$ 9,60** |
| O | 29/10 | 13/11 (a receber) | US$ 0,2715 → R$ 1,4716929 | 5,4206 (30/10, a última carregada) | R$ 14,71 | R$ 4,41 | R$ 0,00 | **R$ 10,30** |
| KO | 30/11 | 15/12 (a receber, anunciado) | US$ 0,53 | 5,4206 (idem) | R$ 28,72 | R$ 8,62 | R$ 0,00 | R$ 20,10 |

O IR brasileiro estimado é zero porque a retenção de 30% supera os 15% (Lei 14.754/2023). O provento a receber usa a
última PTAX carregada e é refeito quando o câmbio novo chega (`CatalogChange.CAMBIO`).

## 7. Andamento

| Data | Etapa | O que aconteceu |
|---|---|---|
| 01/11/2026 | Discovery | Escopo aberto; peças existentes lidas (provento do exterior já calculado pela V10); prova no Massive gratuito: proventos e desdobramentos disponíveis, inclusive desde 2010; D1–D10 |
| 01/11/2026 | Discovery | D1–D10 aprovadas |
| 01/11/2026 | Impact e Planning | Componentes e riscos (correção de valor sem duplicar, data-com, volume, limite do bucket); peças, critérios CA1–CA5, etapas E1–E5 |
| 01/11/2026 | Implementation | Ordem do Guilherme ("execute"): V21, `CashEventSource.MASSIVE`, `ForeignListings` (casamento comum a preço e eventos), `ForeignEventsProvider` no `MassiveClient`, `ForeignCorporateEventsService`, tarefas, rota e atalho, testes |
| 02/11/2026 | Implementation | Carga desde 2010 concluída (seção 6) |
| 02/11/2026 | Testing | Prova conjunta com O e KO: OK (6.1) |
| 02/11/2026 | Testing | `mvn clean verify` verde |
| 02/11/2026 | Review | Documentação: `Claude.md`, `README.md`, `SCHEDULE_TASKS` [A22], `ADM_TASKS` [26], PENDENCIAS 43.2 (resolvida) e 44.5; `data push` concluído |
| 02/11/2026 | Commit | Alterações conferidas (só leitura); mensagem sugerida ao Guilherme |
| 02/11/2026 | Commit | Guilherme validou (inclusive as imagens do `public/images`, alteração dele) e encerrou o escopo |
