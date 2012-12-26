package info.staticfree.android.taguid;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Parcel;
import android.os.Parcelable;

public class RfidRecord implements Parcelable {

    public static final int GROUP_MIN = 1;
    public static final int GROUP_MAX = 7;

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

				boolean delim = false;
                boolean run = false;
                boolean wasRun = false;
                for (int i = GROUP_MIN; i <= GROUP_MAX; i++) {
                    wasRun = run;
                    run = run && (i < GROUP_MAX && isInGroup(i + 1));

                    if (!run) {
                        if (isInGroup(i)) {
                            if (!wasRun && delim) {
                                sb.append(',');
                            }
                            sb.append(i);
                            // look ahead
                            if (i < GROUP_MAX && isInGroup(i + 1)) {
                                run = true;
                                sb.append('-');
                            }
                            delim = true;
                        }
                    }
				}
				return sb.toString();
			}
		}
	}

    public boolean isInGroup(int i) {
        if (i < GROUP_MIN || i > GROUP_MAX) {
            return false;
        }

        return (groups & (1 << (i - 1))) != 0;
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
        return getIdString() + " (" + getGroups() + ")";
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