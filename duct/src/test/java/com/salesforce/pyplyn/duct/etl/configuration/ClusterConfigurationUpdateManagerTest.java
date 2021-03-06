/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.etl.configuration;

import static com.salesforce.pyplyn.duct.com.salesforce.pyplyn.test.ConfigurationsTestHelper.createCustomConfiguration;
import static com.salesforce.pyplyn.duct.com.salesforce.pyplyn.test.ConfigurationsTestHelper.createFullConfiguration;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.salesforce.pyplyn.configuration.Configuration;
import com.salesforce.pyplyn.duct.com.salesforce.pyplyn.test.AppBootstrapFixtures;

/**
 * Test class
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 3.0
 */
public class ClusterConfigurationUpdateManagerTest {
    private AppBootstrapFixtures fixtures;

    @BeforeMethod
    public void setUp() throws Exception {
        // ARRANGE
        fixtures = new AppBootstrapFixtures();
    }

    @Test
    public void testUpdateConfigurations() throws Exception {
        // ARRANGE
        Configuration configuration1 = createFullConfiguration(100L, false);
        Configuration configuration2 = createCustomConfiguration("argus", "refocus",
                "expression", "name",
                "subject", "aspect",
                100L, false);

        fixtures.configurationProviderReturns(configuration1)
                .clusterReturns(configuration1)
                .clusterMasterNode()
                .initializeFixtures();

        ConfigurationUpdateManager configurationUpdateManager = fixtures.configurationManager();

        // ACT
        configurationUpdateManager.run();
        Set<Configuration> firstSet = configurationUpdateManager.get();

        // simulate an update of the configuration set
        fixtures.configurationProviderReturns(configuration2)
                .clusterReturns(configuration2);
        configurationUpdateManager.run();
        Set<Configuration> secondSet = configurationUpdateManager.get();

        // ASSERT
        assertThat(firstSet, hasSize(1));
        assertThat(secondSet, hasSize(1));
        assertThat(firstSet, not(hasItems(secondSet.toArray(new Configuration[]{}))));
    }

    @Test
    public void testClusterDoesNotUpdateConfigurationsOnSlaveNodes() throws Exception {
        // ARRANGE
        fixtures.clusterReturns()
                .clusterSlaveNode()
                .initializeFixtures();

        ConfigurationUpdateManager configurationUpdateManager = fixtures.configurationManager();

        // ACT
        configurationUpdateManager.run();

        // ASSERT
        verify(fixtures.configurationLoader(), times(0)).load();
    }
}