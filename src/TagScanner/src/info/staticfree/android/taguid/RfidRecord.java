package info.staticfree.android.taguid;

public class RfidRecord {

	public RfidRecord() {

	}

	public RfidRecord(int group, byte[] id) {
		this.id = id;
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
		return toIdString();
	}
}