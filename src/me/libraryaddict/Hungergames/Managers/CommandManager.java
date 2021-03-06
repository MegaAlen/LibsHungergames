package me.libraryaddict.Hungergames.Managers;

import me.libraryaddict.Hungergames.Types.AbilityListener;
import me.libraryaddict.Hungergames.Types.HungergamesApi;
import me.libraryaddict.Hungergames.Utilities.ClassGetter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_5_R3.CraftServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Austin Date: 11/7/12 Time: 12:04 PM
 */
public class CommandManager {
    private TranslationManager cm = HungergamesApi.getTranslationManager();
    private YamlConfiguration config;
    private File configFile;
    private boolean newFile = false;

    public CommandManager() {
        loadCommands(HungergamesApi.getHungergames(), "me.libraryaddict.Hungergames.Commands");
    }

    private void addCreatorAliases(List<String> list, String commandName) {
        for (String string : new String[] { "author", "maker", "coder", "download", "hungergames", "creator" })
            if (!commandName.equalsIgnoreCase(string) && !list.contains(string))
                list.add(string);
    }

    public ConfigurationSection getConfigSection(String commandName) {
        ConfigurationSection section = config.getConfigurationSection(commandName);
        if (section == null) {
            section = config.createSection(commandName);
        }
        return section;
    }

