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
const uncheckedBox = fs.readFileSync('android-src/main/res/drawable/widget_2x2_checkbox_unchecked.xml', 'utf8');

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
assert(widget2x2.includes('setImageViewBitmap(checks[i], WidgetDataManager.getThemedCheckBoxOutline(contentColor)'),
    '2x2 未勾选框必须用位图跟随卡面文字色');
assert(!widget2x2.includes('"setColorFilter"'), 'setColorFilter 的残留滤镜在 reapply 时清不掉，会把两色的打勾位图压成一个颜色');
assert(!widget2x2.includes('themeColor'), '2x2 不能再用主题色当文字色');

// 2x2 完成动画：RemoteViews 不能逐帧，只能三次刷新 —— 帧① 划掉前缀并压淡、帧② 整行划掉 + 打勾 + 淡出、帧③ 普通重绘收掉这一行
assert(manager.includes('public static synchronized android.graphics.Bitmap getThemedCheckedBox(int boxColor, int checkColor)'),
    '勾选方块必须分开收方块色与对勾色：跟随主题时卡面就是主题色，方块只能用卡面文字色');
assert(manager.includes('check.moveTo(13, 23)'), '方块里的对勾必须用量过的居中路点，不能照抄圆形图标那条偏右下的路径');
assert(manager.includes('public static synchronized android.graphics.Bitmap getThemedCheckBoxOutline(int color)'),
    '未勾选框也要位图化，描边色传卡面文字色');
const boxPath = 'canvas.drawRoundRect(new android.graphics.RectF(3, 3, 45, 45), 10, 10, paint)';
assert(manager.split(boxPath).length - 1 === 2, '未勾选描边与打勾方块要共用同一条方框路径，两态切换才不跳位');
assert(widget2x2.includes('getThemedCheckedBox('), '2x2 的 stage1 要用自绘的「填充方块 + 对勾」位图');
assert(widget2x2.includes('item.id.equals(completingId)'), 'stage1 的那一行按事项 id 认，不再预扫行号');
assert(widget2x2.includes('new android.text.SpannableString(') && widget2x2.includes('new android.text.style.StrikethroughSpan()'),
    '删除线必须由标题文字的 span 承担：画在背景位图上会被行文字挡住');
assert(manager.includes('getWidgetBackgroundBitmap(Context context, int widthDp, int heightDp)')
    && !manager.includes('strikeRow') && !widget2x2.includes('strikeRow'),
    '背景位图不接行号：v1.9.8 的三参数签名要恢复');
assert(widget2x2.includes('postDelayed(') && widget2x2.includes('= 130L') && widget2x2.includes('= 400L'),
    '帧② 要延后 130ms、帧③ 要延后 400ms 再做普通重绘');
assert(widget2x2.includes('new android.text.style.ForegroundColorSpan(') && widget2x2.includes('SPAN_EXCLUSIVE_EXCLUSIVE'),
    '前缀那一段要划线兼压淡：线色取的是文字 paint，白字划白线在真机上等于隐形');
assert((widget2x2Layout.match(/android:textStyle="bold"/g) || []).length === 8, '7 行标题与空态文案都要加粗');
assert(uncheckedBox.includes('android:width="2dp"'), '未勾选方框的描边要加粗到 2dp');
assert(!uncheckedBox.includes('1.5dp'), '未勾选方框不能再退回 1.5dp 的细描边');
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

