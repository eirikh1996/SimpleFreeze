package org.plugins.simplefreeze;

import net.milkbowl.vault.permission.Permission;
import org.apache.commons.lang.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugins.simplefreeze.cache.FrozenPages;
import org.plugins.simplefreeze.commands.*;
import org.plugins.simplefreeze.hooks.BanManagerHook;
import org.plugins.simplefreeze.hooks.EssentialsHook;
import org.plugins.simplefreeze.hooks.LiteBansHook;
import org.plugins.simplefreeze.listeners.*;
import org.plugins.simplefreeze.managers.*;
import org.plugins.simplefreeze.objects.FreezeAllPlayer;
import org.plugins.simplefreeze.objects.FrozenPlayer;
import org.plugins.simplefreeze.objects.SFLocation;
import org.plugins.simplefreeze.objects.TempFrozenPlayer;
import org.plugins.simplefreeze.util.*;

import java.util.HashMap;
import java.util.UUID;

/* 
 * TODO
 *  - MySQL
 *  
 *  TODO: (Hopefully)
 *  - Player history
 *  - Effects on freeze
 *  - GUI for commands
 *  - Hologram above players head on freeze option
 *  - Titles
 *  - Actionbar
 *
 */

/* CHANGES:
 *   - Make time placeholder on head item update every second
 *   - Particles change on /sf reload
 *   - ALLOW OFFLINE FREEZING/UNFREEZING
 *   - PLAYERS MAY NOW EDIT THEIR INVENTORY WHILE FROZEN
 *   - SQL FREEZING
 *   - TEMPORARY FREEZING
 *   - HEAD-BLOCK HAS MORE OPTIONS, CAN BE ANY ITEM/BLOCK, PLACEHOLDERS, ITEMFLAGS, ENCHANTS, LORE, NAME
 *   - HEAD-BLOCK ALSO WILL BE PUT ON A PLAYERS HEAD EVEN IF THEY ALREADY HAVE A HELMET AND THEIR INVENTORY IS FULL
 *   - /SF RELOAD NOW UPDATES HELMETS AND PARTICLES IF THEY ARE CHANGED
 *   - CUSTOMIZABLE /FROZEN FORMAT
 *   - REPLACED THE OLD .TXT FORMAT WITH A NEW CLEANER AND FASTER .YML FORMAT
 *       - ON JOIN DATA WILL CONVERT PER PLAY SO DON'T DELETE UNTIL/UNLESS THE FILE IS EMPTY
 *   - DISTANCE CHECK NOW OPTIONALLY INCLUDES Y-CORD
 *   - TELEPORT-UP REPLACED WITH TELEPORT-TO-GROUND
 *   - BLOCKED NETHER PORTAL AND END PORTAL TELEPORTATION WHILE FROZEN
 *   - BLOCKED BOOK CHANGING
 *   - MADE BLOCKING OF INTERACTION, BLOCK-BREAKING, ITEM DROPPING AND BOOK EDITING TOGGLEABLE
 *   - ENABLE FLY ON FREEZE SO PLAYERS ARENT KICKED FOR FLYING
 *   - PROPERLY BLOCKED PROJECTILE SHOOTING (ex. bow shooting, eggs, snowballs, fishing rod, splash potions, exp bottles)
 *   - FREEZE MESSAGES CAN NOW BE SENT ON AN INTERVAL
 *   - ADDED OPTIONAL REASON PARAMETER TO /freeze, /tempfreeze AND /freezeall
 *   - PLAYERS ARE UNFREEZED ON BAN
 *   - MOVEMENT HANDLED MORE EFFICIENTLY
 * 
 * BUGS:
 *   - FIXED BUG WHERE PLAYERS WERE SOMETIMES TELEPORTED INTO SUFFOCATION THROUGH THE TELEPORT-UP OPTION
 *
 */

/* NMS:
 * net.minecraft.server.v1_7_R4
 * net.minecraft.server.v1_8_R1
 * net.minecraft.server.v1_8_R2
 * net.minecraft.server.v1_8_R3
 * net.minecraft.server.v1_9_R1
 * net.minecraft.server.v1_9_R2
 * net.minecraft.server.v1_10_R1
 */

public class SimpleFreezeMain extends JavaPlugin {

    private static SimpleFreezeMain plugin;

    private String finalPrefixFormatting = this.updateFinalPrefixFormatting();

    private Permission permission = null;
    private boolean usingLiteBans = false;
    private boolean usingBanManager = false;
    private boolean usingEssentials = false;

