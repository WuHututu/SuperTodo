package com.dax.supertodo.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import com.dax.supertodo.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WidgetDataManager {
    private static final String FILE_NAME = "supertodo_widget_data.json";
    private static final String BG_FILE_NAME = "supertodo_widget_bg.img";
    private static final String PREF_NAME = "supertodo_widget_prefs";

    public static final String ACTION_WIDGET_CLICK = "com.dax.supertodo.ACTION_WIDGET_CLICK";
    public static final String ACTION_REFRESH_WIDGET = "com.dax.supertodo.ACTION_REFRESH_WIDGET";

    public static final String GROUP_ALL = "all";
    public static final String GROUP_SCENE = "scene";
    public static final String GROUP_TIME = "time";

    public static final String SORT_DEFAULT = "default";
    public static final String SORT_STAR = "star";
    public static final String SORT_DUE = "due";
    public static final String SORT_COST = "cost";
    public static final String SORT_UNDONE_FIRST = "undone_first";

    private static volatile String sCachedJson = null;
    private static volatile int sCheckedIconColor = 0;
    private static volatile android.graphics.Bitmap sCheckedIcon = null;
    private static volatile int sWidgetBgHash = 0;
    private static volatile int sWidgetBgLength = 0;
    private static volatile android.graphics.Bitmap sWidgetBgImage = null;
    private static volatile long sWidgetBgImageTime = 0L;
    private static volatile int sWidgetBgImageWidth = 0;
    private static volatile int sWidgetBgImageHeight = 0;
    private static volatile int sWidgetContentColor = 0;
    private static volatile android.graphics.Bitmap sWidgetBgAvgSource = null;
    private static volatile int sWidgetBgAvgColor = 0;
    private static final java.util.concurrent.ExecutorService sIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static synchronized void saveWidgetData(Context context, String json) {
        if (context == null || json == null) return;
        sCachedJson = json;
        final Context appContext = context.getApplicationContext();
        sIoExecutor.execute(() -> {
            try {
                File dir = appContext.getFilesDir();
                File file = new File(dir, FILE_NAME);
                File tempFile = new File(dir, FILE_NAME + ".tmp");
                FileOutputStream fos = new FileOutputStream(tempFile);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.close();
                if (tempFile.exists()) {
                    tempFile.renameTo(file);
                }
            } catch (Exception ignore) {}
        });
    }

    /**
     * 保存 2x2 小部件的背景图片：接受 data:image/...;base64,xxx 数据 URL 或纯 base64，解码后用 .tmp + rename 原子写入独立文件。
     * 入参为空或解码失败时返回 false 且不覆盖已存在的图片；与上次写入内容相同则直接跳过，避免每次防抖保存都重写文件。
     * @param context 上下文
     * @param base64DataUrl 背景图片的 base64 内容（可带 data URL 前缀）
     * @return 是否已写入或无需重复写入
     */
    public static boolean saveWidgetBackgroundImage(Context context, String base64DataUrl) {
        if (context == null || base64DataUrl == null || base64DataUrl.isEmpty()) return false;
        String base64 = base64DataUrl;
        if (base64.startsWith("data:")) {
            int comma = base64.indexOf(',');
            if (comma < 0) return false;
            base64 = base64.substring(comma + 1);
        }
        if (base64.length() == sWidgetBgLength && base64.hashCode() == sWidgetBgHash) return true;
        byte[] bytes;
        try {
            bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        } catch (Exception ignore) {
            return false;
        }
        if (bytes == null || bytes.length == 0) return false;
        sWidgetBgLength = base64.length();
        sWidgetBgHash = base64.hashCode();
        sWidgetBgImage = null;
        final Context appContext = context.getApplicationContext();
        final byte[] data = bytes;
        sIoExecutor.execute(() -> {
            try {
                File dir = appContext.getFilesDir();
                File file = new File(dir, BG_FILE_NAME);
                File tempFile = new File(dir, BG_FILE_NAME + ".tmp");
                FileOutputStream fos = new FileOutputStream(tempFile);
                fos.write(data);
                fos.flush();
                fos.close();
                if (tempFile.exists()) {
                    tempFile.renameTo(file);
                }
            } catch (Exception ignore) {}
        });
        return true;
    }

    /**
     * 从待落盘的 widget JSON 中取出 customBg.image 背景图片交给 saveWidgetBackgroundImage 单独存文件，并返回已清空该字段的 JSON，
     * 防止 MB 级 base64 进入每次刷新都要整体重新解析的数据文件。
     * @param context 上下文
     * @param json 前端同步过来的完整 JSON
     * @return 去掉图片内容后的 JSON，无需处理或解析失败时原样返回
     */
    public static String extractWidgetBackgroundImage(Context context, String json) {
        if (context == null || json == null || json.isEmpty()) return json;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject customBg = root.optJSONObject("customBg");
            String image = customBg != null ? customBg.optString("image", "") : "";
            if (image.isEmpty()) return json;
            saveWidgetBackgroundImage(context, image);
            customBg.put("image", "");
            return root.toString();
        } catch (Exception ignore) {
            return json;
        }
    }

    public static synchronized String getWidgetData(Context context) {
        if (sCachedJson != null && !sCachedJson.isEmpty()) {
            return sCachedJson;
        }
        if (context == null) return "";
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            sCachedJson = sb.toString();
        } catch (Exception ignore) {}
        return sCachedJson != null ? sCachedJson : "";
    }

    public static List<TodoItem> loadAllItems(Context context) {
        List<TodoItem> list = new ArrayList<>();
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            Set<String> trashIds = new HashSet<>();
            JSONArray trash = root.optJSONArray("trash");
            if (trash != null) {
                for (int i = 0; i < trash.length(); i++) {
                    JSONObject item = trash.optJSONObject(i);
                    if (item != null && !item.optString("id", "").isEmpty()) {
                        trashIds.add(item.optString("id"));
                    }
                }
            }
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.optJSONObject(i);
                    if (obj != null && !trashIds.contains(obj.optString("id", ""))) {
                        list.add(TodoItem.fromJson(obj));
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static List<String> loadTags(Context context, String tagKey) {
        List<String> list = new ArrayList<>();
        String json = getWidgetData(context);
        if (!json.isEmpty()) {
            try {
                JSONObject root = new JSONObject(json);
                JSONArray arr = root.optJSONArray(tagKey);
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, "");
                        if (!s.isEmpty()) list.add(s);
                    }
                }
            } catch (Exception ignore) {}
        }
        if (list.isEmpty()) {
            if ("scenes".equals(tagKey)) {
                Collections.addAll(list, "家里", "学校", "出差", "网上", "线下");
            } else if ("times".equals(tagKey)) {
                Collections.addAll(list, "今年", "明年", "以后再说");
            } else if ("types".equals(tagKey)) {
                Collections.addAll(list, "购物", "待办", "计划", "旅游", "愿望");
            }
        }
        return list;
    }

    public static List<String> loadAvailableScenes(Context context) {
        return loadTags(context, "scenes");
    }

    public static List<String> loadAvailableTimes(Context context) {
        return loadTags(context, "times");
    }

    public static List<String> loadAvailableTypes(Context context) {
        return loadTags(context, "types");
    }

    public static boolean isWidgetRemoveDone(Context context) {
        if (context == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            return isWidgetRemoveDone(root);
        } catch (Exception ignore) {
            return false;
        }
    }

    public static boolean isWidgetRemoveDone(JSONObject root) {
        if (root == null) return false;
        return root.optBoolean("widgetRemoveDone", false);
    }

    public static int getWidgetThemeColor(Context context) {
        int fallback = android.graphics.Color.rgb(11, 87, 208);
        if (context == null) return fallback;
        try {
            fallback = androidx.core.content.ContextCompat.getColor(context, R.color.widget_primary);
            String theme = new JSONObject(getWidgetData(context)).optString("theme", "");
            if (theme.matches("^#[0-9a-fA-F]{6}$")) {
                return android.graphics.Color.parseColor(theme);
            }
        } catch (Exception ignore) {}
        return fallback;
    }

    public static int getWidgetBackgroundColor(Context context) {
        boolean dark = context != null && (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int fallback = android.graphics.Color.parseColor(dark ? "#111318" : "#f2f5fb");
        if (context == null) return fallback;
        try {
            JSONObject root = new JSONObject(getWidgetData(context));
            JSONObject widgetBackground = root.optJSONObject("widget2x2Background");
            int opacity = widgetBackground != null ? widgetBackground.optInt("opacity", 80) : 80;
            String theme = root.optString("theme", "");
            int color = isHexColor(theme) ? android.graphics.Color.parseColor(theme) : fallback;
            int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
            return android.graphics.Color.argb(
                    alpha,
                    android.graphics.Color.red(color),
                    android.graphics.Color.green(color),
                    android.graphics.Color.blue(color)
            );
        } catch (Exception ignore) {
            return fallback;
        }
    }

    public static int getWidgetBackgroundBlur(Context context) {
        if (context == null) return 12;
        try {
            JSONObject root = new JSONObject(getWidgetData(context));
            JSONObject widgetBackground = root.optJSONObject("widget2x2Background");
            return Math.max(0, Math.min(30, widgetBackground != null ? widgetBackground.optInt("blur", 12) : 12));
        } catch (Exception ignore) {
            return 12;
        }
    }

    /**
     * 生成 2x2 小组件的背景层位图（带透明圆角四角）。两个滑块语义：
     * opacity = 背景层自身的 alpha —— 主题模式是纯色块的 alpha，图片模式是图片本身的 alpha，桌面壁纸从下面透出来；
     * blur = 图片自身的高斯模糊半径（降采样 + 双线性放大近似，零依赖、任何 API 都能跑）——
     *        主题模式没有可糊的对象，改它不产生视觉效果，这是预期行为。
     * 图片模式不再叠任何主题色遮罩，也不再画一圈描边；圆角一律用硬边抗锯齿 DST_IN 裁切，
     * 不能拿模糊滤镜当圆角蒙版，否则模糊会把 alpha 向圆角外扩散，四角溢出半透明填充。
     * 副作用：顺手记录本次卡面应使用的文字色，供 getWidgetContentColor 读取。
     * @param context 上下文
     * @param widthDp 小部件宽度（dp）
     * @param heightDp 小部件高度（dp）
     * @return 背景位图
     */
    public static android.graphics.Bitmap getWidgetBackgroundBitmap(Context context, int widthDp, int heightDp) {
        int width = Math.max(1, Math.min(dpToPx(context, Math.max(widthDp, 1)), 512));
        int height = Math.max(1, Math.min(dpToPx(context, Math.max(heightDp, 1)), 512));
        float radius = dpToPx(context, 18);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        String bgType = "theme";
        int opacity = 80;
        try {
            JSONObject widgetBackground = new JSONObject(getWidgetData(context)).optJSONObject("widget2x2Background");
            if (widgetBackground != null) {
                bgType = widgetBackground.optString("type", "theme");
                opacity = widgetBackground.optInt("opacity", 80);
            }
        } catch (Exception ignore) {}
        // 只有显式选 image 且图片文件可解码时才走图片分支，否则回退主题色（保持原行为）
        android.graphics.Bitmap bgImage = "image".equals(bgType) ? loadWidgetBackgroundImage(context, width, height) : null;

        if (bgImage == null) {
            sWidgetContentColor = contentColorFor(getWidgetThemeColor(context));
            // getWidgetBackgroundColor 已经把 opacity 换算进 alpha，纯色分支直接画圆角矩形即可，不需要 saveLayer
            paint.setColor(getWidgetBackgroundColor(context));
            canvas.drawRoundRect(new android.graphics.RectF(0, 0, width, height), radius, radius, paint);
            return bitmap;
        }

        int blurPx = dpToPx(context, getWidgetBackgroundBlur(context));
        android.graphics.Bitmap blurred = bgImage;
        if (blurPx > 0) {
            // 降采样比例 f = 1 + blurPx / 4，clamp 到 [1, 32]；放大时用双线性过滤，一次降采样+放大对背景模糊足够
            float factor = Math.max(1F, Math.min(32F, 1 + blurPx / 4F));
            blurred = android.graphics.Bitmap.createScaledBitmap(bgImage,
                    Math.max(1, Math.round(bgImage.getWidth() / factor)),
                    Math.max(1, Math.round(bgImage.getHeight() / factor)), true);
            paint.setFilterBitmap(true);
        }
        sWidgetContentColor = contentColorFor(averageColorOf(blurred));

        int layer = canvas.saveLayer(new android.graphics.RectF(0, 0, width, height), null);
        paint.setAlpha(Math.max(0, Math.min(100, opacity)) * 255 / 100);
        // 模糊会让位图边缘 alpha 衰减，centerCrop 时目标矩形每边向外扩 blurPx 做 overscan，保证圆角内侧不留透明边
        float overscan = blurPx;
        float targetW = width + overscan * 2;
        float targetH = height + overscan * 2;
        float scale = Math.max(targetW / blurred.getWidth(), targetH / blurred.getHeight());
        float drawW = blurred.getWidth() * scale;
        float drawH = blurred.getHeight() * scale;
        canvas.drawBitmap(blurred, null, new android.graphics.RectF(
                (targetW - drawW) / 2F - overscan, (targetH - drawH) / 2F - overscan,
                (targetW + drawW) / 2F - overscan, (targetH + drawH) / 2F - overscan), paint);
        // 裁切前必须把 alpha 复位成 255：在 globalAlpha < 255 时做 DST_IN，圆角外像素的 alpha 会等于填充 alpha，
        // 结果 opacity=100% 时四角反而透不出壁纸
        paint.setAlpha(255);
        // 裁切用新建的 paint：只带 DST_IN 与抗锯齿，没有 MaskFilter、没有半透明
        android.graphics.Paint clipPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        clipPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        canvas.drawRoundRect(new android.graphics.RectF(0, 0, width, height), radius, radius, clipPaint);
        canvas.restoreToCount(layer);
        return bitmap;
    }

    /**
     * 返回 2x2 卡面上文字与勾选框应该用的颜色（亮背景深字、暗背景白字）。
     * 取的是上一次 getWidgetBackgroundBitmap 走哪个分支算出的结果，必须在它之后调用；
     * 万一调用顺序颠倒，就退回按主题色算的默认值。
     * @param context 上下文
     * @return 不透明的 ARGB 文字色
     */
    public static int getWidgetContentColor(Context context) {
        int cached = sWidgetContentColor;
        return cached != 0 ? cached : contentColorFor(getWidgetThemeColor(context));
    }

    /**
     * 求一张图的平均 RGB，用来代表图片分支的有效背景色。
     * 直接复用模糊时那张降采样小图（像素量已经很小），不额外解码原图或再缩放一次；
     * 缓存只按位图实例判等——平均色与画布宽高无关，不能跟 loadWidgetBackgroundImage 的宽高缓存混在一起。
     * @param sample 参与求均值的小图
     * @return 不透明的平均 RGB
     */
    private static synchronized int averageColorOf(android.graphics.Bitmap sample) {
        if (sWidgetBgAvgSource == sample && sWidgetBgAvgColor != 0) return sWidgetBgAvgColor;
        int w = sample.getWidth();
        int h = sample.getHeight();
        int[] row = new int[w];
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        for (int y = 0; y < h; y++) {
            sample.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                sumR += android.graphics.Color.red(row[x]);
                sumG += android.graphics.Color.green(row[x]);
                sumB += android.graphics.Color.blue(row[x]);
            }
        }
        long count = (long) w * h;
        sWidgetBgAvgSource = sample;
        sWidgetBgAvgColor = android.graphics.Color.rgb((int) (sumR / count), (int) (sumG / count), (int) (sumB / count));
        return sWidgetBgAvgColor;
    }

    /**
     * 按 WCAG 相对亮度决定卡面文字色：sRGB 线性化后加权求 L，L > 0.45 用深色字，否则用白字。
     * @param color 有效背景色（不透明的 RGB / ARGB，只看 RGB 通道）
     * @return 0xFF1A1A1A 或 0xFFFFFFFF
     */
    private static int contentColorFor(int color) {
        double[] channels = new double[] {
                android.graphics.Color.red(color) / 255.0,
                android.graphics.Color.green(color) / 255.0,
                android.graphics.Color.blue(color) / 255.0
        };
        for (int i = 0; i < 3; i++) {
            double c = channels[i];
            channels[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        double luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
        return luminance > 0.45 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    /**
     * 读取并缓存 2x2 小部件的背景图片位图：按文件修改时间与目标像素尺寸复用缓存，避免每次 onUpdate 都重新解码 MB 级图片。
     * @param context 上下文
     * @param width 目标画布宽度（px）
     * @param height 目标画布高度（px）
     * @return 解码后的位图，图片文件不存在或解码失败时返回 null 以回退到主题色
     */
    private static synchronized android.graphics.Bitmap loadWidgetBackgroundImage(Context context, int width, int height) {
        File file = new File(context.getFilesDir(), BG_FILE_NAME);
        if (!file.exists()) return null;
        long modified = file.lastModified();
        if (sWidgetBgImage != null && sWidgetBgImageTime == modified && sWidgetBgImageWidth == width && sWidgetBgImageHeight == height) {
            return sWidgetBgImage;
        }
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= width && bounds.outHeight / (sampleSize * 2) >= height) {
            sampleSize *= 2;
        }
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) return null;
        sWidgetBgImageTime = modified;
        sWidgetBgImageWidth = width;
        sWidgetBgImageHeight = height;
        sWidgetBgImage = bitmap;
        return bitmap;
    }

    public static synchronized android.graphics.Bitmap getThemedCheckedIcon(int color) {
        if (sCheckedIcon != null && sCheckedIconColor == color) return sCheckedIcon;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawCircle(24, 24, 22, paint);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        android.graphics.Path check = new android.graphics.Path();
        check.moveTo(13, 25);
        check.lineTo(21, 33);
        check.lineTo(36, 16);
        canvas.drawPath(check, paint);
        sCheckedIconColor = color;
        sCheckedIcon = bitmap;
        return bitmap;
    }

    public static synchronized boolean toggleItemDone(Context context, String itemId) {
        if (context == null || itemId == null || itemId.isEmpty()) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONArray items = root.optJSONArray("items");
            if (items == null) return false;
            boolean updated = false;
            boolean nextState = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.optJSONObject(i);
                if (obj != null && itemId.equals(obj.optString("id"))) {
                    boolean cur = obj.optBoolean("done", false);
                    nextState = !cur;
                    updateDoneMetadata(obj, nextState);
                    updated = true;
                    break;
                }
            }
            // 同步更新四象限中包含的同 ID 事项状态
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw != null) {
                String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                for (String qKey : qKeys) {
                    JSONArray arr = qw.optJSONArray(qKey);
                    if (arr == null) continue;
                    for (int j = arr.length() - 1; j >= 0; j--) {
                        JSONObject qObj = arr.optJSONObject(j);
                        if (qObj != null && itemId.equals(qObj.optString("id"))) {
                            if (removeDone && nextState) {
                                arr.remove(j);
                            } else {
                                updateDoneMetadata(qObj, nextState);
                            }
                            updated = true;
                        }
                    }
                }
            }
            if (updated) {
                root.put("dataUpdatedAt", System.currentTimeMillis());
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static synchronized List<TodoItem> load2x2Items(Context context) {
        List<TodoItem> list = new ArrayList<>();
        if (context == null) return list;
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("widget2x2");
            JSONArray items = root.optJSONArray("items");
            Set<String> trashIds = new HashSet<>();
            JSONArray trash = root.optJSONArray("trash");
            if (trash != null) {
                for (int i = 0; i < trash.length(); i++) {
                    JSONObject item = trash.optJSONObject(i);
                    if (item != null && !item.optString("id", "").isEmpty()) {
                        trashIds.add(item.optString("id"));
                    }
                }
            }
            Map<String, JSONObject> itemById = new HashMap<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null && !trashIds.contains(item.optString("id", ""))
                            && !item.optString("id", "").isEmpty()) {
                        itemById.put(item.optString("id"), item);
                    }
                }
            }
            List<String> orderedIds = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject entry = arr.optJSONObject(i);
                    String id = entry != null ? entry.optString("id", "") : arr.optString(i, "");
                    if (itemById.containsKey(id) && !orderedIds.contains(id)) {
                        orderedIds.add(id);
                    }
                }
            }
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    String id = item != null ? item.optString("id", "") : "";
                    if (item != null && !item.optBoolean("done", false) && !orderedIds.contains(id)) {
                        orderedIds.add(id);
                    }
                }
            }
            for (String id : orderedIds) {
                JSONObject item = itemById.get(id);
                if (item != null && !item.optBoolean("done", false)) {
                    list.add(TodoItem.fromJson(item));
                }
            }
            if ((arr == null || arr.length() == 0) && !orderedIds.isEmpty()) {
                JSONArray new2x2 = new JSONArray();
                for (String id : orderedIds) {
                    new2x2.put(new JSONObject().put("id", id));
                }
                root.put("widget2x2", new2x2);
                root.put("dataUpdatedAt", System.currentTimeMillis());
                saveWidgetData(context, root.toString());
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static synchronized boolean completeNext2x2Item(Context context) {
        if (context == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("widget2x2");
            if (arr == null || arr.length() == 0) {
                load2x2Items(context);
                json = getWidgetData(context);
                root = new JSONObject(json);
                arr = root.optJSONArray("widget2x2");
            }
            if (arr == null || arr.length() == 0) return false;

            String completedId = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                String id = obj != null ? obj.optString("id", "") : arr.optString(i, "");
                JSONObject item = findItem(root.optJSONArray("items"), id);
                if (item != null && !item.optBoolean("done", false)) {
                    updateDoneMetadata(item, true);
                    completedId = id;
                    break;
                }
            }
            if (completedId != null) {
                // 同步更新四象限
                JSONObject qw = root.optJSONObject("quadrantWidget");
                if (qw != null) {
                    String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                    for (String qKey : qKeys) {
                        JSONArray qArr = qw.optJSONArray(qKey);
                        if (qArr == null) continue;
                        for (int k = 0; k < qArr.length(); k++) {
                            JSONObject qObj = qArr.optJSONObject(k);
                            if (qObj != null && completedId.equals(qObj.optString("id"))) {
                                updateDoneMetadata(qObj, true);
                            }
                        }
                    }
                }
                root.put("dataUpdatedAt", System.currentTimeMillis());
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveWidgetConfig(Context context, int widgetId, String groupBy, String category, String typeCategory, String sortKey, boolean sortAsc, boolean hideDone) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putString("config_group_by_" + widgetId, groupBy != null ? groupBy : GROUP_SCENE);
        editor.putString("config_category_" + widgetId, category != null ? category : "全部");
        editor.putString("config_type_category_" + widgetId, typeCategory != null ? typeCategory : "全部标签");
        editor.putString("config_sort_key_" + widgetId, sortKey != null ? sortKey : SORT_DEFAULT);
        editor.putBoolean("config_sort_asc_" + widgetId, sortAsc);
        editor.putBoolean("config_hide_done_" + widgetId, hideDone);
        editor.apply();
    }

    public static String getWidgetGroupBy(Context context, int widgetId) {
        return getPrefs(context).getString("config_group_by_" + widgetId, GROUP_SCENE);
    }

    public static String getWidgetCategory(Context context, int widgetId) {
        return getPrefs(context).getString("config_category_" + widgetId, "全部");
    }

    public static String getWidgetTypeCategory(Context context, int widgetId) {
        return getPrefs(context).getString("config_type_category_" + widgetId, "全部标签");
    }

    public static String getWidgetSortKey(Context context, int widgetId) {
        return getPrefs(context).getString("config_sort_key_" + widgetId, SORT_DEFAULT);
    }

    public static boolean getWidgetSortAsc(Context context, int widgetId) {
        return getPrefs(context).getBoolean("config_sort_asc_" + widgetId, true);
    }

    public static boolean getWidgetHideDone(Context context, int widgetId) {
        return getPrefs(context).getBoolean("config_hide_done_" + widgetId, false);
    }

    public static void removeWidgetConfig(Context context, int widgetId) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.remove("config_group_by_" + widgetId);
        editor.remove("config_category_" + widgetId);
        editor.remove("config_type_category_" + widgetId);
        editor.remove("config_sort_key_" + widgetId);
        editor.remove("config_sort_asc_" + widgetId);
        editor.remove("config_hide_done_" + widgetId);
        editor.apply();
    }

    public static String getWidgetFilterTitle(Context context, int widgetId) {
        String groupBy = getWidgetGroupBy(context, widgetId);
        String category = getWidgetCategory(context, widgetId);
        String typeCategory = getWidgetTypeCategory(context, widgetId);
        StringBuilder title = new StringBuilder();
        if (GROUP_SCENE.equals(groupBy) && !"全部".equals(category) && category != null && !category.trim().isEmpty()) {
            title.append(category);
        } else if (GROUP_TIME.equals(groupBy) && !"全部".equals(category) && category != null && !category.trim().isEmpty()) {
            title.append(category);
        }
        if (!"全部标签".equals(typeCategory) && !"全部".equals(typeCategory) && typeCategory != null && !typeCategory.trim().isEmpty()) {
            if (title.length() > 0) title.append(" · ");
            title.append(typeCategory);
        }
        return title.length() > 0 ? title.toString() : "全部事项";
    }

    public static List<TodoItem> loadTasksForWidget(Context context, int widgetId) {
        List<TodoItem> all = loadAllItems(context);
        String groupBy = getWidgetGroupBy(context, widgetId);
        String category = getWidgetCategory(context, widgetId);
        String typeCategory = getWidgetTypeCategory(context, widgetId);
        String sortKey = getWidgetSortKey(context, widgetId);
        final boolean sortAsc = getWidgetSortAsc(context, widgetId);
        boolean removeDone = isWidgetRemoveDone(context);
        boolean hideDone = getWidgetHideDone(context, widgetId) || removeDone;

        List<TodoItem> filtered = new ArrayList<>();
        for (TodoItem it : all) {
            if (hideDone && it.done) continue;

            boolean matchesGroup = true;
            if (GROUP_SCENE.equals(groupBy)) {
                if ("未分组".equals(category)) {
                    matchesGroup = it.scene == null || it.scene.isEmpty();
                } else if (!"全部".equals(category)) {
                    matchesGroup = category.equals(it.scene);
                }
            } else if (GROUP_TIME.equals(groupBy)) {
                if ("未分组".equals(category)) {
                    matchesGroup = it.time == null || it.time.isEmpty();
                } else if (!"全部".equals(category)) {
                    matchesGroup = category.equals(it.time);
                }
            }
            boolean matchesType = "全部标签".equals(typeCategory) || "全部".equals(typeCategory)
                || typeCategory == null || typeCategory.trim().isEmpty() || it.types.contains(typeCategory);
            if (matchesGroup && matchesType) {
                filtered.add(it);
            }
        }

        Collections.sort(filtered, new Comparator<TodoItem>() {
            @Override
            public int compare(TodoItem a, TodoItem b) {
                if (SORT_UNDONE_FIRST.equals(sortKey)) {
                    if (a.done != b.done) {
                        return a.done ? 1 : -1;
                    }
                    return Long.compare(a.created, b.created);
                }
                if (SORT_STAR.equals(sortKey)) {
                    int cmp = Integer.compare(a.star, b.star);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                } else if (SORT_DUE.equals(sortKey)) {
                    if (a.due.isEmpty() && b.due.isEmpty()) return 0;
                    if (a.due.isEmpty()) return 1;
                    if (b.due.isEmpty()) return -1;
                    int cmp = a.due.compareTo(b.due);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                } else if (SORT_COST.equals(sortKey)) {
                    int cmp = Double.compare(a.cost, b.cost);
                    if (cmp != 0) return sortAsc ? cmp : -cmp;
                }
                int orderCmp = Integer.compare(a.order, b.order);
                if (orderCmp != 0) return sortAsc ? orderCmp : -orderCmp;
                return Long.compare(a.created, b.created);
            }
        });

        return filtered;
    }

    public static List<TodoItem> loadQuadrantItems(Context context, String qKey) {
        List<TodoItem> list = new ArrayList<>();
        if (context == null || qKey == null) return list;
        String json = getWidgetData(context);
        if (json.isEmpty()) return list;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw != null) {
                JSONArray arr = qw.optJSONArray(qKey);
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            TodoItem it = TodoItem.fromJson(obj);
                            if (removeDone && it.done) {
                                continue;
                            }
                            list.add(it);
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static synchronized boolean toggleQuadrantItemDone(Context context, String qKey, String itemId) {
        if (context == null || qKey == null || itemId == null) return false;
        String json = getWidgetData(context);
        if (json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            boolean removeDone = isWidgetRemoveDone(root);
            JSONObject qw = root.optJSONObject("quadrantWidget");
            if (qw == null) return false;
            JSONArray arr = qw.optJSONArray(qKey);
            if (arr == null) return false;
            boolean updated = false;
            boolean nextState = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && itemId.equals(obj.optString("id"))) {
                    boolean cur = obj.optBoolean("done", false);
                    nextState = !cur;
                    if (removeDone && nextState) {
                        arr.remove(i);
                    } else {
                        updateDoneMetadata(obj, nextState);
                    }
                    updated = true;
                    break;
                }
            }
            if (updated) {
                // 保持与主列表对应待办事项完成状态双向同步
                JSONArray rootItems = root.optJSONArray("items");
                if (rootItems != null) {
                    for (int j = 0; j < rootItems.length(); j++) {
                        JSONObject rootIt = rootItems.optJSONObject(j);
                        if (rootIt != null && itemId.equals(rootIt.optString("id"))) {
                            updateDoneMetadata(rootIt, nextState);
                            break;
                        }
                    }
                }
                // 同步更新其他象限中可能存在的同 ID 事项
                String[] qKeys = new String[] { "q1", "q2", "q3", "q4" };
                for (String otherKey : qKeys) {
                    if (otherKey.equals(qKey)) continue;
                    JSONArray otherArr = qw.optJSONArray(otherKey);
                    if (otherArr == null) continue;
                    for (int k = otherArr.length() - 1; k >= 0; k--) {
                        JSONObject oObj = otherArr.optJSONObject(k);
                        if (oObj != null && itemId.equals(oObj.optString("id"))) {
                            if (removeDone && nextState) {
                                otherArr.remove(k);
                            } else {
                                updateDoneMetadata(oObj, nextState);
                            }
                        }
                    }
                }
                root.put("dataUpdatedAt", System.currentTimeMillis());
                saveWidgetData(context, root.toString());
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static void notifyAllWidgets(Context context) {
        if (context == null) return;
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr == null) return;

            ComponentName cn4x2 = new ComponentName(context, TodoWidget4x2Provider.class);
            int[] ids4x2 = mgr.getAppWidgetIds(cn4x2);
            if (ids4x2 != null && ids4x2.length > 0) {
                mgr.notifyAppWidgetViewDataChanged(ids4x2, R.id.widget_list);
                for (int id : ids4x2) {
                    TodoWidget4x2Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cn4x4 = new ComponentName(context, TodoWidget4x4Provider.class);
            int[] ids4x4 = mgr.getAppWidgetIds(cn4x4);
            if (ids4x4 != null && ids4x4.length > 0) {
                mgr.notifyAppWidgetViewDataChanged(ids4x4, R.id.widget_list);
                for (int id : ids4x4) {
                    TodoWidget4x4Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cn2x2 = new ComponentName(context, TodoWidget2x2Provider.class);
            int[] ids2x2 = mgr.getAppWidgetIds(cn2x2);
            if (ids2x2 != null && ids2x2.length > 0) {
                for (int id : ids2x2) {
                    TodoWidget2x2Provider.updateAppWidget(context, mgr, id);
                }
            }

            ComponentName cnQuad = new ComponentName(context, TodoWidgetQuadrantProvider.class);
            int[] idsQuad = mgr.getAppWidgetIds(cnQuad);
            if (idsQuad != null && idsQuad.length > 0) {
                for (int id : idsQuad) {
                    TodoWidgetQuadrantProvider.updateAppWidget(context, mgr, id);
                }
            }

        } catch (Exception ignore) {}
    }

    public static int getWidgetWidth(Context context, AppWidgetManager mgr, int appWidgetId, int defaultWidth) {
        if (mgr == null || context == null) return defaultWidth;
        try {
            Bundle options = mgr.getAppWidgetOptions(appWidgetId);
            if (options == null) return defaultWidth;
            boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int w = isLandscape ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            if (w <= 0) {
                w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            }
            return w > 0 ? w : defaultWidth;
        } catch (Throwable t) {
            return defaultWidth;
        }
    }

    public static int getWidgetHeight(Context context, AppWidgetManager mgr, int appWidgetId, int defaultHeight) {
        if (mgr == null || context == null) return defaultHeight;
        try {
            Bundle options = mgr.getAppWidgetOptions(appWidgetId);
            if (options == null) return defaultHeight;
            boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int h = isLandscape ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h <= 0) {
                h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            }
            return h > 0 ? h : defaultHeight;
        } catch (Throwable t) {
            return defaultHeight;
        }
    }

    private static boolean isHexColor(String value) {
        return value != null && value.matches("^#[0-9a-fA-F]{6}$");
    }

    private static void updateDoneMetadata(JSONObject item, boolean done) {
        if (item == null) return;
        try {
            item.put("done", done);
            JSONArray scenes = item.optJSONArray("scenes");
            if (scenes == null && !item.optString("scene", "").isEmpty()) {
                scenes = new JSONArray().put(item.optString("scene"));
            }
            JSONArray types = item.optJSONArray("types");
            if (types == null && !item.optString("type", "").isEmpty()) {
                types = new JSONArray().put(item.optString("type"));
            }
            JSONArray doneScenes = new JSONArray();
            JSONArray doneTypes = new JSONArray();
            if (done) {
                if (scenes != null) {
                    for (int i = 0; i < scenes.length(); i++) doneScenes.put(scenes.optString(i));
                }
                if (types != null) {
                    for (int i = 0; i < types.length(); i++) doneTypes.put(types.optString(i));
                }
            }
            item.put("doneScenes", doneScenes);
            item.put("doneTypes", doneTypes);
        } catch (Exception ignore) {}
    }

    private static JSONObject findItem(JSONArray items, String itemId) {
        if (items == null || itemId == null || itemId.isEmpty()) return null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && itemId.equals(item.optString("id"))) return item;
        }
        return null;
    }

    private static int dpToPx(Context context, int dp) {
        float density = context != null ? context.getResources().getDisplayMetrics().density : 1F;
        return Math.round(dp * density);
    }
}
