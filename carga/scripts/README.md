# carga/scripts/

Carga e manutenção do PrimeVest (`docs/planos/PLANO_CARGA_INICIAL.md`). Nada aqui depende de pasta fora do
projeto: a raiz do repositório é achada a partir de `carga/scripts/lib/env.mjs`, e as variáveis vêm do
`.env`.

## Máquina nova

```bash
cp .env.example .env                  # preencha PRIMEVEST_SENHA e o token SÓ LEITURA do R2 (S3_*)
docker compose --profile carga run --rm data pull          # bucket R2 -> carga/data/ (~1,4 GB; --minimo só o essencial)
docker compose up -d --build                                # as migrations rodam ao subir as APIs
docker compose --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap
docker compose logs -f adm-api                              # termina em "Carga inicial (...) concluida"
```

Sem Node na máquina, tudo roda pelos contêineres `data` e `tools` (profile `carga`). Com Node:
`node carga/scripts/admin.mjs bootstrap`.

## O que tem aqui

| Arquivo | Uso |
|---|---|
| `admin.mjs` | dispara uma rotina de administrador: `bootstrap`, `proventos`, `cvm/proventos`, `closing`, `logos`, `issuers/import`... Login na app-api, rotina na adm-api |
| `get.mjs` | GET autenticado na app-api, para conferência (`node carga/scripts/get.mjs /api/v1/assets/PETR4/indicators`) |
| `gerar_seed_emissores.mjs` | regera a migration de seed dos emissores (como a V30) a partir do banco de desenvolvimento, depois de uma curadoria |
| `gerar_seed_emissoras_rf.mjs` | regera a migration de seed das emissoras e distribuidoras de renda fixa (como a V55) a partir do banco de desenvolvimento, depois da integração BCB + CVM e da curadoria (`docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md`, E3) |
| `data/data.sh` | `carga/data/` ↔ bucket R2 (rclone), pelo contêiner `data`: `pull [--minimo]`, `push` (só quem tem a chave de escrita — gera os manifests), `status` |
| `sql/contagens.sql` | contagens do catálogo, para comparar um banco novo com o de desenvolvimento |
| `sql/datas_pagamento.sql` | cobertura da data de pagamento dos proventos, por ano |
| `sql/conferir_balancos.sql` | conferência da carga de balanços (quantidade de ações, LPA) |
| `b3investidor/simulador/` | simulador da API da Área do Investidor da B3 (contêiner `b3-investidor-mock`, profile `b3mock`) — seção abaixo |
| `b3investidor/sintetico/` | dados inventados no formato da B3, que o simulador usa quando não há extrato em `carga/mocks/` |
| `b3investidor/conferir.mjs` | ponta a ponta da B3 contra o simulador: conecta, autoriza, sincroniza, confirma e confere C1–C5 |

Os SQL rodam no contêiner do banco: `docker exec -i primevest-db psql -U postgres -d db_primevest < carga/scripts/sql/contagens.sql`.

## Bucket (produtor e consumidor)

A aplicação lê só a pasta `carga/data/`. O bucket `primevest-data` (Cloudflare R2) é a cópia de referência:

- **consumidor** (qualquer máquina, token só leitura): `data pull` na primeira subida;
- **produtor** (quem mantém o projeto, chave de escrita): as rotinas da adm-api gravam em `carga/data/`
  (CVM, COTAHIST, histórico e 12 meses de proventos da B3, avisos do FNET, relatórios de proventos
  da CVM, logos) e o `data push` sobe o que é novo — nunca apaga nada no bucket — e regrava
  `manifests/` (lista de arquivos com tamanho e hash, por dataset, e o resumo `manifest.json`).

## levantamento/xlsx_para_json.py

Gera `carga/data/levantamento/datas-pagamento.json` (datas de pagamento levantadas, docs/planos/PLANO_CARGA_INICIAL.md seção 11)
a partir da planilha em `carga/data/levantamento/origem/`. Precisa de Python com `openpyxl`; roda na máquina produtora:

```
python carga/scripts/levantamento/xlsx_para_json.py
docker compose --profile carga run --rm data push
```

Valida a planilha (status × data, pagamento antes da data-com, chave repetida) e não grava nada se houver erro.

## b3investidor/ — simulador da Área do Investidor da B3

`docs/planos/PLANO_INTEGRACAO_B3.md` (6.10 e F5). Sem contrato com a B3, o PrimeVest fala com um simulador nas mesmas
rotas e no mesmo formato da API (token, autorização, Negociação, Movimentação, Posição, Eventos Provisionados, página de
opt-in com Autorizar e Revogar, paginação de 20 itens, erros 422.01–422.07, falha sob demanda com `?_fail=429|500|503`).

Os dados são os extratos que o investidor exporta da Área do Investidor (Negociação, Movimentação, Proventos a Receber),
em `carga/mocks/` (**fora do git**), lidos **como vieram** — sem conversor nem arquivo intermediário. Sem planilha, o
simulador usa `sintetico/`. O CPF é um fictício válido (`B3I_MOCK_DOCUMENT`): as planilhas não trazem CPF e o real não
aparece em lugar nenhum.

Na pilha de teste (portas e `B3I_*` no `.env.teste`):

```
pvt --profile b3mock up -d --build                 # pvt = docker compose -p primevest-teste --env-file .env.teste ...
node carga/scripts/b3investidor/conferir.mjs --api http://localhost:8082 --mock http://localhost:8190 \
     --gabarito http://localhost:8080 --revogar    # C1 compara com a carteira da pilha de desenvolvimento
```

Página de opt-in do simulador: http://localhost:8190/optin (teste) ou http://localhost:8090/optin (dev).

