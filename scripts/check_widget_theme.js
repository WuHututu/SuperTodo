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

// 2x2 背景渲染：圆角靠 drawRoundRect 自己抗锯齿、图片不叠遮罩不描白边、文字色按卡面亮度取
const bgMethodStart = manager.indexOf('public static android.graphics.Bitmap getWidgetBackgroundBitmap');
const bgMethodEnd = manager.indexOf('private static synchronized android.graphics.Bitmap loadWidgetBackgroundImage');
assert.ok(bgMethodStart >= 0 && bgMethodEnd > bgMethodStart, '找不到 getWidgetBackgroundBitmap 方法体');
const backgroundMethod = manager.slice(bgMethodStart, bgMethodEnd);
assert(!backgroundMethod.includes('Paint.Style.STROKE'), '背景不能描一圈 widget_stroke 白边');
assert(!backgroundMethod.includes('canvas.drawRect'), '图片模式不能用主题色整块盖住图片');
assert(!manager.includes('BlurMaskFilter'), 'BlurMaskFilter 会把 alpha 扩散到圆角外导致四角溢出');
assert(backgroundMethod.includes('drawRoundRect(new android.graphics.RectF(0, 0, width, height), radius, radius, paint)'),
    '纯色与图片必须共用同一条 drawRoundRect 圆角路径');

// 图片分支：填充内容换成 BitmapShader，不允许再回到离屏图层 + DST_IN 裁切那一套（真机上四角仍然溢出）
const imageBranchStart = backgroundMethod.indexOf('int blurPx = dpToPx(');
const imageBranchEnd = backgroundMethod.indexOf('public static int getWidgetContentColor');
assert.ok(imageBranchStart >= 0 && imageBranchEnd > imageBranchStart, '找不到 getWidgetBackgroundBitmap 的图片分支');
const imageBranch = backgroundMethod.slice(imageBranchStart, imageBranchEnd);
assert(!imageBranch.includes('DST_IN'), '圆角不能再用 DST_IN 裁切');
assert(!imageBranch.includes('saveLayer'), '圆角不能再用离屏图层裁切');
assert(!imageBranch.includes('clipPaint'), '圆角不能再用第二个 paint 做混合裁切');
assert(!imageBranch.includes('clipPath'), 'clipPath 不抗锯齿，不能拿来切圆角');
assert(!imageBranch.includes('createScaledBitmap'), '图片分支不能只用一次降采样冒充模糊');
assert(imageBranch.includes('new android.graphics.BitmapShader('), '图片背景必须用 BitmapShader 填进圆角矩形');
assert(imageBranch.includes('setLocalMatrix'), 'BitmapShader 必须用局部矩阵做 centerCrop + overscan');
assert(imageBranch.includes('blurBitmap('), '图片背景必须先过真高斯模糊再上 shader');
assert(imageBranch.includes('setFilterBitmap(true)'), '小图放大回卡面尺寸必须开双线性过滤');

// 模糊实现：3 次可分离盒式模糊 ≈ 高斯，且必须纯 Java（RenderScript 已废弃、RenderEffect 要 API 31）
const blurStart = manager.indexOf('private static android.graphics.Bitmap blurBitmap(');
const blurEnd = manager.indexOf('public static synchronized android.graphics.Bitmap getThemedCheckedIcon');
assert.ok(blurStart >= 0 && blurEnd > blurStart, '找不到 blurBitmap 实现');
const blurCode = manager.slice(blurStart, blurEnd);
assert(blurCode.includes('pass < 3'), '必须跑满 3 轮盒式模糊逼近高斯，只降采样一次就是马赛克');
assert((blurCode.match(/boxBlur\(/g) || []).length === 3, 'boxBlur 应为 1 处定义 + 横向纵向各 1 处调用');
assert(!manager.includes('android.renderscript'), '模糊不能依赖已废弃的 RenderScript');
assert(!manager.includes('android.graphics.RenderEffect') && !manager.includes('setRenderEffect'), '模糊不能依赖 API 31 的 RenderEffect');
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

