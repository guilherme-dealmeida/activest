# PrimeVest — Plano do Cadastro de Usuário

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **implementado em 05/10/2026** (U1–U7) — andamento na seção 9. Criado em 04/10/2026; todas as decisões (D1–D20) tomadas em 04/10/2026 (seção 8). Execução autorizada em 05/10/2026.

---

## 0. Resumo

Passo

Situação hoje

O que falta, em uma linha

1. Login com Google

Backend e front prontos, **sem Client ID criado** e nunca testados

Criar o Client ID (apêndice A), validar de ponta a ponta e corrigir 3 defeitos (sobrescrita do nome, e-mail sem normalizar, wrapper vestigial)

2. Cadastro novo

Só existe `POST /auth/register`, **sem tela, sem verificação** e com contrato divergente do front

Tela "Criar conta" em 2 passos (com CPF opcional) + código por e-mail + aceite dos termos; "Esqueci minha senha"

3. Edição do usuário

Tela **Configurações é 100% maquete** (valores fixos, salvar falso)

Ligar a tela à API: nome, e-mail (com código), celular, CPF, senha, vínculo Google, tema, exclusão da conta

Achado transversal de segurança (entra na etapa U1): **o refresh token é aceito como token de acesso**. Os dois são assinados com a mesma chave, e nada os distingue — o `JwtAuthFilter` aceita qualquer JWT válido, então o refresh (7 dias) funciona como acesso. O inverso também: `/auth/refresh` aceita um token de acesso.

---

## 1. O que existe (conferido no código em 04/10)

### Backend (app-api)

Peça

Estado

`users` (V1)

`id, name, email (único), password_hash (nulo p/ Google), google_id (único), avatar_url, is_admin, created_at, updated_at`. **Sem telefone, sem CPF, sem marca de verificação**

`POST /auth/login`

E-mail/senha via `AuthenticationManager`. Sem limite de tentativas

`POST /auth/register`

Cria usuário + `createDefaults` (carteira e reserva). **Devolve 200 vazio**, mas o front (`authApi.register`) tipa como `TokenResponse`. Sem verificação de e-mail. Mensagem em inglês ("Email already registered")

`POST /auth/google`

`GoogleTokenVerifier` correto (assinatura, `aud`, `iss`, expiração, `email_verified`). Três caminhos: `google_id` → e-mail (vincula) → cria

`POST /auth/refresh`

Existe, mas o front **não usa**: com 24 h o token vence e o usuário cai no login

`GET/PUT /users/me`

`UserUpdateDTO` só tem `name` e `avatarUrl`; `UserResponseDTO` não diz se tem senha, se tem Google, nem é admin

Mensagens

`UserService` e `AuthService` em inglês ("User not found", "Invalid refresh token"); validação do Bean Validation sai no texto padrão ("size must be between 8 and 2147483647" — visto na pilha de teste)

E-mail

`findByEmail` é **sensível a maiúsculas**: `Guilherme@…` e `guilherme@…` viram duas contas

CPF

Validação já existe em `InvestorDocument.cpfValido` (módulo integration) e a cifra das credenciais (`PRIMEVEST_CREDENTIALS_KEY`) — reaproveitáveis

Dependências

Sem `spring-boot-starter-mail`. Redis disponível (serve para os códigos)

### Front

Peça

Estado

`LoginPage`

E-mail/senha + botão Google (some sem `VITE_GOOGLE_CLIENT_ID`). **Sem "Criar conta", sem "Esqueci a senha"**

`useGoogleSignIn`

Google Identity Services direto, ID token, sem client secret. Correto

`main.tsx`

`GoogleOAuthProvider` de `@react-oauth/google` com `"YOUR_GOOGLE_CLIENT_ID_HERE"` — vestigial (já anotado no `Claude.md`)

`SettingsPage`

**Maquete**: nome cai em "Guilherme Henrique", telefone "(41) 99999-9999" e CPF "123.456.789-00" fixos; "Salvar" só troca o texto do botão; não chama `PUT /users/me`; e-mail editável na tela mas ignorado pela API

Preferências

Tema e menu recolhido em `localStorage` (por navegador, não por usuário)

### Ambiente

-   `GOOGLE_CLIENT_ID` **vazio** no `.env` e no `.env.teste` (correção: a primeira versão deste plano dizia "preenchido" no `.env` — a conferência tinha pegado o `r` do fim de linha do Windows). Por isso o botão do Google não aparece e o `/auth/google` responde 422 "não está configurado".
-   No momento só a pilha `primevest-teste` está no ar; a principal (`primevest-*`) está parada.

