"""Build the Chinese design report from its Markdown source and original PNGs.

Body pages are A4; figure sheets use A3 to retain readable UI/UML labels.
Run with the bundled Python (reportlab, Pillow, pypdf).
"""
from pathlib import Path
from html import escape
import re
import json
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4, A3, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, PageBreak,
    NextPageTemplate, Table, TableStyle, Image, KeepTogether,
)
from PIL import Image as PILImage
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / '实验报告.md'
OUTPUT = ROOT / '实验报告.pdf'
QA = ROOT / 'tmp' / 'pdfs'
QA.mkdir(parents=True, exist_ok=True)

pdfmetrics.registerFont(TTFont('CJK', 'C:/Windows/Fonts/msyh.ttc', subfontIndex=0))
pdfmetrics.registerFont(TTFont('CJK-Bold', 'C:/Windows/Fonts/msyhbd.ttc', subfontIndex=0))
pdfmetrics.registerFontFamily('CJK', normal='CJK', bold='CJK-Bold', italic='CJK', boldItalic='CJK-Bold')

INK = colors.HexColor('#303632')
PRIMARY = colors.HexColor('#A97773')
SAGE = colors.HexColor('#637561')
MUTED = colors.HexColor('#73786F')
RULE = colors.HexColor('#DFDED8')
MARGIN = 18 * mm

styles = {
    'body': ParagraphStyle('body', fontName='CJK', fontSize=10.5, leading=16.2,
                           textColor=INK, wordWrap='CJK', spaceAfter=6),
    'h1': ParagraphStyle('h1', fontName='CJK-Bold', fontSize=21, leading=31,
                         textColor=PRIMARY, wordWrap='CJK', spaceBefore=8,
                         spaceAfter=16, keepWithNext=True),
    'h2': ParagraphStyle('h2', fontName='CJK-Bold', fontSize=14, leading=23,
                         textColor=SAGE, wordWrap='CJK', spaceBefore=12,
                         spaceAfter=9, keepWithNext=True),
    'h3': ParagraphStyle('h3', fontName='CJK-Bold', fontSize=11.5, leading=19,
                         textColor=INK, wordWrap='CJK', spaceBefore=9,
                         spaceAfter=7, keepWithNext=True),
    'cell': ParagraphStyle('cell', fontName='CJK', fontSize=8.7, leading=14.5,
                           textColor=INK, wordWrap='CJK'),
    'figureheading': ParagraphStyle('figureheading', fontName='CJK-Bold', fontSize=18,
                                    leading=27, textColor=SAGE, wordWrap='CJK', spaceAfter=12),
    'figurenote': ParagraphStyle('figurenote', fontName='CJK', fontSize=11.2,
                                 leading=18, textColor=INK, wordWrap='CJK', spaceAfter=9),
    'caption': ParagraphStyle('caption', fontName='CJK', fontSize=10,
                              leading=16, textColor=MUTED, alignment=TA_CENTER, spaceAfter=10),
}

def inline(s):
    s = escape(s.strip())
    s = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', s)
    s = re.sub(r'`(.+?)`', r'\1', s)
    return s

def paragraph(s, kind='body'):
    return Paragraph(inline(s), styles[kind])

def header_footer(canvas, doc):
    width, height = doc.pageTemplate.pagesize or doc.pagesize
    canvas.saveState()
    canvas.setFont('CJK', 8.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN, height - 10 * mm, '软件设计与建模  /  活动票务与报名系统')
    canvas.setStrokeColor(RULE)
    canvas.line(MARGIN, height - 13 * mm, width - MARGIN, height - 13 * mm)
    canvas.drawString(MARGIN, 9 * mm, '241220089  蔡杨杨  |  设计报告初稿 v1')
    canvas.drawRightString(width - MARGIN, 9 * mm, str(doc.page))
    canvas.restoreState()

