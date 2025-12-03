# Базовый образ: Linux + Python 3.10
FROM python:3.10-slim


# Часовой пояс и отключение буферизации
ENV TZ=Europe/Moscow
ENV PYTHONUNBUFFERED=1


# Системные библиотеки, которые часто нужны ultralytics/opencv
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    ffmpeg \
    libsm6 \
    libxext6 \
 && rm -rf /var/lib/apt/lists/*


COPY . /app
WORKDIR /app/


RUN pip install --no-cache-dir ultralytics pyyaml opencv-python


# Точка входа: запускать train.py.
ENTRYPOINT ["python", "train.py"]
