const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1080,height:1920}});
await p.goto('file://'+__dirname+'/escena.html');fs.mkdirSync('/tmp/frames',{recursive:true});
const fps=30,T=27;for(let i=0;i<fps*T;i++){await p.evaluate(t=>render(t),i/fps);
await p.screenshot({path:`/tmp/frames/f${String(i).padStart(4,'0')}.jpg`,type:'jpeg',quality:92});}
await b.close();})();
