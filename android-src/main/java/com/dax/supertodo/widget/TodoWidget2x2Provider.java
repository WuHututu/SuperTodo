package com.dax.supertodo.widget;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import com.dax.supertodo.R;
import java.util.List;
public class TodoWidget2x2Provider extends AppWidgetProvider {
 public static final String ACTION_2X2_COMPLETE="com.dax.supertodo.ACTION_2X2_COMPLETE";
 public static final String EXTRA_ITEM_ID="item_id";
 public static void updateAppWidget(Context c,AppWidgetManager m,int id){
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_2x2); int themeColor=WidgetDataManager.getWidgetThemeColor(c); v.setTextColor(R.id.widget_2x2_title1,themeColor); v.setTextColor(R.id.widget_2x2_title2,themeColor); v.setTextColor(R.id.widget_2x2_title3,themeColor); List<TodoItem> items=WidgetDataManager.load2x2Items(c);
  int[] rows={R.id.widget_2x2_row1,R.id.widget_2x2_row2,R.id.widget_2x2_row3}; int[] checks={R.id.widget_2x2_check1,R.id.widget_2x2_check2,R.id.widget_2x2_check3}; int[] titles={R.id.widget_2x2_title1,R.id.widget_2x2_title2,R.id.widget_2x2_title3};
  int shown=0; for(TodoItem it:items){ if(it.done||shown>=3)continue; v.setViewVisibility(rows[shown],View.VISIBLE); v.setTextViewText(titles[shown],it.title); Intent in=new Intent(c,TodoWidget2x2Provider.class).setAction(ACTION_2X2_COMPLETE).putExtra(EXTRA_ITEM_ID,it.id); v.setOnClickPendingIntent(checks[shown],PendingIntent.getBroadcast(c,9000+shown+id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)); shown++; }
  v.setViewVisibility(R.id.widget_2x2_empty_view,shown==0?View.VISIBLE:View.GONE); Intent open=new Intent(c,Widget2x2DialogActivity.class).putExtra("mode","list").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); PendingIntent pi=PendingIntent.getActivity(c,6000+id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); v.setOnClickPendingIntent(R.id.widget_2x2_root,pi); m.updateAppWidget(id,v);
 }
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateAppWidget(c,m,id);}
 @Override public void onReceive(Context c,Intent in){super.onReceive(c,in);if(in==null)return;String a=in.getAction();if(ACTION_2X2_COMPLETE.equals(a)){WidgetDataManager.toggleItemDone(c,in.getStringExtra(EXTRA_ITEM_ID));WidgetDataManager.notifyAllWidgets(c);}else if(WidgetDataManager.ACTION_REFRESH_WIDGET.equals(a)){AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,TodoWidget2x2Provider.class)))updateAppWidget(c,m,id);}}
 @Override public void onDeleted(Context c,int[] ids){for(int id:ids)WidgetDataManager.removeWidgetConfig(c,id);}
}
