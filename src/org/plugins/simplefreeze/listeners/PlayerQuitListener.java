package org.plugins.simplefreeze.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.plugins.simplefreeze.SimpleFreezeMain;
import org.plugins.simplefreeze.managers.MessageManager;
import org.plugins.simplefreeze.managers.PlayerManager;
import org.plugins.simplefreeze.objects.TempFrozenPlayer;

public class PlayerQuitListener implements Listener {

    private final SimpleFreezeMain plugin;
    private final PlayerManager playerManager;
    private final MessageManager messageManager;

    public PlayerQuitListener(SimpleFreezeMain plugin, PlayerManager playerManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (this.playerManager.isFrozen(p)) {
            Location originalLocation = this.playerManager.getOriginalLocation(p.getUniqueId());
            if (originalLocation != null && this.plugin.getConfig().getBoolean("tp-back")) {
                p.teleport(originalLocation);
            }
            p.getInventory().setHelmet(this.playerManager.getFrozenPlayer(p).getHelmet());
            if (this.plugin.getConfig().getBoolean("enable-fly")) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
            if (this.playerManager.getFrozenPlayer(p) instanceof TempFrozenPlayer) {
                ((TempFrozenPlayer) this.playerManager.getFrozenPlayer(p)).cancelTask();
            }

            if (!(this.playerManager.isFreezeAllFrozen(p) && !this.plugin.getConfig().getBoolean("leave-message-during-freezeall"))) {
                for (Player onlineP : Bukkit.getServer().getOnlinePlayers()) {
                    if (onlineP.hasPermission("sf.notify.leave")) {
                        for (String msg : this.plugin.getConfig().getStringList("notify-on-leave-message")) {
                            onlineP.sendMessage(this.plugin.placeholders(msg.replace("{PLAYER}", p.getName())));
                        }
                    }
                }
            }

            if (!(this.playerManager.isFreezeAllFrozen(p) && !this.plugin.getConfig().getBoolean("freezeall-logout-commands"))) {
                for (String cmd : this.plugin.getConfig().getStringList("logout-commands")) {
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{PLAYER}", p.getName()));
                }
            }

            this.playerManager.removeFrozenPlayer(p);
            this.messageManager.removePlayer(p);

        }
    }

}
