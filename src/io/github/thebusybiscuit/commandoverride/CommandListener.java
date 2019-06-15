package io.github.thebusybiscuit.commandoverride;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

public class CommandListener implements Listener {
	
	CommandOverride plugin;
	
	public CommandListener(CommandOverride plugin) {
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		String originalCommand = e.getMessage().split(" ")[0];
		String command = originalCommand.toLowerCase();
		if (command.contains(":")) command = "/" + command.split(":")[1];
		
		plugin.handleCommand(e.getPlayer(), originalCommand, command, e);
		
		if (!plugin.cfg.getBoolean("options.use-unknown-command-message")) return;
		if (!e.isCancelled() && Bukkit.getServer().getHelpMap().getHelpTopic(command) == null && !plugin.isAlias(command)) {
			e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.cfg.getString("options.unknown-command-message")));
            e.setCancelled(true);
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void onCommand(ServerCommandEvent e) {
		String command = e.getCommand().split(" ")[0].toLowerCase();
		if (command.contains(":")) command = "/" + command.split(":")[1];
		
		plugin.handleCommand(command, e);
	}
}
