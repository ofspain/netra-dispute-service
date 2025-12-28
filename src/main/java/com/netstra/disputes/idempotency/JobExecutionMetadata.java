package com.netstra.disputes.idempotency;

import java.time.LocalDateTime;
import java.time.ZoneOffset;


import lombok.Getter;
import lombok.Builder;
import lombok.Singular;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Metadata for Spring's @Scheduled jobs
 */
@Getter
@Builder
public class JobExecutionMetadata {

    // Required fields
    private final String jobName;
    private final String jobId;
    private final LocalDateTime scheduledTime;
    private final LocalDateTime actualStartTime;
    private final LocalDateTime fireTime;

    // Optional with defaults
  //  @Builder.Default
    private final String jobGroup;

    @Singular("parameter")
    private final Map<String, Object> parameters;

   // @Builder.Default
    private final String triggerType;  // CRON, FIXED_DELAY, FIXED_RATE
//
//    @Builder.Default
//    private final String triggerSource = "SCHEDULER";

   // @Builder.Default
    private final int retryCount;

   // @Builder.Default
    private String instanceId;

   // @Builder.Default
    private final boolean recovering;

    // Private constructor for builder
    private JobExecutionMetadata(
            String jobName, String jobId,
            LocalDateTime scheduledTime, LocalDateTime actualStartTime,
            LocalDateTime fireTime, String jobGroup,
            Map<String, Object> parameters, String triggerType,
            /*String triggerSource,*/ int retryCount,
            String instanceId, boolean recovering) {

        this.jobName = jobName != null ? jobName : "UNNAMED_JOB";
        this.jobId = jobId != null ? jobId : generateJobId();
        this.scheduledTime = scheduledTime != null ? scheduledTime : LocalDateTime.now();
        this.actualStartTime = actualStartTime != null ? actualStartTime : LocalDateTime.now();
        this.fireTime = fireTime != null ? fireTime : this.actualStartTime;
        this.jobGroup = jobGroup;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.triggerType = triggerType;
//        this.triggerSource = triggerSource;
        this.retryCount = retryCount;
        this.instanceId = instanceId != null ? instanceId : determineInstanceId();
        this.recovering = recovering;
    }

    private static String generateJobId() {
        return "JOB_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String determineInstanceId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            return hostname + "_" + java.lang.management.ManagementFactory
                    .getRuntimeMXBean().getName().split("@")[0];
        } catch (Exception e) {
            return "UNKNOWN_INSTANCE";
        }
    }

    /**
     * Static factory method for quick creation
     */
    public static JobExecutionMetadata create(String jobName) {
        return JobExecutionMetadata.builder()
                .jobName(jobName)
                .build();
    }

    /**
     * For jobs with parameters
     */
    public static JobExecutionMetadata create(String jobName,
                                                    Map<String, Object> parameters) {
        return JobExecutionMetadata.builder()
                .jobName(jobName)
                .parameters(parameters)
                .build();
    }

    /**
     * For retry scenarios
     */
    public JobExecutionMetadata withRetry(int retryCount) {
        return JobExecutionMetadata.builder()
                .jobName(this.jobName)
                .jobId(this.jobId)  // Keep same job ID for retries
                .scheduledTime(this.scheduledTime)
                .actualStartTime(LocalDateTime.now())
                .fireTime(LocalDateTime.now())
                .jobGroup(this.jobGroup)
                .parameters(this.parameters)
                .triggerType(this.triggerType)
//                .triggerSource("RETRY")
                .retryCount(retryCount)
                .instanceId(this.instanceId)
                .recovering(true)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
                "SpringScheduledJobMetadata{jobName='%s', jobId='%s', scheduledTime=%s, " +
                        "triggerType='%s', retryCount=%d, parameters=%s}",
                jobName, jobId, scheduledTime, triggerType, retryCount,
                parameters.isEmpty() ? "none" : parameters.size() + " params");
    }
}
