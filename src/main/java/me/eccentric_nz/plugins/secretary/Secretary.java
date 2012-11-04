package me.eccentric_nz.plugins.secretary;

import java.io.*;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class Secretary extends JavaPlugin implements Listener {

    public PluginDescriptionFile pdfFile;
    public FileConfiguration config = null;
    public FileConfiguration secrets = null;
    public FileConfiguration todos = null;
    public FileConfiguration reminds = null;
    public HashMap<Player, UUID> PlayerEntityMap = new HashMap<Player, UUID>();
    public File myconfigfile = null;
    public File secretariesfile = null;
    public File todofile = null;
    public File remindersfile = null;
    private Material t;
    private Material r;
    private Material s;
    private SecretaryExecutor secretaryExecutor;
    private boolean profession = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        pdfFile = getDescription();
        Constants.MY_PLUGIN_NAME = "[" + pdfFile.getName() + "]";

        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }
        } catch (Exception e) {
            System.out.println("Secretary 1.0 could not create directory!");
            System.out.println("Secretary 1.0 requires you to manually make the Secretary/ directory!");
        }

        getDataFolder().setWritable(true);
        getDataFolder().setExecutable(true);

        if (config == null) {
            loadConfig();
        }

        secretaryExecutor = new SecretaryExecutor(this);
        getCommand("secretary").setExecutor(secretaryExecutor);

        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                long currentTime = System.currentTimeMillis();
                // check reminders file
                Set<String> playerlist = secrets.getKeys(false);
                for (String d : playerlist) {
                    if (reminds.isSet(d)) {
                        Set<String> uuidlist = reminds.getConfigurationSection(d).getKeys(false);
                        for (String u : uuidlist) {
                            String configPath = d + "." + u;
                            Set<String> remindlist = reminds.getConfigurationSection(configPath).getKeys(false);
                            for (String r : remindlist) {
                                long a = reminds.getLong(configPath + "." + r + ".alarm");
                                if (a < currentTime) {
                                    if (Bukkit.getPlayer(d) != null) {
                                        Player player = Bukkit.getPlayer(d);
                                        Location l = player.getLocation();
                                        String name = secrets.getString(configPath + ".name");
                                        String effSound;
                                        if (secrets.isSet(configPath + ".sound")) {
                                            effSound = secrets.getString(configPath + ".sound");
                                        } else {
                                            effSound = "GHAST_SHRIEK";
                                        }
                                        String worldname = secrets.getString(configPath + ".location.world");
                                        Effect eff = Effect.valueOf(effSound);
                                        player.playEffect(l, eff, 0);
                                        player.sendMessage("Your secretary '" + name + "' (in world: " + worldname + "),");
                                        player.sendMessage("would like to remind you to:");
                                        String m = reminds.getConfigurationSection(configPath + "." + r).getName();
                                        player.sendMessage(ChatColor.BLUE + m);
                                        if (reminds.isSet(configPath + "." + r + ".repeat")) {
                                            // get diff between time-set and alarm
                                            long tset = reminds.getLong(d + "." + u + "." + r + ".time-set");
                                            long newalarm = currentTime + (a - tset);
                                            reminds.set(d + "." + u + "." + r + ".time-set", currentTime);
                                            reminds.set(d + "." + u + "." + r + ".alarm", newalarm);
                                        } else {
                                            reminds.set(d + "." + u + "." + r, null);
                                        }
                                        try {
                                            reminds.save(remindersfile);
                                        } catch (IOException e) {
                                            System.out.println("Could not save the reminders file!");
                                        }
                                    }
                                    //else {
                                    //System.out.println("Player not online!");
                                    //}
                                }
                            }
                        }
                    }
                }
                //System.out.println("Checking reminders file");
            }
        }, 60L, 1200L);
    }

    @Override
    public void onDisable() {
        saveCustomConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {

        Player player = event.getPlayer();

        Entity entity = event.getRightClicked();
        EntityType entityType = entity.getType();
        UUID entID = entity.getUniqueId();
        ItemStack stack = player.getItemInHand();
        Material material = stack.getType();
        String configPath = player.getName() + "." + entID;

        if (entityType.equals(EntityType.VILLAGER)) {

            ChatColor colour;

            if (material.equals(Material.PAPER) || material.equals(Material.INK_SACK) || material.equals(Material.FEATHER)) {

                secrets = YamlConfiguration.loadConfiguration(secretariesfile);

                if (player.hasPermission("secretary.list")) {

                    if (secrets.contains(configPath)) {

                        todos = YamlConfiguration.loadConfiguration(todofile);
                        reminds = YamlConfiguration.loadConfiguration(remindersfile);

                        if (material.equals(Material.PAPER)) {
                            if (todos.contains(configPath)) {
                                Constants.list(todos, configPath, player, "todos");
                            } else {
                                player.sendMessage(Constants.NO_TODOS);
                            }
                        }

                        if (material.equals(Material.INK_SACK)) {
                            if (reminds.contains(configPath)) {
                                Constants.list(reminds, configPath, player, "reminders");
                            } else {
                                player.sendMessage(Constants.NO_REMINDS);
                            }
                        }
                    } else {
                        player.sendMessage(Constants.NOT_OWNER);
                    }

                } else {
                    player.sendMessage(Constants.NO_PERMS_MESSAGE);
                }

                if (player.hasPermission("secretary.remind") || player.hasPermission("secretary.todo") || player.hasPermission("secretary.delete")) {

                    if (material.equals(Material.FEATHER)) {
                        if (secrets.contains(configPath)) {
                            colour = ChatColor.GRAY;
                            if (PlayerEntityMap.containsKey(player)) {
                                PlayerEntityMap.remove(player);
                                player.sendMessage(colour + Constants.CMD_MESSAGE_OFF);
                            } else {
                                PlayerEntityMap.put(player, entID);
                                player.sendMessage(colour + Constants.CMD_MESSAGE_ON);
                            }
                        } else {
                            player.sendMessage(Constants.NOT_OWNER);
                        }
                    }
                } else {
                    player.sendMessage(Constants.NO_PERMS_MESSAGE);
                }
                event.setCancelled(true);
            }
        }
    }

    // remove the secretary from secretaries.yml if it dies
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        // only check if it was a villager
        if (event.getEntityType().equals(EntityType.VILLAGER)) {
            UUID deathID = event.getEntity().getUniqueId();
            Set<String> playerlist = secrets.getKeys(false);
            for (String d : playerlist) {
                Set<String> seclist = secrets.getConfigurationSection(d).getKeys(false);
                for (String u : seclist) {
                    if (u.equals(deathID.toString())) {
                        if (Bukkit.getPlayer(d) != null) {
                            Player player = Bukkit.getPlayer(d);
                            String name = secrets.getString(d + "." + u + ".name");
                            player.sendMessage("Your secretary '" + name + "' died!");
                            Constants.list(todos, d + "." + u, player, "todos");
                            Constants.list(reminds, d + "." + u, player, "reminders");
                            PlayerEntityMap.remove(player);
                        }
                        // clean up
                        secrets.set(d + "." + deathID, null);
                        todos.set(d + "." + deathID, null);
                        reminds.set(d + "." + deathID, null);
                        try {
                            secrets.save(secretariesfile);
                            todos.save(todofile);
                            reminds.save(remindersfile);
                        } catch (IOException e) {
                            System.out.println("Could not save the config files!");
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (PlayerEntityMap.containsKey(player)) {
            // remove them
            PlayerEntityMap.remove(player);
        }
    }

    public FileConfiguration loadConfig() {
        try {
            myconfigfile = new File(getDataFolder(), Constants.CONFIG_FILE_NAME);
            if (!myconfigfile.exists()) {
                // load the default values into file
                copy(getResource(Constants.CONFIG_FILE_NAME), myconfigfile);
            }
            secretariesfile = new File(getDataFolder(), Constants.SECRETARIES_FILE_NAME);
            if (!secretariesfile.exists()) {
                // load the default values into file
                copy(getResource(Constants.SECRETARIES_FILE_NAME), secretariesfile);
            }
            todofile = new File(getDataFolder(), Constants.TODO_FILE_NAME);
            if (!todofile.exists()) {
                // load the default values into file
                copy(getResource(Constants.TODO_FILE_NAME), todofile);
            }
            remindersfile = new File(getDataFolder(), Constants.REMINDERS_FILE_NAME);
            if (!remindersfile.exists()) {
                // load the default values into file
                copy(getResource(Constants.REMINDERS_FILE_NAME), remindersfile);
            }

            config = YamlConfiguration.loadConfiguration(myconfigfile);
            secrets = YamlConfiguration.loadConfiguration(secretariesfile);
            todos = YamlConfiguration.loadConfiguration(todofile);
            reminds = YamlConfiguration.loadConfiguration(remindersfile);

            // read the values we need and convert them to ENUM
            String t_str = config.getString("todo_material");
            String r_str = config.getString("remind_material");
            String s_str = config.getString("select_material");

            // convert strings to Material ENUMs
            t = Material.getMaterial(t_str);
            r = Material.getMaterial(r_str);
            s = Material.getMaterial(s_str);

        } catch (Exception e) {
            System.out.println(Constants.MY_PLUGIN_NAME + " failed to retrieve configuration from directory. Using defaults.");
        }
        return config;
    }

    private void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            System.err.println(Constants.MY_PLUGIN_NAME + " Could not save the config file.");
        }
    }

    public void saveCustomConfig() {
        if (config == null || myconfigfile == null) {
            return;
        }
        try {
            config.save(myconfigfile);
        } catch (IOException ex) {
            System.err.println(Constants.MY_PLUGIN_NAME + "Could not save config to " + myconfigfile);
        }
    }
}