    private PlayerManager playerManager;
    private FreezeManager freezeManager;
    private PlayersConfig playersConfig;
    private StatsConfig statsConfig;
    private HelmetManager helmetManager;
    private LocationManager locationManager;
    private SQLManager sqlManager;
    private FrozenPages frozenPages;
    private DataConverter dataConverter;
    private ParticleManager particleManager;
    private SoundManager soundManager;
    private MessageManager messageManager;
    private MovementManager movementManager;

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            this.permission = permissionProvider.getProvider();
        }
        return (this.permission != null);
    }

    public boolean vaultEnabled() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }

    @Override
    public void onEnable() {
        SimpleFreezeMain.plugin = this;
        this.initializeVariables();
        this.loadConfigs();
        this.soundManager.reset();
        if (vaultEnabled()) {
            this.setupPermissions();
            Bukkit.getConsoleSender().sendMessage(this.placeholders("[SimpleFreeze] Vault found, offline freezing &aenabled"));
        } else {
            Bukkit.getConsoleSender().sendMessage(this.placeholders("[SimpleFreeze] Vault not found, offline freezing &cdisabled"));
        }
        this.registerCommands();
        this.registerListeners();
        Metrics metrics = new Metrics(this);
        metrics.addCustomChart(new Metrics.AdvancedPie("freeze_counts") {

            @Override
            public HashMap<String, Integer> getValues(HashMap<String, Integer> valueMap) {
                valueMap.put("Freeze", statsConfig.getConfig().getInt("freeze-count"));
                valueMap.put("Temp Freeze", statsConfig.getConfig().getInt("temp-freeze-count"));
                valueMap.put("Freezeall", statsConfig.getConfig().getInt("freezeall-count"));
                valueMap.put("Unfreeze", statsConfig.getConfig().getInt("unfreeze-count"));
                return valueMap;
            }
        });

        for (final Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (p.hasPermission("sf.notify.update") && !UpdateNotifier.getLatestVersion().equals(UpdateNotifier.getCurrentVersion())) {
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        p.sendMessage(placeholders("{PREFIX}You are still running version &b" + UpdateNotifier.getCurrentVersion() + "\n{PREFIX}Latest version: &b" + UpdateNotifier.getLatestVersion()));

                    }

                }.runTaskLater(this, 25L);
            }

            final String uuidStr = p.getUniqueId().toString();
            FrozenPlayer frozenPlayer = null;

            if (DataConverter.hasDataToConvert(p)) {
                frozenPlayer = this.dataConverter.convertData(p);
                if (frozenPlayer != null) {
                    this.playerManager.addFrozenPlayer(p.getUniqueId(), frozenPlayer);
                }
            } else if (this.getPlayerConfig().getConfig().isSet("players." + uuidStr)) {
                Long freezeDate = this.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".freeze-date");
                UUID freezerUUID = this.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid").equals("null") ? null : UUID.fromString(this.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid"));
                String originalLocStr = this.getPlayerConfig().getConfig().getString("players." + uuidStr + ".original-location");
                Location originalLocation = originalLocStr.equals("null") ? p.getLocation() : SFLocation.fromString(originalLocStr);
                if (originalLocation.equals(p.getLocation())) {
                    this.getPlayerConfig().getConfig().set("players." + uuidStr + ".original-location", new SFLocation(p.getLocation()).toString());
                    this.getPlayerConfig().saveConfig();
                    this.getPlayerConfig().reloadConfig();
                }
                Location freezeLocation = SFLocation.fromString(this.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freeze-location"));
                boolean sqlFreeze = this.getPlayerConfig().getConfig().getBoolean("players." + uuidStr + ".mysql");
                if (this.getPlayerConfig().getConfig().isSet("players." + uuidStr + ".unfreeze-date")) {
                    Long unfreezeDate = this.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date");
                    if (System.currentTimeMillis() < unfreezeDate) {
                        frozenPlayer = new TempFrozenPlayer(freezeDate, unfreezeDate, p.getUniqueId(), freezerUUID, originalLocation, freezeLocation, sqlFreeze);
                        ((TempFrozenPlayer) frozenPlayer).startTask(this);
                        this.playerManager.addFrozenPlayer(p.getUniqueId(), frozenPlayer);
                    } else if (!getPlayerConfig().getConfig().getBoolean("players." + uuidStr + ".mysql", false)) {
                        getPlayerConfig().getConfig().set("players." + uuidStr, null);
                        this.getPlayerConfig().saveConfig();
                        this.getPlayerConfig().reloadConfig();
                    } else {
                        // SQL TABLE STUFF
                    }
                } else {
                    frozenPlayer = new FrozenPlayer(freezeDate, p.getUniqueId(), freezerUUID, originalLocation, freezeLocation, sqlFreeze);
                    this.playerManager.addFrozenPlayer(p.getUniqueId(), frozenPlayer);
                }
            }

            if (frozenPlayer == null && this.freezeManager.freezeAllActive() && !(p.hasPermission("sf.exempt.*") || p.hasPermission("sf.exempt.freezeall"))) {
                UUID freezerUUID = this.getPlayerConfig().getConfig().getString("freezeall-info.freezer").equals("null") ? null : UUID.fromString(this.getPlayerConfig().getConfig().getString("freezeall-info.freezer"));

                SFLocation freezeLocation = this.getPlayerConfig().getConfig().getString("freezeall-info.location").equals("null") ? null : SFLocation.fromString(this.getPlayerConfig().getConfig().getString("freezeall-info.location"));
                if (freezeLocation == null && this.getPlayerConfig().getConfig().isSet("freezeall-info.players." + uuidStr + ".freeze-location")) {
                    freezeLocation = this.getPlayerConfig().getConfig().getString("freezeall-info.players." + uuidStr + ".freeze-location").equals("null") ? null : SFLocation.fromString(this.getPlayerConfig().getConfig().getString("freezeall-info.players." + uuidStr + ".freeze-location"));
                } else if (freezeLocation == null) {
                    if (this.getConfig().getBoolean("teleport-to-ground")) {
                        freezeLocation = new SFLocation(this.locationManager.getGroundLocation(p.getLocation()));
                    } else {
                        freezeLocation = new SFLocation(new SFLocation(p.getLocation().clone()));
                        if (this.getConfig().getBoolean("enable-fly")) {
                            p.setAllowFlight(true);
                            p.setFlying(true);
                        }
                    }
                }

                Long freezeDate = this.getPlayerConfig().getConfig().getLong("freezeall-info.date");

                frozenPlayer = new FreezeAllPlayer(freezeDate, p.getUniqueId(), freezerUUID, p.getLocation(), freezeLocation);
                this.playerManager.addFrozenPlayer(p.getUniqueId(), frozenPlayer);

                if (!this.getPlayerConfig().getConfig().isSet("freezeall-info.players." + uuidStr)) {
                    this.getPlayerConfig().getConfig().set("freezeall-info.players." + uuidStr + ".original-location", new SFLocation(frozenPlayer.getOriginalLoc()).toString());
                    this.getPlayerConfig().getConfig().set("freezeall-info.players." + uuidStr + ".freeze-location", freezeLocation == null ? "null" : freezeLocation.toString());
                    this.getPlayerConfig().saveConfig();
                    this.getPlayerConfig().reloadConfig();
                }

                final FrozenPlayer finalFreezeAllPlayer = frozenPlayer;

                new BukkitRunnable() {

                    @Override
                    public void run() {
                        finalFreezeAllPlayer.setHelmet(p.getInventory().getHelmet());
                        p.getInventory().setHelmet(helmetManager.getPersonalHelmetItem(finalFreezeAllPlayer));

                        if (finalFreezeAllPlayer.getFreezeLoc() == null) {
                            SFLocation originalLoc = new SFLocation(finalFreezeAllPlayer.getOriginalLoc());
                            Location freezeLoc;
                            if (getConfig().getBoolean("teleport-to-ground")) {
                                freezeLoc = locationManager.getGroundLocation(originalLoc);
                            } else {
                                freezeLoc = new SFLocation(originalLoc.clone());
                            }
                            finalFreezeAllPlayer.setFreezeLoc(freezeLoc);

                            if (getPlayerConfig().getConfig().getString("freezeall-info.players." + uuidStr + ".freeze-location").equals("null")) {
                                getPlayerConfig().getConfig().set("freezeall-info.players." + uuidStr + ".freeze-location", new SFLocation(freezeLoc).toString());
                            }
                        }

                        if (getConfig().getBoolean("enable-fly")) {
                            p.setAllowFlight(true);
                            p.setFlying(true);
                        }
                        p.teleport(finalFreezeAllPlayer.getFreezeLoc());

                        soundManager.playFreezeSound(p);

                        p.sendMessage(placeholders("{PREFIX}SimpleFreeze was re-enabled so you are now frozen again"));

                        String msg = "";
                        for (String line : plugin.getConfig().getStringList("freezeall-message")) {
                            msg += line + "\n";
                        }

                        String location = locationManager.getLocationName(finalFreezeAllPlayer.getFreezeLoc());
                        String locationPlaceholder = locationManager.getLocationPlaceholder(location);

                        msg = msg.length() > 2 ? msg.substring(0, msg.length() - 1) : "";
                        msg = msg.replace("{PLAYER}", p.getName()).replace("{FREEZER}", finalFreezeAllPlayer.getFreezerName()).replace("{LOCATION}", locationPlaceholder);
                        if (location == null && messageManager.getFreezeAllInterval() > 0) {
                            messageManager.addFreezeAllPlayer(p, msg);
                        } else if (messageManager.getFreezeAllLocInterval() > 0) {
                            messageManager.addFreezeAllLocPlayer(p, msg);
                        }
                    }
                }.runTaskLater(this, 10L);

            } else if (frozenPlayer != null) {
                final FrozenPlayer finalFrozenPlayer = frozenPlayer;
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        finalFrozenPlayer.setHelmet(p.getInventory().getHelmet());
                        p.getInventory().setHelmet(helmetManager.getPersonalHelmetItem(finalFrozenPlayer));

                        if (getPlayerConfig().getConfig().getString("players." + uuidStr + ".original-location").equals("null")) {
                            finalFrozenPlayer.setOriginalLoc(p.getLocation());
                            getPlayerConfig().getConfig().set("players." + uuidStr + ".original-location", new SFLocation(p.getLocation()).toString());
                        }

                        if (finalFrozenPlayer.getFreezeLoc() == null) {
                            SFLocation originalLoc = new SFLocation(finalFrozenPlayer.getOriginalLoc());
                            Location freezeLoc;
                            if (getConfig().getBoolean("teleport-to-ground")) {
                                freezeLoc = locationManager.getGroundLocation(originalLoc);
                            } else {
                                freezeLoc = new SFLocation(originalLoc.clone());
                            }
                            finalFrozenPlayer.setFreezeLoc(freezeLoc);

                            if (getPlayerConfig().getConfig().getString("players." + uuidStr + ".freeze-location").equals("null")) {
                                getPlayerConfig().getConfig().set("players." + uuidStr + ".freeze-location", new SFLocation(freezeLoc).toString());
                            }
                        }

                        if (getConfig().getBoolean("enable-fly")) {
                            p.setAllowFlight(true);
                            p.setFlying(true);
                        }
                        p.teleport(finalFrozenPlayer.getFreezeLoc());

                        soundManager.playFreezeSound(p);

                        if (getPlayerConfig().getConfig().getBoolean("players. " + uuidStr + ".message", false)) {
                            String location = locationManager.getLocationName(finalFrozenPlayer.getFreezeLoc());
                            String freezerName = finalFrozenPlayer.getFreezerName();
                            String timePlaceholder = "";
                            String serversPlaceholder = "";
                            String locationPlaceholder = location == null ? getConfig().getString("location") : getConfig().getString("locations." + location + ".placeholder", location);
                            String path;
                            if (finalFrozenPlayer instanceof TempFrozenPlayer) {
                                timePlaceholder = TimeUtil.formatTime((((TempFrozenPlayer) finalFrozenPlayer).getUnfreezeDate() - System.currentTimeMillis()) / 1000L);
                                path = "first-join.temp-frozen";
                            } else {
                                path = "first-join.frozen";
                            }

                            if (location != null) {
                                path += "-location";
                            }
                            p.sendMessage(placeholders(getConfig().getString(path).replace("{PLAYER}", p.getName()).replace("{FREEZER}", freezerName).replace("{TIME}", timePlaceholder).replace("{LOCATION}", locationPlaceholder)));
                            getPlayerConfig().getConfig().set("players." + uuidStr + ".message", null);
                            getPlayerConfig().saveConfig();
                            getPlayerConfig().reloadConfig();
                        } else {
                            p.sendMessage(placeholders("{PREFIX}SimpleFreeze was re-enabled so you are now frozen again"));
                        }

                        String serversPlaceholder = "";
                        String location = locationManager.getLocationName(finalFrozenPlayer.getFreezeLoc());
                        String locationPlaceholder = locationManager.getLocationPlaceholder(location);
                        if (finalFrozenPlayer instanceof TempFrozenPlayer) {
                            TempFrozenPlayer tempFrozenPlayer = (TempFrozenPlayer) finalFrozenPlayer;
                            String msg = "";
                            for (String line : plugin.getConfig().getStringList("temp-freeze-message")) {
                                msg += line + "\n";
                            }
                            msg = msg.length() > 2 ? msg.substring(0, msg.length() - 1) : "";
                            msg = msg.replace("{PLAYER}", p.getName()).replace("{FREEZER}", finalFrozenPlayer.getFreezerName()).replace("{LOCATION}", locationPlaceholder).replace("{SERVERS}", serversPlaceholder);
                            if (location == null && messageManager.getTempFreezeInterval() > 0) {
                                messageManager.addTempFreezePlayer(p, msg);
                            } else if (messageManager.getTempFreezeLocInterval() > 0) {
                                messageManager.addTempFreezeLocPlayer(p, msg);
                            }
                        } else {
                            String msg = "";
                            for (String line : plugin.getConfig().getStringList("freeze-message")) {
                                msg += line + "\n";
                            }
                            msg = msg.length() > 2 ? msg.substring(0, msg.length() - 1) : "";
                            msg = msg.replace("{PLAYER}", p.getName()).replace("{FREEZER}", finalFrozenPlayer.getFreezerName()).replace("{LOCATION}", locationPlaceholder).replace("{TIME}", "").replace("{SERVERS}", serversPlaceholder);
                            if (location == null && messageManager.getFreezeInterval() > 0) {
                                messageManager.addFreezePlayer(p, msg);
                            } else if (messageManager.getFreezeLocInterval() > 0) {
                                messageManager.addFreezeLocPlayer(p, msg);
                            }
                        }

                    }
                }.runTaskLater(this, 10L);
            }
        }

        new BukkitRunnable() {

            @Override
            public void run() {
                frozenPages.setupStrings();
            }
        }.runTaskLater(this, 20L);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (this.playerManager.isFrozen(p)) {
                FrozenPlayer frozenPlayer = this.playerManager.getFrozenPlayer(p);
                p.teleport(frozenPlayer.getOriginalLoc());
                if (p.isFlying()) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.teleport(this.locationManager.getGroundLocation(p.getLocation()));
                }
                p.getInventory().setHelmet(frozenPlayer.getHelmet());
                p.sendMessage(this.placeholders("{PREFIX}SimpleFreeze has been disabled, you will remain unfrozen until it is re-enabled"));
                if (frozenPlayer instanceof TempFrozenPlayer) {
                    ((TempFrozenPlayer) frozenPlayer).cancelTask();
                }
            }
        }
        this.movementManager.endTask();
        this.particleManager.endTask();
    }

    public void setupHookBooleans() {
        PluginManager plManager = Bukkit.getServer().getPluginManager();
        this.usingLiteBans = plManager.getPlugin("LiteBans") != null;
        this.usingBanManager = plManager.getPlugin("BanManager") != null;
        this.usingEssentials = (plManager.getPlugin("Essentials") != null || plManager.getPlugin("EssentialsX") != null);
    }

    private void initializeVariables() {
        this.playersConfig = new PlayersConfig(this);
        this.messageManager = new MessageManager(this);
        this.statsConfig = new StatsConfig(this);
        this.sqlManager = new SQLManager(this);
        this.locationManager = new LocationManager(this);
        this.dataConverter = new DataConverter(this);
        this.soundManager = new SoundManager(this);
        this.frozenPages = new FrozenPages(this, this.locationManager);
        this.playerManager = new PlayerManager(this, this.frozenPages);
        this.particleManager = new ParticleManager(this, this.playerManager);
        this.movementManager = new MovementManager(this, this.playerManager);
        this.helmetManager = new HelmetManager(this, this.playerManager, this.locationManager);
        this.freezeManager = new FreezeManager(this, this.playerManager, this.helmetManager, this.locationManager, this.sqlManager, this.frozenPages, this.soundManager, this.messageManager);
        this.setupHookBooleans();
    }

    private void loadConfigs() {
        this.getConfig().options().copyDefaults(true);
        this.saveDefaultConfig();
        this.reloadConfig();

        this.playersConfig.getConfig().options().copyDefaults(true);
        this.playersConfig.saveDefaultConfig();
        this.playersConfig.reloadConfig();

        this.statsConfig.getConfig().options().copyDefaults(true);
        this.statsConfig.saveDefaultConfig();
        this.statsConfig.reloadConfig();
    }

    private void registerCommands() {
        this.getCommand("simplefreeze").setExecutor(new SimpleFreezeCommand(this, this.helmetManager, this.frozenPages, this.particleManager, this.soundManager, this.messageManager, this.movementManager));
        this.getCommand("freeze").setExecutor(new FreezeCommand(this, this.playerManager, this.freezeManager, this.locationManager, this.permission));
        this.getCommand("tempfreeze").setExecutor(new TempFreezeCommand(this, this.playerManager, this.freezeManager, this.locationManager, this.permission));
        this.getCommand("unfreeze").setExecutor(new UnfreezeCommand(this, this.playerManager, this.freezeManager));
        this.getCommand("frozen").setExecutor(new FrozenCommand(this, this.frozenPages));
        this.getCommand("freezeall").setExecutor(new FreezeAllCommand(this, this.freezeManager, this.locationManager));
    }

    private void registerListeners() {
        PluginManager plManager = this.getServer().getPluginManager();
        plManager.registerEvents(new BlockBreakListener(this, this.playerManager), this);
        plManager.registerEvents(new EntityCombustListener(this, this.playerManager), this);
        plManager.registerEvents(new EntityDamageEntityListener(this, this.playerManager), this);
        plManager.registerEvents(new EntityDamageListener(this, this.playerManager), this);
        plManager.registerEvents(new InventoryClickListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerChatListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerCommandListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerDropListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerEditBookListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerTeleportListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerInteractListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerJoinListener(this, this.freezeManager, this.playerManager, this.locationManager, this.helmetManager, this.dataConverter, this.soundManager, this.messageManager), this);
        //plManager.registerEvents(new PlayerMoveListener(this, this.playerManager), this);
        plManager.registerEvents(new PlayerQuitListener(this, this.playerManager, this.messageManager), this);
        plManager.registerEvents(new PlayerToggleFlightListener(this, this.playerManager), this);
        plManager.registerEvents(new ProjectileLaunchListener(this, this.playerManager), this);
        plManager.registerEvents(new LiteBansHook(this, this.playerManager, this.freezeManager), this);
        plManager.registerEvents(new BanManagerHook(this, this.playerManager, this.freezeManager), this);
        plManager.registerEvents(new EssentialsHook(this, this.playerManager, this.freezeManager), this);
    }

    public PlayerManager getPlayerManager() {
        return this.playerManager;
    }

    public LocationManager getLocationManager() {
        return this.locationManager;
    }

    public MessageManager getMessageManager() {
        return this.messageManager;
    }

    public PlayersConfig getPlayerConfig() {
        return this.playersConfig;
    }

    public StatsConfig getStatsConfig() {
        return this.statsConfig;
    }

    public boolean usingLiteBans() {
        return this.usingLiteBans;
    }

    public boolean usingEssentials() {
        return this.usingEssentials;
    }

    public boolean usingBanManager() {
        return this.usingBanManager;
    }

    public String placeholders(String arg) {
        return StringEscapeUtils.unescapeJava(ChatColor.translateAlternateColorCodes('&', arg.replace("{PREFIX}", this.getConfig().getString("prefix")).replace("{PREFIXFORMAT}", this.getFinalPrefixFormatting())));
    }

    public String getHelpMessage() {
        return this.placeholders("                                           &b&lSimpleFreeze\n" + "&b/sf &8- &7Displays this message\n" + "&b/sf reload &8- &7Reloads configuration file\n" + "&b/frozenlist [page] &8- &7Lists frozen players\n"
                + "&b/freeze <name> [location/servers] [reason] &8- &7Freezes a player\n" + "&b/tempfreeze <name> <time> [location/servers] &8- &7Temporarily freezes a player\n" + "&b/unfreeze <name> &8- &7Unfreezes a player\n"
                + "&b/freezeall [reason] &8- &7Freeze all players\n");
    }

    public String getFinalPrefixFormatting() {
        return this.finalPrefixFormatting == null ? "" : this.finalPrefixFormatting;
    }

    public String updateFinalPrefixFormatting() {
        String color = "", format = "";
        String prefix = this.placeholders("{PREFIX}");
        if (prefix.length() > 1) {
            for (int index = prefix.length(); index > 1; index--) {
                String bit = prefix.substring(index - 2, index);
                if (bit.startsWith("§")) {
                    int chNum = (int) bit.toLowerCase().charAt(1);
                    if ((97 <= chNum && chNum <= 102) || (48 <= chNum && chNum <= 57) || chNum == 114) {
                        color = bit;
                        break;
                    }
                    if (107 <= chNum && chNum <= 112) {
                        format += bit;
                    }

                }
            }
        }
        this.finalPrefixFormatting = color + format;
        return color + format;
    }

}
