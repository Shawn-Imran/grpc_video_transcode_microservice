package com.example.transcoder.model;

import lombok.Data;
import lombok.Builder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a video transcoding job.
 */
@Data
@Builder
public class TranscodeJob {
    
    /**
     * Unique job identifier.
     */
    private String jobId;
    
    /**
     * ID of the original video.
     */
    private String videoId;
    
    /**
     * Path to the input video file.
     */
    private Path inputPath;
    
    /**
     * Path to the output directory.
     */
    private Path outputDirectory;
    
    /**
     * List of output formats to generate.
     */
    private List<VideoFormat> outputFormats;
    
    /**
     * Output container format (e.g., "mp4").
     */
    private String outputContainer;
    
    /**
     * Additional transcoding options.
     */
    private Options options;
    
    /**
     * Current status of the job.
     */
    private JobStatus status;
    
    /**
     * Error message (if job failed).
     */
    private String errorMessage;
    
    /**
     * Video metadata.
     */
    private VideoMetadata metadata;
    
    /**
     * Time when job was created.
     */
    private Instant createdAt;
    
    /**
     * Time when job started processing.
     */
    private Instant startedAt;
    
    /**
     * Time when job completed/failed/cancelled.
     */
    private Instant completedAt;
    
    /**
     * Overall progress percentage (0-100).
     */
    @Builder.Default
    private AtomicInteger progress = new AtomicInteger(0);
    
    /**
     * Current processing stage.
     */
    private String currentStage;
    
    /**
     * List of generated output files.
     */
    @Builder.Default
    private List<OutputFile> outputFiles = new ArrayList<>();
    
    /**
     * Estimated time remaining in seconds.
     */
    private int estimatedTimeRemaining;

    /**
     * Creates a new transcoding job with default values.
     *
     * @param videoId ID of the video to transcode
     * @param inputPath Path to the input video file
     * @return New TranscodeJob instance
     */
    public static TranscodeJob createNew(String videoId, Path inputPath) {
        return TranscodeJob.builder()
                .jobId(UUID.randomUUID().toString())
                .videoId(videoId)
                .inputPath(inputPath)
                .status(JobStatus.QUEUED)
                .createdAt(Instant.now())
                .outputFormats(new ArrayList<>())
                .build();
    }

    /**
     * Adds an output format to this job.
     *
     * @param format Format to add
     */
    public void addOutputFormat(VideoFormat format) {
        if (outputFormats == null) {
            outputFormats = new ArrayList<>();
        }
        outputFormats.add(format);
    }

    /**
     * Adds a generated output file.
     *
     * @param outputFile OutputFile to add
     */
    public void addOutputFile(OutputFile outputFile) {
        outputFiles.add(outputFile);
    }

    /**
     * Updates the job's progress percentage.
     *
     * @param percent Progress percentage (0-100)
     * @param stage Current processing stage
     */
    public void updateProgress(int percent, String stage) {
        progress.set(percent);
        currentStage = stage;
    }

    /**
     * Marks the job as in progress.
     */
    public void markInProgress() {
        status = JobStatus.IN_PROGRESS;
        startedAt = Instant.now();
    }

    /**
     * Marks the job as completed.
     */
    public void markCompleted() {
        status = JobStatus.COMPLETED;
        completedAt = Instant.now();
        progress.set(100);
    }

    /**
     * Marks the job as failed with an error message.
     *
     * @param error Error message
     */
    public void markFailed(String error) {
        status = JobStatus.FAILED;
        completedAt = Instant.now();
        errorMessage = error;
    }

    /**
     * Marks the job as cancelled.
     */
    public void markCancelled() {
        status = JobStatus.CANCELLED;
        completedAt = Instant.now();
    }

    /**
     * Additional options for transcoding.
     */
    @Data
    @Builder
    public static class Options {
        private String audioCodec;
        private int audioBitrate;
        private float frameRate;
        private boolean twoPass;
        private int crf;
    }
}
