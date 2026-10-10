from pathlib import Path
from collections import defaultdict
import hashlib,json
import argparse,subprocess
parser=argparse.ArgumentParser(description='Reproduce bundled offline Mozc lexicon; see docs/japanese-dictionary-2026-10-10.md')
parser.add_argument('--mozc',type=Path,required=True)
args=parser.parse_args()
base=Path(__file__).resolve().parents[1]
p=args.mozc/'src/data/dictionary_oss'
commit=subprocess.check_output(['git','-C',str(args.mozc),'rev-parse','HEAD'],text=True).strip()
assert commit=='18e76511af9371a9a4409b1d13c3e30884b53e9c', 'Use pinned source commit'

r=defaultdict(dict)
pos={int(line.split(' ',1)[0]):line.split(' ',1)[1].strip() for line in (p/'id.def').read_text().splitlines()}
for f in sorted(p.glob('dictionary*.txt')):
 for line in f.open():
  a=line.rstrip('\n').split('\t')
  if len(a)<5:continue
  key,left,right,cost,surface=a[:5]; cost=int(cost)
  proper="固有名詞" in pos[int(left)] or "固有名詞" in pos[int(right)]
  if len(key)>24 or len(surface)>32 or not all('ぁ'<=c<='ゖ' or c=='ー' for c in key):continue
  if (not proper and cost<=7500) or (proper and cost<=4500) or len(surface)==1 and '\u3400'<=surface<='\u9fff':
   ranked=cost+(4000 if proper else 0)
   r[key][surface]=min(ranked,r[key].get(surface,99999))
# Pronunciation aliases keep Japanese spelling available when a Hangul long vowel is omitted.
def short_reading(key):
 out=''
 for ch in key:
  prev=out[-1:] if out else ''
  if ch=='ー' or (ch=='う' and prev in 'うくぐすずつづぬふぶぷむゆゅるおこごそぞとどのほぼぽもよょろを') or (ch=='い' and prev in 'いきぎしじちぢにひびぴみりえけげせぜてでねへべぺめれ') or (ch=='あ' and prev in 'あかがさざただなはばぱまやゃらわ') or (ch=='お' and prev in 'おこごそぞとどのほぼぽもよょろを'):
   continue
  out+=ch
 return out
for key,v in list(r.items()):
 short=short_reading(key)
 if short and short!=key:
  for surface,cost in v.items():
   if cost<=7500:r[short][surface]=min(cost+500,r[short].get(surface,99999))
# Supplement modern standard glyph and two rare Jouyou entries absent from cost-filtered data.
r['じゅうてん']['充塡']=4000
r['ぎょじ']['御璽']=4000
r['ちん']['朕']=1000
out=[(k,s,c) for k,v in sorted(r.items()) for s,c in sorted(v.items(),key=lambda x:(x[1],x[0]))[:8]]
f=base/'shared/dictionaries/ja_lexicon.tsv'
f.write_text(''.join(f'{k}\t{s}\t{c}\n' for k,s,c in out))
license=(args.mozc/'LICENSE').read_text()+'\n\n'+(p/'README.txt').read_text()
(base/'shared/dictionaries/ja_lexicon.LICENSE.txt').write_text(license.rstrip()+"\n")
meta={'source':'https://github.com/google/mozc','commit':'18e76511af9371a9a4409b1d13c3e30884b53e9c','general_pos_cost_limit':7500,'proper_noun_cost_max':4500,'proper_noun_penalty':4000,'single_kanji_included':True,'long_vowel_alias_cost_max':7500,'max_surfaces_per_reading':8,'readings':len(r),'entries':len(out),'unique_kanji':len(set(c for k,s,v in out for c in s if '\u3400'<=c<='\u9fff')),'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()}
(base/'shared/dictionaries/ja_lexicon.manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
meta['joyo_inventory_count']=2136
meta['supplements']={'source':'https://www.bunka.go.jp/seisaku/kokugo_nihongo/kokugo_shisaku/joyokanjihyo_sakuin/','surfaces':['充塡','御璽','朕']}
(base/'shared/dictionaries/ja_lexicon.manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print(meta)
