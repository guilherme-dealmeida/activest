# PLANO_CARGA_INICIAL.md — Ambiente de carga inicial PrimeVest

> **Escopo:** Ambiente de carga inicial PrimeVest · **Etapa:** Planejamento (29/09/2026)
> **Objetivo:** subir o PrimeVest numa máquina nova só com o repositório + bucket, sem depender de nada fora do projeto,
> com a informação do catálogo **completa**.
> Nada deste plano foi executado pelo Claude. Implementação só com ordem explícita.
>
> **Revisões de 29/09**
> - v2: D1–D7 respondidas; bucket criado e carregado pelo Guilherme e conferido (2.4).
> - v3: correção — não havia lacuna do FNET (1.2); proposta de armazenamento híbrido.
> - **v4:** D9 = **baixar na primeira subida** (o híbrido sai); D8 sem complementos; **D13: `EXTRATO_B3` deixa de existir**;
>   D10 reaberta — **data de pagamento completa** vira requisito (seção 5).
> - v4.1: D11 = apagar `curadoria/`; D14 depois da medição; D10 reescrita em uma frase e nova D15 (FII só pelo FNET).
> - v4.2: D10 e D15 aprovadas com as condições; nenhuma decisão pendente — plano pronto para a ordem de execução.
> - **v5 (29–30/09): executado** — o que foi feito e os números na seção 10.
- **v6 (30/09): pilha `primevest-test` removida** (contêineres, volumes, `.env.test`, `data-test/`, parametrização do
  compose). O plano fica só com **bucket + primevest**; a prova numa máquina nova é do Guilherme, em outra máquina, depois.
- **v7 (02/10): D14 decidida** — levantamento das datas de pagamento 2010–2026 concluído pelo Guilherme
  (`proventos_datas_pagamento_2010_2026.xlsx`); entra na carga como arquivo em `carga/data/` (seção 11). Nada implementado ainda.

**Regras do escopo**

- **(A)** O que é carregado num ambiente novo não depende de diretório fora do projeto.
- **(B)** Script necessário para a carga fica no projeto (`carga/scripts/`); dado para popular tabela fica no projeto ou num bucket.
- **(C)** Evento que não é de usuário vem da **integração**, não de importação. **Não existe fonte `EXTRATO_B3` no catálogo.**
- **(D)** A informação do catálogo tem que estar **completa** — inclusive a data de pagamento.

---

## 1. Diagnóstico

| Origem | O que carrega | Situação |
|---|---|---|
| **Migrations V1–V37** | schema + seeds (ativos, categorias, emissores e ligações da V30, units, admin) | ✅ autocontido |
| **`carga/data/`** (fora do git) | lida pelo `POST /admin/bootstrap`: `b3/cotahist`, `b3/proventos`, `cvm/*`, `fnet/`, `logos/`, `curadoria/` | ✅ no bucket desde 29/09 (2.4) |
| **Scripts e dados avulsos, fora do projeto** | disparo das rotinas, conferência, carga da carteira do Guilherme, seed V30 | ❌ fora do projeto → seção 3 |

### 1.1 Importado × integrado

| Item | Situação | Num ambiente novo |
|---|---|---|
| Lista de CNPJ dos FIIs (`fiis.csv`, `fiis_cnpj_curado.json`) | superada pela V30 (346 de 347 iguais; VGRH11 corrigido na V30; todos com ISIN) | **apagar** (D5) |
| `curadoria/` (bucket e `carga/data/`) | superada pela V30 e por `docs/emissores/`; nenhum código lê | **apagar** (D11) |
| 18 eventos de FII rotulados `EXTRATO_B3` | os avisos **estão** em `carga/data/fnet` (1.2) | nascem como `FNET` |
| 3 eventos de PETR4 rotulados `EXTRATO_B3` | nenhuma fonte achada até aqui (5.1) | fora do catálogo até a fonte aparecer |
| 25 datas de pagamento com origem `EXTRATO_B3` | fonte integrável: CVM (5.2) | preenchidas pela integração |
| Transações do Guilherme | dado de usuário | fora da carga inicial |

### 1.2 Os avisos do FNET estão em `carga/data/fnet` (correção da v2)

HGLG11 tem 116 avisos em disco, inclusive 12/2024 a 08/2025. No banco de dev eles aparecem como `EXTRATO_B3` porque o
extrato foi carregado antes e o gravador **completa em vez de duplicar** quando acha o mesmo (ativo, tipo, data-com,
valor) — a fonte fica a do primeiro. Idem VGHF11, VISC11 e BTLG11. Os outros 8 FIIs "com lacuna" (ANCR11, DAMT11, EDGA11,
INHF11, KNRE11, RBOP11, RCFF11, SCPF11) **não distribuíram** no período — nem aviso no FNET nem evento na B3.

