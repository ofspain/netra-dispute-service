package com.netstra.disputes.idempotency;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class JobIdempotencyKeyResolver implements IdempotencyKeyResolver {

    @Override
    public Optional<String> resolveKey(IdempotencyRequest request) {
        if (!(request.getSource() instanceof JobExecutionMetadata)) {
            return Optional.empty();
        }

        JobExecutionMetadata jobMetadata = (JobExecutionMetadata) request.getSource();

        // Create key from job metadata
        String jobName = jobMetadata.getJobName();
        String jobId = jobMetadata.getJobId();

        String scheduledTime = jobMetadata.getScheduledTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Include job ID in fingerprint
        String jobHash = jobId.hashCode() + "";

        return Optional.of("JOB_" + jobName + "_" + scheduledTime + "_" + jobHash);
    }

    @Override
    public IdempotencyContext.ActorType determineActorType(IdempotencyRequest request) {
        return IdempotencyContext.ActorType.JOB;
    }
}