class ReportDocument(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(str(filename), pagesize=A4, leftMargin=MARGIN,
                         rightMargin=MARGIN, topMargin=MARGIN, bottomMargin=MARGIN,
                         title='实验二：软件设计与建模 - 活动票务与报名系统',
                         author='蔡杨杨', subject='主要功能、UML架构设计、各模块UI及功能说明')
        self.bookmark_count = 0
        for name, size in [('Body', A4), ('FigurePortrait', A3), ('FigureLandscape', landscape(A3))]:
            w, h = size
            frame = Frame(MARGIN, MARGIN, w - 2*MARGIN, h - 2*MARGIN,
                          leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0,
                          id=name+'Frame')
            self.addPageTemplates(PageTemplate(id=name, frames=[frame], pagesize=size,
                                                onPage=header_footer))

    def afterFlowable(self, flowable):
        if hasattr(flowable, 'outline_title'):
            key = 'heading-' + str(self.bookmark_count)
            self.bookmark_count += 1
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(flowable.outline_title, key, level=flowable.outline_level, closed=False)

story = []
mode = 'Body'
figures = []

def switch_layout(target):
    global mode
    if mode == target:
        if story and not isinstance(story[-1], PageBreak):
            story.append(PageBreak())
    else:
        if story and isinstance(story[-1], PageBreak):
            story.pop()
        story.extend([NextPageTemplate(target), PageBreak()])
    mode = target

def add_heading(s, level, figure=False):
    p = paragraph(s, 'figureheading' if figure else 'h1' if level == 1 else 'h2' if level == 2 else 'h3')
    # Only the three numbered chapters are top-level outline nodes.
    if re.match(r'^[123] ', s):
        p.outline_title, p.outline_level = s, 0
    elif re.match(r'^[123]\.\d+ ', s):
        p.outline_title, p.outline_level = s, 1
    story.append(p)

def add_table(lines):
    matrix = [[part.strip() for part in line.strip().strip('|').split('|')] for line in lines]
    matrix = [r for r in matrix if not all(re.match(r'^:?-+:?$', c.replace(' ','')) for c in r)]
    width = A4[0] - 2*MARGIN
    count = len(matrix[0])
    weights = [0.18, 0.48, 0.34] if count == 3 else [1/count]*count
    if matrix[0][0] == '角色': weights = [0.15,0.44,0.41]
    data = [[paragraph(c, 'cell') for c in r] for r in matrix]
    table = Table(data, colWidths=[width*w for w in weights], repeatRows=1, hAlign='LEFT')
    table.setStyle(TableStyle([
        ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#EAF0E8')),
        ('VALIGN',(0,0),(-1,-1),'TOP'),
        ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#F8F7F3')]),
        ('LINEBELOW',(0,0),(-1,0),0.6,RULE),
        ('BOTTOMPADDING',(0,0),(-1,-1),8),('TOPPADDING',(0,0),(-1,-1),8),
        ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
    ]))
    story.extend([table,Spacer(1,10)])

def add_figure(block, layout):
    target = 'FigurePortrait' if layout == 'portrait' else 'FigureLandscape'
    switch_layout(target)
    heading = next(line.lstrip('#').strip() for line in block if line.startswith('#'))
    image_line = next(line for line in block if line.startswith('!['))
    match = re.match(r'!\[(.*?)\]\((.*?)\)',image_line)
    caption, relative = match.groups()
    image_path = ROOT / relative
    if not image_path.is_file(): raise FileNotFoundError(image_path)
    paragraphs=[]; buffer=[]
    for line in block:
        if not line.strip():
            if buffer: paragraphs.append(''.join(buffer));buffer=[]
        elif not line.startswith(('#','![')):
            buffer.append(line)
    if buffer: paragraphs.append(''.join(buffer))
    size = A3 if layout == 'portrait' else landscape(A3)
    avail_width, avail_height = size[0]-2*MARGIN,size[1]-2*MARGIN
    heading_p = paragraph(heading,'figureheading')
    heading_p.outline_title, heading_p.outline_level = heading,1
    notes = [paragraph(p,'figurenote') for p in paragraphs]
    cap = paragraph(caption,'caption')
    fixed = sum(f.wrap(avail_width,avail_height)[1]+f.getSpaceAfter()+f.getSpaceBefore()
                for f in [heading_p,cap,*notes]) + 12*mm
    with PILImage.open(image_path) as img: iw,ih=img.size
    image_limit = avail_height - fixed
    if relative.startswith('UI界面/'):
        image_limit = min(image_limit,215*mm)
    scale=min(avail_width/iw,image_limit/ih)
    image=Image(str(image_path),width=iw*scale,height=ih*scale)
    image.hAlign='CENTER'
    story.append(KeepTogether([heading_p,image,Spacer(1,8),cap,*notes]))
    figures.append({'caption':caption,'path':relative,'layout':layout,'source_width':iw,
                    'source_height':ih,'display_width_mm':round(iw*scale/mm,1),
                    'display_height_mm':round(ih*scale/mm,1)})

lines=SOURCE.read_text(encoding='utf-8').splitlines()
i=0;buffer=[]

def flush():
    global buffer, mode
    if not buffer:return
    if mode!='Body':switch_layout('Body')
    story.append(paragraph(''.join(buffer)));buffer=[]

while i<len(lines):
    line=lines[i].strip()
    if line.startswith('<!-- diagram-page:') or line=='<!-- ui-page -->':
        flush()
        layout = 'landscape' if line=='<!-- ui-page -->' or ':landscape' in line else 'portrait'
        block=[];i+=1
        while i<len(lines) and lines[i].strip()!='<!-- /figure-page -->':
            block.append(lines[i]);i+=1
        add_figure(block,layout);i+=1;continue
    if line=='<!-- pagebreak -->':
        flush()
        if story and not isinstance(story[-1],PageBreak):story.append(PageBreak())
    elif not line:flush()
    elif line.startswith('#'):
        flush()
        if mode!='Body':switch_layout('Body')
        m=re.match(r'^(#+)\s+(.*)$',line);add_heading(m.group(2),len(m.group(1)))
    elif line.startswith('|'):
        flush();table_lines=[]
        while i<len(lines) and lines[i].strip().startswith('|'):
            table_lines.append(lines[i]);i+=1
        add_table(table_lines);continue
    elif line.startswith('<!--'):pass
    else:buffer.append(line)
    i+=1
flush()
while story and isinstance(story[-1],PageBreak):story.pop()
ReportDocument(OUTPUT).build(story)

reader=PdfReader(str(OUTPUT))
full_text='\n'.join(page.extract_text() or '' for page in reader.pages)
expected=[f'图2-{n}' for n in range(1,9)]+[f'图3-{n}' for n in range(1,29)]
missing=[s for s in expected if s not in full_text]
if missing:raise RuntimeError('Missing figures in PDF: '+str(missing))
if len(figures)!=36:raise RuntimeError('Expected 36 original image figures')
pages=[]
for number,page in enumerate(reader.pages,1):
    text=page.extract_text() or ''
    resources=page.get('/Resources',{})
    xobjects=resources.get('/XObject',{})
    if hasattr(xobjects,'get_object'):xobjects=xobjects.get_object()
    image_count=sum(1 for obj in xobjects.values() if obj.get_object().get('/Subtype')=='/Image')
    pages.append({'page':number,'width_pt':round(float(page.mediabox.width),2),
                  'height_pt':round(float(page.mediabox.height),2),
                  'text_characters':len(text),'images':image_count,
                  'first_text':text[:100]})
    if image_count==0 and number>1 and len(text)<180:
        raise RuntimeError(f'Almost empty/orphan text page: {number}')
    if float(page.mediabox.width)>800 and image_count==0:
        raise RuntimeError(f'Figure notes separated from figure: {number}')
result={'source':SOURCE.name,'output':OUTPUT.name,'pages':len(reader.pages),
        'figures':figures,'missing_figures':missing,'page_inventory':pages}
(QA/'report-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'pages':len(reader.pages),'figures':len(figures),'bytes':OUTPUT.stat().st_size,
                  'missing_figures':missing,'pdf':str(OUTPUT)},ensure_ascii=False))
