package me.mrCookieSlime.CommandOverride;

import java.util.ArrayList;
import java.util.List;

public class CommandCooldowns {
	
	private List<String> commands;
	private List<Long> cooldowns;
	
	public CommandCooldowns() {
		this.commands = new ArrayList<String>();
		this.cooldowns = new ArrayList<Long>();
	}
	
	public void putCooldown(String command, int cooldown) {
		commands.add(command);
		cooldowns.add(System.currentTimeMillis() + cooldown * 1000);
	}
	
	public void refresh() {
		for (int i = 0; i < commands.size(); i++) {
			if (cooldowns.get(i) <= System.currentTimeMillis()) {
				commands.remove(i);
				cooldowns.remove(i);
			}
		}
	}
	
	public boolean isOnCooldown(String command) {
		return commands.contains(command);
	}
	
	public String getRemainingSeconds(String command) {
		return String.valueOf((System.currentTimeMillis() - cooldowns.get(commands.indexOf(command))) / 1000).replace("-", "");
	}
	
	public boolean isEmpty() {
		return commands.isEmpty() && cooldowns.isEmpty();
	}

	public String getRemainingTime(String command) {
		return String.valueOf((System.currentTimeMillis() - cooldowns.get(commands.indexOf(command))) / 1000).replace("-", "");
	}

}
