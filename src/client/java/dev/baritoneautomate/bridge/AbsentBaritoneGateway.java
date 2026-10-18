package dev.baritoneautomate.bridge;

/** Safe degradation when the Baritone API class is missing or fails to load. */
public final class AbsentBaritoneGateway extends FailingGateway {
	@Override
	protected String reason() {
		return "Baritone is not installed";
	}
}
