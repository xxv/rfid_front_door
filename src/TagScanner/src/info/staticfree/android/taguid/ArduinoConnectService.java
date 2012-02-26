package info.staticfree.android.taguid;

import java.math.BigInteger;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.greenwheel.BluetoothService;

public class ArduinoConnectService extends Service {

	private static final String TAG = ArduinoConnectService.class.getSimpleName();

	public static final String PREF_BT_ADDR = "bt_addr";

	private BluetoothService mBluetoothService;

	private final IBinder mBinder = new LocalBinder();

	private SharedPreferences mPrefs;

	private BluetoothAdapter mBluetoothAdapter;

	private static final int STATE_READY = 0, STATE_WAITING_FOR_RESPONSE = 1,
			STATE_READING_RESPONSE = 2;
	private int mState = STATE_READY;

	private int mCmd = 0;

	private static final int CMD_VER = 100, CMD_LIST = 101, CMD_SET_GROUP = 102, CMD_DEL = 103;

	private final Queue<QueueItem> mSendQueue = new ConcurrentLinkedQueue<QueueItem>();

	private static class QueueItem {

		final String mCmd;
		final int mCmdId;

		public QueueItem(String cmd, int cmdId) {
			mCmd = cmd;
			mCmdId = cmdId;
		}

	}


	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {

		super.onCreate();

		mBluetoothService = new BluetoothService(this, mBtHandler);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	}

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

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mBluetoothService != null) {
			mBluetoothService.stop();
		}
	}

	private List<Record> mRecords = new LinkedList<ArduinoConnectService.Record>();

	private static final Pattern REC_FORMAT = Pattern.compile("(\\d)\t([A-Fa-f0-9:]+)");


	private Record parseRecord(String recLine) {
		final Matcher m = REC_FORMAT.matcher(recLine);
		if (!m.matches()) {
			return null;
		}
		final Record r = new Record();
		r.groups = Integer.valueOf(m.group(1));
		final String hexString = m.group(2);

		final String filteredHex = hexString.replaceAll("[^A-Fa-f0-9]", "");
		r.id = new BigInteger(filteredHex, 16).toByteArray();

		return r;
	}

	private final List<String> mCmdResults = new LinkedList<String>();

	private void onRead(String msg) {
		Log.d(TAG, "just read: " + msg);
		if (mResultListener == null) {
			return;
		}

		if ("".equals(msg)) {
			onCommandFinished();
			mCmd = 0;
			mState = STATE_READY;
			mCmdResults.clear();

			final QueueItem q = mSendQueue.poll();
			if (q != null) {
				sendCommand(q.mCmd, q.mCmdId);
			}

		} else {
			mCmdResults.add(msg);
			mState = STATE_READING_RESPONSE;
		}
	}

	private void onCommandFinished() {
		switch (mCmd){
			case CMD_VER:
				mResultListener.onReceiveVersion(mCmdResults.get(0));
				break;

			case CMD_LIST: {
				for (final String msg : mCmdResults) {
					final Record r = parseRecord(msg);
					if (r != null) {

						mRecords.add(r);
					} else {
						mResultListener.onReceiveRecords(mRecords);
						break;
					}
				}
			}
				break;

			case CMD_SET_GROUP:
				mResultListener.onSetGroupResult(true);
				break;
		}
	}


	public void requestVersion() {
		sendCommand("v", CMD_VER);
	}

	public void requestIdList() {
		sendCommand("l", CMD_LIST);
	}

	public void requestSetGroup(Record r){
		sendCommand("a" + r.toIdString(), CMD_SET_GROUP);
	}

	private void onCmdSent(int cmdId) {
		switch (cmdId) {
			case CMD_SET_GROUP:
				mRecords = new LinkedList<ArduinoConnectService.Record>();
				break;
		}
	}

	private void sendCommand(String command, int cmdId) {
		if (mState == STATE_READY) {
			mCmd = cmdId;
			mBluetoothService.write((command + "\n").getBytes());
			onCmdSent(cmdId);
			mState = STATE_WAITING_FOR_RESPONSE;
		} else {
			mSendQueue.add(new QueueItem(command, cmdId));
		}
	}

	public class LocalBinder extends Binder {
		public ArduinoConnectService getService(){
			return ArduinoConnectService.this;
		}
	}

	private final Handler mBtHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case BluetoothService.MESSAGE_STATE_CHANGE: {
					mResultListener.onStateChange(msg.arg1);
					switch (msg.arg1) {
						case BluetoothService.STATE_CONNECTED:
							Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT)
									.show();
							break;

						case BluetoothService.STATE_NONE:
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
					Log.d(TAG, "just wrote: " + readMessage);
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

	private RFIDoor mResultListener;

	public void setResultListener(RFIDoor resultListener) {
		mResultListener = resultListener;
	}

	public static class Record {
		int groups;
		byte[] id;

		public String toIdString() {
			final StringBuilder sb = new StringBuilder();
			boolean sep = false;
			for (final byte b : id) {
				if (sep) {
					sb.append(':');
				} else {
					sep = true;
				}

				sb.append(String.format("%02x", b));
			}

			return sb.toString();
		}

		@Override
		public String toString() {
			return toIdString();
		}
	}

	public static interface RFIDoor {
		public void onReceiveRecords(List<Record> records);

		public void onReceiveVersion(String version);

		public void onSetGroupResult(boolean result);

		public void onDeleteResult(boolean result);

		public void onStateChange(int state);
	};
}
