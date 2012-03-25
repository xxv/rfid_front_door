package info.staticfree.android.taguid;

public class RfidRecord {

	public RfidRecord() {

	}

	public RfidRecord(int groups, byte[] id) {
		this.id = id;
		this.groups = groups;
	}

	int groups;
	byte[] id;

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
}