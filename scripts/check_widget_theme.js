#!/usr/bin/env node
const assert = require('assert');
const fs = require('fs');

const dir = 'android-src/main/java/com/dax/supertodo/widget/';
const read = name => fs.readFileSync(dir + name, 'utf8');
const manager = read('WidgetDataManager.java');
const widget2x2 = read('TodoWidget2x2Provider.java');
const widget4x2 = read('TodoWidget4x2Provider.java');
const widget4x4 = read('TodoWidget4x4Provider.java');
const quadrant = read('TodoWidgetQuadrantProvider.java');
const service = read('TodoWidgetService.java');
const layoutDir = 'android-src/main/res/layout/';
const widget2x2Layout = fs.readFileSync(layoutDir + 'widget_2x2.xml', 'utf8');
const quadrantLayout = fs.readFileSync(layoutDir + 'widget_quadrant.xml', 'utf8');

assert(manager.includes('optString("theme", "")'), '必须从同步 JSON 读取主题色');
assert(manager.includes('theme.matches("^#[0-9a-fA-F]{6}$")'), '主题色必须校验为六位十六进制');
assert(manager.includes('getThemedCheckedIcon'), '必须保留白色对勾并只替换圆形主色');
assert(manager.includes('getWidgetBackgroundColor'), '必须从同步 JSON 计算小组件背景色');
assert(manager.includes('optInt("opacity", 80)'), '2x2 背景必须读取透明度配置');
assert(manager.includes('optInt("blur", 12)'), '2x2 背景必须读取模糊度配置');
assert(widget2x2.includes('setImageViewBitmap(R.id.widget_2x2_bg'), '2x2 必须使用动态背景 bitmap');
assert(widget2x2Layout.includes('android:id="@+id/widget_2x2_bg"'), '2x2 布局必须保留动态背景层');

// 2x2 背景渲染：圆角硬边裁切、图片不叠遮罩不描白边、文字色按卡面亮度取
const bgMethodStart = manager.indexOf('public static android.graphics.Bitmap getWidgetBackgroundBitmap');
const bgMethodEnd = manager.indexOf('private static synchronized android.graphics.Bitmap loadWidgetBackgroundImage');
assert.ok(bgMethodStart >= 0 && bgMethodEnd > bgMethodStart, '找不到 getWidgetBackgroundBitmap 方法体');
const backgroundMethod = manager.slice(bgMethodStart, bgMethodEnd);
assert(!backgroundMethod.includes('Paint.Style.STROKE'), '背景不能描一圈 widget_stroke 白边');
assert(!backgroundMethod.includes('canvas.drawRect'), '图片模式不能用主题色整块盖住图片');
assert(backgroundMethod.includes('PorterDuff.Mode.DST_IN'), '圆角必须用 DST_IN 硬边裁切');
assert(!manager.includes('BlurMaskFilter'), 'BlurMaskFilter 会把 alpha 扩散到圆角外导致四角溢出');
assert(widget2x2.includes('setTextColor(titles[i], contentColor)'), '2x2 文字色必须来自卡面亮度而不是主题色');
assert(widget2x2.includes('setTextColor(R.id.widget_2x2_empty_view, contentColor)'), '2x2 空态文案必须跟随卡面文字色');
assert(widget2x2.includes('setInt(checks[i], "setColorFilter", contentColor)'), '2x2 勾选框描边必须跟随卡面文字色');
assert(!widget2x2.includes('themeColor'), '2x2 不能再用主题色当文字色');
assert(!quadrantLayout.includes('android:background="#FFFFFF"'), '四象限方向标签背景不能硬编码为日间白色');

for (const source of [widget4x2, widget4x4, service]) {
    assert(source.includes('setTextColor') && source.includes('themeColor'), '列表标签必须跟随主题色');
    assert(source.includes('setImageViewBitmap') && source.includes('getThemedCheckedIcon'), '已完成图标必须跟随主题色');
}
assert(quadrant.includes('getThemedCheckedIcon(themeColor)'), '四象限完成图标必须跟随主题色');

const configActivity = read('WidgetConfigActivity.java');
const configLayout = fs.readFileSync(layoutDir + 'activity_widget_config.xml', 'utf8');
assert(configLayout.includes('android:text="小组件设置"'), '弹窗标题必须为《小组件设置》');
assert(configActivity.includes('WidgetDataManager.getWidgetThemeColor(this)'), '小组件设置弹窗必须动态获取当前主题色');
assert(configActivity.includes('btnSave.setBackground(ripple)'), '保存按钮必须动态应用主题色背景');
assert(configActivity.includes('cbHideDone.setButtonTintList'), '复选框勾选色必须动态应用主题色');
assert(configActivity.includes('pillBg.setColor(themeColor)'), '选中胶囊必须动态应用主题色');

console.log('widget theme checks: OK');

