# PrimeVest — Plano de Emissoras e Distribuidoras de Renda Fixa

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **finalizado** (plano criado em 16/10/2026; D1–D8 respondidas no mesmo dia, todas pela recomendação;
> E1–E7 e Adendo A concluídos em 16/10/2026 — seção 13). Ficou aberto na PENDENCIAS: 28.1 (logos sem fonte, busca
> manual), 28.2, 28.3, 28.4 e 28.5.
> Origem: ESCOPO "Emissoras de Renda Fixa PrimeVest", levantamento de 08–16/10/2026 (seção 10). Curadoria dos grupos em
> `docs/emissores/Curadoria_Grupos_RF_2026-10-09.xlsx`. Liga com `docs/planos/PLANO_VENDA_RENDA_FIXA.md` (D1 dele: a
> regra de saída passa a ser lida da distribuidora). Pendências ligadas: `docs/PENDENCIAS.md` 4.3, 16.5, 16.6, 26.7,
> 27.1 e 27.2.

---

## 0. Resumo

Hoje o título de renda fixa (`fixed_income_notes`) guarda o emissor como **texto livre** e não sabe onde foi comprado.
Na sua base convivem "BANCO XP S/A", "NU FINANCEIRA SA..." e "Picpay cofrinho turbinado" — produto, distribuidor e
emissor misturados no mesmo campo.

Com este plano:

| | Hoje | Depois |
|---|---|---|
| **Emissora** | texto livre | empresa do cadastro, escolhida num seletor filtrado pelo produto (CDB → bancos; Debênture → companhias...) |
| **Distribuidora** | não existe | empresa do cadastro (corretora, DTVM, banco, financeira, cooperativa) |
| **Código do papel** | não existe | opcional (ex.: `CDBA2478FYE`), manual ou por integração |
| **Grupo** | não existe | conglomerado do BCB ("Nubank" = Nu Financeira + Nu Investimentos + Nu Pagamentos), com nome amigável |
| **Regra de saída da venda RF** | PROPORCIONAL fixo | lida da distribuidora (PROPORCIONAL padrão; Nu Financeira UEPS) |
| **Venda do Tesouro** | FIFO entre todos os títulos do ativo | FIFO por ativo **e distribuidora** |
| **Garantia** | não existe | garantidor da empresa (FGC, FGCoop, nenhum) + "produto coberto" na categoria |

- O cadastro (`fixed_income_issuers`) vem de **integração** (BCB + CVM) na adm-api, com rotina mensal, e nasce de um
  **seed com o estado conferido** (como a V30): banco novo sobe completo, sem rede.
- **RDC** (Recibo de Depósito Cooperativo) entra no catálogo.
- O **ativo continua o principal** nas telas; emissora e distribuidora entram como colunas, filtros e no logo.

**Fora deste escopo:** limite do FGC por conglomerado; Open Finance (caixinhas); importar RF/TD pela integração B3
(PENDENCIAS 16.5, 16.6, 26.7) — este plano só deixa registrada a regra de casamento da instituição; logo pelo site da
empresa (caminho 2, vira pendência); taxa de custódia do Tesouro (27.4).

---

## 1. O que existe (conferido no código em 16/10/2026)

| Peça | Onde | Hoje | Neste plano |
|---|---|---|---|
| `fixed_income_notes.issuer` | V1 / `FixedIncomeNote.issuer` | texto livre; `getLabel()` = "Emissor · venc. aaaa-mm-dd" | **sai**; entram `issuer_id`, `distributor_id`, `paper_code` |
| `issuers` | V28/V30 | companhias abertas e FIIs da bolsa | **não muda** e não se liga ao cadastro novo (L1) |
| `asset_categories` (RF) | V3 | 15 categorias 1:1 com os 15 produtos de `assets` (V7) | ganham `issuer_kinds` e `guaranteed`; entra RDC |
| `fixed_income_tax_rules` | V52 | IR/IOF por faixa; isenções por ticker do produto | **não muda**: RDC segue a regra geral (IR regressivo + IOF) |
| `FixedIncomeValuation.saleRule(pa)` | core | devolve `SaleRule.DEFAULT` (PROPORCIONAL) | lê `distributor.sale_rule` do título |
| `TreasuryAllocator.titlesOf` | core | FIFO entre **todos** os títulos do ativo na carteira | FIFO entre os títulos do ativo **na distribuidora da venda** |
| Regra H | `TransactionService.validateOnePurchasePerDate` + `uk_fixed_income_notes_purchase (user_id, asset_id, purchase_date)` | um título do Tesouro por ativo por dia | **D3** |
| DTOs | app-api | `TransactionCreateDTO.issuer`, `NoteOptionDTO.issuer`, `TransactionResponseDTO.issuer`, `PortfolioAssetDetailDTO.issuer` (texto) | ids + rótulos de emissora e distribuidora (seção 6) |
| Telas | web | `FixedIncomeFields` (campo Emissor digitado), seletor de título do `TransactionFormModal`, coluna EMISSOR da Carteira, coluna TÍTULO de Transações (`rotuloDoTitulo`) | seletores com busca, colunas, filtros, logo (seção 7) |
| Logos | adm-api `LogoScheduler`/`AssetLogoService`, app-api `AssetLogos` | arquivo `carga/data/logos/{TICKER}.{ext}` é o estado; RF mostra "RF" | grupo aponta para um ticker que já tem logo (L22) |
| Carga inicial | adm-api `BootstrapService` | etapas: emissores, COTAHIST, proventos, ..., Tesouro | ganha a etapa "emissoras de renda fixa" |
| Notificações | core `NotificationKind` | `INTEGRACAO_*`, `RF_VENCIDO`... | nada novo neste escopo (a mensagem de "instituição não listada" fica para a importação de RF da B3 — L21) |
| Integração B3 | `primevest-integration` `B3InvestidorConnector` | lê `participantName`; RF e TD **não são importados** (16.5/16.6) | sem mudança (L21) |

