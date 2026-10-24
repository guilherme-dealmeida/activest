# PrimeVest — Plano da Fonte do Tesouro (cache com recusa, bloqueio e fonte parada)

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **concluído** (22/10/2026; execução por etapas autorizada no mesmo dia) — andamento na seção 8. Decisões D1–D9
> tomadas em 22/10/2026 (D9: aguardar, PENDENCIAS 34.8, em observação).
> Origem: `docs/PENDENCIAS.md`, item **34.5** (achado da validação das Tarefas, 22/10/2026).

## 1. Objetivo

Fazer a rodada do preço do Tesouro (`TREASURY_PRICES`) dizer a verdade sobre a fonte:

1. **não gravar no cache** (`carga/data/`) uma resposta que não é o arquivo esperado (página HTML de recusa, aviso de
   limite de requisições);
2. tratar a recusa como **fonte indisponível**: aviso `FONTE_INDISPONIVEL` e rodada que não termina `SUCCEEDED` com
   zero linhas sem explicação;
3. **parar de insistir** com uma fonte que está recusando (não alimentar o bloqueio);
4. avisar quando o **Tesouro não publicou** dados novos, que é diferente de estar bloqueado;
5. entender o bloqueio e sair dele.

## 2. Diagnóstico (22/10/2026)

Fontes: log da `primevest-adm-api` (desde 05/10/2026), metadados do CKAN do Tesouro Transparente
(`/ckan/api/3/action/resource_show`), código de `PublicFileCache`, `TesouroPriceClient`, `TesouroSalesClient`,
`TreasuryPriceService` e `TreasuryPricesTask`. Horários em Brasília.

### 2.1 Linha do tempo

| Quando | O que aconteceu |
|---|---|
| 15/10 08:00 | Último download normal com dado novo: PU de 14/10 |
| 16/10 08:01 | Download normal: PU de **15/10** (58 títulos) — é o último PU gravado |
| 16/10 ~07:30 | **Última atualização dos dois arquivos no Tesouro** (`last_modified` 2026-10-16T10:34 e 10:29 no CKAN) |
| 17/10 a 20/10 | Rodadas com 58 títulos, 0 dias gravados, "último 2026-10-15": a fonte não tinha nada novo (sem download) |
| 20/10 | O último PU passa a ter mais de 4 dias: `needsRecovery()` fica verdadeiro e **toda subida** da adm-api chama a fonte |
| 21/10 01:13–01:43 | 6 subidas seguidas (reinícios do desenvolvimento). A fonte responde **HTTP 200 com a página "Request Rejected"** (244 bytes); o cache grava a página por cima dos dois CSVs |
| 21/10 01:52 em diante | A fonte responde **HTTP 429** ("Muitas Requisições Detectadas — volume atípico de requisições a partir da sua rede"), `Retry-After: 30`. O cache usa a cópia em disco (a página HTML) e a rodada termina `SUCCEEDED` com 0 títulos |
| 22/10 ~16:00 | Ainda 429 (conferido por `curl` na máquina do Guilherme). O endereço da API do CKAN responde normalmente — o limite é no download |

Volume de chamadas depois do bloqueio: 19 rodadas em 21/10 e 7 em 22/10 (cada uma pede os 2 arquivos), quase todas
"recuperação na subida". A D37 do plano de Tarefas (subida no máximo uma vez por dia) já reduz isso daqui para frente.

### 2.2 Achados

**A1 — O preço parado é da fonte, não nosso.** O Tesouro Transparente não atualiza `PrecoTaxaTesouroDireto.csv` nem
`VendasTesouroDireto.csv` desde 16/10/2026. O PU de 15/10 é o mais novo que existe lá. O bloqueio e o cache com a
página HTML vieram depois (21/10) e não mudaram o resultado até agora — mas vão esconder a volta da fonte (A2) e
nada no PrimeVest avisou que o preço do Tesouro está há uma semana sem atualizar.

