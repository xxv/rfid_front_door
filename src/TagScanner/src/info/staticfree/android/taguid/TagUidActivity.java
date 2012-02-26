package info.staticfree.android.taguid;

import info.staticfree.android.taguid.ArduinoConnectService.RFIDoor;
import info.staticfree.android.taguid.ArduinoConnectService.Record;

import java.math.BigInteger;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.BluetoothChat.DeviceListActivity;

import edu.mit.mobile.android.greenwheel.BluetoothService;

public class TagUidActivity extends Activity implements OnClickListener, ServiceConnection {

	PendingIntent pendingIntent;

	private IntentFilter[] intentFiltersArray;

	private NfcAdapter mAdapter;

	private final String[][] techListsArray = new String[][] { new String[] { NfcA.class.getName() } };

	private TextView mUidTextView;
	private TextView mUidIntTextView;

	private BluetoothAdapter mBluetoothAdapter;

	private SharedPreferences mPrefs;

	private ArduinoConnectService mArduinoService;

	private ListView mList;
	private ArrayAdapter<Record> mArrayAdapter;

	private ProgressBar mLoadingView;

	private Record mRecord;

	private static final int REQUEST_PAIR = 100, REQUEST_BT_ENABLE = 101, REQUEST_BT_DEVICE = 102;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		mUidTextView = (TextView) findViewById(R.id.uid);
		mUidIntTextView = (TextView) findViewById(R.id.uid_int);
		mLoadingView = (ProgressBar) findViewById(R.id.loading);
		findViewById(R.id.connect).setOnClickListener(this);
		findViewById(R.id.add).setOnClickListener(this);

		mUidTextView.setOnClickListener(this);
		mUidIntTextView.setOnClickListener(this);

		mList = (ListView) findViewById(android.R.id.list);
		mArrayAdapter = new ArrayAdapter<ArduinoConnectService.Record>(this,
				android.R.layout.simple_list_item_1);
		// mList.setAdapter(mArrayAdapter);

		parseIntent(getIntent());

		pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		final IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType("*/*"); /*
									 * Handles all MIME based dispatches. You should specify only
									 * the ones that you need.
									 */
		} catch (final MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		intentFiltersArray = new IntentFilter[] { ndef, };

		mAdapter = NfcAdapter.getDefaultAdapter(this);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

	@Override
	protected void onPause() {
		super.onPause();

		mAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onStart() {
		super.onStart();

		maybeAutoconnect();
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (mArduinoService != null) {
			unbindService(this);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		mAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);


	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.uid:
			case R.id.uid_int:
				copyTextToClipboard(v.getId());
				break;

			case R.id.connect:
				connectOrPair();
				break;

			case R.id.add:
				if (mArduinoService != null) {
					mArduinoService.requestSetGroup(mRecord);
				}
				break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_PAIR: {
				if (RESULT_OK == resultCode) {

				}
			}
				break;

			case REQUEST_BT_ENABLE: {
				if (RESULT_OK == resultCode) {
					connectOrPair();
				}
			}
				break;

			case REQUEST_BT_DEVICE: {
				if (RESULT_OK == resultCode) {
					final String btAddr = data.getExtras().getString(
							DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					mPrefs.edit().putString(ArduinoConnectService.PREF_BT_ADDR, btAddr).commit();
				}
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		// setIntent(intent);
		parseIntent(intent);
	}

	private void parseIntent(Intent intent) {

		final String action = intent.getAction();
		if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

			// Parcelable[] rawMsgs =
			// intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

			final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			showUid(tag.getId());
		}
	}

	private void showUid(byte[] uid) {
		mUidTextView.setText(toUidString(uid));
		final byte[] unsigned = new byte[uid.length + 1];

		System.arraycopy(uid, 0, unsigned, 1, uid.length);
		mUidIntTextView.setText(new BigInteger(unsigned).toString());

		mRecord = new Record();
		mRecord.id = uid;
		mRecord.groups = 1;
		findViewById(R.id.add).setEnabled(true);
	}

	private String toUidString(byte[] uid) {
    	final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (final byte b : uid) {
			if (!first) {
				sb.append(':');
			}
			if ((b & 0xff) < 0x10) {
				sb.append('0');
			}
			sb.append(Long.toString(b & 0xff, 16));
			first = false;
    	}
		return sb.toString();
    }

	private void copyTextToClipboard(int textViewId) {
		final TextView tv = (TextView) findViewById(textViewId);

		final CharSequence t = tv.getText();
		final ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

		cm.setText(t);

		Toast.makeText(this, t + " copied to clipboard.", Toast.LENGTH_SHORT).show();
	}


	/**
	 * Only autoconnects if Bluetooth is already enabled.
	 */
	private void maybeAutoconnect() {
		if (!mBluetoothAdapter.isEnabled()) {
			return;
		}

		final String btAddr = mPrefs.getString(ArduinoConnectService.PREF_BT_ADDR, null);
		if (btAddr == null) {
			return;
		}

		connectOrPair();
	}

	private void connectOrPair() {
		if (!mBluetoothAdapter.isEnabled()) {
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
					REQUEST_BT_ENABLE);
			return;
		}

		final String btAddr = mPrefs.getString(ArduinoConnectService.PREF_BT_ADDR, null);
		if (btAddr == null) {
			startActivityForResult(new Intent(this, DeviceListActivity.class), REQUEST_BT_DEVICE);
			return;
		}

		bindService(new Intent(this, ArduinoConnectService.class), this, BIND_AUTO_CREATE);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		mArduinoService = ((ArduinoConnectService.LocalBinder) service).getService();

		mArduinoService.setResultListener(mResultListener);
		mArduinoService.connect();


	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		mArduinoService.setResultListener(null);
		mArduinoService = null;
	}

	private final ArduinoConnectService.RFIDoor mResultListener = new RFIDoor() {

		@Override
		public void onReceiveVersion(String version) {
			Toast.makeText(TagUidActivity.this, version, Toast.LENGTH_LONG).show();

		}

		@Override
		public void onReceiveRecords(List<Record> records) {

			mList.setAdapter(new ArrayAdapter<Record>(TagUidActivity.this,
					android.R.layout.simple_list_item_1, records));

		}

		@Override
		public void onStateChange(int state) {
			switch (state) {
				case BluetoothService.STATE_CONNECTED:
					mLoadingView.setVisibility(View.GONE);
					findViewById(R.id.connect).setVisibility(View.GONE);
					mArduinoService.requestIdList();

					break;

				case BluetoothService.STATE_RECONNECTING:
				case BluetoothService.STATE_CONNECTING:
					mLoadingView.setVisibility(View.VISIBLE);
					findViewById(R.id.connect).setVisibility(View.GONE);
					break;

			}

		}

		@Override
		public void onSetGroupResult(boolean result) {
			mArduinoService.requestIdList();

		}

		@Override
		public void onDeleteResult(boolean result) {
			mArduinoService.requestIdList();

		}
	};

}