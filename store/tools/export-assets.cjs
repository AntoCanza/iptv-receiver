// Original vector artwork. Requires sharp; no provider content or third-party logos.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const out = path.join(root, 'store/assets');
const write = (p, text) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, text); };
const foreground = '<rect x="25" y="31" width="58" height="42" rx="10" fill="#47E3C2"/><rect x="30" y="36" width="48" height="32" rx="6" fill="#101B31"/><path d="M49 43L64 52L49 61Z" fill="#F4F8FF"/><rect x="43" y="78" width="22" height="4" rx="2" fill="#47E3C2"/>';
const icon = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><rect width="108" height="108" fill="#101B31"/>${foreground}</svg>`;
const feature = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500"><defs><linearGradient id="g"><stop stop-color="#101B31"/><stop offset="1" stop-color="#183F48"/></linearGradient></defs><rect width="1024" height="500" fill="url(#g)"/><circle cx="900" cy="110" r="230" fill="#47E3C2" opacity=".04"/><circle cx="850" cy="420" r="190" fill="#47E3C2" opacity=".05"/><g transform="translate(650 80) scale(2.9)">${foreground}</g><text x="70" y="205" fill="#F4F8FF" font-family="Arial,sans-serif" font-weight="700" font-size="58">IPTV Receiver</text><text x="74" y="264" fill="#47E3C2" font-family="Arial,sans-serif" font-size="28">Your service. Your screen.</text><text x="74" y="345" fill="#B7C9DA" font-family="Arial,sans-serif" font-size="21">Live TV  /  Movies  /  Series</text></svg>`;
async function main() {
  write(path.join(out, 'icon.svg'), icon);
  write(path.join(out, 'feature-graphic.svg'), feature);
  await sharp(Buffer.from(icon)).png().toFile(path.join(out, 'play-icon-512.png'));
  await sharp(Buffer.from(feature)).removeAlpha().png().toFile(path.join(out, 'feature-graphic-1024x500.png'));
  const res = path.join(root, 'app/src/main/res');
  write(path.join(res,'drawable/ic_launcher_foreground.xml'), `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:fillColor="#47E3C2" android:pathData="M35,31H73Q83,31 83,41V63Q83,73 73,73H35Q25,73 25,63V41Q25,31 35,31M45,78H63Q65,78 65,80Q65,82 63,82H45Q43,82 43,80Q43,78 45,78"/>
<path android:fillColor="#101B31" android:pathData="M36,36H72Q78,36 78,42V62Q78,68 72,68H36Q30,68 30,62V42Q30,36 36,36"/>
<path android:fillColor="#F4F8FF" android:pathData="M49,43L64,52L49,61Z"/>
</vector>`);
  write(path.join(res,'drawable/ic_launcher_monochrome.xml'), `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M35,31H73Q83,31 83,41V63Q83,73 73,73H35Q25,73 25,63V41Q25,31 35,31M36,36H72Q78,36 78,42V62Q78,68 72,68H36Q30,68 30,62V42Q30,36 36,36M49,43L64,52L49,61ZM45,78H63Q65,78 65,80Q65,82 63,82H45Q43,82 43,80Q43,78 45,78"/>
</vector>`);
  write(path.join(res,'mipmap-anydpi/ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_monochrome"/></adaptive-icon>`);
  write(path.join(res,'values/launcher_colors.xml'), '<?xml version="1.0" encoding="utf-8"?><resources><color name="launcher_background">#101B31</color></resources>');
  const titles = ['OCEAN LINES', 'QUIET ORBIT', 'FOREST LIGHT', 'GOLDEN HOUR', 'BEYOND THE RIDGE', 'NIGHT SIGNALS'];
  const colors = [['#123D56','#63DAD5'],['#201D52','#B3A4FF'],['#0E3C32','#B9D995'],['#582F35','#FFC783'],['#233F55','#BFDEE8'],['#252342','#CE9FDB']];
  for(let i=0;i<6;i++) {
    const [base,accent]=colors[i];
    const art=`<svg xmlns="http://www.w3.org/2000/svg" width="480" height="720"><rect width="480" height="720" fill="${base}"/><circle cx="325" cy="200" r="112" fill="${accent}" opacity=".85"/><path d="M0 415L155 250L310 430L480 300V720H0Z" fill="#0C172C" opacity=".6"/><path d="M0 510Q220 330 480 515V720H0Z" fill="${accent}" opacity=".2"/><text x="38" y="602" fill="#FFFFFF" font-family="sans-serif" font-size="${i==4?30:36}" font-weight="bold">${titles[i]}</text><text x="40" y="650" fill="${accent}" font-family="sans-serif" font-size="18" letter-spacing="3">DEMO COLLECTION</text></svg>`;
    write(path.join(root,`app/src/debug/assets/demo/poster-${i}.svg`),art);
  }
  console.log('Exported icon, feature graphic, launcher resources, and six original demo posters.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
