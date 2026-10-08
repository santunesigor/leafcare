"""Promove o checkpoint já selecionado; não treina nem recalibra pelo teste."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

import numpy as np
from leafcare.common import sha256, classes_hash, read_json, write_json
from leafcare.inference import TFLitePredictor
from leafcare.preprocessing import DISTILLED_RESIZE, decode_rgb, resize_distilled_rgb, preprocess
from experiments.dinov2.benchmark import classification_metrics, encode_labels, split_rows

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
SOURCE = ROOT / 'benchmark_artifacts/distillation/mobilenetv4_small_distilled'
REPORT = REPO / 'docs/model-reports/mobilenetv4-distilled'
MODEL_SHA = '422ab461585a83bfabe6896d7ac7b32c726a8b285ff4ade76cb0cec54ba6da99'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true', help='Atualiza artifacts e assets após validar todo o replay.')
    parser.add_argument('--pixel-parity-dir', type=Path, help='Fixtures RGB locais para teste Kotlin (não versionadas).')
    args = parser.parse_args()
    config, export = read_json(SOURCE / 'experiment_config.json'), read_json(SOURCE / 'mobile_export.json')
    model = ROOT / export['path_local']
    assert sha256(model) == export['sha256'] == MODEL_SHA
    assert sha256(ROOT / config['checkpoint_local']) == config['checkpoint_sha256']
    assert sha256(ROOT / 'data/prepared/manifest.json') == config['manifest_sha256']
    manifest = read_json(ROOT / 'data/prepared/manifest.json')
    classes = config['class_order']
    assert classes == manifest['classes'] == read_json(ROOT / 'artifacts/classes.json')
    predictor = TFLitePredictor(model, classes)
    parity = {'passed': True, 'split': 'validation', 'n_images': 104, 'max_abs_error_limit': 1e-4}
    results = {}
    pixels = []
    with np.load(SOURCE / 'logits.npz', allow_pickle=False) as recorded:
        for split in ('validation', 'test'):
            rows = split_rows(manifest, split)
            probabilities = []
            for index, row in enumerate(rows):
                image = ROOT / 'data/raw' / row['path']
                assert sha256(image) == row['sha256']
                rgb = decode_rgb(image)
                resized = resize_distilled_rgb(rgb)
                scores, _ = predictor.scores(resized[None, ...])
                probabilities.append(scores)
                if split == 'validation' and args.pixel_parity_dir:
                    args.pixel_parity_dir.mkdir(parents=True, exist_ok=True)
                    name = f'{index:03d}.rgb'
                    (args.pixel_parity_dir / name).write_bytes(rgb.tobytes())
                    pixels.append({'file': name, 'width': rgb.shape[1], 'height': rgb.shape[0],
                                   'sha256': hashlib.sha256(resized.astype(np.uint8).tobytes()).hexdigest()})
            probabilities = np.asarray(probabilities)
            logits = recorded[split] / config['temperature']
            exp = np.exp(logits - logits.max(axis=1, keepdims=True))
            expected = exp / exp.sum(axis=1, keepdims=True)
            error = float(np.max(np.abs(probabilities - expected)))
            assert error <= 1e-4 and np.array_equal(probabilities.argmax(1), expected.argmax(1)), (split, error)
            truth = encode_labels(rows, classes)
            metrics = classification_metrics(truth, probabilities, classes)
            accepted = probabilities.max(axis=1) >= np.float32(config['threshold'])
            correct = probabilities.argmax(axis=1) == truth
            metrics.update(coverage=float(accepted.mean()), accepted=int(accepted.sum()),
                           accepted_correct=int(correct[accepted].sum()), accepted_accuracy=float(correct[accepted].mean()))
            original = read_json(SOURCE / f'{split}_metrics.json')
            for key in ('top1_accuracy', 'macro_f1', 'top3_accuracy', 'coverage', 'accepted_accuracy'):
                assert metrics[key] == original[key], (split, key)
            results[split] = {**metrics, 'split': split, 'model_sha256': MODEL_SHA,
                              'threshold': config['threshold'], 'test_used_for_selection': False}
            if split == 'validation':
                parity.update(max_abs_error=error, top1_agreement=1.0)
    if args.pixel_parity_dir:
        write_json(args.pixel_parity_dir / 'index.json', pixels)
        lines = [f'count={len(pixels)}']
        for index, row in enumerate(pixels):
            lines.extend(f'{index:03d}.{key}={row[key]}' for key in ('width', 'height', 'sha256'))
        (args.pixel_parity_dir / 'index.properties').write_text('\n'.join(lines) + '\n')
    metadata = {
        'schema_version': 2, 'status': 'trained', 'architecture': 'MobileNetV4SmallDistilled',
        'classes': classes, 'classes_sha256': classes_hash(classes), 'model_sha256': MODEL_SHA,
        'input_shape': [1,224,224,3], 'input_dtype': 'float32', 'output_shape': [1,len(classes)], 'output_dtype': 'float32',
        'resize': DISTILLED_RESIZE, 'normalization': 'embedded_imagenet_mean_std',
        'input_range': [0,255], 'max_image_pixels': 16000000,
        'preprocessing': {**config['preprocessing'], 'short_side': 256, 'center_crop': 224, 'coefficient_precision_bits': 22},
        'temperature': config['temperature'], 'confidence_threshold': config['threshold'], 'threshold_calibrated': True,
        'probability_calibration': 'embedded_temperature_scaling', 'parameters': config['parameters'],
        'checkpoint_sha256': config['checkpoint_sha256'], 'manifest_sha256': config['manifest_sha256'],
        'knowledge_distillation': {'teacher': 'DINOv2-B partial finetune', 'teacher_checkpoint_sha256': config['teacher_checkpoint_sha256'],
                                  'temperature': 4.0, 'weight': 0.5},
        'source_experiment': 'benchmark_artifacts/distillation/mobilenetv4_small_distilled',
        'selected_epoch': config['selected_epoch'], 'conversion_parity': parity, 'test_used_for_selection': False,
    }
    if args.install:
        REPORT.mkdir(parents=True, exist_ok=True)
        for split, metrics in results.items():
            write_json(REPORT / f'{split}_metrics.json', metrics)
        write_json(REPORT / 'integration_parity.json', parity)
        image = REPO / 'samples/reference_frog_eye.jpg'
        scores, _ = predictor.scores(preprocess(image, resize=DISTILLED_RESIZE))
        write_json(REPORT / 'reference_prediction.json', {'image': image.relative_to(REPO).as_posix(),
            'image_sha256': sha256(image), 'model_sha256': MODEL_SHA, 'probabilities': scores.tolist()})
        for folder in (ROOT / 'artifacts', REPO / 'android-app/app/src/main/assets'):
            shutil.copyfile(model, folder / 'leafcare.tflite')
            write_json(folder / 'model_metadata.json', metadata)
        write_json(ROOT / 'artifacts/training_metadata.json', {**config, 'architecture': 'MobileNetV4SmallDistilled', 'model_sha256': MODEL_SHA})
        write_json(ROOT / 'artifacts/metrics.json', {**results['test'], 'status': 'evaluated', 'inference_backend': 'TFLite float32',
            'classes': classes, 'accuracy': results['test']['top1_accuracy'], 'threshold_calibrated': True})
    print(json.dumps({'installed': args.install, 'parity': parity, 'test_top1': results['test']['top1_accuracy']}, indent=2))


if __name__ == '__main__':
    main()
