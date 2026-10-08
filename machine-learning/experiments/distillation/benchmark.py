"""Paired mobile students, supervised vs. DINOv2 distillation; never deploys models."""
import argparse
import gc
import json
import platform
import random
import statistics
import time
from collections import Counter
from importlib.metadata import distributions
from pathlib import Path

import numpy as np
import timm
import torch
from huggingface_hub import hf_hub_download
from PIL import Image
from safetensors.torch import load_file, save_file
from scipy.special import softmax
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.transforms import InterpolationMode

from experiments.dinov2.benchmark import (
    ROOT, SEED, EXPECTED_SPLITS, classification_metrics, digest, encode_labels,
    fit_temperature, manifest_digest, save_class_metrics, save_confusion,
    save_predictions, select_threshold, split_rows, validate_manifest,
)
from experiments.vision.benchmark import evaluated_metrics, fit_probes
from experiments.vision.finetune import Classifier, validation_key
from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import decode_rgb

CACHE = ROOT / 'experiments/distillation/.cache'
OUTPUT = ROOT / 'benchmark_artifacts/distillation'
TEACHER_FOLDER = ROOT / 'benchmark_artifacts/vision_finetune/dinov2_vitb14'
MODELS = {
    'mobilenetv3_small': {'architecture': 'mobilenetv3_small_100.lamb_in1k',
        'revision': '1824797e7887cbec1990e4adbd6675960a36c589'},
    'mobilenetv4_small': {'architecture': 'mobilenetv4_conv_small.e2400_r224_in1k',
        'revision': '331fb803779522b685cf942e15f914fb6741c1eb'},
    'fastvit_t8': {'architecture': 'fastvit_t8.apple_dist_in1k',
        'revision': '09215e34b4f178654bf6efc44131c50aa865779a'},
    'efficientvit_m2': {'architecture': 'efficientvit_m2.r224_in1k',
        'revision': 'f8b4f12220f55c2ea95fd81cde848ac5612ea4be'},
    'efficientvit_m3': {'architecture': 'efficientvit_m3.r224_in1k',
        'revision': '9e6613643bedb7bd2432251bb71a00bdc47f9895'},
}
METHODS = ('supervised', 'distilled')
RECIPE = {
    'seed': SEED, 'batch_size': 8, 'warmup_epochs': 3, 'finetune_epochs': 15,
    'patience': 5, 'optimizer': 'AdamW', 'head_warmup_lr': 1e-3,
    'head_finetune_lr': 1e-4, 'backbone_lr': 1e-5, 'weight_decay': 1e-4,
    'class_weight': 'balanced from training counts only',
    'distillation_temperature': 4.0, 'distillation_weight': 0.5,
    'loss': '(1-alpha) * weighted CE + alpha * T^2 * batchmean KL(teacher || student)',
    'augmentation': 'flip H/V p=.5; rotation +/-12 bilinear white fill; brightness/contrast .1 saturation .05; seed=42+epoch*100000+train_index',
    'batch_norm': 'running statistics frozen',
    'initialization': 'same pretrained encoder and validation-selected C=1 logistic probe for each pair',
    'selection': 'validation Macro-F1, Top-1, Top-3; earliest on ties',
    'precision': 'float32',
}
AUGMENT = transforms.Compose([
    transforms.RandomHorizontalFlip(), transforms.RandomVerticalFlip(),
    transforms.RandomRotation(12, interpolation=InterpolationMode.BILINEAR, fill=255),
    transforms.ColorJitter(brightness=.1, contrast=.1, saturation=.05),
])


def read_json(path):
    return json.loads(Path(path).read_text())


def protected_hashes():
    """Only model/data/benchmark public artifacts; never local credentials."""
    roots = [ROOT / 'artifacts', ROOT / 'data/prepared',
             ROOT.parent / 'android-app/app/src/main/assets',
             ROOT.parent / 'docs/model-reports',
             ROOT / 'benchmark_artifacts']
    files = [ROOT / 'config.yaml', ROOT / 'tla_class_map.yaml', ROOT / 'train_ensemble.py']
    for directory in roots:
        files.extend(p for p in directory.rglob('*') if p.is_file()
                     and not p.is_relative_to(OUTPUT))
    return {str(p.relative_to(ROOT.parent)): digest(p) for p in sorted(set(files))}


