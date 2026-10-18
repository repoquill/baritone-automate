package dev.baritoneautomate.automate;

public record PhaseTick(PhaseSignal signal, String detail) {
	public PhaseTick {
		if (detail == null) {
			detail = "";
		}
	}

	public static PhaseTick cont(String detail) {
		return new PhaseTick(PhaseSignal.CONTINUE, detail);
	}

	public static PhaseTick advance(String detail) {
		return new PhaseTick(PhaseSignal.ADVANCE, detail);
	}

	public static PhaseTick fail(String detail) {
		return new PhaseTick(PhaseSignal.FAIL, detail);
	}
}
