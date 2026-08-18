package com.maya4pa.gatedungeon.instance;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.config.ConfigManager;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.Marker;
import com.maya4pa.gatedungeon.template.RegionMarker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DungeonInstance {
    public enum State { CREATING, PREPARING, READY, ACTIVE, BOSS, COMPLETED, CLEANUP, DESTROYED }

    private final String id;
    private final DungeonTemplate template;
    private final Gate gate;
    private final World world;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();
    private final Map<String, List<LivingEntity>> spawnedMobs = new ConcurrentHashMap<>();
    private State state = State.CREATING;
    private int currentWave = 0;
    private int totalWaves = 0;
    private boolean bossDefeated = false;
    private Location entrance;
    private Location exit;
    private Location bossSpawn;
    private BukkitTask waveCheckTask;
    private BukkitTask closeTask;
    private BukkitTask bossWatchTask;
    private BukkitTask prepTask;
    private BukkitTask settleTask;

    private final Map<UUID, Long> exitEntryTime = new ConcurrentHashMap<>();
    private BukkitTask exitMonitorTask;
    private ArmorStand countdownHologram;
    private BukkitTask countdownTask;
    private int lastRewardExp = 100;
    private boolean worldReady = false;
    private boolean preloadStarted = false;
    private int prepRemaining = -1;

    public DungeonInstance(String id, DungeonTemplate template, Gate gate, Player owner, World world) {
        this.id = id;
        this.template = template;
        this.gate = gate;
        this.world = world;
        this.players.add(owner.getUniqueId());
        gate.addPlayer(owner.getUniqueId());

        world.setDifficulty(Difficulty.HARD);
        findMarkers();
        totalWaves = template.getMaxWave();
        if (totalWaves == 0) totalWaves = 3;
        // Stay CREATING until chunks are loaded and the first player is teleported in.
    }

    private void findMarkers() {
        for (Marker m : template.getMarkers()) {
            Location loc = remap(m.getLocation());
            switch (m.getType()) {
                case "ENTRANCE" -> entrance = loc;
                case "EXIT" -> exit = loc;
                case "BOSS_SPAWN" -> bossSpawn = loc;
            }
        }
        if (entrance != null) {
            entrance.add(0, GateDungeonPlugin.getInstance().getConfigManager().getTeleportOffsetY(), 0);
        } else {
            entrance = new Location(world, world.getSpawnLocation().getX(),
                    world.getSpawnLocation().getY() + 1, world.getSpawnLocation().getZ());
        }
        if (exit != null) {
            exit.add(0, 1, 0);
        }
    }

    private Location remap(Location loc) {
        Location copy = loc.clone();
        copy.setWorld(world);
        return copy;
    }

    public void addPlayer(Player player) {
        players.add(player.getUniqueId());
        gate.addPlayer(player.getUniqueId());
    }

    /**
     * Queue or admit a player. Nobody is teleported until chunks are loaded.
     * Later joiners inherit the existing prep countdown and do not reset it.
     */
    public void beginEntry(Player player) {
        if (player == null || !player.isOnline()) return;
        addPlayer(player);
        if (!worldReady) {
            player.sendTitle("§6✦ Opening the Gate", "§eLoading dungeon — please wait...", 0, 80, 10);
            player.sendMessage("§ePreparing dungeon... you will be teleported when it is ready.");
            startPreload();
            return;
        }
        admitPlayer(player);
    }

    public void teleportPlayer(Player player) {
        beginEntry(player);
    }

    private void startPreload() {
        if (preloadStarted || worldReady) return;
        preloadStarted = true;
        GateDungeonPlugin plugin = GateDungeonPlugin.getInstance();
        plugin.getWorldManager().ensureSafePlatform(world, entrance);
        int radius = plugin.getConfigManager().getChunkLoadRadius();
        plugin.getWorldManager().preloadAround(world, entrance, radius, this::onChunksReady);
    }

    private void onChunksReady() {
        if (state == State.DESTROYED || state == State.CLEANUP) return;
        GateDungeonPlugin plugin = GateDungeonPlugin.getInstance();
        int settle = plugin.getConfigManager().getSettleTicks();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state == State.DESTROYED || state == State.CLEANUP) return;
                worldReady = true;
                for (UUID uuid : new HashSet<>(players)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        admitPlayer(p);
                    }
                }
            }
        }.runTaskLater(plugin, settle);
    }

    private void admitPlayer(Player player) {
        if (player == null || !player.isOnline() || entrance == null) return;
        Location dest = entrance.clone();
        dest.setWorld(world);
        player.sendTitle("§a✦ Dungeon ready", "§eTeleporting...", 0, 30, 10);
        try {
            player.teleportAsync(dest).thenAccept(ok ->
                    Bukkit.getScheduler().runTask(GateDungeonPlugin.getInstance(), () -> afterTeleport(player, dest)));
        } catch (Throwable ignored) {
            player.teleport(dest);
            afterTeleport(player, dest);
        }
    }

    private void afterTeleport(Player player, Location dest) {
        if (!player.isOnline() || state == State.DESTROYED || state == State.CLEANUP) return;
        if (!world.equals(player.getWorld())) {
            player.teleport(dest);
        }
        player.setFallDistance(0f);
        player.setGravity(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, false));
        startSettleWatch(player);

        if (state == State.CREATING || (state == State.PREPARING && prepTask == null)) {
            state = State.PREPARING;
            startPrepCountdown();
        } else if (state == State.PREPARING && prepRemaining >= 0) {
            player.sendTitle("§6✦ " + template.getName(), "§eWave 1 in " + prepRemaining + "s", 10, 40, 10);
            player.sendMessage("§eWave 1 starts in " + prepRemaining + "s. Prepare!");
        } else if (state == State.ACTIVE || state == State.BOSS) {
            player.sendTitle("§6✦ " + template.getName(), "§cAlready in progress", 10, 40, 10);
            player.sendMessage("§cThe dungeon has already started — join the fight!");
        } else {
            player.sendTitle("§6✦ " + template.getName(), "§7Get ready", 10, 40, 10);
        }
    }

    private void startSettleWatch(Player player) {
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || state == State.DESTROYED || state == State.CLEANUP) {
                    cancel();
                    return;
                }
                ticks += 5;
                if (!p.getWorld().equals(world)) {
                    cancel();
                    return;
                }
                Location loc = p.getLocation();
                if (loc.getY() < world.getMinHeight() + 8) {
                    rescueToEntrance(p);
                }
                boolean grounded = loc.getBlock().getRelative(0, -1, 0).getType().isSolid();
                if (grounded || ticks >= 60) {
                    p.setGravity(true);
                    p.setFallDistance(0f);
                    cancel();
                }
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 5L, 5L);
    }

    public void rescueToEntrance(Player player) {
        if (player == null || !player.isOnline() || entrance == null) return;
        Location dest = entrance.clone();
        dest.setWorld(world);
        player.setFallDistance(0f);
        player.teleport(dest);
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
    }

    private void startPrepCountdown() {
        if (prepTask != null) return;
        GateDungeonPlugin plugin = GateDungeonPlugin.getInstance();
        prepRemaining = plugin.getConfigManager().getPrepSeconds();
        if (prepRemaining <= 0) {
            start();
            return;
        }
        prepTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.PREPARING) {
                    cancel();
                    return;
                }
                if (prepRemaining <= 0) {
                    cancel();
                    prepTask = null;
                    start();
                    return;
                }
                String title = prepRemaining <= 5 ? "§c" + prepRemaining : "§e" + prepRemaining;
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) continue;
                    p.sendTitle(title, "§7Wave 1 starts soon — prepare!", 0, 25, 5);
                    p.sendActionBar(ChatColor.YELLOW + "Wave 1 in " + prepRemaining + "s");
                    if (prepRemaining <= 5) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                    }
                }
                prepRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void start() {
        if (state == State.ACTIVE || state == State.BOSS || state == State.COMPLETED) return;
        if (state == State.DESTROYED || state == State.CLEANUP) return;
        state = State.ACTIVE;
        sendSubtitle("§c⚔ Wave 1 — Fight!");
        spawnWave(1);
    }

    private void spawnWave(int wave) {
        if (state == State.DESTROYED || state == State.CLEANUP || state == State.COMPLETED) return;
        currentWave = wave;
        if (wave > totalWaves) {
            triggerBoss();
            return;
        }

        List<RegionMarker> regions = template.getRegionsByWave(wave);
        if (regions.isEmpty()) {
            GateDungeonPlugin.getInstance().getLogger().info("No regions for wave " + wave + " – skipping.");
            if (wave >= totalWaves) {
                triggerBoss();
            } else {
                spawnWave(wave + 1);
            }
            return;
        }

        String rank = gate.getRank();
        List<ConfigManager.MobEntry> mobEntries = GateDungeonPlugin.getInstance().getConfigManager().getMobsForWave(rank, wave);
        if (mobEntries.isEmpty()) {
            GateDungeonPlugin.getInstance().getLogger().warning("mobs.yml has no list at ranks." + rank + ".waves." + wave + ".mobs — using built-in defaults.");
            mobEntries = getDefaultMobs(wave);
        }

        List<LivingEntity> waveEntities = new ArrayList<>();
        int totalRegions = Math.max(1, regions.size());
        for (RegionMarker region : regions) {
            for (ConfigManager.MobEntry entry : mobEntries) {
                int perRegion = Math.max(1, (int) Math.ceil(entry.amount / (double) totalRegions));
                for (int i = 0; i < perRegion; i++) {
                    Location spawnLoc = safeSpawn(region.getRandomLocation(world), i);
                    if (spawnLoc == null) continue;
                    LivingEntity mob = spawnMob(spawnLoc, entry.type, entry.name, entry.health, entry.damage);
                    if (mob != null) {
                        waveEntities.add(mob);
                        spawnedMobs.computeIfAbsent(region.getId(), k -> new ArrayList<>()).add(mob);
                    }
                }
            }
        }

        sendSubtitle("§e⚔ Wave " + wave + " started!");

        if (waveCheckTask != null) {
            waveCheckTask.cancel();
        }
        waveCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.ACTIVE) {
                    cancel();
                    return;
                }
                boolean allDead = true;
                for (LivingEntity mob : waveEntities) {
                    if (mob != null && !mob.isDead() && mob.isValid()) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) {
                    cancel();
                    sendSubtitle("§a✓ Wave " + wave + " cleared!");
                    spawnWave(wave + 1);
                }
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 20L, 20L);
    }

    private Location safeSpawn(Location candidate, int index) {
        Location base = candidate;
        if (base == null || base.getWorld() == null) {
            base = entrance != null ? entrance.clone() : world.getSpawnLocation();
        }
        base.setWorld(world);
        if (base.getBlock().getRelative(0, -1, 0).getType().isAir()
                || base.getY() < world.getMinHeight() + 2) {
            Location fallback = entrance != null ? entrance.clone() : world.getSpawnLocation().clone();
            fallback.setWorld(world);
            fallback.add((index % 5) - 2, 0, (index / 5) % 3);
            base = fallback;
        }
        return base;
    }

    private List<ConfigManager.MobEntry> getDefaultMobs(int wave) {
        List<ConfigManager.MobEntry> list = new ArrayList<>();
        switch (wave) {
            case 1 -> {
                list.add(new ConfigManager.MobEntry("ZOMBIE", "§aGoblin", 3, 20, 3));
                list.add(new ConfigManager.MobEntry("SKELETON", "§eGoblin Archer", 2, 16, 4));
            }
            case 2 -> list.add(new ConfigManager.MobEntry("ZOMBIE", "§cElite Goblin", 5, 30, 5));
            case 3 -> list.add(new ConfigManager.MobEntry("ZOMBIE", "§5Goblin Berserker", 8, 40, 7));
            default -> list.add(new ConfigManager.MobEntry("ZOMBIE", "§cGoblin", 10 + wave * 2, 20, 4));
        }
        return list;
    }

    private LivingEntity spawnMob(Location location, String typeName, String name, double health, double damage) {
        try {
            EntityType type = EntityType.valueOf(typeName.toUpperCase());
            if (!type.isAlive() || !type.isSpawnable()) return null;
            LivingEntity entity = (LivingEntity) world.spawnEntity(location, type);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            if (name != null) {
                entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
                entity.setCustomNameVisible(true);
            }
            applyStats(entity, health, damage);
            return entity;
        } catch (Exception e) {
            GateDungeonPlugin.getInstance().getLogger().warning("Failed to spawn mob type: " + typeName);
            return null;
        }
    }

    private AttributeInstance firstAttribute(LivingEntity entity, String... names) {
        for (String name : names) {
            try {
                Attribute attr = Attribute.valueOf(name);
                AttributeInstance inst = entity.getAttribute(attr);
                if (inst != null) return inst;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private void applyStats(LivingEntity entity, double health, double damage) {
        if (health > 0) {
            AttributeInstance max = firstAttribute(entity, "GENERIC_MAX_HEALTH", "MAX_HEALTH");
            if (max != null) {
                max.setBaseValue(health);
                entity.setHealth(Math.min(health, max.getValue()));
            }
        }
        if (damage > 0) {
            AttributeInstance atk = firstAttribute(entity, "GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE");
            if (atk != null) {
                atk.setBaseValue(damage);
            }
        }
    }

    private void triggerBoss() {
        if (state == State.BOSS || state == State.COMPLETED) return;
        state = State.BOSS;
        sendSubtitle("§c⚠ The boss is spawning!");

        Location spawnAt = bossSpawn != null ? bossSpawn : entrance;
        if (spawnAt == null) {
            complete();
            return;
        }

        ConfigManager.BossEntry bossEntry = GateDungeonPlugin.getInstance().getConfigManager().getBossForRank(gate.getRank());
        if (bossEntry == null) {
            bossEntry = new ConfigManager.BossEntry("ZOMBIE", "§cGoblin King", 200, 10, 100);
        }
        lastRewardExp = bossEntry.expReward;
        LivingEntity boss = spawnBoss(spawnAt, bossEntry);
        if (boss == null) {
            complete();
            return;
        }
        bossWatchTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    cancel();
                    bossDefeated = true;
                    complete();
                }
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 20L, 20L);
    }

    private LivingEntity spawnBoss(Location location, ConfigManager.BossEntry entry) {
        try {
            EntityType type = EntityType.valueOf(entry.type.toUpperCase());
            LivingEntity boss = (LivingEntity) world.spawnEntity(location, type);
            boss.setCustomName(ChatColor.translateAlternateColorCodes('&', entry.name));
            boss.setCustomNameVisible(true);
            applyStats(boss, entry.health, entry.damage);
            boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            return boss;
        } catch (Exception e) {
            GateDungeonPlugin.getInstance().getLogger().warning("Failed to spawn boss type: " + entry.type);
            return null;
        }
    }

    private void complete() {
        if (state == State.COMPLETED || state == State.DESTROYED || state == State.CLEANUP) return;
        state = State.COMPLETED;
        sendSubtitle("§6✦ Dungeon Complete! ✦");

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.giveExp(Math.max(0, lastRewardExp));
                p.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));
                p.sendMessage("§aYou earned " + lastRewardExp + " XP and 1 Diamond!");
                var data = GateDungeonPlugin.getInstance().getDatabaseManager().loadPlayer(uuid);
                data.setName(p.getName());
                data.incrementCompleted();
                data.updateHighestRank(gate.getRank());
                GateDungeonPlugin.getInstance().getDatabaseManager().savePlayer(data);
                GateDungeonPlugin.getInstance().getDatabaseManager().addCompletion(uuid, template.getId());
            }
            completed.add(uuid);
        }

        gate.setClosing(true);
        startExitMonitoring();
        startCountdownHologram();

        int delay = Math.max(5, GateDungeonPlugin.getInstance().getConfigManager().getBossDefeatRemovalDelay());
        closeTask = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (state == State.DESTROYED) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    forceClose();
                    return;
                }
                remaining--;
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 0L, 20L);
    }

    private void forceClose() {
        List<UUID> copy = new ArrayList<>(players);
        for (UUID uuid : copy) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                teleportOut(p);
            } else {
                players.remove(uuid);
                gate.removePlayer(uuid);
                GateDungeonPlugin.getInstance().getInstanceManager().removePlayerFromInstance(uuid);
            }
        }
        GateDungeonPlugin.getInstance().getGateManager().removeGate(gate.getId());
        cleanup();
    }

    private void startCountdownHologram() {
        Location hologramLoc = (bossSpawn != null ? bossSpawn : entrance).clone().add(0, 2, 0);
        countdownHologram = (ArmorStand) world.spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        countdownHologram.setVisible(false);
        countdownHologram.setMarker(true);
        countdownHologram.setSmall(true);
        countdownHologram.setGravity(false);
        countdownHologram.setBasePlate(false);
        countdownHologram.setInvulnerable(true);
        countdownHologram.setCustomNameVisible(true);

        countdownTask = new BukkitRunnable() {
            int remaining = GateDungeonPlugin.getInstance().getConfigManager().getBossDefeatRemovalDelay();

            @Override
            public void run() {
                if (remaining <= 0 || countdownHologram == null || countdownHologram.isDead()) {
                    cancel();
                    if (countdownHologram != null && !countdownHologram.isDead()) {
                        countdownHologram.remove();
                    }
                    return;
                }
                countdownHologram.setCustomName("§cGate closes in " + remaining + "s");
                remaining--;
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 0L, 20L);
    }

    private void startExitMonitoring() {
        if (exit == null) return;

        double exitRadius = GateDungeonPlugin.getInstance().getConfigManager().getExitRadius();
        if (exitRadius <= 0) exitRadius = 5;
        int teleportDelay = GateDungeonPlugin.getInstance().getConfigManager().getExitTeleportDelay();
        final double radius = exitRadius;

        exitMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.COMPLETED) {
                    cancel();
                    return;
                }
                if (exit.getWorld() == null) return;

                for (UUID uuid : new HashSet<>(players)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;
                    if (!player.getWorld().equals(world)) continue;

                    double distance = exit.distance(player.getLocation());
                    if (distance <= radius) {
                        if (!exitEntryTime.containsKey(uuid)) {
                            exitEntryTime.put(uuid, System.currentTimeMillis());
                            sendSubtitle(player, "§aTeleporting out in " + teleportDelay + " seconds...");
                            showExitParticles(player, radius);
                        } else {
                            long elapsed = System.currentTimeMillis() - exitEntryTime.get(uuid);
                            if (elapsed >= teleportDelay * 1000L) {
                                exitEntryTime.remove(uuid);
                                teleportOut(player);
                            } else {
                                long remaining = (teleportDelay * 1000L - elapsed) / 1000 + 1;
                                player.sendActionBar(ChatColor.GREEN + "Teleporting in " + remaining + "...");
                                showExitParticles(player, radius);
                            }
                        }
                    } else if (exitEntryTime.remove(uuid) != null) {
                        sendSubtitle(player, "§cExit cancelled – stay near the emerald block!");
                    }
                }
            }
        }.runTaskTimer(GateDungeonPlugin.getInstance(), 20L, 20L);
    }

    private void showExitParticles(Player player, double radius) {
        if (exit == null || exit.getWorld() == null) return;
        if (!GateDungeonPlugin.getInstance().getConfigManager().isExitParticle()) return;

        Particle.DustOptions color = new Particle.DustOptions(Color.LIME, 1);
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI / 16) * i;
            Location particleLoc = exit.clone().add(radius * Math.cos(angle), 0.5, radius * Math.sin(angle));
            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, color);
        }
        if (GateDungeonPlugin.getInstance().getConfigManager().isExitSound()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        }
    }

    public void teleportOut(Player player) {
        player.setGravity(true);
        player.setFallDistance(0f);
        Location safe = getSafeExitLocation(gate.getLocation());
        if (safe != null && safe.getWorld() != null) {
            player.teleport(safe);
        } else if (!Bukkit.getWorlds().isEmpty()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        player.sendTitle("", "§aYou have returned safely!", 10, 40, 10);
        players.remove(player.getUniqueId());
        gate.removePlayer(player.getUniqueId());
        GateDungeonPlugin.getInstance().getInstanceManager().removePlayerFromInstance(player.getUniqueId());
        if (players.isEmpty()) {
            cleanup();
        }
    }

    public void handleDeath(Player player) {
        var data = GateDungeonPlugin.getInstance().getDatabaseManager().loadPlayer(player.getUniqueId());
        data.setName(player.getName());
        data.incrementFailed();
        GateDungeonPlugin.getInstance().getDatabaseManager().savePlayer(data);
    }

    private Location getSafeExitLocation(Location gateLocation) {
        if (gateLocation == null || gateLocation.getWorld() == null) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        World w = gateLocation.getWorld();
        for (int i = 0; i < 10; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = 3.0 + ThreadLocalRandom.current().nextDouble() * 2.0;
            Location candidate = gateLocation.clone().add(distance * Math.cos(angle), 0, distance * Math.sin(angle));
            candidate.setY(w.getHighestBlockYAt(candidate) + 1);
            if (candidate.getBlock().isPassable()) {
                return candidate;
            }
        }
        Location fallback = gateLocation.clone().add(3, 0, 0);
        fallback.setY(w.getHighestBlockYAt(fallback) + 1);
        return fallback;
    }

    private void sendSubtitle(String text) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle("", text, 0, 40, 10);
            }
        }
    }

    private void sendSubtitle(Player player, String text) {
        player.sendTitle("", text, 0, 40, 10);
    }

    public void cleanup() {
        if (state == State.DESTROYED) return;
        state = State.CLEANUP;
        cancelTask(waveCheckTask);
        cancelTask(exitMonitorTask);
        cancelTask(countdownTask);
        cancelTask(closeTask);
        cancelTask(bossWatchTask);
        cancelTask(prepTask);
        cancelTask(settleTask);
        waveCheckTask = exitMonitorTask = countdownTask = closeTask = bossWatchTask = prepTask = settleTask = null;

        for (List<LivingEntity> list : spawnedMobs.values()) {
            for (LivingEntity e : list) {
                if (e != null && !e.isDead() && e.isValid()) e.remove();
            }
        }
        spawnedMobs.clear();
        if (countdownHologram != null && !countdownHologram.isDead()) {
            countdownHologram.remove();
        }
        exitEntryTime.clear();

        for (UUID uuid : new HashSet<>(players)) {
            gate.removePlayer(uuid);
            GateDungeonPlugin.getInstance().getInstanceManager().removePlayerFromInstance(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getWorld().equals(world)) {
                p.setGravity(true);
                Location safe = getSafeExitLocation(gate.getLocation());
                if (safe != null) p.teleport(safe);
            }
        }
        players.clear();

        state = State.DESTROYED;
        GateDungeonPlugin.getInstance().getInstanceManager().unregisterInstance(id);
        GateDungeonPlugin.getInstance().getWorldManager().unloadInstanceWorld(world);
        // A dungeon that finished (gate closing) should never leave the gate
        // behind forever. If everyone left before the close-countdown expired,
        // the closeTask was cancelled here, so remove the gate ourselves.
        if (gate.isClosing()) {
            GateDungeonPlugin.getInstance().getGateManager().removeGate(gate.getId());
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
    }

    public String getId() { return id; }
    public DungeonTemplate getTemplate() { return template; }
    public Gate getGate() { return gate; }
    public State getState() { return state; }
    public Set<UUID> getPlayers() { return new HashSet<>(players); }
    public boolean isBossDefeated() { return bossDefeated; }
    public World getWorld() { return world; }
}
