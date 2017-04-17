package org.plugins.simplefreeze.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugins.simplefreeze.SimpleFreezeMain;
import org.plugins.simplefreeze.managers.FreezeManager;
import org.plugins.simplefreeze.managers.PlayerManager;
import org.plugins.simplefreeze.managers.SQLManager;

import java.util.UUID;

public class UnfreezeCommand implements CommandExecutor {

    private final SimpleFreezeMain plugin;
    private final PlayerManager playerManager;
    private final FreezeManager freezeManager;
    private final SQLManager sqlManager;

    public UnfreezeCommand(SimpleFreezeMain plugin, PlayerManager playerManager, FreezeManager freezeManager, SQLManager sqlManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.freezeManager = freezeManager;
        this.sqlManager = sqlManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("unfreeze")) {

            if (!sender.hasPermission("sf.unfreeze")) {
                for (String msg : this.plugin.getConfig().getStringList("no-permission-message")) {
                    if (!msg.equals("")) {
                        sender.sendMessage(this.plugin.placeholders(msg));
                    }
                }
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(this.plugin.placeholders("{PREFIX}" + "Not enough arguments, try &b/unfreeze <name>"));
                return true;
            }

            String playerName;
            UUID uuid;
            Player onlineP = Bukkit.getPlayer(args[0]);
            OfflinePlayer offlineP = Bukkit.getOfflinePlayer(args[0]);

            if (onlineP != null) {
                playerName = onlineP.getName();
                uuid = onlineP.getUniqueId();
            } else if (offlineP != null) {
                if (offlineP.hasPlayedBefore()) {
                    playerName = offlineP.getName();
                    uuid = offlineP.getUniqueId();
                } else {
                    sender.sendMessage(this.plugin.placeholders("{PREFIX}&b{PLAYER} " + this.plugin.getFinalPrefixFormatting() + "has never played this server before").replace("{PLAYER}", args[0]));
                    return true;
                }
            } else {
                sender.sendMessage(this.plugin.placeholders("{PREFIX}&b{PLAYER} " + this.plugin.getFinalPrefixFormatting() + "has never played this server before").replace("{PLAYER}", args[0]));
                return true;
            }

            if (!this.playerManager.isFrozen(uuid)) {
                for (String msg : this.plugin.getConfig().getStringList("not-frozen")) {
                    if (!msg.equals("")) {
                        sender.sendMessage(this.plugin.placeholders(msg.replace("{PLAYER}", playerName)));
                    }
                }
                return true;
            }

            if (this.plugin.usingMySQL()) {
                this.sqlManager.addUnfreeze(playerName, uuid, sender.getName());
            } else {
                this.freezeManager.notifyOfUnfreeze(sender, uuid, playerName, this.plugin.getServerID());
                this.freezeManager.unfreeze(uuid);
            }

            return true;
        }

        return false;
    }

}
