/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.pyplyn.util.SerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.salesforce.pyplyn.util.CollectionUtils.nullOutByteArray;
import static com.salesforce.pyplyn.util.CollectionUtils.nullableArrayCopy;
import static java.util.Objects.isNull;

/**
 * Encapsulates the logic for deserializing connector configurations and reading the passwords securely
 * <p/>
 * <p/>Returns a copy of the password bytes, then nulls out passwords from all connectors
 * <p/>
 * <p/>Note: efficiency is traded here in favor of added security.
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 3.0
 */
class InsecurePasswordUtil {
    private static final Logger logger = LoggerFactory.getLogger(InsecurePasswordUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper();


    /**
     * Utilities classes should not be instantiated
     */
    private InsecurePasswordUtil() { }

    /**
     * Always reads a fresh copy of the password bytes from disk
     *   to avoid keeping them in-memory, for security reasons to minimize the attack time required to extract passwords via a heap dump
     */
    static byte[] readPasswordBytes(String connectorFile, String id) {
        try {
            Optional<InsecureConnector> thisConnector =
                    Arrays.stream(mapper.readValue(SerializationHelper.loadResourceInsecure(connectorFile), InsecureConnector[].class))
                            .filter(connector -> connector.id.equals(id))
                            .findAny();

            if (thisConnector.isPresent()) {
                InsecureConnector insecureConnector = thisConnector.get();

                try {
                    // retrieve password from connector definition, or return null
                    return nullableArrayCopy(insecureConnector.password);

                } finally {
                    insecureConnector.clearPassword();
                }
            }

        } catch (IOException e) {
            // this shouldn't happen, unless the source file is moved, deleted, made unavailable, or its syntax is made invalid
            logger.error("Unexpected error deserializing from the connector source", e);

        }

        // return null if connector was not found, or IOException during deserialization
        return null;
    }


    /**
     * Insecure implementation that deserializes the id and password
     * <p/>Used in this class to allow reading the password bytes when so required
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InsecureConnector {
        @JsonProperty(required = true)
        private String id;

        @JsonProperty(required = true)
        private byte[] password;

        /**
         * Null-out password bytes
         */
        private void clearPassword() {
            // nothing to do if password is null
            if (isNull(password)) {
                return;
            }

            // clear out all password bytes
            nullOutByteArray(this.password);

            // and de-reference the object
            this.password = null;
        }
    }
}
