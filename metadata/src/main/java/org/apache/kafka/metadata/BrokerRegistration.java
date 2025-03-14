/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.metadata;

import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.metadata.RegisterBrokerRecord;
import org.apache.kafka.common.metadata.RegisterBrokerRecord.BrokerEndpoint;
import org.apache.kafka.common.metadata.RegisterBrokerRecord.BrokerFeature;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.image.writer.ImageWriterOptions;
import org.apache.kafka.server.common.ApiMessageAndVersion;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An immutable class which represents broker registrations.
 */
public class BrokerRegistration {
    private static Map<String, Endpoint> listenersToMap(Collection<Endpoint> listeners) {
        Map<String, Endpoint> listenersMap = new HashMap<>();
        for (Endpoint endpoint : listeners) {
            listenersMap.put(endpoint.listenerName().get(), endpoint);
        }
        return listenersMap;
    }

    public static Optional<Long> zkBrokerEpoch(long value) {
        if (value == -1) {
            return Optional.empty();
        } else {
            return Optional.of(value);
        }
    }

    private final int id;
    private final long epoch;
    private final Uuid incarnationId;
    private final Map<String, Endpoint> listeners;
    private final Map<String, VersionRange> supportedFeatures;
    private final Optional<String> rack;
    private final boolean fenced;
    private final boolean inControlledShutdown;
    private final Optional<Long> migratingZkBrokerEpoch;

    // Visible for testing
    public BrokerRegistration(int id,
                              long epoch,
                              Uuid incarnationId,
                              List<Endpoint> listeners,
                              Map<String, VersionRange> supportedFeatures,
                              Optional<String> rack,
                              boolean fenced,
                              boolean inControlledShutdown) {
        this(id, epoch, incarnationId, listenersToMap(listeners), supportedFeatures, rack,
            fenced, inControlledShutdown, Optional.empty());
    }

    public BrokerRegistration(int id,
                              long epoch,
                              Uuid incarnationId,
                              List<Endpoint> listeners,
                              Map<String, VersionRange> supportedFeatures,
                              Optional<String> rack,
                              boolean fenced,
                              boolean inControlledShutdown,
                              Optional<Long> migratingZkBrokerEpoch) {
        this(id, epoch, incarnationId, listenersToMap(listeners), supportedFeatures, rack,
            fenced, inControlledShutdown, migratingZkBrokerEpoch);
    }

    // Visible for testing
    public BrokerRegistration(int id,
                              long epoch,
                              Uuid incarnationId,
                              Map<String, Endpoint> listeners,
                              Map<String, VersionRange> supportedFeatures,
                              Optional<String> rack,
                              boolean fenced,
                              boolean inControlledShutdown) {
        this(id, epoch, incarnationId, listeners, supportedFeatures, rack, fenced, inControlledShutdown, Optional.empty());
    }

    public BrokerRegistration(int id,
                              long epoch,
                              Uuid incarnationId,
                              Map<String, Endpoint> listeners,
                              Map<String, VersionRange> supportedFeatures,
                              Optional<String> rack,
                              boolean fenced,
                              boolean inControlledShutdown,
                              Optional<Long> migratingZkBrokerEpoch) {
        this.id = id;
        this.epoch = epoch;
        this.incarnationId = incarnationId;
        Map<String, Endpoint> newListeners = new HashMap<>(listeners.size());
        for (Entry<String, Endpoint> entry : listeners.entrySet()) {
            if (!entry.getValue().listenerName().isPresent()) {
                throw new IllegalArgumentException("Broker listeners must be named.");
            }
            newListeners.put(entry.getKey(), entry.getValue());
        }
        this.listeners = Collections.unmodifiableMap(newListeners);
        Objects.requireNonNull(supportedFeatures);
        this.supportedFeatures = new HashMap<>(supportedFeatures);
        Objects.requireNonNull(rack);
        this.rack = rack;
        this.fenced = fenced;
        this.inControlledShutdown = inControlledShutdown;
        this.migratingZkBrokerEpoch = migratingZkBrokerEpoch;
    }

    public static BrokerRegistration fromRecord(RegisterBrokerRecord record) {
        Map<String, Endpoint> listeners = new HashMap<>();
        for (BrokerEndpoint endpoint : record.endPoints()) {
            listeners.put(endpoint.name(), new Endpoint(endpoint.name(),
                SecurityProtocol.forId(endpoint.securityProtocol()),
                endpoint.host(),
                endpoint.port()));
        }
        Map<String, VersionRange> supportedFeatures = new HashMap<>();
        for (BrokerFeature feature : record.features()) {
            supportedFeatures.put(feature.name(), VersionRange.of(
                feature.minSupportedVersion(), feature.maxSupportedVersion()));
        }
        return new BrokerRegistration(record.brokerId(),
            record.brokerEpoch(),
            record.incarnationId(),
            listeners,
            supportedFeatures,
            Optional.ofNullable(record.rack()),
            record.fenced(),
            record.inControlledShutdown(),
            zkBrokerEpoch(record.migratingZkBrokerEpoch()));
    }

