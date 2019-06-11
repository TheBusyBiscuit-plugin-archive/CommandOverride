package me.mrCookieSlime.CommandOverride;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.clip.placeholderapi.PlaceholderAPI;
import me.mrCookieSlime.CSCoreLibPlugin.CSCoreLib;
import me.mrCookieSlime.CSCoreLibPlugin.PluginUtils;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.ListUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Math.DoubleHandler;
import me.mrCookieSlime.CSCoreLibSetup.CSCoreLibLoader;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;

public class CommandOverride extends JavaPlugin {
	
	public Config cfg;
	private Config aliases;
	private Config cost_money;
	private Config cost_xp;
	private Config arguments;
	private Config cooldown;
	private Config permissions;
	private Map<UUID, CommandCooldowns> cooldowns;
	private Economy economy = null;
	private Chat chat = null;
	
	private boolean isVaultInstalled, isPlaceHolderAPIInstalled;
	
	@Override
	public void onEnable() {
		CSCoreLibLoader loader = new CSCoreLibLoader(this);
		
		if (loader.load()) {
			PluginUtils utils = new PluginUtils(this);
			utils.setupConfig();
			utils.setupUpdater(83964, getFile());
			utils.setupMetrics();
			
			cfg = utils.getConfig();
			cooldowns = new HashMap<>();
			new CommandListener(this);
			
			aliases = new Config("plugins/CommandOverride/aliases.yml");
			cost_money = new Config("plugins/CommandOverride/cost_money.yml");
			cost_xp = new Config("plugins/CommandOverride/cost_xp.yml");
			arguments = new Config("plugins/CommandOverride/arguments.yml");
			cooldown = new Config("plugins/CommandOverride/cooldowns.yml");
			permissions = new Config("plugins/CommandOverride/permissions.yml");
			
			for (String key: cfg.getKeys("aliases")) {
				if (key.startsWith("/")) {
					aliases.setValue(key, cfg.getString("aliases." + key));
					cfg.setValue("aliases." + key, null);
				}
			}
			
			for (String key: cfg.getKeys("restricted")) {
				if (key.startsWith("/")) {
					permissions.setValue(key + ".permission", cfg.getString("restricted." + key));
					permissions.setValue(key + ".message", cfg.getString("restricted.message"));
					cfg.setValue("restricted." + key, null);
				}
			}
			
			for (String key: cfg.getKeys("cooldown")) {
				if (key.startsWith("/")) {
					cooldown.setValue(key + ".cooldown", cfg.getInt("cooldown." + key));
					cooldown.setValue(key + ".message", cfg.getString("cooldown.message"));
					cfg.setValue("cooldown." + key, null);
				}
			}
			
			for (String key: cfg.getKeys("money-cost")) {
				if (key.startsWith("/")) {
					cost_money.setValue(key + ".cost", cfg.getDouble("money-cost." + key));
					cost_money.setValue(key + ".message", cfg.getString("money-cost.message"));
					cfg.setValue("money-cost." + key, null);
				}
			}
			
			for (String key: cfg.getKeys("xp-cost")) {
				if (key.startsWith("/")) {
					cost_xp.setValue(key + ".cost", cfg.getInt("xp-cost." + key));
					cost_xp.setValue(key + ".message", cfg.getString("xp-cost.message"));
					cfg.setValue("xp-cost." + key, null);
				}
			}
			
			for (String key: cfg.getKeys("arguments")) {
				if (key.startsWith("/")) {
					arguments.setValue(key + ".min", cfg.getString("arguments." + key + ".min"));
					arguments.setValue(key + ".message", cfg.getString("arguments." + key + ".message"));
					cfg.setValue("arguments." + key, null);
				}
			}
			
			cfg.setValue("restricted", null);
			cfg.setValue("cooldown", null);
			cfg.setValue("gem-cost", null);
			cfg.setValue("money-cost", null);
			cfg.setValue("xp-cost", null);
			cfg.setValue("aliases", null);
			cfg.setValue("arguments", null);
			
			aliases.save();
			permissions.save();
			cooldown.save();
			arguments.save();
			cost_money.save();
			cost_xp.save();
			cfg.save();
			
			if (getServer().getPluginManager().isPluginEnabled("Vault")) {
				System.out.println("[CommandOverride] Found Vault - Hooking into it...");
				setupChat();
				setupEconomy();
				isVaultInstalled = true;
			}
			else {
				isVaultInstalled = false;
			}
			
			if (getServer().getPluginManager().isPluginEnabled("PlaceHolderAPI")) {
				System.out.println("[CommandOverride] Found PlaceHolderAPI - Hooking into it...");
				isPlaceHolderAPIInstalled = true;
			}
			else {
				isPlaceHolderAPIInstalled = false;
			}
		}
	}
	
