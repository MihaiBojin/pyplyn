/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.appconfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.salesforce.pyplyn.duct.com.salesforce.pyplyn.test.AppBootstrapFixtures;
import com.salesforce.pyplyn.util.SerializationHelper;

/**
 * Test class
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 5.0
 */
public class AppConfigProviderTest {
    public static final String CORRECT_APP_CONFIG = "/config/correct-app-config.json";
    public static final String INVALID_APP_CONFIG = "/config/invalid-app-config.json";

    private AppBootstrapFixtures fixtures;

    @BeforeMethod
    public void setUp() throws Exception {
        // ARRANGE
        fixtures = new AppBootstrapFixtures();
    }

    @Test
    public void testReadAppConfig() throws Exception {
        // ARRANGE
        ObjectMapper mapper = fixSerializationHelper(fixtures);

        // ACT
        AppConfigProvider configProvider = new AppConfigProvider(CORRECT_APP_CONFIG, mapper);
        AppConfig appConfig = configProvider.get();

        // ASSERT
        assertThat(appConfig.global(), notNullValue());
        assertThat(appConfig.global().configurationsPath(), equalTo("configurationsPath"));
        assertThat(appConfig.global().connectorsPath(), equalTo("connectorsPath"));
        assertThat(appConfig.global().runOnce(), equalTo(false));
        assertThat(appConfig.global().updateConfigurationIntervalMillis(), equalTo(2L));

        assertThat(appConfig.hazelcast(), notNullValue());
        assertThat(appConfig.hazelcast().isEnabled(), equalTo(true));
        assertThat(appConfig.hazelcast().config(), equalTo("config"));

        assertThat(appConfig.alert(), notNullValue());
        assertThat(appConfig.alert().isEnabled(), equalTo(true));
        assertThat(appConfig.alert().checkIntervalMillis(), equalTo(3L));
        assertThat(appConfig.alert().thresholds(), hasEntry("thresholds", 4.0d));
    }

    @Test(expectedExceptions = IOException.class)
    public void testDeserializationError() throws Exception {
        // ARRANGE
        ObjectMapper mapper = fixSerializationHelper(fixtures);

        // ACT/ASSERT
        new AppConfigProvider(INVALID_APP_CONFIG, mapper);
    }

    /**
     * Creates a fixture for {@link SerializationHelper}
     */
    public static ObjectMapper fixSerializationHelper(AppBootstrapFixtures fixtures) {
        Injector injector = fixtures.initializeFixtures().injector();
        return injector.getInstance(ObjectMapper.class);
    }
}