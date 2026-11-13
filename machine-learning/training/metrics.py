"""Metrics, calibration, threshold and linear-probe helpers shared by training and replay."""
from __future__ import annotations

import csv
import time
import warnings
from pathlib import Path

import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

SEED = 42
TARGET_ACCEPTED_ACCURACY = 0.90


def encode_labels(rows, classes):
    indices = {name: i for i, name in enumerate(classes)}
    return np.asarray([indices[row["class_id"]] for row in rows], dtype=np.int64)


def classification_metrics(truth, scores, classes):
    scores = np.asarray(scores, dtype=np.float64)
    labels = list(range(len(classes)))
    predicted = scores.argmax(axis=1)
    top3 = np.argsort(-scores, axis=1, kind="stable")[:, :3]
    top3_correct = int(np.any(top3 == np.asarray(truth)[:, None], axis=1).sum())
    return {
        "n_images": int(len(truth)),
        "top1_correct": int((predicted == truth).sum()),
        "top1_accuracy": float(accuracy_score(truth, predicted)),
        "macro_f1": float(f1_score(truth, predicted, labels=labels, average="macro", zero_division=0)),
        "top3_correct": top3_correct,
        "top3_accuracy": float(top3_correct / len(truth)),
        "classification_report": classification_report(truth, predicted, labels=labels, target_names=classes,
                                                       output_dict=True, zero_division=0),
        "confusion_matrix": confusion_matrix(truth, predicted, labels=labels).tolist(),
    }


def validation_key(metrics):
    return tuple(metrics[k] for k in ("macro_f1", "top1_accuracy", "top3_accuracy"))


def expected_calibration_error(truth, probabilities, bins=15):
    confidence = probabilities.max(axis=1)
    correct = probabilities.argmax(axis=1) == truth
    edges = np.linspace(0.0, 1.0, bins + 1)
    return float(sum(
        (selected.sum() / len(truth)) * abs(float(correct[selected].mean()) - float(confidence[selected].mean()))
        for index in range(bins)
        if (selected := (confidence > edges[index]) & (confidence <= edges[index + 1])).any()
    ))


def calibration_metrics(truth, logits, temperature):
    probabilities = softmax(logits / temperature, axis=1)
    nll = float(-np.mean(logits[np.arange(len(truth)), truth] / temperature - logsumexp(logits / temperature, axis=1)))
    return {"temperature": float(temperature), "negative_log_likelihood": nll,
            "expected_calibration_error_15_bins": expected_calibration_error(truth, probabilities),
            "probabilities": probabilities}


def fit_temperature(truth, logits):
    before = calibration_metrics(truth, logits, 1.0)
    result = minimize_scalar(lambda t: calibration_metrics(truth, logits, float(np.exp(t)))["negative_log_likelihood"],
                             bounds=(-3.0, 3.0), method="bounded")
    candidate = float(np.exp(result.x))
    after = calibration_metrics(truth, logits, candidate)
    selected = candidate if after["negative_log_likelihood"] < before["negative_log_likelihood"] else 1.0
    return selected, {
        "method": "temperature scaling minimizing validation NLL",
        "before": {k: v for k, v in before.items() if k != "probabilities"},
        "optimized": {k: v for k, v in after.items() if k != "probabilities"},
        "applied": selected != 1.0, "selected_temperature": selected,
    }


def select_threshold(truth, probabilities, target=TARGET_ACCEPTED_ACCURACY):
    confidence = probabilities.max(axis=1)
    correct = probabilities.argmax(axis=1) == truth
    options = []
    for threshold in sorted(set(float(value) for value in confidence)):
        accepted = confidence >= threshold
        options.append((threshold, int(accepted.sum()), float(correct[accepted].mean()) if accepted.any() else 0.0))
    eligible = [item for item in options if item[2] >= target]
    if eligible:
        threshold, count, accuracy = max(eligible, key=lambda item: (item[1], -item[0]))
    else:
        threshold, count, accuracy = max(options, key=lambda item: (item[2], item[1], -item[0]))
    return threshold, {
        "target_accepted_accuracy": target, "selected_validation_accepted_accuracy": accuracy,
        "selected_validation_accepted": count, "selected_validation_coverage": count / len(truth),
        "target_reached": bool(eligible), "fallback": None if eligible else "maximize accuracy, then coverage",
    }


def thresholded(metrics, truth, probabilities, threshold):
    """Add the app's abstention view: coverage and accuracy among accepted predictions."""
    accepted = probabilities.max(axis=1) >= threshold
    correct = probabilities.argmax(axis=1) == truth
    metrics.update(threshold=float(threshold), coverage=float(accepted.mean()), accepted=int(accepted.sum()),
                   accepted_correct=int((correct & accepted).sum()),
                   accepted_accuracy=float(correct[accepted].mean()) if accepted.any() else None)
    return metrics


def evaluated_metrics(truth, logits, classes, temperature, threshold):
    calibrated = calibration_metrics(truth, logits, temperature)
    probabilities = calibrated.pop("probabilities")
    metrics = {**classification_metrics(truth, logits, classes), **calibrated}
    return thresholded(metrics, truth, probabilities, threshold), probabilities


def fit_probes(train_x, train_y, val_x, val_y, classes):
    """Two predeclared C=1 logistic probes; the validation-best initializes the head."""
    candidates, classifiers = [], []
    for name, weight in (("unweighted", None), ("balanced", "balanced")):
        classifier = LogisticRegression(C=1.0, class_weight=weight, max_iter=1000, solver="lbfgs",
                                        random_state=SEED, tol=1e-4)
        started = time.perf_counter()
        with warnings.catch_warnings():  # never start from a probe that failed to converge
            warnings.simplefilter("error", ConvergenceWarning)
            classifier.fit(train_x, train_y)
        metrics = classification_metrics(val_y, classifier.decision_function(val_x), classes)
        candidates.append({"name": name, "class_weight": weight, "C": 1.0, "iterations": classifier.n_iter_.tolist(),
                           "training_seconds": time.perf_counter() - started,
                           "validation": {k: metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")}})
        classifiers.append(classifier)
    best = max(range(len(candidates)), key=lambda i: (*validation_key(candidates[i]["validation"]),
                                                      candidates[i]["name"] == "unweighted"))
    return classifiers[best], candidates, candidates[best]["name"]


def save_confusion(path, matrix, classes):
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["true_class\\predicted_class", *classes])
        for label, values in zip(classes, matrix):
            writer.writerow([label, *values])


def save_class_metrics(path, results, classes):
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=["split", "class", "precision", "recall", "f1", "support",
                                                    "statistical_note"], lineterminator="\n")
        writer.writeheader()
        for split, metrics in results.items():
            for name in classes:
                values = metrics["classification_report"][name]
                support = int(values["support"])
                writer.writerow({"split": split, "class": name, "precision": values["precision"],
                                 "recall": values["recall"], "f1": values["f1-score"], "support": support,
                                 "statistical_note": "instável: suporte 1 ou 2" if support <= 2 else ""})


def save_predictions(path, rows, truth, probabilities, threshold, classes):
    top3 = np.argsort(-probabilities, axis=1, kind="stable")[:, :3]
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=["path", "true_class", "predicted_class", "confidence", "accepted",
                                                    "top3_classes"], lineterminator="\n")
        writer.writeheader()
        for index, row in enumerate(rows):
            writer.writerow({"path": row["path"], "true_class": classes[truth[index]],
                             "predicted_class": classes[top3[index, 0]],
                             "confidence": float(probabilities[index].max()),
                             "accepted": bool(probabilities[index].max() >= threshold),
                             "top3_classes": "|".join(classes[i] for i in top3[index])})
