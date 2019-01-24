/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * Copyright (c) 2014, SBPrime <https://github.com/SBPrime/>
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
package org.primesoft.asyncworldedit.worldedit.entity;

import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.Location;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primesoft.asyncworldedit.injector.wedev.entity._Entity;

/**
 *
 * @author SBPrime
 */
public class EntityLazyWrapper implements Entity, _Entity {

    private final static Method s_setLocation;

    static {
        Class<?> clsEntity = Entity.class;
        Method m;
        try {
            m = clsEntity.getMethod("setLocation", Location.class);
        } catch (Exception ex) {
            m = null;
            // Ignore
        }

        s_setLocation = m;
    }

    /**
     * The wrapped entity
     */
    private Entity m_entity;
    private _Entity m_entityDev;

    /**
     * Is the entity removed
     */
    private boolean m_isRemoved;

    private Location m_defaultLocation;

    private BaseEntity m_defaultState;

    private Extent m_defaultExtent;

    public EntityLazyWrapper(Location location, Extent extent) {
        m_isRemoved = false;
        m_defaultExtent = extent;
        m_defaultLocation = location;
        m_defaultState = null;
        m_entity = null;
        m_entityDev = null;
    }

    @Override
    public BaseEntity getState() {
        Entity entity = m_entity;
        m_defaultState = entity != null ? entity.getState() : m_defaultState;
        return m_defaultState;
    }

    @Override
    public Location getLocation() {
        Entity entity = m_entity;
        m_defaultLocation = entity != null ? entity.getLocation() : m_defaultLocation;
        return m_defaultLocation;
    }

    @Override
    public Extent getExtent() {
        Entity entity = m_entity;
        m_defaultExtent = entity != null ? entity.getExtent() : m_defaultExtent;
        return m_defaultExtent;
    }

    @Override
    public boolean remove() {
        Entity entity = m_entity;

        if (entity == null) {
            m_isRemoved = true;
            return true;
        }

        m_isRemoved = entity.remove();
        return m_isRemoved;
    }

    @Override
    public <T> T getFacet(Class<? extends T> type) {
        Entity entity = m_entity;

        return entity != null ? entity.getFacet(type) : null;
    }

    /**
     * Sets the wrapped entity
     *
     * @param entity
     */
    public void setEntity(Entity entity) {
        if (m_isRemoved) {
            entity.remove();
            return;
        }

        m_entity = entity;
        if (m_entity instanceof _Entity) {
            m_entityDev = (_Entity) entity;
        } else if (s_setLocation != null) {
            m_entityDev = new ReflectionEntity(entity, s_setLocation);
        } else {
            m_entityDev = null;
        }
    }

    @Override
    public boolean setLocation(Location lctn) {
        if (m_entityDev == null) {
            return true;
        }

        return m_entityDev.setLocation(lctn);
    }

    private static class ReflectionEntity implements _Entity {

        private final Entity m_target;
        private final Method m_method;

        public ReflectionEntity(Entity target, Method m) {
            m_target = target;
            m_method = m;
        }

        @Override
        public boolean setLocation(Location lctn) {
            try {
                return (boolean)m_method.invoke(m_target, lctn);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
