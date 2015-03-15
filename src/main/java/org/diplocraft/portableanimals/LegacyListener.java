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
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import static org.bukkit.Material.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import java.io.*;
import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.HorseInventory;

/**
 *
 * @author psygate (https://github.com/psygate)
 */
public class LegacyListener implements Listener {
    private final static Set<InventoryAction> placeset = new HashSet<>();
    private final static Set<InventoryAction> pickset = new HashSet<>();
    public final static String MARKER = "==DATA==";
    public final static String SIGNATURE = "Portable Horse V1";
    public final static int LINE_LENGTH = 32;
    public final static String PERMISSION = "portableanimals.use";
    private boolean disabled = false;

    static {
        placeset.add(InventoryAction.PLACE_ALL);
        placeset.add(InventoryAction.PLACE_SOME);
        placeset.add(InventoryAction.PLACE_ONE);

        pickset.add(InventoryAction.PICKUP_ALL);
        pickset.add(InventoryAction.PICKUP_HALF);
        pickset.add(InventoryAction.PICKUP_ONE);
        pickset.add(InventoryAction.PICKUP_SOME);
    }

    private boolean isSignature(String signature) {
        return SIGNATURE.equals(signature);
    }

    private boolean isPortableSaddle(final ItemStack stack) {
        return stack != null && stack.getType() == Material.SADDLE
                && stack.hasItemMeta() && stack.getItemMeta().hasLore()
                && isSignature(stack.getItemMeta().getLore().get(0));
    }

    private ItemStack isSaddleUpEvent(final InventoryClickEvent ev) {
        if (ev.getInventory() != null && ev.getWhoClicked() instanceof Player
                && ev.getWhoClicked().hasPermission(PERMISSION)
                && ev.getInventory() instanceof HorseInventory
                && ev.getClickedInventory() != null
                && !disabled) {
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

    private boolean isRelevantRespawnAction(final PlayerInteractEvent ev) {
        return (ev.getPlayer()
                .hasPermission(PERMISSION)
                && ev.getAction() == Action.RIGHT_CLICK_BLOCK
                && isPortableSaddle(ev.getItem()));
    }

    private Horse createHorse(final Location loc) {
        return (Horse) loc.getWorld().spawnEntity(loc.add(0, 1, 0), EntityType.HORSE);
    }

    private String extractData(final ItemStack item) {
        ListIterator<String> it = item.getItemMeta().getLore().listIterator();
        String base = "";
        while (it.hasNext()) {
            if (MARKER.equals(it.next())) {
                while (it.hasNext()) {
                    String cur = it.next();
                    if (!MARKER.equals(cur)) {
                        base += cur;
                    }
                }

                break;
            }
        }

        return ("".equals(base)) ? null : base;
    }

    @EventHandler
    public void respawnHorse(final PlayerInteractEvent ev) {
        if (isRelevantRespawnAction(ev)) {
            try {
                final String loreData = extractData(ev.getItem());

                if (loreData == null) {
                    return;
                }


                byte[] bdata = Base64.decode(loreData);

                try (ByteArrayInputStream bai = new ByteArrayInputStream(bdata);
                        ByteObjectInputStream boi = new ByteObjectInputStream(bai)) {
                    HorseData data = new HorseData();
                    data.readExternal(boi);
                    Horse horse = createHorse(ev.getClickedBlock().getLocation());
                    data.apply(horse);
                    ItemStack saddle = ev.getItem();
                    ItemMeta meta = saddle.getItemMeta();
                    meta.setLore(null);
                    saddle.setItemMeta(meta);
                    horse.getInventory().setSaddle(saddle);
                    ev.getPlayer().setItemInHand(null);
                }

            } catch (IOException | ClassNotFoundException e) {
                ev.getPlayer().sendMessage(ChatColor.RED + "Something went wrong. Contact a local administrator.");

            }
        }
    }

    public class ByteObjectInputStream implements ObjectInput {

        private final InputStream in;

        public ByteObjectInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public Object readObject() throws ClassNotFoundException, IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            int read = 0;
            while (read < b.length) {
                int last = in.read();
                if (last == -1) {
                    break;
                } else {
                    b[read] = (byte) (last & 0xFF);
                    read++;
                }
            }

            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = 0;
            while (read < b.length && read < len) {
                int last = in.read();
                if (last == -1) {
                    break;
                } else {
                    read++;
                    b[read + off] = (byte) (last & 0xFF);
                }
            }

            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            return in.skip(n);
        }

        @Override
        public int available() throws IOException {
            return in.available();
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            int read = 0;
            while (read < b.length) {
                int last = in.read();
                if (last == -1) {
                    throw new EOFException();
                } else {
                    read++;
                    b[read] = (byte) (last & 0xFF);
                }
            }
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            int read = 0;
            while (read < b.length && read < len) {
                int last = in.read();
                if (last == -1) {
                    throw new EOFException();
                } else {
                    read++;
                    b[read + off] = (byte) (last & 0xFF);
                }
            }
        }

        @Override
        public int skipBytes(int n) throws IOException {
            return (int) in.skip(n);
        }

        @Override
        public boolean readBoolean() throws IOException {
            int val = readByte();

            return (val == 0);
        }

        @Override
        public byte readByte() throws IOException {
            int val = read();
            if (val == -1) {
                throw new EOFException();
            }
            return (byte) (val & 0xFF);
        }

        @Override
        public int readUnsignedByte() throws IOException {
            int val = read();
            if (val == -1) {
                throw new EOFException();
            }
            return val;
        }

        @Override
        public short readShort() throws IOException {
            return (short) (readUnsignedShort() & 0xFFFF);
        }

        @Override
        public int readUnsignedShort() throws IOException {
            int a = readUnsignedByte();
            int b = readUnsignedByte();
            return (a & 0xFF) << 8 | (b & 0xFF);
        }

        @Override
        public char readChar() throws IOException {
            return (char) readUnsignedShort();
        }

        @Override
        public int readInt() throws IOException {
            return (readUnsignedByte() << 24) | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8) | readUnsignedByte();
        }

        @Override
        public long readLong() throws IOException {
            return (((long) readUnsignedByte()) << 56)
                    | (((long) readUnsignedByte()) << 48)
                    | (((long) readUnsignedByte()) << 40)
                    | ((long) readUnsignedByte() << 32)
                    | (((long) readUnsignedByte()) << 24)
                    | (((long) readUnsignedByte()) << 16)
                    | (((long) readUnsignedByte()) << 8)
                    | readUnsignedByte();
        }

        @Override
        public float readFloat() throws IOException {
            return Float.intBitsToFloat(readInt());
        }

        @Override
        public double readDouble() throws IOException {
            return Double.longBitsToDouble(readLong());
        }

        @Override
        public String readLine() throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String readUTF() throws IOException {
            int size = readUnsignedByte() << 8 + readUnsignedByte();
            byte[] data = new byte[size];
            read(data);

            return new String(data);
        }

    }

