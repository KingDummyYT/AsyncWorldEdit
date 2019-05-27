/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * Copyright (c) 2016, SBPrime <https://github.com/SBPrime/>
 * Copyright (c) AsyncWorldEdit contributors
 *
 * All rights reserved.
 *
 * Redistribution in source, use in source and binary forms, with or without
 * modification, are permitted free of charge provided that the following 
 * conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 * 2.  Redistributions of source code, with or without modification, in any form
 *     other then free of charge is not allowed,
 * 3.  Redistributions of source code, with tools and/or scripts used to build the 
 *     software is not allowed,
 * 4.  Redistributions of source code, with information on how to compile the software
 *     is not allowed,
 * 5.  Providing information of any sort (excluding information from the software page)
 *     on how to compile the software is not allowed,
 * 6.  You are allowed to build the software for your personal use,
 * 7.  You are allowed to build the software using a non public build server,
 * 8.  Redistributions in binary form in not allowed.
 * 9.  The original author is allowed to redistrubute the software in bnary form.
 * 10. Any derived work based on or containing parts of this software must reproduce
 *     the above copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided with the
 *     derived work.
 * 11. The original author of the software is allowed to change the license
 *     terms or the entire license of the software as he sees fit.
 * 12. The original author of the software is allowed to sublicense the software
 *     or its parts using any license terms he sees fit.
 * 13. By contributing to this project you agree that your contribution falls under this
 *     license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.primesoft.asyncworldedit.platform.bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.primesoft.asyncworldedit.core.ChunkWatch;

/**
 *
 * @author SBPrime
 */
class BukkitChunkWatcher extends ChunkWatch implements Listener {

    private final static int HOLD_CHUNK = 5000;

    private final Plugin m_plugin;

    private final Map<String, Map<Long, Long>> m_forceload = new HashMap<>();

    private final Queue<ForceLoadEntry> m_forceLoadQueue = new ConcurrentLinkedQueue<>();
    private final Queue<ForceLoadEntry> m_forceUnLoadQueue = new ConcurrentLinkedQueue<>();

    private final BukkitScheduler m_scheduler;
    private final Server m_server;

    private long m_lastAccess;

    public BukkitChunkWatcher(Plugin plugin) {
        m_plugin = plugin;
        m_server = plugin.getServer();
        m_scheduler = m_server.getScheduler();

        m_scheduler.runTaskTimer(plugin, this::forceLoadProcessor, 1, 1);
    }

    private void forceLoadProcessor() {
        long now = System.currentTimeMillis();
        long minTime = now - HOLD_CHUNK;
        /*final Predicate<ForceLoadEntry> test = t -> t.timestamp <= minTime || !t.unloadChunk;
        while (!m_forceLoadQueue.isEmpty() && test.test(m_forceLoadQueue.peek())) {
            final ForceLoadEntry e = m_forceLoadQueue.poll();
            final World world = m_server.getWorld(e.world);
            if (world == null) {
                continue;
            }

            final Chunk chunk = world.getChunkAt(e.cx, e.cz);
            final long coords = encode(e.cx, e.cz);            
            
            if (!e.unloadChunk && !chunk.isForceLoaded() && e.timestamp >= m_lastAccess) {
                chunk.setForceLoaded(true);
                m_forceload.computeIfAbsent(e.world, i -> new HashSet<>()).add(coords);
            } else if (e.unloadChunk) {
                final Set<Long> entries = m_forceload.get(e.world);
                if (!entries.contains(coords) || getReferences(e.world, e.cx, e.cz) > 0) {
                    continue;
                }
                
                entries.remove(coords);
                chunk.setForceLoaded(false);
            }
            
            m_lastAccess = e.timestamp;
        }*/
        while (!m_forceLoadQueue.isEmpty()) {
            final ForceLoadEntry e = m_forceLoadQueue.poll();

            final World world = m_server.getWorld(e.world);
            if (world == null) {
                continue;
            }

            final Chunk chunk = world.getChunkAt(e.cx, e.cz);
            final long coords = encode(e.cx, e.cz);
            
            Map<Long, Long> chunkEntry = m_forceload.computeIfAbsent(e.world, i -> new HashMap<>());
            if (!chunk.isForceLoaded()) {
                chunkEntry.put(coords, now);
                chunk.setForceLoaded(true);
            } else if (chunkEntry.containsKey(coords)) {
                chunkEntry.put(coords, now);
            }
        }
        
        while (!m_forceUnLoadQueue.isEmpty()) {
            final ForceLoadEntry e = m_forceUnLoadQueue.peek();            
            
            Map<Long, Long> chunkEntry = m_forceload.get(e.world);
            if (chunkEntry == null) {
                m_forceUnLoadQueue.poll();
                continue;
            }
            
            final long coords = encode(e.cx, e.cz);
            Long value = chunkEntry.get(coords);
            if (value == null) {
                m_forceUnLoadQueue.poll();
                continue;
            }
            
            if (value >= now - HOLD_CHUNK) {
                break;
            }
            
            m_forceUnLoadQueue.poll();
            chunkEntry.remove(coords);
            
            final World world = m_server.getWorld(e.world);
            if (world == null) {
                continue;
            }

            final Chunk chunk = world.getChunkAt(e.cx, e.cz);
            if (chunk == null) {
                continue;
            }
            chunk.setForceLoaded(false);
        }
    }

    @EventHandler
    public void onChunkLoadEvent(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        Integer cx = chunk.getX();
        Integer cz = chunk.getZ();
        String worldName = chunk.getWorld().getName();

        chunkLoaded(worldName, cx, cz);
    }

    @EventHandler
    public void onChunkUnloadEvent(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();

        int cx = chunk.getX();
        int cz = chunk.getZ();
        String worldName = chunk.getWorld().getName();
        chunkUnloading(worldName, cx, cz);
    }

    @Override
    public void registerEvents() {
        Server server = m_plugin.getServer();

        server.getPluginManager().registerEvents(this, m_plugin);
        for (World w : server.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                chunkLoaded(w.getName(), c.getX(), c.getZ());
            }
        }

    }

    @Override
    protected boolean doLoadChunk(int cx, int cz, String worldName) {
        World w = m_plugin.getServer().getWorld(worldName);
        if (w == null) {
            return false;
        }

        return w.getChunkAt(cx, cz) != null;
    }

    @Override
    protected final boolean supportUnloadCancel() {
        return false;
    }

    @Override
    protected void forceloadOff(String world, int cx, int cz) {
        m_forceUnLoadQueue.add(new ForceLoadEntry(world, cx, cz));
    }

    @Override
    protected void forceloadOn(String world, int cx, int cz) {
        m_forceLoadQueue.add(new ForceLoadEntry(world, cx, cz));
    }

    @Override
    public void clear() {
        super.clear();

        /*
        for (Map.Entry<String, Map<Long, Boolean>> entry : m_forceload.entrySet()) {
            final String world = entry.getKey();
            m_scheduler.runTask(m_plugin, () -> {
                World w = m_server.getWorld(world);
                if (w == null) {
                    return;
                }
                for (Map.Entry<Long, Boolean> chunkEntry : entry.getValue().entrySet()) {
                    if (chunkEntry.getValue()) {
                        long c = chunkEntry.getKey();
                        Chunk chunk = w.getChunkAt((int) (c >> 32), (int) c);
                        if (chunk != null) {
                            chunk.setForceLoaded(false);
                        }
                    }
                }
            });
        }
         */
        m_forceload.clear();
    }

    private static class ForceLoadEntry {

        public final String world;
        public final int cx;
        public final int cz;
        public final long timestamp;

        ForceLoadEntry(String world, int cx, int cz) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
