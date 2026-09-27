# Cloud Run: python:3.12-slim, non-root, honors $PORT injected by Cloud Run.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN useradd --create-home --uid 1000 --shell /usr/sbin/nologin appuser

WORKDIR /srv/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY migrations ./migrations
COPY openapi.yaml ./openapi.yaml

USER appuser

EXPOSE 8080

# Cloud Run injects $PORT; default to 8080 for local runs.
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080} --proxy-headers"]
