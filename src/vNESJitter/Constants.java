package vNESJitter;

import java.util.HashMap;
import java.util.Map;

public class Constants {
	private Constants(){};

	public static final int WIDTH = 256;
	public static final int HEIGHT = 240;
	public static final int DEFAULT_FRAMERATE = 60;

	public static enum Command {
		ROM,
		SET_SPEED,
		SOUND,
	};

	public static final Map<String, Command> COMMAND_LIST = new HashMap<String, Command>();
	static {
		COMMAND_LIST.put("rom", Command.ROM);
		COMMAND_LIST.put("setSpeed", Command.SET_SPEED);
		COMMAND_LIST.put("sound", Command.SOUND);
	}

}
