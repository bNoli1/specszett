package me.specialarmor;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SpecialArmorPlugin extends JavaPlugin implements Listener {

    private final NamespacedKey setTagKey = new NamespacedKey(this, "special_set_name");

    @Override
    public void onEnable() {
        // Config inicializálása
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        
        // /setspecial <név> <offset> parancs
        if (getCommand("setspecial") != null) {
            getCommand("setspecial").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("specialarmor.admin")) {
                    player.sendMessage(Component.text("Nincs jogosultságod!", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(Component.text("Használat: /setspecial <szettnév> <plusz_védelem>", NamedTextColor.YELLOW));
                    return true;
                }

                String setName = args[0].toLowerCase();
                int offset;
                try {
                    offset = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Az erősségnek számnak kell lennie!", NamedTextColor.RED));
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    player.sendMessage(Component.text("Fogj egy tárgyat a kezedben!", NamedTextColor.RED));
                    return true;
                }

                // Mentés a configba
                getConfig().set("sets." + setName + ".offset", offset);
                saveConfig();

                // Tárgy megjelölése
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(setTagKey, PersistentDataType.STRING, setName);
                item.setItemMeta(meta);

                player.sendMessage(Component.text("Siker! A(z) ", NamedTextColor.GREEN)
                        .append(Component.text(setName, NamedTextColor.GOLD))
                        .append(Component.text(" szett hozzáadva +" + offset + " értékkel.", NamedTextColor.GREEN)));
                
                updateArmorStats(player);
                return true;
            });
        }

        // /specialreload parancs
        if (getCommand("specialreload") != null) {
            getCommand("specialreload").setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("specialarmor.admin")) {
                    sender.sendMessage(Component.text("Nincs jogosultságod a config újratöltéséhez!", NamedTextColor.RED));
                    return true;
                }

                reloadConfig();
                sender.sendMessage(Component.text("SpecialArmor config sikeresen újratöltve!", NamedTextColor.AQUA));
                
                // Ha játékos futtatta, azonnal frissítjük a nála lévő cuccokat is
                if (sender instanceof Player player) {
                    updateArmorStats(player);
                }
                return true;
            });
        }
    }

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            getServer().getScheduler().runTask(this, () -> updateArmorStats(player));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateArmorStats(event.getPlayer());
    }

    private void updateArmorStats(Player player) {
        int bestHelmet = findMaxProt(player, "_HELMET");
        int bestChest = findMaxProt(player, "_CHESTPLATE");
        int bestLegs = findMaxProt(player, "_LEGGINGS");
        int bestBoots = findMaxProt(player, "_BOOTS");

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            String setName = meta.getPersistentDataContainer().get(setTagKey, PersistentDataType.STRING);
            
            if (setName != null && getConfig().contains("sets." + setName)) {
                int offset = getConfig().getInt("sets." + setName + ".offset");
                String typeName = item.getType().name();

                int targetLevel;
                if (typeName.endsWith("_HELMET")) targetLevel = bestHelmet + offset;
                else if (typeName.endsWith("_CHESTPLATE")) targetLevel = bestChest + offset;
                else if (typeName.endsWith("_LEGGINGS")) targetLevel = bestLegs + offset;
                else if (typeName.endsWith("_BOOTS")) targetLevel = bestBoots + offset;
                else continue;

                if (meta.getEnchantmentLevel(Enchantment.PROTECTION) != targetLevel) {
                    meta.addEnchant(Enchantment.PROTECTION, targetLevel, true);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    private int findMaxProt(Player player, String suffix) {
        int max = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().endsWith(suffix) && !isAnySpecial(item)) {
                int level = item.getEnchantmentLevel(Enchantment.PROTECTION);
                if (level > max) max = level;
            }
        }
        return Math.max(max, 3);
    }

    private boolean isAnySpecial(ItemStack item) {
        return item != null && item.hasItemMeta() && 
               item.getItemMeta().getPersistentDataContainer().has(setTagKey, PersistentDataType.STRING);
    }
}