---

## 2. Passo 1 — Login direto com conta Google

O código está pronto; o que falta é a credencial no Google, validação e três correções.

### 2.1 Configuração (Guilherme) — passo a passo no **apêndice A**

Resumo do que precisa sair do console: credencial **Aplicativo da Web**; origens `http://localhost` e `http://localhost:5173`; nenhuma URI de redirecionamento; público **Externo** em modo **Teste** com as contas de teste cadastradas. A pilha de teste fica sem Google (D2).

### 2.2 Correções no código

#

Correção

Detalhe

G5

**Nome e foto preenchidos só na criação** (D5)

Hoje `refreshProfile` grava nome e foto do Google em todo login: o que o usuário editar em Configurações some no próximo login. Passa a só criar; nos logins seguintes não mexe

G6

**Normalizar e-mail** (minúsculas, sem espaços) em cadastro, login, Google e edição

Evita conta duplicada por maiúscula. Migration confere se já há duplicata antes de criar índice único em `lower(email)`

G7

Tirar `GoogleOAuthProvider` do `main.tsx` e a dependência `@react-oauth/google`

Código morto com client id falso

G8

Usuário criado pelo Google nasce com **e-mail verificado** (`email_verified_at`)

O Google já garantiu (`email_verified`)

G9

Botão Google também na tela "Criar conta", com o aviso de aceite dos termos (D17)

Mesmo endpoint: quem não tem conta é criado

G10

Com `SIGNUP_ENABLED=false`, o Google **não cria** conta nova (só entra/vincula)

Senão a chave do D16 teria uma porta lateral

### 2.3 Validação de ponta a ponta (Claude, última etapa)

1.  Subir a pilha principal com o `GOOGLE_CLIENT_ID` e conferir que o botão aparece.
2.  Entrar com `guilhermeunatec@gmail.com` pelo Google → **vincula** `google_id` à conta semeada (caminho 2), sem criar outra; carteira e transações continuam as mesmas.
3.  Sair e entrar de novo pelo Google → caminho 1.
4.  Entrar com outra conta Google (de teste) → cria usuário com carteira "Minha Carteira", reserva padrão e aceite dos termos registrado.
5.  Conferir que o login por senha do usuário semeado e o `admin.mjs` (que usa `PRIMEVEST_SENHA`) continuam funcionando.

---

## 3. Passo 2 — Criação de conta a partir da tela de login

### 3.1 Fluxo na tela

```
/login ── "Criar conta" ──► /cadastro            (link some com SIGNUP_ENABLED=false)
   Passo 1 — Dados: nome, e-mail, celular (opcional), CPF (opcional), senha + confirmação,
             [ ] Li e aceito os Termos de Uso e a Política de Privacidade (obrigatório)
                                                             ──► POST /auth/signup/start
   Passo 2 — Código enviado ao e-mail: 6 dígitos, "reenviar" com contagem regressiva, "corrigir dados"
                                                             ──► POST /auth/signup/confirm
   Conta criada ──► já entra logado (token) ──► /dashboard
```

Ou "Continuar com Google" (G9), que dispensa o código. Sob o botão, a frase "Ao continuar com o Google você aceita os Termos de Uso e a Política de Privacidade" (com os links); o aceite é registrado na criação da conta (D17). O CPF de quem entra pelo Google fica para Configurações (o Google não informa CPF).

CPF **entra no cadastro, opcional** (D18): validado e conferido como único já no `start` (CPF repetido recusa o cadastro com 422 — aqui não há anti-enumeração possível sem esconder o erro, e CPF não é o login). Guardado no cadastro pendente do Redis já cifrado, nunca em claro.

### 3.2 Regras do código de verificação

Regra

Valor proposto

Formato

6 dígitos numéricos, gerados com `SecureRandom`

Guarda

**Redis**, com TTL — só o hash (HMAC-SHA256 com chave do `.env`), nunca o código em claro

Validade

10 minutos

Tentativas

5 erros invalidam o código (precisa pedir outro)

Reenvio

60 s entre envios; no máximo 5 envios por hora por e-mail

Limite por IP

início de cadastro, "esqueci a senha" e login limitados por IP (contador no Redis)

