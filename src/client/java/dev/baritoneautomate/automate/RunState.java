package dev.baritoneautomate.automate;

public enum RunState {
	IDLE("空闲"),
	RUNNING("进行中"),
	PAUSED("已暂停"),
	COMBAT("战斗中"),
	FAILED("失败"),
	CANCELLED("已取消"),
	DONE("完成");

	private final String label;

	RunState(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean active() {
		return this == RUNNING || this == PAUSED || this == COMBAT;
	}
}
