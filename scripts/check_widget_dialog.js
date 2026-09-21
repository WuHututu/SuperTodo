const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('widget_dialog.html', 'utf8');
const androidAsset = fs.readFileSync('android-src/main/assets/widget_dialog.html', 'utf8');
const appSource = fs.readFileSync('app.js', 'utf8');
const managerSource = fs.readFileSync('android-src/main/java/com/dax/supertodo/widget/WidgetDataManager.java', 'utf8');
const normalizedSource = source.replace(/\r\n/g, '\n');

assert.equal(androidAsset, source, 'Android asset must match widget_dialog.html');
assert.match(source, /-webkit-tap-highlight-color:\s*transparent/);
assert.match(source, /function applyWidgetTheme\(hex\)/);
assert.match(source, /applyWidgetTheme\(rootData\.theme \|\| '#0b57d0'\)/);
assert.match(source, /function syncWidget2x2FromItems\(fillWhenEmpty\)/);
assert.match(source, /localStorage\.getItem\('listapp\.data\.v2'\)/);
assert.match(source, /localStorage\.setItem\('listapp\.data\.v2', json\)/);
assert.match(appSource, /widget2x2:\s*buildWidget2x2Items\(state\.widget2x2,\s*state\.items\)/);
assert.match(appSource, /state\.items\.push\(normalizeStoredItem\(natIt\)\)/);
assert.doesNotMatch(source, /btn-close/, 'header close button and its CSS must be gone');
assert.match(source, /function handleBackPressed\(\)\s*\{\s*closeSelfWithAnimation\(\);/, 'back press must still close the dialog');
assert.doesNotMatch(source, /viewSettings|viewPicker|从已有待办库中挑选添加|挑选已有待办|拖拽右侧手柄/);

// 标题栏调色盘按钮与小组件样式面板
assert.match(source, /<button class="tool-btn" onclick="toggleBgPanel\(\)" title="小组件样式">/);
assert.match(source, /<svg width="14" height="14" viewBox="0 0 1024 1024" fill="currentColor">/);
assert.match(source, /<div class="bg-style-panel" id="bgStylePanel" style="display:none;">/);
assert.match(source, /id="bgOpacitySlider" min="10" max="100" step="5"/);
assert.match(source, /id="bgBlurSlider" min="0" max="30" step="1"/);
assert.match(source, /id="bgImageButton" onclick="setBgType\('image'\)">背景图</);
assert.match(source, /id="bgThemeButton" onclick="setBgType\('theme'\)">跟随主题</);
assert.match(source, /function normalizeWidget2x2Background\(v\)/);
assert.match(source, /rootData\.widget2x2Background = normalizeWidget2x2Background\(rootData\.widget2x2Background\)/);
assert.match(source, /function saveBgSliders\(\)[\s\S]*?saveData\(\);/);
assert.match(source, /function showInputRow\(\)\s*\{\s*document\.getElementById\('bgStylePanel'\)\.style\.display = 'none';/, 'input row and style panel must stay mutually exclusive');
assert.match(source, /function toggleBgPanel\(\)[\s\S]*?hideInputRow\(\)/);

// 行生命周期动效：塌陷离场 + 主题色划线 + 沉底重入（全程 780ms）
assert.match(source, /\.task-list\s*\{[^}]*gap:\s*0;/, 'flex gap 收不掉，行距必须交给 margin-bottom');
assert.match(source, /\.task-row\s*\{[^}]*margin-bottom:\s*8px;/);
assert.match(source, /\.task-row:last-child\s*\{\s*margin-bottom:\s*0;/);
assert.match(source, /\.task-row\.leaving\s*\{[^}]*height:\s*0\s*!important;/, '离场行必须能塌到 0 高');
assert.match(source, /@keyframes rowEnter\s*\{[^}]*translateY\(-6px\)/);
assert.match(source, /\.task-row\.entering\s*\{[^}]*animation:\s*rowEnter/);
assert.match(source, /\.t-text::after\s*\{[\s\S]*?background:\s*var\(--primary\);/, '划线必须是主题色');
assert.match(source, /\.t-text::after\s*\{[\s\S]*?transform:\s*scaleX\(0\);[\s\S]*?transform-origin:\s*left center;/);
assert.match(source, /\.task-row\.completing \.t-text::after, \.task-row\.done \.t-text::after\s*\{\s*transform:\s*scaleX\(1\);/);
assert.doesNotMatch(source, /text-decoration:\s*line-through/, 'text-decoration 不可动画，划线改由 ::after 承担');
assert.match(source, /\.t-text\s*\{[^}]*text-overflow:\s*ellipsis;/, '省略号得跟着内层 span，划线才不会划到手柄上');
assert.match(source, /\.task-check\s*\{[^}]*border:\s*2\.6px solid/, '勾选框加粗');
assert.match(source, /border-width:\s*0 2\.6px 2\.6px 0;/);
assert.match(source, /transform:\s*translateY\(-1\.41px\) rotate\(45deg\);/, '√ 用 translateY 补回 content-box 下沉的 1.41px');
assert.match(source, /\.task-title\s*\{[^}]*font-weight:\s*800;/, '待办文字加粗');
assert.match(source, /const ordered = visibleTasks\.slice\(\)\.sort\(\(a, b\) => \(itemMap\.get\(a\.id\)\.done \? 1 : 0\) - \(itemMap\.get\(b\.id\)\.done \? 1 : 0\)\);/, '已完成只显示在未完成之后');
assert.match(source, /let enterIds = new Set\(\);/);
assert.match(source, /row\.classList\.add\('done', 'completing'\);/);
assert.match(source, /enterIds\.add\(id\);\s*renderList\(\);/);
assert.match(source, /enterIds\.add\(newId\);/);

// 取消勾选反向时间线：收线 0→250 →（重叠 30ms）尾部塌陷 220→290 → 未完成区末尾展开 290→520
assert.match(source, /\.task-row\.done \.task-check::after, \.task-row\.unchecking \.task-check::after\s*\{\s*content: '';/, '反向态得留着 ::after 的 content，否则 √ 没有退场动画、直接消失');
assert.doesNotMatch(source, /\.task-row\.unchecking:not\(\.done\) \.task-check::after\s*\{[^}]*scale\(0\)[^}]*transition:[^;]*1\.6/, '√ 退场不能用 checkPop 那条 1.6 回弹曲线，会越过 0 反手');
assert.match(source, /\.task-row\.unchecking:not\(\.done\) \.task-check::after\s*\{[^}]*transform:\s*translateY\(-1\.41px\) rotate\(45deg\) scale\(0\);[^}]*opacity:\s*0;/, '√ 反向缩回并淡出，且对齐数值一个不动');
assert.match(source, /\.task-row\.unchecking \.t-text::after\s*\{\s*transition:\s*transform 0\.25s cubic-bezier\(0\.65, 0, 0\.35, 1\);/, '收线用对称曲线：正向那条前 20% 吃掉 80% 位移，倒着跑一眨眼就没');
assert.match(source, /\.task-row\.unchecking\.leaving\s*\{\s*transition-duration:\s*0\.07s;\s*transition-timing-function:\s*linear;/, 'B 段塌陷 70ms');
assert.match(source, /\.task-row\.unchecking\.entering\s*\{\s*transition-duration:\s*0\.23s;/, 'C 段展开 230ms');
assert.doesNotMatch(source, /if \(!mainItem\.done\) \{\s*renderList\(\);/, '取消勾选不再走硬重建');
assert.match(source, /row\.classList\.add\('unchecking'\);\s*\/\/ [^\n]*\n\s*row\.classList\.remove\('done', 'completing'\);/, 'A 段起跑：挂反向态 + 摘完成态');
assert.match(source, /row\.classList\.add\('leaving'\);\s*\}, 220\);/, 'B 段 220ms 起跑（比 A 段终点早 30ms）');
assert.match(source, /document\.querySelector\(selector\)\.classList\.add\('unchecking'\);\s*\}, 290\);/, 'C 段 290ms 重建成从塌陷态展开');
assert.match(source, /revived\.classList\.remove\('unchecking'\);\s*revived\.style\.height = '';\s*\}, 520\);/, '520ms 收尾：清反向态与行内 height');
const reverseCss = normalizedSource.slice(normalizedSource.indexOf('/* 取消勾选（.unchecking）'), normalizedSource.indexOf('.drag-handle {'));
assert.ok(reverseCss.includes('.task-row.unchecking.entering'), '找不到反向样式块');
assert.doesNotMatch(reverseCss, /#FFF|var\(--primary\)|var\(--text-sec\)|background:/, '反向只改时长与缓动，配色一个字节都不许动');

// 背景设置规范化：透明度 10~100、模糊度 0~30、类型白名单
const normalizeStart = normalizedSource.indexOf('    function normalizeWidget2x2Background(v) {');
const normalizeEnd = normalizedSource.indexOf('\n\n    function syncWidget2x2FromItems', normalizeStart);
assert.ok(normalizeStart >= 0 && normalizeEnd > normalizeStart, 'normalizeWidget2x2Background function not found');
const normalizeSandbox = {};
vm.runInNewContext(`
  ${normalizedSource.slice(normalizeStart, normalizeEnd)}
  globalThis.result = [
    normalizeWidget2x2Background(undefined),
    normalizeWidget2x2Background({ opacity: 0, blur: 99, type: 'bogus' }),
    normalizeWidget2x2Background({ opacity: 45, blur: 0, type: 'image' })
  ];
`, normalizeSandbox);
assert.deepEqual(JSON.parse(JSON.stringify(normalizeSandbox.result)), [
  { opacity: 80, blur: 12, type: 'theme' },
  { opacity: 10, blur: 30, type: 'theme' },
  { opacity: 45, blur: 0, type: 'image' }
]);

const appNormalize = appSource.match(/function normalizeWidget2x2Background\(value\)\{[\s\S]*?\n\}/);
assert(appNormalize && appNormalize[0].includes('type'), 'app.js normalizeWidget2x2Background must keep type');
assert(managerSource.includes('supertodo_widget_bg.img'), 'native must load the widget background image file');
assert(managerSource.includes('saveWidgetBackgroundImage'), 'native must expose saveWidgetBackgroundImage');

const appScript = normalizedSource.slice(normalizedSource.lastIndexOf('<script>') + 8, normalizedSource.lastIndexOf('</script>'));
new vm.Script(appScript);

const syncStart = normalizedSource.indexOf('    function syncWidget2x2FromItems(fillWhenEmpty) {');
const syncEnd = normalizedSource.indexOf('\n\n    // 从 Java Bridge', syncStart);
assert.ok(syncStart >= 0 && syncEnd > syncStart, 'syncWidget2x2FromItems function not found');

const syncSandbox = {};
vm.runInNewContext(`
  let rootData = {
    items: [{ id: 'a', title: '主数据标题', done: true }, { id: 'b', title: '新增事项', done: false }],
    widget2x2: [{ id: 'c', title: '已删除事项', done: false }]
  };
  ${normalizedSource.slice(syncStart, syncEnd)}
  syncWidget2x2FromItems(false);
  globalThis.result = rootData.widget2x2;
`, syncSandbox);
assert.deepEqual(JSON.parse(JSON.stringify(syncSandbox.result)), [
  { id: 'b' }
]);

const start = normalizedSource.indexOf('    function initSortable(el) {');
const end = normalizedSource.indexOf('\n\n    function escapeHtml(str)', start);
assert.ok(start >= 0 && end > start, 'initSortable function not found');

const sandbox = {};
vm.runInNewContext(`
  let sortableInstance = null;
  let rootData = { widget2x2: [{ id: 'a' }, { id: 'b' }, { id: 'c' }] };
  let saveCount = 0;
  function saveData() { saveCount += 1; }
  class Sortable {
    constructor(el, options) { globalThis.options = options; }
    destroy() {}
  }
  ${normalizedSource.slice(start, end)}
  const sequences = [
    { textContent: '1.', dataset: { id: 'b' } },
    { textContent: '2.', dataset: { id: 'c' } },
    { textContent: '3.', dataset: { id: 'a' } }
  ];
  initSortable({ querySelectorAll: () => sequences });
  options.onEnd({ oldIndex: 0, newIndex: 2 });
  globalThis.result = {
    ids: rootData.widget2x2.map(item => item.id).join(','),
    labels: sequences.map(item => item.textContent).join(','),
    saveCount,
    forceFallback: options.forceFallback,
    fallbackOnBody: options.fallbackOnBody,
    delayOnTouchOnly: options.delayOnTouchOnly,
    draggable: options.draggable
  };
`, sandbox);

assert.deepEqual(JSON.parse(JSON.stringify(sandbox.result)), {
  ids: 'b,c,a',
  labels: '1.,2.,3.',
  saveCount: 1,
  forceFallback: true,
  fallbackOnBody: true,
  delayOnTouchOnly: true,
  draggable: '.task-row'
});

console.log('2x2 widget dialog checks: OK');
