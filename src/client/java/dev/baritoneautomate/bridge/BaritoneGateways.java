package dev.baritoneautomate.bridge;

import dev.baritoneautomate.bootstrap.BaritonePresence;
import org.slf4j.Logger;

public final class BaritoneGateways {
	private BaritoneGateways() {
	}

	public static BaritoneGateway open(Logger logger) {
		if (!BaritonePresence.classPresent()) {
			logger.warn("Baritone API class is missing. Install a 26.3 Baritone build (branch 26.3). Pathing is disabled.");
			return new AbsentBaritoneGateway();
		}
		try {
			logger.info("Baritone API found. The gateway binds to the primary Baritone on first use.");
			return new BaritoneGatewayImpl(logger);
		} catch (Throwable failure) {
			logger.warn("Baritone API is present but the gateway failed to load: {}", failure.toString());
			return new AbsentBaritoneGateway();
		}
	}
}