---

## 2. Passo 1 — bucket (feito)

### 2.1 Modelo (D9 — **decidido: baixar na primeira subida**)

A aplicação continua lendo **só a `carga/data/` local**, como hoje. O bucket é a cópia de referência: a máquina nova baixa uma
vez (`data-pull`); quem produz arquivo novo sobe (`data-push`). Nada muda no código Java por causa do bucket.

Do anexo 1 fica: Object Storage + **manifest** + scripts de sync. Não entram: tabela `data_files`, `raw/processed`, NoSQL.
Do anexo "primevest R2": não entra (leitura direta do bucket / híbrido / Redis) — decisão D9.

### 2.2 Provedor — **Cloudflare R2**, bucket `primevest-data` (D1)

Variáveis já no `.env`: `DATA_STORAGE=s3`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` (entram no
`.env.example` sem valor). `DATA_STORAGE`: `s3` = o `data-pull` busca no bucket; `local` = usa a `carga/data/` como está, sem bucket.

### 2.3 Chaves

| Chave | Permissão | Onde |
|---|---|---|
| a atual do `.env` | leitura e escrita | só na máquina do Guilherme — **produtor** |
| **token só leitura** (fase E3) | leitura, só no `primevest-data` | outras máquinas — **consumidor** |

Criar o token: painel da Cloudflare → R2 → *Manage API tokens* → *Object Read only*, restrito ao bucket. É o Guilherme
(conta dele; o segredo só aparece uma vez, para quem cria).

**Onde fica o token só leitura** — mesmos nomes de variável, sem nome novo:

| Onde | Arquivo | Variáveis |
|---|---|---|
| máquina nova | o **`.env`** dela (copiado do `.env.example`) | `DATA_STORAGE=s3`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` |

O token só leitura continua ativo na conta da Cloudflare; os valores dele estavam no `.env.test`, que foi para a lixeira
do Windows — recuperar de lá ou criar outro quando for montar a outra máquina.

### 2.4 Situação do bucket (conferido em 29/09)

`b3/cotahist` 17 · 587,5 MiB — `b3/proventos` 307 · 3,1 MiB — `cvm` 65 · 716,7 MiB — `fnet` 22.911 · 23,7 MiB —
`logos` 1.451 · 5,9 MiB — `curadoria` 4 · 0,3 MiB. **24.755 objetos, ~1,30 GiB, idênticos à `carga/data/` local.** Falta o manifest.

---

## 3. Passo 2 — scripts

### 3.1 Apagar (D5)

`generate_assets_sql.py`, `generate_classes.py`, `generate_seeds.js` (raiz: geradores de uma vez, de caminhos e migrations
que não existem mais), `fiis.csv` (raiz) e o `fiis_cnpj_curado.json` avulso (superados pela V30). Junto: tirar as 3
linhas da árvore do `Claude.md` (309–311).

### 3.2 Scripts avulsos → `primevest/scripts`

`admin.mjs` e `get.mjs` (`.env` relativo ao repo, portas por variável), `gerar_seed_emissores.mjs` (D3, saída relativa),
`ensaio/contagens.sql` e `conferir_balancos.sql` → `carga/scripts/sql/`.
Ficaram fora do projeto (dado de usuário, PENDENCIAS 16): `converter*`, `carregar*`, `proventos_regras*`, `binance-mcp/`.
Descartáveis (edições pontuais de sessões anteriores) listados na v2; nada além de 3.1 era apagado sem ordem.

### 3.3 Docker (D6)

Serviços num profile `carga` (não sobem no `up` normal):

| Serviço | Imagem | Faz |
|---|---|---|
| `tools` | `node:22-alpine` | `docker compose --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap` |
| `data pull [--minimo]` | `rclone/rclone:1.75.1` | bucket → `carga/data/`, incremental (só o que falta/mudou); `--minimo` = `fnet`, `b3/proventos`, `b3/proventos-12m`, `cvm/ipe`, `cvm/proventos` |
| `data push` | `rclone/rclone:1.75.1` | `carga/data/` → bucket (copia, nunca apaga lá) + regrava os manifests |
| `data status` | `rclone/rclone:1.75.1` | `carga/data/` × bucket, por dataset |

rclone configurado só por variável (`S3_*` do `.env`), 32 transferências simultâneas (22.911 avisos do FNET em ~1–2 min).

