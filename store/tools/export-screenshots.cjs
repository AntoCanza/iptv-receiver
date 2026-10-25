const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const directory = process.argv[2];
if(!directory) throw new Error('Pass the directory containing the four synthetic-catalog captures.');
const scenes = [
  ['01-home','Your service, in one place','Live TV · Movies · Series'],
  ['02-movies','Browse your movie library','Categories and artwork from your provider'],
  ['03-series','Find your next episode','Series organized by season'],
  ['04-languages','Feel at home','Device language, with English fallback'],
];
async function main(){
  const output=path.join(root,'store/screenshots');fs.mkdirSync(output,{recursive:true});
  for(const [file,title,subtitle] of scenes){
    const source=path.join(directory,`${file}.png`);
    const meta=await sharp(source).metadata();
    if(meta.width!==1344||meta.height!==2992) throw new Error('Unexpected capture dimensions; review crop before export.');
    // Remove personal Android status/navigation bars. The actual app UI is scaled proportionally.
    const app=await sharp(source).extract({left:0,top:130,width:1344,height:2718})
      .resize({height:1460}).png().toBuffer();
    const am=await sharp(app).metadata();
    const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920"><rect width="1080" height="1920" fill="#101B31"/><text x="72" y="104" fill="#47E3C2" font-family="Arial,sans-serif" font-size="25" letter-spacing="3">IPTV RECEIVER</text><text x="72" y="182" fill="#F4F8FF" font-family="Arial,sans-serif" font-weight="700" font-size="46">${title}</text><text x="72" y="238" fill="#B7C9DA" font-family="Arial,sans-serif" font-size="25">${subtitle}</text><text x="540" y="1845" text-anchor="middle" fill="#B7C9DA" font-family="Arial,sans-serif" font-size="22">Illustrative catalog · Your provider supplies the content</text></svg>`;
    await sharp(Buffer.from(svg)).composite([{input:app,left:Math.round((1080-am.width)/2),top:300}])
      .removeAlpha().png().toFile(path.join(output,`${file}.png`));
  }
  console.log('Exported four 1080 × 1920 screenshots.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
