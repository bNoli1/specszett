package me.specialarmor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

public class SpecialArmorPlugin extends JavaPlugin implements Listener {

    private final NamespacedKey setTagKey = new NamespacedKey(this, "special_set_name");
    private final String ADMIN_GUI_TITLE = "GUI Szerkesztő (Admin)";
    private final String PLAYER_GUI_TITLE = "Referencia Páncélok";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("specadmin") != null) {
            getCommand("specadmin").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player && player.hasPermission("specialarmor.admin")) {
                    openAdminGui(player);
                }
                return true;
            });
        }

        if (getCommand("szett") != null) {
            getCommand("szett").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    openPlayerGui(player);
                }
                return true;
            });
        }
    }

    private void openAdminGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(ADMIN_GUI_TITLE, NamedTextColor.RED));
        List<?> layout = getConfig().getList("gui-layout");
        if (layout != null) {
            for (int i = 0; i < Math.min(layout.size(), 27); i++) {
                inv.setItem(i, (ItemStack) layout.get(i));
            }
        }
        player.openInventory(inv);
    }

    private void openPlayerGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(PLAYER_GUI_TITLE, NamedTextColor.DARK_PURPLE));
        
        // 1. Sablon betöltése
        List<?> layout = getConfig().getList("gui-layout");
        if (layout != null) {
            for (int i = 0; i < Math.min(layout.size(), 27); i++) {
                inv.setItem(i, (ItemStack) layout.get(i));
            }
        }

        // 2. Játékos adatainak betöltése a fájlból
        String uuid = player.getUniqueId().toString();
        if (getConfig().contains("player-data." + uuid)) {
            for (String key : getConfig().getConfigurationSection("player-data." + uuid).getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = getConfig().getItemStack("player-data." + uuid + "." + slot);
                    if (item != null) inv.setItem(slot, item);
                } catch (Exception e) { /* Hibás adat kihagyása */ }
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        if (event.getView().title().equals(Component.text(ADMIN_GUI_TITLE, NamedTextColor.RED))) {
            getConfig().set("gui-layout", List.of(inv.getContents()));
            saveConfig();
            player.sendMessage(Component.text("GUI Sablon elmentve!", NamedTextColor.GREEN));
        } 
        else if (event.getView().title().equals(Component.text(PLAYER_GUI_TITLE, NamedTextColor.DARK_PURPLE))) {
            String uuid = player.getUniqueId().toString();
            List<?> layout = getConfig().getList("gui-layout");
            
            // Csak azokat mentjük, amik nem az admin sablon részei
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack current = inv.getItem(i);
                ItemStack template = (layout != null && i < layout.size()) ? (ItemStack) layout.get(i) : null;

                if (current != null && !current.equals(template)) {
                    getConfig().set("player-data." + uuid + "." + i, current);
                } else {
                    getConfig().set("player-data." + uuid + "." + i, null);
                }
            }
            saveConfig();
            updateArmorStats(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (event.getView().title().equals(Component.text(PLAYER_GUI_TITLE, NamedTextColor.DARK_PURPLE))) {
            int slot = event.getRawSlot();
            ItemStack cursorItem = event.getCursor();

            // 1. Sablon védelem
            if (slot >= 0 && slot < event.getInventory().getSize()) {
                List<?> layout = getConfig().getList("gui-layout");
                ItemStack template = (layout != null && slot < layout.size()) ? (ItemStack) layout.get(slot) : null;
                if (template != null && template.getType() != Material.AIR) {
                    event.setCancelled(true);
                    return;
                }
            }

            // 2. Behelyezés ellenőrzése
            if (cursorItem != null && !cursorItem.getType().isAir()) {
                if (isAnySpecial(cursorItem)) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("Speciális szettet nem használhatsz referenciaként!", NamedTextColor.RED));
                    return;
                }

                String typeName = cursorItem.getType().name();
                String category = getArmorCategory(typeName);

                if (category != null) {
                    for (int i = 0; i < event.getInventory().getSize(); i++) {
                        if (i == slot) continue;
                        ItemStack itemInInv = event.getInventory().getItem(i);
                        if (itemInInv != null && itemInInv.getType().name().endsWith(category)) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("Ebből a szettrészből már van egy bent!", NamedTextColor.RED));
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateArmorStats(event.getPlayer());
    }

    private void updateArmorStats(Player player) {
        int[] bestFromGui = findMaxProtInGui(player);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            String setName = meta.getPersistentDataContainer().get(setTagKey, PersistentDataType.STRING);
            
            if (setName != null && getConfig().contains("sets." + setName)) {
                int offset = getConfig().getInt("sets." + setName + ".offset");
                String typeName = item.getType().name();
                int target;
                if (typeName.endsWith("_HELMET")) target = bestFromGui[0] + offset;
                else if (typeName.endsWith("_CHESTPLATE")) target = bestFromGui[1] + offset;
                else if (typeName.endsWith("_LEGGINGS")) target = bestFromGui[2] + offset;
                else if (typeName.endsWith("_BOOTS")) target = bestFromGui[3] + offset;
                else continue;

                if (item.getEnchantmentLevel(Enchantment.PROTECTION) != target) {
                    meta.addEnchant(Enchantment.PROTECTION, target, true);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    private int[] findMaxProtInGui(Player player) {
        int[] maxes = {0, 0, 0, 0};
        String uuid = player.getUniqueId().toString();
        if (getConfig().contains("player-data." + uuid)) {
            for (String key : getConfig().getConfigurationSection("player-data." + uuid).getKeys(false)) {
                ItemStack item = getConfig().getItemStack("player-data." + uuid + "." + key);
                if (item != null) {
                    int prot = item.getEnchantmentLevel(Enchantment.PROTECTION);
                    String name = item.getType().name();
                    if (name.endsWith("_HELMET")) maxes[0] = Math.max(maxes[0], prot);
                    else if (name.endsWith("_CHESTPLATE")) maxes[1] = Math.max(maxes[1], prot);
                    else if (name.endsWith("_LEGGINGS")) maxes[2] = Math.max(maxes[2], prot);
                    else if (name.endsWith("_BOOTS")) maxes[3] = Math.max(maxes[3], prot);
                }
            }
        }
        for(int i=0; i<4; i++) if(maxes[i] < 3) maxes[i] = 3;
        return maxes;
    }

    private String getArmorCategory(String typeName) {
        if (typeName.endsWith("_HELMET")) return "_HELMET";
        if (typeName.endsWith("_CHESTPLATE")) return "_CHESTPLATE";
        if (typeName.endsWith("_LEGGINGS")) return "_LEGGINGS";
        if (typeName.endsWith("_BOOTS")) return "_BOOTS";
        return null;
    }

    private boolean isAnySpecial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(setTagKey, PersistentDataType.STRING);
    }
}
