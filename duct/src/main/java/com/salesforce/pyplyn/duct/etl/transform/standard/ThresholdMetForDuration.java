/*
 *  Copyright (c) 2016-2017, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see the LICENSE.txt file in repo root
 *    or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.pyplyn.duct.etl.transform.standard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Iterables;
import com.salesforce.pyplyn.duct.etl.transform.standard.Threshold.Type;
import com.salesforce.pyplyn.model.Transform;
import com.salesforce.pyplyn.model.TransformationResult;
import com.salesforce.pyplyn.model.builder.TransformationResultBuilder;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.salesforce.pyplyn.duct.etl.transform.standard.Threshold.Value.*;
import static com.salesforce.pyplyn.duct.etl.transform.standard.Threshold.changeValue;
import static com.salesforce.pyplyn.util.FormatUtils.formatNumber;

/**
 * Determine if the input time series data has matched the specified threshold
 * for the specified critial/warn/info duration, and return
 * critical/warning/info/ok status values respectively
 * 
 * <p/>
 * Applying this transformation effectively transforms a matrix of E
 * {@link com.salesforce.pyplyn.model.Extract}s by N data points (i.e.: when
 * Extracts return time-series data for more than one time) into a matrix of Ex1
 * (where E is the number of Extracts defined in the
 * {@link com.salesforce.pyplyn.configuration.Configuration}) where each of the
 * element is reduced to critical-3, warn-2 or ok-0 based on the duration
 * threshold testing
 *
 * @author Jing Qian &lt;jqian@salesforce.com&gt;
 *
 * @since 6.0
 */
public class ThresholdMetForDuration implements Transform, Serializable {
    private static final long serialVersionUID = -4594665847342745577L;
    private final static String MESSAGE_TEMPLATE = "%s threshold hit by %s, with value=%s %s %.2f, duration longer than %s";


    @JsonProperty(required=true)
    Double threshold;

    @JsonProperty
    Type type;

    @JsonProperty(defaultValue = "0")
    Long criticalDurationMillis;

    @JsonProperty(defaultValue = "0")
    Long warnDurationMillis;

    @JsonProperty(defaultValue = "0")
    Long infoDurationMillis;


    /**
     * Default constructor
     *
     * @param threshold Threshold to match
     * @param type Determines if the duration should be higher or lower than the threshold
     * @param criticalDurationMillis Duration after which the status becomes "critical"
     * @param warnDurationMillis Duration after which the status becomes "warning"
     * @param infoDurationMillis Duration after which the status becomes "info"
     */
    @JsonCreator
    public ThresholdMetForDuration(@JsonProperty("threshold") Double threshold,
                                   @JsonProperty("type") Type type,
                                   @JsonProperty("criticalDurationMillis") Long criticalDurationMillis,
                                   @JsonProperty("warnDurationMillis") Long warnDurationMillis,
                                   @JsonProperty("infoDurationMillis") Long infoDurationMillis) {
        this.threshold = threshold;
        this.type = type;
        this.criticalDurationMillis = criticalDurationMillis;
        this.warnDurationMillis = warnDurationMillis;
        this.infoDurationMillis = infoDurationMillis;
    }

