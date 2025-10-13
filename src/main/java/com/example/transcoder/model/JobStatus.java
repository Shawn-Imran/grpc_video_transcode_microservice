package com.example.transcoder.model;

/**
 * Enum representing the status of a transcoding job.
 */
public enum JobStatus {
    UNKNOWN,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

