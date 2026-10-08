import hashlib
from pathlib import Path
import numpy as np
import pytest
from leafcare.preprocessing import resize_distilled_rgb, preprocess, DISTILLED_RESIZE


def test_pillow_synthetic_golden():
    fixture = Path(__file__).resolve().parents[2] / 'android-app/app/src/test/resources/distilled_preprocess_golden.properties'
    props = dict(line.split('=', 1) for line in fixture.read_text().splitlines() if line and not line.startswith('#'))
    for name in props['cases'].split(','):
        width, height = map(int, props[f'{name}.size'].split(','))
        y, x = np.indices((height, width))
        rgb = np.stack([(x*37+y*13)%256, (x*11+y*43)%256, (x*53+y*7)%256], axis=-1)
        if name == 'alpha':
            a = ((x*17+y*31)%256)[..., None]
            rgb = (rgb*a+255*(255-a)+127)//255
        result = resize_distilled_rgb(rgb.astype(np.uint8))
        assert result.shape == (224,224,3)
        assert hashlib.sha256(result.astype(np.uint8).tobytes()).hexdigest() == props[f'{name}.sha256']


def test_invalid_preprocessing_contract(tmp_path):
    from PIL import Image
    image = tmp_path / 'test.png'
    Image.new('RGB', (10,20)).save(image)
    with pytest.raises(ValueError):
        preprocess(image, resize='unknown')
    with pytest.raises(ValueError):
        preprocess(image, size=128, resize=DISTILLED_RESIZE)


def test_legacy_training_cannot_replace_current_bundle(tmp_path, monkeypatch):
    import json
    from legacy import train
    output = tmp_path / 'artifacts'
    output.mkdir()
    metadata = output / 'model_metadata.json'
    metadata.write_text(json.dumps({'architecture': 'MobileNetV4SmallDistilled'}))
    monkeypatch.setattr(train, 'load_manifest', lambda config: {})
    monkeypatch.setattr(train, 'location', lambda config, key: output)
    original = metadata.read_bytes()
    with pytest.raises(ValueError, match='output_dir separado'):
        train.train({})
    assert metadata.read_bytes() == original
