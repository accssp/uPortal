/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.events.aggr;

import java.util.Date;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.TableGenerator;
import javax.persistence.Version;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
@Entity
@Table(name = "UP_EVENT_AGGR_STATUS")
@SequenceGenerator(name = "UP_EVENT_AGGR_STATUS_GEN", sequenceName = "UP_EVENT_AGGR_STATUS_SEQ", allocationSize = 10)
@TableGenerator(name = "UP_EVENT_AGGR_STATUS_GEN", pkColumnValue = "UP_EVENT_AGGR_STATUS", allocationSize = 10)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
class EventAggregatorStatusImpl implements IEventAggregatorStatus {
    @Id
    @GeneratedValue(generator = "UP_EVENT_AGGR_STATUS_GEN")
    @Column(name = "ID")
    private final long id;

    @Version
    @Column(name = "ENTITY_VERSION")
    private final long entityVersion;
    
    @NaturalId
    @Column(name = "PROCESSING_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private final ProcessingType processingType;
    
    @Column(name = "SERVER_NAME", length = 200)
    private String serverName;
    
    @Column(name="LAST_START")
    private Date lastStart;
    
    @Column(name="LAST_END")
    private Date lastEnd;
    
    @Column(name="LAST_EVENT_DATE")
    private Date lastEventDate;

    @SuppressWarnings("unused")
    private EventAggregatorStatusImpl() {
        this.id = -1;
        this.entityVersion = -1;
        this.processingType = null;
    }

    EventAggregatorStatusImpl(ProcessingType processingType) {
        this.id = -1;
        this.entityVersion = -1;
        this.processingType = processingType;
    }

    @Override
    public String getServerName() {
        return this.serverName;
    }

    @Override
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    @Override
    public Date getLastStart() {
        return this.lastStart;
    }

    @Override
    public void setLastStart(Date lastStart) {
        this.lastStart = lastStart;
    }

    @Override
    public Date getLastEnd() {
        return this.lastEnd;
    }

    @Override
    public void setLastEnd(Date lastEnd) {
        this.lastEnd = lastEnd;
    }

    @Override
    public Date getLastEventDate() {
        return this.lastEventDate;
    }

    @Override
    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
    }

    @Override
    public ProcessingType getProcessingType() {
        return this.processingType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.processingType == null) ? 0 : this.processingType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventAggregatorStatusImpl other = (EventAggregatorStatusImpl) obj;
        if (this.processingType != other.processingType)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "EventAggregatorStatusImpl [id=" + this.id + ", entityVersion=" + this.entityVersion
                + ", processingType=" + this.processingType + ", serverName=" + this.serverName + ", lastStart="
                + this.lastStart + ", lastEnd=" + this.lastEnd + ", lastEventDate=" + this.lastEventDate + "]";
    }
}