### 3.4 Estrutura final

```
carga/scripts/
├── README.md
├── admin.mjs  get.mjs  gerar_seed_emissores.mjs
├── lib/env.mjs    (raiz do projeto, `.env`, URLs da API)
├── data/          data.sh  (entrypoint do rclone: pull, push, status)
└── sql/           contagens.sql  conferir_balancos.sql  datas_pagamento.sql
```

---

## 4. Validação numa máquina nova (fora deste plano)

A prova de subida do zero fica com o Guilherme, em outra máquina, seguindo o README (seção de máquina nova: `.env` com o
token só leitura → `data pull` → `compose up` → `tools ... bootstrap`). Referência para conferir, com
`carga/scripts/sql/contagens.sql` e `datas_pagamento.sql` contra o dev: `cvm_documents` 19.349, `company_quarters` 19.344,
`asset_stock_events` 1.053, emissores 1.198, `fii_monthly` 42.815, `asset_cash_events` ≈ dev (± janela de 12 meses);
**nenhum evento com fonte `EXTRATO_B3`**; cobertura de datas da 10.1. Carga ~50 min + a etapa da CVM.

---

## 5. Data de pagamento completa (D10 reaberta, regra D)

### 5.1 O que cada fonte traz

| Fonte | Cobre | Data de pagamento? |
|---|---|---|
| **FNET** (avisos de FII) | FIIs, 2016+ | **Sim** (`DataPagamento`) — 17.496 de 17.500 eventos com data |
| **B3 — 12 meses** (`GetListedSupplementCompany`) | empresas, ~12 meses | **Sim** — mas o PrimeVest não guarda a resposta; o evento que sai da janela só tem a data no banco de quem já tinha carregado |
| **B3 — histórico longo** (`GetListedCashDividends`) | empresas, anos 90+ | **Não** — o campo não existe na resposta. São os **31.594** eventos `B3_HISTORICO` sem data |
| **CVM — "Relatório Proventos"** (IPE, dados abertos + PDF do RAD) | empresas, **2022+** (~900 documentos/ano, ~250 companhias) | **Sim** — formulário padronizado: ISIN, valor bruto por ação, último dia "com", **data de pagamento**, parcelas. Conferido no do Banco do Brasil de 21/02/2024 |
| CVM — antes de 2022 | "Aviso aos Acionistas" / "Comunicado ao Mercado" | texto livre em PDF, sem formulário (em 2010, 2013, 2016, 2019 e 2021 não há "Relatório Proventos") |
| BRAPI dividendos | empresas | tem data, mas **exige plano Startup (R$ 119,99/mês)** — conferido: o plano atual responde `FEATURE_NOT_AVAILABLE`; a profundidade do histórico não dá para verificar no gratuito |

Resposta curta à pergunta: **sim — FNET tem data de pagamento; a B3 só tem nos últimos 12 meses.**

PETR4: a B3 publica a atualização pela Selic como `RENDIMENTO` em alguns casos (PETR4 com data-com 22/04/2026: 0,0165 e
0,0204; BBAS3 todo trimestre), mas **não** os 3 valores do extrato (data-com 22/12/2025). A integração da CVM (5.2) confere
se eles aparecem num relatório de proventos; se não, não entram no catálogo (regra C).

### 5.2 O que entra no plano (produtor — D8)

| # | Entrega | Efeito |
|---|---|---|
| P1 | **Guardar a resposta dos 12 meses da B3** em `carga/data/b3/proventos-12m/{emissor}.json`, **acumulando** (linha nova entra, antiga fica) | a data de pagamento não se perde mais quando o evento sai da janela |
| P2 | **Exportar uma vez, do banco de dev**, os 5.339 eventos `B3` com data para esse mesmo arquivo | salva o que já saiu da janela da B3 (20 eventos hoje, e crescendo) e só existe no banco do Guilherme |
| P3 | **Integração nova: CVM "Relatório Proventos"** — índice anual do IPE (`dados.cvm.gov.br`, `carga/data/cvm/ipe/`), PDF de cada relatório (`rad.cvm.gov.br`, `carga/data/cvm/proventos/{protocolo}.pdf`), leitura do formulário; completa a data de pagamento de `B3_HISTORICO` por (ISIN, data-com, valor); origem da data = `CVM`. Entra no bootstrap e na rotina diária | cobre 2022+ |
| P4 | **FNET: busca de avisos novos** por CNPJ (PENDENCIAS 18.10 d), na rotina das 20:00 | FII continua completo depois de 27/09 |
| P5 | **Manifest** por dataset (`manifests/{dataset}.json`: chave, tamanho, hash, data), gerado no `data-push` a partir do `rclone lsjson` | o que o bucket tem, sem listar |