    public int id() {
        return id;
    }

    public long epoch() {
        return epoch;
    }

    public Uuid incarnationId() {
        return incarnationId;
    }

    public Map<String, Endpoint> listeners() {
        return listeners;
    }

    public Optional<Node> node(String listenerName) {
        Endpoint endpoint = listeners().get(listenerName);
        if (endpoint == null) {
            return Optional.empty();
        }
        return Optional.of(new Node(id, endpoint.host(), endpoint.port(), rack.orElse(null)));
    }

    public Map<String, VersionRange> supportedFeatures() {
        return supportedFeatures;
    }

    public Optional<String> rack() {
        return rack;
    }

    public boolean fenced() {
        return fenced;
    }

    public boolean inControlledShutdown() {
        return inControlledShutdown;
    }

    public boolean isMigratingZkBroker() {
        return migratingZkBrokerEpoch.isPresent();
    }

    public Optional<Long> migratingZkBrokerEpoch() {
        return migratingZkBrokerEpoch;
    }

    public ApiMessageAndVersion toRecord(ImageWriterOptions options) {
        RegisterBrokerRecord registrationRecord = new RegisterBrokerRecord().
            setBrokerId(id).
            setRack(rack.orElse(null)).
            setBrokerEpoch(epoch).
            setIncarnationId(incarnationId).
            setFenced(fenced);

        if (inControlledShutdown) {
            if (options.metadataVersion().isInControlledShutdownStateSupported()) {
                registrationRecord.setInControlledShutdown(true);
            } else {
                options.handleLoss("the inControlledShutdown state of one or more brokers");
            }
        }

        if (migratingZkBrokerEpoch.isPresent()) {
            if (options.metadataVersion().isMigrationSupported()) {
                registrationRecord.setMigratingZkBrokerEpoch(migratingZkBrokerEpoch.get());
            } else {
                options.handleLoss("the isMigratingZkBroker state of one or more brokers");
            }
        }

        for (Entry<String, Endpoint> entry : listeners.entrySet()) {
            Endpoint endpoint = entry.getValue();
            registrationRecord.endPoints().add(new BrokerEndpoint().
                setName(entry.getKey()).
                setHost(endpoint.host()).
                setPort(endpoint.port()).
                setSecurityProtocol(endpoint.securityProtocol().id));
        }

        for (Entry<String, VersionRange> entry : supportedFeatures.entrySet()) {
            registrationRecord.features().add(new BrokerFeature().
                setName(entry.getKey()).
                setMinSupportedVersion(entry.getValue().min()).
                setMaxSupportedVersion(entry.getValue().max()));
        }

        return new ApiMessageAndVersion(registrationRecord,
            options.metadataVersion().registerBrokerRecordVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, epoch, incarnationId, listeners, supportedFeatures,
            rack, fenced, inControlledShutdown, migratingZkBrokerEpoch);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BrokerRegistration)) return false;
        BrokerRegistration other = (BrokerRegistration) o;
        return other.id == id &&
            other.epoch == epoch &&
            other.incarnationId.equals(incarnationId) &&
            other.listeners.equals(listeners) &&
            other.supportedFeatures.equals(supportedFeatures) &&
            other.rack.equals(rack) &&
            other.fenced == fenced &&
            other.inControlledShutdown == inControlledShutdown &&
            other.migratingZkBrokerEpoch.equals(migratingZkBrokerEpoch);
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        bld.append("BrokerRegistration(id=").append(id);
        bld.append(", epoch=").append(epoch);
        bld.append(", incarnationId=").append(incarnationId);
        bld.append(", listeners=[").append(
            listeners.keySet().stream().sorted().
                map(n -> listeners.get(n).toString()).
                collect(Collectors.joining(", ")));
        bld.append("], supportedFeatures={").append(
            supportedFeatures.keySet().stream().sorted().
                map(k -> k + ": " + supportedFeatures.get(k)).
                collect(Collectors.joining(", ")));
        bld.append("}");
        bld.append(", rack=").append(rack);
        bld.append(", fenced=").append(fenced);
        bld.append(", inControlledShutdown=").append(inControlledShutdown);
        bld.append(", migratingZkBrokerEpoch=").append(migratingZkBrokerEpoch.orElse(-1L));
        bld.append(")");
        return bld.toString();
    }

    public BrokerRegistration cloneWith(
        Optional<Boolean> fencingChange,
        Optional<Boolean> inControlledShutdownChange
    ) {
        boolean newFenced = fencingChange.orElse(fenced);
        boolean newInControlledShutdownChange = inControlledShutdownChange.orElse(inControlledShutdown);

        if (newFenced == fenced && newInControlledShutdownChange == inControlledShutdown)
            return this;

        return new BrokerRegistration(
            id,
            epoch,
            incarnationId,
            listeners,
            supportedFeatures,
            rack,
            newFenced,
            newInControlledShutdownChange,
            migratingZkBrokerEpoch
        );
    }
}