---

## 2. Regras do escopo

| # | Regra |
|---|---|
| R1 | **Empresa** = uma linha de `fixed_income_issuers`, identificada pela **raiz do CNPJ (8 dígitos)**, única. O CNPJ completo (14) é informação, quando a fonte traz. "Tesouro Nacional" e "Instituição não listada" não têm CNPJ e são identificadas pelo tipo |
| R2 | **Tipo** da empresa: `BANCO`, `FINANCEIRA`, `COOPERATIVA`, `CORRETORA_DISTRIBUIDORA`, `SECURITIZADORA`, `COMPANHIA`, `TESOURO`, `NAO_LISTADA` — derivado do segmento do BCB ou da fonte CVM (seção 4.3) |
| R3 | Empresa **morta** (inativa, liquidada, falida, cancelada) **nunca sai** do cadastro: guarda a situação e continua escolhível (lançamento retroativo) |
| R4 | **Seletor de emissora** filtra pelos tipos aceitos pelo produto (`asset_categories.issuer_kinds`, seção 4.4). "Instituição não listada" aparece sempre |
| R5 | **Seletor de distribuidora**: `CORRETORA_DISTRIBUIDORA`, `BANCO`, `FINANCEIRA`, `COOPERATIVA` + "Instituição não listada" |
| R6 | **Tesouro Direto**: emissora fixa "Tesouro Nacional" (sem seletor) e distribuidora obrigatória |
| R7 | **Emissora e distribuidora obrigatórias** no título (RF e TD). Títulos que já existem entram com "Instituição não listada" nas duas (REGRAS 8.1) |
| R8 | **Código do papel** opcional; título com código só recebe aporte com o **mesmo** código; título sem código (caixinha, lançamento manual) aceita aportes como hoje (D4) |
| R9 | **Grupo** = conglomerado prudencial do BCB, inclusive os de uma empresa só. Toda empresa pode ter grupo (automático pelo BCB ou por curadoria). Nome do grupo = o curado/sugerido; a importação **nunca** sobrescreve nome, ligação curada, regra de saída nem ticker do logo |
| R10 | **Exibição**: colunas mostram o nome do grupo (razão social na dica); seletores mostram "Grupo — razão social"; sem grupo: nome comercial da CVM, senão a razão social |
| R11 | **Garantidor** da empresa: `BANCO`/`FINANCEIRA` → FGC, exceto banco cooperativo → FGCoop; `COOPERATIVA` → FGCoop; demais → nenhum. O título é garantido se o produto é coberto (`asset_categories.guaranteed`) **e** a emissora tem garantidor. Só informação neste escopo |
| R12 | **Regra de saída** (`sale_rule`) é da **empresa distribuidora**: PROPORCIONAL padrão; Nu Financeira UEPS (provado, PLANO_VENDA_RENDA_FIXA seção 10). Tesouro é PEPS fixo e não lê a coluna |
| R13 | **Venda do Tesouro** informa a distribuidora; o FIFO consome só os títulos do ativo nessa distribuidora |
| R14 | **Edição de compra já vendida** (valor, data, quantidade) fica como hoje: refaz o FIFO/lotes e bloqueia se não fechar |
| R15 | **Troca de distribuidora no Tesouro** com venda ligada: modal da cascata (seção 5.1) |
| R16 | **Troca de distribuidora na RF**: direta se a regra de saída não muda; se muda, aviso + recálculo das vendas do título (líquido mantido; bruto, IR e IOF refeitos); bloqueia se não couber no saldo (seção 5.2) |

---

## 3. Banco de dados

### 3.1 V54 — estrutura

