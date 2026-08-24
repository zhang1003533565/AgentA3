FROM python:3.11.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AI_EXPORT_ROOT=/app/data/ai-exports \
    CHROMIUM_PATH=/usr/bin/chromium \
    PRESENTON_ENABLE_PPTX=true \
    PRESENTON_EXPORT_ROOT=/app/presenton_runtime/presentation-export \
    BUILT_PYTHON_MODULE_PATH=/app/presenton_runtime/presentation-export/py/convert-linux-current

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --create-home app

RUN apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install --no-install-recommends --yes libreoffice-impress fonts-noto-cjk nodejs npm chromium unzip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./requirements.txt
RUN python -m pip install --no-cache-dir --upgrade pip==25.1.1 \
    && python -m pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY presenton_runtime ./presenton_runtime
COPY main.py ./main.py

RUN cd /app/presenton_runtime \
    && npm ci --omit=dev \
    && npx esbuild src/template-v2-json-to-html.ts --bundle --platform=node --format=esm --outfile=dist/template-v2-json-to-html.mjs \
    && node sync-presentation-export.cjs --force \
    && npx playwright install-deps chromium \
    && chown -R app:app /app/presenton_runtime

RUN mkdir -p /app/data/ai-exports /app/data/ppt-sources \
    && chown -R app:app /app

USER app

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8081/healthz', timeout=3).read()" || exit 1

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]
