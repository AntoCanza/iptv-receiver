const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const catalog = fs.readFileSync(path.join(root, 'gradle', 'libs.versions.toml'), 'utf8').split('[libraries]')[0];
const pinned = Object.fromEntries([...catalog.matchAll(/^([\w-]+)\s*=\s*"([^"]+)"/gm)].map(m => [m[1], m[2]]));
const families = {
  agp: ['com.android.tools.build', 'gradle'], kotlin: ['org.jetbrains.kotlin', 'kotlin-gradle-plugin'],
  'compose-bom': ['androidx.compose', 'compose-bom'], activity: ['androidx.activity', 'activity-compose'],
  appcompat: ['androidx.appcompat', 'appcompat'], lifecycle: ['androidx.lifecycle', 'lifecycle-runtime-compose'],
  navigation: ['androidx.navigation', 'navigation-compose'], media3: ['androidx.media3', 'media3-exoplayer'],
  coroutines: ['org.jetbrains.kotlinx', 'kotlinx-coroutines-core'], serialization: ['org.jetbrains.kotlinx', 'kotlinx-serialization-json'],
  okhttp: ['com.squareup.okhttp3', 'okhttp'], coil: ['io.coil-kt.coil3', 'coil-compose'],
  junit: ['junit', 'junit'], work: ['androidx.work', 'work-runtime'],
};
const stable = value => /^\d+(?:\.\d+){1,3}(?:-(?:android|jre))?$/.test(value);
const memo = new Map();
async function latest(group, artifact) {
  const id = `${group}:${artifact}`;
  if (memo.has(id)) return memo.get(id);
  const promise = (async () => {
    const repository = group.startsWith('androidx.') || group.startsWith('com.android.') ?
      'https://dl.google.com/dl/android/maven2' : 'https://repo.maven.apache.org/maven2';
    const source = `${repository}/${group.replaceAll('.', '/')}/${artifact}/maven-metadata.xml`;
    try {
      const response = await fetch(source, { signal: AbortSignal.timeout(20000) });
      if (!response.ok) return { source, latestStable: null, status: `HTTP ${response.status}` };
      const xml = await response.text();
      const release = xml.match(/<release>([^<]+)<\/release>/)?.[1];
      const versions = [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map(m => m[1]);
      const latestStable = release && stable(release) ? release : versions.reverse().find(stable) || null;
      return { source, latestStable, status: latestStable ? 'verified' : 'no stable metadata version identified' };
    } catch (error) { return { source, latestStable: null, status: error.name }; }
  })();
  memo.set(id, promise);
  return promise;
}
async function mapLimited(items, action, concurrency = 8) {
  const result = new Array(items.length); let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    for (;;) { const index = cursor++; if (index >= items.length) break; result[index] = await action(items[index]); }
  })); return result;
}
(async () => {
  const direct = await mapLimited(Object.entries(families), async ([name, [group, artifact]]) =>
    ({ name, group, artifact, current: pinned[name], ...await latest(group, artifact) }));
  const coil3 = await latest('io.coil-kt.coil3', 'coil-compose');
  const inventory = JSON.parse(fs.readFileSync(path.join(root, 'build/dependency-audit/resolved-dependencies.json'), 'utf8'));
  const unique = new Map();
  for (const item of inventory) {
    const id = `${item.group}:${item.artifact}:${item.version}`;
    if (!unique.has(id)) unique.set(id, { ...item, scopes: [] });
    unique.get(id).scopes.push(item.scope);
  }
  const resolved = await mapLimited([...unique.values()], async item => ({ ...item, ...await latest(item.group, item.artifact) }));
  let gradle;
  try { gradle = await (await fetch('https://services.gradle.org/versions/current')).json(); } catch { gradle = null; }
  const actions = await mapLimited(['actions/checkout', 'actions/setup-java', 'gradle/actions', 'actions/upload-artifact'], async repository => {
    try {
      const response = await fetch(`https://api.github.com/repos/${repository}/releases/latest`, { headers: { 'User-Agent': 'iptv-receiver-audit' } });
      if (!response.ok) return { repository, status: `HTTP ${response.status}` };
      const data = await response.json(); return { repository, latest: data.tag_name, source: data.html_url };
    } catch (error) { return { repository, status: error.name }; }
  });
  const report = { checkedAt: new Date().toISOString().slice(0,10), direct, coil3, gradle: gradle && { version: gradle.version, source: 'https://services.gradle.org/versions/current' }, actions, resolved };
  fs.writeFileSync(path.join(root, 'build/dependency-audit/dependency-audit.json'), JSON.stringify(report, null, 2));
  fs.mkdirSync(path.join(root, 'docs'), { recursive: true });
  const quote = value => '"' + String(value ?? '').replaceAll('"', '""') + '"';
  const lines = [['group', 'artifact', 'resolved_version', 'latest_stable', 'status', 'source', 'scopes'].join(',')];
  for (const item of resolved) lines.push([item.group, item.artifact, item.version, item.latestStable, item.status, item.source, item.scopes.join(';')].map(quote).join(','));
  fs.writeFileSync(path.join(root, 'docs', 'dependency-inventory.csv'), lines.join('\n') + '\n');
  console.log(JSON.stringify({ direct, coil3, gradle: report.gradle, actions, resolvedCount: resolved.length,
    unverifiedCount: resolved.filter(item => item.status !== 'verified').length }, null, 2));
})();
