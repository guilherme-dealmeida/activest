"""
Simulador da API da Area do Investidor da B3 (docs/planos/PLANO_INTEGRACAO_B3.md, F3).

Responde nas mesmas rotas e no mesmo formato da B3, com os extratos que o investidor exporta da
Area do Investidor (Negociacao, Movimentacao, Proventos a Receber), lidos como vieram: sem
conversor e sem arquivo intermediario (principio P4). O que a planilha nao tem vai nulo — nada
e inventado nem calculado. Sem planilhas na pasta, usa os dados sinteticos (sintetico/*.json).

Rotas (as inferidas — Negociacao v2, Eventos v2, Autorizacao — estao marcadas no plano, 6.3):
  POST /{tenant}/oauth2/v2.0/token                      token client_credentials (3.599 s)
  GET  /api/acesso/healthcheck
  GET  /api/authorization/v1/investors/{cpf}            autorizacao do investidor (inferida)
  GET  /api/asset-trading/v2/investors/{cpf}            negociacao (inferida, campos do v1)
  GET  /api/movement/v2/{equities|treasury-bonds|fixed-income}/investors/{cpf}
  GET  /api/position/v3/equities/investors/{cpf}        vazia: a Posicao nao foi exportada
  GET  /api/provisioned-events/v2/investors/{cpf}       proventos a receber (inferida, campos do v1)
  GET  /optin                                            pagina de autorizacao (Autorizar / Revogar)

Comportamento da B3 reproduzido: Bearer obrigatorio (401), periodo obrigatorio
(referenceStartDate/referenceEndDate), 20 itens por pagina com Links, erros 422.01 a 422.07.
Falha sob demanda: parametro _fail=429|500|503 em qualquer rota da API.

Variaveis: B3I_MOCK_CLIENT_ID, B3I_MOCK_CLIENT_SECRET, B3I_MOCK_SCOPE, B3I_MOCK_DOCUMENT (CPF
ficticio valido), B3I_MOCK_AUTHORIZED (true/false), B3I_MOCK_PUBLIC_URL, B3I_MOCK_DATA_DIR (/data),
B3I_MOCK_PORT (8090).
"""

import datetime as dt
import glob
import html
import json
import os
import re
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlparse

PAGE_SIZE = 20
TOKEN_TTL = 3599

CLIENT_ID = os.environ.get("B3I_MOCK_CLIENT_ID", "activest-teste")
CLIENT_SECRET = os.environ.get("B3I_MOCK_CLIENT_SECRET", "segredo-teste")
SCOPE = os.environ.get("B3I_MOCK_SCOPE", "b3i-simulador/.default")
DOCUMENT = re.sub(r"\D", "", os.environ.get("B3I_MOCK_DOCUMENT", "52998224725"))
PUBLIC_URL = os.environ.get("B3I_MOCK_PUBLIC_URL", "http://localhost:8090").rstrip("/")
DATA_DIR = os.environ.get("B3I_MOCK_DATA_DIR", "/data")
SYNTHETIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "sintetico")
PORT = int(os.environ.get("B3I_MOCK_PORT", "8090"))

_lock = threading.Lock()
_tokens = {}  # token -> expira em (epoch)
_authorized = {DOCUMENT: os.environ.get("B3I_MOCK_AUTHORIZED", "false").lower() == "true"}
_authorized_at = {DOCUMENT: dt.datetime.now().isoformat(timespec="seconds") if _authorized[DOCUMENT] else None}


# ------------------------------------------------------------------ leitura das planilhas

def _date(value):
    """dd/mm/aaaa (ou datetime) -> AAAA-MM-DD; '-' e vazio -> None."""
    if value is None:
        return None
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    text = str(value).strip()
    if not text or text == "-":
        return None
    return dt.datetime.strptime(text, "%d/%m/%Y").date().isoformat()


