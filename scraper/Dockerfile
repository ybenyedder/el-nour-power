# Image officielle Playwright : chromium + toutes les deps système déjà installées
FROM mcr.microsoft.com/playwright/python:v1.45.0-jammy

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .
EXPOSE 5000
CMD ["python", "server.py", "5000"]