**A2 — A recusa virou arquivo.** `PublicFileCache.fetch` aceita qualquer HTTP 200 como o arquivo. A página de recusa
foi gravada como `carga/data/tesouro/*.csv`, com a data do download (a fonte não manda `Last-Modified` na recusa).
A leitura não acha nenhuma linha, a rodada grava 0 e termina `SUCCEEDED`. Enquanto a página estiver lá, o
`If-Modified-Since` vai com 21/10 01:43.

**A3 — Recusa com cópia em disco é silenciosa.** Para 429 (e qualquer status ≠ 200/304/404) o cache registra um
`WARN` e devolve a cópia em disco. O `TreasuryPriceService` só chama `notices.sourceUnavailable` quando **não** há
cópia — com cópia, nunca. Resultado: nenhum `FONTE_INDISPONIVEL`, nenhuma rodada `FAILED`.

**A4 — A recuperação na subida insiste com a fonte que recusou.** `needsRecovery()` olha só a data do último PU. Com
a fonte parada (A1) ele fica verdadeiro todos os dias, e antes da D37 cada reinício chamava a fonte. O bloqueio diz
"volume atípico a partir da sua rede": é compatível com essas chamadas repetidas, mas não dá para afirmar daqui que
foram a causa (o Tesouro também pode ter endurecido o filtro na mesma época em que parou de publicar).

**A5 — O mesmo risco existe nas outras fontes.** `PublicFileCache` serve também à B3 (COTAHIST), à CVM e ao BCB.
Onde o arquivo é baixado sem `refresh` (anos fechados do COTAHIST, arquivos da CVM), uma página de recusa gravada
seria **permanente**. Varredura de 22/10 em `carga/data/`: só os 2 arquivos do Tesouro estão com HTML.

**A6 — Cópia de referência no R2.** Um `data push` feito agora subiria a página HTML para o bucket como se fosse o
arquivo novo (o push sobe o que mudou). Não há sinal de que tenha acontecido; conferir com `data status`.

**A7 — Diagnóstico pela nuvem não é possível:** o proxy da organização nega o domínio (403 de política, regra 5.6).
Os testes foram feitos na máquina do Guilherme: 4 pedidos ao endereço de download e 3 à API do CKAN em 22/10.

**A8 — O bloqueio é por tipo de cliente, não pela rede (teste da D8, 22/10/2026 17:51).** No mesmo minuto, na mesma
máquina: o navegador do Guilherme baixou `PrecoTaxaTesouroDireto.csv` inteiro (14.598.768 bytes, o tamanho que o CKAN
informa; cabeçalho certo; última data base **15/10/2026**, 177.434 linhas) e o `curl` recebeu HTTP 429. A página fala
em "volume atípico a partir da sua rede", mas o filtro recusa o acesso automático (curl, `Java-http-client`) e deixa
passar o navegador. O download também confirma A1: o arquivo da fonte para em 15/10.

## 3. Regras propostas

| # | Regra | Onde |
|---|---|---|
| R1 | O download só vira o arquivo do cache depois de **conferido**: cada cliente informa o que espera (CSV: a primeira linha começa com o cabeçalho conhecido; ZIP: começa com `PK\3\4`). Sem conferência específica, vale a padrão: recusa conteúdo que começa com `<html`/`<!doctype` ou chega como `text/html`. Reprovado: apaga o `.part`, mantém o arquivo anterior e trata como recusa (R2) | `PublicFileCache` + clientes |
| R2 | O cache devolve **o que aconteceu**, não só o caminho: baixou, não mudou (304), recusado (429/403/5xx/conteúdo reprovado, com o status e o `Retry-After`), sem rede, não existe (404) — e o arquivo em disco, se houver. Quem chama decide o que fazer com a cópia | `PublicFileCache` |
| R3 | Tesouro: recusa ou fonte fora do ar → `FONTE_INDISPONIVEL` (uma por dia, como hoje) e o desfecho da rodada da D2 | `TreasuryPriceService` |
| R4 | Uma tentativa por rodada, sem repetir dentro dela; o `Retry-After` vai para a mensagem e para o log | `PublicFileCache` |
| R5 | Fonte parada: se a data base mais recente do arquivo estiver mais de N dias úteis atrás (D3), aviso de que o Tesouro não publica desde a data X | `TreasuryPriceService` + `BusinessCalendar` |
| R6 | Leitura defensiva: arquivo sem o cabeçalho esperado é erro ("não é o CSV do Tesouro"), não "0 linhas" — cobre arquivos já gravados antes da R1 | `TesouroPriceClient.read`, `TesouroSalesClient.onSale` |
| R7 | Identificação do cliente HTTP (D6) | `PublicFileCache` |
| R8 | Testes: 200 com HTML não substitui o arquivo; 429 com e sem cópia; CSV sem cabeçalho; fonte parada gera o aviso | `PublicFileCacheTest` (novo), `TreasuryPriceIT` |

