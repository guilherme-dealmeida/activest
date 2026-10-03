# Simulador da API da Area do Investidor da B3 (docs/planos/PLANO_INTEGRACAO_B3.md, F3).
# O codigo e os dados entram por volume (docker-compose.yml, servico b3-investidor-mock).
FROM python:3.12-slim
RUN pip install --no-cache-dir openpyxl==3.1.5
WORKDIR /app
EXPOSE 8090
CMD ["python", "/app/simulador/simulador.py"]
