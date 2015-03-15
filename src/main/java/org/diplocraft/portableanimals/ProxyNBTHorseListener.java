/*
 * The MIT License
 *
 * Copyright 2015 psygate (https://github.com/psygate/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.diplocraft.portableanimals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.server.v1_7_R3.ItemMapEmpty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.diplocraft.portableanimals.serializable.HandleProxy;
import org.diplocraft.portableanimals.serializable.NBTagProxy;

/**
 *
 * @author psygate (https://github.com/psygate/)
 */
public class ProxyNBTHorseListener implements Listener {

    private final static Set<InventoryAction> placeset = new HashSet<>();
    private final static Set<InventoryAction> pickset = new HashSet<>();

    private static final String SIGNATURE = "Portable Horse v1.1NBT";
    private static final String PERMISSION = "portableanimals.use";
    private NBTagProxy nbtproxy;

    static {
        placeset.add(InventoryAction.PLACE_ALL);
        placeset.add(InventoryAction.PLACE_SOME);
        placeset.add(InventoryAction.PLACE_ONE);

        pickset.add(InventoryAction.PICKUP_ALL);
        pickset.add(InventoryAction.PICKUP_HALF);
        pickset.add(InventoryAction.PICKUP_ONE);
        pickset.add(InventoryAction.PICKUP_SOME);
    }