	@Override
	public void onDisable() {
		cfg = null;
		cooldowns = null;
	}
	
	private boolean setupEconomy() {
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
	    if (economyProvider != null) {
	      economy = economyProvider.getProvider();
	    }

	    return economy != null;
	}
	
	private boolean setupChat() {
        RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }

        return (chat != null);
    }
	
	public void handleCommand(String command, ServerCommandEvent e) {
		if (aliases.contains(command)) {
			e.setCommand(e.getCommand().replace(command, aliases.getString(command)));
			command = aliases.getString(command);
		}
		
		if (!getMessages(command).isEmpty()) {
			handleMessages(e, getMessages(command));
		}
	}

	public void handleCommand(Player p, String originalCommand, String command, PlayerCommandPreprocessEvent e) {
		CommandCooldowns commandcooldowns = cooldowns.get(p.getUniqueId());
		if (commandcooldowns != null) {
			commandcooldowns.refresh();
			if (commandcooldowns.isEmpty()) {
				cooldowns.remove(p.getUniqueId());
				commandcooldowns = null;
			}
		}
		
		if (isAlias(command)) {
			e.setMessage(e.getMessage().replaceFirst(originalCommand, aliases.getString(command)));
			command = aliases.getString(command);
		}
		
		if (permissions.contains(command + ".permission") && !p.hasPermission(permissions.getString(command + ".permission"))) {
			e.setCancelled(true);
			if (permissions.getString(command + ".message") != null) p.sendMessage(ChatColor.translateAlternateColorCodes('&', permissions.getString(command + ".message")));
			return;
		}
		
		if (!checkPenalties(e, command)) {
			e.setCancelled(true);
			return;
		}
		
		if (cooldown.contains(command + ".cooldown")) {
			if (commandcooldowns != null) {
				if (commandcooldowns.isOnCooldown(command)) {
					e.setCancelled(true);
					if (cooldown.getString(command + ".message") != null) p.sendMessage(ChatColor.translateAlternateColorCodes('&', cooldown.getString(command + ".message").replace("%time%", commandcooldowns.getRemainingTime(command)).replace("%command%", command)));
					return;
				}
			}
			else commandcooldowns = new CommandCooldowns();
			
			commandcooldowns.putCooldown(command, cooldown.getInt(command + ".cooldown"));
			cooldowns.put(p.getUniqueId(), commandcooldowns);
		}
		
		if (!getMessages(command).isEmpty()) {
			e.setCancelled(true);
			applyPenalties(e, command);
			handleMessages(e, getMessages(command));
		}
	}
	
	public boolean isAlias(String command) {
		return aliases.contains(command);
	}

	private boolean checkPenalties(PlayerCommandPreprocessEvent e, String command) {
		Player p = e.getPlayer();
		if (cost_money.contains(command + ".cost") && getServer().getPluginManager().isPluginEnabled("Vault")) {
			if (economy.getBalance(p) < cost_money.getDouble(command + ".cost")) {
				if (cost_money.getString(command + ".message") != null) p.sendMessage(ChatColor.translateAlternateColorCodes('&', cost_money.getString(command + ".message").replace("%money%", String.valueOf(cost_money.getDouble(command + ".cost"))).replace("%command%", command)));
				return false;
			}
		}
		if (cost_xp.contains(command + ".cost")) {
			if (p.getLevel() < cost_xp.getInt(command + ".cost")) {
				if (cost_xp.getString(command + ".message") != null) p.sendMessage(ChatColor.translateAlternateColorCodes('&', cost_xp.getString(command + ".message").replace("%levels%", String.valueOf(cost_xp.getInt(command + ".cost"))).replace("%command%", command)));
				return false;
			}
		}
		if (arguments.contains(command + ".min")) {
			if ((e.getMessage().split(" ").length - 1) < arguments.getInt(command + ".min")) {
				if (arguments.getString(command + ".message") != null) p.sendMessage(ChatColor.translateAlternateColorCodes('&', arguments.getString(command + ".message")));
				return false;
			}
		}
		return true;
	}
	
	private void applyPenalties(PlayerCommandPreprocessEvent e, String command) {
		Player p = e.getPlayer();
		if (cost_money.contains(command + ".cost") && getServer().getPluginManager().isPluginEnabled("Vault")) {
			economy.withdrawPlayer(p, cost_money.getDouble(command + ".cost"));
		}
		if (cost_xp.contains(command + ".cost")) {
			p.setLevel(p.getLevel() - cost_xp.getInt(command + ".cost"));
		}
	}
	
	public void handleMessages(final ServerCommandEvent e, List<String> messages) {
		List<String> random = null;
		Map<String, Integer> chances = null;
		int amount = 0, max = 0, breakpoint = messages.size() - 1;
		for (int i = 0; i < messages.size(); i++) {
			if (i > breakpoint) break;
			String message = messages.get(i);
			
			if (message.startsWith("RANDOM")) {
				random = new ArrayList<String>();
				chances = new HashMap<String, Integer>();
				amount = Integer.parseInt(message.split(" ")[1]);
				max = Integer.parseInt(message.split(" ")[2]);
			}
			else if (random != null) {
				if (message.startsWith("[chance: ")) {
					int chance = Integer.parseInt(message.substring(message.indexOf("[") + 9, message.indexOf("]")));
					random.add(message.replace("[chance: " + chance + "]", ""));
					chances.put(message.replace("[chance: " + chance + "]", ""), chance);
				}
				else random.add(message);
				if (random.size() >= max) {
					List<String> list = new ArrayList<String>();
					
					if (chances.isEmpty()) list = ListUtils.getRandomEntries(random, amount);
					else {
						for (int j = 0; j < amount; j++) {
							int total = 0;
							for (String command: chances.keySet()) {
								total = total + chances.get(command);
							}
							int choice = CSCoreLib.randomizer().nextInt(total);
							int subtotal = 0;
							
							String chosen = null;
							for (String command: chances.keySet()) {
								subtotal = subtotal + chances.get(command);
								if (choice < subtotal) {
									chosen = command;
									break;
								}
							}
							list.add(chosen);
							chances.remove(chosen);
						}
					}
					handleMessages(e, list);
					random = null;
					chances = null;
				}
			}
			else {
				if (message.equalsIgnoreCase("BREAK")) {
					break;
				}
				else if (message.startsWith("BREAK ")) {
					breakpoint = i + Integer.parseInt(message.replace("BREAK ", ""));
				}
				
				if (message.startsWith("WAIT ")) {
					int delay = Integer.parseInt(message.replace("WAIT ", ""));
					final List<String> queuedMessages = new ArrayList<String>();
					for (int j = i + 1; j < messages.size(); j++) {
						queuedMessages.add(messages.get(j));
					}
					
					getServer().getScheduler().runTaskLater(this, () -> {
						handleMessages(e, queuedMessages);
					}, delay * 20L);
					
					break;
				}
				else if (message.startsWith("WAIT-T ")) {
					int delay = Integer.parseInt(message.replace("WAIT-T ", ""));
					final List<String> queuedMessages = new ArrayList<String>();
					for (int j = i + 1; j < messages.size(); j++) {
						queuedMessages.add(messages.get(j));
					}
					
					getServer().getScheduler().runTaskLater(this, () -> {
						handleMessages(e, queuedMessages);
					}, delay * 20L);
					
					break;
				}
				else {
					message = applyVariables(e, message);
					
					if (message.startsWith("command:/")) {
						String cmd = message.replace("command:/", "");
						ServerCommandEvent event = new ServerCommandEvent(e.getSender(), cmd);
						getServer().getPluginManager().callEvent(event);
						getServer().dispatchCommand(e.getSender(), cmd);
					}
					else if (message.startsWith("unsafe-command:/")) {
						getServer().dispatchCommand(e.getSender(), message.replace("unsafe-command:/", ""));
					}
					else if (message.startsWith("console:/")) getServer().dispatchCommand(getServer().getConsoleSender(), ChatColor.translateAlternateColorCodes('&', message.replace("console:/", "")));
					else if (message.startsWith("broadcast[")) {
						String permission = message.substring(message.indexOf("["), message.indexOf("]"));
						message = message.replace("broadcast[" + permission + "]:", "");
						Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&', message), permission);
					}
					else if (message.startsWith("message[")) {
						String player = message.substring(message.indexOf("["), message.indexOf("]"));
						message = message.replace("message[" + player + "]:", "");
						if (Bukkit.getPlayer(player) != null) {
							Bukkit.getPlayer(player).sendMessage(message);
						}
					}
					else if (message.startsWith("broadcast:")) Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message.replace("broadcast:", "")));
					else if (message.startsWith("playsound ")) {
						String[] args = message.split(" ");
						if (args[1].equalsIgnoreCase("all")) {
							try {
								Sound sound = Sound.valueOf(args[2]);
								if (sound != null) {
									for (Player player: Bukkit.getOnlinePlayers()) {
										player.playSound(player.getLocation(), sound, Float.valueOf(args[3]), Float.valueOf(args[4]));
									}
								}
							} catch(Exception x) {
							}
						}
						else {
							Player player = Bukkit.getPlayer(args[1]);
							if (player != null) {
								try {
									Sound sound = Sound.valueOf(args[2]);
									player.playSound(player.getLocation(), sound, Float.valueOf(args[3]), Float.valueOf(args[4]));
								} catch(Exception x) {
									System.err.println("[CommandOverride] Tried to play unknown Sound: " + args[2]);
								}
							}
						}
					}
					else e.getSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
				}
			}
		}
	}
	
	public void handleMessages(final PlayerCommandPreprocessEvent e, List<String> messages) {
		Player p = e.getPlayer();
		List<String> random = null;
		Map<String, Integer> chances = null;
		int amount = 0, max = 0, breakpoint = messages.size() - 1;
		for (int i = 0; i < messages.size(); i++) {
			if (i > breakpoint) break;
			String message = messages.get(i);
			
			if (message.startsWith("[permission: ")) {
				String permission = message.substring(message.indexOf("[") + 13, message.indexOf("]"));
				message = message.replace("[permission: " + permission + "]", "");
				if (!p.hasPermission(permission)) continue;
			}
			else if (message.startsWith("[!permission: ")) {
				String permission = message.substring(message.indexOf("[") + 14, message.indexOf("]"));
				message = message.replace("[!permission: " + permission + "]", "");
				if (p.hasPermission(permission)) continue;
			}
			
			if (message.startsWith("[world: ")) {
				String world = message.substring(message.indexOf("[") + 8, message.indexOf("]"));
				message = message.replace("[world: " + world + "]", "");
				if (!p.getWorld().getName().equals(world)) continue;
			}
			
			if (message.startsWith("[arglength smaller than ")) {
				int length = Integer.parseInt(message.substring(message.indexOf("[") + 24, message.indexOf("]")));
				message = message.replace("[arglength smaller than " + length + "]", "");
				if (e.getMessage().split(" ").length > length) continue;
			}
			
			if (message.startsWith("[arglength at least ")) {
				int length = Integer.parseInt(message.substring(message.indexOf("[") + 20, message.indexOf("]")));
				message = message.replace("[arglength at least " + length + "]", "");
				if (e.getMessage().split(" ").length <= length) continue;
			}
			
			if (message.startsWith("RANDOM")) {
				random = new ArrayList<String>();
				chances = new HashMap<String, Integer>();
				amount = Integer.parseInt(message.split(" ")[1]);
				max = Integer.parseInt(message.split(" ")[2]);
			}
			else if (random != null) {
				if (message.startsWith("[chance: ")) {
					int chance = Integer.parseInt(message.substring(message.indexOf("[") + 9, message.indexOf("]")));
					random.add(message.replace("[chance: " + chance + "]", ""));
					chances.put(message.replace("[chance: " + chance + "]", ""), chance);
				}
				else random.add(message);
				if (random.size() >= max) {
					List<String> list = new ArrayList<String>();
					
					if (chances.isEmpty()) list = ListUtils.getRandomEntries(random, amount);
					else {
						for (int j = 0; j < amount; j++) {
							int total = 0;
							for (String command: chances.keySet()) {
								total = total + chances.get(command);
							}
							int choice = CSCoreLib.randomizer().nextInt(total);
							int subtotal = 0;
							
							String chosen = null;
							for (String command: chances.keySet()) {
								subtotal = subtotal + chances.get(command);
								if (choice < subtotal) {
									chosen = command;
									break;
								}
							}
							list.add(chosen);
							chances.remove(chosen);
						}
					}
					handleMessages(e, list);
					random = null;
					chances = null;
				}
			}
			else {
				if (message.equalsIgnoreCase("BREAK")) break;
				else if (message.startsWith("BREAK ")) breakpoint = i + Integer.parseInt(message.replace("BREAK ", ""));
				if (message.startsWith("WAIT ")) {
					int delay = Integer.parseInt(message.replace("WAIT ", ""));
					final List<String> queuedMessages = new ArrayList<String>();
					for (int j = i + 1; j < messages.size(); j++) {
						queuedMessages.add(messages.get(j));
					}
					getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
						
						@Override
						public void run() {
							handleMessages(e, queuedMessages);
						}
						
					}, delay * 20L);
					break;
				}
				else if (message.startsWith("WAIT-T ")) {
					int delay = Integer.parseInt(message.replace("WAIT-T ", ""));
					final List<String> queuedMessages = new ArrayList<String>();
					for (int j = i + 1; j < messages.size(); j++) {
						queuedMessages.add(messages.get(j));
					}
					getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
						
						@Override
						public void run() {
							handleMessages(e, queuedMessages);
						}
						
					}, delay);
					break;
				}
				else {
					message = applyVariables(e, message);
					
					if (message.startsWith("command:/")) {
						String cmd = message.replace("command:/", "");
						PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(p, "/" + cmd);
						getServer().getPluginManager().callEvent(event);
						if (!event.isCancelled()) getServer().dispatchCommand(p, cmd);
					}
					else if (message.startsWith("unsafe-command:/")) {
						getServer().dispatchCommand(p, message.replace("unsafe-command:/", ""));
					}
					else if (message.startsWith("op-command:/")) {
						String cmd = message.replace("op-command:/", "");
						PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(p, "/" + cmd);
						getServer().getPluginManager().callEvent(event);
						if (!event.isCancelled()) {
							boolean op = p.isOp();
							p.setOp(true);
							getServer().dispatchCommand(p, cmd);
				            p.setOp(op);
						}
					}
					else if (message.startsWith("console:/")) getServer().dispatchCommand(getServer().getConsoleSender(), ChatColor.translateAlternateColorCodes('&', message.replace("console:/", "")));
					else if (message.startsWith("broadcast[")) {
						String permission = message.substring(message.indexOf("["), message.indexOf("]"));
						message = message.replace("broadcast[" + permission + "]:", "");
						Bukkit.broadcast(ChatColor.translateAlternateColorCodes('&', message), permission);
					}
					else if (message.startsWith("message[")) {
						String player = message.substring(message.indexOf("["), message.indexOf("]"));
						message = message.replace("message[" + player + "]:", "");
						if (Bukkit.getPlayer(player) != null) {
							Bukkit.getPlayer(player).sendMessage(message);
						}
					}
					else if (message.startsWith("broadcast:")) Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message.replace("broadcast:", "")));
					else if (message.startsWith("playsound ")) {
						String[] args = message.split(" ");
						if (args[1].equalsIgnoreCase("all")) {
							try {
								Sound sound = Sound.valueOf(args[2]);
								if (sound != null) {
									for (Player player: Bukkit.getOnlinePlayers()) {
										player.playSound(player.getLocation(), sound, Float.valueOf(args[3]), Float.valueOf(args[4]));
									}
								}
							} catch(Exception x) {
							}
						}
						else {
							Player player = Bukkit.getPlayer(args[1]);
							if (player != null) {
								try {
									Sound sound = Sound.valueOf(args[2]);
									if (sound != null) player.playSound(player.getLocation(), sound, Float.valueOf(args[3]), Float.valueOf(args[4]));
								} catch(Exception x) {
								}
							}
						}
					}
					else p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
				}
			}
		}
	}
	


	private String applyVariables(ServerCommandEvent e, String message) {
		while(message.contains("[online: ")) {
			String permission = message.substring(message.indexOf("[") + 9, message.indexOf("]"));
			StringBuilder online = new StringBuilder("");
			List<String> players = new ArrayList<String>();
			for (Player player: Bukkit.getOnlinePlayers()) {
				if (player.hasPermission(permission)) {
					players.add(player.getName());
				}
			}
			for (String player: players) {
				if (players.indexOf(player) < players.size() - 1) online.append("&a" + player + "&r, ");
				else  online.append("&a" + player + "&r");
			}
			
			message = message.replace("[online: " + permission + "]", online.toString());
		}
		while(message.contains("[rplayer ")) {
			String permission = message.substring(message.indexOf("[") + 9, message.indexOf("]"));
			List<String> names = new ArrayList<String>();
			for (Player pl: Bukkit.getOnlinePlayers()) {
				if (pl.hasPermission(permission)) names.add(pl.getName());
			}
			if (names.isEmpty()) message = message.replace("[rplayer " + permission + "]", "");
			else message = message.replace("[rplayer " + permission + "]", (String) ListUtils.getRandomEntries(names, 1).get(0));
		}
		while(message.contains("<args ")) {
			String args = message.substring(message.indexOf("<") + 6, message.indexOf(">"));
			StringBuilder builder = new StringBuilder();
			for (int i = Integer.parseInt(args.split(" - ")[0]); i <= Integer.parseInt(args.split(" - ")[1]); i++) {
				if ((e.getCommand().split(" ").length - 1) > i) builder.append(e.getCommand().split(" ")[i] + " ");
				else break;
			}
			message = message.replace("<args " + args + ">", builder.toString());
		}
		while(message.contains("<player>")) {
			message = message.replace("<player>", "CONSOLE");
		}
		while(message.contains("<rplayer>")) {
			message = message.replace("<rplayer>", Bukkit.getOnlinePlayers().toArray(new Player[Bukkit.getOnlinePlayers().size()])[CSCoreLib.randomizer().nextInt(Bukkit.getOnlinePlayers().size())].getName());
		}
		while(message.contains("<players_online>")) {
			message = message.replace("<players_online>", String.valueOf(Bukkit.getOnlinePlayers().size()));
		}
		while(message.contains("<players_max>")) {
			message = message.replace("<players_max>", String.valueOf(Bukkit.getMaxPlayers()));
		}
		
		while(message.contains("<arg ")) {
			int index = Integer.parseInt(message.substring(message.indexOf("<") + 5, message.indexOf(">")));
			String arg = (e.getCommand().split(" ").length - 1) > index ? e.getCommand().split(" ")[index + 1]: "";
			message = message.replace("<arg " + index + ">", arg);
		}
		while (message.contains("[unicode: ")) {
			String unicode = message.substring(message.indexOf("[") + 10, message.indexOf("]"));
			message = message.replace("[unicode: " + unicode + "]", String.valueOf((char) Integer.parseInt(unicode, 16)));
		}
		
		return message;
	}

	private String applyVariables(PlayerCommandPreprocessEvent e, String message) {
		Player p = e.getPlayer();
		while(message.contains("[online: ")) {
			String permission = message.substring(message.indexOf("[") + 9, message.indexOf("]"));
			StringBuilder online = new StringBuilder("");
			List<String> players = new ArrayList<String>();
			for (Player player: Bukkit.getOnlinePlayers()) {
				if (player.hasPermission(permission)) {
					players.add(player.getName());
				}
			}
			for (String player: players) {
				if (players.indexOf(player) < players.size() - 1) online.append("&a" + player + "&r, ");
				else  online.append("&a" + player + "&r");
			}
			
			message = message.replace("[online: " + permission + "]", online.toString());
		}
		while(message.contains("[rplayer ")) {
			String permission = message.substring(message.indexOf("[") + 9, message.indexOf("]"));
			List<String> names = new ArrayList<String>();
			for (Player pl: Bukkit.getOnlinePlayers()) {
				if (pl.hasPermission(permission)) names.add(pl.getName());
			}
			if (names.isEmpty()) message = message.replace("[rplayer " + permission + "]", "");
			else message = message.replace("[rplayer " + permission + "]", (String) ListUtils.getRandomEntries(names, 1).get(0));
		}
		while(message.contains("<args ")) {
			String args = message.substring(message.indexOf("<") + 6, message.indexOf(">"));
			StringBuilder builder = new StringBuilder();
			for (int i = Integer.parseInt(args.split(" - ")[0]); i <= Integer.parseInt(args.split(" - ")[1]); i++) {
				if ((e.getMessage().split(" ").length - 1) > i) builder.append(e.getMessage().split(" ")[i] + " ");
				else break;
			}
			message = message.replace("<args " + args + ">", builder.toString());
		}
		while(message.contains("<player>")) {
			message = message.replace("<player>", p.getName());
		}
		while(message.contains("<rplayer>")) {
			message = message.replace("<rplayer>", Bukkit.getOnlinePlayers().toArray(new Player[Bukkit.getOnlinePlayers().size()])[CSCoreLib.randomizer().nextInt(Bukkit.getOnlinePlayers().size())].getName());
		}
		while(message.contains("<players_online>")) {
			message = message.replace("<players_online>", String.valueOf(Bukkit.getOnlinePlayers().size()));
		}
		while(message.contains("<players_max>")) {
			message = message.replace("<players_max>", String.valueOf(Bukkit.getMaxPlayers()));
		}
		while(message.contains("<arg ")) {
			int index = Integer.parseInt(message.substring(message.indexOf("<") + 5, message.indexOf(">")));
			String arg = (e.getMessage().split(" ").length - 1) > index ? e.getMessage().split(" ")[index + 1]: "";
			message = message.replace("<arg " + index + ">", arg);
		}
		while (message.contains("[unicode: ")) {
			String unicode = message.substring(message.indexOf("[") + 10, message.indexOf("]"));
			message = message.replace("[unicode: " + unicode + "]", String.valueOf((char) Integer.parseInt(unicode, 16)));
		}
		
		if (isVaultInstalled) {
			while(message.contains("<rank-prefix>")) {
				message = message.replace("<rank-prefix>", chat.getPlayerPrefix(p));
			}
			while(message.contains("<rank-suffix>")) {
				message = message.replace("<rank-suffix>", chat.getPlayerSuffix(p));
			}
			while(message.contains("<money>")) {
				message = message.replace("<money>", String.valueOf(economy.getBalance(p)));
			}
			while(message.contains("<ezbalance>")) {
				message = message.replace("<ezbalance>", DoubleHandler.getFancyDouble(economy.getBalance(p)));
			}
		}
		
		if (isPlaceHolderAPIInstalled) {
			message = PlaceholderAPI.setPlaceholders(p, message);
		}
		
		return message;
	}
	
	public List<String> getMessages(String command) {
		if (cfg.contains("commands." + command)) return cfg.getStringList("commands." + command);
		else if (aliases.contains(command)) {
			if (cfg.contains("commands." + aliases.getString(command))) return cfg.getStringList("commands." + aliases.getString(command));
			else return new ArrayList<String>();
		}
		else return new ArrayList<String>();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("reloadcommands")) {
			if (sender instanceof ConsoleCommandSender || sender.hasPermission("CommandOverride.reload")) {
				sender.sendMessage(ChatColor.GREEN + "The config file has been reloaded!");
				cfg.reload();
				permissions.reload();
				aliases.reload();
				cost_money.reload();
				cost_xp.reload();
				cooldown.reload();
				arguments.reload();
			}
			else sender.sendMessage(ChatColor.DARK_RED + "You do not have permission to perform this command");
		}
		return true;
	}
	
}
