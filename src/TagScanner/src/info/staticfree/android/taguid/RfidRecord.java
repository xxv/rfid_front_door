package info.staticfree.android.taguid;

import android.os.Parcel;
import android.os.Parcelable;

public class RfidRecord implements Parcelable {

	public final int groups;
	public final byte[] id;

	public RfidRecord(byte[] id, int groups) {
		this.id = id;
		this.groups = groups;
	}

	public String toIdString() {
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

	@Override
	public String toString() {
		return groups + " " + toIdString();
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