```sql
CREATE TABLE fixed_income_groups (
    id           BIGSERIAL    PRIMARY KEY,
    bcb_code     VARCHAR(10)  UNIQUE,          -- conglomerado prudencial (C0084693); nulo em grupo só de curadoria
    name         VARCHAR(120) NOT NULL,        -- nome amigável ("Nubank")
    name_source  VARCHAR(10)  NOT NULL,        -- MANUAL | AUTOMATICO (o MANUAL a importação não toca)
    description  VARCHAR(255),                 -- nome do BCB / da empresa líder
    leader_cnpj_root VARCHAR(8),
    logo_ticker  VARCHAR(20),                  -- ticker cujo logo o grupo usa (L22); curadoria
    extinct      BOOLEAN      NOT NULL DEFAULT FALSE,  -- só existe em bases antigas (Banco Master, Dacasa)
    created_at / updated_at
);

CREATE TABLE fixed_income_issuers (
    id                  BIGSERIAL    PRIMARY KEY,
    kind                VARCHAR(25)  NOT NULL,   -- R2
    cnpj_root           VARCHAR(8)   UNIQUE,     -- R1; nulo só em TESOURO e NAO_LISTADA
    cnpj                VARCHAR(14),
    legal_name          VARCHAR(255) NOT NULL,
    trade_name          VARCHAR(255),            -- nome comercial (CVM)
    segment             VARCHAR(120),            -- segmento do BCB / tipo de participante da CVM
    status              VARCHAR(30),             -- situação (ATIVA, INATIVA, LIQUIDACAO_EXTRAJUDICIAL, ...)
    website             VARCHAR(255),
    guarantor           VARCHAR(10)  NOT NULL,   -- FGC | FGCOOP | NENHUM (R11)
    sale_rule           VARCHAR(15)  NOT NULL DEFAULT 'PROPORCIONAL',  -- R12
    group_id            BIGINT REFERENCES fixed_income_groups (id),
    group_source        VARCHAR(10),             -- BCB | MANUAL (o MANUAL a importação não toca)
    sources             VARCHAR(40),             -- BCB, CVM_INTERMED, CVM_CIA, CVM_SECURIT (onde a empresa apareceu)
    source_updated_at   TIMESTAMP,
    created_at / updated_at
);
-- 2 linhas fixas (L9): Tesouro Nacional (TESOURO, NENHUM) e Instituição não listada (NAO_LISTADA, NENHUM)

ALTER TABLE asset_categories ADD COLUMN issuer_kinds VARCHAR(120);   -- só RF: tipos aceitos (seção 4.4)
ALTER TABLE asset_categories ADD COLUMN guaranteed   BOOLEAN NOT NULL DEFAULT FALSE;
-- categoria e ativo RDC (como a V3/V7), tipos e "coberto" das 16 categorias de RF

ALTER TABLE fixed_income_notes ADD COLUMN issuer_id      BIGINT REFERENCES fixed_income_issuers (id);
ALTER TABLE fixed_income_notes ADD COLUMN distributor_id BIGINT REFERENCES fixed_income_issuers (id);
ALTER TABLE fixed_income_notes ADD COLUMN paper_code     VARCHAR(20);
UPDATE fixed_income_notes SET issuer_id = <id da não listada>, distributor_id = <id da não listada>;  -- REGRAS 8.1
ALTER TABLE fixed_income_notes ALTER COLUMN issuer_id SET NOT NULL, ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE fixed_income_notes DROP COLUMN issuer;
CREATE UNIQUE INDEX uk_fixed_income_notes_paper_code ON fixed_income_notes (user_id, paper_code) WHERE paper_code IS NOT NULL;
-- regra H: D3
```

O UPDATE é um valor único para todas as linhas (o id da "Instituição não listada", procurado pelo tipo dentro da
própria migration), não dedução linha a linha — cabe na REGRAS 8.1.

### 3.2 V55 — seed com o estado conferido (L2, opção B)

Gerado do banco de desenvolvimento depois da integração (etapa E2) e da curadoria, por script em `carga/scripts/`
(como a V30 saiu do `gerar_seed_emissores.mjs`): **todas as empresas** do recorte da D1, os **659 grupos** (657 do BCB
06/2026 + 2 extintos), as ligações empresa → grupo (inclusive as 32 inativas do grupo antigo) e a curadoria: nomes
`MANUAL`, `sale_rule` da Nu Financeira (UEPS) e `logo_ticker` dos grupos. Chave natural: `cnpj_root` e `bcb_code`
(ids de BIGSERIAL não valem entre bancos). `ON CONFLICT`: no banco que já rodou a integração, o dado da fonte fica e só
o curado e o que faltar vêm do seed.

---

## 4. Integração (adm-api)

### 4.1 Fontes

| Fonte | O que traz | Uso |
|---|---|---|
| BCB IF.data — cadastro (`olinda.bcb.gov.br/olinda/servico/IFDATA/.../IfDataCadastro`), data-base mais recente | todas as instituições, **vivas e mortas**, com segmento, situação, conglomerado prudencial, CNPJ raiz da líder (06/2026: 5.864 linhas) | empresas BCB, tipo, situação, grupo |
| BCB IF.data — bases antigas (12/2018, 12/2020, 12/2022, 12/2024) | conglomerado de quem já morreu | grupo antigo das inativas (só se a empresa não tem grupo hoje) |
| BCB "Instituições em funcionamento" (`Instituicoes_em_funcionamento`) | só as **vivas**, com site | site (`SITIO_NA_INTERNET`) |
| CVM `intermed-cad` | intermediários (corretoras, DTVMs, bancos com carteira de investimento), vivos e cancelados, CNPJ 14, nome comercial, site | completa as do BCB; distribuidoras |
| CVM `cia_aberta-cad` | companhias abertas (categorias A e B), vivas e canceladas | emissoras de debênture e nota comercial |
| CVM `securit-doc-inf_mensal_cri` / `_cra` (aba geral) | CNPJ e nome das securitizadoras | emissoras de CRI e CRA |

Todo arquivo baixado fica em `carga/data/bcb/ifdata/...` e `carga/data/cvm/...` (arquivo = cache, como as demais
fontes) e sobe para o bucket.

### 4.2 Rotina

- `FixedIncomeIssuerImportService` (adm-api): baixa as fontes, junta por `cnpj_root`, deriva tipo (4.3) e garantidor
  (R11), cria/atualiza grupos (`AUTOMATICO`) e ligações (`BCB`). Nunca toca no que é `MANUAL`, em `sale_rule` nem em
  `logo_ticker` (R9). Empresa que some da fonte **não** é apagada (R3).
- `FixedIncomeIssuerScheduler`: **mensal** (dia 1, horário a definir junto do `LogoScheduler`), com recuperação na
  subida como as demais.
- `POST /api/v1/admin/fixed-income-issuers` (disparo manual) e etapa nova no `BootstrapService`.
- Mudou o cadastro → nada a avisar à app-api: as telas leem a tabela na hora; a regra de saída só muda o cálculo na
  próxima escrita do título (ou no refresh das 21:00).