Espaço: os PDFs da CVM são ~100 KB → ~90 MB/ano, ~0,4 GB de 2022 a hoje; o bucket vai a ~1,7 GB (a faixa gratuita é 10 GB).

### 5.3 Antes de 2022 — D14

Nenhuma fonte **gratuita e estruturada** foi achada para a data de pagamento de ações entre 2010 e 2021. Opções:

| Opção | Prós | Contras |
|---|---|---|
| **a)** BRAPI Startup (R$ 119,99/mês) | API pronta; já é a fonte do fechamento | custo recorrente; profundidade só se verifica pagando |
| **b)** B3 Up2Data (produto pago da B3) | fonte oficial | contrato e custo bem maiores |
| **c)** Ler os "Aviso aos Acionistas" em texto livre da CVM (2010–2021) | gratuito, mesma infraestrutura da P3 | acerto incerto: texto sem padrão; exigiria conferência por amostragem |

Recomendação: fazer P1–P5, medir o que sobra sem data (`datas_pagamento.sql`, por ano) e **então** escolher entre (a) e (c)
com o número na mão — (c) primeiro em amostra, (a) se o acerto for baixo.

### 5.4 Cobertura esperada

| Período | Ações | FIIs |
|---|---|---|
| últimos 12 meses | B3 (100%) | FNET + B3 |
| 2022 até a janela da B3 | CVM (P3) + o que P2 salvou | FNET |
| 2010–2021 | **D14** | FNET (2016+) |

---

## 6. `EXTRATO_B3` sai do catálogo (D13)

| Onde | Mudança |
|---|---|
| Migration nova (core) | os 18 eventos de FII: fonte → `FNET` (conferindo contra o aviso em disco); os 3 de PETR4: saem do catálogo e os 3 proventos do usuário ligados a eles ficam com `cash_event_id` nulo (dado do usuário, com valor e data próprios); as 25 datas com origem `EXTRATO_B3`: origem → `CVM` quando a P3 confirmar, senão a data sai; `EXTRATO_B3` sai dos `CHECK` de `source` e `payment_date_source` |
| Core | `CashEventSource` e `PaymentDateSource` sem `EXTRATO_B3`; `CashEventWriter` sem o caminho do extrato |
| App-api | `ExtratoProventosImportService` deixa de criar ou completar evento do catálogo: só casa as linhas do extrato com os proventos do usuário; linha sem evento no catálogo vai para o relatório como "sem evento" |
| Front | `DividendsPage.tsx` e `types/index.ts` sem o rótulo |
| Testes | `ExtratoProventosIT`, `PaymentDateSourceIT` ajustados |

---

## 7. Fases de execução

| Fase | Entrega | Quem |
|---|---|---|
| **E1** | apagar os 5 arquivos (3.1) e a `curadoria/` da `carga/data/` e do bucket (D11); mover os scripts (3.2); `carga/scripts/README.md` | Claude |
| **E2** | serviços `tools` e `data` (profile `carga`), `.env.example` | Claude |
| **E3** | token só leitura do R2 | **Guilherme** |
| **E4** | manifests (P5) + primeiro `data-push`; `data-status` sem diferença | Claude |
| **E5** | P2 (exportar os eventos `B3` do dev) — **antes de tudo que mexa em proventos** | Claude |
| **E6** | P1 (12 meses acumulados), P4 (busca do FNET) | Claude |
| **E7** | P3 (CVM "Relatório Proventos") + etapa no bootstrap + `datas_pagamento.sql` | Claude |
| **E8** | seção 6 (`EXTRATO_B3` sai) | Claude |
| **E9** | README, `Claude.md`, `docs/PENDENCIAS.md`; `data-push` do que E5–E7 gravaram | Claude |
| **E10** | bateria de testes; medição de 5.4 para a D14 | Claude |

## 8. Decisões