    /**
     * Applies this transformation and returns a new {@link TransformationResult} matrix
     */
    @Override
    public List<List<TransformationResult>> apply(List<List<TransformationResult>> input) {
        return input.stream()
                .map(this::applyThreshold)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());        
    }

    /**
     * Check for the input points, compare each with threshold, if it continue
     *   to pass the threshold for the critical/warn/info duration time period, change
     *   its the value to be critical/warn/info, otherwise, it's ok
     */
    List<TransformationResult> applyThreshold(List<TransformationResult> points) {
        // nothing to do
        if (points.isEmpty()) {
            return null;
        }

        TransformationResult lastPoint = Iterables.getLast(points);
        ZonedDateTime lastPointTS = lastPoint.time();

        // get the timestamp for the critical, warning, info durations
        //   if the millisecond unit is not supported, it will throw UnsupportedTemporalTypeException
        ZonedDateTime infoDurationTS = lastPointTS.minus(infoDurationMillis, ChronoUnit.MILLIS);
        ZonedDateTime warnDurationTS = lastPointTS.minus(warnDurationMillis, ChronoUnit.MILLIS);
        ZonedDateTime criticalDurationTS = lastPointTS.minus(criticalDurationMillis, ChronoUnit.MILLIS);

        ListIterator<TransformationResult> iter = points.listIterator(points.size());
        boolean matchThreshold = true;
        boolean atWarningLevel = false;
        boolean atInfoLevel = false;

        while (iter.hasPrevious() && matchThreshold) {
            TransformationResult result = iter.previous();
            ZonedDateTime pointTS = result.time();

            Number value = result.value();
            matchThreshold = type.matches(value, threshold);

            if (matchThreshold) {
                if (pointTS.compareTo(criticalDurationTS) <= 0) {
                    return Collections.singletonList(appendMessage(changeValue(result, CRIT.value()), CRIT.code(), threshold, criticalDurationMillis));

                } else if (pointTS.compareTo(warnDurationTS) <= 0) {
                    atWarningLevel = true;

                } else if (pointTS.compareTo(infoDurationTS) <= 0) {
                    atInfoLevel = true;
                }

            } else {
                if (pointTS.compareTo(warnDurationTS) <= 0) {
                    return Collections.singletonList(appendMessage(changeValue(result, WARN.value()), WARN.code(), threshold, warnDurationMillis));

                } else if (pointTS.compareTo(infoDurationTS) <= 0) {
                    return Collections.singletonList(appendMessage(changeValue(result, INFO.value()), INFO.code(), threshold, warnDurationMillis));

                } else {
                    return Collections.singletonList(changeValue(result, OK.value())); // OK status
                }
            }
        }

        // critical, warning or info duration value is longer than available input time series
        return atWarningLevel
                ? Collections.singletonList(appendMessage(changeValue(lastPoint, WARN.value()), WARN.code(), threshold, warnDurationMillis))
                : (atInfoLevel
                        ? Collections.singletonList(appendMessage(changeValue(lastPoint, INFO.value()), INFO.code(), threshold, warnDurationMillis))
                        : Collections.singletonList(changeValue(lastPoint, OK.value())));
    }

    /**
     * Appends a message with the explanation of what threshold was hit
     */
    TransformationResult appendMessage(TransformationResult result, String code, Double threshold, long durationMillis) {
        String thresholdHitAlert = String.format(MESSAGE_TEMPLATE, code, result.name(),
                formatNumber(result.originalValue()), type.name(), threshold, convertToTimeDuration(durationMillis));

        return new TransformationResultBuilder(result).metadata((metadata) -> metadata.addMessage(thresholdHitAlert))
                .build();
    }

    /**
     * Convert duration milli to day:hour:min:second dd:hh:mm:ss
     * <p/>omitted any duration that's less than 1 second
     * 
     * @return if less than 1 day, xxh:xxm:xxs
     *         <p/>if more than 1 day, xx days xxh:xxm:xxs
     */
    private String convertToTimeDuration(long milliseconds) {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);
        String hms = String.format("%02dh:%02dm:%02ds",
                TimeUnit.MILLISECONDS.toHours(milliseconds) % TimeUnit.DAYS.toHours(1),
                TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(milliseconds) % TimeUnit.MINUTES.toSeconds(1));
        return (days > 0 ? String.format("%02ddays ", days) : "") + hms;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ThresholdMetForDuration other = (ThresholdMetForDuration) obj;

        if (criticalDurationMillis != null ? !criticalDurationMillis.equals(other.criticalDurationMillis) : other.criticalDurationMillis != null) {
            return false;
        }

        if (warnDurationMillis != null ? !warnDurationMillis.equals(other.warnDurationMillis) : other.warnDurationMillis != null) {
            return false;
        }

        if (infoDurationMillis != null ? !infoDurationMillis.equals(other.warnDurationMillis) : other.infoDurationMillis != null) {
            return false;
        }

        if (threshold != null ? !threshold.equals(other.threshold) : other.threshold != null) {
            return false;
        }

        return type == other.type;
    }

    @Override
    public int hashCode() {
        int result = ((criticalDurationMillis == null) ? 0 : criticalDurationMillis.hashCode());
        result = 31 * result + ((threshold == null) ? 0 : threshold.hashCode());
        result = 31 * result + ((type == null) ? 0 : type.hashCode());
        result = 31 * result + ((warnDurationMillis == null) ? 0 : warnDurationMillis.hashCode());
        result = 31 * result + ((infoDurationMillis == null) ? 0 : infoDurationMillis.hashCode());
        return result;
    }
}
