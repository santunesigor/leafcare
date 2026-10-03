# DINOv2 linear probe

This isolated experiment uses the official Meta DINOv2 ViT-S/14 checkpoint and the existing prepared manifest. It does not modify or export the Android model.

From `machine-learning/`, create the experiment environment and install the pinned packages. For CUDA 12.1 on a compatible NVIDIA GPU:

~~~powershell
py -3.12 -m venv experiments/dinov2/.venv
.\experiments\dinov2\.venv\Scripts\Activate.ps1
python -m pip install --extra-index-url https://download.pytorch.org/whl/cu121 -r experiments/dinov2/requirements.txt
python -m experiments.dinov2.benchmark --validate-only
python -m experiments.dinov2.benchmark
~~~

The script verifies the existing image hashes and split, extracts frozen train/validation features, selects between two predeclared linear probes by validation Macro-F1, and freezes calibration and threshold before reading test images. Embeddings and Torch's checkpoint cache are local and must not be committed.

Artifacts are written to `benchmark_artifacts/dinov2/`. The official checkpoint is downloaded to the user's Torch cache.
