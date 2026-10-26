// Executed through Figma use_figma. Reuses the discovered Simple Design System.
// createVariableCollection follows the official figma-generate-library helper.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('0:1'));
const existing = await figma.variables.getLocalVariableCollectionsAsync();
if (existing.some(c => c.name === 'Morandi / Primitives')) throw new Error('Foundations already exist; inspect state before retry.');
async function createVariableCollection(name, modeNames) {
  const collection=figma.variables.createVariableCollection(name);
  const modeIds={}; collection.renameMode(collection.modes[0].modeId,modeNames[0]);
  modeIds[modeNames[0]]=collection.modes[0].modeId;
  return {collection,modeIds};
}
const ids=[]; const variables={}; const styles={};
const palette={bg:'#F6F4F0',surface:'#FFFEFC',primary:'#A97773',primarySoft:'#F1E6E3',sage:'#7C8C7C',sageSoft:'#EAF0E8',text:'#303632',muted:'#777C76',border:'#DFDED8',oat:'#E9E3D9',white:'#FFFFFF',warning:'#997D53',warningSoft:'#F6F0E5'};
function rgb(hex){return {r:parseInt(hex.slice(1,3),16)/255,g:parseInt(hex.slice(3,5),16)/255,b:parseInt(hex.slice(5,7),16)/255};}
const primitive=await createVariableCollection('Morandi / Primitives',['Value']);
const semantic=await createVariableCollection('Morandi / UI',['Light']);
for(const [key,hex] of Object.entries(palette)){
  const p=figma.variables.createVariable('palette/'+key,primitive.collection,'COLOR');p.scopes=[];p.setValueForMode(primitive.modeIds.Value,{...rgb(hex),a:1});p.setVariableCodeSyntax('WEB',`var(--palette-${key})`);ids.push(p.id);
  const v=figma.variables.createVariable(key,semantic.collection,'COLOR');v.scopes=['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE_COLOR'];v.setValueForMode(semantic.modeIds.Light,{type:'VARIABLE_ALIAS',id:p.id});v.setVariableCodeSyntax('WEB',`var(--color-${key})`);variables[key]=v.id;ids.push(v.id);
}
for(const [key,value,scope] of [['gap8',8,'GAP'],['gap16',16,'GAP'],['gap24',24,'GAP'],['pad32',32,'GAP'],['radius8',8,'CORNER_RADIUS'],['radius12',12,'CORNER_RADIUS']]){
  const v=figma.variables.createVariable(key,semantic.collection,'FLOAT');v.scopes=[scope];v.setValueForMode(semantic.modeIds.Light,value);v.setVariableCodeSyntax('WEB',`var(--${key})`);variables[key]=v.id;ids.push(v.id);
}
for(const [name,family,style,size] of [['Body','Noto Sans SC','Regular',15],['Label','Noto Sans SC','Medium',14],['Small','Noto Sans SC','Regular',12],['Title','Noto Serif SC','Bold',32],['Section','Noto Sans SC','Medium',20]]){
  await figma.loadFontAsync({family,style});const s=figma.createTextStyle();s.name='Morandi/'+name;s.fontName={family,style};s.fontSize=size;s.lineHeight={unit:'PERCENT',value:150};styles[name]=s.id;ids.push(s.id);
}
return {createdNodeIds:ids,collectionIds:[primitive.collection.id,semantic.collection.id],variables,styles,palette,buttonComponent:'2:57',inputComponent:'2:144',pageId:'0:1',font:'Noto Sans SC',titleFont:'Noto Serif SC'};
