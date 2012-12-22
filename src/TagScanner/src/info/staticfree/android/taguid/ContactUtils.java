package info.staticfree.android.taguid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

public class ContactUtils {

    public static String JSON_KEY_RFID = "id";
    /**
     * The value is a list of email addresses
     */
    public static String JSON_KEY_EMAIL = "email";
    public static String JSON_KEY_NAME = "name";

    public static void export(Context context, File output) throws IOException, JSONException {

        final ContentResolver cr = context.getContentResolver();

        final Cursor c = cr.query(ContactId.CONTENT_URI, null, null, null, null);

        final JSONArray rfids = new JSONArray();
        while (c.moveToNext()) {
            final JSONObject rfidEntry = new JSONObject();

            try {
                rfidEntry.put(JSON_KEY_RFID, c.getString(c.getColumnIndex(ContactId.RFID)));

                final Uri contactLookup = c.isNull(c.getColumnIndex(ContactId.CONTACT_URI)) ? null
                        : Uri.parse(c.getString(c.getColumnIndex(ContactId.CONTACT_URI)));

                JSONArray emailAddrs = null;

                if (contactLookup != null) {
                    final Uri contact = ContactsContract.Contacts.lookupContact(cr, contactLookup);
                    final long id = ContentUris.parseId(contact);
                    final Cursor emailAddr = cr.query(Email.CONTENT_URI, null, Email.CONTACT_ID
                            + "=?", new String[] { String.valueOf(id) }, null);

                    try {
                        while (emailAddr.moveToNext()) {
                            if (emailAddrs == null) {
                                emailAddrs = new JSONArray();
                            }

                            emailAddrs.put(emailAddr.getString(emailAddr
                                    .getColumnIndexOrThrow(Email.DATA)));
                        }
                    } finally {
                        emailAddr.close();
                    }

                    final Cursor contactCursor = cr.query(contact, null, null, null, null);
                    try {
                        if (contactCursor.moveToFirst()) {
                            rfidEntry.put(JSON_KEY_NAME, contactCursor.getString(contactCursor
                                    .getColumnIndexOrThrow(Contacts.DISPLAY_NAME)));
                        }

                    } finally {
                        contactCursor.close();
                    }
                }
                if (emailAddrs != null) {
                    rfidEntry.put(JSON_KEY_EMAIL, emailAddrs);
                }
                rfids.put(rfidEntry);
            } catch (final JSONException e) {
                e.printStackTrace();
                continue;
            }
        }

        final FileOutputStream fos = new FileOutputStream(output);
        final OutputStreamWriter osw = new OutputStreamWriter(fos);
        osw.append(rfids.toString(2));
        osw.close();
    }

    /**
     * @param context
     * @param emailAddress
     *            a list of email addresses to search for
     * @return a list of lookup URIs for contacts that match the list of email addresses
     */
    public static List<Uri> findContactByEmailAddress(Context context, String... emailAddress) {

        if (emailAddress.length == 0) {
            return null;
        }

        final List<Uri> results = new ArrayList<Uri>();

        final ContentResolver cr = context.getContentResolver();

        final String[] CONTACT_PROJECTION = new String[] { ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Email.TYPE };

        final StringBuilder addressPlaceholders = new StringBuilder();

        for (int i = 0; i < emailAddress.length; i++) {
            if (i > 0) {
                addressPlaceholders.append(" OR ");
            }
            addressPlaceholders.append(ContactsContract.CommonDataKinds.Email.ADDRESS + "=?");
        }

        final String selection = ContactsContract.Data.MIMETYPE + "='"
                + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "' AND ("
                + addressPlaceholders + ")";

        final Cursor c = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                CONTACT_PROJECTION, selection, emailAddress, ContactsContract.Contacts.LOOKUP_KEY);

        final ArrayList<String> uniqueKeys = new ArrayList<String>();
        while (c.moveToNext()) {
            final String key = c.getString(c
                    .getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));

            if (uniqueKeys.size() == 0 || !uniqueKeys.get(uniqueKeys.size() - 1).equals(key)) {
                uniqueKeys.add(key);
                results.add(ContactsContract.Contacts.getLookupUri(
                        c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)), key));
            }

            final String contact = c.getString(c
                    .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
            Log.d("ContactUtils", "contact: " + contact + ", key: " + key);
        }

        try {
            return results;

        } finally {
            c.close();
        }
    }

    public static void importContacts(Context context, File savefile) throws IOException,
            JSONException, RemoteException, OperationApplicationException {

        final ArrayList<ContentProviderOperation> cpos = new ArrayList<ContentProviderOperation>();

        final FileReader fr = new FileReader(savefile);

        final BufferedReader br = new BufferedReader(fr);
        final StringBuffer sb = new StringBuffer();
        try {

            for (String line; (line = br.readLine()) != null;) {
                sb.append(line);
            }
        } finally {
            br.close();
        }

        final JSONArray ja = new JSONArray(sb.toString());

        final ContentValues cv = new ContentValues();
        for (int i = 0; i < ja.length(); i++) {
            final JSONObject jo = ja.getJSONObject(i);
            cv.clear();

            cv.put(ContactId.RFID, jo.getString(JSON_KEY_RFID));
            if (jo.has(JSON_KEY_EMAIL)) {
                final JSONArray emailAddresses = jo.getJSONArray(JSON_KEY_EMAIL);
                final String[] addresses = new String[emailAddresses.length()];

                for (int j = 0; j < emailAddresses.length(); j++) {
                    addresses[j] = emailAddresses.getString(j);
                }

                final List<Uri> lookup = findContactByEmailAddress(context, addresses);
                if (lookup.size() == 1) {
                    cv.put(ContactId.CONTACT_URI, lookup.toString());
                }
            } else {
                // search by name
            }
            if (cv.containsKey(ContactId.CONTACT_URI)) {
                final Builder ins = ContentProviderOperation.newInsert(ContactId.CONTENT_URI);
                ins.withValues(cv);
                cpos.add(ins.build());
            }
        }

        context.getContentResolver().applyBatch(ContactIdProvider.AUTHORITY, cpos);
    }
}
