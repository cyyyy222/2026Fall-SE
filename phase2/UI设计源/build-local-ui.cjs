const fs=require('fs');const path=require('path');
const root=__dirname;
const state=JSON.parse(fs.readFileSync(path.join(root,'figma-state.json'),'utf8'));
const screens=JSON.parse(fs.readFileSync(path.join(root,'screens.json'),'utf8'));
const source=fs.readFileSync(path.join(root,'figma-render.js'),'utf8');
const data={state,screens,source};
const html=`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>活动票务 · UI设计预览</title><style>html,body{margin:0;background:#F6F4F0}body{font-family:'Microsoft YaHei',sans-serif}*{box-sizing:border-box}</style></head><body><script src="local-design-runtime.js"></script><script>const DATA=${JSON.stringify(data).replace(/</g,'\\u003c')};const current=new URLSearchParams(location.search).get('screen')||'04';const selected=DATA.screens.filter(s=>s.id===current);const api=createLocalDesignAPI(DATA.state);const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;new AsyncFunction('figma','STATE','SPECS',DATA.source)(api,DATA.state,selected).then(completeLocalDesign).catch(e=>{document.body.textContent=e.stack;window.designError=e.stack;});</script></body></html>`;
fs.writeFileSync(path.join(root,'index.html'),html,'utf8');
console.log('Local editable design preview built for '+screens.length+' screens.');
