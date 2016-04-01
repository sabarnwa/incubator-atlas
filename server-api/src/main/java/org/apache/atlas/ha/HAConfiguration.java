/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.ha;

import org.apache.atlas.AtlasConstants;
import org.apache.atlas.AtlasException;
import org.apache.atlas.security.SecurityProperties;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.net.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * A wrapper for getting configuration entries related to HighAvailability.
 */
public class HAConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HAConfiguration.class);

    public static final String ATLAS_SERVER_HA_PREFIX = "atlas.server.ha";
    public static final String ATLAS_SERVER_HA_ENABLED_KEY = ATLAS_SERVER_HA_PREFIX + ".enabled";
    public static final String ATLAS_SERVER_ADDRESS_PREFIX = "atlas.server.address.";
    public static final String ATLAS_SERVER_IDS = "atlas.server.ids";
    public static final String HA_ZOOKEEPER_CONNECT = ATLAS_SERVER_HA_PREFIX + ".zookeeper.connect";
    public static final int DEFAULT_ZOOKEEPER_CONNECT_SLEEPTIME_MILLIS = 1000;
    public static final String HA_ZOOKEEPER_RETRY_SLEEPTIME_MILLIS = ATLAS_SERVER_HA_PREFIX + ".zookeeper.retry.sleeptime.ms";
    public static final String HA_ZOOKEEPER_NUM_RETRIES = ATLAS_SERVER_HA_PREFIX + ".zookeeper.num.retries";
    public static final int DEFAULT_ZOOKEEPER_CONNECT_NUM_RETRIES = 3;
    public static final String HA_ZOOKEEPER_SESSION_TIMEOUT_MS = ATLAS_SERVER_HA_PREFIX + ".zookeeper.session.timeout.ms";
    public static final int DEFAULT_ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 20000;

    /**
     * Return whether HA is enabled or not.
     * @param configuration underlying configuration instance
     * @return
     */
    public static boolean isHAEnabled(Configuration configuration) {
        return configuration.getBoolean(ATLAS_SERVER_HA_ENABLED_KEY, false);
    }

    /**
     * Return the ID corresponding to this Atlas instance.
     *
     * The match is done by looking for an ID configured in {@link HAConfiguration#ATLAS_SERVER_IDS} key
     * that has a host:port entry for the key {@link HAConfiguration#ATLAS_SERVER_ADDRESS_PREFIX}+ID where
     * the host is a local IP address and port is set in the system property
     * {@link AtlasConstants#SYSTEM_PROPERTY_APP_PORT}.
     *
     * @param configuration
     * @return
     * @throws AtlasException if no ID is found that maps to a local IP Address or port
     */
    public static String getAtlasServerId(Configuration configuration) throws AtlasException {
        // ids are already trimmed by this method
        String[] ids = configuration.getStringArray(ATLAS_SERVER_IDS);
        String matchingServerId = null;
        int appPort = Integer.parseInt(System.getProperty(AtlasConstants.SYSTEM_PROPERTY_APP_PORT));
        for (String id : ids) {
            String hostPort = configuration.getString(ATLAS_SERVER_ADDRESS_PREFIX +id);
            if (!StringUtils.isEmpty(hostPort)) {
                InetSocketAddress socketAddress;
                try {
                    socketAddress = NetUtils.createSocketAddr(hostPort);
                } catch (Exception e) {
                    LOG.warn("Exception while trying to get socket address for " + hostPort, e);
                    continue;
                }
                if (!socketAddress.isUnresolved()
                        && NetUtils.isLocalAddress(socketAddress.getAddress())
                        && appPort == socketAddress.getPort()) {
                    LOG.info("Found matched server id " + id + " with host port: " + hostPort);
                    matchingServerId = id;
                    break;
                }
            } else {
                LOG.info("Could not find matching address entry for id: " + id);
            }
        }
        if (matchingServerId == null) {
            String msg = String.format("Could not find server id for this instance. " +
                    "Unable to find IDs matching any local host and port binding among %s",
                    StringUtils.join(ids, ","));
            throw new AtlasException(msg);
        }
        return matchingServerId;
    }

    /**
     * Get the web server address that a server instance with the passed ID is bound to.
     *
     * This method uses the property {@link SecurityProperties#TLS_ENABLED} to determine whether
     * the URL is http or https.
     *
     * @param configuration underlying configuration
     * @param serverId serverId whose host:port property is picked to build the web server address.
     * @return
     */
    public static String getBoundAddressForId(Configuration configuration, String serverId) {
        String hostPort = configuration.getString(ATLAS_SERVER_ADDRESS_PREFIX +serverId);
        boolean isSecure = configuration.getBoolean(SecurityProperties.TLS_ENABLED);
        String protocol = (isSecure) ? "https://" : "http://";
        return protocol + hostPort;
    }

    /**
     * A collection of Zookeeper specific configuration that is used by High Availability code
     */
    public static class ZookeeperProperties {
        private String connectString;
        private int retriesSleepTimeMillis;
        private int numRetries;
        private int sessionTimeout;

        public ZookeeperProperties(String connectString, int retriesSleepTimeMillis, int numRetries,
                                   int sessionTimeout) {
            this.connectString = connectString;
            this.retriesSleepTimeMillis = retriesSleepTimeMillis;
            this.numRetries = numRetries;
            this.sessionTimeout = sessionTimeout;
        }

        public String getConnectString() {
            return connectString;
        }

        public int getRetriesSleepTimeMillis() {
            return retriesSleepTimeMillis;
        }

        public int getNumRetries() {
            return numRetries;
        }

        public int getSessionTimeout() {
            return sessionTimeout;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ZookeeperProperties that = (ZookeeperProperties) o;

            if (retriesSleepTimeMillis != that.retriesSleepTimeMillis) return false;
            if (numRetries != that.numRetries) return false;
            if (sessionTimeout != that.sessionTimeout) return false;
            return !(connectString != null ? !connectString.equals(that.connectString) : that.connectString != null);

        }

        @Override
        public int hashCode() {
            int result = connectString != null ? connectString.hashCode() : 0;
            result = 31 * result + retriesSleepTimeMillis;
            result = 31 * result + numRetries;
            result = 31 * result + sessionTimeout;
            return result;
        }
    }

    public static ZookeeperProperties getZookeeperProperties(Configuration configuration) {
        String zookeeperConnectString = configuration.getString("atlas.kafka.zookeeper.connect");
        if (configuration.containsKey(HA_ZOOKEEPER_CONNECT)) {
            zookeeperConnectString = configuration.getString(HA_ZOOKEEPER_CONNECT);
        }

        int retriesSleepTimeMillis = configuration.getInt(HA_ZOOKEEPER_RETRY_SLEEPTIME_MILLIS,
                DEFAULT_ZOOKEEPER_CONNECT_SLEEPTIME_MILLIS);

        int numRetries = configuration.getInt(HA_ZOOKEEPER_NUM_RETRIES, DEFAULT_ZOOKEEPER_CONNECT_NUM_RETRIES);

        int sessionTimeout = configuration.getInt(HA_ZOOKEEPER_SESSION_TIMEOUT_MS,
                DEFAULT_ZOOKEEPER_SESSION_TIMEOUT_MILLIS);
        return new ZookeeperProperties(zookeeperConnectString, retriesSleepTimeMillis, numRetries, sessionTimeout);
    }
}