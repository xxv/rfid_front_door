package info.staticfree.android.taguid;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Parcel;
import android.os.Parcelable;

public class RfidRecord implements Parcelable {

	public final int groups;
	public final byte[] id;

	public RfidRecord(byte[] id, int groups) {
		this.id = id;
		this.groups = groups;
	}

	public RfidRecord(String id, int groups) {
		this.id = parseIdString(id);
		this.groups = groups;
	}

	public String getIdString() {
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

	public String getGroups() {
		switch (groups) {
			case 0:
				return "no groups";
			case 127:
				return "all groups";
			default: {
				final StringBuilder sb = new StringBuilder();
				// int prevShown = -1;
				// for (int i = 1; i <= 7; i++) {
				// if ((groups & (1 << (i - 1))) != 0) {
				// if (prevShown != i - 1) {
				// sb.append(i);
				// prevShown = i;
				// }
				// }else{
				// if (prevShown)
				// }
				// }
				// return sb.toString();

				boolean delim = false;
				for (int i = 1; i <= 7; i++) {
					if ((groups & (1 << (i - 1))) != 0) {
						if (delim) {
							sb.append(',');
						}
							sb.append(i);
						delim = true;
					}
				}
				return sb.toString();
			}
		}
	}

	private static final Pattern REC_FORMAT = Pattern.compile("([A-Fa-f0-9:]+)");

	public static byte[] parseIdString(String id) throws IllegalArgumentException {

		final Matcher m = REC_FORMAT.matcher(id);
		if (!m.matches()) {
			throw new IllegalArgumentException("Could not parse ID");
		}

		final String hexString = m.group(1);

		final String filteredHex = hexString.replaceAll("[^A-Fa-f0-9]", "");

		return new BigInteger(filteredHex, 16).toByteArray();
	}

	@Override
	public String toString() {
		return getGroups() + " " + getIdString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(id.length);
		dest.writeByteArray(id);
		dest.writeInt(groups);
	}

	public static final Parcelable.Creator<RfidRecord> CREATOR = new Parcelable.Creator<RfidRecord>() {
		public RfidRecord createFromParcel(Parcel in) {
			return new RfidRecord(in);
		}

		public RfidRecord[] newArray(int size) {
			return new RfidRecord[size];
		}
	};

	private RfidRecord(Parcel in) {
		final int len = in.readInt();
		id = new byte[len];

		in.readByteArray(id);
		groups = in.readInt();
	}
}