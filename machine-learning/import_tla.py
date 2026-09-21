"""Importa TV3 bruto; evita misturar fragmentos e versões processadas no teste."""
import argparse,csv,hashlib,json,re
from collections import Counter
from pathlib import Path,PurePosixPath
from zipfile import ZipFile
import yaml
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tla',type=Path,required=True);parser.add_argument('--ttdd',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=Path('data/raw'));args=parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()): parser.error('Destino não vazio.')
    with ZipFile(args.ttdd) as z:
        taxonomy=z.read('TTDD/class_map.yaml');mapping={str(x['tv3_folder']):x['slug'] for x in yaml.safe_load(taxonomy)['classes']}
        derived=[n for n in z.namelist() if '/.cache/' not in n and PurePosixPath(n).suffix.lower() in {'.jpg','.jpeg','.png'}]
    Path('tla_class_map.yaml').write_bytes(taxonomy);rows=[]
    with ZipFile(args.tla) as z:
        for info in sorted(z.infolist(),key=lambda x:x.filename):
            p=PurePosixPath(info.filename).parts
            if len(p)!=4 or p[:2]!=('TLA','TV3') or PurePosixPath(p[-1]).suffix.lower() not in {'.jpg','.jpeg','.png'}:continue
            if '\\' in info.filename or info.file_size>30_000_000 or p[2] not in mapping:raise ValueError('Entrada inválida')
            slug=mapping[p[2]];path=args.output/slug/p[-1]
            if path.exists():raise ValueError('Nome duplicado')
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(info))
            match=re.search(r'IMG[_ ]?(\d+)',path.stem,re.I)
            rows.append({'path':path.relative_to(args.output).as_posix(),'group_id':slug+'_'+(match.group(1) if match else path.stem)})
    counts=Counter(r['path'].split('/')[0] for r in rows)
    if set(counts)!=set(mapping.values()):raise ValueError('Classes ausentes')
    with (args.output.parent/'groups.csv').open('w',newline='',encoding='utf-8') as stream:
        writer=csv.DictWriter(stream,fieldnames=['path','group_id']);writer.writeheader();writer.writerows(rows)
    report={'sources':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [args.tla,args.ttdd]},'selection':'Somente TV3 bruto. TV6 e TTDD derivados excluídos para reduzir vazamento.', 'counts':dict(counts),'total':len(rows),'ttdd_images_audited':len(derived),'ttdd_synthetic':sum('_aug' in n or '_paste' in n for n in derived),'limitations':'Grupos por classe + IMG de origem, complementados por dHash. Sem identificação de planta/propriedade; não comprova independência de campo.'}
    (args.output.parent/'import_report.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