| # | Decisão | Resposta |
|---|---|---|
| D1 | Provedor | **Cloudflare R2**, `primevest-data` |
| D2 | O que vai para o bucket | **tudo** (feito e conferido) |
| D3 | `gerar_seed_emissores.mjs` em `carga/scripts/` | **sim** |
| D4 | Eventos que não são de usuário | **vêm da integração** (regra C) |
| D5 | `fiis.csv` e afins | **apagar** |
| D6 | contêiner `tools` | **sim** |
| D7 | Pilha de teste | ~~`primevest-test`~~ **removida em 30/09** — a prova fica para outra máquina (seção 4) |
| D8 | Produtor e consumidor | **sim, sem complementos** (5.2, 3.3) |
| D9 | Modelo de armazenamento | **baixar na primeira subida**; aplicação lê só a `carga/data/` local |
| D12 | Cripto | não tem proventos — a pergunta era outra: o **histórico de preço** de cripto (CoinGecko) não é refeito num ambiente novo; o gráfico começa na data da carga. Fica anotada no PENDENCIAS, fora deste escopo |
| D13 | `EXTRATO_B3` | **deixa de existir** (seção 6) |
| D11 | `curadoria/` | **apagar da `carga/data/` e do bucket** (fase E1) |
| D14 | Data de pagamento de ações 2010–2021 | **levantamento manual** (Investidor10, Status Invest, Fundamentus, Brapi, RI) gravado em `carga/data/levantamento/`; o que ficou sem data é **A DEFINIR** (seção 11) |

| D10 | Data de pagamento das **ações** | **sim** — (1) 12 meses da B3 guardados em arquivo, (2) datas do banco de dev salvas hoje, (3) relatórios de proventos da CVM 2022+ (5.2) |
| D15 | Proventos de **FII** | **FNET como fonte principal**, B3 só de reserva (11 FIIs sem aviso no FNET), **com a busca de avisos novos (P4)** |

**Pendentes:** nenhuma decisão (D16 e D17 decididas em 02/10, seção 11.6).

> O FNET é só de fundos (FII, Fiagro); **ação não publica no FNET** — por isso não dá para usar só o FNET para tudo.

## 9. Fora do escopo

Produção/hospedagem; importação de extrato como funcionalidade (PENDENCIAS 16); histórico de preço de cripto; limpeza dos
arquivos avulsos além de 3.1.

---

## 10. Execução (29–30/09/2026)

| Fase | Situação | O que ficou |
|---|---|---|
| E1 | ✅ | `generate_*`, `fiis.csv` e `fiis_cnpj_curado.json` na lixeira do Windows; `curadoria/` na lixeira e apagada do bucket; `carga/scripts/` com `admin.mjs`, `get.mjs`, `gerar_seed_emissores.mjs` (corrigido: `issuers.segment`, contêiner `primevest-db`), `lib/env.mjs` (raiz achada pelo próprio arquivo), `sql/` e `README.md`; os originais avulsos na lixeira |
| E2 | ✅ | serviços `data` (rclone 1.75.1: `pull [--minimo]`, `push`, `status`) e `tools` (Node 22) no profile `carga`; `.env.example` com as `S3_*` e `PRIMEVEST_SENHA` |
| E3 | ✅ Guilherme | token só leitura: lista o bucket e recebe **403 AccessDenied** ao tentar gravar (conferido; ver 2.3) |
| E4 | ✅ | `data push` compara por hash (`--checksum`) e grava `manifests/{dataset}.json` (chave e tamanho) + `manifests/manifest.json` |
| E5 | ✅ | 4.327 proventos com data (fonte B3) exportados do banco de dev para `carga/data/b3/proventos-12m` (503 emissores) |
| E6 | ✅ | `B3SupplementStore` (12 meses acumulados); FII com FNET primeiro e B3 de reserva; busca de avisos novos por CNPJ quando a B3 mostra provento mais novo (1×/dia/fundo); `CashEventWriter`: o FNET assume o evento da B3. Rodada no dev: 654 emissores, 0 falhas; **3.175 eventos de FII passaram de B3 a FNET** |
| E7 | ✅ | `CvmProventosReport` (PDFBox; formato de 2022 e de 2023+), `CvmPaymentDateService`, etapa no bootstrap, rotina das 20:00 e `POST /admin/cvm/proventos`. Rodada no dev: **3.412 relatórios** (2022–2026, 779 MB), 113 ilegíveis, **4.075 datas de pagamento completadas** em 529 ativos |
| E8 | ✅ | V38 aplicada no dev (18 eventos de FII → FNET, 3 de PETR4 fora do catálogo — os 3 proventos do usuário ficaram `MANUAL`, somando R$ 1,16 —, datas do extrato apagadas e refeitas pelo FNET/CVM); enums, extrato só conferência (`JA_EXISTE`/`SEM_DATA`/`SEM_EVENTO`), tela de Dividendos e testes |
| E9 | ✅ | README (máquina nova), `Claude.md`, `docs/PENDENCIAS.md` (18.10 d resolvido, seção 20), `carga/scripts/README.md` |
| E10 | ✅ | bateria completa verde (30/09): core 138, adm-api 74 + 26, app-api 198 + 158 — 0 falhas; inclui `dataInvalida`. Front: `tsc` e `eslint` ok |

