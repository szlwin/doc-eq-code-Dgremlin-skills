// Generated event-handler tests against a minimal SVG DOM contract, without a browser/network.
const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
class Element {
  constructor() { this.attrs={}; this.dataset={}; this.style={}; this.events={}; }
  setAttribute(k,v) { this.attrs[k]=String(v); }
  getAttribute(k) { return this.attrs[k] ?? null; }
  hasAttribute(k) { return k in this.attrs; }
  removeAttribute(k) { delete this.attrs[k]; }
  addEventListener(k,callback) { this.events[k]=callback; }
}
const svg=new Element(), nodes=[], edges=[], controls=[];
svg.dataset.originalViewbox='0 0 1600 1200';
svg.viewBox={baseVal:{x:0,y:0,width:1600,height:1200}};
svg.getBoundingClientRect=()=>({left:0,top:0,width:1600,height:1200});
svg.getScreenCTM=()=>({inverse:()=>({})});
svg.createSVGPoint=()=>({x:0,y:0,matrixTransform(){return {x:this.x,y:this.y};}});
function makeNode(id,x,y,w,h,detail=false) {
  const n=new Element(), face=new Element();
  n.dataset.nodeId=id; n.detail=detail;
  face.getBBox=()=>({x,y,width:w,height:h});
  n.querySelector=selector=>selector==='.info-face, .rule-face'?face:null;
  n.closest=selector=>selector==='.interactive-svg'?svg:selector==='.viz-node'?n:null;
  n.setPointerCapture=()=>{};
  if(detail)n.style.display='none';else n.setAttribute('data-info-x',x);
  nodes.push(n); return n;
}
const source=makeNode('info:order.waitPay',500,400,280,136);
const inspector={innerHTML:''};
const template={dataset:{detailId:source.dataset.nodeId},innerHTML:'Information details and RuleView link'};
const detailTemplate={dataset:{detailId:'rule:order.waitPay:right'},innerHTML:'Selected rule details'};
const panelContainer={querySelector:()=>inspector,querySelectorAll:()=>[template,detailTemplate]};
const sourceClosest=source.closest;
source.closest=selector=>selector==='.panel'?panelContainer:sourceClosest(selector);
makeNode('info:order.other',900,400,280,136); // The right detail must avoid this existing node.
for(const direction of ['up','down','left','right']) {
  const panel=makeNode('rule:order.waitPay:'+direction,0,0,320,340,true);
  const control=new Element(), text=new Element();
  text.textContent='＋'; control.dataset={direction,panelId:panel.dataset.nodeId};
  control.setAttribute('aria-expanded','false');control.querySelector=()=>text;
  control.closest=selector=>selector==='.info-expand-control'?control:selector==='.viz-node'?source:null;
  controls.push(control);
  const edge=new Element(), path=new Element(), label=new Element();
  edge.dataset={source:source.dataset.nodeId,target:panel.dataset.nodeId};edge.style.display='none';
  edge.querySelector=selector=>selector==='.edge-path'?path:label;edges.push(edge);
}
svg.querySelectorAll=selector=>selector==='.viz-node'?nodes:selector==='.graph-edge'||selector==='.rule-detail-edge'?edges:selector==='.info-expand-control'?controls:selector==='.rule-detail-node, .rule-detail-edge'?[...nodes.filter(n=>n.detail),...edges]:[];
const reset=new Element();reset.dataset.graphAction='reset';reset.closest=()=>({querySelector:()=>svg});
const document={querySelectorAll:selector=>selector==='.interactive-svg'?[svg]:[reset]};
// A Directory dependency expands through a shared Information DAG to rule frames.
const payable=makeNode('dep:pay:0',1500,100,155,48);
const ordered=makeNode('expand:ordered',0,0,280,136,true);
const waitPay=makeNode('expand:waitPay',0,0,280,136,true);
const rule=makeNode('expand:rule',0,0,320,340,true);
payable.dataset.expansionRoot='true';
payable.dataset.expansionTargets=JSON.stringify([ordered.dataset.nodeId,waitPay.dataset.nodeId]);
ordered.dataset.expansionTargets=JSON.stringify([rule.dataset.nodeId]);
waitPay.dataset.expansionTargets=JSON.stringify([rule.dataset.nodeId]);
for(const n of [ordered,waitPay,rule])n.dataset.expansionNode='true';
const expansionControls=[];
for(const n of [payable,ordered,waitPay]) {
  for(const direction of ['up','down','left','right']) {
    const control=new Element(),text=new Element();text.textContent='＋';
    control.dataset={expansion:'true',direction};control.setAttribute('aria-expanded','false');
    control.querySelector=()=>text;
    control.closest=s=>s==='.info-expand-control'?control:s==='.viz-node'?n:null;
    expansionControls.push(control);
  }
  for(const target of JSON.parse(n.dataset.expansionTargets)) {
    const e=new Element(),path=new Element(),label=new Element();
    e.dataset={expansionEdge:'true',source:n.dataset.nodeId,target};e.style.display='none';
    e.querySelector=s=>s==='.edge-path'?path:label;edges.push(e);
  }
}
const previousQuery=svg.querySelectorAll;
svg.querySelectorAll=s=>s==='.info-expand-control'?[...controls,...expansionControls]:previousQuery(s);
vm.runInNewContext(fs.readFileSync(process.argv[2],'utf8'),{document});
const click=(control)=>source.events.click({target:control});
// A plain node click no longer expands its height or opens a panel.
source.events.click({target:source});assert.equal(inspector.innerHTML,template.innerHTML);assert.ok(nodes.filter(n=>n.detail).every(n=>n.style.display==='none'));
for(const control of controls) {
  click(control);
  const panel=nodes.find(n=>n.dataset.nodeId===control.dataset.panelId);
  assert.equal(control.getAttribute('aria-expanded'),'true');assert.equal(panel.style.display,'');
  const x=Number(panel.dataset.dx),y=Number(panel.dataset.dy);
  if(control.dataset.direction==='up')assert.ok(y+340<400);
  if(control.dataset.direction==='down')assert.ok(y>536);
  if(control.dataset.direction==='left')assert.ok(x+320<500);
  if(control.dataset.direction==='right')assert.ok(x>1180,'Avoid occupied area to the right');
  assert.equal(edges.find(e=>e.dataset.target===panel.dataset.nodeId).style.display,'');
}
assert.equal(source.querySelector('.info-face, .rule-face').getBBox().height,136);
assert.equal(inspector.innerHTML,template.innerHTML,'Directional controls must preserve the sidebar');
const right=controls[3], edge=edges[3], before=edge.querySelector('.edge-path').getAttribute('d');
// Dragging the original node updates all visible detail connections.
svg.events.pointerdown({target:source,clientX:0,clientY:0,pointerId:1,preventDefault(){},stopPropagation(){}});
svg.events.pointermove({clientX:40,clientY:20});svg.events.pointerup();
assert.notEqual(edge.querySelector('.edge-path').getAttribute('d'),before);
click(right);assert.equal(right.getAttribute('aria-expanded'),'false');assert.equal(edge.style.display,'none');
assert.equal(controls[0].getAttribute('aria-expanded'),'true','Directions close independently');
source.events.keydown({target:right,key:'Enter',preventDefault(){}});
assert.equal(right.getAttribute('aria-expanded'),'true');
// Selecting a rule frame must update the sidebar instead of retaining the last node.
const selectedPanel=nodes.find(n=>n.dataset.nodeId===right.dataset.panelId);
const panelClosest=selectedPanel.closest;
selectedPanel.closest=selector=>selector==='.panel'?panelContainer:panelClosest(selector);
svg.events.pointerdown({target:selectedPanel,clientX:0,clientY:0,pointerId:2,preventDefault(){},stopPropagation(){}});
svg.events.pointerup();
selectedPanel.events.click({target:selectedPanel});
assert.equal(inspector.innerHTML,detailTemplate.innerHTML);
// Clicking/scrolling the HTML content must not close or drag the detail panel.
const panel=nodes.find(n=>n.dataset.nodeId===right.dataset.panelId);
const content={closest:selector=>selector==='.inline-information'?{}:null};
panel.events.click({target:content});
const contentEvent={target:content,preventDefault(){throw Error('Content event intercepted');}};
svg.events.wheel(contentEvent);svg.events.pointerdown(contentEvent);
assert.equal(right.getAttribute('aria-expanded'),'true');
reset.events.click();
assert.ok(nodes.filter(n=>n.detail).every(n=>n.style.display==='none'));
assert.ok(edges.every(e=>e.style.display==='none'));
assert.ok(controls.every(c=>c.getAttribute('aria-expanded')==='false'&&c.querySelector('text').textContent==='＋'));
assert.equal(Number(source.dataset.dx),0);assert.equal(svg.viewBox.baseVal.width,1600);
payable.events.click({target:payable});
assert.equal(ordered.style.display,'');assert.equal(waitPay.style.display,'');assert.equal(rule.style.display,'none');
ordered.events.click({target:ordered});assert.equal(rule.style.display,'');
waitPay.events.click({target:waitPay});
ordered.events.click({target:ordered});assert.equal(rule.style.display,'','Shared detail stays visible through the other expanded parent');
payable.events.click({target:payable});
for(const n of [ordered,waitPay,rule])assert.equal(n.style.display,'none');
assert.ok(expansionControls.every(c=>c.getAttribute('aria-expanded')==='false'));
for(const c of expansionControls.filter(c=>c.closest('.viz-node')===payable)) {
  payable.events.keydown({target:c,key:'Enter',preventDefault(){}});
  assert.equal(c.getAttribute('aria-expanded'),'true');assert.equal(ordered.style.display,'');
  reset.events.click();
  assert.equal(ordered.style.display,'none');assert.equal(payable.dataset.expansionDirection,undefined);
}
console.log('PASS: four directions, independent toggles, collision avoidance, connector drag, keyboard, reset, content events');
