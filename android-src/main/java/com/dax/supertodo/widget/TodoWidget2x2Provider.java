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

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_2x2);
        int width = WidgetDataManager.getWidgetWidth(context, appWidgetManager, appWidgetId, 120);
        int height = WidgetDataManager.getWidgetHeight(context, appWidgetManager, appWidgetId, 120);
        int themeColor = WidgetDataManager.getWidgetThemeColor(context);
        views.setImageViewBitmap(R.id.widget_2x2_bg, WidgetDataManager.getWidgetBackgroundBitmap(context, width, height));

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
            views.setTextColor(titles[i], themeColor);
            views.setViewVisibility(rows[i], View.GONE);
        }

        List<TodoItem> items = WidgetDataManager.load2x2Items(context);
        int capacity = getVisibleRowCount(context, height);
        int shown = 0;
        for (TodoItem item : items) {
            if (item.done || shown >= capacity) {
                continue;
            }
            views.setViewVisibility(rows[shown], View.VISIBLE);
            views.setTextViewText(titles[shown], item.title);
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
            WidgetDataManager.toggleItemDone(context, intent.getStringExtra(EXTRA_ITEM_ID));
            WidgetDataManager.notifyAllWidgets(context);
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