### 4.3 Tipo da empresa (R2)

| Origem | Tipo |
|---|---|
| BCB: Banco Múltiplo, Comercial, de Investimento, de Desenvolvimento, de Câmbio, Caixa Econômica, Banco do Brasil, BNDES, banco estrangeiro | `BANCO` (garantidor FGC) |
| BCB: Banco Múltiplo Cooperativo | `BANCO` (garantidor **FGCoop**) |
| BCB: SCFI, Sociedade de Crédito Imobiliário, Companhia Hipotecária, APE | `FINANCEIRA` |
| BCB: Cooperativa de Crédito | `COOPERATIVA` |
| BCB: DTVM, CTVM (e CVM intermediário sem registro no BCB) | `CORRETORA_DISTRIBUIDORA` |
| CVM: informe de CRI/CRA | `SECURITIZADORA` |
| CVM: companhia aberta | `COMPANHIA` |
| Demais segmentos do BCB (IP, SCD, consórcio, câmbio...) | **D1** |

Uma empresa que aparece em duas fontes (ex.: Banco XP no BCB e na CVM) é uma linha só; o tipo do BCB prevalece.

### 4.4 Produto → tipos de emissora (R4)

| Produto | Tipos aceitos (além de NAO_LISTADA) | Coberto (R11) |
|---|---|---|
| CDB | BANCO | sim |
| RDB | BANCO, FINANCEIRA | sim |
| LC | FINANCEIRA | sim |
| LCI | BANCO, FINANCEIRA, COOPERATIVA | sim |
| LCA | BANCO, COOPERATIVA | sim |
| LF | BANCO, FINANCEIRA | não |
| LIG | BANCO, FINANCEIRA | a conferir na base legal (E1) |
| DPGE | BANCO, FINANCEIRA | sim |
| POUPANCA | BANCO, FINANCEIRA, COOPERATIVA | sim |
| CCB | BANCO, FINANCEIRA (o banco como emissora — L7) | não |
| RDC (novo) | COOPERATIVA | sim (FGCoop) |
| CRI, CRA | SECURITIZADORA | não |
| DEBENTURE, DEBENTURE_INCENTIVADA, NOTA_COMERCIAL | COMPANHIA | não |

Tabela a confirmar na **D2**. Como na V52, a base legal da cobertura do FGC/FGCoop é conferida na implementação e
anotada na migration.

---

## 5. Troca de distribuidora

### 5.1 Tesouro Direto (R15)

Ao salvar uma compra com distribuidora nova, se ela tem venda ligada, abre o modal:

> **Quais transações abaixo quer mover de [distribuidora antiga] para [distribuidora nova]?**

- A lista é a **cascata**: as vendas que consumiram a compra, as compras que essas vendas consumiram, e assim por
  diante. A compra editada vem marcada e travada; as demais vêm **marcadas** (o caso comum é corrigir um erro em
  bloco).
- Uma venda é **um item só**, mesmo quando consumiu vários títulos: não pode ficar dividida entre duas distribuidoras.
- Salvar refaz o FIFO e o IR/IOF de cada parte (`TreasuryAllocator`) **nas duas pontas**: o que fica precisa fechar na
  antiga, e o que vai precisa fechar na nova, junto com o que já estava lá.
- Fechou → grava tudo (**tudo ou nada**, numa transação de banco). Não fechou → volta ao modal com o motivo ao lado do
  item (ex.: "Venda de 15 cotas em 10/09: faltariam 5 cotas na Nu Invest").
- Sem venda ligada → troca direta, sem modal.

Endpoints: prévia da cascata (`POST .../distributor-change/preview`, devolve os itens e o resultado do FIFO) e
aplicação (`POST .../distributor-change` com os itens marcados).

### 5.2 Renda fixa privada (R16)

A venda RF é do próprio título, então sempre acompanha a troca. Se a `sale_rule` da distribuidora nova é a mesma,
a troca é direta. Se muda, aviso antes de salvar:

> A regra de saída passa de PROPORCIONAL para UEPS. As N vendas deste título serão recalculadas (o líquido recebido
> fica; bruto, IR e IOF são refeitos).

O recálculo é o da cascata R10 do plano de venda (`FixedIncomeValuation.syncSales`); se o bruto refeito de alguma
venda não couber no saldo (regra E), a troca é recusada com a venda que impede.

**Efeito na sua base:** as notas migradas ficam com "Instituição não listada" (PROPORCIONAL). Ao corrigir as do Nubank
para Nu Financeira, o aviso recalcula as vendas por UEPS — que é o que bate com o extrato.

---

## 6. app-api

| Peça | Mudança |
|---|---|
| `GET /api/v1/fixed-income-issuers?role=ISSUER\|DISTRIBUTOR&category=CDB&q=...` | busca para os seletores (milhares de linhas: paginada, por nome do grupo, razão social, nome comercial ou CNPJ); devolve id, rótulo "Grupo — razão social", tipo, situação, garantidor |
| `TransactionCreateDTO` | `issuerId`, `distributorId`, `paperCode` no lugar de `issuer`; TD: `distributorId` na compra **e na venda**; `issuerId` ignorado (Tesouro Nacional) |
| `TransactionService` | valida tipos (R4/R5), R6, R7, R8 (D4); regra H (D3); troca de distribuidora (seção 5) |
| `TreasuryAllocator` | `titlesOf` filtra pela distribuidora da venda (R13); prévia da venda idem |
| `FixedIncomeValuation.saleRule(pa)` | lê `pa.getNote().getDistributor().getSaleRule()` |
| `FixedIncomeNote.getLabel()` | "Grupo da emissora · venc. dd/mm/aaaa" |
| `NoteOptionDTO`, `TransactionResponseDTO`, `PortfolioAssetDetailDTO` | emissora e distribuidora (id, nome de exibição R10, razão social para a dica), código do papel, garantidor/coberto, logo (D5) |

