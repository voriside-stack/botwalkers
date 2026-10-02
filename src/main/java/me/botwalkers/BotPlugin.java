package me.botwalkers;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BotPlugin extends JavaPlugin {

    private BotManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new BotManager(this);
        manager.start();

        PluginCommand cmd = getCommand("bots");
        if (cmd != null) {
            BotCommand handler = new BotCommand(this, manager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.removeAll();
    }
}