Enumeração (D10)

o `start` responde igual para e-mail já cadastrado; o aviso "você já tem conta" vai **para o e-mail**, não para a tela

Uso

um código serve a um propósito (`SIGNUP`, `RESET_PASSWORD`, `CHANGE_EMAIL`, `DELETE_ACCOUNT`) e é apagado ao ser aceito

### 3.3 Cadastro pendente: onde fica até o código ser confirmado — **D3: Redis**

Decidido em 04/10/2026: opção (a). A comparação fica registrada:

(a) Redis — escolhida

(b) `users` com `email_verified_at` nulo

Como

`start` guarda `{nome, e-mail, celular, hash da senha, aceite}` no Redis com o TTL do código; `confirm` cria o usuário, chama `createDefaults` e devolve o token

`start` já cria o usuário; login recusado ("confirme seu e-mail") até o código

Lixo

nenhum: quem desiste some sozinho em 10 min

contas nunca confirmadas ficam em `users`, com carteira; precisa rotina de limpeza

Princípio do projeto

sem tabela auxiliar nem coluna de estado de processo

`email_verified_at` vira, na prática, estado do cadastro

E-mail "preso"

não acontece

alguém pode registrar o e-mail de outra pessoa e segurá-lo até a limpeza

Contra

se o Redis reiniciar durante os 10 min, a pessoa recomeça o cadastro

—

`email_verified_at` existe nos dois casos (G8 e troca de e-mail); muda só se ele também é a trava do cadastro.

### 3.4 Envio por e-mail

Mesmo desenho das integrações — uma interface e a implementação:

```
VerificationSender            (interface: send(destino, código, propósito))
└── EmailVerificationSender    SMTP (spring-boot-starter-mail)
```

Com D8, só o e-mail existe; a interface fica para não prender o serviço de código ao SMTP.

