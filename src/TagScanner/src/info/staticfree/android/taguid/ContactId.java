package info.staticfree.android.taguid;

import android.net.Uri;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.UriPath;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.DBColumn.OnConflict;
import edu.mit.mobile.android.content.column.TextColumn;

@UriPath(ContactId.PATH)
public class ContactId implements ContentItem {

	@DBColumn(type = TextColumn.class, notnull = true)
	public static final String CONTACT_URI = "contact";

	@DBColumn(type = TextColumn.class, unique = true, notnull = true, onConflict = OnConflict.REPLACE)
	public static final String RFID = "rfid";

	public static final String PATH = "contacts";

	public static final Uri CONTENT_URI = ProviderUtils.toContentUri(ContactIdProvider.AUTHORITY,
			PATH);
}