## 4. Decisões (para o Guilherme)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Onde entra a conferência do conteúdo (R1/R2) | (a) só nos clientes do Tesouro; (b) no `PublicFileCache`, para todas as fontes, com a conferência informada por cliente | **Decidido (22/10/2026): (b).** A5: na CVM e nos anos fechados do COTAHIST a recusa gravada seria permanente |
| D2 | Desfecho da rodada do Tesouro quando a fonte recusa e há cópia em disco | (a) `FAILED` com a mensagem da fonte ("Tesouro Transparente recusou: HTTP 429 …"); (b) `SUCCEEDED` com a cópia e só o aviso | **Decidido (22/10/2026): (a).** A rodada existe para trazer o PU novo; com a fonte recusando ela não trouxe, e a tela de Tarefas deve mostrar isso |
| D3 | Avisar fonte parada (A1)? Com quantos dias úteis de atraso? | (a) sim, mais de 2 dias úteis sem data base nova; (b) sim, outro limite; (c) não | **Decidido (22/10/2026): (a).** O arquivo sai com D-1 todo dia útil; 2 dias úteis já é anormal. Aviso no máximo um por dia |
| D4 | Insistência depois de uma recusa | (a) basta a D37 (subida uma vez por dia) + o 08:00 + Run manual; (b) além disso, guardar "recusado até" e pular a fonte até lá, também no Run | **Decidido (22/10/2026): (a).** Fica em no máximo ~2 chamadas por dia sem estado novo; (b) só se o bloqueio voltar |
| D5 | `needsRecovery()` com a fonte parada | (a) como está (último PU com mais de 4 dias) — com a D37 roda uma vez por dia; (b) considerar também a última rodada que conseguiu falar com a fonte | **Decidido (22/10/2026): (a).** A D37 já limita; (b) acrescenta estado sem ganho claro |
| D6 | Identificação nas chamadas | (a) `User-Agent` próprio e honesto (ex.: `PrimeVest/1.0 (+e-mail de contato)`); (b) manter o padrão do Java (`Java-http-client/21`) | **Decidido (22/10/2026): (a).** Ajuda o Tesouro a nos reconhecer se for preciso pedir liberação. **Não** se imita navegador para passar pelo filtro |
| D7 | Os 2 arquivos com HTML em `carga/data/tesouro/` | (a) apagar na execução (a próxima rodada baixa o arquivo inteiro quando a fonte liberar; até lá, a rodada falha com a mensagem certa); (b) baixar de novo do R2 (`data pull`) | **Feito pelo Guilherme (22/10/2026): apagados os 2 arquivos em `carga/data/tesouro/` e no bucket R2** — equivale à (a). Antes, recomendação: **(b) se o R2 tiver a cópia boa de 16/10** (conferir com `data status`), senão (a). Opção (c), depois de A8: usar os arquivos baixados pelo navegador do Guilherme (o de preços já existe, 22/10/2026), com a data do arquivo igual ao `Last-Modified` da fonte (16/10) para o `If-Modified-Since` continuar certo |
| D8 | O bloqueio | (a) esperar, sem chamar a fonte além do 08:00, e reavaliar em 48 h depois da correção; (b) abrir chamado no Tesouro Transparente com o *Support ID* da página; (c) os dois, nessa ordem | **Decidido (22/10/2026): (c)** — esperar 48 h com a correção no ar e, se continuar, chamado (texto preparado pelo Claude, aberto pelo Guilherme). Antes, o Guilherme confere no navegador se o bloqueio é da rede de casa (abrir o link em casa e no 4G). **Teste feito (22/10/2026): o navegador em casa baixa, o curl não (A8)** — o chamado passa a pedir liberação do acesso automático identificado (D6), não do IP. Imitar navegador continua fora |
| D9 | Se o filtro continuar recusando o acesso automático depois da correção e do chamado (A8) | (a) só esperar/insistir no chamado; (b) procurar outra fonte oficial do PU que aceite acesso automático; (c) carga manual pelo administrador (enviar o CSV baixado no navegador pela tela de Tarefas) como contingência | **Decidido (22/10/2026): aguardar** — vira pendência (`docs/PENDENCIAS.md`, 34.8) e só se decide se o bloqueio persistir depois do chamado; hoje não há urgência, a fonte não publica desde 16/10 (A1) |

