# PrimeVest — Testes funcionais (de ponta a ponta)

> Gerado em 02/10/2026 lendo direto o código de teste (classes `*IT`) · revisado em 03/10/2026 (Markdown) · 05/10/2026
> (cadastro de usuário: `SignupIT`, `SignupDisabledIT`; `IntegrationsB3IT` e o 11º teste de `IntegrationsIT`
> entraram na lista) · 10/10/2026 (Preço Teto: `CeilingPriceIT`; COTAHIST diário: 2 testes em `HistoricalQuotesImportIT`) · 21/10/2026
> (`DividendCorrectionIT` e `DividendReconciliationIT` entram; `ExtratoProventosIT` sai — `docs/planos/PLANO_CORRECAO_PROVENTOS.md`). Contagens conferidas no `mvn clean verify` de 05/10/2026.

**O que é "funcional" aqui:** as classes `*IT`, rodadas pelo Failsafe no `mvn clean verify`. Sobem o Spring inteiro contra PostgreSQL 17 de verdade (Testcontainers, com Flyway e `ddl-auto: validate`) e exercitam a API por HTTP ou o serviço com o banco real. Fontes externas (B3, FNET, CVM, BRAPI, CoinGecko, Binance) são mockadas: nenhum teste depende de rede. Rotinas agendadas e mensagens Redis ficam desligadas no perfil de teste; os testes de ponta a ponta ligam adm-api e app-api direto (`CatalogE2EBase`).

Os nomes são os `@DisplayName` do código (sem acento, como estão lá).

```bash
mvn clean verify                       # tudo (precisa do Docker Desktop)
mvn -pl primevest-app-api -am verify   # um módulo
```

**Total: 27 classes, 209 testes funcionais** (contagem de 05/10/2026; em 10/10/2026 o `mvn verify` rodou 40 de integração na adm-api e 199 na app-api).

## Resumo

