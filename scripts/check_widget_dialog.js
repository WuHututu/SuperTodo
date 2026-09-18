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
