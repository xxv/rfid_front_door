package info.staticfree.android.taguid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ListAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class RfidAdapter implements Adapter, ListAdapter {
	private final List<RfidRecord> mRecords;
	private final int mLayout;
	private final Context mContext;
	private final LayoutInflater mLayoutInflater;
	private final DataSetObservable mObservable;
	private Cursor mContacts;
	private final HashMap<String, String> mIdContactMap = new HashMap<String, String>();

	public RfidAdapter(Context context, int layout, Cursor contacts) {
		this(context, layout, new ArrayList<RfidRecord>(), contacts);

	}

	public RfidAdapter(Context context, int layout, List<RfidRecord> records, Cursor contacts) {
		mRecords = records;
		mLayout = layout;
		mContext = context;

		mLayoutInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mObservable = new DataSetObservable();

		swapCursor(contacts);

	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public boolean isEnabled(int position) {
		return true;
	}

	@Override
	public int getCount() {
		return mRecords.size();
	}

	@Override
	public RfidRecord getItem(int position) {
		return mRecords.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = mLayoutInflater.inflate(mLayout, parent, false);
		}
		final RfidRecord id = getItem(position);

		((TextView) convertView.findViewById(android.R.id.text1)).setText(id.toString());

		final QuickContactBadge quickContactBadge = (QuickContactBadge) convertView
				.findViewById(R.id.contact);
		final String contact = mIdContactMap.get(id.getIdString());
		if (contact != null) {
			final Uri contactUri = Uri.parse(contact);
			quickContactBadge.assignContactUri(contactUri);
			final Drawable icon = Drawable.createFromResourceStream(
					mContext.getResources(),
					null,
					ContactsContract.Contacts.openContactPhotoInputStream(
							mContext.getContentResolver(), contactUri), null);

			quickContactBadge.setImageDrawable(icon);
			quickContactBadge.setVisibility(View.VISIBLE);
		} else {
			quickContactBadge.setVisibility(View.GONE);
			quickContactBadge.assignContactUri(null);
		}

		return convertView;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return mRecords.isEmpty();
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		mObservable.registerObserver(observer);
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		mObservable.unregisterObserver(observer);
	}

	private void loadContacts() {
		mIdContactMap.clear();
		if (mContacts == null) {
			return;
		}
		final int contactCol = mContacts.getColumnIndex(ContactId.CONTACT_URI);
		final int rfidCol = mContacts.getColumnIndex(ContactId.RFID);
		for (mContacts.moveToFirst(); !mContacts.isAfterLast(); mContacts.moveToNext()) {
			mIdContactMap.put(mContacts.getString(rfidCol), mContacts.getString(contactCol));
		}
		mObservable.notifyChanged();
	}

	public void swapCursor(Cursor contacts) {
		if (mContacts != null) {
			mContacts.unregisterDataSetObserver(mContactsObserver);
		}
		mContacts = contacts;
		if (mContacts != null) {
			mContacts.registerDataSetObserver(mContactsObserver);
			loadContacts();
		}
	}

	private final DataSetObserver mContactsObserver = new DataSetObserver() {
		@Override
		public void onChanged() {
			loadContacts();
		}
	};

}