---

## 7. Telas

| Tela | Mudança |
|---|---|
| **Nova Transação — RF** | "Dados do título": seletor de **Emissora** (filtrado pelo produto), seletor de **Distribuidora**, campo **Código do papel**; seletor de título mostra "Grupo · venc. · taxa" |
| **Nova Transação — TD** | seletor de **Distribuidora** na compra e na venda (na venda, só as distribuidoras com título aberto do ativo — D7); emissora não aparece |
| **Seletores** | componente com busca (digitação), rótulo "Grupo — razão social", situação da empresa morta em cinza ("Inativa", "Em liquidação") |
| **Carteira** (abas RF e TD) | colunas EMISSORA e DISTRIBUIDORA com o nome do grupo e a razão social na dica; logo da linha pelo grupo (D5), "RF" quando não houver |
| **Transações** | coluna TÍTULO com o grupo; filtros de Emissora e Distribuidora quando o tipo é RF ou TD (D6) |
| **Modal da troca** | seção 5.1 (TD) e aviso da 5.2 (RF) |

---

## 8. Etapas

| Etapa | Conteúdo |
|---|---|
| **E1** | V54 (estrutura, 2 linhas fixas, RDC, `issuer_kinds`/`guaranteed`, colunas do título, remoção do texto livre); entidades e repositórios no core; base legal da cobertura (4.4). Testes de migration e de repositório |
| **E2** | Integração na adm-api (seção 4): clientes BCB e CVM, arquivos em `carga/data`, `FixedIncomeIssuerImportService`, scheduler mensal, endpoint de admin, etapa do bootstrap. Testes com respostas reais gravadas (como `CvmRespostasReaisTest`) e de não sobrescrever o curado |
| **E3** | Curadoria aplicada (planilha de 09/10: nomes, inativas, extintos; Nu Financeira UEPS; `logo_ticker`) e V55 gerada por script; prova de subida num banco vazio sem rede |
| **E4** | app-api (seção 6): busca, DTOs, validações R4–R8, regra H, FIFO por distribuidora, `saleRule` da distribuidora. Testes de serviço e funcionais (TransacoesIT, RendaFixaCurvaIT) |
| **E5** | Troca de distribuidora (seção 5): prévia e aplicação da cascata do TD; aviso e recálculo da RF. Testes com o exemplo do levantamento (a, b, c) e com a regra mudando |
| **E6** | Telas (seção 7) + conferência no navegador (REGRAS 4.2) |
| **E7** | Documentação (Claude.md, PENDENCIAS 4.3 resolvida, 27.1 resolvida, 27.2 atualizada, novas pendências da seção 12; andamento deste plano; nota no PLANO_VENDA_RENDA_FIXA D1) e validação final |

### Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Responder D1–D8 | | ✔ |
| E1–E7 (código, migrations, testes, docs) | ✔ | |
| Conferir a base legal da cobertura (4.4) | ✔ (pesquisa) | ✔ (aceite) |
| Conferir a V55 antes de entrar (amostra de empresas, grupos e curadoria) | ✔ (prepara o resumo) | ✔ |
| Corrigir emissora/distribuidora dos seus títulos atuais | | ✔ (pela tela, depois da E6; ou fora do sistema) |
| Data push das pastas novas de `carga/data` para o bucket | ✔ | |
| Validação final (compilar, testes, lint, subir, navegador) | ✔ | confere no app |

---

## 9. Riscos e achados

- **Securitizadora com nome em texto livre**: o informe de CRI traz o nome digitado pela própria securitizadora (ex.:
  "Opea Securtizadora"). O nome vem do cadastro da CVM quando existir; senão, do informe, e a curadoria corrige no seed.
- **Grupo perdido na morte**: a empresa que morre perde o conglomerado na base atual (Banco Master, 06/2026). Por isso
  as bases antigas (4.1) e o `group_source = MANUAL` das 32 inativas.
- **IF.data é trimestral**: a situação de uma empresa pode estar até um trimestre atrasada. Não bloqueia nada (empresa
  morta continua escolhível).
- **Debênture de empresa sem registro na CVM** (comum nas incentivadas) cai em "Instituição não listada" (L7).
- **Volume do seletor**: milhares de empresas — o filtro por produto e a busca são obrigatórios, não opcionais.
- **Troca de distribuidora no TD** é a parte mais delicada: a cascata e a conta nas duas pontas precisam de teste com
  vendas que consomem títulos de idades diferentes.

---

## 10. Decisões tomadas no levantamento (08–16/10/2026)