### 10.1 Medição da data de pagamento (entrada da D14)

`carga/scripts/sql/datas_pagamento.sql` no banco de dev, depois da carga da CVM:

| Ano | FII com data | Ações com data | Ações sem data |
|---|---|---|---|
| 2026 | 100% | 98,1% | 22 |
| 2025 | 99,9% | 82,2% | 402 |
| 2024 | 100% | 75,1% | 466 |
| 2023 | 100% | 76,8% | 416 |
| 2022 | 100% | 24,6% | 1.363 (o relatório da CVM começa no fim de 2022) |
| 2010–2021 | ~100% (2016+) | ~0% | ~17 mil |

A D14 decide 2010–2021. As ~400/ano sem data de 2023–2025 e os 113 relatórios ilegíveis estão no PENDENCIAS (20.6).

### 10.2 Divisão do que falta (30/09)

Download ou espera longa ficam com o Guilherme; o Claude só confere o resultado.

| Passo | Quem |
|---|---|
| Referência do aviso nos 18 eventos de FII da V38 | ✅ conferido em 02/10: os 20.848 eventos de FII com fonte FNET têm o número do aviso |
| `data push` periódico | Guilherme |
| Prova numa máquina nova (seção 4) | Guilherme, em outra máquina |
| D14 | ✅ decidida (seção 11) |

### 10.3 Achados durante a execução

- O formato do relatório da CVM mudou em 2023: em 2022 as datas do cabeçalho vêm em inglês com hora, o valor com ponto e
  "N"/"V" no lugar de "Não"/"A Vista" — o leitor aceita os dois (teste `formato2022`).
- A V38 manteve a referência da linha do extrato nos 18 eventos de FII; o `CashEventWriter` troca pelo id do aviso do
  FNET na próxima rodada de proventos (referência que não é número de aviso).
- Os testes de integração da adm-api gravavam na `carga/data/` real; o de proventos passou a usar `target/it-data-proventos`
  (PENDENCIAS 20.7 para os demais).
- O manifest com hash por arquivo exigia um HEAD por objeto no R2 (22 mil no FNET): ficou só chave e tamanho.
- Alguns relatórios da CVM usam 01/01/0001, 31/12/2998 ou 31/12/9999 como data de pagamento ("a definir"): 8 eventos (16
  linhas com o fracionário) receberam essas datas. O leitor passou a aceitar só data entre a data-com e 10 anos depois
  (a CEB pagou em 2024 dividendos de 2019) — teste `dataInvalida`; as 16 linhas do dev voltaram a ficar sem data.

---

## 11. Datas de pagamento levantadas (D14 — decidida em 02/10/2026)

### 11.1 O que foi levantado

`proventos_datas_pagamento_2010_2026.xlsx` (Guilherme): os **8.933** proventos de ações que estavam sem data de
pagamento no dev (data-com 2010–2026; cada linha cobre também o ticker fracionário).

| STATUS GERAL | Linhas | Data de pagamento |
|---|---|---|
| CONFIRMADO | 8.723 | sim — 2+ fontes, 1 fonte, regra aprovada ou preenchimento manual |
| ACEITE NA ÚLTIMA REVISÃO | 48 | sim — sugestão adotada na última revisão |
| A DEFINIR | 162 | não |

Conferência do arquivo: nenhuma data antes da data-com; 31 em fim de semana (prazos como 31/12, aceitos na revisão).

### 11.2 Regra para o A DEFINIR (Guilherme)

- **a) recentes** (2025–2026): recebimento normal; a data entra quando for informada — B3 12 meses (P1) e relatório da
  CVM (P3) já fazem isso nas rotinas; nada novo.
- **b) antigos** (2010–2022): ficam sem data até existir **fonte oficial paga** (B3 Up2Data e/ou BRAPI) — D17.

### 11.3 Como entra no fluxo (proposta — implementação só com ordem)