## 5. Etapas

| Etapa | O que | Fecha quando |
|---|---|---|
| E1 | Conter: conferir o R2 (`data status`), tratar os 2 arquivos (D7); nenhuma chamada manual à fonte | `carga/data/tesouro` sem HTML |
| E2 | `PublicFileCache`: R1, R2, R4, R7 + conferência por cliente (Tesouro, B3, CVM, BCB conforme D1); testes de unidade | bateria verde |
| E3 | Tesouro: R3, R5, R6 (D2, D3); `TreasuryPriceIT` com recusa, HTML e fonte parada | bateria verde |
| E4 | Validação: compilar, testes, lint, subir; rodada com a fonte ainda recusando → `FAILED` + `FONTE_INDISPONIVEL`, arquivo do cache intacto; quando a fonte voltar, rodada normal com PU novo | comportamento conferido |
| E5 | Documentação: `Claude.md`, `docs/PENDENCIAS.md` (34.5), `README.md`, andamento deste plano | — |

## 6. Quem faz

| O que | Claude | Guilherme |
|---|---|---|
| Decisões D1–D8, ordem de execução, git | — | ✔ |
| Teste do link no navegador (casa × 4G) e chamado ao Tesouro (D8) | prepara o texto | ✔ |
| `data status` / `data pull` (E1) | ✔ | — |
| E2–E5 | ✔ | revisa |

## 7. Riscos

- **A fonte continuar parada** depois do desbloqueio: a correção não traz PU novo — só faz o sistema avisar (R5). A
  valorização do Tesouro segue no PU de 15/10 até o Tesouro publicar.
- **Cabeçalho do CSV mudar** no Tesouro: a conferência da R1 recusa o arquivo novo. É o comportamento desejado (falha
  com mensagem clara em vez de ler errado), mas exige ajuste no cliente.
- **Rodada `FAILED` todo dia** enquanto o bloqueio durar (D2-a): esperado; o aviso `FONTE_INDISPONIVEL` sai uma vez
  por dia.

## 8. Andamento

- 22/10/2026 — Diagnóstico e plano.
- 22/10/2026 — Guilherme decidiu D1 (b), D2 (a), D3 (a) e D6 (a), como recomendado. Em aberto: D4, D5, D7, D8 (teste
  do link no navegador, em casa e no 4G, antes do chamado) e a ordem de execução.
- 22/10/2026 — Teste da D8: o navegador em casa baixou o arquivo de preços (última data base 15/10) enquanto o curl, na
  mesma máquina e no mesmo minuto, recebia 429 — o bloqueio é por tipo de cliente (A8). Nova opção (c) na D7.
- 22/10/2026 — D7 resolvida pelo Guilherme: os 2 arquivos com HTML foram apagados do projeto e do bucket R2. Sem cópia
  em disco, a rodada do Tesouro passa a falhar com `FONTE_INDISPONIVEL` enquanto o filtro recusar (comportamento atual
  do código para fonte fora do ar sem cópia); a próxima rodada que a fonte aceitar baixa os dois arquivos inteiros. Uma
  carga inicial num ambiente novo (`data pull` + `bootstrap`) fica sem o Tesouro até lá.
- 22/10/2026 — Guilherme decidiu D4 (a), D5 (a), D8 (c) e D9 (aguardar; registrada como PENDENCIAS 34.8). Todas as
  decisões tomadas; falta a ordem de execução (de uma vez ou por etapas, E2–E5).
