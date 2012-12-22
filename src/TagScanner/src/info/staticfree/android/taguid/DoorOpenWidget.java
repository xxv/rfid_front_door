package info.staticfree.android.taguid;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class DoorOpenWidget extends AppWidgetProvider {

    private static final String TAG = DoorOpenWidget.class.getSimpleName();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget);

        rv.setOnClickPendingIntent(R.id.open, getOpenIntent(context));

        Log.d(TAG, "onclick intent has been set");

        appWidgetManager.updateAppWidget(appWidgetIds, rv);
    }

    public static PendingIntent getOpenIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0, new Intent(OpenReceiver.ACTION_OPEN_DOOR),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
