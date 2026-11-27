FROM alpine:3 AS trace-init
ADD --checksum=sha256:da44154cea128257779c8a782b9651fd7c0c6aa177cd3810f9a867ac8509555d https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.34.0/opentelemetry-javaagent-2.34.0.jar /agent.jar

FROM python:3.12-alpine
RUN apk add --no-cache openssl && pip install --no-cache-dir 'psycopg[binary]==3.3.2'
WORKDIR /opt/lab
COPY scripts/token.py signing.py
COPY deploy/manual/*.py ./
COPY deploy/manual/web ./web
ENV PYTHONDONTWRITEBYTECODE=1
CMD ["python", "server.py"]
