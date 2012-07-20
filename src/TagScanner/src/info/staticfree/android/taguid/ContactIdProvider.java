package info.staticfree.android.taguid;

import edu.mit.mobile.android.content.GenericDBHelper;
import edu.mit.mobile.android.content.SimpleContentProvider;

public class ContactIdProvider extends SimpleContentProvider {

	public static final String AUTHORITY = "info.staticfree.android.rfiddoor";

	public static final int DB_VER = 2;

	public ContactIdProvider() {
		super(AUTHORITY, DB_VER);

		final GenericDBHelper contacts = new GenericDBHelper(ContactId.class);

		addDirAndItemUri(contacts, ContactId.PATH);
	}

}