def _number(value):
    """Numero da planilha: float/int, ou texto '1.479' / '0,25' / '-' (nulo)."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return value
    text = str(value).strip().replace(" ", "").replace(" ", "")
    if not text or text == "-":
        return None
    if "," in text:
        text = text.replace(".", "").replace(",", ".")
    elif re.fullmatch(r"-?\d{1,3}(\.\d{3})+", text):
        text = text.replace(".", "")
    return float(text)


def _text(value):
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _product(value):
    """'PETR4 - PETROLEO BRASILEIRO S/A' -> ('PETR4', 'PETROLEO BRASILEIRO S/A')."""
    text = _text(value) or ""
    if " - " in text:
        ticker, name = text.split(" - ", 1)
        return ticker.strip(), name.strip()
    return text, None


def _latest(prefix):
    files = sorted(glob.glob(os.path.join(DATA_DIR, prefix + "*.xlsx")))
    return files[-1] if files else None


def _rows(path):
    import openpyxl  # so quando ha planilha
    ws = openpyxl.load_workbook(path, data_only=True).worksheets[0]
    rows = [r for r in ws.iter_rows(values_only=True) if any(c not in (None, "") for c in r)]
    header = [str(h).strip() if h is not None else "" for h in rows[0]]
    return [dict(zip(header, r)) for r in rows[1:]]


def load_trades():
    path = _latest("negociacao")
    if not path:
        return _synthetic("negociacao")
    out = []
    for r in _rows(path):
        out.append({
            "referenceDate": _date(r.get("Data do Negócio")),
            "side": _text(r.get("Tipo de Movimentação")),
            "marketName": _text(r.get("Mercado")),
            "expirationDate": _date(r.get("Prazo/Vencimento")),
            "participantName": _text(r.get("Instituição")),
            "tickerSymbol": _text(r.get("Código de Negociação")),
            "tradeQuantity": _number(r.get("Quantidade")),
            "priceValue": _number(r.get("Preço")),
            "totalTradeAmount": _number(r.get("Valor")),
        })
    return out


def load_movements():
    path = _latest("movimentacao")
    if not path:
        return _synthetic("movimentacao")
    out = []
    for r in _rows(path):
        product = _text(r.get("Produto")) or ""
        out.append({
            "referenceDate": _date(r.get("Data")),
            "operationType": _text(r.get("Entrada/Saída")),
            "movementType": _text(r.get("Movimentação")),
            "product": product,
            "participantName": _text(r.get("Instituição")),
            "quantity": _number(r.get("Quantidade")),
            "unitPrice": _number(r.get("Preço unitário")),
            "operationValue": _number(r.get("Valor da Operação")),
        })
    return out


def load_events():
    path = _latest("eventos")
    if not path:
        return _synthetic("eventos")
    out = []
    for r in _rows(path):
        if not _text(r.get("Tipo de Evento")):
            continue  # linha de total no fim da planilha
        ticker, name = _product(r.get("Produto"))
        out.append({
            "tickerSymbol": ticker,
            "corporationName": name,
            "specificationCode": _text(r.get("Tipo")),
            "corporateActionTypeDescription": _text(r.get("Tipo de Evento")),
            "PaymentDate": _date(r.get("Previsão de pagamento")),
            "participantName": _text(r.get("Instituição")),
            "eventQuantity": _number(r.get("Quantidade")),
            "eventValue": _number(r.get("Preço unitário")),
            "netValue": _number(r.get("Valor líquido")),
        })
    return out


def _synthetic(name):
    path = os.path.join(SYNTHETIC_DIR, name + ".json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def _product_class(product):
    if product.startswith("Tesouro"):
        return "treasury-bonds"
    first = product.split(" - ", 1)[0].strip()
    if first in ("CDB", "LCI", "LCA", "CRI", "CRA", "LC", "RDB", "DEB", "LF"):
        return "fixed-income"
    return "equities"


def equities_movement(m):
    ticker, name = _product(m["product"])
    return {
        "referenceDate": m["referenceDate"], "productCategoryName": None, "productTypeName": None,
        "movementType": m["movementType"], "operationType": m["operationType"],
        "tickerSymbol": ticker, "corporationName": name,
        "participantName": m["participantName"], "participantDocumentNumber": None,
        "equitiesQuantity": m["quantity"], "unitPrice": m["unitPrice"], "operationValue": m["operationValue"],
        "transferOwnershipDescription": None, "isinCode": None, "baseIsinCode": None,
        "movementTypeDetailCode": None, "processNumber": None,
    }


def treasury_movement(m):
    return {
        "referenceDate": m["referenceDate"], "productCategoryName": "Tesouro Direto", "productTypeName": None,
        "movementType": m["movementType"], "operationType": m["operationType"],
        "tickerSymbol": None, "assetDescription": m["product"],
        "participantName": m["participantName"], "participantDocumentNumber": None,
        "treasuryBondsQuantity": m["quantity"], "unitPrice": m["unitPrice"],
        "operationValue": m["operationValue"], "isinCode": None,
    }


def fixed_income_movement(m):
    parts = [p.strip() for p in m["product"].split(" - ")]
    return {
        "referenceDate": m["referenceDate"], "productCategoryName": "Renda Fixa",
        "productTypeName": parts[0] if parts else None,
        "movementType": m["movementType"], "operationType": m["operationType"],
        "tickerSymbol": parts[1] if len(parts) > 1 else None,
        "corporationName": parts[2] if len(parts) > 2 else None,
        "expirationDate": None, "participantName": m["participantName"], "participantDocumentNumber": None,
        "fixedIncomeQuantity": m["quantity"], "unitPrice": m["unitPrice"],
        "operationValue": m["operationValue"], "assetDescription": m["product"],
        "updatedUnitPriceMTM": None, "updateValueMTM": None,
    }


DATA = {}


def reload_data():
    DATA["trades"] = load_trades()
    DATA["movements"] = load_movements()
    DATA["events"] = load_events()
    origem = "planilhas de " + DATA_DIR if _latest("negociacao") else "dados sinteticos"
    print(f"[simulador] {len(DATA['trades'])} negocios, {len(DATA['movements'])} movimentacoes, "
          f"{len(DATA['events'])} proventos a receber ({origem}); CPF ficticio ****{DOCUMENT[-2:]}", flush=True)


# ------------------------------------------------------------------ HTTP

ERRORS = {
    "422.01": "Formato inválido para CPF/CNPJ",
    "422.02": "Participante não autorizado acessar CPF/CNPJ",
    "422.03": "CPF/CNPJ não localizado",
    "422.04": "Formato inválido para Data de Referência AAAA-MM-DD",
    "422.05": "Data de referência deve ser inferior a data atual",
    "422.06": "A data final é menor que data inicial",
    "422.07": "A data inicial de referência não pode ser inferior ao ano de 2018",
}


class Handler(BaseHTTPRequestHandler):
    server_version = "B3iSimulador/1.0"

    def log_message(self, fmt, *args):
        print("[simulador] " + (fmt % args), flush=True)

    # --- apoio
    def _send(self, status, body, content_type="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        if status == 429:
            self.send_header("Retry-After", "1")
        self.end_headers()
        self.wfile.write(data)

    def _error(self, code):
        self._send(422, {"errors": [{"code": code, "title": ERRORS[code], "detail": ERRORS[code]}]})

    def _authorized_bearer(self):
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return False
        with _lock:
            exp = _tokens.get(auth[7:].strip())
        return exp is not None and exp > time.time()

    def _period(self, query):
        """Valida documento e periodo como a B3; devolve (inicio, fim) ou None se ja respondeu erro."""
        try:
            start = dt.date.fromisoformat(query.get("referenceStartDate", [""])[0])
            end = dt.date.fromisoformat(query.get("referenceEndDate", [""])[0])
        except ValueError:
            self._error("422.04")
            return None
        if end >= dt.date.today():
            self._error("422.05")
            return None
        if end < start:
            self._error("422.06")
            return None
        if start < dt.date(2018, 1, 1):
            self._error("422.07")
            return None
        return start.isoformat(), end.isoformat()

    def _investor(self, doc):
        if not re.fullmatch(r"\d{11}|\d{14}", doc):
            self._error("422.01")
            return False
        if doc != DOCUMENT:
            self._error("422.03")
            return False
        if not _authorized.get(doc):
            self._error("422.02")
            return False
        return True

    def _page(self, items, query):
        page = max(1, int(query.get("page", ["1"])[0] or 1))
        last = max(1, (len(items) + PAGE_SIZE - 1) // PAGE_SIZE)
        base = {k: v[0] for k, v in query.items() if k != "page"}

        def link(p):
            return f"{PUBLIC_URL}{urlparse(self.path).path}?{urlencode({**base, 'page': p})}"

        links = {"self": link(page), "first": link(1), "prev": link(page - 1) if page > 1 else None,
                 "next": link(page + 1) if page < last else None, "last": link(last)}
        return items[(page - 1) * PAGE_SIZE: page * PAGE_SIZE], links

    # --- rotas
    def do_POST(self):
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0") or 0)
        form = parse_qs(self.rfile.read(length).decode("utf-8")) if length else {}
        if re.fullmatch(r"/[^/]+/oauth2/v2\.0/token", url.path):
            ok = (form.get("grant_type", [""])[0] == "client_credentials"
                  and form.get("client_id", [""])[0] == CLIENT_ID
                  and form.get("client_secret", [""])[0] == CLIENT_SECRET
                  and form.get("scope", [""])[0] == SCOPE)
            if not ok:
                return self._send(401, {"error": "invalid_client",
                                        "error_description": "client_id, client_secret ou scope invalido"})
            token = secrets.token_hex(24)
            with _lock:
                _tokens[token] = time.time() + TOKEN_TTL
            return self._send(200, {"token_type": "Bearer", "expires_in": TOKEN_TTL,
                                    "ext_expires_in": TOKEN_TTL, "access_token": token})
        if url.path in ("/optin/authorize", "/optin/revoke"):
            doc = re.sub(r"\D", "", form.get("documentNumber", [DOCUMENT])[0])
            with _lock:
                _authorized[doc] = url.path.endswith("authorize")
                _authorized_at[doc] = dt.datetime.now().isoformat(timespec="seconds") if _authorized[doc] else None
            return self._optin_page(doc)
        return self._send(404, {"errors": [{"code": "404", "title": "Rota não encontrada"}]})

    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)
        if url.path == "/optin":
            return self._optin_page(re.sub(r"\D", "", query.get("documentNumber", [DOCUMENT])[0]))
        if not url.path.startswith("/api/"):
            return self._send(404, {"errors": [{"code": "404", "title": "Rota não encontrada"}]})
        fail = query.get("_fail", [None])[0]
        if fail in ("429", "500", "503"):
            return self._send(int(fail), {"errors": [{"code": fail, "title": "Falha simulada"}]})
        if not self._authorized_bearer():
            return self._send(401, {"errors": [{"code": "401", "title": "Token ausente ou expirado"}]})

        if url.path == "/api/acesso/healthcheck":
            return self._send(200, {"status": "Sucesso",
                                    "mensagem": "Autenticação e autorização do usuário simulador validadas com sucesso."})

        m = re.fullmatch(r"/api/authorization/v1/investors/(\d+)", url.path)
        if m:
            doc = m.group(1)
            if doc != DOCUMENT:
                return self._error("422.03")
            return self._send(200, {"data": {"documentNumber": doc, "authorized": bool(_authorized.get(doc)),
                                             "authorizationDate": _authorized_at.get(doc)}})

        m = re.fullmatch(r"/api/(asset-trading/v2|movement/v2/(equities|treasury-bonds|fixed-income)"
                         r"|position/v3/(equities|treasury-bonds|fixed-income)|provisioned-events/v2)"
                         r"/investors/([^/]+)", url.path)
        if not m:
            return self._send(404, {"errors": [{"code": "404", "title": "Rota não encontrada"}]})
        doc = m.group(4)
        if not self._investor(doc):
            return None
        period = self._period(query)
        if period is None:
            return None
        start, end = period

        if m.group(1) == "asset-trading/v2":
            trades = [t for t in DATA["trades"] if start <= t["referenceDate"] <= end]
            trades.sort(key=lambda t: (t["referenceDate"], t["tickerSymbol"] or ""))
            page, links = self._page(trades, query)
            days = {}
            for t in page:
                days.setdefault(t["referenceDate"], []).append({k: v for k, v in t.items() if k != "referenceDate"})
            period_list = [{"referenceDate": d, "assetTradingList": lst} for d, lst in days.items()]
            return self._send(200, {"data": {"documentNumber": doc, "referenceStartDate": start,
                                             "referenceEndDate": end, "periodList": period_list}, "Links": links})

        if m.group(1).startswith("movement/v2"):
            kind = m.group(2)
            movs = [x for x in DATA["movements"]
                    if start <= (x["referenceDate"] or "") <= end and _product_class(x["product"]) == kind]
            movs.sort(key=lambda x: x["referenceDate"])
            page, links = self._page(movs, query)
            if kind == "equities":
                data = {"equitiesPeriods": {"documentNumber": doc, "referenceStartDate": start, "referenceEndDate": end,
                                            "equitiesMovements": [equities_movement(x) for x in page]}}
            elif kind == "treasury-bonds":
                data = {"treasuryBondsPeriods": {"documentNumber": doc, "referenceStartDate": start,
                                                 "referenceEndDate": end,
                                                 "treasuryBondsMovements": [treasury_movement(x) for x in page]}}
            else:
                data = {"fixedIncomes": {"documentNumber": doc, "referenceStartDate": start, "referenceEndDate": end,
                                         "fixedIncomeMovements": [fixed_income_movement(x) for x in page]}}
            return self._send(200, {"data": data, "Links": links})

        if m.group(1).startswith("position/v3"):
            # A Posicao nao foi exportada (plano, 6.4): devolve vazia, sem inventar.
            key = {"equities": "equitiesPositions", "treasury-bonds": "treasuryBondsPositions",
                   "fixed-income": "fixedIncomePositions"}[m.group(3)]
            page, links = self._page([], query)
            return self._send(200, {"data": {key: page}, "Links": links})

        # provisioned-events/v2: os proventos a receber como estao no extrato (a planilha e uma foto do dia).
        events = sorted(DATA["events"], key=lambda e: (e["PaymentDate"] or "", e["tickerSymbol"] or ""))
        page, links = self._page([{"documentNumber": doc, "referenceDate": end, **e} for e in events], query)
        return self._send(200, {"data": {"provisionedEvents": page}, "Links": links})

    def _optin_page(self, doc):
        ok = _authorized.get(doc, False)
        cpf = "***.***.***-" + doc[-2:] if len(doc) >= 2 else "?"
        desde = "<br><small>desde " + html.escape(_authorized_at.get(doc) or "") + "</small>" if ok else ""
        body = f"""<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<title>Simulador B3 — autorização</title>
