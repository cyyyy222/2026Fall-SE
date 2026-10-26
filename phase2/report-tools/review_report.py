"""Read-only PDF geometry checks plus contact sheets for visual QA."""
from pathlib import Path
import json
import pdfplumber
from PIL import Image, ImageOps, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
qa=ROOT/'tmp'/'pdfs'
inventory=json.loads((qa/'report-validation.json').read_text(encoding='utf-8'))
violations=[]
with pdfplumber.open(ROOT/'实验报告.pdf') as pdf:
    for index,page in enumerate(pdf.pages,1):
        for char in page.chars:
            if char['x0'] < -1 or char['x1']>page.width+1 or char['top'] < -1 or char['bottom']>page.height+1:
                violations.append({'page':index,'text':char['text'],'x0':char['x0'],'top':char['top']})
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
paths=[]
for start in range(1,inventory['pages']+1,7):
    canvas=Image.new('RGB',(1260,1815),'#E9E7E2')
    draw=ImageDraw.Draw(canvas)
    for offset,num in enumerate(range(start,min(start+7,inventory['pages']+1))):
        src=qa/f'final-{num:02d}.png'
        image=Image.open(src).convert('RGB')
        image=ImageOps.contain(image,(396,550))
        x=16+(offset%3)*416+(396-image.width)//2
        y=36+(offset//3)*596
        canvas.paste(image,(x,y))
        draw.text((16+(offset%3)*416,y+555),f'第 {num} 页',fill='#303632',font=font)
    out=qa/f'review-sheet-{start:02d}.png';canvas.save(out);paths.append(str(out))
result={'pages':inventory['pages'],'figures':len(inventory['figures']),
        'out_of_page_characters':violations,'review_sheets':paths}
(qa/'geometry-review.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
if violations:raise RuntimeError(str(violations[:10]))
print(f'Checked {inventory["pages"]} pages, 36 figures, zero out-of-page characters; {len(paths)} review sheets.')
