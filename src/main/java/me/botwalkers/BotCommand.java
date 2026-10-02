package me.botwalkers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final BotManager manager;

    public BotCommand(JavaPlugin plugin, BotManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "Only players can spawn bots.", NamedTextColor.RED);
                    return true;
                }
                int max = plugin.getConfig().getInt("max-spawn-per-command", 50);
                int amount = 1;
                boolean armour = false;

                for (int i = 1; i < args.length; i++) {
                    String a = args[i].toLowerCase(Locale.ROOT);
                    if (a.equals("armour") || a.equals("armor") || a.equals("-armour") || a.equals("-armor")) {
                        armour = true;
                    } else {
                        try {
                            amount = Integer.parseInt(a);
                        } catch (NumberFormatException ex) {
                            msg(sender, "Invalid amount: " + args[i], NamedTextColor.RED);
                            return true;
                        }
                    }
                }
                amount = Math.max(1, Math.min(amount, max));

                int spawned = manager.spawn(player, amount, armour);
                msg(sender, "Spawned " + spawned + " bot(s)" + (armour ? " with random armour." : "."),
                        NamedTextColor.GREEN);
            }
            case "armour", "armor" -> {
                int n = manager.randomiseAllArmour();
                msg(sender, "Randomised armour on " + n + " bot(s).", NamedTextColor.GREEN);
            }
            case "clear", "remove" -> {
                int n = manager.removeAll();
                msg(sender, "Removed " + n + " bot(s).", NamedTextColor.YELLOW);
            }
            case "count", "list" -> msg(sender, manager.count() + " bot(s) active.", NamedTextColor.AQUA);
            default -> usage(sender, label);
        }
        return true;
    }

    private void usage(CommandSender s, String label) {
        msg(s, "/" + label + " spawn [amount] [armour]  - spawn random bots (add 'armour' for random armour)", NamedTextColor.GRAY);
        msg(s, "/" + label + " armour                   - re-roll armour on all existing bots", NamedTextColor.GRAY);
        msg(s, "/" + label + " clear                    - remove all bots", NamedTextColor.GRAY);
        msg(s, "/" + label + " count                    - how many bots are active", NamedTextColor.GRAY);
    }

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("spawn", "armour", "clear", "count")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
            for (String s : List.of("1", "5", "10", "armour")) {
                if (s.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
