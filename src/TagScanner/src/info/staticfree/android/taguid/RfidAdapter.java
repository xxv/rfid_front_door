package info.staticfree.android.taguid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ListAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class RfidAdapter implements Adapter, ListAdapter {
    private List<RfidRecord> mRecords;
    private final int mLayout;
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final DataSetObservable mObservable;
    private Cursor mContacts;
    private final HashMap<String, String> mIdContactMap = new HashMap<String, String>();

    private final LruCache<Uri, Drawable> mFaces = new LruCache<Uri, Drawable>(
            24 /* MiB */* 1024 * 1024);

    private final HashMap<Uri, String> mContactNames = new HashMap<Uri, String>();

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

    public void setRecords(List<RfidRecord> records) {
        mRecords = records;
        mObservable.notifyChanged();
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
        return mRecords != null ? mRecords.size() : 0;
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

        CharSequence name;

        final QuickContactBadge quickContactBadge = (QuickContactBadge) convertView
                .findViewById(R.id.contact);
        final String contact = mIdContactMap.get(id.getIdString());
        if (contact != null) {
            final Uri contactUri = Uri.parse(contact);
            quickContactBadge.assignContactUri(contactUri);
            final Drawable face = mFaces.get(contactUri);
            if (face != null) {
                quickContactBadge.setImageDrawable(face);
            } else {
                loadContactFace(contactUri);
            }

            name = mContactNames.get(contactUri);

            quickContactBadge.setVisibility(View.VISIBLE);
        } else {
            quickContactBadge.setVisibility(View.GONE);
            quickContactBadge.assignContactUri(null);
            name = null;
        }

        if (name == null) {
            name = id.getIdString();
        }

        final CharSequence label = name + " (" + id.getGroups() + ")";

        ((TextView) convertView.findViewById(android.R.id.text1)).setText(label);

        return convertView;
    }

    private void loadContactFace(Uri contactUri) {
        new LoadContactTask(mContext, this).execute(contactUri);
    }

    private static class LoadContactTask extends AsyncTask<Uri, Void, Drawable> {

        private static final String[] CONTACT_PROJECTION = new String[] {
                ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME };
        private final Resources mResources;
        private final ContentResolver mCR;
        private Uri mContact;
        private final LruCache<Uri, Drawable> mFaces;
        private final DataSetObservable mObservable;
        private final HashMap<Uri, String> mContactNames;

        public LoadContactTask(Context context, RfidAdapter adapter) {

            mResources = context.getResources();
            mCR = context.getContentResolver();
            mFaces = adapter.mFaces;
            mObservable = adapter.mObservable;
            mContactNames = adapter.mContactNames;
        }

        @Override
        protected Drawable doInBackground(Uri... params) {
            mContact = params[0];
            final Cursor contactRec = mCR.query(mContact, CONTACT_PROJECTION, null, null, null);
            try {
                if (contactRec.moveToFirst()) {
                    final String name = contactRec.getString(contactRec
                            .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    if (name != null) {
                        mContactNames.put(mContact, name);
                    }
                }
            } finally {
                contactRec.close();
            }

            return Drawable.createFromResourceStream(mResources, null,
                    ContactsContract.Contacts.openContactPhotoInputStream(mCR, params[0]), null);
        }

        @Override
        protected void onPostExecute(Drawable result) {
            if (result != null) {
                mFaces.put(mContact, result);
                mObservable.notifyChanged();
            } else {
                mFaces.put(mContact, mResources.getDrawable(R.drawable.ic_launcher));
            }
        }
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
        return mRecords != null ? mRecords.isEmpty() : true;
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