| Módulo | Classe | Testes |
|---|---|---|
| adm-api | [PaymentDateSurveyIT](#paymentdatesurveyit-2) | 2 |
| adm-api | [DividendCatalogIT](#proventoscatalogoit-6) | 6 |
| adm-api | [DividendCorrectionIT](#correcaoproventosit-24) | 24 |
| adm-api | [FiiMonthlyImportIT](#fiimonthlyimportit-3) | 3 |
| adm-api | [FinancialStatementImportIT](#financialstatementimportit-6) | 6 |
| adm-api | [IssuerImportIT](#issuerimportit-2) | 2 |
| adm-api | [IssuerLinkIT](#issuerlinkit-5) | 5 |
| adm-api | [HistoricalQuotesImportIT](#historicalquotesimportit-6) | 6 |
| app-api | [PaymentDateSourceIT](#paymentdatesourceit-5) | 5 |
| app-api | [AllocationIT](#alocacaoit-6) | 6 |
| app-api | [SignupDisabledIT](#cadastrodesligadoit-1) | 1 |
| app-api | [SignupIT](#cadastrousuarioit-14) | 14 |
| app-api | [PortfolioIT](#carteirait-13) | 13 |
| app-api | [StrategyIT](#estrategiait-13) | 13 |
| app-api | [CorporateEventsIT](#eventossocietariosit-5) | 5 |
| app-api | [ClosingIT](#fechamentoit-8) | 8 |
| app-api | [IndicatorsIT](#indicadoresit-9) | 9 |
| app-api | [DividendReconciliationIT](#proventosconferenciait-5) | 5 |
| app-api | [IntegrationsB3IT](#integracoesb3it-6) | 6 |
| app-api | [IntegrationsConfirmationIT](#integracoesconfirmacaoit-9) | 9 |
| app-api | [IntegrationsQueueIT](#integracoesfilait-9) | 9 |
| app-api | [IntegrationsIT](#integracoesit-11) | 11 |
| app-api | [PositionRefreshIT](#positionrefreshit-3) | 3 |
| app-api | [CeilingPriceIT](#precotetoit-6) | 6 |
| app-api | [DividendsToReceiveIT](#proventosareceberit-7) | 7 |
| app-api | [DividendsIT](#proventosit-6) | 6 |
| app-api | [DividendTaxIT](#proventosirit-15) | 15 |
| app-api | [IssuerPageIT](#telaemissorasit-7) | 7 |
| app-api | [TransactionsIT](#transacoesit-30) | 30 |

## Módulo adm-api (catálogo — porta 8081) — 7 classes, 28 testes

### PaymentDateSurveyIT (2)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/dividend/PaymentDateSurveyIT.java`

Datas de pagamento do levantamento no banco (docs/planos/PLANO_CARGA_INICIAL.md, 11 — L6/L7): grava so em evento sem data, com origem LEVANTAMENTO; nao passa por cima de data existente; A DEFINIR nao grava.

| # | Teste | Método |
|---|---|---|
| 1 | Completa o evento sem data (origem LEVANTAMENTO), mantem a data que ja existe e nao grava o A DEFINIR | `aplica` |
| 2 | Rodar de novo nao muda nada; a data da propria fonte do evento substitui a do levantamento | `idempotenteEFonteManda` |

### DividendCatalogIT (6)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/dividend/DividendCatalogIT.java`

F8 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: a rodada de proventos cobre o catalogo (ativos ligados a emissor, sem ninguem ter posicao) e le o historico de FII do FNET pelo CNPJ, so do cache `carga/data/fnet` — avisos reais de VISC11, PLAG11 e MAXR11, nos dois formatos.

| # | Teste | Método |
|---|---|---|
| 1 | Catalogo sem posicao: FNET pelo CNPJ, formato antigo e atual, sem recibo nem data-base invalida | `fnetPeloCnpj` |
| 2 | D15: B3 com provento mais novo que o FNET — busca no FNET uma vez; sem aviso novo, a B3 fica de reserva | `fnetPrincipalBuscaQuandoB3TemNovidade` |
| 3 | Rodar de novo nao grava nada; o mesmo provento da B3 com outras casas nao duplica | `idempotenteESemDuplicar` |
| 4 | FII que ainda nao distribuiu (APXR11): B3 sem proventos e nada em disco — sem falha, zero eventos | `fundoSemDistribuicao` |
| 5 | AMORTIZACAO RF da B3 e amortizacao: completa a do FNET (outras casas) e entra quando so a B3 tem | `amortizacaoRf` |
| 6 | Historico da B3 em disco (F10): baixado uma vez por emissor; sem evento gravado, relido do arquivo | `historicoDaEmpresaEmDisco` |

### DividendCorrectionIT (24)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/dividend/DividendCorrectionIT.java`

docs/planos/PLANO_CORRECAO_PROVENTOS.md (21/10/2026): as classes de erro da investigacao de proventos, com as respostas reais da B3 (KLBN, RECV, TRIS, ETER, PETR) e os avisos reais do FNET (TRXF11, AIEC11, SMRE11, BRIP11, ARRI11, XLPR11, MXRF11, CYCR11, EURO11, URPR11, EXES11).

| # | Teste | Método |
|---|---|---|
| 1 | A — KLBN: parcelas com a ultima arredondada (…225 x 3 e …224) nao duplicam nem somem, em duas rodadas | `parcelasDaB3ComArredondamento` |
| 2 | A — RECV3, TRIS3 e ETER3: a parcela de valor menor nao vira duplicata das outras | `outrasParcelas` |
| 3 | B — TRXF11: o aviso traz a cota e quatro recibos (TRXF13, 14, 15 e 26); so a cota entra | `reciboDeSubscricao` |
| 4 | B — ARRI11: recibos com o ticker da cota no aviso (R30 0,03) ficam fora; vale o ISIN | `reciboComOTickerDaCota` |
| 5 | C — AIEC11: aviso retificado (data-base corrigida); o inativo no FNET nao vira evento | `avisoRetificadoInativo` |
| 6 | C — AIEC11: o aviso antigo ja gravado vira o novo no lugar (mesmo id) quando o FNET o marca inativo | `avisoRetificadoDepoisDeGravado` |
| 7 | C — aviso republicado (mesma data-base): o evento passa a apontar para o aviso ativo, sem duplicar | `avisoRepublicado` |
| 8 | C — MXRF11: a retificacao que so muda o pagamento (07/11 -> 14/11/2025) atualiza o evento no lugar | `retificacaoMudaSoOPagamento` |
| 9 | C — MXRF11: sem o aviso antigo inativo no FNET, pagamento diferente nao muda o evento | `pagamentoDiferenteSemSituacaoNaoMuda` |
| 10 | C — CYCR11: aviso listado no FNET sob o CNPJ de outro fundo (CBOP11) e inativado vale pela situacao | `avisoInativoListadoSobOutroCnpj` |
| 11 | C — CYCR11: o evento ja gravado pelo aviso listado sob outro CNPJ passa para o aviso ativo | `avisoDeOutroCnpjJaGravadoPassaParaOAtivo` |
| 12 | C — a busca no FNET grava a situacao de cada aviso em carga/data/fnet/status | `situacaoGravadaNaBusca` |
| 13 | C — fundo com exatamente 100 avisos: a busca para no total (o FNET nao responde a pagina vazia) | `buscaParaNoTotal` |
| 14 | D — SMRE11: a reserva da B3 (data-com 01/06) e absorvida pelo aviso do FNET (data-base 29/05) | `reservaDaB3AbsorvidaPeloFnet` |
| 15 | D — SMRE11: com o aviso do FNET gravado, a B3 com outra data-com nao grava a reserva | `b3NaoDuplicaOFnet` |
| 15a | C8 — reler todos os avisos (fnet=todos) refaz a linha que faltava e nao duplica a que o FNET completou | `releituraDeTodosOsAvisos` |
| 16 | E — BRIP11: aviso com o ticker do fundo e o ISIN de VCRI11 (erro de preenchimento) vale pelo ticker | `isinDeOutroFundoComOTickerDoFundo` |
| 17 | E — sem o ticker do fundo, o ISIN da cota de outro fundo do catalogo (incorporacao) fica fora | `isinDeOutroFundoSemTicker` |
| 18 | E — XLPR11: aviso de outra classe do mesmo CNPJ (ANGC11, R$ 770) no dia do rendimento da cota fica fora | `outraClasseNoDiaDaCota` |
| 19 | E — EURO11: no dia da cota (ISIN BREUROCTF011, R$ 1,23), o recibo BREURO01M15 com o ticker EURO11 fica fora | `reciboComOTickerNoDiaDaCota` |
| 20 | B — URPR11: a linha de URPR13 (R$ 0,078) com o ISIN da cota, no aviso da cota (R$ 0,30), fica fora | `outraClasseComOIsinDaCota` |
| 21 | B — EXES11: rendimento da cota publicado so com o ticker EXES13 (sem a linha de EXES11) fica | `tickerDeOutraClasseSozinhoNoDia` |
| 22 | G — KLABIN S/A: o historico e pedido a B3 sem a barra (KLABIN SA); com a barra ela devolve vazio | `nomeDePregaoComBarra` |
| 23 | G — historico vazio em disco de quem tem provento nos 12 meses: busca de novo e grava | `historicoVazioBuscaDeNovo` |

### FiiMonthlyImportIT (3)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/fundamentals/FiiMonthlyImportIT.java`

F7 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: informe mensal de FII com o recorte real de 2026 (HGLG11 e o fundo 00.613.094/0001-74, jan a ago; os dois sao emissores no seed). O complemento traz, alem das linhas reais, uma linha a mais do HGLG11 em 08/2026 com versao 1 e valores 1 — a CVM so publica a versao mais recente, e a linha simula a reapresentacao chegando junto.

| # | Teste | Método |
|---|---|---|
| 1 | Oito meses de cada fundo: complemento e carteira juntos, versao mais alta | `importa` |
| 2 | Rodar de novo nao regrava; force regrava tudo | `idempotente` |
| 3 | Versao nova de um mes ja gravado substitui a linha | `reapresentacao` |

### FinancialStatementImportIT (6)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/fundamentals/FinancialStatementImportIT.java`

F5 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: importacao de DFP/ITR com os recortes reais (DFP 2025 e ITR 2026 de 8 companhias, DFP 2015 de BB e Petrobras, FRE 2025 e 2015).

| # | Teste | Método |
|---|---|---|
| 1 | DFP 2025 e ITR 2026: metricas, quantidade de acoes com escala conferida pelo FRE e layout do emissor | `importa` |
| 2 | Idempotente: segunda rodada nao regrava; versao nova (reapresentacao) substitui; force reprocessa | `idempotente` |
| 3 | Antes de 2020 (DFP 2015): quantidade de acoes do historico de capital do FRE, sem tesouraria | `antesDe2020` |
| 4 | Sem conferencia (REVISAR): o documento validado mais proximo decide a escala; erro na fonte fica | `vizinho` |
| 5 | F6: serie trimestral recalculada ao fim da carga — trimestre isolado, 12 meses e posicao | `serieTrimestral` |
| 6 | Grupamento entre dois balancos: a quantidade em REVISAR fecha pelo evento registrado (ate a entrega) | `grupamentoEntreBalancos` |

### IssuerImportIT (2)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/issuer/IssuerImportIT.java`

F1 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: emissores a partir dos recortes reais da CVM (Petrobras, Itau, Klabin, Taesa, HGLG11), com o PostgreSQL de verdade.

| # | Teste | Método |
|---|---|---|
| 1 | Companhias ativas em bolsa e FIIs negociados em bolsa viram emissores | `importa` |
| 2 | Rodar de novo atualiza pelo CNPJ e nunca toca o segmento curado do FII | `idempotente` |

### IssuerLinkIT (5)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/issuer/IssuerLinkIT.java`

F3 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: ligacao ativo -> emissor com os recortes reais da CVM (Petrobras, Itau, Klabin, Taesa; HGLG11 e PABY11).

| # | Teste | Método |
|---|---|---|
| 1 | Acoes: FCA, raiz do ticker e B3; o fracionario segue o lote padrao; unit com composicao | `acoes` |
| 2 | FIIs pelo ISIN, inclusive o que a CVM marca como fora de bolsa (cria o emissor) | `fiis` |
| 3 | Lista curada: confirma, cria emissor sem informe, acusa ticker de fora; MANUAL nao e sobrescrito | `listaCurada` |
| 4 | B3 aponta codigo CVM que nao e emissor: cria a companhia pelo cadastro da CVM (caso WDCN3) | `b3CriaCompanhiaDoCadastro` |
| 5 | Curadoria manual: bonus sem classe perde a composicao; unit com composicao manual; CNPJ do cadastro | `curadoriaManual` |

### HistoricalQuotesImportIT (6)

`primevest-adm-api/src/test/java/com/primevest/adm/domain/pricehistory/HistoricalQuotesImportIT.java`

F2 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: serie historica da B3 em asset_price_history, com linhas reais de PETR4, PETR4F, HGLG11 e KLBN11.

| # | Teste | Método |
|---|---|---|
| 1 | Grava os pregoes a vista e fracionario dos ativos do catalogo, com a B3 como origem | `importa` |
| 2 | Ticker antigo com o mesmo ISIN entra no ativo atual, sem passar por cima do pregao do ticker atual | `tickerAntigoPeloIsin` |
| 3 | Catalogo so com o fracionario: o pregao do lote padrao nao cai nele pelo ISIN | `fracionarioNaoRecebeLotePadrao` |
| 4 | Rodar de novo nao duplica e a serie da B3 substitui o que a BRAPI gravou no dia | `idempotenteESubstituiBrapi` |
| 5 | COTAHIST diario: um arquivo por pregao, fim de semana nao vai a B3, dia sem arquivo fica para depois | `pregaoAPregao` |
| 6 | Rotina diaria: comeca no dia seguinte ao ultimo pregao gravado pela B3 e para hoje | `rotinaDiariaContinuaDoUltimoPregao` |

## Módulo app-api (carteira — porta 8080) — 20 classes, 181 testes

### PaymentDateSourceIT (5)

`primevest-app-api/src/test/java/com/primevest/domain/dividend/PaymentDateSourceIT.java`

Precedencia no `CashEventWriter` (docs/planos/PLANO_CARGA_INICIAL.md): a data de pagamento que veio de outra fonte (relatorio de proventos da CVM) cede a data da propria fonte do evento; e o FNET substitui a B3 como fonte do provento de FII (D15).

| # | Teste | Método |
|---|---|---|
| 1 | Data da CVM e substituida pela data da B3, e a origem volta a nula | `oficialSubstituiCvm` |
| 2 | Data do levantamento (V39) tambem cede a data da B3 | `oficialSubstituiLevantamento` |
| 3 | A B3 muda a data do proprio provento: a data nova vale; 'a definir' (sem data) nao apaga a que existe | `b3MudaAPropriaData` |
| 4 | O historico longo da B3 (sem data) nao mexe na data que a B3 recente gravou | `historicoNaoMexeNaData` |
| 5 | FII: o aviso do FNET assume o provento gravado pela B3 como reserva (fonte, aviso e data) | `fnetAssumeReservaDaB3` |

### AllocationIT (6)

`primevest-app-api/src/test/java/com/primevest/functional/AllocationIT.java`

A tela Alocacao e o Dashboard — que leem o mesmo endpoint com criterios diferentes sobre a reserva de emergencia.

| # | Teste | Método |
|---|---|---|
| 1 | O Dashboard (sem excludeReservation) inclui a reserva | `dashboardIncluiReserva` |
| 2 | A tela Alocacao (excludeReservation=true) deixa so a geracao de renda, somando 100% | `telaAlocacaoExcluiReserva` |
| 3 | O total sem reserva e exatamente a geracao de renda do grafico da reserva — a amarracao da tela | `totalSemReservaBateComGeracaoDeRenda` |
| 4 | A alocacao por categoria nunca inclui a reserva e agrupa por tipo e categoria | `alocacaoDetalhada` |
| 5 | Marcar um CDB como reserva muda a alocacao sem nenhuma outra escrita | `reservaMudaAAlocacao` |
| 6 | Posicao liquidada sai da alocacao | `posicaoLiquidadaSaiDaAlocacao` |

### SignupDisabledIT (1)

`primevest-app-api/src/test/java/com/primevest/functional/SignupDisabledIT.java`

Cadastro com `primevest.auth.signup-enabled=false` (docs/planos/PLANO_CADASTRO_USUARIO.md, D16, caso C10).

| # | Teste | Método |
|---|---|---|
| 1 | Cadastro desligado: enabled=false e signup/start 403 | `desligado` |

### SignupIT (14)

`primevest-app-api/src/test/java/com/primevest/functional/SignupIT.java`

Cadastro, entrada e conta de ponta a ponta na API (docs/planos/PLANO_CADASTRO_USUARIO.md, seção 7). O e-mail é o mock
`accountMailer` do `FunctionalBase`: o código é lido dele. Códigos e contadores ficam em memória no perfil de teste.

| # | Teste | Método |
|---|---|---|
| 1 | C4: cadastro com codigo cria a conta com carteira, e-mail verificado e termos aceitos | `cadastroCompleto` |
| 2 | C8: e-mail ja cadastrado responde igual, avisa por e-mail e nenhum codigo confirma | `emailExistente` |
| 3 | C5/C7: codigo errado conta tentativas; reenvio antes de 60 s e recusado | `tentativasEReenvio` |
| 4 | C9/C16/C17/C25: sem aceite 400; CPF invalido, CPF e celular repetidos 422 | `validacoes` |
| 5 | C12/C13: refresh nao serve de acesso, acesso nao serve de refresh; a renovacao funciona | `tiposDeToken` |
| 6 | Login: senha errada 401; depois de 10 falhas o e-mail fica bloqueado (429) | `limiteDeLogin` |
| 7 | C14: esqueci a senha — codigo no e-mail, nova senha vale e as sessoes antigas caem | `esqueciASenha` |
| 8 | C21: trocar a senha devolve token novo e derruba o antigo | `trocarSenha` |
| 9 | C15: trocar o e-mail so com o codigo enviado ao novo; o antigo e avisado | `trocarEmail` |
| 10 | Perfil: nome, celular, CPF (nulo nao mexe, vazio tira) e tema | `perfil` |
| 11 | C20: desvincular o Google sem senha e recusado | `desvincularGoogleSemSenha` |
| 12 | C26: termos desatualizados — 403 TERMS_REQUIRED ate aceitar; /users/me continua liberado | `novoAceiteDosTermos` |
| 13 | C23: excluir a conta com o codigo apaga o usuario e os dados; o token deixa de valer | `excluirConta` |
| 14 | Foto de perfil: envio vira JPEG quadrado, sai com a versao no perfil, recusa invalida e remove | `fotoDePerfil` |

### PortfolioIT (13)

`primevest-app-api/src/test/java/com/primevest/functional/PortfolioIT.java`

A tela Carteira: posicao apurada das transacoes, valorizacao e o switch da reserva de emergencia.

| # | Teste | Método |
|---|---|---|
| 1 | A posicao nao e gravada: editar a transacao muda a carteira na mesma hora | `posicaoSaiDasTransacoes` |
| 2 | Com cotacao, a valorizacao e cotas x cotacao menos custo, e e regravada a cada transacao | `valorizacaoComCotacao` |
| 3 | Sem cotacao, o valor atual da renda fixa e o proprio aplicado | `rendaFixaSemCotacao` |
| 4 | O tipo aparece pela descricao, e nao pelo nome interno | `tipoPelaDescricao` |
| 5 | O resumo soma a carteira e aplica o retorno percentual | `resumo` |
| 6 | O switch da reserva aceita renda fixa e recusa acao com 422 | `switchDaReserva` |
| 7 | O switch manda o estado desejado: repetir a mesma chamada nao inverte nada | `switchEIdempotente` |
| 8 | Posicao de outro usuario responde 404 no switch da reserva | `switchEmPosicaoDeOutro` |
| 9 | Cada usuario ve so a propria carteira | `carteirasSaoIsoladas` |
| 10 | Dois CDBs do mesmo ativo aparecem como linhas distintas, cada uma com o seu id | `carteiraRepeteTicker` |
| 11 | A quantidade de cripto mantem as 8 casas | `fracaoDeCripto` |
| 12 | transactions.updated_at muda na edicao; created_at nao | `auditoriaDeDatas` |
| 13 | Sem token, a carteira responde 401 | `semToken` |

### StrategyIT (13)

`primevest-app-api/src/test/java/com/primevest/functional/StrategyIT.java`

A tela Estrategia: ideal por tipo, rebalanceamento e reserva de emergencia.

| # | Teste | Método |
|---|---|---|
| 1 | PUT e full replace: salvar sem um tipo remove aquele tipo | `fullReplace` |
| 2 | F7 — a soma precisa fechar 100%; 33,33 tres vezes passa e 90% nao | `somaDeCem` |
| 3 | F3 — o mesmo tipo duas vezes responde 422, e nao erro de banco | `tipoRepetido` |
| 4 | F8 — o DELETE da estrategia nao existe mais | `deleteRemovido` |
| 5 | Regra A do rebalanceamento: a base e o tipo mais acima do ideal, e ele fica com sugestao zero | `rebalanceamentoPorTipo` |
| 6 | Regra C — a posicao marcada como reserva sai do rebalanceamento por tipo | `reservaForaDoRebalanceamentoPorTipo` |
| 7 | Reserva por percentual: carteira 10.000 com 2.000 de reserva e ideal 30% pede 1.428,57 | `aporteDaReservaPorPercentual` |
| 8 | Reserva por valor: ideal R$ 3.000 pede 1.000; ideal 10% devolve −1.111,11 | `aporteDaReservaPorValorEExcesso` |
| 9 | A leitura da aba Estrategia Reserva cria a linha padrao; o rebalanceamento nao grava nada | `nascimentoDaLinhaDaReserva` |
| 10 | Trocar o tipo da reserva limpa o alvo do tipo anterior | `trocaDeTipoDaReserva` |
| 11 | Reserva por valor acima da carteira: o % ideal para o diff nao passa de 100 | `idealLimitadoACem` |
| 12 | Marcar um CDB como reserva muda ao mesmo tempo o rebalanceamento, a reserva e a alocacao | `umSoSwitchMudaAsTresLeituras` |
| 13 | A base da estrategia e o custo, e a da alocacao e o valor atual | `basesDiferentes` |

### CorporateEventsIT (5)

`primevest-app-api/src/test/java/com/primevest/functional/CorporateEventsIT.java`

Desdobramento, grupamento e bonificacao pela API, do jeito que a tela ve (docs/planos/PLANO_BRAPI.md, F6). O evento e gravado no catalogo; as transacoes do usuario nunca sao reescritas.

| # | Teste | Método |
|---|---|---|
| 1 | Desdobramento 1:2: dobra as cotas, mantem o custo, e a venda na quantidade nova e aceita | `desdobramentoUmParaDois_dobraCotasEMantemCusto` |
| 2 | Sem o evento, a mesma venda seria recusada por falta de saldo (regra B) | `semEventoVendaRecusada` |
| 3 | Bonificacao sem custo informado (B27): cotas novas na hora, com custo zero provisorio | `bonificacaoSemCusto_entraComCustoZeroProvisorio` |
| 4 | Grupamento 10:1 com fracao: sobra a parte inteira e a fracao sai do custo | `grupamentoComFracao` |
| 5 | Provento com data-com depois do desdobramento e pago sobre as cotas novas | `proventoDepoisDoDesdobramento` |

### ClosingIT (8)

`primevest-app-api/src/test/java/com/primevest/functional/ClosingIT.java`

Rotina de fechamento (docs/planos/PLANO_BRAPI.md, secao 3, F2 e F4): so ativos em carteira, 1 por chamada, OHLCV no historico e ultimo fechamento no preco.

| # | Teste | Método |
|---|---|---|
| 1 | So ativos em carteira, 1 por chamada: grava OHLCV dos pregoes e poe o ultimo fechamento no preco | `soCarteira` |
| 2 | V29: dia gravado pela serie historica da B3 nao e sobrescrito pela BRAPI | `serieDaB3Vence` |
| 3 | Com historico em dia, a rodada seguinte pede so 5 dias | `segundaRodadaCincoDias` |
| 4 | Ticker renomeado: so registra; o catalogo nao e trocado sozinho | `renomeNaoTroca` |
| 5 | 401/403: a rodada para no primeiro ativo, sem insistir nos outros | `tokenInvalidoPara` |
| 6 | Posicao vendida por inteiro nao entra na rodada | `posicaoZeradaFora` |
| 7 | Recuperacao: sem o ultimo fechamento, a subida roda a rodada; depois dela, nada a recuperar | `recuperacao` |
| 8 | Cripto: preco em reais pelo id da CoinGecko e fechamento do dia no historico | `cripto` |

### IndicatorsIT (9)

`primevest-app-api/src/test/java/com/primevest/functional/IndicatorsIT.java`

F9 do docs/planos/PLANO_EMPRESAS_BALANCOS.md de ponta a ponta: balancos (`company_quarters`), informe de FII (`fii_monthly`), proventos e precos no banco → endpoints da app-api. Numeros da Petrobras dos DFP 2024 e 2025 reais (lucro e PL da controladora, acoes, DVA).

| # | Teste | Método |
|---|---|---|
| 1 | PETR4 atual: DFP 2025 com o preco atual — LPA, VPA, P/L, ROE e payout; DY dos ultimos 12 meses | `petr4Atual` |
| 2 | PETR4 historico do DY: 2024 sem provento = 0 (nao nulo); 2025 = 3,21 / 30,00; media com o Atual | `petr4HistoricoDy` |
| 3 | PETR4 historico do LPA e do ROE: 2024 sem PL de inicio fica nulo com motivo | `petr4HistoricoLpaRoe` |
| 4 | PETR4F usa o preco e os proventos do PETR4 (D8) | `fracionario` |
| 5 | HGLG11: indicadores do informe de ago/2026; DY 2025 = 13,20 / 160,00 | `hglg` |
| 6 | FII que ainda nao distribuiu (APXR11): DY 0; sem informe, o resto nulo com motivo | `fundoSemDistribuicaoESemInforme` |
| 7 | Erros: ativo sem emissor 404, indicador invalido 400, indicador de acao em FII 400 | `erros` |
| 8 | Emissor: cadastro com tickers, serie trimestral da companhia, informe do FII | `emissor` |
| 9 | Quantidade do ultimo balanco em revisao: vale a do anterior, com motivo; com evento no meio, nulo | `quantidadeEmRevisao` |

### IntegrationsB3IT (6)

`primevest-app-api/src/test/java/com/primevest/functional/IntegrationsB3IT.java`

Área do Investidor da B3 de ponta a ponta na API, com o cliente HTTP mockado (`SyntheticAccountB3`). Desde 05/10/2026 a
conexão usa o CPF do perfil (docs/planos/PLANO_CADASTRO_USUARIO.md, D20).

| # | Teste | Método |
|---|---|---|
| 1 | Com b3i.enabled a B3 aparece entre as fontes, pedindo o CPF | `fontes` |
| 2 | Conectar com CPF que ainda nao autorizou: PENDING_AUTHORIZATION, com o link de autorizacao | `conectarAguardando` |
| 3 | D20: sem CPF no perfil, CPF diferente do perfil, CPF que a B3 nao localiza, CPF de outro usuario (A1) e troca de CPF com conexao viva sao recusados | `recusas` |
| 4 | Fluxo: aguardando -> autoriza -> importa as negociacoes (B1) -> segunda busca sem nada novo (C4) | `fluxoCompleto` |
| 5 | C5: o investidor revoga na B3 -> a proxima busca encerra a conexao (REVOKED) e apaga o CPF | `revogacao` |
| 6 | A rotina diaria tambem confere a conexao que aguarda autorizacao (G8) | `rotinaConfereAguardando` |

### IntegrationsConfirmationIT (9)

`primevest-app-api/src/test/java/com/primevest/functional/IntegrationsConfirmationIT.java`

Confirmacao da importacao, duplicatas e as regras E1, E2 e E4 (docs/planos/PLANO_CRIPTO_BINANCE.md, F4 e 6.10) — pela API, com a conta ficticia do `BinanceFake`: ordem 5001 (compra de 0,001998 BTC liquido), ordem 5002 (venda de 0,0002 BTC) e "Comprar Cripto" P1 (0,00025 ETH).

| # | Teste | Método |
|---|---|---|
| 1 | Confirmar grava as 3 operacoes como AUTO, pelas regras normais, e a carteira fecha com a corretora | `confirmar` |
| 2 | Depois de confirmar, a proxima busca e incremental e o que voltar aparece como ja importado | `segundaBusca` |
| 3 | Confirmar duas vezes a mesma pre-visualizacao: a segunda e recusada | `confirmarDuasVezes` |
| 4 | Nova pre-visualizacao cancela a anterior, que nao pode mais ser confirmada | `novaCancelaAnterior` |
| 5 | 6.6: compra digitada antes vira possivel duplicata; sem decisao nao confirma; REPLACE troca o manual pelo importado | `duplicataSubstituida` |
| 6 | 6.6: SKIP nao importa a operacao e o lancamento manual fica | `duplicataMantidaOuIgnorada` |
| 7 | E1 e E4: editar a importada marca editedAt; restaurar volta aos valores da corretora | `editarERestaurar` |
| 8 | E2 e E4: excluir a importada registra a exclusao; restaurar a traz de volta na hora | `excluirERestaurar` |
| 9 | Excluir um lancamento MANUAL nao registra exclusao | `excluirManual` |

### IntegrationsQueueIT (9)

`primevest-app-api/src/test/java/com/primevest/functional/IntegrationsQueueIT.java`

Fila, worker, rotina diaria e aviso de divergencia (docs/planos/PLANO_CRIPTO_BINANCE.md, F5) — com a conta ficticia do `BinanceFake`.

| # | Teste | Método |
|---|---|---|
| 1 | Pedir a sincronizacao responde na hora (202, QUEUED) sem ir a corretora; pedir de novo devolve a mesma | `pedidoEntraNaFila` |
| 2 | O worker processa a fila e para quando ela esvazia | `worker` |
| 3 | Chave recusada na busca: execucao FAILED com o motivo, conexao INVALID_CREDENTIALS | `falhaNaBusca` |
| 4 | Rotina diaria: a primeira importacao espera o usuario; depois dela, confirma sozinha | `rotinaDiaria` |
| 5 | Rotina diaria nao confirma sozinha quando ha possivel duplicata | `rotinaDiariaComDuplicata` |
| 6 | E3: depois de confirmar, aponta os ativos em que a carteira difere do saldo da corretora (BRL fora) | `divergencia` |
| 7 | F6: historico de execucoes, divergencias na lista de conexoes e origem na transacao | `dadosDaTela` |
| 8 | Execucao RUNNING perdida (API caiu no meio) vira FAILED e libera a conexao | `execucaoPerdida` |
| 9 | A lista de conexoes mostra a ultima execucao | `ultimaExecucaoNaLista` |

### IntegrationsIT (11)

`primevest-app-api/src/test/java/com/primevest/functional/IntegrationsIT.java`

Integracao com a Binance pela API, do jeito que qualquer usuario (e a tela de Integracoes, F6) vai usar — com a Binance mockada no cliente HTTP e uma conta ficticia (`BinanceFake`). docs/planos/PLANO_CRIPTO_BINANCE.md, F3.

| # | Teste | Método |
|---|---|---|
| 1 | Sem token: 401 | `semToken` |
| 2 | Conectar: confere a chave, grava cifrada e devolve so os 4 ultimos caracteres | `conectar` |
| 3 | Chave que negocia e recusada (422) e nada e gravado | `chaveQueNegocia` |
| 4 | Chave recusada pela Binance: 422 com orientacao, nada gravado | `chaveRecusada` |
| 5 | Sem aceitar o consentimento: 400 | `semConsentimento` |
| 6 | Apelido repetido para o mesmo usuario: 422; outro usuario pode usar o mesmo apelido | `apelidoRepetido` |
| 7 | Pre-visualizacao pela API: 3 novas, 2 nao importadas com motivo, 1.000,00 depositados — nada gravado | `preVisualizacao` |
| 8 | Dois usuarios: um nao lista, nao pre-visualiza nem desconecta a conexao do outro (404) | `isolamentoEntreUsuarios` |
| 9 | Chave que passou a negociar depois de conectada: a busca falha com o motivo e marca a conexao | `permissaoMudouDepois` |
| 10 | Desconectar apaga a credencial, marca REVOKED e bloqueia a pre-visualizacao | `desconectar` |
| 11 | Sem b3i.enabled a B3 nao aparece entre as fontes nem conecta | `b3Desligada` |

### PositionRefreshIT (3)

`primevest-app-api/src/test/java/com/primevest/functional/PositionRefreshIT.java`

A carteira reage ao "catalogo mudou" da adm-api (docs/planos/PLANO_ADM_API.md, 10.3): o evento entra no catalogo depois da compra, e so o recalculo da app-api o transforma em provento do usuario.

| # | Teste | Método |
|---|---|---|
| 1 | PROVENTOS: o evento novo do catalogo vira provento na posicao so quando a carteira e avisada | `proventos` |
| 2 | FECHAMENTO: preco novo so revaloriza, nao gera provento | `fechamento` |
| 3 | Rede de seguranca diaria: sem mensagem nenhuma, recalcular tudo acerta a carteira | `recalculaTudo` |

### CeilingPriceIT (6)

`primevest-app-api/src/test/java/com/primevest/functional/CeilingPriceIT.java`

Preço Teto de ponta a ponta (docs/planos/PLANO_PRECO_TETO.md, 8.1): padrões do catálogo, cálculo sem gravar, gravação com as validações, origem de cada entrada, lista com as três comparações e o dono. Balanços sintéticos da Petrobras.

| # | Teste | Método |
|---|---|---|
| 1 | Padrões: 4 trimestres do catálogo, ações sem tesouraria, médias de 3 exercícios, Selic meta, 3% e 15% | `padroesDoCatalogo` |
| 2 | Cenário 12 pela API: a linha VALE3 da planilha no formulário dá o mesmo teto e ideal | `planilhaPelaApi` |
| 3 | Salvar com os padrões: origens PADRAO, detalhe devolve o gravado, lista com as comparações e preço médio | `salvaComPadroes` |
| 4 | Campo vazio, d ≤ G e alertas sem confirmação: não salva (422) | `validacoesAoSalvar` |
| 5 | Lucro em dólar: PTAX do fim do trimestre por padrão; câmbio informado vale | `lucroEmDolar` |
| 6 | Só ações; o cálculo de um usuário não aparece para outro | `soAcoesEDono` |

### DividendsToReceiveIT (7)

`primevest-app-api/src/test/java/com/primevest/functional/DividendsToReceiveIT.java`

Proventos recebidos x a receber, de ponta a ponta. Recebido = pagamento ate hoje (fuso da B3, contando o proprio dia); sem data de pagamento, conforme a origem (Dividend.isReceived). A Carteira e o Dashboard so somam o recebido.

| # | Teste | Método |
|---|---|---|
| 1 | PETR4 em 24/09/2026: JCP pago em 21/09 e recebido; os de 23/11 e 21/12 sao a receber | `situacaoPorPagamento` |
| 2 | D0: o provento pago hoje ja conta como recebido | `pagoHojeERecebido` |
| 3 | Virada do dia: o a receber passa a recebido sozinho, sem recalculo | `viradaDoDia` |
| 4 | Carteira e Dashboard ignoram o que ainda sera pago | `carteiraIgnoraAReceber` |
| 5 | Posicao so com provento futuro (o caso TIMS3) mostra zero na Carteira | `soProventoFuturo` |
| 6 | Resumo: recebido e a receber separados, e os dois somam tudo | `resumoSeparado` |
| 7 | Sem data de pagamento: recebido se veio do historico longo; a receber se veio da fonte recente | `semDataConformeAOrigem` |

### DividendReconciliationIT (5)

`primevest-app-api/src/test/java/com/primevest/functional/DividendReconciliationIT.java`

Conferencia dos proventos que a Area do Investidor da B3 informa pela API com os proventos da carteira (docs/planos/PLANO_INTEGRACAO_B3.md, 6.8 e C3). So le — nada vai para o catalogo nem para a carteira.

| # | Teste | Método |
|---|---|---|
| 1 | Ja existe: o provento da carteira tem o mesmo pagamento, cotas e liquido | `jaExiste` |
| 2 | Sem data: o provento existe, mas o evento do catalogo nao tem pagamento — nada e gravado | `semData` |
| 3 | Sem evento: o catalogo nao tem o provento — a conferencia nao cria evento | `semEvento` |
| 4 | Tres proventos iguais no mesmo dia (ISAE4) que ja existem: tres conferidos | `tresIguaisJaExistentes` |
| 5 | Nao conferido: ativo fora do catalogo e tipo que a B3 informa sem correspondencia | `naoConferido` |

### DividendsIT (6)

`primevest-app-api/src/test/java/com/primevest/functional/DividendsIT.java`

Proventos pela B3 de ponta a ponta (docs/planos/PLANO_BRAPI.md, F5): a rotina grava os eventos do papel no catalogo e cada posicao recebe a sua conta.

| # | Teste | Método |
|---|---|---|
| 1 | A rodada grava os eventos da B3 e os proventos da posicao batem com a B3, so no ISIN do papel | `proventosBatemComAB3` |
| 2 | Compra retroativa so recalcula: os eventos ja estao no catalogo, nenhuma chamada nova a B3 | `compraRetroativaSoRecalcula` |
| 3 | B3 fora do ar: a rodada registra a falha e nao apaga nada | `b3ForaDoAr` |
| 4 | Amortizacao (B13): entra como provento e reduz o custo da posicao a partir do pagamento | `amortizacaoReduzCusto` |
| 5 | Rotulo desconhecido da B3: o evento fica no catalogo, mas nao gera provento | `rotuloDesconhecido` |
| 6 | FII que ainda nao distribuiu (APXR11, resposta real da B3 sem proventos): rodada sem falha, carteira com zero | `fundoSemDistribuicao` |

### DividendTaxIT (15)

`primevest-app-api/src/test/java/com/primevest/functional/DividendTaxIT.java`

IR retido na fonte sobre proventos, de ponta a ponta (docs/planos/PLANO_IR_PROVENTOS.md, 5.2): uma regra por teste.

| # | Teste | Método |
|---|---|---|
| 1 | F1 — JCP creditado em 2025 e pago em 20/02/2026 retem 15%: 20,05 | `f1JcpCreditadoEm2025` |
| 2 | F2 — JCP de 20/03/2026: bruto truncado 8,75 (nao 8,76) e liquido 7,44 | `f2BrutoTruncado` |
| 3 | F3 — Dividendo de 20/03/2026 e isento: 14,82 | `f3DividendoIsento` |
| 4 | F4 — JCP creditado em 2026 (pago em 22/06/2026) retem 17,5%: 12,92 | `f4JcpCreditadoEm2026` |
| 5 | F5 — Rendimento de acao (atualizacao pela Selic) de 22/06/2026 retem 22,5%: 0,79 | `f5RendimentoDeAcao` |
| 6 | F6 — BBAS3, 125 cotas, JCP de 11/06/2026 em duas parcelas: 8,41 + 6,16 = 14,57 | `f6Bbas3` |
| 7 | F7 — Rendimento de FII e isento: TOTAL igual ao bruto | `f7RendimentoDeFii` |
| 8 | F8 — Amortizacao de FII e isenta e continua reduzindo o custo | `f8Amortizacao` |
| 9 | F9 — Restituicao de capital de acao e isenta | `f9RestituicaoDeCapital` |
| 10 | F10 — ETF nao tem regra: aliquota e liquido nulos, e a carteira soma o bruto | `f10SemRegra` |
| 11 | F11 — Proventos novos (rotina e compra retroativa) nascem com o IR calculado | `f11ProventosNovos` |
| 12 | F12 — Lei nova por migration: so os JCP creditados na nova vigencia mudam | `f12LeiNova` |
| 13 | F13 — Verificador na subida: proventos sem IR voltam preenchidos, com os mesmos valores | `f13Verificador` |
| 14 | F14 — Carteira e Dashboard somam o provento liquido (so o ja recebido) | `f14CarteiraEmLiquido` |
| 15 | F15 — Resumo da tela de proventos: totais e byType em liquido; bruto e IR a parte | `f15ResumoDaTela` |

### IssuerPageIT (7)

`primevest-app-api/src/test/java/com/primevest/functional/IssuerPageIT.java`

Backend da tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md, B1–B6): historico de todos os indicadores numa chamada, emissor com logo/site/situacao, busca so com emissor, cotacao historica ajustada e o issuerId nas telas que linkam.

| # | Teste | Método |
|---|---|---|
| 1 | B4: todos os historicos da PETR4 numa chamada, na ordem do enum, iguais aos individuais | `historicoEmLote` |
| 2 | B1/B2: emissor com situacao, site e hasLogo por ticker | `emissor` |
| 3 | B3: busca com withIssuer so traz ativos ligados a emissor | `buscaComEmissor` |
| 4 | B5: 1D = fechamento anterior -> ultimo; MAX desde o primeiro pregao; periodo especifico; resumo | `cotacao` |
| 5 | B5/D9: SBSP3 ajustada pelo desdobramento — sem degrau de -80% | `cotacaoAjustada` |
| 6 | B5: erros — ticker inexistente 404, periodo invalido 400, datas invertidas 400; sem preco = serie vazia | `cotacaoErros` |
| 7 | B6: issuerId na carteira e no preco teto; nulo no ativo sem emissor | `issuerIdNasTelas` |

### TransactionsIT (30)

`primevest-app-api/src/test/java/com/primevest/functional/TransactionsIT.java`

Transacoes de ponta a ponta: HTTP, regras e banco.

| # | Grupo | Teste | Método |
|---|---|---|---|
| 1 | Renda variavel | Compra cria a posicao e aparece na listagem e na busca por id | `compraCriaPosicao` |
| 2 | Renda variavel | Preco medio ponderado: 100x10, 100x20 e venda de 100 deixam PM 15 e custo 1.500 | `precoMedioPonderado` |
| 3 | Renda variavel | Ticker fora do catalogo responde 422 e nao cria ativo | `tickerInexistente` |
| 4 | Renda variavel | Regra A — venda de ativo fora da carteira responde 422 e nao grava nada | `vendaSemPosicao` |
| 5 | Renda variavel | Vender tudo liquida a posicao; uma nova compra a reabre | `liquidacaoEReabertura` |
| 6 | Renda variavel | Usuario B nao le, nao edita e nao exclui transacao de A — 404, nunca 403 | `escopoPorUsuario` |
| 7 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 1 — a segunda venda falha por faltarem 2 cotas em 11/09 | `exemplo1` |
| 8 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 1a — uma compra de exatamente 2 cotas antes da data libera a venda | `exemplo1a_compraDeDuasCotas` |
| 9 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 1b — editar a compra original para 12 cotas libera a venda | `exemplo1b_editarACompra` |
| 10 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 1c — reduzir a primeira venda para 6 cotas tambem libera a segunda | `exemplo1c_editarAVenda` |
| 11 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 1d — a compra depois da venda nao resolve: a regra olha a data | `exemplo1d_compraDepoisDaVendaNaoResolve` |
| 12 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 2 — reduzir a compra para 5 cotas falha por faltarem 3 em 04/09, e nada muda | `exemplo2` |
| 13 | Exemplos 1 a 3 do escopo — saldo por cota em qualquer data | Exemplo 3 — excluir a compra falha por faltarem 8 cotas em 04/09, e a compra continua la | `exemplo3` |
| 14 | Renda fixa | Dois CDBs de emissores diferentes sao duas linhas, distinguidas por emissor e vencimento | `doisCdbsDuasLinhas` |
| 15 | Renda fixa | A taxa do titulo guarda as 3 casas digitadas (IPCA + 6,125%) | `taxaComTresCasas` |
| 16 | Renda fixa | Renda fixa com quantidade e recusada: ela e lancada pelo valor total | `rendaFixaComQuantidade` |
| 17 | Renda fixa | Venda de renda fixa sem apontar o titulo e recusada | `vendaSemTitulo` |
| 18 | Renda fixa | Resgate que alcanca o saldo corrige a valorizacao e deixa o centavo da regra F | `resgateParcialDeixaCentavo` |
| 19 | Renda fixa | Liquidacao total zera a posicao e grava o ganho realizado | `liquidacaoTotal` |
| 20 | Renda fixa | Regra G — liquidacao total abaixo do aplicado e recusada | `liquidacaoAbaixoDoAplicado` |
| 21 | Renda fixa | Editar so o valor nao apaga emissor, vencimento nem taxa do titulo | `edicaoNaoApagaOTitulo` |
| 22 | Renda fixa | Titulo de outro usuario nao pode receber lancamento | `tituloDeOutroUsuario` |
| 23 | Tesouro Direto — FIFO por titulo | Regra H — dois titulos do mesmo ativo comprados no mesmo dia sao recusados | `regraH` |
| 24 | Tesouro Direto — FIFO por titulo | Exemplo 4 — a venda de 5 cotas consome 3 do titulo de 01/09 (liquidado) e 2 do de 15/09 | `exemplo4` |
| 25 | Tesouro Direto — FIFO por titulo | Exemplo 5 — a venda de 14/09 falha: o titulo de 15/09 foi comprado depois e nao conta | `exemplo5` |
| 26 | Tesouro Direto — FIFO por titulo | Exemplo 6 — a compra retroativa de 31/08 refaz a reparticao: 4 cotas dela e 1 do titulo de 01/09 | `exemplo6` |
| 27 | Regra K — posicao sem transacao deixa de existir | Regra K — acao: a posicao fica enquanto houver transacao e sai com a ultima | `regraK_acao` |
| 28 | Regra K — posicao sem transacao deixa de existir | Regra K — renda fixa liquidada com ganho: excluidas venda e compra, posicao, titulo e valorizacao somem | `regraK_rendaFixaLiquidada` |
| 29 | Regra K — posicao sem transacao deixa de existir | Regra K — posicao que teve provento tambem sai, levando o provento junto | `regraK_comProvento` |
| 30 | Regra K — posicao sem transacao deixa de existir | Regra K — Tesouro: excluir a unica compra apaga o titulo e libera a data para a regra H | `regraK_tesouro` |

## Fora desta lista

Testes unitários e de repositório (`*Test`, Surefire, `mvn clean test`): regras puras, serviços com mock, controllers e consultas de repositório.

| Módulo | Classes | Testes |
|---|---|---|
| adm-api | 16 | 79 |
| app-api | 16 | 167 |
| core | 20 | 138 |
| integration | 9 | 103 |