-   **DEV (D7)**: **Mailpit** no compose — recebe todo e-mail enviado e mostra numa página (`http://localhost:8025`); não envia nada para fora. Também tem API, que o Playwright usa para ler o código.
-   **SMTP real** configurável no `.env` (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`); sem configuração, aponta para o Mailpit.
-   E-mails em português, HTML simples no visual do PrimeVest (fundo escuro, dourado), com versão texto: código de cadastro, "você já tem conta", redefinição de senha, troca de e-mail, exclusão de conta, e o aviso "sua senha foi alterada" (sem código, só aviso).

### 3.5 "Esqueci minha senha" (D4 — entra)

`/login` → "Esqueci a senha" → e-mail → código → nova senha. Ao redefinir: `password_changed_at` atualizado, outras sessões caem (D15) e o aviso de senha alterada é enviado.

### 3.6 Termos de uso e política de privacidade (D9 — com texto fictício)

-   Duas páginas públicas no front: `/termos` e `/privacidade`, com texto **fictício e marcado como provisório** ("Texto provisório — será substituído antes da produção"), em `src/content/` para trocar sem mexer em tela.
-   Versão do texto numa constante (`TERMS_VERSION = "2026-10-provisorio"`) no backend.
-   `users.terms_accepted_at` + `users.terms_version`. O cadastro exige o aceite; o usuário semeado recebe o aceite na V41.
-   Quando a versão mudar, quem aceitou a anterior vê um **modal de novo aceite** no próximo login (D19): enquanto não aceitar, a app-api responde 403 "aceite os novos termos" a tudo, menos `/users/me`, `/users/me/terms` e o logout.
-   Google (D17): aceite pela frase sob o botão, registrado na criação.

### 3.7 Endpoints novos (em `/api/v1/auth/**`, públicos)

Método

Rota

Corpo → resposta

GET

`/auth/signup/enabled`

→ `{enabled, termsVersion}` (a tela decide se mostra "Criar conta")

POST

`/auth/signup/start`

`{name, email, phone?, cpf?, password, acceptTerms}` → `202 {signupId, emailMascarado, reenviarEm}`

POST

`/auth/signup/resend`

`{signupId}` → `202`

POST

`/auth/signup/confirm`

`{signupId, code}` → `TokenResponse`

POST

`/auth/password/forgot`

`{email}` → `202` (sempre)

POST

`/auth/password/reset`

`{requestId, code, newPassword}` → `204`

`POST /auth/register` atual **sai** (D11). Com `SIGNUP_ENABLED=false` (D16), os `signup/*` respondem 403 com mensagem clara; no `.env` do DEV local fica `true`, no `.env.example` `false`.

---

## 4. Passo 3 — Edição do usuário (Configurações)

### 4.1 O que falta

#

Item

Hoje

Proposta

E1

Tela ligada à API

maquete

carrega `GET /users/me`, salva em `PUT /users/me`, toast de sucesso/erro, atualiza o `AuthContext` (nome no menu)

E2

Nome

só na API

editável

E3

E-mail

campo ignorado

troca com **código enviado ao e-mail novo**; aviso ao e-mail antigo. E-mail é o login: sem código não troca

E4

Celular

maquete

opcional, **único**, formato E.164 (`+5541…`), máscara na tela; **sem verificação** (D6 + D8) — é contato, não login

E5

CPF

maquete (com CNPJ)

**fica no usuário** (D12): só CPF (sai o CNPJ), opcional e único (D18), validado, **cifrado** no banco, exibido mascarado (`***.456.789-**`). Detalhes em 4.2

E6

Senha

não existe

"Alterar senha" (pede a atual); "Definir senha" para quem só tem Google

E7

Google

não aparece

"Conta Google vinculada" / "Vincular conta Google"; desvincular só se houver senha (senão a pessoa fica sem como entrar)

E8

Foto

`avatarUrl` texto livre

foto do Google (da criação) ou iniciais; **sem upload** neste escopo — `avatarUrl` sai do `UserUpdateDTO`

E9

Tema

`localStorage`

**no servidor** (D13): `users.theme`; o `localStorage` vira só cache para a tela de login e o primeiro desenho. Menu recolhido continua no navegador

E10

Excluir conta

não existe

**definitiva**, com **código por e-mail** (D14). As FKs de usuário já são `ON DELETE CASCADE`; conferir na implementação as tabelas de integração (credenciais cifradas) e o que a adm-api referencia

E11

Sessões

trocar senha não derruba outras sessões

`password_changed_at`: token emitido antes dela é recusado (D15)

E12

Termos

—

mostrar versão e data do aceite, com link para os textos

### 4.2 CPF no usuário (D12)

-   **Guarda**: cifrado com a mesma cifra das credenciais de integração (`PRIMEVEST_CREDENTIALS_KEY`, com versão de chave) em `users.cpf_encrypted`, mais um `users.cpf_hash` (HMAC) para a unicidade e para procurar sem decifrar.
-   **Validação**: `InvestorDocument.cpfValido` (dígitos verificadores), movido para o core se necessário.
-   **Exibição**: a API devolve só o mascarado (`***.456.789-**`); o número inteiro só é decifrado para uso interno.
-   **Integração B3 (D20)**: a conexão usa o CPF do perfil — campo pré-preenchido e travado. Sem CPF no perfil, a tela de conexão pede para cadastrá-lo em Configurações. A armadilha A1 (CPF de outra pessoa, PENDÊNCIAS 16.9) passa a ser coberta pela unicidade do CPF entre usuários. Trocar o CPF do perfil com conexão B3 viva: recusado (422) até encerrar a conexão — senão a conexão ficaria apontando para outro CPF.
-   **Perda da chave**: perder `PRIMEVEST_CREDENTIALS_KEY` passa a perder também os CPFs (o usuário recadastra) — mesmo comportamento já documentado para as credenciais.

### 4.3 Layout proposto da tela

Uma página, quatro cartões, no padrão visual atual:

1.  **Perfil** — iniciais/foto, nome, e-mail (com "verificado" e "Alterar", que abre o modal de código), celular, CPF.
2.  **Acesso** — senha (alterar/definir), Google (vinculado/vincular/desvincular), termos aceitos.
3.  **Preferências** — tema.
4.  **Zona de perigo** — excluir conta.

### 4.4 API

Método

Rota

O quê

GET

`/users/me`

+ `phone`, `cpfMasked`, `emailVerified`, `hasPassword`, `googleLinked`, `admin`, `theme`, `termsVersion`, `termsAcceptedAt`, `createdAt`

PUT

`/users/me`

`{name, phone?, cpf?, theme}`

POST

`/users/me/email` · `/users/me/email/confirm`

troca de e-mail com código

PUT

`/users/me/password`

`{currentPassword?, newPassword}` — atual obrigatória se já houver senha

POST · DELETE

`/users/me/google`

vincular (ID token) · desvincular

POST

`/users/me/terms`

aceitar a versão atual (D19)

POST · DELETE

`/users/me/delete-request` · `/users/me`

pede o código · exclui com `{code}`

---

## 5. Modelo de dados — migration V41

```sql
-- e-mail sem diferença de maiúsculas (G6); antes, conferir duplicatas
UPDATE users SET email = lower(trim(email));
ALTER TABLE users
    ADD COLUMN phone               VARCHAR(20),     -- E.164, opcional (D6)
    ADD COLUMN cpf_encrypted       TEXT,            -- D12, cifra das credenciais
    ADD COLUMN cpf_hash            VARCHAR(64),     -- HMAC, unicidade
    ADD COLUMN email_verified_at   TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP,       -- D15
    ADD COLUMN terms_accepted_at   TIMESTAMP,       -- D9
    ADD COLUMN terms_version       VARCHAR(30),
    ADD COLUMN theme               VARCHAR(10) NOT NULL DEFAULT 'DARK'
        CONSTRAINT ck_users_theme CHECK (theme IN ('DARK','LIGHT'));   -- D13
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uk_users_phone ON users (phone)    WHERE phone    IS NOT NULL;
CREATE UNIQUE INDEX uk_users_cpf   ON users (cpf_hash) WHERE cpf_hash IS NOT NULL;   -- D18
-- usuário semeado: e-mail verificado e aceite da versão provisória
UPDATE users SET email_verified_at = NOW(), terms_accepted_at = NOW(), terms_version = '2026-10-provisorio'
 WHERE email = 'guilhermeunatec@gmail.com';
```

Sai da proposta anterior: `phone_verified_at` (sem SMS/WhatsApp, D8). Códigos e cadastros pendentes ficam no Redis (D3) — nenhuma tabela auxiliar.

---

## 6. Etapas

Etapa

Conteúdo

Depende de

**U1** Base de auth

refresh ≠ acesso (claim `typ`; filtro só aceita `access`, `/refresh` só `refresh`); front renova o token no 401 uma vez antes de mandar para `/login`; `password_changed_at` no filtro (D15); e-mail normalizado (G6); mensagens em português (auth, user e Bean Validation); limite de tentativas no login; V41

—

**U2** Google

G5, G7, G8, G10; validação 2.3

U1, apêndice A feito

**U3** E-mail e código

`VerificationSender` + SMTP; Mailpit no compose; serviço de código no Redis (3.2); modelos de e-mail

U1

**U4** Cadastro

endpoints 3.7, tela `/cadastro` em 2 passos, link no `/login`, `SIGNUP_ENABLED`, páginas `/termos` e `/privacidade`, botão Google (G9); remove `/auth/register`

U3

**U5** Esqueci a senha

endpoints e tela (3.5)

U3

**U6** Configurações

seção 4 inteira (inclui CPF cifrado, tema no servidor, exclusão, novo aceite dos termos) e a conexão B3 passando a usar o CPF do perfil (D20)

U3

**U7** Documentação

`Claude.md` (Autenticação, Endpoints, Telas, V9≠V22), `USE_CASES.md` (UC01, UC11), `.env.example`, `TESTES_FUNCIONAIS.md`, `PENDENCIAS.md` (9.4 e 16.9)

todas

A antiga U7 (SMS e WhatsApp) saiu com D8.

Testes em cada etapa: unitários do serviço de código (expiração, tentativas, reenvio), testes de controller (status e mensagens em português) e IT com Testcontainers para cadastro → confirmação → login, esqueci a senha e exclusão. Os casos de conferência no navegador (caminho 3) estão na seção 7.

## Quem faz

Item

Claude

Guilherme

Código, migration, testes, compose (Mailpit), textos provisórios, documentação

✔

Client ID no Google Cloud (apêndice A) e `GOOGLE_CLIENT_ID` no `.env`

✔

SMTP real, se quiser envio de verdade além do Mailpit

✔

Texto definitivo de termos e privacidade (antes da produção)

✔

Validação final (compilar, testes, subir, conferir no navegador)

✔

---

## 7. Casos de conferência

#

Caso

Esperado

C1

Google com a conta semeada

vincula, mesma carteira

C2

Google com conta nova

usuário + carteira + reserva; e-mail verificado; aceite registrado

C3

Editar nome e entrar de novo pelo Google

nome editado permanece (G5)

C4

Cadastro por e-mail, código certo

conta criada e logada; código chega no Mailpit

C5

Código errado 5 vezes

código invalidado, pede reenvio

C6

Código depois de 10 min

recusado como expirado

C7

Reenviar antes de 60 s

recusado com o tempo restante

C8

Cadastro com e-mail já existente

tela igual à de sucesso; e-mail avisa "você já tem conta"

C9

Cadastro sem marcar o aceite

botão desabilitado; API recusa (400)

C10

`SIGNUP_ENABLED=false`

"Criar conta" some; `signup/*` 403; Google não cria conta nova

C11

`GUILHERME@…` no login

entra na mesma conta

C12

Refresh token no header `Authorization`

401

C13

Token vencido com refresh válido

front renova sem sair da tela

C14

Esqueci a senha

código no e-mail, nova senha vale, sessões antigas caem

C15

Trocar e-mail

só troca após código no e-mail novo; e-mail antigo avisado

C16

Celular ou CPF já usado por outro usuário

422 com mensagem clara

C17

CPF inválido

422 "Informe um CPF válido." (regra do serviço, como os outros CPFs)

C18

CPF no banco

só cifrado; a API devolve mascarado

C19

Usuário só-Google define senha

passa a entrar por senha também

C20

Desvincular Google sem senha

recusado (422)

C21

Trocar senha

outra sessão aberta cai no próximo pedido

C22

Tema trocado num navegador

outro navegador abre no mesmo tema após o login

C23

Excluir conta com código

usuário e dados somem; login seguinte recusado

C24

`admin.mjs bootstrap`

continua entrando com `PRIMEVEST_SENHA`

C25

Cadastro com CPF de outro usuário

422 no passo 1

C26

Versão dos termos trocada

próximo login abre o modal; sem aceitar, as telas recebem 403

C27

Conectar B3

CPF do perfil pré-preenchido e travado; sem CPF, pede para cadastrar

C28

Trocar CPF com conexão B3 viva

recusado (422)

---

## 8. Decisões

### Tomadas em 04/10/2026

#

Decisão

D1

Client ID **não existe**: Guilherme cria seguindo o apêndice A (sem custo — ver nota no apêndice)

D2

Pilha `primevest-teste` sem login Google; o Playwright entra por e-mail/senha

D3

Cadastro pendente no **Redis**: o usuário só é criado depois do código confirmado

D4

"Esqueci minha senha" entra

D5

Google preenche nome/foto **só na criação**

D6

Celular **opcional e único** (login continua por e-mail)

D7

E-mail no DEV pelo Mailpit; SMTP real configurável no `.env`

D8

**SMS e WhatsApp fora do escopo**: validação só por e-mail

D9

Aceite dos termos implementado, com **texto fictício** até haver o definitivo

D10

Anti-enumeração: a tela não diz que o e-mail já existe

D11

`POST /auth/register` sem verificação é removido

D12

**CPF permanece no usuário**

D13

Tema no servidor; menu recolhido no navegador

D14

Excluir a própria conta entra: definitiva, com código

D15

Trocar/redefinir senha derruba as outras sessões

D16

Cadastro atrás de `SIGNUP_ENABLED`, ligado no DEV local

D17

Cadastro pelo Google: aceite dos termos pela **frase sob o botão**, registrado na criação

D18

CPF **opcional, único, pedido no cadastro** (e editável em Configurações)

D19

Nova versão dos termos: **novo aceite** obrigatório (modal no login)

D20

Integração B3 **usa o CPF do perfil**

Pendências que ficam para o escopo de produção: CAPTCHA no cadastro (ex.: Cloudflare Turnstile), domínio próprio nas origens do Google e no remetente de e-mail (SPF/DKIM), publicação do app no Google com logo e URLs dos textos definitivos de termos e privacidade.

---

## 9. Andamento

Data

O quê

04/10/2026

Plano criado (levantamento do código e da pilha de teste)

04/10/2026

D1–D16 respondidas (exceto D3); SMS/WhatsApp fora; CPF fica no usuário; termos com texto fictício. Abertas D17–D20. Correção: `GOOGLE_CLIENT_ID` está vazio no `.env`

04/10/2026

D3 e D17–D20 respondidas: Redis, frase sob o botão do Google, CPF opcional/único no cadastro, novo aceite dos termos, B3 com o CPF do perfil. Plano fechado, aguardando ordem de execução

05/10/2026

Client ID criado pelo Guilherme (apêndice A, projeto `primevest-510701`); login Google conferido por ele. Ordem de execução

05/10/2026

**U1** — `typ` access/refresh no JWT; `SessionValidator` (usuário existe, token posterior a `password_changed_at`); renovação no front (`api/client.ts`); e-mail normalizado; mensagens em português (`spring.web.locale: pt_BR` fixo e mensagens nos DTOs); limite de falhas de login (429); V41

05/10/2026

**U2** — G5 (nome/foto só na criação), G7 (`GoogleOAuthProvider` e `@react-oauth/google` fora), G8, G9, G10. `useGoogleSignIn` passou a usar ref de callback: com `useRef` o botão não aparecia em tela que monta o container depois (cadastro, Configurações)

05/10/2026

**U3** — `VerificationCodeService` (HMAC, 10 min, 5 tentativas, 60 s, 5/h), `VerificationStore` (Redis; memória nos testes), `RateLimiter`, `AccountMailer`/`SmtpAccountMailer` com modelos HTML; Mailpit no compose (8025/1025; teste 8026/1026); proxy do Vite com `xfwd` (IP real nos limites)

05/10/2026

**U4** — `/auth/signup/{enabled,start,resend,confirm}`, telas `/cadastro`, `/termos`, `/privacidade`, `SIGNUP_ENABLED`; `/auth/register` removido. **U5** — `/auth/password/{forgot,resend,reset}` e `/esqueci-senha`

05/10/2026

**U6** — `/users/me` completo (perfil, CPF cifrado com `UserCpfService`, `preferences`, `password`, `email` com código, `google`, `terms`, exclusão com código); `TermsAcceptanceFilter` (403 `TERMS_REQUIRED`) + `TermsGate`; tela Configurações refeita; B3 usa o CPF do perfil e o CPF não muda com conexão viva

05/10/2026

**U7** — validação: `mvn clean verify` verde, `tsc -b` e `eslint` limpos; no ambiente: V41 aplicada, cadastro → código no Mailpit → conta → Configurações (tema gravado, CPF mascarado) → exclusão com código, pelo navegador; conta de teste apagada. Documentação atualizada. Efeito: token emitido antes de 05/10/2026 não tem `typ` e deixa de valer — quem estava logado entra de novo uma vez

---

## 10. Adendo — foto de perfil (05/10/2026)

Pedido do Guilherme depois da implementação: o usuário troca a foto enviando uma imagem que respeite os padrões de foto
de perfil; a foto fica centralizada em Configurações, com a opção de envio, e aparece no menu lateral.

Decisões (05/10/2026):

- **A1 — Armazenamento: no banco.** Tabela `user_avatars` (V42), 1:1 com `users`, `ON DELETE CASCADE`: some com a conta e
  entra no backup do banco, sem depender de bucket nem de pasta.
- **A2 — Recorte na tela.** Ao escolher o arquivo abre o `AvatarCropModal` (área quadrada com máscara redonda, arrastar e
  zoom); o navegador gera o JPEG 512×512.

Padrões:

- **Na tela:** JPEG, PNG ou WebP, até 5 MB, com pelo menos 128×128 px. O que sobe é o recorte.
- **Na API** (`PUT /users/me/avatar`, multipart `file`): JPEG ou PNG conferido pelos bytes (não pela extensão), até 2 MB,
  de 128 a 4096 px (dimensões lidas antes de decodificar). A API recorta o quadrado central se precisar e regrava JPEG
  (qualidade 0,88, no máximo 512×512) — o que também apaga os metadados do arquivo (EXIF, localização). Fica em média
  com 15–80 KB.
- **Exibição:** `GET /users/me/avatar` com o token (a tela mostra como blob; `<img src>` não mandaria o header);
  `avatarVersion` no perfil muda a cada envio. Sem foto enviada, vale a do Google (da criação) e, sem ela, a inicial.
  O mesmo componente (`UserAvatar`) serve Configurações e o menu lateral.

Conferido em 05/10/2026: `UserAvatarServiceTest` (3), `CadastroUsuarioIT.fotoDePerfil`, `mvn clean verify`, `tsc` e
`eslint`; no navegador, envio de uma imagem 900×600, recorte com zoom e arrasto, foto centralizada em Configurações e no
menu, JPEG de 13,6 KB gravado; conta de teste apagada.

---

## Apêndice A — Criar o Client ID do Google (Guilherme)

**Custo**: nenhum. Criar o projeto, a tela de consentimento e o Client ID é gratuito e **não pede conta de faturamento** (cartão); o login com Google (Sign In With Google, ID token) também não é cobrado. Não confundir com o *Identity Platform* do Google Cloud, produto pago à parte que o PrimeVest **não usa**. Se o console oferecer "Ativar avaliação gratuita"/faturamento, pode ignorar — não é necessário para nada deste plano.

Fonte: guia oficial do Google ("Get your Google API client ID", Sign In With Google). O console agrupa isso em **Google Auth Platform**, com as páginas *Branding*, *Audience*, *Data Access* e *Clients*. Os nomes podem aparecer em português conforme o idioma da conta.

1.  **Projeto** — em `https://console.cloud.google.com`, seletor de projeto no topo → **Novo projeto** → nome `PrimeVest` → Criar. Selecione o projeto criado.
    
2.  **Branding** (tela de consentimento) — o *Google Auth Platform* **não aparece no menu lateral padrão**. Três caminhos, qualquer um serve (projeto criado em 04/10/2026: ID `primevest-510701`):
    
    -   link direto: `https://console.cloud.google.com/auth/overview?project=primevest-510701`;
    -   barra de pesquisa do topo: digite **Google Auth Platform** e abra o resultado;
    -   menu lateral: **APIs e serviços** → **Tela de permissão OAuth** (redireciona para o Google Auth Platform).
    
    Na primeira vez a página mostra **"Primeiros passos"/"Get started"**, um assistente que já é o Branding + Audience:
    
    -   Nome do app: `PrimeVest`
    -   E-mail de suporte: `guilhermeunatec@gmail.com`
    -   Logo, página inicial, links de privacidade/termos: **deixe em branco** por enquanto (só são exigidos para publicar; em teste, logo enviado pode disparar verificação do Google).
    -   Contato do desenvolvedor: o seu e-mail. Aceite a política de dados do Google e conclua.
3.  **Audience** (público):
    
    -   Tipo de usuário: **Externo** (conta Gmail pessoal não tem a opção Interno).
    -   Status de publicação: **Teste**. Em teste, só entram as contas listadas em *Test users* — adicione `guilhermeunatec@gmail.com` e as outras contas Google que for usar nos testes do cadastro (C2). Conta fora da lista é recusada pelo próprio Google, antes de chegar ao PrimeVest.
4.  **Data Access** (escopos) — não precisa adicionar nada: `openid`, `email` e `profile` são os padrões e bastam.
    
5.  **Clients** → **Create client**:
    
    -   Tipo de aplicativo: **Aplicativo da Web**
    -   Nome: `PrimeVest Web (DEV)`
    -   **Origens JavaScript autorizadas**: adicione as duas — o Google exige ambas para localhost:
        -   `http://localhost`
        -   `http://localhost:5173`
    -   **URIs de redirecionamento autorizados**: **nenhum** (o fluxo do PrimeVest é por ID token, sem callback).
    -   Criar.
6.  **Copiar o Client ID** — o valor termina em `.apps.googleusercontent.com`. O **client secret não é usado**: não copie para o projeto.
    
7.  **No PrimeVest** — no `.env` da raiz: `GOOGLE_CLIENT_ID=<o valor>` (uma linha, sem aspas). O compose entrega o mesmo valor à app-api (`GOOGLE_CLIENT_ID`) e ao front (`VITE_GOOGLE_CLIENT_ID`). Recrie os dois containers: `docker compose up -d --force-recreate app-api web`.
    
8.  **Conferir** — abra `http://localhost:5173/login` (por `localhost`, não `127.0.0.1`: esta origem não está autorizada) e veja o botão "Continuar com o Google". A alteração de origem pode levar alguns minutos para valer no Google; `origin_mismatch` logo depois de salvar costuma ser isso.
    

Problemas comuns:

Sintoma

Causa

Botão não aparece

`GOOGLE_CLIENT_ID` vazio ou containers não recriados

`origin_mismatch` / "The given origin is not allowed"

origem faltando no client, aberto por `127.0.0.1`, ou aguardar a propagação

"Acesso bloqueado: o app não concluiu a verificação"

conta não está em *Test users*

422 "Token do Google inválido ou expirado"

Client ID da app-api diferente do front (os dois vêm da mesma variável — conferir o `.env`)