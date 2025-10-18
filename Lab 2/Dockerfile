FROM python:3.11-slim

WORKDIR /app

COPY server.py /app/

VOLUME /content

EXPOSE 8000

ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["python", "-u", "server.py"]

CMD ["/content"]
