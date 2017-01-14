package org.plugins.simplefreeze.cache;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.plugins.simplefreeze.SimpleFreezeMain;
import org.plugins.simplefreeze.managers.LocationManager;
import org.plugins.simplefreeze.objects.SFLocation;
import org.plugins.simplefreeze.util.TimeUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Created by Rory on 12/21/2016.
 */
public class FrozenPages {

    private final SimpleFreezeMain plugin;
    private final LocationManager locationManager;

    HashMap<UUID, String> playerInfo = new HashMap<UUID, String>();

    public FrozenPages(SimpleFreezeMain plugin, LocationManager locationManager) {
        this.plugin = plugin;
        this.locationManager = locationManager;
        this.setupStrings();
    }

    private void setupStrings() {
        for (String uuidStr : this.plugin.getPlayerConfig().getConfig().getConfigurationSection("players").getKeys(false)) {
            StringBuilder path = new StringBuilder("frozen");
            UUID freezeeUUID = UUID.fromString(uuidStr);

            UUID freezerUUID = this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid").equals("null") ? null : UUID.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid"));

            Location freezeLocation = SFLocation.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freeze-location"));

            String freezeeName = Bukkit.getPlayer(freezeeUUID) == null ? Bukkit.getOfflinePlayer(freezeeUUID).getName() : Bukkit.getPlayer(freezeeUUID).getName();

            String freezerName = freezerUUID == null ? "CONSOLE" : Bukkit.getPlayer(freezerUUID) == null ? Bukkit.getOfflinePlayer(freezerUUID).getName() : Bukkit.getPlayer(freezerUUID).getName();

            String onlinePlaceholder = Bukkit.getPlayer(freezeeUUID) != null ? this.plugin.getConfig().getString("frozen-list-format.online-placeholder") : this.plugin.getConfig().getString("frozen-list-format.offline-placeholder");

            String timePlaceholder = this.plugin.getPlayerConfig().getConfig().isSet("players." + uuidStr + ".unfreeze-date") ? TimeUtil.formatTime((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L) : "";

            if (!timePlaceholder.equals("")) {
                if ((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L <= 0) {
                    continue;
                }
                path = new StringBuilder("temp-frozen");
            }

            String locationName = this.locationManager.getLocationName(freezeLocation);
            if (locationName != null) {
                path.append("-location");
            }

            String locationPlaceholder = this.locationManager.getLocationPlaceholder(locationName);

            this.playerInfo.put(freezeeUUID, this.plugin.placeholders(this.plugin.getConfig().getString
                    ("frozen-list-format" + ".formats." + path).replace("{ONLINE}", onlinePlaceholder).replace("{PLAYER}", freezeeName).replace("{TIME}", timePlaceholder).replace("{LOCATION}", locationPlaceholder).replace("{FREEZER}", freezerName)));
        }
    }

