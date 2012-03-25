package info.staticfree.android.taguid;

import java.util.List;

public interface OnDoorResultListener {
	public void onListResult(List<RfidRecord> rfidRecords);

	public void onVersionResult(String version);

	public void onAddResult(boolean result);

	public void onDeleteResult(boolean result);

	public void onStateChange(int state);

	public void onOpenResult();

	public void onCurGroupResult(int group);

}