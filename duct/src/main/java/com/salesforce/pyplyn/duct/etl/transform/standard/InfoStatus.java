/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.etl.transform.standard;

import java.util.List;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.salesforce.pyplyn.annotations.PyplynImmutableStyle;
import com.salesforce.pyplyn.model.ImmutableTransmutation;
import com.salesforce.pyplyn.model.Transform;
import com.salesforce.pyplyn.model.Transmutation;

/**
 * Forces at least a status of INFO, if the status is currently OK
 * <p/>Predefined statuses are 0=OK, 1=INFO, 2=WARN, 3=CRIT
 * <p/>
 * <p/>Note: be careful to either apply this transform after a {@link Threshold},
 *     or when you are sure that the input values are already in a [0-3] range
 *
 * @author Mihai Bojin &lt;mbojin@salesforce.com&gt;
 * @since 3.0
 */
@Value.Immutable
@PyplynImmutableStyle
@JsonDeserialize(as = ImmutableInfoStatus.class)
@JsonSerialize(as = ImmutableInfoStatus.class)
@JsonTypeName("InfoStatus")
public abstract class InfoStatus implements Transform {
    private static final long serialVersionUID = -1927779729819920375L;

    /**
     * Applies this transformation and returns a new {@link Transmutation} matrix
     */
    @Override
    public List<List<Transmutation>> apply(List<List<Transmutation>> input) {
        // for all rows and columns
        return input.stream()
                .map(rows -> rows.stream()
                        .map(point -> {
                            // if the value indicates a status of OK, remap to a status of 1 (INFO)
                            if (point.value().intValue() == 0) {
                                return ImmutableTransmutation.builder().from(point).value(1).build();
                            }

                            // otherwise, return the point as is
                            return point;
                        })
                        .collect(Collectors.toList())
                )
                .collect(Collectors.toList());
    }
}
