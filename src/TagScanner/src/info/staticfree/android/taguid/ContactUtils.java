package info.staticfree.android.taguid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;

public class ContactUtils {

    public static void export(Context context, File output) throws IOException, JSONException {

        final ContentResolver cr = context.getContentResolver();

        final Cursor c = cr.query(ContactId.CONTENT_URI, null, null, null, null);

        final JSONArray rfids = new JSONArray();
        while (c.moveToNext()) {
            final JSONObject rfidEntry = new JSONObject();

            try {
                rfidEntry.put("id", c.getString(c.getColumnIndex(ContactId.RFID)));

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
                            rfidEntry.put("name", contactCursor.getString(contactCursor
                                    .getColumnIndexOrThrow(Contacts.DISPLAY_NAME)));
                        }

                    } finally {
                        contactCursor.close();
                    }
                }
                if (emailAddrs != null) {
                    rfidEntry.put("email", emailAddrs);
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
}
