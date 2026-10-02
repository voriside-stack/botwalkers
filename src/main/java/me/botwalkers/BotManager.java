package me.botwalkers;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class BotManager {

    private static final class Bot {
        final Mannequin entity;
        double yaw;
        int walkTicks;
        int idleTicks;

        Bot(Mannequin entity) {
            this.entity = entity;
        }
    }

    private static final String[] ADJECTIVES = {
            "Swift", "Lazy", "Happy", "Grumpy", "Sneaky", "Brave", "Silent", "Mighty", "Tiny", "Wild",
            "Cosmic", "Frosty", "Blazing", "Clever", "Shadow", "Golden", "Crimson", "Lucky", "Rusty", "Epic"
    };
    private static final String[] NOUNS = {
            "Fox", "Creeper", "Miner", "Knight", "Wolf", "Panda", "Ninja", "Wizard", "Builder", "Ghost",
            "Dragon", "Ranger", "Pickaxe", "Zombie", "Pirate", "Falcon", "Golem", "Slime", "Bandit", "Hero"
    };

    private static final Material[] HELMETS = {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
            Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET};
    private static final Material[] CHESTPLATES = {
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE};
    private static final Material[] LEGGINGS = {
            Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
            Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS};
    private static final Material[] BOOTS = {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
            Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS};

    /** Blocks bots will refuse to step on / into, even though they have no collision. */
    private static final java.util.Set<Material> AVOID = java.util.Set.of(
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.COBWEB, Material.WITHER_ROSE, Material.CACTUS);

    private final JavaPlugin plugin;
    private final List<Bot> bots = new ArrayList<>();
    private final Random rng = new Random();

    public BotManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public int count() {
        return bots.size();
    }

    // ------------------------------------------------------------------ spawning

    /** Spawns {@code amount} bots around the player. Returns how many were actually spawned. */
    public int spawn(Player player, int amount, boolean randomArmour) {
        List<String> skins = plugin.getConfig().getStringList("skins");
        if (skins.isEmpty()) skins = List.of("Notch");

        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            Location loc = findSpawnPoint(player.getLocation());
            if (loc == null) continue;

            String skin = skins.get(rng.nextInt(skins.size()));
            String name = randomName();

            Mannequin m = loc.getWorld().spawn(loc, Mannequin.class, e -> {
                e.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
                e.customName(Component.text(name));
                e.setCustomNameVisible(true);
                e.setDescription(null);   // hide the default "Mannequin" subtitle
                e.setGravity(false);      // we handle ground-snapping ourselves
                e.setInvulnerable(true);
                e.setPersistent(false);   // never saved to disk
                if (randomArmour) applyRandomArmour(e);
            });

            Bot bot = new Bot(m);
            bot.yaw = rng.nextDouble() * 360;
            bots.add(bot);
            spawned++;
        }
        return spawned;
    }

    private String randomName() {
        String n = ADJECTIVES[rng.nextInt(ADJECTIVES.length)] + NOUNS[rng.nextInt(NOUNS.length)];
        return rng.nextBoolean() ? n + (1 + rng.nextInt(99)) : n;
    }

    // ------------------------------------------------------------------ armour

    public void applyRandomArmour(Mannequin e) {
        EntityEquipment eq = e.getEquipment();
        eq.setHelmet(randomPiece(HELMETS));
        eq.setChestplate(randomPiece(CHESTPLATES));
        eq.setLeggings(randomPiece(LEGGINGS));
        eq.setBoots(randomPiece(BOOTS));
    }

    /** Re-rolls armour on every existing bot. */
    public int randomiseAllArmour() {
        int n = 0;
        for (Bot b : bots) {
            if (b.entity.isValid()) {
                applyRandomArmour(b.entity);
                n++;
            }
        }
        return n;
    }

    /** 20% chance of an empty slot, otherwise a random material tier. */
    private ItemStack randomPiece(Material[] tiers) {
        if (rng.nextInt(5) == 0) return null;
        return new ItemStack(tiers[rng.nextInt(tiers.length)]);
    }

    // ------------------------------------------------------------------ removal

    public int removeAll() {
        int n = bots.size();
        for (Bot b : bots) {
            if (b.entity.isValid()) b.entity.remove();
        }
        bots.clear();
        return n;
    }

    // ------------------------------------------------------------------ walking AI

    private void tickAll() {
        double speed = plugin.getConfig().getDouble("speed", 0.215);
        Iterator<Bot> it = bots.iterator();
        while (it.hasNext()) {
            Bot b = it.next();
            if (!b.entity.isValid()) {
                it.remove();
                continue;
            }
            if (!b.entity.getLocation().isChunkLoaded()) continue;
            tick(b, speed);
        }
    }

    private void tick(Bot b, double speed) {
        if (b.idleTicks > 0) {
            b.idleTicks--;
            return;
        }

        if (b.walkTicks <= 0) {
            b.yaw = rng.nextDouble() * 360;
            b.walkTicks = 20 + rng.nextInt(80);
            if (rng.nextInt(3) == 0) {          // sometimes stand still for a bit
                b.idleTicks = 20 + rng.nextInt(60);
                return;
            }
        }

        double rad = Math.toRadians(b.yaw);
        double dx = -Math.sin(rad) * speed;
        double dz = Math.cos(rad) * speed;

        Location next = tryMove(b.entity, dx, dz);
        if (next == null) {
            // Blocked: stop, then pick a brand new direction next tick.
            b.walkTicks = 0;
            b.idleTicks = 2 + rng.nextInt(10);
            return;
        }

        next.setYaw((float) b.yaw);
        next.setPitch(0f);
        b.entity.teleport(next);
        b.walkTicks--;
    }

    /**
     * Works out where the bot would end up after moving (dx, dz), or returns null if
     * the move is unsafe. Rules:
     *  - never move into solid blocks (bounding-box collision test)
     *  - step up at most 1 block (and only if there's headroom)
     *  - step down at most 1 block (so no walking off cliffs)
     *  - never enter liquids or a few harmful blocks
     */
    private Location tryMove(Mannequin e, double dx, double dz) {
        World w = e.getWorld();
        Location cur = e.getLocation();

        // Slightly shrunk box so we don't snag on neighbouring walls.
        BoundingBox box = e.getBoundingBox().clone().expand(-0.02).shift(dx, 0, dz);
        double dy = 0;

        if (w.hasCollisionsIn(box)) {
            // Try stepping up one block.
            BoundingBox up = box.clone().shift(0, 1.0, 0);
            // Need headroom at the current spot too, otherwise we'd clip a ceiling.
            BoundingBox headroom = e.getBoundingBox().clone().expand(-0.02).shift(0, 1.0, 0);
            if (w.hasCollisionsIn(up) || w.hasCollisionsIn(headroom)) return null;
            box = up;
            dy = 1.0;
        }

        // Settle downwards onto the ground (max 1 block drop = no cliffs).
        int steps = 0;
        while (!w.hasCollisionsIn(box.clone().shift(0, -0.05, 0)) && steps < 20) {
            box.shift(0, -0.05, 0);
            dy -= 0.05;
            steps++;
        }
        if (steps >= 20) return null; // too big a drop

        Location next = cur.clone().add(dx, dy, dz);

        // Avoid liquids and harmful blocks at feet / underfoot.
        if (isBad(next.getBlock().getType()) || next.getBlock().isLiquid()) return null;
        Location below = next.clone().subtract(0, 0.1, 0);
        if (isBad(below.getBlock().getType()) || below.getBlock().isLiquid()) return null;

        return next;
    }

    private boolean isBad(Material m) {
        return AVOID.contains(m) || m == Material.LAVA || m == Material.WATER;
    }

    // ------------------------------------------------------------------ spawn point search

    /** Finds a free, grounded spot a few blocks away from {@code origin}. */
    private Location findSpawnPoint(Location origin) {
        World w = origin.getWorld();
        for (int attempt = 0; attempt < 40; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double dist = 3 + rng.nextDouble() * 7;
            double x = origin.getX() + Math.cos(ang) * dist;
            double z = origin.getZ() + Math.sin(ang) * dist;

            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                double y = Math.floor(origin.getY()) + dy;
                BoundingBox body = new BoundingBox(x - 0.28, y, z - 0.28, x + 0.28, y + 1.8, z + 0.28);
                if (w.hasCollisionsIn(body)) continue;
                if (!w.hasCollisionsIn(body.clone().shift(0, -0.1, 0))) continue; // needs ground

                Location loc = new Location(w, x, y, z, (float) (rng.nextDouble() * 360), 0f);
                if (loc.getBlock().isLiquid() || isBad(loc.getBlock().getType())) continue;
                Location below = loc.clone().subtract(0, 0.1, 0);
                if (below.getBlock().isLiquid() || isBad(below.getBlock().getType())) continue;
                return loc;
            }
        }
        return null;
    }
}