| # | Peça | O que é |
|---|---|---|
| L1 | **Arquivo** `carga/data/levantamento/datas-pagamento.json` | gerado da planilha; vai para o bucket como os demais (`data push`) e entra no `--minimo` do `data pull`. A planilha fica junto (`carga/data/levantamento/origem/…xlsx`) como evidência |
| L2 | **Chave natural**, não `id_evento` | o id só existe no banco de dev e muda num reset. Chave: `ticker` (+ `ticker_fracionario`; sem ele, tenta também `ticker`+`F`), `tipo`, `data_com`, `parcela` (`seq`) e `valor_por_acao` — valor exato primeiro, senão o mais próximo dentro de 0,000001 (há pares no banco que diferem na 9ª casa, ex. AZEV3 11/12/2015); cada evento é usado uma vez |
| L3 | **Campos por linha** | chave + `data_pagamento` (nula no A DEFINIR) + `status` (`CONFIRMADO` / `ACEITE_ULTIMA_REVISAO` / `A_DEFINIR`) + `fonte` + `regra` |
| L4 | **Conversão** `carga/scripts/levantamento/xlsx_para_json.py` | lê a planilha e grava o JSON (ordenado, sem hash de dev); rodado pelo Guilherme quando a planilha mudar; depois `data push` |
| L5 | **Fonte da data** `PaymentDateSource.LEVANTAMENTO` + migração **V39** | o CHECK `ck_asset_cash_events_payment_date_source` passa a aceitar `LEVANTAMENTO` (com `payment_date` não nula) |
| L6 | **Serviço** `PaymentDateSurveyService` (adm-api) | aplica o arquivo **só em evento sem data**; grava `payment_date_source = LEVANTAMENTO`; publica `CatalogChange` (recalcula os proventos do usuário). Relatório: aplicados, já tinham data (iguais / diferentes), sem evento correspondente, A DEFINIR |
| L7 | **Precedência** | data da própria fonte do evento (B3 12 meses, FNET) > CVM > LEVANTAMENTO. Mesmo comportamento da CVM hoje: o `CashEventWriter` troca a data de uma `PaymentDateSource` pela da fonte quando ela chega |
| L8 | **Bootstrap** | nova etapa **depois** de "datas de pagamento CVM": "datas de pagamento do levantamento". Endpoint `POST /admin/proventos/levantamento` para rodar à parte (ex.: aplicar no dev sem reset) |
| L9 | **Rotina** | não entra no agendador — o arquivo é estático; o bootstrap e o endpoint bastam |
| L10 | **Testes** | leitura do JSON e casamento pela chave (inclui fracionário e parcela); precedência (B3 e CVM vencem, LEVANTAMENTO não sobrescreve); `SchemaTest` V39 |
| L11 | **Docs** | `Claude.md` (tabela de fontes), `carga/scripts/README.md`, `docs/PENDENCIAS.md` (A DEFINIR antigo, D17), README (dataset `levantamento`) |

### 11.4 Cobertura esperada depois da L8

Das 8.933 linhas levantadas, **8.771 (98%)** ganham data. Ficam sem data **145 antigas** (2010–2022, D17) e
**17 recentes** (2025–2026, entram pelas rotinas da B3/CVM quando anunciadas).

### 11.5 Fases

| Fase | Entrega | Quem |
|---|---|---|
| L-a | L1–L4: JSON gerado e conferido (contagens da 11.1), `data push` | Claude gera; Guilherme roda o push |
| L-b | L5–L10: V39, enum, serviço, etapa do bootstrap, endpoint, testes | Claude |
| L-c | aplicar no dev pelo endpoint; `datas_pagamento.sql` antes × depois | Claude confere; Guilherme roda a bateria |
| L-d | L11 | Claude |

### 11.6 Decisões em aberto

| # | Pergunta | Decisão (02/10) |
|---|---|---|
| D16 | Provento sem data: como aparece para o usuário | **"data não informada" em toda situação sem data** (antigo ou recente). Entra na L-b; conferir antes o comportamento atual com data nula (recebidos, gráfico mensal, IR) |
| D17 | Fonte oficial paga para os A DEFINIR antigos | **A DEFINIR antigos ficam sem prazo** até haver fonte paga (B3 Up2Data e/ou BRAPI) — PENDENCIAS 20.8 |

### 11.7 Execução (02/10/2026)

