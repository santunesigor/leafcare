"""Generate a separate report from experimental evidence, without rewriting old benchmarks."""
import csv
from experiments.distillation import benchmark as b

REPORT = b.ROOT.parent / 'docs/benchmarks/BENCHMARK_DESTILACAO.md'


def collect():
    rows = []
    for model_id in b.MODELS:
        for method in b.METHODS:
            run_id = model_id + '_' + method
            folder = b.OUTPUT / run_id
            summary = b.read_json(folder / 'run_summary.json')
            if summary['status'] != 'completed':
                raise ValueError('Cannot report incomplete training: ' + run_id)
            cfg = b.read_json(folder / 'experiment_config.json')
            mobile = b.read_json(folder / 'mobile_export.json') if (folder / 'mobile_export.json').exists() else {'status': 'not_attempted'}
            row = {'model_id': run_id, 'model': model_id, 'method': method,
                   'parameters': cfg['parameters'], 'selected_epoch': cfg['selected_epoch'],
                   'selected_stage': cfg['selected_stage'], 'epochs_executed': cfg['epochs_executed'],
                   'temperature': cfg['temperature'], 'threshold': cfg['threshold'],
                   'checkpoint_mib': cfg['checkpoint_bytes'] / 1048576,
                   'export_status': mobile['status'], 'export_error': mobile.get('error', ''),
                   'test_used_for_selection': False}
            row.update({split + '_' + metric: value for split in ('validation', 'test') for metric, value in summary[split].items()})
            for key in ('bytes', 'desktop_tflite_ms_median', 'desktop_tflite_ms_p95',
                        'desktop_python_pipeline_ms_median', 'desktop_python_pipeline_ms_p95'):
                row[key] = mobile.get(key, '')
            row['tflite_mib'] = mobile['bytes'] / 1048576 if 'bytes' in mobile else ''
            rows.append(row)
    return sorted(rows, key=lambda r: (r['validation_macro_f1'], r['validation_top1_accuracy'], r['validation_top3_accuracy']), reverse=True)