    public void refreshString(UUID freezeeUUID) {
        String uuidStr = freezeeUUID.toString();
        StringBuilder path = new StringBuilder("frozen");

        UUID freezerUUID = this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid").equals("null") ? null : UUID.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid"));

        Location freezeLocation = SFLocation.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freeze-location"));

        String freezeeName = Bukkit.getPlayer(freezeeUUID) == null ? Bukkit.getOfflinePlayer(freezeeUUID).getName() : Bukkit.getPlayer(freezeeUUID).getName();

        String freezerName = freezerUUID == null ? "CONSOLE" : Bukkit.getPlayer(freezerUUID) == null ? Bukkit.getOfflinePlayer(freezerUUID).getName() : Bukkit.getPlayer(freezerUUID).getName();

        String onlinePlaceholder = Bukkit.getPlayer(freezeeUUID) != null ? this.plugin.getConfig().getString("frozen-list-format.online-placeholder") : this.plugin.getConfig().getString("frozen-list-format.offline-placeholder");

        String timePlaceholder = this.plugin.getPlayerConfig().getConfig().isSet("players." + uuidStr + ".unfreeze-date") ? TimeUtil.formatTime((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L) : "";

        if (!timePlaceholder.equals("")) {
            if ((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L <= 0) {
                return;
            }
            path = new StringBuilder("temp-frozen");
        }

        String locationName = this.locationManager.getLocationName(freezeLocation);
        if (locationName != null) {
            path.append("-location");
        }

        String locationPlaceholder = this.locationManager.getLocationPlaceholder(locationName);

        this.playerInfo.put(freezeeUUID, this.plugin.placeholders(this.plugin.getConfig().getString
                ("frozen-list-format" + ".formats." + path).replace("{ONLINE}", onlinePlaceholder).replace("{PLAYER}", freezeeName).replace("{TIME}", timePlaceholder).replace("{LOCATION}", locationPlaceholder).replace("{FREEZER}", freezerName)));
    }

    public void removePlayer(UUID uuid) {
        this.playerInfo.remove(uuid);
    }

    public void refreshStrings(HashSet<String> strings) {
        for (UUID freezeeUUID : this.playerInfo.keySet()) {

            String uuidStr = freezeeUUID.toString();
            StringBuilder path = new StringBuilder("frozen");

            String timePlaceholder = this.plugin.getPlayerConfig().getConfig().isSet("players." + uuidStr + ".unfreeze-date") ? TimeUtil.formatTime((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L) : "";

            if (!timePlaceholder.equals("")) {
                if ((this.plugin.getPlayerConfig().getConfig().getLong("players." + uuidStr + ".unfreeze-date") - System.currentTimeMillis()) / 1000L <= 0) {
                    return;
                }
                path = new StringBuilder("temp-frozen");
            }

            Location freezeLocation = SFLocation.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freeze-location"));

            String locationName = this.locationManager.getLocationName(freezeLocation);
            if (locationName != null) {
                path.append("-location");
            }

            if (!strings.contains(path.toString())) {
                continue;
            }

            UUID freezerUUID = this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid").equals("null") ? null : UUID.fromString(this.plugin.getPlayerConfig().getConfig().getString("players." + uuidStr + ".freezer-uuid"));

            String freezeeName = Bukkit.getPlayer(freezeeUUID) == null ? Bukkit.getOfflinePlayer(freezeeUUID).getName() : Bukkit.getPlayer(freezeeUUID).getName();

            String freezerName = freezerUUID == null ? "CONSOLE" : Bukkit.getPlayer(freezerUUID) == null ? Bukkit.getOfflinePlayer(freezerUUID).getName() : Bukkit.getPlayer(freezerUUID).getName();

            String onlinePlaceholder = Bukkit.getPlayer(freezeeUUID) != null ? this.plugin.getConfig().getString("frozen-list-format.online-placeholder") : this.plugin.getConfig().getString("frozen-list-format.offline-placeholder");

            String locationPlaceholder = this.locationManager.getLocationPlaceholder(locationName);

            this.playerInfo.put(freezeeUUID, this.plugin.placeholders(this.plugin.getConfig().getString
                    ("frozen-list-format" + ".formats." + path).replace("{ONLINE}", onlinePlaceholder).replace("{PLAYER}", freezeeName).replace("{TIME}", timePlaceholder).replace("{LOCATION}", locationPlaceholder).replace("{FREEZER}", freezerName)));
        }
    }

    public String getPage(int page) {
        StringBuilder sb = new StringBuilder();
        int firstIndex = (page - 1) * this.plugin.getConfig().getInt("frozen-list-format.players-per-page");
        int lastIndex = page * this.plugin.getConfig().getInt("frozen-list-format.players-per-page");
        int index = 0;
        for (String string : this.playerInfo.values()) {
            if (index >= firstIndex && index <= lastIndex) {
                sb.append(string + "\n");
            }
            index++;
        }
        return sb.substring(0, sb.length() - 1);
    }

    public boolean noPages() {
        if (this.playerInfo.size() == 0) {
            return true;
        }
        return false;
    }

    public int getMaxPageNum() {
        return (int) Math.ceil((double) this.playerInfo.size() / this.plugin.getConfig().getDouble("frozen-list-format.players-per-page"));
    }

}