- 22/10/2026 — Guilherme mandou executar por etapas.
- 22/10/2026 — **E1** já estava feita (D7). **E2** (cache): `PublicFileCache` com `get` → `Result` (`Outcome`: baixou,
  não mudou, do disco, 404, recusou, sem resposta; status e `Retry-After`), `ContentCheck` (padrão: não HTML nem
  vazio; `csvHeader`, `zip`, `pdf`, `json`) aplicada no `.part` antes de virar arquivo, `User-Agent` `PrimeVest/1.0`;
  o `fetch` de sempre mantém o comportamento (recusa com cópia devolve a cópia). Conferência por cliente: Tesouro
  (`Tipo Titulo;`), CVM (`CNPJ_CIA;`, zips, PDF do RAD), B3 (zips), BCB (JSON). Varredura de `carga/data` (b3, bcb,
  cvm): **7 páginas HTML do RAD gravadas como PDF** em `cvm/proventos` em 05/10/2026 (1572213_1, 1572296_1, 1572834_4,
  1573116_2, 1573131_1, 1573141_1, 1573629_2) — o risco A5 tinha acontecido; apagadas com autorização para serem
  baixadas de novo (a cópia do R2, se houver, fica com o Guilherme). `PublicFileCacheTest` (7); `CvmOpenDataClientTest`
  passou a servir conteúdo válido. Bateria: core 210; integration 103; adm-api 125 + 78; app-api 210 + 263 — verde.
- 22/10/2026 — **E3** (Tesouro): `TesouroPriceClient`/`TesouroSalesClient.fetch()` devolvem o `Result`; leitura confere
  o cabeçalho (R6); `TreasuryPriceService` — recusa/sem resposta/404 → `FONTE_INDISPONIVEL`, com cópia lê a cópia e
  devolve `sourceProblem`, sem cópia erro (D2); fonte respondeu e a data base está mais de 2 dias úteis atrás →
  `FONTE_DESATUALIZADA` (tipo novo, pendência; `AdminNotices.sourceStale`/`sourceCurrent`), em dia resolve (D3);
  `TreasuryPricesTask` grava o resumo e termina `FAILED` com o `sourceProblem`. Testes: `TreasuryPriceIT` 4 → 8,
  `TreasuryPricesTaskTest` (2). Bateria: core 210; integration 103; adm-api 127 + 82; app-api 210 + 263 — verde na
  segunda rodada (na primeira, `TaskExecutorTest.logDaRodada`, do core e dependente de tempo, falhou uma vez sem
  relação com a mudança).
- 22/10/2026 — **E4** (validação): adm-api e app-api reiniciadas com o código novo; `admin.mjs tesouro` (rodada 16):
  **o download do `PrecoTaxaTesouroDireto.csv` passou** com a identificação nova (14.598.768 bytes, data do arquivo
  16/10/2026 07:34, a da fonte), enquanto o `curl` no mesmo minuto seguia com 429 — o filtro recusava o cliente sem
  identificação (A8), não a rede. Rodada `SUCCEEDED`: 109 títulos, 0 dias gravados, último 15/10, `staleSince`
  2026-10-15, e a pendência **`FONTE_DESATUALIZADA` aberta** ("Tesouro Transparente sem dado novo desde 15/10/2026",
  4 dias úteis). O arquivo de vendas, na mesma rodada, caiu sem resposta ("header parser received no bytes"): oferta
  sem atualizar, como previsto; a rodada das 08:00 tenta de novo. O caminho da recusa (rodada `FAILED`) ficou provado
  pelos testes — na validação a fonte aceitou. Sem mudança no front (lint não se aplica).
- 22/10/2026 — **E5**: `Claude.md` (revisão, rotina e fonte do Tesouro, tipos de notificação, seção "Fonte do Tesouro
  e cache dos arquivos públicos", testes), `README.md`, `docs/PENDENCIAS.md` (34.5 resolvida; 34.8 em observação: com a
  identificação o acesso voltou) e este andamento. D8 (chamado) fica sem objeto enquanto a chamada identificada passar.