    public class HorseData implements Externalizable {

        public static final byte VERSION = 0;
        public static final long serialVersionUID = 7487892987L;
        private String ownerName;
        private int age;
        private boolean ageLock;
        private boolean canPickup;
        private boolean canBreed;
        private String customName;
        private int domestication;
        private int color;
        private int fireTicks;
        private double jumpStrength;
        private int maxAir;
        private int maxNoDamageTicks;
        private int remainingAir;
        private int style;
        private int ticksLived;
        private UUID owner;
        private int type;
        private UUID uuid;
        private int variant;
        private boolean tamed;
        private boolean adult;
        private boolean showName;
        private double health;
        private double maxHealth;
        private boolean chested;
        private HashMap<String, int[]> effects = new HashMap<>();
        private Collection<SerialStack> items = new LinkedList<>();

        public HorseData() {

        }

        public HorseData(final Horse horse) {
            ownerName = horse.getOwner().getName();
            customName = horse.getCustomName();
            age = horse.getAge();
            ageLock = horse.getAgeLock();
            canPickup = horse.getCanPickupItems();
            canBreed = horse.canBreed();
            domestication = horse.getDomestication();
            color = horse.getColor().ordinal();
            fireTicks = horse.getFireTicks();
            jumpStrength = horse.getJumpStrength();
            maxAir = horse.getMaximumAir();
            maxNoDamageTicks = horse.getMaximumNoDamageTicks();
            remainingAir = horse.getRemainingAir();
            style = horse.getStyle().ordinal();
            ticksLived = horse.getTicksLived();
            owner = horse.getOwner().getUniqueId();
            type = horse.getType().ordinal();
            uuid = horse.getUniqueId();
            variant = horse.getVariant().ordinal();
            tamed = horse.isTamed();
            adult = horse.isAdult();
            showName = horse.isCustomNameVisible();
            health = (horse.getHealth() < 1) ? 1 : horse.getHealth();
            maxHealth = horse.getMaxHealth();
            chested = horse.isCarryingChest();

            for (PotionEffect effect : horse.getActivePotionEffects()) {
                effects.put(effect.getType().getName(), new int[]{effect.getDuration(), effect.getAmplifier()});
            }

            if (horse.getInventory() != null) {
                for (int i = 1; i < horse.getInventory().getSize(); i++) {
                    ItemStack invStack = horse.getInventory().getItem(i);
                    if (invStack != null && invStack.getType() != Material.AIR) {
                        SerialStack serialstack = new SerialStack(invStack, i);

                        items.add(serialstack);
                    }
                }
            }

//        loadNBT(horse);
        }