| # | Decisão |
|---|---|
| L1 | Tabela própria `fixed_income_issuers`, de onde saem emissora e distribuidora; sem vínculo com `issuers` (os indicadores da companhia não servem à lógica da RF) |
| L2 | Cadastro por **integração** CVM + BCB **e seed** com o estado completo (opção B, como a V30); rotina mensal na adm-api + disparo manual |
| L3 | Chave = **raiz do CNPJ** (8 dígitos); CNPJ completo informativo |
| L4 | Empresas mortas continuam no cadastro (lançamento retroativo) |
| L5 | Cooperativas entram; **RDC** entra no catálogo neste escopo |
| L6 | Tipos novos de empresa (R2) |
| L7 | Seletor de emissora filtrado pelo produto; CCB → banco como emissora; Nota Comercial → companhias + não listada; LCI e LCA aceitam cooperativas (BC autorizou LCI em 04/2020; Sicoob e Sicredi emitem LCA); debênture sem registro na CVM → não listada |
| L8 | Seletor de distribuidora: intermediários da CVM + bancos, financeiras e cooperativas do BCB (quem emite também vende o que emite) |
| L9 | Sem texto livre; "Instituição não listada" e "Tesouro Nacional" entram por migration (2 linhas) |
| L10 | Título: `issuer_id` e `distributor_id` obrigatórios, `paper_code` opcional; a coluna `issuer` sai; títulos existentes com "Instituição não listada" (REGRAS 8.1); TD novo com emissora Tesouro Nacional automática |
| L11 | Distribuidora só na RF e no TD, gravada no título |
| L12 | Título com código só recebe aporte do mesmo código (papéis de códigos diferentes são títulos diferentes) |
| L13 | Grupos: tabela própria (nome amigável e descrição); **todos** os conglomerados do BCB, inclusive os de uma empresa; nomes sugeridos aceitos sem curadoria; inativas ligadas ao grupo antigo; grupo vinculável a qualquer empresa por curadoria |
| L14 | Exibição: grupo nas colunas (razão social na dica); "Grupo — razão social" nos seletores; sem grupo: nome comercial → razão social |
| L15 | Garantidor (FGC, FGCoop, nenhum) na empresa + "produto coberto" na categoria |
| L16 | Regra de saída na **empresa distribuidora** (não no grupo): PROPORCIONAL padrão, Nu Financeira UEPS, TD PEPS fixo |
| L17 | Venda do TD: FIFO por ativo e distribuidora; a venda informa a distribuidora |
| L18 | Troca de distribuidora no TD: modal da cascata (seção 5.1) |
| L19 | Troca de distribuidora na RF: direta se a regra não muda; aviso + recálculo se muda; bloqueio se não couber |
| L20 | Edição de compra já vendida fica como hoje (refaz e bloqueia se não fechar) |
| L21 | Integração B3: casar a instituição pelo CNPJ (`participantDocumentNumber`, da API oficial) e, sem ele, pelo nome; sem correspondência → "Instituição não listada" + notificação. Vale quando a importação de RF/TD existir (16.5/16.6) |
| L22 | Logos: caminho 1 (grupo → ticker com logo já baixado: BB, Itaú, Bradesco, BTG, Santander, Banrisul, Nubank/ROXO34, XP/XPBR31, Inter/INBR32, BMG, ABC, Pine, BRB, Banestes, Mercantil, PagBank, Mercado Pago); caminho 2 (ícone pelo site) vira pendência |
| L23 | Fora: limite do FGC, Open Finance, importação de RF/TD da B3, logo pelo site |
| L24 | O ativo continua o principal nas telas; emissora e distribuidora entram na tela e nos filtros |

---

## 11. Decisões D1–D8 — **tomadas em 16/10/2026: todas pela recomendação**

Resumo do que vale: **D1 (b)** só os segmentos da 4.3; **D2** tabela 4.4 aceita (cobertura conferida na E1);
**D3 (a)** regra H por ativo + distribuidora + data; **D4 (a)** código repetido é recusado; **D5** logo pela emissora;
**D6 (a)** filtros em Transações; **D7** venda do TD só nas distribuidoras com título aberto; **D8** sem grupo.

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Quais empresas do BCB entram no cadastro (e no seed) | **(a)** todas as do IF.data (inclui instituições de pagamento, SCD, consórcio, câmbio — milhares que não emitem nem distribuem RF); **(b)** só os segmentos da tabela 4.3 (bancos, financeiras, cooperativas, corretoras/DTVMs), vivas e mortas | **(b)** — o resto nunca aparece num seletor; o grupo não precisa da empresa líder no cadastro (a Nu Pagamentos é líder do grupo Nubank e é instituição de pagamento) |
| **D2** | Tabela produto → tipos de emissora e "coberto" (seção 4.4) | aceitar como está / ajustar linhas | **aceitar**, com a cobertura conferida na base legal na E1 (LIG em aberto) |
| **D3** | Regra H no Tesouro (um título por ativo por dia) com distribuidora | **(a)** por ativo **e distribuidora** (comprar o mesmo título no mesmo dia em duas corretoras passa a valer); **(b)** como hoje | **(a)** — é uma compra legítima, e o FIFO por distribuidora já separa os dois títulos |
| **D4** | Aporte com código de papel que já existe em outro título do usuário | **(a)** recusa com mensagem "já existe o título X com esse código — escolha-o"; **(b)** o sistema direciona o aporte para esse título | **(a)** — o usuário vê o que está acontecendo; código igual em título errado é erro de digitação, não intenção |
| **D5** | Logo da linha de RF | grupo da **emissora** / grupo da distribuidora | **emissora** — é quem deve o dinheiro (e já era a intenção registrada no escopo de bugs de transação) |
| **D6** | Filtros de Emissora e Distribuidora | **(a)** em Transações (quando o tipo é RF ou TD) e colunas ordenáveis na Carteira; **(b)** também filtro na Carteira | **(a)** — a Carteira já agrupa por tipo e as colunas resolvem; o filtro faz falta onde há muitas linhas |
| **D7** | Seletor de distribuidora na **venda** do TD | só as distribuidoras com título aberto do ativo (já escolhida quando houver uma só) / todas | **só as com título aberto** — vender onde não há título nunca fecha |
| **D8** | Nome do grupo da "Instituição não listada" e do "Tesouro Nacional" | sem grupo (mostram o próprio nome) / grupo próprio | **sem grupo** — não são conglomerados |