| Fase | Situação | O que ficou |
|---|---|---|
| L-a | ✅ | planilha em `carga/data/levantamento/origem/proventos_datas_pagamento_2010_2026.xlsx`; `carga/scripts/levantamento/xlsx_para_json.py` (valida status × data, pagamento antes da data-com, chave repetida — nada é gravado se houver erro) gerou `carga/data/levantamento/datas-pagamento.json` com **8.933 linhas** (CONFIRMADO 8.723, ACEITE_ULTIMA_REVISAO 48, A_DEFINIR 162); `levantamento` entrou no `MINIMO` do `data.sh`. `data push` feito em 02/10 (bucket: levantamento 2 arquivos; `data status` sem diferença) |
| L-b | ✅ | `PaymentDateSource.LEVANTAMENTO` + **V39** (CHECK aceita `LEVANTAMENTO`); `PaymentDateSurveyService` (adm-api: lê o JSON, casa pela chave da L2, grava só em evento sem data, publica `CatalogChange`); etapa **"datas de pagamento do levantamento"** no bootstrap, depois da CVM; `POST /admin/proventos/levantamento` (`node carga/scripts/admin.mjs proventos/levantamento`); D16 na tela de Dividendos ("Data não informada" + hover da origem `LEVANTAMENTO`). Testes: `PaymentDateSurveyServiceTest` (5), `PaymentDateSurveyIT` (2), `PaymentDateSourceIT` (+1: B3 substitui LEVANTAMENTO), `BootstrapServiceTest` (7 etapas), `SchemaTest` (V39) |
| L-c | ✅ | aplicado no dev pelo endpoint (02/10): 8.933 linhas, 17.520 eventos casados — **17.209 datas gravadas**, 12 já tinham a mesma data (CVM), 0 divergentes, 299 A DEFINIR, 0 linhas sem evento; app-api recalculou a carteira (623 ativos); proventos do usuário com evento: 229 de 229 com a data do evento. Antes × depois em `carga/data/levantamento/{antes,depois}_datas_pagamento.txt` |
| L-d | ✅ | `Claude.md` (fonte `LEVANTAMENTO`, V39, tela), `docs/PENDENCIAS.md` (20.1 resolvido, 20.8 e 20.9), `carga/scripts/README.md` (conversão) e `README.md` (dataset `levantamento` e etapa da carga) |

Datas em fim de semana aceitas no levantamento (31 linhas; em geral prazo "até 31/12" ou fim de mês). **Em 02/10 o
Guilherme decidiu recuar cada uma para o dia útil anterior** (ex.: 31/12/2017 → 29/12/2017; 01/04/2017 → 31/03/2017);
a planilha em `carga/data/levantamento/origem/` e o JSON já estão ajustados (nota em OBSERVACAO de cada linha). Datas originais:

| Ticker | Data-com | id_evento (dev) | Pagamento |
|---|---|---|---|
| AFLT3 | 20/09/2017 | 5180 | 31/12/2017 (dom) |
| ALUP4 | 25/04/2017 | 5999 | 31/12/2017 (dom) |
| AMAR3 | 19/04/2012 | 6241 | 15/09/2012 (sáb) |
| BALM3 | 30/04/2015 | 7499, 7501, 7503, 7505 | 29/05/2016 (dom) |
| BBDC3 | 01/03/2017 | 8335 | 01/04/2017 (sáb) |
| CEEB3 | 23/03/2016 | 16065 | 31/12/2016 (sáb) |
| CEEB3 | 04/08/2016 | 16063 | 31/12/2016 (sáb) |
| CEEB3 | 20/12/2016 | 16061 | 31/12/2017 (dom) |
| CEEB5 | 04/08/2016 | 16269 | 31/12/2016 (sáb) |
| CEEB5 | 20/12/2016 | 16267 | 31/12/2017 (dom) |
| FIGE3F | 29/04/2016 | 24001 | 31/12/2016 (sáb) |
| FIGE3F | 28/04/2017 | 24000 | 31/12/2017 (dom) |
| GEPA3 | 26/10/2016 | 26338 | 31/12/2016 (sáb) |
| GEPA4 | 26/10/2016 | 26450 | 31/12/2016 (sáb) |
| GEPA4 | 27/04/2018 | 26440 | 30/06/2018 (sáb) |
| HBTS5 | 28/04/2020 | 28106 | 27/06/2020 (sáb) |
| ITUB4 | 13/08/2014 | 31985 | 24/08/2014 (dom) |
| MILS3 | 28/12/2010 | 36365 | 30/04/2011 (sáb) |
| MILS3 | 21/06/2013 | 36351 | 14/06/2014 (sáb) |
| MILS3 | 23/12/2013 | 36349 | 14/06/2014 (sáb) |
| ODER4 | 05/05/2021 | 37890 | 30/05/2021 (dom) |
| PASS3 | 27/09/2022 | 38157 | 31/12/2022 (sáb) |
| POSI3 | 28/04/2023 | 40374 | 30/09/2023 (sáb) |
| RADL3 | 26/09/2019 | 41149 | 31/05/2020 (dom) |
| RADL3 | 11/12/2019 | 41147 | 31/05/2020 (dom) |
| TRIS3 | 14/04/2014 | 49198, 49200 | 31/05/2014 (sáb) |
| TUPY3 | 07/11/2016 | 49520 | 31/12/2016 (sáb) |

