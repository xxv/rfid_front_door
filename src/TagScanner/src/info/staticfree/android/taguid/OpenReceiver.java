package info.staticfree.android.taguid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OpenReceiver extends BroadcastReceiver {

    private static final String TAG = OpenReceiver.class.getSimpleName();

    public static String ACTION_OPEN_DOOR = "info.staticfree.android.taguid.ACTION_OPEN_DOOR";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "received intent " + intent);

        if (ACTION_OPEN_DOOR.equals(intent.getAction())) {

            context.startService(new Intent(context, ArduinoConnectService.class)
                    .setAction(ACTION_OPEN_DOOR));
        }
    }
}