def verify_protected(before):
    after = protected_hashes()
    if before != after:
        changed = sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k))
        raise ValueError('Protected files changed: ' + ', '.join(changed))
    return {'passed': True, 'files_checked': len(before), 'sha256': before}


def augmented(image, epoch, index):
    # Do not perturb dropout or loader RNG; both methods get identical PIL views.
    with torch.random.fork_rng(devices=[]):
        torch.random.default_generator.manual_seed(SEED + epoch * 100000 + index)
        return AUGMENT(image)


class Images(Dataset):
    def __init__(self, config, manifest, split, transform, epoch=0):
        self.config, self.rows, self.transform, self.epoch = config, split_rows(manifest, split), transform, epoch
        self.labels = encode_labels(self.rows, manifest['classes'])
        if epoch and split != 'train':
            raise ValueError('Only training images may be augmented or distilled')

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        image = Image.fromarray(decode_rgb(ROOT / self.config['dataset_dir'] / self.rows[index]['path']))
        if self.epoch:
            image = augmented(image, self.epoch, index)
        return self.transform(image), int(self.labels[index]), index


def loader(images, shuffle=False, epoch=0):
    return DataLoader(images, batch_size=RECIPE['batch_size'], shuffle=shuffle,
                      generator=torch.Generator().manual_seed(SEED + epoch), num_workers=0)


def evaluate(model, images, device):
    model.eval()
    logits = []
    with torch.inference_mode():
        for batch, _, _ in loader(images):
            logits.append(model(batch.to(device)).cpu().numpy())
    return np.concatenate(logits)


def clear_attention_cache(model):
    for module in model.modules():
        if hasattr(module, 'attention_bias_cache'):
            module.attention_bias_cache.clear()


def load_student(model_id):
    spec = MODELS[model_id]
    pretrained = Path(hf_hub_download('timm/' + spec['architecture'], 'model.safetensors',
                                    revision=spec['revision'], cache_dir=str(CACHE / 'weights')))
    encoder = timm.create_model(spec['architecture'], pretrained=False)
    encoder.load_state_dict(load_file(str(pretrained)), strict=True)
    encoder.reset_classifier(0)
    preprocessing = timm.data.resolve_model_data_config(encoder)
    transform = timm.data.create_transform(**preprocessing, is_training=False)
    return encoder, transform, preprocessing, pretrained


