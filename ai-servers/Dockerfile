FROM python:3.11.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AI_EXPORT_ROOT=/app/data/ai-exports

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --create-home app

COPY requirements.txt ./requirements.txt
RUN python -m pip install --no-cache-dir --upgrade pip==25.1.1 \
    && python -m pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY main.py ./main.py

RUN mkdir -p /app/data/ai-exports \
    && chown -R app:app /app

USER app

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8081/healthz', timeout=3).read()" || exit 1

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]
