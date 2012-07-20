package info.staticfree.android.taguid;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
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

	public RfidAdapter(Context context, int layout) {
		this(context, layout, new ArrayList<RfidRecord>());

	}

	public RfidAdapter(Context context, int layout, List<RfidRecord> records) {
		mRecords = records;
		mLayout = layout;
		mContext = context;
		mLayoutInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mObservable = new DataSetObservable();
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

		((TextView) convertView.findViewById(android.R.id.text1)).setText(getItem(position)
				.toString());

		final QuickContactBadge quickContactBadge = (QuickContactBadge) convertView
				.findViewById(R.id.contact);
		quickContactBadge.assignContactFromEmail("steve@staticfree.info", true);


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

}