---

## 12. Pendências que este plano cria

- Logo pelo site da empresa (caminho 2): o BCB e a CVM informam o site; ícone pelo domínio com qualidade variável
  (testado em 15/10: Nubank 256 px, Opea 192 px, Sicredi 16 px).
- Nomes da B3 sem correspondência (L21): aplicar quando a importação de RF/TD existir.
- Regra de saída das demais instituições (27.2 continua): só a Nu Financeira está provada.
- Cooperativas por sistema (Sicoob, Sicredi, Unicred, pela central): não têm conglomerado prudencial no BCB.

---

## Adendo A — logos por raiz de CNPJ (16/10/2026)

Pedido do Guilherme depois da E7: o logo só existia pelo ticker de uma ação (`fixed_income_groups.logo_ticker`), e
quase nenhuma emissora de renda fixa tem ação na B3 (planilha `docs/emissores/Logos_Emissoras_RF_2026-10-16.xlsx`:
272 grupos sem logo). Fontes levantadas no mesmo dia:

| Ordem | Fonte | Chave | Formato | Observação |
|---|---|---|---|---|
| 1 | Diretório de participantes do Open Finance Brasil (`data.directory.openbankingbrasil.org.br/participants`, campo `AuthorisationServers[].CustomerFriendlyLogoUri`) | CNPJ (`RegistrationNumber`) | SVG | oficial: a instituição publica o próprio logo; 105 organizações em 16/10, cobrindo 55 empresas ativas do cadastro |
| 2 | `logos-bancos-br` (npm, jsDelivr), `data/logo-urls.min.json` (ISPB → arquivo) | ISPB = raiz do CNPJ | PNG 256×256 normalizado | listas do BCB (STR, Pix) + o diretório acima; versão fixa na configuração |
| — | Brandfetch (por domínio), Bancos-em-SVG | domínio / nome | — | fora: termos de armazenamento não claros; sem chave |

| # | Regra |
|---|---|
| A1 | O arquivo é o estado (como os logos do catálogo): `carga/data/logos/rf/{raiz}.{svg\|png}`, sem coluna nova |
| A2 | Rotina na adm-api (`FixedIncomeLogoService`): para cada raiz do cadastro e cada líder de grupo, a primeira fonte que tiver logo; só quem não tem arquivo (mensal, junto da rotina de emissoras) ou tudo de novo (`POST /api/v1/admin/fixed-income-issuers/logos?all=true`). Fonte fora do ar não falha a rotina |
| A3 | SVG só entra sem script (`<script`, `on…=`, `javascript:`, `<foreignObject`); a API serve com a mesma CSP dos logos do catálogo |
| A4 | Logo de um título (D5, pela emissora): o ticker curado do grupo; senão o arquivo da própria emissora; senão o da líder do grupo; senão o de outra empresa do grupo, as ativas primeiro (acrescentado no fechamento). "Instituição não listada" e Tesouro Nacional não têm |
| A5 | `GET /api/v1/fixed-income-issuers/logos/{raiz}`, público como `/assets/*/logo` (a tela usa `<img src>`); o DTO passa a levar a URL (`issuerLogoUrl`/`logoUrl`) em vez do ticker |

---

## 13. Andamento

