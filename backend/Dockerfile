FROM python:3.12-slim

WORKDIR /app

# curl is here for the healthcheck below - the slim image ships without it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY alembic.ini ./
COPY alembic/ ./alembic/
COPY app/ ./app/

ENV PYTHONUNBUFFERED=1 \
    DATABASE_URL=sqlite:////data/trombadario.db

EXPOSE 8000
VOLUME ["/data"]

HEALTHCHECK --interval=60s --timeout=5s --start-period=15s \
  CMD curl -fsS http://127.0.0.1:8000/api/health >/dev/null || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