def main():
    rows = collect()
    with (b.OUTPUT / 'comparison.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)
    before = b.read_json(b.OUTPUT / 'isolation_before.json')
    isolation = b.verify_protected(before)
    teacher = b.read_json(b.OUTPUT / 'teacher.json')
    best = rows[0]
    lines = ['# Benchmark isolado — modelos leves e destilação', '',
        'Rodada de 2026-10-08 na branch `experiment/mobile-models-distillation`, criada após pull da main em `4106004`. Dez treinos efetivamente executados; nenhum candidato substituiu o ensemble do aplicativo.', '',
        f"Melhor configuração pela validação: **{best['model_id']}**, Macro-F1 {best['validation_macro_f1']:.4f}. A escolha não usa o teste. A referência integrada tem Macro-F1 de validação 0,8179 e TFLite de 17,22 MiB; são números históricos, não um retreinamento desta rodada.", '',
        '## Protocolo e isolamento', '',
        '- Dataset existente: 696 imagens, 16 classes, split 489/104/103. SHA256 das imagens auditado; manifesto, grupos, ordem das classes e split preservados.',
        '- Diretórios exclusivos: `machine-learning/experiments/distillation/`, `benchmark_artifacts/distillation/` e cache ignorado `experiments/distillation/.cache/`. Helpers de métricas/manifesto existentes são importados somente para leitura; receitas e saídas antigas não são executadas nem sobrescritas.',
        f"- Proteção por SHA256: {isolation['files_checked']} arquivos de assets, artefatos atuais, manifesto/config e resultados históricos idênticos antes/depois. Evidências em `isolation_before.json`, `isolation_after.json` e `isolation_after_export.json`.",
        '- Checkpoints ImageNet fixados por revisão e SHA256. Cabeça de 16 classes inicializada com probe logístico C=1 (com/sem pesos), selecionado pela validação; cada par usa exatamente o mesmo estado inicial, buffers, preprocessing e augmentations.',
        '- Seed 42; float32; batch 8; AdamW; warmup da cabeça 3 épocas a 1e-3; ajuste até 15 épocas a 1e-4 cabeça/1e-5 encoder; weight decay 1e-4; BatchNorm com estatísticas congeladas; patience 5 no ajuste. Época zero (probe) também pode vencer.',
        '- Augmentations: flips horizontal/vertical p=0,5; rotação ±12° bilinear com fundo branco; brilho/contraste 0,1 e saturação 0,05. Seed por época/imagem; a mesma foto aumentada serve para professor/aluno, seguida do preprocessing oficial de cada checkpoint.',
        '- Seleção por Macro-F1 de validação; desempates Top-1, Top-3 e época anterior. Temperatura minimiza NLL de validação; limiar maximiza cobertura com pelo menos 90% de acerto na validação, ou fallback explicitado em `validation_metrics.json`.',
        '- Checkpoint, configuração, temperatura e limiar persistidos antes de inferir o teste. O teste já foi conhecido em rodadas anteriores: comparação histórica interna, não avaliação independente.', '',
        '## Professor e destilação', '',
        'Professor: DINOv2-B com ajuste parcial existente, escolhido pela maior Macro-F1 de validação entre os DINOv2 registrados (0,8069). Nenhuma seleção pelo Top-1 de teste. O professor fica congelado, fora do APK; somente imagens de treino fornecem alvos de destilação.', '',
        '`loss = 0,5 × CE balanceada + 0,5 × T² × KL(professor || aluno)`, com `T=4` e KL média por batch. Os logits crus do professor são usados, sem aplicar sua temperatura de calibração ao treinamento. A temperatura de destilação e a calibração do aluno são coisas distintas.', '',
        f"Replay do professor nas 104 imagens de validação: concordância Top-1 {teacher['validation_top1_agreement']*100:.0f}%, erro máximo de logits {teacher['validation_replay_max_abs_error']:.3g}. Alvos cacheados com shape {teacher['target_shape']}, identidade de professor/manifesto/receita/caminhos e SHA256; sem alvos de validação/teste usados na perda.", '',
        'O aluno tem o mesmo número de parâmetros com ou sem destilação. A redução em relação ao professor vem da arquitetura pequena; o experimento verifica quanto do desempenho ela consegue aprender. FastViT já usa um checkpoint destilado em ImageNet: o controle significa ausência de **destilação adicional para LeafCare**.', '',
        '## Resultados', '',
        'Tabela ordenada somente pela validação. Top-1/Top-3 em porcentagem; Macro-F1 entre 0 e 1. S = supervisionado; D = destilado.', '',
        '| Modelo | Receita | Parâmetros | Época | Val: Top-1 / F1 / Top-3 | Teste: Top-1 / F1 / Top-3 | Teste: cobertura / acerto aceito |',
        '|---|---|---:|---:|---|---|---|']
    for r in rows:
        lines.append(f"| {r['model']} | {'D' if r['method']=='distilled' else 'S'} | {r['parameters']:,} | {r['selected_epoch']} | {r['validation_top1_accuracy']*100:.2f}% / {r['validation_macro_f1']:.4f} / {r['validation_top3_accuracy']*100:.2f}% | {r['test_top1_accuracy']*100:.2f}% / {r['test_macro_f1']:.4f} / {r['test_top3_accuracy']*100:.2f}% | {r['test_coverage']*100:.2f}% / {r['test_accepted_accuracy']*100:.2f}% |")
    lines += ['', '## Efeito pareado da destilação', '',
        'D menos S na mesma arquitetura. Delta Top-1 em pontos percentuais; a conclusão se baseia primeiro na validação.', '',
        '| Modelo | Δ Macro-F1 validação | Δ Top-1 validação | Δ Macro-F1 teste | Δ Top-1 teste |',
        '|---|---:|---:|---:|---:|']
    for model_id in b.MODELS:
        s, d = [next(r for r in rows if r['model']==model_id and r['method']==method) for method in b.METHODS]
        lines.append(f"| {model_id} | {d['validation_macro_f1']-s['validation_macro_f1']:+.4f} | {(d['validation_top1_accuracy']-s['validation_top1_accuracy'])*100:+.2f} | {d['test_macro_f1']-s['test_macro_f1']:+.4f} | {(d['test_top1_accuracy']-s['test_top1_accuracy'])*100:+.2f} |")
    improved = []
    for model_id in b.MODELS:
        s, d = [next(r for r in rows if r['model']==model_id and r['method']==method) for method in b.METHODS]
        if d['validation_macro_f1'] > s['validation_macro_f1']:
            improved.append(model_id)
    lines += ['', f"Nesta receita fixa, a destilação melhorou a Macro-F1 de validação em {len(improved)}/5 arquiteturas: " +
              (', '.join(improved) if improved else 'nenhuma') + '. Isso não demonstra um ganho geral da técnica nem substitui múltiplas seeds/avaliação de campo.']
    if best['validation_macro_f1'] <= .8179:
        lines += ['', 'Nenhuma configuração desta rodada superou a Macro-F1 de validação histórica do ensemble atual (0,8179). O bundle atual permanece como referência; não há proposta de substituição automática com base no teste conhecido.']
    initial_winners = [r['model_id'] for r in rows if r['selected_epoch'] == 0]
    if initial_winners:
        lines += ['', 'Checkpoint inicial venceu em: ' + ', '.join('`' + name + '`' for name in initial_winners) +
                  '. Os treinos foram executados e seus históricos registrados, mas os pesos treinados não foram selecionados. Uma receita D com época zero selecionada não contém destilação adicional no checkpoint final.']
    lines += ['', '## Exportações experimentais e custo', '',
        'TFLite float32 NHWC RGB 0–255, saída de 16 probabilidades calibradas. Normalização/softmax/temperatura no grafo; resize/crop oficiais fora. Isso não comprova compatibilidade com o preprocessing Android integrado.', '',
        'FastViT/EfficientViT são reparametrizados após restaurar os pesos selecionados. A fusão e a conversão são conferidas nas 104 imagens de validação. Conversão exige Top-1 idêntico e erro máximo de probabilidades ≤0,0001, incluindo replay contra logits do treino. Exportações falhas ficam registradas e não são consideradas aprovadas.', '',
        '| Configuração | Exportação | TFLite MiB | CPU inferência mediana / P95 ms | Pipeline Python mediana / P95 ms |',
        '|---|---|---:|---|---|']
    for r in rows:
        if r['export_status'] == 'completed':
            lines.append(f"| {r['model_id']} | Aprovada | {r['tflite_mib']:.2f} | {r['desktop_tflite_ms_median']:.2f} / {r['desktop_tflite_ms_p95']:.2f} | {r['desktop_python_pipeline_ms_median']:.2f} / {r['desktop_python_pipeline_ms_p95']:.2f} |")
        else:
            lines.append(f"| {r['model_id']} | {r['export_status']} | — | — | — |")
    failures = [r for r in rows if r['export_status'] == 'failed']
    for r in failures:
        error = r['export_error'].replace('\n', ' ')[:650]
        lines += ['', f"Falha `{r['model_id']}`: {error}"]
    lines += ['', 'Tempos de treino/perfil PyTorch: GTX 1650 4 GiB. TFLite: Intel Core i5-13400 CPU, duas threads, cinco aquecimentos e 30 medições, após concluir os treinos. Pipeline Python inclui decode, preprocessing, inferência e Top-3; exclui Room/UI. Hardware, versões e hashes estão nos artefatos. Resultados desktop não substituem medição Android.', '',
        '**Galaxy A06: medição de até três segundos permanece pendente.** Nenhum candidato foi instalado no app nesta rodada. O APK debug entregue contém somente o ensemble atual.', '',
        '## Limitações e reprodução', '',
        '- Seed única, treino pequeno, validação com 104 imagens já usada em buscas anteriores; diferenças pequenas não demonstram superioridade estatística. Algumas classes têm suporte de uma ou duas imagens no teste.',
        '- Destilação pode transmitir erros do professor; não garante manter sua acurácia. Macro-F1, métricas por classe, calibração/cobertura e custo devem ser considerados juntos.',
        '- MobileNetV3 timm e receita PyTorch desta rodada diferem do MobileNetV3 Keras integrado; a comparação causal é o par S/D desta rodada, não a diferença contra o histórico.',
        '- Nenhuma validação externa de campo ou avaliação de imagens fora das 16 classes. Pontuações não representam certeza agronômica.', '',
        'Comandos e ambientes: [README do experimento](../../machine-learning/experiments/distillation/README.md). Artefatos: [distillation](../../machine-learning/benchmark_artifacts/distillation/), incluindo protocolo, environment, probes, históricos, logits, confusões, métricas por classe, previsões, calibração, hashes e paridade. Pesos e TFLites ficam somente no cache local ignorado; os resultados antigos permanecem intactos.', '',
        '## Fontes primárias', '',
        '- [Destilação de conhecimento — Hinton, Vinyals e Dean](https://arxiv.org/abs/1503.02531).',
        '- [DINOv2 — implementação Meta](https://github.com/facebookresearch/dinov2).',
        '- [MobileNetV4 Small — checkpoint timm](https://huggingface.co/timm/mobilenetv4_conv_small.e2400_r224_in1k).',
        '- [FastViT — implementação Apple](https://github.com/apple-aiml-research/ml-fastvit).',
        '- [EfficientViT M — Microsoft/Cream](https://github.com/microsoft/Cream/tree/main/EfficientViT). Esta família é a MSRA, distinta de outras arquiteturas chamadas EfficientViT.',
        '- [LiteRT Torch — conversão](https://github.com/google-ai-edge/litert-torch).', '']
    validation_path = b.OUTPUT / 'delivery_validation.json'
    if validation_path.exists():
        validation = b.read_json(validation_path)
        if validation.get('status') == 'completed':
            index = lines.index('## Fontes primárias')
            lines[index:index] = ['## Validações da entrega', '',
                f"- Pytest dos experimentos de destilação, vision e DINOv2: {validation['python_tests_passed']} testes passaram, sem skips. Inclui replay das métricas/épocas, inicializações pareadas e matrizes de paridade/hashes das dez exportações.",
                '- OpenSpec: validação atual e arquivada com `--strict --no-interactive`, sem falhas.',
                '- Android: `assembleDebug` e `verifyModelAssets testDebugUnitTest` concluídos. `testDebugUnitTest` estava UP-TO-DATE; não é uma nova execução dos testes unitários.',
                '- Hash do TFLite dentro do APK idêntico ao baseline protegido; artefato e hash do APK em `android_validation.json`. ADB sem aparelhos conectados.',
                '- Checkpoints e TFLites experimentais ignorados pelo Git; nenhum asset do Android substituído.', '']
    REPORT.write_text('\n'.join(lines))
    print(str(REPORT))


if __name__ == '__main__':
    main()