        private void loadNBT(Entity en) {
            try {
                Object handle = en.getClass().getDeclaredMethod("getHandle", new Class<?>[]{})
                        .invoke(en, new Object[]{});
                final LinkedList<Method> methods = new LinkedList<>();
                for (Method mnbts : handle.getClass().getDeclaredMethods()) {
                    if (mnbts.getParameterCount() == 1 && !mnbts.getParameterTypes()[0].isPrimitive()
                            && mnbts.getParameterTypes()[0].getName().toLowerCase().contains("nbttag")) {
                        methods.add(mnbts);
                    }
//                else {
//                    logger.log(Level.FINER, "Not a candidate: {0}", mnbts);
//                }
                }

                Collections.sort(methods, new Comparator<Method>() {

                    @Override
                    public int compare(Method o1, Method o2) {
                        return o2.getName().compareToIgnoreCase(o1.getName());
                    }
                });

                for (Method m : methods) {
                    Object nbtinstance = m.getParameterTypes()[0].newInstance();
                    m.invoke(handle, nbtinstance);
                    for (Method mnbt : nbtinstance.getClass().getDeclaredMethods()) {
                        if (mnbt.getName().toLowerCase().contains("has")
                                || mnbt.getName().toLowerCase().contains("contains")) {
                        }
                    }

//                logger.log(Level.INFO, "Tag created: {0}", nbtinstance);
                }

            } catch (Exception e) {
            }
        }

        public void apply(Horse horse) {
            horse.setStyle(Horse.Style.values()[style]);
            horse.setVariant(Horse.Variant.values()[variant]);

            horse.setColor(Horse.Color.values()[color]);

            horse.setOwner(Bukkit.getPlayer(ownerName));
            horse.setAge(age);
            horse.setAgeLock(ageLock);
            horse.setCanPickupItems(canPickup);
            horse.setBreed(canBreed);
            horse.setCustomName(customName);
            horse.setDomestication(domestication);
//        horse.setFireTicks(fireTicks);
            horse.setJumpStrength(jumpStrength);
            horse.setMaximumAir(maxAir);
            horse.setMaximumNoDamageTicks(maxNoDamageTicks);
            horse.setRemainingAir(remainingAir);
            horse.setTicksLived(ticksLived);
            horse.setTamed(tamed);
            if (adult) {
                horse.setAdult();
            } else {
                horse.setBaby();
            }

            horse.setCustomNameVisible(showName);
            horse.setMaxHealth(maxHealth);
            horse.setHealth(health);
            horse.setCarryingChest(chested);
            horse.getInventory().clear();
            if (items.size() > 1) {
                horse.setCarryingChest(true);
            }
            for (SerialStack s : items) {
                horse.getInventory().setItem(s.getSlot(), s.toItemStack());
            }
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.write(new byte[]{'h', 'd'});
            out.writeByte(VERSION);
            out.writeByte(ownerName.length());
            out.write(ownerName.getBytes());
            out.writeInt(age);
            out.writeBoolean(ageLock);
            out.writeBoolean(canPickup);
            out.writeBoolean(canBreed);
            if (customName == null || customName.length() == 0) {
                out.writeByte(0);
            } else {
                out.writeByte(customName.length());
                out.write(customName.getBytes());
            }
            out.writeInt(domestication);
            out.writeInt(color);
            out.writeInt(fireTicks);
            out.writeDouble(jumpStrength);
            out.writeInt(maxAir);
            out.writeInt(maxNoDamageTicks);
            out.writeInt(remainingAir);
            out.writeInt(style);
            out.writeInt(ticksLived);
            out.writeInt(type);
            out.writeInt(variant);
            out.writeBoolean(tamed);
            out.writeBoolean(showName);
            out.writeDouble(health);
            out.writeDouble(maxHealth);
            out.writeBoolean(chested);
            out.writeBoolean(adult);
            out.writeByte(items.size());
            if (items.size() > Byte.MAX_VALUE) {
                throw new IOException("Unable to encode items.");
            }

            for (SerialStack stack : items) {
                stack.writeExternal(out);
            }

            out.flush();
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            if (in.readByte() != 'h' || in.readByte() != 'd') {
                throw new IOException("Wrong object.");
            }

            int version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Wrong version.");
            }

            int ownernamelength = in.readByte();
            byte[] ownername = new byte[ownernamelength];
            in.read(ownername);
            ownerName = new String(ownername);

            age = in.readInt();
            ageLock = in.readBoolean();
            canPickup = in.readBoolean();
            canBreed = in.readBoolean();
            int customnamelength = in.readByte();
            if (customnamelength > 0) {
                byte[] customname = new byte[customnamelength];
                in.read(customname);
                customName = new String(customname);
            }

            domestication = in.readInt();
            color = in.readInt();
            fireTicks = in.readInt();
            jumpStrength = in.readDouble();
            maxAir = in.readInt();
            maxNoDamageTicks = in.readInt();
            remainingAir = in.readInt();
            style = in.readInt();
            ticksLived = in.readInt();
            type = in.readInt();
            variant = in.readInt();
            tamed = in.readBoolean();
            showName = in.readBoolean();
            health = in.readDouble();
            maxHealth = in.readDouble();
            chested = in.readBoolean();
            adult = in.readBoolean();

            int size = in.readByte();

            if (size < 0) {
                throw new IOException("Encoding broken.");
            }
            items.clear();
            for (int i = 0; i < size; i++) {

                SerialStack stack = new SerialStack();
                stack.readExternal(in);
                items.add(stack);
            }

