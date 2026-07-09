FROM python:3.11-slim

ARG ENGINE_COMMIT=bfd7a2f0791dbe685ca84e32e176f215122e9f73

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /opt/intern-engine

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates git \
    && git init . \
    && git remote add origin https://github.com/zshah101/Automated-List-Of-Summer-2027-and-Fall-2026-Tech-Internships.git \
    && git fetch --depth 1 origin "${ENGINE_COMMIT}" \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "${ENGINE_COMMIT}" \
    && pip install --no-cache-dir -r requirements.txt \
    && rm -rf .git \
    && apt-get purge -y --auto-remove git \
    && rm -rf /var/lib/apt/lists/*

COPY run-loop.sh /usr/local/bin/run-loop.sh
RUN chmod 0755 /usr/local/bin/run-loop.sh

CMD ["/usr/local/bin/run-loop.sh"]
