import time

import numpy as np

from .preprocessing import preprocess


def rank(scores, classes, threshold):
    if not 0 <= threshold <= 1:
        raise ValueError("Limiar deve estar entre 0 e 1.")
    # Same Float rounding as Kotlin, including the exact boundary.
    threshold = float(np.float32(threshold))
    scores = np.asarray(scores, dtype=np.float32).reshape(-1)
    if len(classes) < 3 or len(scores) != len(classes) or len(set(classes)) != len(classes):
        raise ValueError("Quantidade/ordem de classes incompatível com a saída.")
    if not np.isfinite(scores).all() or (scores < 0).any() or (scores > 1.0001).any() or abs(float(scores.sum()) - 1) > 0.01:
        raise ValueError("O modelo deve produzir probabilidades softmax válidas.")
    indices = sorted(range(len(classes)), key=lambda i: (-float(scores[i]), i))[:3]
    return {
        "inconclusive": float(scores[indices[0]]) < threshold,
        "threshold": threshold,
        "top3": [{"class_id": classes[i], "confidence": float(scores[i])} for i in indices],
        "notice": "Triagem visual. Confirme com um profissional; confiança não é acurácia.",
    }


class TFLitePredictor:
    """LiteRT interpreter with the app contract: RGB 0-255 float32 [1,224,224,3] -> softmax [1,classes]."""

    def __init__(self, model_path, classes):
        from ai_edge_litert.interpreter import Interpreter
        self.interpreter = Interpreter(model_path=str(model_path), num_threads=2)
        self.interpreter.allocate_tensors()
        self.input = self.interpreter.get_input_details()[0]
        self.output = self.interpreter.get_output_details()[0]
        if list(self.input["shape"]) != [1, 224, 224, 3] or self.input["dtype"] != np.float32:
            raise ValueError("Entrada esperada: float32 [1,224,224,3].")
        if list(self.output["shape"]) != [1, len(classes)] or self.output["dtype"] != np.float32:
            raise ValueError("Saída incompatível com classes.json.")

    def scores(self, tensor):
        self.interpreter.set_tensor(self.input["index"], np.asarray(tensor, dtype=np.float32))
        start = time.perf_counter()
        self.interpreter.invoke()
        elapsed = (time.perf_counter() - start) * 1000
        return self.interpreter.get_tensor(self.output["index"])[0].copy(), elapsed

    def predict(self, path, classes, threshold):
        scores, elapsed = self.scores(preprocess(path))
        return {**rank(scores, classes, threshold), "inference_ms": elapsed}