            if (items.size() > 1 && !chested) {
                this.chested = true;
            }
        }
    }

    private class SerialStack implements Externalizable {

        private int material;
        private short data;
        private int amount;
        private int slot;
        private String name;
        private LinkedList<SerialEnchantment> enchantments = new LinkedList<>();

        public SerialStack() {

        }

        public SerialStack(int material, short data, int amount, int slot, String name, Collection<SerialEnchantment> enchantments) {
            this.material = material;
            this.data = data;
            this.amount = amount;
            this.slot = slot;
            this.name = name;
            this.enchantments.addAll(enchantments);
        }

        public SerialStack(ItemStack stack, int slot) {
            if (stack == null) {
                throw new NullPointerException();
            }
            material = Table.toInteger(stack.getType());
            data = stack.getDurability();
            amount = stack.getAmount();
            this.slot = slot;

            if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                name = stack.getItemMeta().getDisplayName();
            } else {
                name = null;
            }

            if (stack.getEnchantments() != null && !stack.getEnchantments().isEmpty()) {
                for (Map.Entry<Enchantment, Integer> en : stack.getEnchantments().entrySet()) {
                    enchantments.add(new SerialEnchantment(en.getKey(), en.getValue()));
                }
            }
        }

        public ItemStack toItemStack() {
//    private int material;
//    private short data;
//    private int amount;
//    private int slot;
//    private String name;
//    private LinkedList<SerialEnchantment> enchantments = new LinkedList<>();
            ItemStack stack = new ItemStack(Table.materialFromInteger(material), amount, data);
            if (!enchantments.isEmpty()) {
                for (SerialEnchantment en : enchantments) {
                    stack.addEnchantment(en.toEnchantment(), en.getLevel());
                }
            }

            if (hasName()) {
                ItemMeta meta = stack.getItemMeta();
                meta.setDisplayName(name);
                stack.setItemMeta(meta);
            }

            return stack;
        }

        public boolean hasName() {
            return name != null;
        }

        public boolean hasEnchantments() {
            return enchantments.size() > 0;
        }

        public int getMaterial() {
            return material;
        }

        public void setMaterial(int material) {
            this.material = material;
        }

        public short getData() {
            return data;
        }

        public void setData(short data) {
            this.data = data;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public int getSlot() {
            return slot;
        }

        public void setSlot(int slot) {
            this.slot = slot;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LinkedList<SerialEnchantment> getEnchantments() {
            return enchantments;
        }

        public void setEnchantments(LinkedList<SerialEnchantment> enchantments) {
            this.enchantments = enchantments;
        }

        @Override
        public String toString() {
            return toItemStack().toString();
        }

        @Override
        public void writeExternal(ObjectOutput oo) throws IOException {

            oo.writeInt(material);
            oo.writeShort(data);
            oo.writeInt(amount);
            oo.writeInt(slot);
            if (hasName()) {
                oo.writeInt(getName().getBytes().length);
                oo.write(getName().getBytes());
            } else {
                oo.writeInt(0);
            }

            oo.writeInt(enchantments.size());
            for (SerialEnchantment en : enchantments) {
                en.writeExternal(oo);
            }
        }

        @Override
        public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
            enchantments.clear();
            material = oi.readInt();
            data = oi.readShort();
            amount = oi.readInt();
            slot = oi.readInt();

            int namelen = oi.readInt();

            if (namelen > 0) {
                byte[] nameb = new byte[namelen];
                oi.read(nameb);
                name = new String(nameb);
            } else {
                name = null;
            }

            int enchlen = oi.readInt();

            if (enchlen > 0) {
                for (int i = 0; i < enchlen; i++) {
                    SerialEnchantment nen = new SerialEnchantment();
                    nen.readExternal(oi);
                    enchantments.add(nen);
                }
            }
        }

    }

    private class SerialEnchantment implements Externalizable {

        private int type;
        private int level;

        public SerialEnchantment() {

        }

        public SerialEnchantment(Enchantment en, int level) {
            this.type = Table.toInteger(en);
            this.level = level;
        }

        public Enchantment toEnchantment() {
            return Table.enchantmentFromInteger(this.type);
        }

        public int getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return Table.enchantmentFromInteger(type).getName() + ":" + this.level;
        }

        @Override
        public void writeExternal(ObjectOutput oo) throws IOException {
            oo.writeInt(type);
            oo.writeInt(level);
        }

        @Override
        public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
            type = oi.readInt();
            level = oi.readInt();
        }

    }

    private static class Table {

        public final static HashMap<Integer, Material> materials = new HashMap<>();
        public final static HashMap<Material, Integer> revmaterials = new HashMap<>();
        public final static HashMap<String, Integer> enchantments = new HashMap<>();
        public final static HashMap<Integer, String> revenchantments = new HashMap<>();

        public static int toInteger(Material mat) {
            if (revmaterials.containsKey(mat)) {
                return revmaterials.get(mat);
            } else {
                throw new IllegalArgumentException();
            }
        }

        public static Material materialFromInteger(int mat) {
            if (materials.containsKey(mat)) {
                return materials.get(mat);
            } else {
                throw new IllegalArgumentException();
            }
        }

        public static int toInteger(Enchantment ench) {
            if (enchantments.containsKey(ench.getName())) {
                return enchantments.get(ench.getName());
            } else {
                throw new IllegalArgumentException();
            }
        }

        public static Enchantment enchantmentFromInteger(int ench) {
            if (revenchantments.containsKey(ench)) {
                return Enchantment.getByName(revenchantments.get(ench));
            } else {
                throw new IllegalArgumentException();
            }
        }

        static {
            materials.put(0, Material.AIR);
            materials.put(1, Material.STONE);
            materials.put(2, Material.GRASS);
            materials.put(3, Material.DIRT);
            materials.put(4, Material.COBBLESTONE);
            materials.put(5, Material.WOOD);
            materials.put(6, Material.SAPLING);
            materials.put(7, Material.BEDROCK);
            materials.put(8, Material.WATER);
            materials.put(9, Material.STATIONARY_WATER);
            materials.put(10, Material.LAVA);
            materials.put(11, Material.STATIONARY_LAVA);
            materials.put(12, Material.SAND);
            materials.put(13, Material.GRAVEL);
            materials.put(14, Material.GOLD_ORE);
            materials.put(15, Material.IRON_ORE);
            materials.put(16, Material.COAL_ORE);
            materials.put(17, Material.LOG);
            materials.put(18, Material.LEAVES);
            materials.put(19, Material.SPONGE);
            materials.put(20, Material.GLASS);
            materials.put(21, Material.LAPIS_ORE);
            materials.put(22, Material.LAPIS_BLOCK);
            materials.put(23, Material.DISPENSER);
            materials.put(24, Material.SANDSTONE);
            materials.put(25, Material.NOTE_BLOCK);
            materials.put(26, Material.BED_BLOCK);
            materials.put(27, Material.POWERED_RAIL);
            materials.put(28, Material.DETECTOR_RAIL);
            materials.put(29, Material.PISTON_STICKY_BASE);
            materials.put(30, Material.WEB);
            materials.put(31, Material.LONG_GRASS);
            materials.put(32, Material.DEAD_BUSH);
            materials.put(33, Material.PISTON_BASE);
            materials.put(34, Material.PISTON_EXTENSION);
            materials.put(35, Material.WOOL);
            materials.put(36, Material.PISTON_MOVING_PIECE);
            materials.put(37, Material.YELLOW_FLOWER);
            materials.put(38, Material.RED_ROSE);
            materials.put(39, Material.BROWN_MUSHROOM);
            materials.put(40, Material.RED_MUSHROOM);
            materials.put(41, Material.GOLD_BLOCK);
            materials.put(42, Material.IRON_BLOCK);
            materials.put(43, Material.DOUBLE_STEP);
            materials.put(44, Material.STEP);
            materials.put(45, Material.BRICK);
            materials.put(46, Material.TNT);
            materials.put(47, Material.BOOKSHELF);
            materials.put(48, Material.MOSSY_COBBLESTONE);
            materials.put(49, Material.OBSIDIAN);
            materials.put(50, Material.TORCH);
            materials.put(51, Material.FIRE);
            materials.put(52, Material.MOB_SPAWNER);
            materials.put(53, Material.WOOD_STAIRS);
            materials.put(54, Material.CHEST);
            materials.put(55, Material.REDSTONE_WIRE);
            materials.put(56, Material.DIAMOND_ORE);
            materials.put(57, Material.DIAMOND_BLOCK);
            materials.put(58, Material.WORKBENCH);
            materials.put(59, Material.CROPS);
            materials.put(60, Material.SOIL);
            materials.put(61, Material.FURNACE);
            materials.put(62, Material.BURNING_FURNACE);
            materials.put(63, Material.SIGN_POST);
            materials.put(64, Material.WOODEN_DOOR);
            materials.put(65, Material.LADDER);
            materials.put(66, Material.RAILS);
            materials.put(67, Material.COBBLESTONE_STAIRS);
            materials.put(68, Material.WALL_SIGN);
            materials.put(69, Material.LEVER);
            materials.put(70, Material.STONE_PLATE);
            materials.put(71, Material.IRON_DOOR_BLOCK);
            materials.put(72, Material.WOOD_PLATE);
            materials.put(73, Material.REDSTONE_ORE);
            materials.put(74, Material.GLOWING_REDSTONE_ORE);
            materials.put(75, Material.REDSTONE_TORCH_OFF);
            materials.put(76, Material.REDSTONE_TORCH_ON);
            materials.put(77, Material.STONE_BUTTON);
            materials.put(78, Material.SNOW);
            materials.put(79, Material.ICE);
            materials.put(80, Material.SNOW_BLOCK);
            materials.put(81, Material.CACTUS);
            materials.put(82, Material.CLAY);
            materials.put(83, Material.SUGAR_CANE_BLOCK);
            materials.put(84, Material.JUKEBOX);
            materials.put(85, Material.FENCE);
            materials.put(86, Material.PUMPKIN);
            materials.put(87, Material.NETHERRACK);
            materials.put(88, Material.SOUL_SAND);
            materials.put(89, Material.GLOWSTONE);
            materials.put(90, Material.PORTAL);
            materials.put(91, Material.JACK_O_LANTERN);
            materials.put(92, Material.CAKE_BLOCK);
            materials.put(93, Material.DIODE_BLOCK_OFF);
            materials.put(94, Material.DIODE_BLOCK_ON);
            materials.put(95, Material.LOCKED_CHEST);
            materials.put(96, Material.STAINED_GLASS);
            materials.put(97, Material.TRAP_DOOR);
            materials.put(98, Material.MONSTER_EGGS);
            materials.put(99, Material.SMOOTH_BRICK);
            materials.put(100, Material.HUGE_MUSHROOM_1);
            materials.put(101, Material.HUGE_MUSHROOM_2);
            materials.put(102, Material.IRON_FENCE);
            materials.put(103, Material.THIN_GLASS);
            materials.put(104, Material.MELON_BLOCK);
            materials.put(105, Material.PUMPKIN_STEM);
            materials.put(106, Material.MELON_STEM);
            materials.put(107, Material.VINE);
            materials.put(108, Material.FENCE_GATE);
            materials.put(109, Material.BRICK_STAIRS);
            materials.put(110, Material.SMOOTH_STAIRS);
            materials.put(111, Material.MYCEL);
            materials.put(112, Material.WATER_LILY);
            materials.put(113, Material.NETHER_BRICK);
            materials.put(114, Material.NETHER_FENCE);
            materials.put(115, Material.NETHER_BRICK_STAIRS);
            materials.put(116, Material.NETHER_WARTS);
            materials.put(117, Material.ENCHANTMENT_TABLE);
            materials.put(118, Material.BREWING_STAND);
            materials.put(119, Material.CAULDRON);
            materials.put(120, Material.ENDER_PORTAL);
            materials.put(121, Material.ENDER_PORTAL_FRAME);
            materials.put(122, Material.ENDER_STONE);
            materials.put(123, Material.DRAGON_EGG);
            materials.put(124, Material.REDSTONE_LAMP_OFF);
            materials.put(125, Material.REDSTONE_LAMP_ON);
            materials.put(126, Material.WOOD_DOUBLE_STEP);
            materials.put(127, Material.WOOD_STEP);
            materials.put(128, Material.COCOA);
            materials.put(129, Material.SANDSTONE_STAIRS);
            materials.put(130, Material.EMERALD_ORE);
            materials.put(131, Material.ENDER_CHEST);
            materials.put(132, Material.TRIPWIRE_HOOK);
            materials.put(133, Material.TRIPWIRE);
            materials.put(134, Material.EMERALD_BLOCK);
            materials.put(135, Material.SPRUCE_WOOD_STAIRS);
            materials.put(136, Material.BIRCH_WOOD_STAIRS);
            materials.put(137, Material.JUNGLE_WOOD_STAIRS);
            materials.put(138, Material.COMMAND);
            materials.put(139, Material.BEACON);
            materials.put(140, Material.COBBLE_WALL);
            materials.put(141, Material.FLOWER_POT);
            materials.put(142, Material.CARROT);
            materials.put(143, Material.POTATO);
            materials.put(144, Material.WOOD_BUTTON);
            materials.put(145, Material.SKULL);
            materials.put(146, Material.ANVIL);
            materials.put(147, Material.TRAPPED_CHEST);
            materials.put(148, Material.GOLD_PLATE);
            materials.put(149, Material.IRON_PLATE);
            materials.put(150, Material.REDSTONE_COMPARATOR_OFF);
            materials.put(151, Material.REDSTONE_COMPARATOR_ON);
            materials.put(152, Material.DAYLIGHT_DETECTOR);
            materials.put(153, Material.REDSTONE_BLOCK);
            materials.put(154, Material.QUARTZ_ORE);
            materials.put(155, Material.HOPPER);
            materials.put(156, Material.QUARTZ_BLOCK);
            materials.put(157, Material.QUARTZ_STAIRS);
            materials.put(158, Material.ACTIVATOR_RAIL);
            materials.put(159, Material.DROPPER);
            materials.put(160, Material.STAINED_CLAY);
            materials.put(161, Material.STAINED_GLASS_PANE);
            materials.put(162, Material.LEAVES_2);
            materials.put(163, Material.LOG_2);
            materials.put(164, Material.ACACIA_STAIRS);
            materials.put(165, Material.DARK_OAK_STAIRS);
            materials.put(166, Material.HAY_BLOCK);
            materials.put(167, Material.CARPET);
            materials.put(168, Material.HARD_CLAY);
            materials.put(169, Material.COAL_BLOCK);
            materials.put(170, Material.PACKED_ICE);
            materials.put(171, Material.DOUBLE_PLANT);
            materials.put(172, Material.IRON_SPADE);
            materials.put(173, Material.IRON_PICKAXE);
            materials.put(174, Material.IRON_AXE);
            materials.put(175, Material.FLINT_AND_STEEL);
            materials.put(176, Material.APPLE);
            materials.put(177, Material.BOW);
            materials.put(178, Material.ARROW);
            materials.put(179, Material.COAL);
            materials.put(180, Material.DIAMOND);
            materials.put(181, Material.IRON_INGOT);
            materials.put(182, Material.GOLD_INGOT);
            materials.put(183, Material.IRON_SWORD);
            materials.put(184, Material.WOOD_SWORD);
            materials.put(185, Material.WOOD_SPADE);
            materials.put(186, Material.WOOD_PICKAXE);
            materials.put(187, Material.WOOD_AXE);
            materials.put(188, Material.STONE_SWORD);
            materials.put(189, Material.STONE_SPADE);
            materials.put(190, Material.STONE_PICKAXE);
            materials.put(191, Material.STONE_AXE);
            materials.put(192, Material.DIAMOND_SWORD);
            materials.put(193, Material.DIAMOND_SPADE);
            materials.put(194, Material.DIAMOND_PICKAXE);
            materials.put(195, Material.DIAMOND_AXE);
            materials.put(196, Material.STICK);
            materials.put(197, Material.BOWL);
            materials.put(198, Material.MUSHROOM_SOUP);
            materials.put(199, Material.GOLD_SWORD);
            materials.put(200, Material.GOLD_SPADE);
            materials.put(201, Material.GOLD_PICKAXE);
            materials.put(202, Material.GOLD_AXE);
            materials.put(203, Material.STRING);
            materials.put(204, Material.FEATHER);
            materials.put(205, Material.SULPHUR);
            materials.put(206, Material.WOOD_HOE);
            materials.put(207, Material.STONE_HOE);
            materials.put(208, Material.IRON_HOE);
            materials.put(209, Material.DIAMOND_HOE);
            materials.put(210, Material.GOLD_HOE);
            materials.put(211, Material.SEEDS);
            materials.put(212, Material.WHEAT);
            materials.put(213, Material.BREAD);
            materials.put(214, Material.LEATHER_HELMET);
            materials.put(215, Material.LEATHER_CHESTPLATE);
            materials.put(216, Material.LEATHER_LEGGINGS);
            materials.put(217, Material.LEATHER_BOOTS);
            materials.put(218, Material.CHAINMAIL_HELMET);
            materials.put(219, Material.CHAINMAIL_CHESTPLATE);
            materials.put(220, Material.CHAINMAIL_LEGGINGS);
            materials.put(221, Material.CHAINMAIL_BOOTS);
            materials.put(222, Material.IRON_HELMET);
            materials.put(223, Material.IRON_CHESTPLATE);
            materials.put(224, Material.IRON_LEGGINGS);
            materials.put(225, Material.IRON_BOOTS);
            materials.put(226, Material.DIAMOND_HELMET);
            materials.put(227, Material.DIAMOND_CHESTPLATE);
            materials.put(228, Material.DIAMOND_LEGGINGS);
            materials.put(229, Material.DIAMOND_BOOTS);
            materials.put(230, Material.GOLD_HELMET);
            materials.put(231, Material.GOLD_CHESTPLATE);
            materials.put(232, Material.GOLD_LEGGINGS);
            materials.put(233, Material.GOLD_BOOTS);
            materials.put(234, Material.FLINT);
            materials.put(235, Material.PORK);
            materials.put(236, Material.GRILLED_PORK);
            materials.put(237, Material.PAINTING);
            materials.put(238, Material.GOLDEN_APPLE);
            materials.put(239, Material.SIGN);
            materials.put(240, Material.WOOD_DOOR);
            materials.put(241, Material.BUCKET);
            materials.put(242, Material.WATER_BUCKET);
            materials.put(243, Material.LAVA_BUCKET);
            materials.put(244, Material.MINECART);
            materials.put(245, Material.SADDLE);
            materials.put(246, Material.IRON_DOOR);
            materials.put(247, Material.REDSTONE);
            materials.put(248, Material.SNOW_BALL);
            materials.put(249, Material.BOAT);
            materials.put(250, Material.LEATHER);
            materials.put(251, Material.MILK_BUCKET);
            materials.put(252, Material.CLAY_BRICK);
            materials.put(253, Material.CLAY_BALL);
            materials.put(254, Material.SUGAR_CANE);
            materials.put(255, Material.PAPER);
            materials.put(256, Material.BOOK);
            materials.put(257, Material.SLIME_BALL);
            materials.put(258, Material.STORAGE_MINECART);
            materials.put(259, Material.POWERED_MINECART);
            materials.put(260, Material.EGG);
            materials.put(261, Material.COMPASS);
            materials.put(262, Material.FISHING_ROD);
            materials.put(263, Material.WATCH);
            materials.put(264, Material.GLOWSTONE_DUST);
            materials.put(265, Material.RAW_FISH);
            materials.put(266, Material.COOKED_FISH);
            materials.put(267, Material.INK_SACK);
            materials.put(268, Material.BONE);
            materials.put(269, Material.SUGAR);
            materials.put(270, Material.CAKE);
            materials.put(271, Material.BED);
            materials.put(272, Material.DIODE);
            materials.put(273, Material.COOKIE);
            materials.put(274, Material.MAP);
            materials.put(275, Material.SHEARS);
            materials.put(276, Material.MELON);
            materials.put(277, Material.PUMPKIN_SEEDS);
            materials.put(278, Material.MELON_SEEDS);
            materials.put(279, Material.RAW_BEEF);
            materials.put(280, Material.COOKED_BEEF);
            materials.put(281, Material.RAW_CHICKEN);
            materials.put(282, Material.COOKED_CHICKEN);
            materials.put(283, Material.ROTTEN_FLESH);
            materials.put(284, Material.ENDER_PEARL);
            materials.put(285, Material.BLAZE_ROD);
            materials.put(286, Material.GHAST_TEAR);
            materials.put(287, Material.GOLD_NUGGET);
            materials.put(288, Material.NETHER_STALK);
            materials.put(289, Material.POTION);
            materials.put(290, Material.GLASS_BOTTLE);
            materials.put(291, Material.SPIDER_EYE);
            materials.put(292, Material.FERMENTED_SPIDER_EYE);
            materials.put(293, Material.BLAZE_POWDER);
            materials.put(294, Material.MAGMA_CREAM);
            materials.put(295, Material.BREWING_STAND_ITEM);
            materials.put(296, Material.CAULDRON_ITEM);
            materials.put(297, Material.EYE_OF_ENDER);
            materials.put(298, Material.SPECKLED_MELON);
            materials.put(299, Material.MONSTER_EGG);
            materials.put(300, Material.EXP_BOTTLE);
            materials.put(301, Material.FIREBALL);
            materials.put(302, Material.BOOK_AND_QUILL);
            materials.put(303, Material.WRITTEN_BOOK);
            materials.put(304, Material.EMERALD);
            materials.put(305, Material.ITEM_FRAME);
            materials.put(306, Material.FLOWER_POT_ITEM);
            materials.put(307, Material.CARROT_ITEM);
            materials.put(308, Material.POTATO_ITEM);
            materials.put(309, Material.BAKED_POTATO);
            materials.put(310, Material.POISONOUS_POTATO);
            materials.put(311, Material.EMPTY_MAP);
            materials.put(312, Material.GOLDEN_CARROT);
            materials.put(313, Material.SKULL_ITEM);
            materials.put(314, Material.CARROT_STICK);
            materials.put(315, Material.NETHER_STAR);
            materials.put(316, Material.PUMPKIN_PIE);
            materials.put(317, Material.FIREWORK);
            materials.put(318, Material.FIREWORK_CHARGE);
            materials.put(319, Material.ENCHANTED_BOOK);
            materials.put(320, Material.REDSTONE_COMPARATOR);
            materials.put(321, Material.NETHER_BRICK_ITEM);
            materials.put(322, Material.QUARTZ);
            materials.put(323, Material.EXPLOSIVE_MINECART);
            materials.put(324, Material.HOPPER_MINECART);
            materials.put(325, Material.IRON_BARDING);
            materials.put(326, Material.GOLD_BARDING);
            materials.put(327, Material.DIAMOND_BARDING);
            materials.put(328, Material.LEASH);
            materials.put(329, Material.NAME_TAG);
            materials.put(330, Material.COMMAND_MINECART);
            materials.put(331, Material.GOLD_RECORD);
            materials.put(332, Material.GREEN_RECORD);
            materials.put(333, Material.RECORD_3);
            materials.put(334, Material.RECORD_4);
            materials.put(335, Material.RECORD_5);
            materials.put(336, Material.RECORD_6);
            materials.put(337, Material.RECORD_7);
            materials.put(338, Material.RECORD_8);
            materials.put(339, Material.RECORD_9);
            materials.put(340, Material.RECORD_10);
            materials.put(341, Material.RECORD_11);
            materials.put(342, Material.RECORD_12);
            enchantments.put("PROTECTION_ENVIRONMENTAL", 0);

            enchantments.put("DIG_SPEED", 1);

            enchantments.put("PROTECTION_FIRE", 2);

            enchantments.put("SILK_TOUCH", 3);

            enchantments.put("PROTECTION_FALL", 4);

            enchantments.put("DURABILITY", 5);

            enchantments.put("PROTECTION_EXPLOSIONS", 6);

            enchantments.put("LOOT_BONUS_BLOCKS", 7);

            enchantments.put("PROTECTION_PROJECTILE", 8);

            enchantments.put("OXYGEN", 9);

            enchantments.put("WATER_WORKER", 10);

            enchantments.put("THORNS", 11);

            enchantments.put("DAMAGE_ALL", 12);

            enchantments.put("ARROW_DAMAGE", 13);

            enchantments.put("DAMAGE_UNDEAD", 14);

            enchantments.put("ARROW_KNOCKBACK", 15);

            enchantments.put("DAMAGE_ARTHROPODS", 16);

            enchantments.put("ARROW_FIRE", 17);

            enchantments.put("KNOCKBACK", 18);

            enchantments.put("ARROW_INFINITE", 19);

            enchantments.put("FIRE_ASPECT", 20);

            enchantments.put("LOOT_BONUS_MOBS", 21);

            enchantments.put("LUCK", 22);

            enchantments.put("LURE", 23);

            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                revenchantments.put(entry.getValue(), entry.getKey());
            }

            for (Map.Entry<Integer, Material> entry : materials.entrySet()) {
                revmaterials.put(entry.getValue(), entry.getKey());
            }
        }
    }
}
