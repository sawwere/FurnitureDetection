import argparse
import shutil
from pathlib import Path

import yaml
from ultralytics import YOLO


# Глобальные переменные
PROJECT_ROOT: Path | None = None
DATASETS_ROOT: Path | None = None
OUTPUT_DIR: Path | None = None

DATA_YAML_SRC: Path | None = None
DATASET_ROOT: Path | None = None
DATA_YAML_FIXED: Path | None = None

IMG_SIZE = 640
EPOCHS = 20

MODEL_NAME = "yolo11n.pt"   


def find_data_yaml() -> Path:
    """
    Ищем первый файл data.yaml внутри DATASETS_ROOT рекурсивно.
    """
    assert DATASETS_ROOT is not None

    if not DATASETS_ROOT.exists():
        raise FileNotFoundError(
            f"Папка с датасетом не найдена: {DATASETS_ROOT}\n"
            f"Передай корректный --data-dir при запуске."
        )

    print(f"[DEBUG] Ищем data.yaml в {DATASETS_ROOT} ...")
    candidates = list(DATASETS_ROOT.rglob("data.yaml"))
    if not candidates:
        raise FileNotFoundError(
            f"Не найден ни один data.yaml внутри {DATASETS_ROOT}\n"
            f"Проверь структуру датасета и наличие файла data.yaml."
        )

    data_yaml_path = candidates[0]
    print(f"[INFO] Найден data.yaml: {data_yaml_path}")
    return data_yaml_path


