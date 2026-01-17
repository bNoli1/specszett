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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public class SpecialArmorPlugin extends JavaPlugin implements Listener {

    private final NamespacedKey setTagKey = new NamespacedKey(this, "special_set_name");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("specadmin").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player && player.hasPermission("specialarmor.admin")) {
                openAdminGui(player);
            }
            return true;
        });

        getCommand("szett").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                openPlayerGui(player);
            }
            return true;
        });

        getCommand("setspecial").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player) || !player.hasPermission("specialarmor.admin")) {
                sender.sendMessage(getMsg("msg-no-permission", "&cNincs jogod!"));
                return true;
            }
            if (args.length < 2) return true;
            
            String setName = args[0].toLowerCase();
            int offset;
            try { offset = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return true; }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) return true;

            getConfig().set("sets." + setName + ".offset", offset);
            saveConfig();

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(setTagKey, PersistentDataType.STRING, setName);
            item.setItemMeta(meta);
            updateArmorStats(player);
            player.sendMessage(Component.text("Szettrész mentve!"));
            return true;
        });
    }

    private Component getMsg(String path, String def) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getConfig().getString(path, def));
    }

    private void openAdminGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, getMsg("gui-title-admin", "Admin GUI"));
        List<?> layout = getConfig().getList("gui-layout");
        if (layout != null) {
            for (int i = 0; i < Math.min(layout.size(), 27); i++) inv.setItem(i, (ItemStack) layout.get(i));
        }
        player.openInventory(inv);
    }

    private void openPlayerGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, getMsg("gui-title-player", "Szett GUI"));
        List<?> layout = getConfig().getList("gui-layout");
        if (layout != null) {
            for (int i = 0; i < Math.min(layout.size(), 27); i++) inv.setItem(i, (ItemStack) layout.get(i));
        }
        String uuid = player.getUniqueId().toString();
        if (getConfig().contains("player-data." + uuid)) {
            for (String key : getConfig().getConfigurationSection("player-data." + uuid).getKeys(false)) {
                inv.setItem(Integer.parseInt(key), getConfig().getItemStack("player-data." + uuid + "." + key));
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().title().equals(getMsg("gui-title-player", "Szett GUI"))) {
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 27) {
                List<?> layout = getConfig().getList("gui-layout");
                ItemStack template = (layout != null && slot < layout.size()) ? (ItemStack) layout.get(slot) : null;
                if (template != null && template.getType() != Material.AIR) {
                    event.setCancelled(true);
                    return;
                }
            }

            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (isAnySpecial(cursor)) {
                    event.setCancelled(true);
                    player.sendMessage(getMsg("msg-error-special", "&cSpeciális!"));
                    return;
                }

                String category = getArmorCategory(cursor.getType().name());
                if (category != null) {
                    for (int i = 0; i < event.getInventory().getSize(); i++) {
                        if (i == slot) continue;
                        ItemStack item = event.getInventory().getItem(i);
                        if (item != null && item.getType().name().endsWith(category)) {
                            event.setCancelled(true);
                            player.sendMessage(getMsg("msg-error-duplicate", "&cDuplikált!"));
                            return;
                        }
                    }
                    // SIKERES BERAKÁS ÜZENET
                    String partName = getConfig().getString("parts." + category, category);
                    int prot = cursor.getEnchantmentLevel(Enchantment.PROTECTION);
                    String msg = getConfig().getString("msg-item-added", "OK")
                            .replace("%part%", partName)
                            .replace("%level%", String.valueOf(prot));
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().title().equals(getMsg("gui-title-player", "Szett GUI"))) {
            Player player = (Player) event.getPlayer();
            String uuid = player.getUniqueId().toString();
            List<?> layout = getConfig().getList("gui-layout");
            for (int i = 0; i < event.getInventory().getSize(); i++) {
                ItemStack current = event.getInventory().getItem(i);
                ItemStack template = (layout != null && i < layout.size()) ? (ItemStack) layout.get(i) : null;
                if (current != null && !current.equals(template)) getConfig().set("player-data." + uuid + "." + i, current);
                else getConfig().set("player-data." + uuid + "." + i, null);
            }
            saveConfig();
            updateArmorStats(player);
        } else if (event.getView().title().equals(getMsg("gui-title-admin", "Admin GUI"))) {
            getConfig().set("gui-layout", List.of(event.getInventory().getContents()));
            saveConfig();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { updateArmorStats(event.getPlayer()); }

    private void updateArmorStats(Player player) {
        int[] best = findMaxProtInGui(player);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String setName = item.getItemMeta().getPersistentDataContainer().get(setTagKey, PersistentDataType.STRING);
            if (setName != null && getConfig().contains("sets." + setName)) {
                int off = getConfig().getInt("sets." + setName + ".offset");
                String type = item.getType().name();
                int target = -1;
                if (type.endsWith("_HELMET")) target = best[0] + off;
                else if (type.endsWith("_CHESTPLATE")) target = best[1] + off;
                else if (type.endsWith("_LEGGINGS")) target = best[2] + off;
                else if (type.endsWith("_BOOTS")) target = best[3] + off;

                if (target != -1) {
                    ItemMeta meta = item.getItemMeta();
                    meta.addEnchant(Enchantment.PROTECTION, Math.max(0, target), true);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    private int[] findMaxProtInGui(Player player) {
        int[] m = {0,0,0,0}; // Alapértelmezett minimum mostantól 0
        String uuid = player.getUniqueId().toString();
        if (getConfig().contains("player-data." + uuid)) {
            for (String k : getConfig().getConfigurationSection("player-data." + uuid).getKeys(false)) {
                ItemStack it = getConfig().getItemStack("player-data." + uuid + "." + k);
                if (it != null) {
                    int p = it.getEnchantmentLevel(Enchantment.PROTECTION);
                    String n = it.getType().name();
                    if (n.endsWith("_HELMET")) m[0] = p;
                    else if (n.endsWith("_CHESTPLATE")) m[1] = p;
                    else if (n.endsWith("_LEGGINGS")) m[2] = p;
                    else if (n.endsWith("_BOOTS")) m[3] = p;
                }
            }
        }
        return m;
    }

    private String getArmorCategory(String t) {
        if (t.endsWith("_HELMET")) return "_HELMET";
        if (t.endsWith("_CHESTPLATE")) return "_CHESTPLATE";
        if (t.endsWith("_LEGGINGS")) return "_LEGGINGS";
        if (t.endsWith("_BOOTS")) return "_BOOTS";
        return null;
    }

    private boolean isAnySpecial(ItemStack i) {
        return i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(setTagKey, PersistentDataType.STRING);
    }
}
