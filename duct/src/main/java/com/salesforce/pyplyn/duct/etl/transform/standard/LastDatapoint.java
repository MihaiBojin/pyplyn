/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.etl.transform.standard;

import static java.util.Objects.nonNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Iterables;
import com.salesforce.pyplyn.annotations.PyplynImmutableStyle;
import com.salesforce.pyplyn.model.Transform;
import com.salesforce.pyplyn.model.Transmutation;

/**
 * Filters out all but the last data point
 * <p/>
 * Applying this transformation effectively transforms a matrix of E {@link com.salesforce.pyplyn.model.Extract}s
 *   by N data points (i.e.: when Extracts return time-series data for more than one time)
 *   into a matrix of Ex1 (where E is the number of Extracts defined in the {@link com.salesforce.pyplyn.configuration.Configuration})
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 3.0
 */
@Value.Immutable
@PyplynImmutableStyle
@JsonDeserialize(as = ImmutableLastDatapoint.class)
@JsonSerialize(as = ImmutableLastDatapoint.class)
@JsonTypeName("LastDatapoint")
public abstract class LastDatapoint implements Transform {
    private static final long serialVersionUID = -2187464148729449576L;

    /**
     * Applies this transformation and returns a new {@link Transmutation} matrix
     */
    @Override
    public List<List<Transmutation>> apply(List<List<Transmutation>> input) {
        return input.stream()
                .map(points -> {
                    // load last result point and return a list containing a single element
                    Transmutation result = Iterables.getLast(points, null);
                    if (nonNull(result)) {
                        return Collections.singletonList(result);
                    }

                    // or return null if no result was found
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
