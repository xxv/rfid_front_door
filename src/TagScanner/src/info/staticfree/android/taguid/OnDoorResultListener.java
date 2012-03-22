package info.staticfree.android.taguid;

import java.util.List;

public interface OnDoorResultListener {
	public void onReceiveRecords(List<RfidRecord> rfidRecords);

	public void onReceiveVersion(String version);

	public void onSetGroupResult(boolean result);

	public void onDeleteResult(boolean result);

	public void onStateChange(int state);

	public void onOpenResult();

}