    public void load(File file) {
        configFile = file;
        try {
            config = new YamlConfiguration();
            if (!configFile.exists()) {
                newFile = true;
                save();
            } else
                newFile = false;
            config.load(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean loadCommand(JavaPlugin owningPlugin, CommandExecutor exc, boolean save) {
        File newFile = new File(owningPlugin.getDataFolder(), "commands.yml");
        if (configFile == null || !configFile.equals(newFile)) {
            load(newFile);
        }
        boolean modified = false;
        if (exc instanceof AbilityListener) {
            try {
                Method field = exc.getClass().getMethod("getCommands");
                if (field != null) {
                    String[] commands = (String[]) field.invoke(exc);
                    for (String command : commands) {
                        boolean modify = startRegisteringCommand(exc, command);
                        if (!modified)
                            modified = modify;
                    }
                }
            } catch (Exception ex) {
            }
            try {
                Method field = exc.getClass().getMethod("getCommand");
                String command = (String) field.invoke(exc);
                if (command != null) {
                    boolean modify = startRegisteringCommand(exc, command);
                    if (!modified)
                        modified = modify;
                }
            } catch (Exception ex) {
            }
        } else {
            modified = startRegisteringCommand(exc, exc.getClass().getSimpleName());
        }
        // System.out.print(String.format(cm.getLoggerFoundCommandInPackage(),
        // commandName));
        if (save && modified)
            save();
        return modified;
    }

    private void loadCommands(JavaPlugin plugin, String packageName) {
        boolean saveConfig = false;
        System.out.print(String.format(cm.getLoggerLoadCommandsInPackage(), plugin.getName(), packageName));
        for (Class commandClass : ClassGetter.getClassesForPackage(plugin, packageName)) {
            if (CommandExecutor.class.isAssignableFrom(commandClass)) {
                try {
                    CommandExecutor commandListener = (CommandExecutor) commandClass.newInstance();
                    final boolean modified = loadCommand(plugin, commandListener, false);
                    if (modified)
                        saveConfig = true;
                } catch (Exception e) {
                    System.out.print(String.format(cm.getLoggerErrorWhileLoadingCommands(), commandClass.getSimpleName(),
                            e.getMessage()));
                }
            }
        }
        if (saveConfig)
            save();
    }

    public boolean loadConfig(ConfigurationSection section, CommandExecutor exc, String commandName) {
        try {
            boolean modified = false;
            if (!section.contains("CommandName")) {
                modified = true;
                section.set("CommandName", commandName);
            }
            if (!section.contains("EnableCommand")) {
                modified = true;
                section.set("EnableCommand", true);
            }
            for (Field field : exc.getClass().getDeclaredFields()) {
                if ((field.getName().equals("aliases") || field.getName().equals("description"))
                        && !Modifier.isTransient(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                    try {
                        Object value = section.get(field.getName());
                        if (value == null) {
                            value = field.get(exc);
                            if (value instanceof String[]) {
                                String[] strings = (String[]) value;
                                String[] newStrings = new String[strings.length];
                                for (int i = 0; i < strings.length; i++) {
                                    newStrings[i] = strings[i].replace("\n", "\\n").replace("§", "&").toLowerCase();
                                }
                                section.set(field.getName(), newStrings);
                            } else {
                                if (value instanceof String)
                                    value = ((String) value).replace("\n", "\\n").replace("§", "&");
                                section.set(field.getName(), value);
                            }
                            modified = true;
                            if (!newFile)
                                System.out.print(String.format(cm.getLoggerCommandsMissingValue(), field.getName(), commandName));
                        } else if (field.getType().isArray() && value.getClass() == ArrayList.class) {
                            List<Object> array = (List<Object>) value;
                            value = array.toArray(new String[array.size()]);
                        }
                        if (value instanceof String) {
                            value = ChatColor.translateAlternateColorCodes('&', (String) value).replace("\\n", "\n");
                        }
                        if (value instanceof String[]) {
                            String[] strings = (String[]) value;
                            for (int i = 0; i < strings.length; i++)
                                strings[i] = ChatColor.translateAlternateColorCodes('&', strings[i]).replace("\\n", "\n");
                            value = strings;
                        }
                        if (field.getType().getSimpleName().equals("float") && value.getClass() == Double.class) {
                            field.set(exc, ((float) (double) (Double) value));
                        } else
                            field.set(exc, value);
                        if (field.getName().equals("commandCreator")) {
                            /**
                             * Touch this and you better leave this entire plugin alone because I didn't give you permission to
                             * modify this. By changing the creatorMessage to something which doesn't refer players to the plugin
                             * itself. You are going against my wishes.
                             */
                            String message = String.format(((String) value), "libraryaddict", "http://ow.ly/kWBpO").toLowerCase();
                            if (!message.contains("libraryaddict") && !message.contains("http://ow.ly/kwbpo")
                                    && !message.contains("spigotmc.org/resources/libs-hungergames.55")) {
                                Bukkit.getScheduler().scheduleSyncRepeatingTask(HungergamesApi.getHungergames(), new Runnable() {
                                    public void run() {
                                        Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Hungergames] " + ChatColor.AQUA
                                                + "This plugin was created by libraryaddict! Download it at http://ow.ly/kWBpO");
                                    }
                                }, 20 * 60 * 10, 20 * 60 * 10);
                            }
                        }
                    } catch (Exception e) {
                        System.out.print(String.format(cm.getLoggerErrorWhileLoadingCommands(), e.getMessage()));
                    }
            }
            return modified;
        } catch (Exception e) {
            System.out.print(String.format(cm.getLoggerErrorWhileLoadingCommands(), e.getMessage()));
        }
        return false;
    }

    private void registerCommand(String name, CommandExecutor exc) throws Exception {
        PluginCommand command = Bukkit.getServer().getPluginCommand(name.toLowerCase());
        if (command == null) {
            Constructor<?> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            command = (PluginCommand) constructor.newInstance(name, HungergamesApi.getHungergames());
        }
        command.setExecutor(exc);
        try {
            Field field = exc.getClass().getDeclaredField("aliases");
            if (field.get(exc) instanceof String[]) {
                List<String> list = Arrays.asList((String[]) field.get(exc));
                if (exc.getClass().getSimpleName().equals("Creator"))
                    addCreatorAliases(list, name);
                command.setAliases(list);
            }
        } catch (Exception ex) {
            if (exc.getClass().getSimpleName().equals("Creator")) {
                List<String> list = new ArrayList<String>();
                addCreatorAliases(list, name);
                command.setAliases(list);
            }
        }
        if (command.getAliases() != null) {
            for (String alias : command.getAliases())
                unregisterCommand(alias);
        }
        try {
            Field field = exc.getClass().getDeclaredField("description");
            if (field != null && field.get(exc) instanceof String)
                command.setDescription(ChatColor.translateAlternateColorCodes('&', (String) field.get(exc)));
        } catch (Exception ex) {
        }
        Field field = CraftServer.class.getDeclaredField("commandMap");
        field.setAccessible(true);
        SimpleCommandMap map = ((CraftServer) Bukkit.getServer()).getCommandMap();
        map.register(name, command);
    }

    public void save() {
        try {
            if (!configFile.exists()) {
                Bukkit.getLogger().info(cm.getLoggerCreatingCommandsConfig());
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                newFile = true;
            } else
                newFile = false;
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean startRegisteringCommand(CommandExecutor exc, String commandName) {
        ConfigurationSection section = getConfigSection(commandName);
        boolean modified = loadConfig(section, exc, commandName);
        if (section.getBoolean("EnableCommand") || exc.getClass().getSimpleName().equals("Creator")) {
            try {
                registerCommand(section.getString("CommandName"), exc);
            } catch (Exception ex) {
                System.out.print(String.format(cm.getLoggerErrorWhileLoadingCommands(), exc.getClass().getSimpleName(),
                        ex.getMessage()));
            }
        }
        return modified;
    }

    private void unregisterCommand(String name) {
        try {
            Field known = SimpleCommandMap.class.getDeclaredField("knownCommands");
            Field alias = SimpleCommandMap.class.getDeclaredField("aliases");
            known.setAccessible(true);
            alias.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) known.get(((CraftServer) Bukkit.getServer())
                    .getCommandMap());
            Set<String> aliases = (Set<String>) alias.get(((CraftServer) Bukkit.getServer()).getCommandMap());
            knownCommands.remove(name.toLowerCase());
            aliases.remove(name.toLowerCase());
        } catch (Exception ex) {

        }
    }
}
