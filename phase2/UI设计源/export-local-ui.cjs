const fs=require('fs');const path=require('path');const {pathToFileURL}=require('url');
const packageRoot=process.env.UI_NODE_MODULES||'C:/Users/20431/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const {chromium}=require(path.join(packageRoot,'playwright'));
(async()=>{
const browser=await chromium.launch({headless:true,executablePath:process.env.UI_BROWSER||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
const page=await browser.newPage({viewport:{width:1440,height:1200},deviceScaleFactor:1});
const screens=JSON.parse(fs.readFileSync(path.join(__dirname,'screens.json'),'utf8'));
const selected=process.argv.slice(2);const report=[];
for(const s of screens.filter(s=>!selected.length||selected.includes(s.id))){
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href+'?screen='+s.id);
 await page.waitForFunction(()=>window.designReady||window.designError);
 const error=await page.evaluate(()=>window.designError);if(error)throw new Error(error);
 await page.evaluate(()=>document.fonts.ready);
 const bounds=await page.locator('#screen').boundingBox();
 await page.locator('#screen').screenshot({path:path.join(__dirname,'../UI界面',s.id+'-'+s.title+'.png')});
 const issues=await page.evaluate(()=>{const root=document.querySelector('#screen'),rr=root.getBoundingClientRect();return [...root.querySelectorAll('[data-node]')].filter(n=>{const r=n.getBoundingClientRect();return r.width&&r.height&&(r.right>rr.right+2||r.bottom>rr.bottom+2);}).map(n=>({name:n.dataset.name,x:n.getBoundingClientRect().x,y:n.getBoundingClientRect().y,w:n.getBoundingClientRect().width,h:n.getBoundingClientRect().height}));});
 report.push({id:s.id,name:s.title,width:bounds.width,height:bounds.height,issues});console.log(s.id+' '+s.title+' '+Math.round(bounds.width)+'x'+Math.round(bounds.height)+' / overflow '+issues.length);
}
fs.writeFileSync(path.join(__dirname,'local-render-check.json'),JSON.stringify(report,null,2));
await browser.close();
})().catch(e=>{console.error(e);process.exitCode=1;});