<style>body{{font-family:system-ui,sans-serif;max-width:560px;margin:48px auto;padding:0 16px;color:#1f2937}}
.box{{border:1px solid #d1d5db;border-radius:8px;padding:20px}}button{{padding:8px 16px;margin-right:8px}}
.ok{{color:#15803d}}.no{{color:#b91c1c}}small{{color:#6b7280}}</style></head><body>
<div class="box"><h2>Simulador da Área do Investidor (B3)</h2>
<p>Investidor: <b>{html.escape(cpf)}</b></p>
<p>Compartilhamento com o Activest: <b class="{'ok' if ok else 'no'}">{'autorizado' if ok else 'não autorizado'}</b>
{desde}</p>
<form method="post" action="/optin/authorize" style="display:inline"><input type="hidden" name="documentNumber" value="{html.escape(doc)}">
<button type="submit">Autorizar</button></form>
<form method="post" action="/optin/revoke" style="display:inline"><input type="hidden" name="documentNumber" value="{html.escape(doc)}">
<button type="submit">Revogar</button></form>
<p><small>Simulador local (docs/planos/PLANO_INTEGRACAO_B3.md, F3). Na B3 real esta tela é a jornada de
consentimento da Área do Investidor, com login do investidor.</small></p></div></body></html>"""
        return self._send(200, body.encode("utf-8"), "text/html; charset=utf-8")


def main():
    reload_data()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[simulador] ouvindo na porta {PORT}; opt-in em {PUBLIC_URL}/optin", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        sys.exit(0)


if __name__ == "__main__":
    main()
