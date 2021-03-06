package info.staticfree.android.taguid;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.greenwheel.BluetoothService;

public class ArduinoConnectService extends Service {

    private static final String TAG = ArduinoConnectService.class.getSimpleName();

    public static String ACTION_OPEN_DOOR = "info.staticfree.android.taguid.ACTION_OPEN_DOOR";

    // set to true to enable debug logging
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int SELF_DESTRUCT_TIMEOUT = 5000; // ms

    public static final String PREF_BT_ADDR = "bt_addr";

    private BluetoothService mBluetoothService;

    private final IBinder mBinder = new LocalBinder();

    private SharedPreferences mPrefs;

    private BluetoothAdapter mBluetoothAdapter;

    public static final int STATE_DISCONNECTED = 100, STATE_CONNECTING = 101, STATE_READY = 200,
            STATE_WAITING_FOR_RESPONSE = 201, STATE_READING_RESPONSE = 202;
    private int mState = STATE_DISCONNECTED;

    private char mCmd = 0;

    /**
     * Get firmware version
     */
    private static final char CMD_VER = 'v';

    /**
     * List all entries
     */
    private static final char CMD_LIST = 'l';

    /**
     * Add an entry
     */
    private static final char CMD_ADD = 'a';

    /**
     * Delete an entry
     */
    private static final char CMD_DEL = 'd';

    /**
     * Open the door
     */
    private static final char CMD_OPEN = 'o';

    /**
     * Retrieve/set the current group
     */
    private static final char CMD_CUR_GROUP = 'g';

    private final Queue<String> mSendQueue = new ConcurrentLinkedQueue<String>();

    private boolean mStopping;

    @Override
    public IBinder onBind(Intent intent) {

        cancelSelfDestruct();
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onRebind");
        }
        cancelSelfDestruct();
        super.onRebind(intent);

