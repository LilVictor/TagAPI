/*
 * Copyright 2012 Matt Baxter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kitteh.tag.api;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public abstract class PacketHandler implements IPacketHandler {

    public class ArrayLizt<E> implements List<E> {

        private final List<E> list;
        private final Player owner;

        public ArrayLizt(Player owner, List<E> list) {
            this.list = list;
            this.owner = owner;
        }

        @Override
        public boolean add(E packet) {
            if (PacketHandler.this.handlePacketAdd(packet, this.owner)) {
                return this.list.add(packet);
            }
            return false;
        }

        @Override
        public void add(int index, E packet) {
            if (PacketHandler.this.handlePacketAdd(packet, this.owner)) {
                this.list.add(index, packet);
            }
        }

        @Override
        public boolean addAll(Collection<? extends E> packetPile) {
            boolean changed = false;
            for (final E packet : packetPile) {
                if (PacketHandler.this.handlePacketAdd(packet, this.owner)) {
                    this.list.add(packet);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> packetPile) {
            boolean changed = false;
            for (final E packet : packetPile) {
                if (PacketHandler.this.handlePacketAdd(packet, this.owner)) {
                    this.list.add(index++, packet);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void clear() {
            this.list.clear();
        }

        @Override
        public boolean contains(Object o) {
            return this.list.contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return this.list.containsAll(c);
        }

        @Override
        public E get(int index) {
            return this.list.get(index);
        }

        public List<E> getList() {
            return this.list;
        }

        @Override
        public int indexOf(Object o) {
            return this.list.indexOf(o);
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        @Override
        public Iterator<E> iterator() {
            return this.list.iterator();
        }

        @Override
        public int lastIndexOf(Object o) {
            return this.list.lastIndexOf(o);
        }

        @Override
        public ListIterator<E> listIterator() {
            return this.list.listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            return this.list.listIterator(index);
        }

        @Override
        public E remove(int index) {
            return this.list.remove(index);
        }

        @Override
        public boolean remove(Object o) {
            return this.list.remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return this.list.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return this.list.retainAll(c);
        }

        @Override
        public E set(int index, E packet) {
            if (PacketHandler.this.handlePacketAdd(packet, this.owner)) {
                return this.list.set(index, packet);
            }
            return packet;
        }

        @Override
        public int size() {
            return this.list.size();
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            return this.list.subList(fromIndex, toIndex);
        }

        @Override
        public Object[] toArray() {
            return this.list.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return this.list.toArray(a);
        }

    }

    public class HandlerListener implements Listener {

        private final PacketHandler handler;

        public HandlerListener(PacketHandler handler) {
            this.handler = handler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            this.handler.hookPlayer(event.getPlayer());
        }

    }

    protected final TagHandler handler;
    private final Plugin plugin;
    private final Map<Class<?>, Field> fieldMap = new HashMap<Class<?>, Field>();

    public PacketHandler(TagHandler handler) {
        this.plugin = handler.getPlugin();
        this.handler = handler;
        this.plugin.getServer().getPluginManager().registerEvents(new HandlerListener(this), this.plugin);
        try {
            this.construct();
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Found " + this.getVersion() + " but something is wrong.", e);
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
            return;
        }
    }

    @Override
    public void shutdown() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player != null) {
                this.releasePlayer(player);
            }
        }
    }

    @Override
    public void startup() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.hookPlayer(player);
        }
    }

    protected abstract void construct() throws NoSuchFieldException, SecurityException;

    protected abstract Object getNetworkManager(Player player);

    protected abstract String getQueueField();

    protected abstract String getVersion();

    protected abstract boolean handlePacketAdd(Object o, Player owner);

    protected void hookPlayer(Player player) {
        try {
            this.listSwap(player, true);
        } catch (final Exception e) {
            new TagAPIException("[TagAPI] Failed to inject into networkmanager for " + player.getName(), e).printStackTrace();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void listSwap(Player player, boolean joining) throws IllegalArgumentException, IllegalAccessException {
        final Object nm = this.getNetworkManager(player);
        final Class<?> clazz = nm.getClass();
        if (!this.fieldMap.containsKey(clazz)) {
            try {
                final Field field = clazz.getDeclaredField(this.getQueueField());
                field.setAccessible(true);
                this.fieldMap.put(clazz, field);
            } catch (final Exception e) {
                this.fieldMap.put(clazz, null);
            }
        }
        final Field field = this.fieldMap.get(clazz);
        if (field == null) {
            this.handler.debug("Player " + player.getName() + " has incompatible NetworkManager " + clazz.getCanonicalName());
            return;
        }
        final List<?> old = (List<?>) field.get(nm);
        List<?> replacement = null;
        if (joining && !(old instanceof ArrayLizt)) {
            replacement = new ArrayLizt(player, old);
        }
        if (!joining && (old instanceof ArrayLizt)) {
            replacement = ((ArrayLizt<?>) old).getList();
        }
        if (replacement != null) {
            field.set(nm, replacement);
        }
    }

    protected void releasePlayer(Player player) {
        try {
            this.listSwap(player, false);
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to restore " + player.getName() + ". Could be a problem.", e);
        }
    }

}