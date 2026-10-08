"""Export all completed paired experiments into their own cache; never Android assets."""
import argparse
import gc
import json
import platform
import statistics
import time
from importlib.metadata import distributions

import numpy as np
import torch
from safetensors.torch import load_file
from timm.layers import set_fused_attn
from timm.utils import reparameterize_model

from experiments.distillation import benchmark as b
from experiments.vision.export_candidates import MobileInput
from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest


def check_parity(reference, converted, recorded):
    result = {
        'top1_agreement': float((reference.argmax(1) == converted.argmax(1)).mean()),
        'max_abs_error': float(np.abs(reference - converted).max()),
        'recorded_training_top1_agreement': float((reference.argmax(1) == recorded.argmax(1)).mean()),
        'recorded_training_max_abs_error': float(np.abs(reference - recorded).max()),
        'max_abs_error_limit': 1e-4,
    }
    result['passed'] = (result['top1_agreement'] == result['recorded_training_top1_agreement'] == 1
                        and result['max_abs_error'] <= 1e-4
                        and result['recorded_training_max_abs_error'] <= 1e-4)
    return result


def export_run(run_id, config, manifest):
    import litert_torch
    from ai_edge_litert.interpreter import Interpreter
    folder = b.OUTPUT / run_id
    cfg = b.read_json(folder / 'experiment_config.json')
    source = b.ROOT / cfg['checkpoint_local']
    if b.digest(source) != cfg['checkpoint_sha256']:
        raise ValueError('Selected checkpoint changed')
    if cfg['manifest_sha256'] != b.manifest_digest(b.ROOT / config['prepared_dir'] / 'manifest.json'):
        raise ValueError('Export manifest changed')
    if cfg['class_order'] != manifest['classes']:
        raise ValueError('Export class order changed')
    previous = folder / 'mobile_export.json'
    if previous.exists() and b.read_json(previous)['status'] == 'completed':
        metadata = b.read_json(previous)
        if b.digest(b.ROOT / metadata['path_local']) != metadata['sha256']:
            raise ValueError('Previously exported model changed')
        print('Preserving exported run ' + run_id, flush=True)
        return
    encoder, transform, preprocessing, _ = b.load_student(cfg['base_model_id'])
    with np.load(b.OUTPUT / cfg['base_model_id'] / 'initial_probe.npz', allow_pickle=False) as initial:
        classifier = b.Classifier(encoder, initial['coef'], initial['intercept'])
    classifier.load_state_dict(load_file(str(source)), strict=True)
    classifier.eval().requires_grad_(False)
    b.clear_attention_cache(classifier)
    val = b.Images(config, manifest, 'validation', transform)
    unfused_logits = b.evaluate(classifier, val, torch.device('cpu'))
    fused = cfg['base_model_id'].startswith(('fastvit', 'efficientvit'))
    if fused:
        classifier = reparameterize_model(classifier).eval()
        b.clear_attention_cache(classifier)
    fused_logits = b.evaluate(classifier, val, torch.device('cpu'))
    fusion_error = float(np.abs(unfused_logits - fused_logits).max())
    fusion_agreement = float((unfused_logits.argmax(1) == fused_logits.argmax(1)).mean())
    if fusion_error > 1e-3 or fusion_agreement != 1:
        raise ValueError('Reparameterization parity failed')
    mobile = MobileInput(classifier, preprocessing['mean'], preprocessing['std'], cfg['temperature']).eval()
    height, width = cfg['input_shape'][-2:]
    sample = torch.zeros(1, height, width, 3)
    destination = b.CACHE / 'mobile' / (run_id + '_float32.tflite')
    destination.parent.mkdir(parents=True, exist_ok=True)
    write_json(previous, {'status': 'running', 'model_id': run_id, 'experimental_only': True})
    print('Converting ' + run_id, flush=True)
    b.clear_attention_cache(mobile)
    tick = time.perf_counter()
    converted = litert_torch.convert(mobile, (sample,))
    converted.export(str(destination))
    conversion_seconds = time.perf_counter() - tick
    b.clear_attention_cache(mobile)
    interpreter = Interpreter(model_path=str(destination), num_threads=2)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    if list(inp['shape']) != [1, height, width, 3] or inp['dtype'] != np.float32:
        raise ValueError('Export input contract mismatch')
    if list(out['shape']) != [1, 16] or out['dtype'] != np.float32:
        raise ValueError('Export output contract mismatch')
    mean = torch.tensor(preprocessing['mean']).reshape(3, 1, 1)
    std = torch.tensor(preprocessing['std']).reshape(3, 1, 1)

    def input_for(index):
        normalized = val[index][0]
        return ((normalized * std + mean) * 255).permute(1, 2, 0).unsqueeze(0).contiguous().numpy()

    references, predictions = [], []
    with torch.inference_mode():
        for index in range(len(val)):
            rgb = input_for(index)
            references.append(mobile(torch.from_numpy(rgb)).numpy()[0])
            interpreter.set_tensor(inp['index'], rgb)
            interpreter.invoke()
            predictions.append(interpreter.get_tensor(out['index'])[0])
    references, predictions = np.array(references), np.array(predictions)
    with np.load(folder / 'logits.npz', allow_pickle=False) as scores:
        from scipy.special import softmax
        recorded = softmax(scores['validation'] / cfg['temperature'], axis=1)
    parity = check_parity(references, predictions, recorded)
    parity.update(n_images=len(val), split='validation', reparameterized=fused,
                  fusion_max_abs_logit_error=fusion_error, fusion_top1_agreement=fusion_agreement)
    write_json(folder / 'conversion_parity.json', parity)
    np.savez_compressed(folder / 'conversion_probabilities.npz', reference=references, tflite=predictions, recorded=recorded)
    if not parity['passed']:
        raise ValueError('TFLite conversion parity failed')
    inference, pipeline = [], []
    rgb = input_for(0)
    for i in range(35):
        interpreter.set_tensor(inp['index'], rgb)
        start = time.perf_counter()
        interpreter.invoke()
        if i >= 5:
            inference.append((time.perf_counter() - start) * 1000)
        start = time.perf_counter()
        image = input_for(i % len(val))
        interpreter.set_tensor(inp['index'], image)
        interpreter.invoke()
        scores = interpreter.get_tensor(out['index'])[0]
        np.argsort(-scores, kind='stable')[:3]
        if i >= 5:
            pipeline.append((time.perf_counter() - start) * 1000)
    metadata = {
        'status': 'completed', 'experimental_only': True, 'model_id': run_id,
        'format': 'TFLite float32', 'path_local': str(destination.relative_to(b.ROOT)),
        'sha256': b.digest(destination), 'bytes': destination.stat().st_size,
        'input_shape': [int(v) for v in inp['shape']], 'input_dtype': 'float32',
        'output_shape': [1, 16], 'output_dtype': 'float32',
        'class_order': manifest['classes'], 'preprocessing': preprocessing,
        'normalization': 'RGB 0-255 NHWC; mean/std, softmax and calibration embedded; resize/crop official outside graph',
        'temperature': cfg['temperature'], 'threshold': cfg['threshold'],
        'source_checkpoint_sha256': cfg['checkpoint_sha256'],
        'manifest_sha256': cfg['manifest_sha256'], 'conversion_seconds': conversion_seconds,
        'parity': parity,
        'desktop_tflite_ms_median': statistics.median(inference),
        'desktop_tflite_ms_p95': float(np.percentile(inference, 95)),
        'desktop_python_pipeline_ms_median': statistics.median(pipeline),
        'desktop_python_pipeline_ms_p95': float(np.percentile(pipeline, 95)),
        'timing_note': 'Intel Core i5-13400, CPU 2 threads, 5 warmups/30 samples; pipeline decode, official preprocessing, TFLite, Top3; excludes Room/UI; not Android.',
        'runtime': {'python': platform.python_version(), 'torch': torch.__version__, 'litert_torch': litert_torch.__version__,
                    'installed_packages': sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions())},
        'android_target_ms': 3000, 'android_reference_device': 'Samsung Galaxy A06',
        'android_target_verified': False, 'android_full_analysis_ms': None,
        'android_blocker': 'No connected reference device; experimental assets not integrated',
        'operators': sorted({op['op_name'] for op in interpreter._get_ops_details()}),
    }
    write_json(previous, metadata)
    print(json.dumps({k: metadata[k] for k in ('model_id', 'bytes', 'desktop_tflite_ms_median')}), flush=True)
    del mobile, encoder, classifier, interpreter, converted
    gc.collect()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', nargs='+')
    args = parser.parse_args()
    torch.set_num_threads(2)
    set_fused_attn(False)
    config = load_config(b.ROOT / 'config.yaml')
    manifest = load_manifest(config, verify=True)
    runs = [m + '_' + method for m in b.MODELS for method in b.METHODS]
    if args.runs and not set(args.runs) <= set(runs):
        raise ValueError('Unknown experimental run')
    for run_id in runs:
        if b.read_json(b.OUTPUT / run_id / 'run_summary.json')['status'] != 'completed':
            raise ValueError('All paired training runs must complete before exports')
    before = b.read_json(b.OUTPUT / 'isolation_before.json')
    b.verify_protected(before)
    failures = []
    try:
        for run_id in args.runs or runs:
            try:
                export_run(run_id, config, manifest)
            except Exception as error:
                write_json(b.OUTPUT / run_id / 'mobile_export.json', {
                    'status': 'failed', 'experimental_only': True, 'model_id': run_id,
                    'error_type': type(error).__name__, 'error': str(error),
                    'android_target_verified': False,
                })
                failures.append(run_id)
                print('Export failed: ' + run_id + ': ' + str(error), flush=True)
                gc.collect()
        write_json(b.OUTPUT / 'export_summary.json', {'attempted': args.runs or runs, 'failed': failures,
                   'android_assets_replaced': False, 'all_runs_attempted': not bool(args.runs)})
    finally:
        write_json(b.OUTPUT / 'isolation_after_export.json', b.verify_protected(before))
    if failures:
        raise RuntimeError('Experimental exports failed: ' + ', '.join(failures))


if __name__ == '__main__':
    main()
