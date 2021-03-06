/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.processor;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.Collectors;

import org.testng.annotations.Test;

import com.codahale.metrics.Meter;
import com.salesforce.pyplyn.model.Load;
import com.salesforce.pyplyn.model.LoadImpl;
import com.salesforce.pyplyn.model.Transmutation;
import com.salesforce.pyplyn.status.SystemStatus;

/**
 * Test class
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 3.0
 */
public class AbstractMeteredLoadProcessorTest {
    @Test
    public void testProcessOneElement() throws Exception {
        // ARRANGE
        AbstractMeteredLoadProcessorImpl processor = new AbstractMeteredLoadProcessorImpl(1, "result", 1, "destination");

        Transmutation result = mock(Transmutation.class);
        doReturn("result").when(result).name(); // FindBugs: RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT - IGNORE
        List<Transmutation> transmutations = singletonList(result);

        List<Load> destinations = singletonList(new LoadImpl("destination"));

        // ACT
        List<Boolean> processResult = processor.execute(transmutations, destinations);

        // ASSERT
        assertThat(processResult, hasSize(1));
        assertThat(processResult.stream().findAny().get(), is(Boolean.TRUE));
    }

    @Test
    public void testProcessNoElements() throws Exception {
        // ARRANGE
        AbstractMeteredLoadProcessorImpl processor = new AbstractMeteredLoadProcessorImpl(1, "result", 0, null);

        Transmutation result = mock(Transmutation.class);
        doReturn("result").when(result).name(); // FindBugs: RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT - IGNORE
        List<Transmutation> transmutations = singletonList(result);

        // ACT
        List<Boolean> processResult = processor.execute(transmutations, emptyList());

        // ASSERT
        assertThat(processResult, hasSize(0));
    }

    @Test
    public void testMetersRetrievedAndMarkIsDelegated() throws Exception {
        // ARRANGE
        SystemStatus systemStatus = mock(SystemStatus.class);

        Meter meter = mock(Meter.class);
        doReturn(meter).when(systemStatus).meter(anyString(), any());

        AbstractMeteredLoadProcessorImpl processor = spy(new AbstractMeteredLoadProcessorImpl(0, null, 0, null));
        processor.setSystemStatus(systemStatus);

        // ACT
        processor.succeeded();
        processor.failed();
        processor.authenticationFailure();

        // ASSERT
        verify(processor, times(3)).meterName(); // FindBugs: RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT - IGNORE
        verify(systemStatus, times(3)).meter(anyString(), any());
        verify(meter, times(3)).mark();
    }

    /**
     * Implementation class
     *   runs assertions in process, since this stage only returns booleans
     */
    private static class AbstractMeteredLoadProcessorImpl extends AbstractMeteredLoadProcessor<LoadImpl> {
        private final int assertNumberOfElements;
        private final String elementName;
        private final int assertNumberOfLoadDestinations;
        private final String destinationName;

        public AbstractMeteredLoadProcessorImpl(int assertNumberOfElements, String elementName, int assertNumberOfLoadDestinations, String destinationName) {
            this.assertNumberOfElements = assertNumberOfElements;
            this.elementName = elementName;
            this.assertNumberOfLoadDestinations = assertNumberOfLoadDestinations;
            this.destinationName = destinationName;
        }

        @Override
        public List<Boolean> process(List<Transmutation> data, List<LoadImpl> destinations) {
            // ASSERT
            assertThat("Expected transformation result(s)", data, hasSize(assertNumberOfElements));
            if (assertNumberOfElements > 0) {
                assertThat("Expected the name to be set", data.stream().findAny().get().name(), is(elementName));
            }

            assertThat("Expected Load destination(s)", destinations, hasSize(assertNumberOfLoadDestinations));
            if (assertNumberOfLoadDestinations > 0) {
                assertThat("Expected the name to be set", destinations.stream().findAny().get().id(), is(destinationName));
            }

            return destinations.stream().map(load -> Boolean.TRUE).collect(Collectors.toList());
        }

        @Override
        protected String meterName() {
            return AbstractMeteredLoadProcessorImpl.class.getSimpleName();
        }

        @Override
        public Class<LoadImpl> filteredType() {
            return LoadImpl.class;
        }
    }
}