        mStopping = false;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        mBluetoothService = new BluetoothService(this, mBtHandler);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        processIntent(intent);

        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        scheduleSelfDestruct();
        mResultListener = null;
        if (DEBUG) {
            Log.d(TAG, "onUnbind");
        }
        return true;
    }

    private void processIntent(Intent intent) {

        if (DEBUG) {
            Log.d(TAG, "got new intent " + intent);
        }
        if (intent == null) {
            return;
        }

        cancelSelfDestruct();

        if (ACTION_OPEN_DOOR.equals(intent.getAction())) {

            new Thread(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "started thread to unlock door...");

                    connect();

                    Log.d(TAG, "unlock thread: connected");
                    requestOpen();
                    final Bundle args = new Bundle(1);
                    args.putString(BluetoothService.TOAST, getString(R.string.door_open_result));
                    final Message m = mBtHandler.obtainMessage(BluetoothService.MESSAGE_TOAST);
                    m.setData(args);
                    m.sendToTarget();
                    scheduleSelfDestruct();
                }
            }).start();
        }
    }

    /**
     * @return false if the device is already connected or could not be connected to
     */
    public boolean connect() {
        if (mBluetoothService.getState() != BluetoothService.STATE_NONE) {
            return false;
        }
        final String btAddr = mPrefs.getString(PREF_BT_ADDR, null);
        if (btAddr == null) {
            return false;
        }

        final BluetoothDevice btDev = mBluetoothAdapter.getRemoteDevice(btAddr);

        mBluetoothService.connect(btDev);

        return true;
    }

    /**
     * Disconnect the Bluetooth service
     */
    public void disconnect() {
        mSendQueue.clear();

        if (mBluetoothService != null
                && mBluetoothService.getState() != BluetoothService.STATE_NONE) {
            mBluetoothService.stop();
        }
    }

    /**
     * Disconnect and stop the service.
     */
    public void stop() {
        if (mStopping) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Stop self");
        }
        mStopping = true;
        disconnect();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DEBUG) {
            Log.d(TAG, "onDestroy");
        }
        disconnect();
    }

    private final List<RfidRecord> mRfidRecords = new LinkedList<RfidRecord>();
    private boolean mHasCopyOfList = false;

    private static final Pattern REC_FORMAT = Pattern.compile("(\\d+)\t([A-Fa-f0-9:]+)");

    private RfidRecord parseRecord(String recLine) {
        final Matcher m = REC_FORMAT.matcher(recLine);
        if (!m.matches()) {
            return null;
        }

        final String hexString = m.group(2);

        final RfidRecord r = new RfidRecord(hexString, Integer.valueOf(m.group(1)));

        return r;
    }

    private final List<String> mCmdResults = new LinkedList<String>();

    private void onRead(String msg) {
        if (DEBUG) {
            Log.d(TAG, "just read: " + msg);
        }

        if ("".equals(msg)) {
            onCommandFinished();
            mCmd = 0;
            setState(STATE_READY);
            mCmdResults.clear();

            sendEnqueued();

        } else {
            mCmdResults.add(msg);
            setState(STATE_READING_RESPONSE);
        }
    }

    private void sendEnqueued() {
        final String cmd = mSendQueue.poll();
        if (cmd != null) {
            sendCommand(cmd);
        }
    }

    private void onCommandFinished() {
        switch (mCmd) {
            case CMD_VER:
                if (mResultListener != null) {
                    mResultListener.onVersionResult(mCmdResults.get(0));
                }
                break;

            case CMD_LIST: {
                mRfidRecords.clear();
                for (final String msg : mCmdResults) {
                    final RfidRecord r = parseRecord(msg);
                    if (r != null) {

                        mRfidRecords.add(r);
                    } else {
                        mHasCopyOfList = true;
                        if (mResultListener != null) {
                            mResultListener.onListResult(mRfidRecords);
                        }
                        break;
                    }
                }
            }
                break;

            case CMD_ADD:
                if (mResultListener != null) {
                    mResultListener.onAddResult(true);
                }
                break;

            case CMD_DEL:
                if (mResultListener != null) {
                    mResultListener.onDeleteResult(true);
                }
                break;

            case CMD_OPEN:
                if (mResultListener != null) {
                    mResultListener.onOpenResult();
                }
                break;

            case CMD_CUR_GROUP:
                if (mResultListener != null) {
                    mResultListener.onCurGroupResult(Integer.valueOf(mCmdResults.get(0)));
                }
                break;
        }
    }

    public void requestVersion() {
        sendCommand(CMD_VER);
    }

    public void requestOpen() {
        sendCommand(CMD_OPEN);
    }

    public void requestIdList() {
        sendCommand(CMD_LIST);
    }

    public void requestGetCurGroup() {
        sendCommand(CMD_CUR_GROUP);
    }

    public void requestSetCurGroup(int group) {
        sendCommand(CMD_CUR_GROUP + " " + group);
    }

    public boolean requestCachedList() {
        if (mHasCopyOfList) {
            if (mResultListener != null) {
                mResultListener.onListResult(mRfidRecords);
            }
        }
        return mHasCopyOfList;
    }

    public void requestDeleteId(RfidRecord record) {
        sendCommand(CMD_DEL + " " + record.getIdString());
    }

    public void requestAdd(RfidRecord r) {
        sendCommand(CMD_ADD + r.getIdString());
    }

    private void onCmdSent(char cmdId) {
        switch (cmdId) {
            case CMD_ADD:

                break;
        }
    }

    private void sendCommand(char command) {
        if (mState == STATE_READY) {
            mCmd = command;
            mBluetoothService.write((command + "\n").getBytes());
            onCmdSent(command);
            setState(STATE_WAITING_FOR_RESPONSE);
        } else {
            if (DEBUG) {
                Log.d(TAG, "enqueued command: " + command);
            }
            mSendQueue.add(Character.toString(command));
        }
    }

    private void sendCommand(String command) {
        final char cmdId = command.charAt(0);
        if (mState == STATE_READY) {
            mCmd = cmdId;
            mBluetoothService.write((command + "\n").getBytes());
            onCmdSent(cmdId);
            setState(STATE_WAITING_FOR_RESPONSE);
            if (mResultListener == null) {
                cancelSelfDestruct();
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "enqueued command: " + command);
            }
            mSendQueue.add(command);
        }
    }

    public int getState() {
        return mState;
    }

    private void setState(int state) {
        mState = state;
        mHandler.obtainMessage(MSG_STATE_CHANGED, state, 0).sendToTarget();
    }

    public class LocalBinder extends Binder {
        public ArduinoConnectService getService() {
            return ArduinoConnectService.this;
        }
    }

    private final Handler mBtHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE: {

                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:

                            setState(STATE_READY);
                            Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT)
                                    .show();
                            sendEnqueued();
                            break;

                        case BluetoothService.STATE_NONE:
                            if (mResultListener == null) {
                                scheduleSelfDestruct();
                            }
                            setState(STATE_DISCONNECTED);
                            break;

                        case BluetoothService.STATE_CONNECTING:
                        case BluetoothService.STATE_RECONNECTING:
                            setState(STATE_CONNECTING);
                            break;
                    }

                }
                    break;

                case BluetoothService.MESSAGE_TOAST: {

                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(BluetoothService.TOAST), Toast.LENGTH_SHORT)
                            .show();
                }
                    break;

                case BluetoothService.MESSAGE_WRITE: {
                    final byte[] readBuf = (byte[]) msg.obj;
                    final String readMessage = new String(readBuf, 0, msg.arg1);
                    if (DEBUG) {
                        Log.d(TAG, "just wrote: " + readMessage);
                    }
                }
                    break;

                case BluetoothService.MESSAGE_READ: {
                    final byte[] readBuf = (byte[]) msg.obj;
                    final String readMessage = new String(readBuf, 0, msg.arg1);
                    onRead(readMessage);
                }
                    break;

            }
        };
    };

    private void scheduleSelfDestruct() {
        cancelSelfDestruct();
        mHandler.sendEmptyMessageDelayed(MSG_STOP_SELF, SELF_DESTRUCT_TIMEOUT);
    }

    private void cancelSelfDestruct() {
        mHandler.removeMessages(MSG_STOP_SELF);
    }

    private static final int MSG_STOP_SELF = 100, MSG_STATE_CHANGED = 101;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_STOP_SELF:
                    stop();
                    break;

                case MSG_STATE_CHANGED: {
                    if (mResultListener != null) {
                        mResultListener.onStateChange(msg.arg1);
                    }
                }
                    break;
            }
        };
    };

    private OnDoorResultListener mResultListener;

    public void setResultListener(OnDoorResultListener resultListener) {
        mResultListener = resultListener;
        // re-send the ready state so the client knows it's ready.
        if (mState == STATE_READY) {
            setState(STATE_READY);
        }
    };
}
