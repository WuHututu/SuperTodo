package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.dax.supertodo.R;

import java.util.List;

public class TodoWidget2x2Provider extends AppWidgetProvider {
    public static final String ACTION_2X2_COMPLETE = "com.dax.supertodo.ACTION_2X2_COMPLETE";
    public static final String EXTRA_ITEM_ID = "item_id";
    // 完成动画的淡出档位：满线帧整行文字 50%、勾选框 70%，一眼看得出这一行正在消失但仍认得清
    private static final int STAGE1_TEXT_ALPHA = 0x80;
    private static final int STAGE1_ICON_ALPHA = 0xB3;
    // 前缀帧只划掉的那一截。StrikethroughSpan 的线色取的就是文字 paint（连 alpha 一起），
    // 白字上划白线只在字形缝隙里露一点，所以这一帧必须划线和变淡一起做
    private static final int STAGE1_PARTIAL_ALPHA = 0x40;
    // RemoteViews 没有逐帧动画，只能分段画：帧② 整行划掉并打勾，帧③ 普通重绘收掉这一行
    private static final long STAGE2_MS = 130L;
    private static final long STAGE3_MS = 400L;

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        updateAppWidget(context, appWidgetManager, appWidgetId, null, null, 0);
    }

    /**
     * 渲染 2x2 卡面。
     * @param completingId 完成动画要突出的那一条事项 id；普通重绘传 null
     * @param stageItems 完成动画的列表快照。必须在 toggleItemDone 之前取：
     *                   load2x2Items 会把已完成的条目整个过滤掉，切换之后再也读不到这一条
     * @param frame 0=普通重绘、1=只划掉标题前缀、2=整行划掉并打勾
     */
    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                                        String completingId, List<TodoItem> stageItems, int frame) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_2x2);
        int width = WidgetDataManager.getWidgetWidth(context, appWidgetManager, appWidgetId, 120);
        int height = WidgetDataManager.getWidgetHeight(context, appWidgetManager, appWidgetId, 120);
        views.setImageViewBitmap(R.id.widget_2x2_bg, WidgetDataManager.getWidgetBackgroundBitmap(context, width, height));
        int contentColor = WidgetDataManager.getWidgetContentColor(context);

        int[] rows = {
                R.id.widget_2x2_row1, R.id.widget_2x2_row2, R.id.widget_2x2_row3,
                R.id.widget_2x2_row4, R.id.widget_2x2_row5, R.id.widget_2x2_row6,
                R.id.widget_2x2_row7
        };
        int[] checks = {
                R.id.widget_2x2_check1, R.id.widget_2x2_check2, R.id.widget_2x2_check3,
                R.id.widget_2x2_check4, R.id.widget_2x2_check5, R.id.widget_2x2_check6,
                R.id.widget_2x2_check7
        };
        int[] titles = {
                R.id.widget_2x2_title1, R.id.widget_2x2_title2, R.id.widget_2x2_title3,
                R.id.widget_2x2_title4, R.id.widget_2x2_title5, R.id.widget_2x2_title6,
                R.id.widget_2x2_title7
        };
        for (int i = 0; i < titles.length; i++) {
            views.setTextColor(titles[i], contentColor);
            // 勾选框两种状态都走位图。setColorFilter 设进的滤镜在 reapply 时清不掉（宿主复用视图），
            // 会把之后打勾那张两色位图压成一个颜色、对勾隐形，所以每次渲染都显式重设图片
            views.setImageViewBitmap(checks[i], WidgetDataManager.getThemedCheckBoxOutline(contentColor));
            views.setViewVisibility(rows[i], View.GONE);
        }
        views.setTextColor(R.id.widget_2x2_empty_view, contentColor);

        List<TodoItem> items = stageItems != null ? stageItems : WidgetDataManager.load2x2Items(context);
        int capacity = getVisibleRowCount(context, height);
        int shown = 0;
        for (TodoItem item : items) {
            if (item.done || shown >= capacity) {
                continue;
            }
            views.setViewVisibility(rows[shown], View.VISIBLE);
            CharSequence title = item.title;
            if (item.id.equals(completingId)) {
                android.text.SpannableString styled = new android.text.SpannableString(item.title);
                if (frame == 1) {
                    // 帧①：前缀划掉并压淡，整行文字与方框都不动（8 字取 3 字，与预览一致）
                    int n = Math.max(1, item.title.length() * 3 / 8);
                    styled.setSpan(new android.text.style.StrikethroughSpan(),
                            0, n, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new android.text.style.ForegroundColorSpan(withAlpha(contentColor, STAGE1_PARTIAL_ALPHA)),
                            0, n, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    // 帧②：整行淡一档、方框换成打勾块。删除线用 span 挂在标题上，画在背景位图上会被行文字挡住
                    views.setTextColor(titles[shown], withAlpha(contentColor, STAGE1_TEXT_ALPHA));
                    views.setImageViewBitmap(checks[shown], WidgetDataManager.getThemedCheckedBox(
                            withAlpha(contentColor, STAGE1_ICON_ALPHA),
                            withAlpha(WidgetDataManager.getWidgetThemeColor(context), STAGE1_ICON_ALPHA)
                    ));
                    styled.setSpan(new android.text.style.StrikethroughSpan(),
                            0, item.title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                title = styled;
            }
            views.setTextViewText(titles[shown], title);
            Intent intent = new Intent(context, TodoWidget2x2Provider.class)
                    .setAction(ACTION_2X2_COMPLETE)
                    .setData(Uri.parse("supertodo://2x2/toggle/" + appWidgetId + "/" + Uri.encode(item.id)))
                    .putExtra(EXTRA_ITEM_ID, item.id);
            views.setOnClickPendingIntent(
                    checks[shown],
                    PendingIntent.getBroadcast(
                            context,
                            buildToggleRequestCode(appWidgetId, item.id),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    )
            );
            shown++;
        }

        views.setViewVisibility(R.id.widget_2x2_empty_view, shown == 0 ? View.VISIBLE : View.GONE);
        Intent open = new Intent(context, Widget2x2DialogActivity.class)
                .setData(Uri.parse("supertodo://2x2/open/" + appWidgetId))
                .putExtra("mode", "list")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                6000 + appWidgetId,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_2x2_root, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int getVisibleRowCount(Context context, int heightDp) {
        float density = context.getResources().getDisplayMetrics().density;
        float fontScale = context.getResources().getConfiguration().fontScale;
        int heightPx = Math.round(Math.max(0, heightDp) * density);
        int paddingPx = Math.round(20 * density);
        int rowPx = Math.max(1, Math.round(28 * density * Math.max(1F, fontScale)));
        int contentPx = Math.max(0, heightPx - paddingPx);
        return Math.min(7, contentPx / rowPx);
    }

    private static int buildToggleRequestCode(int appWidgetId, String itemId) {
        int itemHash = itemId == null ? 0 : itemId.hashCode();
        return (appWidgetId * 31 + itemHash) & 0x7fffffff;
    }

    /** 把不透明色换成带 alpha 的同色 ARGB，完成动画各帧的淡出全靠这个，避免又开一堆颜色参数 */
    private static int withAlpha(int color, int alpha) {
        return android.graphics.Color.argb(alpha,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_2X2_COMPLETE.equals(action)) {
            final String completingId = intent.getStringExtra(EXTRA_ITEM_ID);
            // 先取切换前的列表快照：toggleItemDone 之后 load2x2Items 就把这一条过滤掉了，帧①②再也画不出来
            final List<TodoItem> stageItems = WidgetDataManager.load2x2Items(context);
            // 数据立刻落盘，动画中途进程被杀也不会退回未完成状态
            WidgetDataManager.toggleItemDone(context, completingId);
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TodoWidget2x2Provider.class));
            final Context appContext = context.getApplicationContext();
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, completingId, stageItems, 1);
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    for (int appWidgetId : appWidgetIds) {
                        updateAppWidget(appContext, appWidgetManager, appWidgetId, completingId, stageItems, 2);
                    }
                }
            }, STAGE2_MS);
            // 帧③：普通重绘，这一行直接消失、下面的行顶上来（没有移动路径，已确认）
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    WidgetDataManager.notifyAllWidgets(appContext);
                }
            }, STAGE3_MS);
        } else if (WidgetDataManager.ACTION_REFRESH_WIDGET.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TodoWidget2x2Provider.class));
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetDataManager.removeWidgetConfig(context, appWidgetId);
        }
    }
}