- 16/10/2026 — plano criado a partir do levantamento (seção 10).
- 16/10/2026 — D1–D8 respondidas pelo Guilherme, todas pela recomendação (seção 11); implementação autorizada.
- 16/10/2026 — implementação E1–E7:
  - **E1** V54 (estrutura, as 2 linhas fixas, RDC, `issuer_kinds`/`guaranteed` com a cobertura da 4.4 conferida no
    Regulamento do FGC — Res. CMN 4.222/2013, Anexo II, art. 2º — e no do FGCoop — Res. CMN 4.284/2013 —, colunas do
    título e remoção do texto livre); entidades, repositórios e `TreasuryAllocator` por distribuidora no core.
  - **E2** adm-api: `BcbInstitutionsClient` (IF.data pela última data-base publicada, até 8 trimestres para trás;
    sites das sedes), `CvmIntermediary`, `CvmSecuritizer`, `FixedIncomeIssuerImportService`, `FixedIncomeIssuerScheduler`,
    `POST /api/v1/admin/fixed-income-issuers`, etapa do bootstrap. Testes com recortes das respostas reais.
  - **E4** app-api: `GET /api/v1/fixed-income-issuers`, `NoteInfo` nos DTOs, validações R4–R8, regra H e FIFO por
    distribuidora, regra de saída da distribuidora; compra de Tesouro importada da B3 casa a distribuidora pelo nome do
    participante (sem correspondência: "Instituição não listada" + notificação `RF_DISTRIBUIDORA_NAO_LISTADA`).
  - **E5** troca de distribuidora: cascata do Tesouro (`GET`/`POST /api/v1/transactions/{id}/distributor-change`) e
    troca direta na RF com o recálculo dos resgates. Testes funcionais em `EmissorasRendaFixaIT`.
  - **E6** telas: `IssuerPicker` (emissora filtrada pelo produto, distribuidora; na venda do Tesouro só as com título
    aberto), código do papel, aviso da regra de saída na edição de RF, `DistributorChangeModal`; Carteira com
    EMISSORA/DISTRIBUIDORA e o logo do grupo da emissora; Transações com o TÍTULO pelo grupo (no Tesouro, a
    distribuidora) e os filtros de Emissora e Distribuidora.
  - **E7** Claude.md, PENDENCIAS (4.3 e 27.1 resolvidos, 27.2 atualizado, seção 28), README e `admin.mjs`, nota na D1
    do PLANO_VENDA_RENDA_FIXA.
  - **Desvios:** (1) a rotina mensal não tem recuperação na subida (é mensal e o bootstrap cobre o banco novo); (2) as
    bases antigas do IF.data não entram na rotina — a empresa que perde o conglomerado mantém o grupo, e os vínculos das
    inativas da curadoria vêm pelo seed (V55); (3) o erro da cascata que não fecha é a mensagem do `TreasuryAllocator`
    (venda, data e distribuidora), mostrada no modal, e não um motivo ao lado de cada item; (4) achado: a busca dos
    títulos do ativo (`GET /transactions/notes`) só achava ticker em maiúsculas, o que deixava o Tesouro de fora —
    corrigido (exato primeiro, depois em maiúsculas).
  - **E3** feita no mesmo dia, quando o IF.data voltou (estava com HTTP 500 desde ~01:00): integração rodada no banco
    de desenvolvimento (data-base 06/2026 — a de 09/2026 ainda vem vazia; 4.301 instituições do BCB, 1.730
    intermediários, 2.531 companhias e 51 securitizadoras da CVM → 6.920 empresas e 290 grupos); curadoria da planilha
    de 09/10 aplicada (98 nomes de grupo MANUAL — os outros 15 da planilha são de grupos sem empresa de RF, fora pela
    D1 —, 2 grupos extintos, 32 inativas no grupo antigo, Nu Financeira UEPS, logo de 17 grupos); V55 gerada por
    `carga/scripts/gerar_seed_emissoras_rf.mjs` (292 grupos, 6.920 empresas) e provada no banco vazio do Testcontainers
    (`SchemaTest.seedDasEmissoras`).
  - Achado na conferência: os títulos de Tesouro que já existiam ficaram com "Instituição não listada" também como
    emissora (REGRAS 8.1), então a coluna TÍTULO de Transações passou a reconhecer o Tesouro pelo tipo do ativo, e não
    pelo tipo da emissora.
- 16/10/2026 — validação: `mvn install` (core com o `SchemaTest` da V55, integration, adm-api) e a suíte da app-api
  verdes; front com tsc, lint e build; V55 aplicada no banco de desenvolvimento (6.922 empresas, 292 grupos);
  conferência no navegador: colunas EMISSORA/DISTRIBUIDORA na Carteira, seletor da venda do Tesouro com só a
  distribuidora de título aberto (já escolhida), busca de emissora filtrada pelo produto (CDB só bancos, empresa em
  liquidação em cinza), busca de distribuidora pelo grupo ("nubank" → Nu Financeira, Nu Invest e a Nu DTVM inativa),
  filtros de Emissora e Distribuidora em Transações.
- 16/10/2026 — Adendo A (logos por raiz de CNPJ) aprovado pelo Guilherme como parte deste escopo e implementado:
  `FixedIncomeLogos` (core, pasta `carga/data/logos/rf`), `FixedIncomeLogoService` (adm-api; Open Finance → logos-bancos-br
  0.8.0; SVG com script recusado), passo de logos na rotina mensal de emissoras e
  `POST /api/v1/admin/fixed-income-issuers/logos`, `GET /api/v1/fixed-income-issuers/logos/{raiz}` público com CSP,
  `issuerLogoUrl`/`logoUrl` no lugar do ticker nos DTOs, telas com o logo pela URL. Primeira rodada no banco de
  desenvolvimento: 105 raízes no Open Finance e 467 no logos-bancos-br; 443 logos gravados (60 do Open Finance, 383 do
  pacote); recusados por script/HTML: Losango, Bradesco Financiamentos (Losango), Uniprime (2) e Ágora — ficam com o
  PNG do pacote quando ele tem. O PicPay passou a ter logo (arquivo do PicPay Bank).
- 16/10/2026 — conferência do Guilherme: o logo do PicPay (PNG do logos-bancos-br) parecia menor que os outros — o pacote
  normaliza com margem transparente. A rotina passou a recortar a margem do PNG e centralizar a marca num quadrado
  (`trimTransparentMargin`); as 383 imagens do pacote foram baixadas de novo. Na mesma conferência, os FIIs sem logo
  (só 66 de 347 tinham arquivo): a BRAPI tem o ícone deles (em geral o da gestora/administradora, o mesmo arquivo
  para vários fundos), mas não estavam em disco — a rotina mensal só procura quem falta, no dia 1, e a causa da falta
  anterior não foi apurada. A rodada completa de logos do catálogo (`POST /admin/logos`) levou a 312 de 347.
- 16/10/2026 — fechamento, a pedido do Guilherme: V56 (FK composta título × posição no mesmo produto, `ON UPDATE
  CASCADE`; achado da troca de CDB para RDB feita só na posição; remover a duplicação ficou na PENDENCIAS 28.5) e o
  quarto passo do logo (A4: outra empresa do grupo). Rodada completa de testes no fim.
- 16/10/2026 — escopo finalizado pelo Guilherme. A busca manual dos logos que nenhuma fonte cobriu (207 grupos e 844
  empresas, planilha de 16/10) foi para a PENDENCIAS 28.1.
