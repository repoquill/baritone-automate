package dev.baritoneautomate.automate;

public interface LogSink {
	void info(String message);

	void warn(String message);

	static LogSink none() {
		return new LogSink() {
			@Override
			public void info(String message) {
			}

			@Override
			public void warn(String message) {
			}
		};
	}
}
