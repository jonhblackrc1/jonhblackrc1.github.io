const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1080,height:1920}});
await p.goto('file://'+__dirname+'/escena-7s.html');fs.mkdirSync('/tmp/f7',{recursive:true});
const fps=30,T=7;for(let i=0;i<fps*T;i++){await p.evaluate(t=>render(t),i/fps);
await p.screenshot({path:`/tmp/f7/f${String(i).padStart(4,'0')}.jpg`,type:'jpeg',quality:92});}
await b.close();})();
