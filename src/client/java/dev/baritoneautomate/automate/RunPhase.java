package dev.baritoneautomate.automate;

/**
 * P0 route from the architecture. Diamond can be skipped. Later phases may be stubs,
 * but crystals and the dragon still hand off to combat.
 */
public enum RunPhase {
	CHECK("检查"),
	EARLY_WOOD("前期木头"),
	IRON("铁"),
	DIAMOND("钻石"),
	NETHER_PORTAL("下界门"),
	BLAZE_PEARL("烈焰棒与珍珠"),
	STRONGHOLD("要塞"),
	ENTER_END("进末地"),
	END_APPROACH("末地接近"),
	CRYSTALS("末影水晶"),
	DRAGON("末影龙"),
	DONE("完成");

	private final String label;

	RunPhase(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean usesCombat() {
		return this == CRYSTALS || this == DRAGON;
	}

	public RunPhase next() {
		int index = ordinal() + 1;
		RunPhase[] values = values();
		return index >= values.length ? null : values[index];
	}
}