class DinoTokens(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.backbone = timm.create_model('vit_base_patch14_dinov2.lvd142m', pretrained=False)
        self.backbone.set_input_size(img_size=224)
        self.backbone.reset_classifier(0)

    def forward(self, images):
        tokens = self.backbone.forward_features(images)
        return torch.cat((tokens[:, 0], tokens[:, self.backbone.num_prefix_tokens:].mean(dim=1)), dim=1)


def load_teacher(manifest_hash, classes):
    config = read_json(TEACHER_FOLDER / 'experiment_config.json')
    if config['manifest_sha256'] != manifest_hash or config['class_order'] != classes:
        raise ValueError('Teacher manifest/classes mismatch')
    source = ROOT / config['checkpoint_local']
    if digest(source) != config['checkpoint_sha256']:
        raise ValueError('Teacher checkpoint hash mismatch')
    teacher = Classifier(DinoTokens(), np.zeros((16, 1536)), np.zeros(16))
    teacher.load_state_dict(load_file(str(source)), strict=True)
    teacher.eval().requires_grad_(False)
    transform = timm.data.create_transform(**config['preprocessing'], is_training=False)
    return teacher, transform, config


def cache_teacher(config, manifest, device, manifest_hash):
    teacher, transform, teacher_config = load_teacher(manifest_hash, manifest['classes'])
    teacher.to(device)
    val = Images(config, manifest, 'validation', transform)
    scores = evaluate(teacher, val, device)
    with np.load(TEACHER_FOLDER / 'logits.npz', allow_pickle=False) as recorded:
        error = float(np.abs(scores - recorded['validation']).max())
        agreement = float((scores.argmax(1) == recorded['validation'].argmax(1)).mean())
    if error > 1e-3 or agreement != 1:
        raise ValueError('Teacher validation replay failed')
    metadata = {
        'teacher': 'DINOv2-B partial fine-tuning',
        'selected_by': 'highest historical DINOv2 validation Macro-F1; never test',
        'checkpoint_sha256': teacher_config['checkpoint_sha256'],
        'manifest_sha256': manifest_hash, 'class_order': manifest['classes'],
        'recipe': RECIPE, 'rows': [r['path'] for r in split_rows(manifest, 'train')],
        'validation_replay_max_abs_error': error, 'validation_top1_agreement': agreement,
        'source_config': str((TEACHER_FOLDER / 'experiment_config.json').relative_to(ROOT)),
        'logits': 'raw, uncalibrated; softened with T=4 only in KD loss',
        'only_train_targets': True,
    }
    cache_path, meta_path = CACHE / 'teacher_targets.npy', CACHE / 'teacher_targets.json'
    identity = {k: metadata[k] for k in ('checkpoint_sha256', 'manifest_sha256', 'class_order', 'recipe', 'rows')}
    if cache_path.exists() and meta_path.exists():
        old = read_json(meta_path)
        if identity != {k: old[k] for k in identity} or digest(cache_path) != old['targets_sha256']:
            raise ValueError('Teacher target cache identity/hash changed')
        targets = np.load(cache_path, allow_pickle=False)
    else:
        targets = []
        for epoch in range(1, RECIPE['warmup_epochs'] + RECIPE['finetune_epochs'] + 1):
            targets.append(evaluate(teacher, Images(config, manifest, 'train', transform, epoch), device))
            print(json.dumps({'teacher_cache_epoch': epoch, 'of': 18}), flush=True)
        targets = np.stack(targets)
        CACHE.mkdir(parents=True, exist_ok=True)
        np.save(cache_path, targets, allow_pickle=False)
        metadata['targets_sha256'] = digest(cache_path)
        write_json(meta_path, metadata)
    expected = (18, EXPECTED_SPLITS['train'], len(manifest['classes']))
    if targets.shape != expected or not np.isfinite(targets).all():
        raise ValueError('Invalid teacher target shape/values')
    write_json(OUTPUT / 'teacher.json', {**metadata, 'targets_sha256': digest(cache_path), 'target_shape': list(targets.shape)})
    del teacher
    gc.collect()
    if device.type == 'cuda':
        torch.cuda.empty_cache()
    return targets


def kd_loss(student_logits, teacher_logits, labels, class_weights, distilled):
    ce = torch.nn.functional.cross_entropy(student_logits, labels, weight=class_weights)
    if not distilled:
        return ce, ce.detach(), torch.zeros((), device=student_logits.device)
    temperature, alpha = RECIPE['distillation_temperature'], RECIPE['distillation_weight']
    teacher_probabilities = torch.softmax(teacher_logits.detach() / temperature, dim=1)
    kl = torch.nn.functional.kl_div(torch.log_softmax(student_logits / temperature, dim=1),
                                   teacher_probabilities, reduction='batchmean') * temperature**2
    return (1 - alpha) * ce + alpha * kl, ce.detach(), kl.detach()


def initial_probe(model_id, config, manifest, device):
    folder = OUTPUT / model_id
    folder.mkdir(parents=True, exist_ok=True)
    probe_path = folder / 'initial_probe.npz'
    if probe_path.exists():
        return
    torch.manual_seed(SEED)
    encoder, transform, preprocessing, pretrained = load_student(model_id)
    encoder.to(device).eval().requires_grad_(False)
    train, val = [Images(config, manifest, split, transform) for split in ('train', 'validation')]
    train_x, val_x = [evaluate(encoder, ds, device) for ds in (train, val)]
    probe, scores, candidates, selected = fit_probes(train_x, train.labels, val_x, val.labels, manifest['classes'])
    np.savez_compressed(probe_path, coef=probe.coef_, intercept=probe.intercept_, classes=manifest['classes'])
    write_json(folder / 'probe_comparison.json', {
        'models': candidates, 'selected': selected, 'pretrained_sha256': digest(pretrained),
        'preprocessing': preprocessing, 'test_used': False,
        'validation': classification_metrics(val.labels, scores, manifest['classes']),
    })
    del encoder
    gc.collect()
    if device.type == 'cuda':
        torch.cuda.empty_cache()


def train_run(model_id, method, config, manifest, targets, device, manifest_hash):
    run_id = model_id + '_' + method
    folder = OUTPUT / run_id
    if (folder / 'run_summary.json').exists():
        summary = read_json(folder / 'run_summary.json')
        if summary['status'] == 'completed':
            saved = read_json(folder / 'experiment_config.json')
            if saved['recipe'] != RECIPE or saved['manifest_sha256'] != manifest_hash:
                raise ValueError('Completed run identity changed; use a new experiment directory')
            if digest(ROOT / saved['checkpoint_local']) != saved['checkpoint_sha256']:
                raise ValueError('Completed checkpoint changed')
            print('Preserving completed run ' + run_id, flush=True)
            return
        raise ValueError('Incomplete run exists; inspect it before retrying: ' + run_id)
    folder.mkdir(parents=True, exist_ok=True)
    write_json(folder / 'run_summary.json', {'status': 'running', 'model_id': run_id})
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    encoder, transform, preprocessing, pretrained = load_student(model_id)
    probe_path = OUTPUT / model_id / 'initial_probe.npz'
    with np.load(probe_path, allow_pickle=False) as probe:
        if probe['classes'].tolist() != manifest['classes']:
            raise ValueError('Initial probe class order changed')
        model = Classifier(encoder, probe['coef'], probe['intercept']).to(device)
    initial_state_hash_path = CACHE / (model_id + '_initial.safetensors')
    if not initial_state_hash_path.exists():
        save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(initial_state_hash_path))
    # Load same initial checkpoint for both methods, including buffers.
    model.load_state_dict(load_file(str(initial_state_hash_path)), strict=True)
    val = Images(config, manifest, 'validation', transform)
    train = Images(config, manifest, 'train', transform)
    counts = Counter(train.labels)
    weights = torch.tensor([len(train) / (len(manifest['classes']) * counts[i]) for i in range(16)],
                           device=device, dtype=torch.float32)
    checkpoint = CACHE / 'checkpoints' / (run_id + '.safetensors')
    checkpoint.parent.mkdir(parents=True, exist_ok=True)

    def save_best():
        save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(checkpoint))

    metrics = classification_metrics(val.labels, evaluate(model, val, device), manifest['classes'])
    best_key, best_epoch, best_stage = validation_key(metrics), 0, 'initial_probe'
    save_best()
    history = [{'epoch': 0, 'stage': best_stage, 'validation': {k: metrics[k] for k in ('macro_f1', 'top1_accuracy', 'top3_accuracy')}}]
    started, epoch = time.perf_counter(), 0
    for stage, epochs in (('head_warmup', 3), ('finetune', 15)):
        model.encoder.requires_grad_(stage == 'finetune')
        model.head.requires_grad_(True)
        groups = [{'params': model.head.parameters(), 'lr': RECIPE['head_warmup_lr'] if stage == 'head_warmup' else RECIPE['head_finetune_lr']}]
        if stage == 'finetune':
            groups.append({'params': model.encoder.parameters(), 'lr': RECIPE['backbone_lr']})
        optimizer = torch.optim.AdamW(groups, weight_decay=RECIPE['weight_decay'])
        stale = 0
        for _ in range(epochs):
            epoch += 1
            tick = time.perf_counter()
            train.epoch = epoch
            model.train()
            if stage == 'head_warmup':
                model.encoder.eval()
            for module in model.modules():
                if isinstance(module, torch.nn.modules.batchnorm._BatchNorm):
                    module.eval()
            losses = np.zeros(3)
            for images, labels, indices in loader(train, True, epoch):
                optimizer.zero_grad(set_to_none=True)
                scores = model(images.to(device))
                teacher_scores = torch.from_numpy(targets[epoch - 1, indices.numpy()]).to(device) if method == 'distilled' else None
                loss, ce, kl = kd_loss(scores, teacher_scores, labels.to(device), weights, method == 'distilled')
                if not torch.isfinite(loss):
                    raise ValueError('Non-finite training loss')
                loss.backward()
                optimizer.step()
                losses += np.array([float(loss.detach()), float(ce), float(kl)]) * len(labels)
            scores = evaluate(model, val, device)
            metrics = classification_metrics(val.labels, scores, manifest['classes'])
            improved = validation_key(metrics) > best_key
            if improved:
                best_key, best_epoch, best_stage = validation_key(metrics), epoch, stage
                save_best()
                stale = 0
            else:
                stale += 1
            record = {'epoch': epoch, 'stage': stage, 'selected': improved,
                      'training_loss': float(losses[0] / len(train)),
                      'training_ce': float(losses[1] / len(train)), 'training_kl_T2': float(losses[2] / len(train)),
                      'seconds': time.perf_counter() - tick,
                      'validation': {k: metrics[k] for k in ('macro_f1', 'top1_accuracy', 'top3_accuracy')}}
            history.append(record)
            write_json(folder / 'history.json', history)
            print(json.dumps({'model_id': run_id, **record}), flush=True)
            if stage == 'finetune' and stale >= RECIPE['patience']:
                break
        del optimizer
    model.load_state_dict(load_file(str(checkpoint)), strict=True)
    clear_attention_cache(model)
    model.eval().requires_grad_(False)
    val_logits = evaluate(model, val, device)
    temperature, calibration = fit_temperature(val.labels, val_logits)
    threshold, threshold_info = select_threshold(val.labels, softmax(val_logits / temperature, axis=1))
    val_metrics, _ = evaluated_metrics(val.labels, val_logits, manifest['classes'], temperature, threshold)
    if validation_key(val_metrics) != best_key:
        raise ValueError('Restored checkpoint validation differs from selected epoch')
    sample = val[0][0].unsqueeze(0).to(device)
    durations = []
    if device.type == 'cuda':
        torch.cuda.reset_peak_memory_stats()
    with torch.inference_mode():
        for i in range(35):
            if device.type == 'cuda':
                torch.cuda.synchronize()
            tick = time.perf_counter()
            model(sample)
            if device.type == 'cuda':
                torch.cuda.synchronize()
            if i >= 5:
                durations.append((time.perf_counter() - tick) * 1000)
    configuration = {
        **MODELS[model_id], 'model_id': run_id, 'base_model_id': model_id, 'method': method,
        'recipe': RECIPE, 'selected_epoch': best_epoch, 'selected_stage': best_stage,
        'epochs_executed': epoch, 'training_seconds': time.perf_counter() - started,
        'class_order': manifest['classes'], 'manifest_sha256': manifest_hash,
        'split_counts': EXPECTED_SPLITS, 'pretrained_sha256': digest(pretrained),
        'initial_probe_sha256': digest(probe_path), 'initial_state_sha256': digest(initial_state_hash_path),
        'checkpoint_local': str(checkpoint.relative_to(ROOT)), 'checkpoint_sha256': digest(checkpoint),
        'checkpoint_bytes': checkpoint.stat().st_size, 'parameters': sum(p.numel() for p in model.parameters()),
        'preprocessing': preprocessing, 'input_shape': list(sample.shape),
        'temperature': temperature, 'threshold': threshold, 'test_used_for_selection': False,
        'teacher_checkpoint_sha256': read_json(OUTPUT / 'teacher.json')['checkpoint_sha256'] if method == 'distilled' else None,
        'timing': {'desktop_single_image_ms_median': statistics.median(durations),
                   'desktop_single_image_ms_p95': float(np.percentile(durations, 95)),
                   'peak_cuda_allocated_bytes_batch1': torch.cuda.max_memory_allocated() if device.type == 'cuda' else None,
                   'note': 'GTX 1650 FP32 encoder+head; batch1, 5 warmups/30 synchronized samples; excludes preprocessing; not Android.'},
    }
    write_json(folder / 'experiment_config.json', configuration)
    write_json(folder / 'validation_metrics.json', {**val_metrics, 'calibration': calibration, 'threshold_selection': threshold_info})
    # Test decoding happens only after checkpoint, selection and calibration have been persisted.
    test = Images(config, manifest, 'test', transform)
    test_logits = evaluate(model, test, device)
    test_metrics, probabilities = evaluated_metrics(test.labels, test_logits, manifest['classes'], temperature, threshold)
    write_json(folder / 'test_metrics.json', {**test_metrics, 'test_used_for_selection': False})
    np.savez_compressed(folder / 'logits.npz', validation=val_logits, test=test_logits)
    save_confusion(folder / 'confusion_matrix.csv', test_metrics['confusion_matrix'], manifest['classes'])
    save_confusion(folder / 'confusion_matrix_validation.csv', val_metrics['confusion_matrix'], manifest['classes'])
    save_class_metrics(folder / 'per_class_metrics.csv', {'validation': val_metrics, 'test': test_metrics}, manifest['classes'])
    save_predictions(folder / 'predictions_test.csv', test.rows, test.labels, test_logits, probabilities, threshold, manifest['classes'])
    for path in folder.glob('*.csv'):
        path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
    summary = {'status': 'completed', 'model_id': run_id, 'selected_epoch': best_epoch,
               'selected_stage': best_stage, 'experimental_only': True,
               'validation': {k: val_metrics[k] for k in ('top1_accuracy', 'macro_f1', 'top3_accuracy')},
               'test': {k: test_metrics[k] for k in ('top1_accuracy', 'macro_f1', 'top3_accuracy', 'coverage', 'accepted_accuracy')},
               'test_decoded_after_freezing_selection_temperature_threshold': True}
    write_json(folder / 'run_summary.json', summary)
    print(json.dumps(summary), flush=True)
    del model, encoder
    gc.collect()
    if device.type == 'cuda':
        torch.cuda.empty_cache()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate-only', action='store_true')
    parser.add_argument('--models', nargs='+', choices=list(MODELS), default=list(MODELS))
    args = parser.parse_args()
    config = load_config(ROOT / 'config.yaml')
    manifest = load_manifest(config, verify=True)
    classes = validate_manifest(manifest)
    if classes != read_json(ROOT / 'artifacts/classes.json'):
        raise ValueError('Classes differ from integrated bundle')
    before = protected_hashes()
    if args.validate_only:
        print(json.dumps({'valid': True, 'split_counts': EXPECTED_SPLITS, 'protected_files': len(before)}))
        return
    OUTPUT.mkdir(parents=True, exist_ok=True)
    protocol = {'models': MODELS, 'methods': list(METHODS), 'recipe': RECIPE,
                'manifest_sha256': manifest_digest(ROOT / config['prepared_dir'] / 'manifest.json'),
                'class_order': classes, 'split_counts': EXPECTED_SPLITS,
                'teacher_selection': 'DINOv2 with highest historical validation Macro-F1',
                'test_used_for_selection': False, 'test_note': 'Known internal historical test; not independent field validation',
                'android_target_ms': 3000, 'android_device': 'Samsung Galaxy A06',
                'export_policy': 'attempt all 10 runs; no integration into app'}
    protocol_path = OUTPUT / 'protocol.json'
    if protocol_path.exists() and read_json(protocol_path) != protocol:
        raise ValueError('Protocol changed; start a separate experiment instead of overwriting')
    if (OUTPUT / 'isolation_before.json').exists():
        if read_json(OUTPUT / 'isolation_before.json') != before:
            raise ValueError('Protected baseline changed since experiment start')
    else:
        write_json(OUTPUT / 'isolation_before.json', before)
    write_json(protocol_path, protocol)
    torch.set_num_threads(2)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    if not (OUTPUT / 'environment.json').exists():
        write_json(OUTPUT / 'environment.json', {
            'python': platform.python_version(), 'platform': platform.platform(), 'torch': torch.__version__,
            'gpu': torch.cuda.get_device_name(0) if device.type == 'cuda' else None,
            'cuda_runtime': torch.version.cuda,
            'installed_packages': sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions()),
        })
    try:
        targets = cache_teacher(config, manifest, device, protocol['manifest_sha256'])
        for model_id in args.models:
            initial_probe(model_id, config, manifest, device)
            for method in METHODS:
                train_run(model_id, method, config, manifest, targets, device, protocol['manifest_sha256'])
    finally:
        write_json(OUTPUT / 'isolation_after.json', verify_protected(before))


if __name__ == '__main__':
    main()
