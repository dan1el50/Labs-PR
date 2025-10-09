FROM python:3.11-slim

WORKDIR /app

# Copy the server script
COPY server.py /app/

# Create a directory for serving files
VOLUME /content

# Expose port 8000
EXPOSE 8000

# Set Python to unbuffered mode so logs show immediately
ENV PYTHONUNBUFFERED=1

# Run the server
ENTRYPOINT ["python", "-u", "server.py"]
CMD ["/content"]