def fix_data_yaml():
    """
    Приводим пути в data.yaml к корректным абсолютным путям
    и сохраняем в fixed_data.yaml рядом с исходным.
    """
    assert DATA_YAML_SRC is not None
    assert DATASET_ROOT is not None
    assert DATA_YAML_FIXED is not None

    if not DATA_YAML_SRC.exists():
        raise FileNotFoundError(f"Не найден файл {DATA_YAML_SRC}")

    with open(DATA_YAML_SRC, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    # структура Roboflow:
    # <dataset_root>/train/images, valid/images, test/images
    train_path = DATASET_ROOT / "train" / "images"
    val_path = DATASET_ROOT / "valid" / "images"
    test_path = DATASET_ROOT / "test" / "images"

    print("[DEBUG] train images dir:", train_path)
    print("[DEBUG] valid images dir:", val_path)
    print("[DEBUG] test images dir:", test_path)

    data["train"] = str(train_path)
    data["val"] = str(val_path)
    data["test"] = str(test_path)

    with open(DATA_YAML_FIXED, "w", encoding="utf-8") as f:
        yaml.safe_dump(data, f, allow_unicode=True)

    print(f"[INFO] fixed_data.yaml сохранён в: {DATA_YAML_FIXED}")

    return data


def train_model() -> tuple[Path, Path]:
    """
    Обучение YOLO, возвращаем:
    - путь к лучшим весам best.pt
    - директорию с логами эксперимента (runs/train/exp*)
    """
    assert DATA_YAML_FIXED is not None
    assert OUTPUT_DIR is not None

    print("[INFO] Запуск обучения YOLO11...")

    runs_root = OUTPUT_DIR / "runs"  
    model = YOLO(MODEL_NAME)
    model.train(
        data=str(DATA_YAML_FIXED),
        epochs=EPOCHS,
        imgsz=IMG_SIZE,
        project=str(runs_root),
        name="train",   #  runs/train/exp*
        # device="0",   #  GPU
    )

    best_weights = Path(model.trainer.best)
    run_dir = Path(model.trainer.save_dir)  # runs/train/exp*
    print(f"[INFO] Лучшие веса: {best_weights}")
    print(f"[INFO] Логи эксперимента: {run_dir}")

    return best_weights, run_dir


def run_prediction(best_weights: Path, test_images_dir: Path):
    """
    Запуск предсказаний на тестовом наборе.
    """
    assert OUTPUT_DIR is not None

    print("[INFO] Запуск предсказаний на тестовом наборе...")

    predict_project = OUTPUT_DIR / "runs" / "predict_test"
    model = YOLO(str(best_weights))
    model.predict(
        source=str(test_images_dir),
        save=True,
        conf=0.25,
        project=str(predict_project),
        name="best",
        exist_ok=True,
    )

    print(f"[INFO] Результаты предсказаний в: {predict_project / 'best'}")


def export_tflite(best_weights: Path) -> Path:
    """
    Экспорт модели в TFLite в OUTPUT_DIR.
    """
    assert OUTPUT_DIR is not None

    print("[INFO] Экспорт модели в TFLite (YOLO11)...")

    model = YOLO(str(best_weights))
    exported_path = Path(
        model.export(
            format="tflite",
            nms=True,
            conf=0.25,
            iou=0.45,
            topk=100,
            imgsz=IMG_SIZE,
        )
    )

    target_path = OUTPUT_DIR / exported_path.name
    exported_path.replace(target_path)

    print(f"[INFO] TFLite-модель сохранена в: {target_path}")
    return target_path


def export_labels_txt() -> Path:
    """
    Читает names из исходного data.yaml и создаёт labels.txt в OUTPUT_DIR.
    """
    assert DATA_YAML_SRC is not None
    assert OUTPUT_DIR is not None

    print("[INFO] Экспорт labels.txt...")

    output_path = OUTPUT_DIR / "labels.txt"

    with open(DATA_YAML_SRC, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    labels = data.get("names", [])

    with open(output_path, "w", encoding="utf-8") as f:
        for label in labels:
            f.write(str(label) + "\n")

    print(f"[INFO] labels.txt сохранён в: {output_path}")
    return output_path


def copy_experiment_logs(run_dir: Path):
    """
    При необходимости можно дополнительно скопировать содержимое
    runs/train/exp* в более "плоскую" директорию внутри OUTPUT_DIR.
    """
    assert OUTPUT_DIR is not None

    target_dir = OUTPUT_DIR / "train_exp"
    print(f"[INFO] Копируем артефакты обучения из {run_dir} в {target_dir} ...")
    shutil.copytree(run_dir, target_dir, dirs_exist_ok=True)
    print("[INFO] Копирование логов завершено.")


def parse_args():
    parser = argparse.ArgumentParser(
        description="YOLO11 эксперимент в Docker-контейнере"
    )
    parser.add_argument(
        "--data-dir",
        required=True,
        help="Директория с размеченным набором данных (внутри должен быть data.yaml)",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Директория для результатов обучения и логов эксперимента",
    )
    return parser.parse_args()


def main():
    global PROJECT_ROOT, DATASETS_ROOT, OUTPUT_DIR
    global DATA_YAML_SRC, DATASET_ROOT, DATA_YAML_FIXED

    args = parse_args()

    PROJECT_ROOT = Path(__file__).resolve().parent
    DATASETS_ROOT = Path(args.data_dir).resolve()
    OUTPUT_DIR = Path(args.output_dir).resolve()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"[DEBUG] PROJECT_ROOT: {PROJECT_ROOT}")
    print(f"[DEBUG] DATASETS_ROOT: {DATASETS_ROOT}")
    print(f"[DEBUG] OUTPUT_DIR: {OUTPUT_DIR}")

    # находим и готовим data.yaml
    DATA_YAML_SRC = find_data_yaml()
    DATASET_ROOT = DATA_YAML_SRC.parent
    DATA_YAML_FIXED = DATASET_ROOT / "fixed_data.yaml"

    print("[STEP] 1/4: правим data.yaml")
    fixed_data = fix_data_yaml()

    print("[STEP] 2/4: обучение модели (YOLO11)")
    best_weights, run_dir = train_model()

    print("[STEP] 3/4: предсказания на тесте")
    test_images_dir = Path(fixed_data["test"])
    run_prediction(best_weights, test_images_dir)

    print("[STEP] 4/4: экспорт TFLite и labels.txt")
    tflite_path = export_tflite(best_weights)
    labels_path = export_labels_txt()

    #  копируем логи эксперимента в отдельную папку внутри output
    copy_experiment_logs(run_dir)

    print("[DONE] Эксперимент завершён.")
    print(f"[RESULT] Всё важное лежит в: {OUTPUT_DIR}")
    print(f"         - Модель: {tflite_path.name}")
    print(f"         - Лейблы: {labels_path.name}")
    print(f"         - Логи/артефакты: runs/* и train_exp/")


if __name__ == "__main__":
    main()
