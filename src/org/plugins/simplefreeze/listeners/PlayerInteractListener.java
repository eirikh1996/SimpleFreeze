package org.plugins.simplefreeze.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.plugins.simplefreeze.SimpleFreezeMain;
import org.plugins.simplefreeze.managers.PlayerManager;

public class PlayerInteractListener implements Listener {

    private final SimpleFreezeMain plugin;
    private final PlayerManager playerManager;

    public PlayerInteractListener(SimpleFreezeMain plugin, PlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (this.playerManager.isFrozen(e.getPlayer()) && e.getAction() == Action.RIGHT_CLICK_BLOCK && !this.plugin.getConfig().getBoolean("interact")) {
            if (!(e.getPlayer().getInventory().getItemInHand().getType().isBlock() && !this.plugin.getConfig().getBoolean("block-place"))) {
                e.setCancelled(true);
                for (String msg : this.plugin.getConfig().getStringList("interact-message")) {
                    e.getPlayer().sendMessage(this.plugin.placeholders(msg));
                }
            }
        }
    }

}
