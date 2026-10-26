// Local HTML adapter for the shared Figma drawing script, used when MCP export quota is exhausted.
// This does not access Figma, impersonate its API or execute network requests.
// The design script is interpreted into editable DOM text, Flexbox and SVG art.
(function(){
 let next=0;const nodes=new Map();
 function color(p){if(!p)return'transparent';if(p.hex)return p.hex;const c=p.color||{};return `rgb(${(c.r||0)*255},${(c.g||0)*255},${(c.b||0)*255})`;}
 class DesignNode{
  constructor(type='FRAME',tag='div'){this.type=type;this.id='local-'+(++next);this.el=document.createElement(tag);this.el.dataset.node=this.id;this.el.style.boxSizing='border-box';this.el.style.flexShrink='0';this.children=[];this.parent=null;this._={layoutMode:'NONE'};nodes.set(this.id,this);
   return new Proxy(this,{get:(t,k)=>{if(k==='width')return t.el.getBoundingClientRect().width;if(k==='height')return t.el.getBoundingClientRect().height;if(k==='x')return parseFloat(t.el.style.left)||0;if(k==='y')return parseFloat(t.el.style.top)||0;if(k in t)return t[k];return t._[k];},set:(t,k,v)=>{if(['parent','children','id','type'].includes(k)){t[k]=v;return true;}t._[k]=v;const s=t.el.style;
    if(k==='name')t.el.dataset.name=v;
    else if(k==='x'||k==='y'){s.position='absolute';s[k==='x'?'left':'top']=v+'px';t._.positioned=true;}
    else if(k==='characters'){t.el.textContent=v;s.whiteSpace='pre-wrap';s.overflowWrap='break-word';}
    else if(k==='fontName')s.fontFamily=v.family==='Noto Serif SC'?'"Noto Serif SC", "SimSun", serif':'"Noto Sans SC", "Microsoft YaHei", sans-serif',s.fontWeight=['Bold','SemiBold'].includes(v.style)?'700':v.style==='Medium'?'500':'400';
    else if(k==='fontSize')s.fontSize=v+'px';
    else if(k==='lineHeight')s.lineHeight=v.unit==='PERCENT'?(v.value/100):v.value+'px';
    else if(k==='fills'){const col=color(v[0]);if(t.type==='TEXT')s.color=col;else s.background=col;}
    else if(k==='strokes')s.borderColor=color(v[0]),s.borderStyle=v.length?'solid':'none',s.borderWidth=v.length?'1px':'0';
    else if(k==='strokeWeight')s.borderWidth=v+'px';
    else if(k==='cornerRadius')s.borderRadius=v+'px';
    else if(k==='clipsContent')s.overflow=v?'hidden':'visible';
    else if(k==='visible')s.display=v?(t._.layoutMode!=='NONE'?'flex':'block'):'none';
    else if(k==='layoutMode'){s.display=v==='NONE'?'block':'flex';s.flexDirection=v==='HORIZONTAL'?'row':'column';}
    else if(k==='primaryAxisSizingMode'){if(t._.layoutMode==='VERTICAL')s.height=v==='AUTO'?'auto':s.height;else s.width=v==='AUTO'?'max-content':s.width;}
    else if(k==='counterAxisSizingMode'){if(t._.layoutMode==='HORIZONTAL')s.height=v==='AUTO'?'auto':s.height;else s.width=v==='AUTO'?'max-content':s.width;}
    else if(k==='itemSpacing')s.gap=v+'px';
    else if(/^padding(Top|Bottom|Left|Right)$/.test(k))s[k]=v+'px';
    else if(k==='textAlignHorizontal')s.textAlign=String(v).toLowerCase();
    return true;}});
  }
  appendChild(n){if(n.parent){n.parent.children=n.parent.children.filter(c=>c!==n);}this.children.push(n);n.parent=this;if(!n._.positioned)n.el.style.position='relative';this.el.appendChild(n.el);return n;}
  resize(w,h){this.el.style.width=w+'px';this.el.style.height=this.type==='TEXT'?'auto':h+'px';}
  findAll(fn=()=>true){const out=[];for(const n of this.children){if(fn(n))out.push(n);out.push(...n.findAll(fn));}return out;}
  findAllWithCriteria({types}){return this.findAll(n=>types.includes(n.type));}
  setBoundVariable(){ }
  async setReactionsAsync(){ }
 }
 const page=new DesignNode('PAGE');page.id='0:1';document.body.appendChild(page.el);
 function create(type='FRAME',tag='div'){const n=new DesignNode(type,tag);page.appendChild(n);return n;}
 function txt(name,value){const t=create('TEXT');t.name=name;t.characters=value;t.fontName={family:'Noto Sans SC',style:'Regular'};t.fontSize=15;t.lineHeight={unit:'PERCENT',value:150};return t;}
 const buttonComponent={name:'Variant=Primary, State=Default, Size=Medium',createInstance(){const b=create('INSTANCE');b.layoutMode='HORIZONTAL';b.el.style.justifyContent='center';b.el.style.alignItems='center';b.appendChild(txt('Button','Button'));b.el.style.cursor='pointer';return b;}};
 const inputComponent={name:'State=Default, Value Type=Default',createInstance(){const f=create('INSTANCE');f.layoutMode='VERTICAL';f.itemSpacing=7;const label=txt('Label','Label');f.appendChild(label);const input=create();input.name='Input';input.layoutMode='HORIZONTAL';input.el.style.padding='10px 14px';input.el.style.border='1px solid #DFDED8';input.el.style.borderRadius='6px';input.el.style.background='#FFFEFC';input.el.style.height='42px';input.el.style.width='100%';input.el.style.alignItems='center';input.appendChild(txt('Value','Value'));f.appendChild(input);return f;}};
 const variableById=new Map();
 window.createLocalDesignAPI=function(state){for(const [k,id]of Object.entries(state.variables))variableById.set(id,{key:k});return{
  currentPage:page,root:{children:[page]},
  async getNodeByIdAsync(id){return id==='0:1'?page:nodes.get(id)||null;},async setCurrentPageAsync(){},async loadFontAsync(){},
  createFrame(){return create();},createAutoLayout(dir){const n=create();n.layoutMode=dir||'HORIZONTAL';return n;},createText(){return create('TEXT');},
  createNodeFromSvg(svg){const n=create();n.name='SVG插画';n.el.innerHTML=svg;n.el.firstElementChild.style.width='100%';n.el.firstElementChild.style.height='100%';return n;},
  async importComponentSetByKeyAsync(key){return {children:key==='cc8b558dc7d9684011b6b99ce8e6509399bc836b'?[buttonComponent]:[inputComponent]};},
  variables:{async getVariableByIdAsync(id){return variableById.get(id);},setBoundVariableForPaint(p,key,v){return{...p,hex:state.palette[v.key]};}}
 };};
 window.completeLocalDesign=function(result){for(const l of result.links){const n=nodes.get(l.id);if(n)n.el.addEventListener('click',()=>{location.search='screen='+l.to;});}for(const f of result.frames){const n=nodes.get(f.id);n.x=0;n.y=0;n.el.id='screen';document.body.style.width=f.width+'px';document.body.style.height=f.height+'px';}window.localDesignResult=result;window.designReady=true;};
})();
