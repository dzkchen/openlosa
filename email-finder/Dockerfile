FROM python:3.11-slim

ARG EMAIL_ME_COMMIT=8b448def98d0bf88143affc2459f7c7d433dc869

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt /app/requirements.txt

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates git \
    && git clone https://github.com/dzkchen/email-me.git /opt/email-me \
    && cd /opt/email-me \
    && git checkout "${EMAIL_ME_COMMIT}" \
    && pip install --no-cache-dir . -r /app/requirements.txt \
    && apt-get purge -y --auto-remove git \
    && rm -rf /var/lib/apt/lists/*

COPY server.py /app/server.py

EXPOSE 8091

CMD ["gunicorn", "--bind", "0.0.0.0:8091", "--workers", "1", "--threads", "4", "--timeout", "300", "server:app"]