    public ProxyNBTHorseListener() {
        Entity entity = null;
        try {
            World w = Bukkit.getWorlds().get(0);
            entity = w.spawnEntity(new Location(w, 0, 128, 0), EntityType.HORSE);
            nbtproxy = new NBTagProxy(entity);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private boolean hasSignature(final ItemStack stack) {
        return stack != null
                && stack.hasItemMeta()
                && stack.getItemMeta().hasLore()
                && stack.getItemMeta().getLore().size() >= 1
                && SIGNATURE.equals(stack.getItemMeta().getLore().get(0));
    }

//    @EventHandler
//    public void saddleInteract(final InventoryClickEvent ev) {
//        System.out.println("Inventory: " + ev.getInventory());
//        System.out.println("Cursor: " + ev.getCursor());
//        System.out.println("Item: " + ev.getCurrentItem());
//        System.out.println("Action: " + ev.getAction());
//        System.out.println("Clicked: " + ev.getClickedInventory());
//    }
    private ItemStack isSaddleUpEvent(final InventoryClickEvent ev) {
        if (ev.getInventory() != null && ev.getWhoClicked() instanceof Player
                && ev.getWhoClicked().hasPermission(PERMISSION)
                && ev.getInventory() instanceof HorseInventory
                && ev.getClickedInventory() != null) {
            HorseInventory inv = (HorseInventory) ev.getInventory();

            if (placeset.contains(ev.getAction()) && ev.getCursor() != null
                    && ev.getCursor().getType() == Material.SADDLE
                    && ev.getClickedInventory() instanceof HorseInventory) {
                return ev.getCursor();
            } else if (ev.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (ev.getCurrentItem() != null && ev.getCurrentItem().getType() == Material.SADDLE
                        && (inv.getSaddle() == null || inv.getSaddle().getType() == Material.AIR)) {

                    return ev.getCurrentItem();
                }
            }
        }

        return null;
    }

    private boolean isPortableSaddle(final ItemStack stack) {
        return stack != null
                && stack.getType() == Material.SADDLE
                && stack.hasItemMeta()
                && stack.getItemMeta().hasLore()
                && stack.getItemMeta().getLore().size() > 1
                && SIGNATURE.equals(stack.getItemMeta().getLore().get(0));
    }

    private boolean isSaddle(final ItemStack stack) {
        return stack != null && stack.getType() == Material.SADDLE;
    }

    private ItemStack isSaddleRemoveEvent(final InventoryClickEvent ev) {
        if (ev.getInventory() instanceof HorseInventory
                && ev.getClickedInventory() instanceof HorseInventory
                && ev.getWhoClicked().hasPermission(PERMISSION)
                && (pickset.contains(ev.getAction()) || ev.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                && (isSaddle(ev.getCurrentItem()))) {
            return ev.getCurrentItem();
        }

        return null;
    }

    private byte[] getLoreData(final ItemStack stack) throws IOException {
        Iterator<String> it = stack.getItemMeta().getLore().iterator();
        it.next();
        StringBuilder builder = new StringBuilder();
        while (it.hasNext()) {
            builder.append(it.next());
        }

        return Base64.decode(builder.toString());
    }

    @EventHandler
    public void respawnHorse(final PlayerInteractEvent ev) {
        if (ev.getAction() == Action.RIGHT_CLICK_AIR && isPortableSaddle(ev.getItem())) {
            Horse horse = null;
            try {
                horse = (Horse) ev.getPlayer().getWorld().spawnEntity(ev.getPlayer().getLocation(), EntityType.HORSE);
                byte[] data = getLoreData(ev.getItem());
                try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
                        DataInputStream din = new DataInputStream(bin)) {
                    nbtproxy.load(din, data.length, horse);
                }

                ev.getPlayer().setItemInHand(null);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                if (horse != null) {
                    horse.remove();
                }
            }
        }
    }

    @EventHandler
    public void saddlePlacement(final InventoryClickEvent ev) {
        ItemStack saddle;

        if ((saddle = isSaddleUpEvent(ev)) == null) {
            return;
        }

        HorseInventory hin = (HorseInventory) ev.getInventory();

        try {
            encodeFully((Horse) hin.getHolder(), saddle);
//            ((Horse) hin.getHolder()).remove();
        } catch (IllegalArgumentException e) {
            if (ev.getWhoClicked() instanceof Player) {
                ((Player) ev.getWhoClicked()).sendMessage(ChatColor.RED + "There is too much data in this horse to encode.");
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @EventHandler
    public void saddleRemoval(final InventoryClickEvent ev) {
        ItemStack saddle;

        if ((saddle = isSaddleRemoveEvent(ev)) == null) {
            return;
        }

        HorseInventory hin = (HorseInventory) ev.getInventory();

        try {
            encodeFully((Horse) hin.getHolder(), saddle);
            ((Horse) hin.getHolder()).remove();
        } catch (IllegalArgumentException e) {
            if (ev.getWhoClicked() instanceof Player) {
                ((Player) ev.getWhoClicked()).sendMessage(ChatColor.RED + "There is too much data in this horse to encode.");
            }
        } catch (Exception e) {
            ItemMeta meta = saddle.getItemMeta();
            meta.setLore(null);
            saddle.setItemMeta(meta);
            e.printStackTrace(System.err);
        }
    }

    private void encodeFully(final Horse horse, final ItemStack saddle) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException, InstantiationException {

        if (saddle.getItemMeta().hasLore()) {
            ItemMeta meta = saddle.getItemMeta();
            meta.setLore(null);
            saddle.setItemMeta(meta);
        }

        for (int i = 0; i < horse.getInventory().getSize(); i++) {
            final ItemStack stack = horse.getInventory().getItem(i);

            if (stack != horse.getInventory().getSaddle()) {
                if (isPortableSaddle(stack)) {
                    horse.getInventory().setItem(i, null);
                    horse.getLocation().getWorld().dropItemNaturally(horse.getLocation(), stack);
                }
            }
        }

        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream dout = new DataOutputStream(bout)) {
            nbtproxy.write(dout, horse);
            dout.flush();
//            gout.finish();
            dout.close();
//            gout.close();
            bout.close();

            if (bout.toByteArray().length > 4096) {
                throw new IllegalArgumentException();
            }

            String lore = Base64.encodeBytes(bout.toByteArray());
            ItemMeta meta = saddle.getItemMeta();
            meta.setLore(addSignature(split(lore, 32)));

            if (horse.getCustomName() != null && !"".equals(horse.getCustomName())) {
                meta.setDisplayName(horse.getCustomName());
            } else {
                meta.setDisplayName("Portable " + horse.getVariant().name());
            }

            saddle.setItemMeta(meta);
        }
    }

    private List<String> addSignature(final List<String> list) {
        final ArrayList<String> alist = new ArrayList<>();

        alist.add(SIGNATURE);
        alist.addAll(list);

        return alist;
    }

    private List<String> split(final String text, final int size) {
        List<String> ret = new ArrayList<>((text.length() + size - 1) / size);

        for (int start = 0; start < text.length(); start += size) {
            ret.add(text.substring(start, Math.min(text.length(), start + size)));
        }
        return ret;
    }
}
