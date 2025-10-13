package com.example.transcoder.service;

import com.example.transcoder.model.JobStatus;
import com.example.transcoder.model.OutputFile;
import com.example.transcoder.model.TranscodeJob;
import com.example.transcoder.model.VideoFormat;
import com.example.transcoder.model.VideoMetadata;
import com.example.transcoder.repository.JobRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for managing video transcoding operations.
 */
@Service
@Slf4j
public class TranscodeManager {

    @Autowired
    private FFmpegService ffmpegService;

    @Autowired
    private VideoStorageService videoStorageService;

    @Autowired
    private JobRepository jobRepository;

    @Value("${transcode.worker.thread-pool-size:5}")
    private int threadPoolSize;

    @Value("${transcode.default-formats:1080p,720p,480p,360p}")
    private String defaultFormats;

    private Executor transcodeExecutor;

    /**
     * Initializes the transcoding thread pool.
     */
    @PostConstruct
    public void init() {
        transcodeExecutor = Executors.newFixedThreadPool(threadPoolSize);
        log.info("Initialized transcode manager with thread pool size: {}", threadPoolSize);
    }

    /**
     * Creates a new transcoding job.
     *
     * @param videoId ID of the video to transcode
     * @param outputFormats List of output formats to generate
     * @param outputContainer Output container format
     * @param options Additional transcoding options
     * @return The created job, or null if creation failed
     */
    public TranscodeJob createJob(String videoId, List<VideoFormat> outputFormats, 
                                 String outputContainer, TranscodeJob.Options options) {
        // Find the video file
        Path videoPath = videoStorageService.getVideoFile(videoId);
        if (videoPath == null) {
            log.error("Video file not found for ID: {}", videoId);
            return null;
        }

        // Extract video metadata
        VideoMetadata metadata = ffmpegService.extractMetadata(videoPath);
        if (metadata == null) {
            log.error("Failed to extract metadata for video: {}", videoId);
            return null;
        }

        // Create the transcoding job
        TranscodeJob job = TranscodeJob.createNew(videoId, videoPath);
        job.setOutputFormats(outputFormats);
        job.setOutputContainer(outputContainer);
        job.setOptions(options);
        job.setMetadata(metadata);

        // Create output directory
        Path outputDir = videoStorageService.createJobOutputDirectory(job.getJobId());
        job.setOutputDirectory(outputDir);

        // Calculate estimated time (very rough estimate based on duration and formats)
        float durationMinutes = metadata.getDurationSeconds() / 60.0f;
        int estimatedMinutes = Math.round(durationMinutes * outputFormats.size() * 0.5f);
        job.setEstimatedTimeRemaining(estimatedMinutes * 60);

        // Save the job
        jobRepository.save(job);
        log.info("Created transcoding job: {} for video: {}", job.getJobId(), videoId);

        return job;
    }

    /**
     * Starts processing a transcoding job asynchronously.
     *
     * @param jobId ID of the job to process
     * @return CompletableFuture that completes when the job is done
     */
    @Async
    public CompletableFuture<TranscodeJob> processJob(String jobId) {
        Optional<TranscodeJob> jobOpt = jobRepository.findById(jobId);
        if (!jobOpt.isPresent()) {
            log.warn("Job not found: {}", jobId);
            return CompletableFuture.completedFuture(null);
        }

        TranscodeJob job = jobOpt.get();
        job.markInProgress();
        jobRepository.save(job);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting transcoding job: {}", job.getJobId());
                
                // Process each format
                boolean success = true;
                for (int i = 0; i < job.getOutputFormats().size(); i++) {
                    VideoFormat format = job.getOutputFormats().get(i);
                    
                    // Update progress to show which format we're working on
                    int baseProgress = (i * 100) / job.getOutputFormats().size();
                    int nextProgress = ((i + 1) * 100) / job.getOutputFormats().size();
                    job.updateProgress(baseProgress, "Processing " + format.getName());
                    jobRepository.save(job);
                    
                    // Get output path for this format
                    Path outputPath = videoStorageService.getOutputFilePath(
                            job.getJobId(), 
                            job.getVideoId(), 
                            format.getName(), 
                            job.getOutputContainer());
                    
                    // Transcode the video
                    boolean formatSuccess = ffmpegService.transcodeVideo(
                            job, 
                            format, 
                            outputPath, 
                            (percent, message) -> {
                                // Scale progress within this format's portion
                                int scaledProgress = baseProgress + 
                                        (percent * (nextProgress - baseProgress) / 100);
                                job.updateProgress(scaledProgress, message);
                                jobRepository.save(job);
                            });
                    
                    if (!formatSuccess) {
                        success = false;
                        job.markFailed("Failed to transcode format: " + format.getName());
                        jobRepository.save(job);
                        break;
                    }
                    
                    // Create output file record
                    try {
                        long fileSize = Files.size(outputPath);
                        OutputFile outputFile = OutputFile.builder()
                                .format(format.getName())
                                .location(outputPath.toString())
                                .size(fileSize)
                                .duration(job.getMetadata().getDurationSeconds())
                                .bitrate(format.getBitrate())
                                .build();
                        job.addOutputFile(outputFile);
                    } catch (IOException e) {
                        log.error("Error getting output file size: {}", e.getMessage(), e);
                    }
                }
                
                // Update job status
                if (success) {
                    job.markCompleted();
                    log.info("Completed transcoding job: {}", job.getJobId());
                }
                
                jobRepository.save(job);
                return job;
                
            } catch (Exception e) {
                log.error("Error processing transcoding job {}: {}", job.getJobId(), e.getMessage(), e);
                job.markFailed("Error: " + e.getMessage());
                jobRepository.save(job);
                return job;
            }
        }, transcodeExecutor);
    }

    /**
     * Cancels a running job.
     *
     * @param jobId ID of the job to cancel
     * @return True if cancellation was successful
     */
    public boolean cancelJob(String jobId) {
        Optional<TranscodeJob> jobOpt = jobRepository.findById(jobId);
        if (!jobOpt.isPresent()) {
            return false;
        }

        TranscodeJob job = jobOpt.get();
        if (job.getStatus() == JobStatus.COMPLETED || 
            job.getStatus() == JobStatus.FAILED ||
            job.getStatus() == JobStatus.CANCELLED) {
            return false;
        }

        job.markCancelled();
        jobRepository.save(job);
        log.info("Cancelled job: {}", jobId);
        return true;
    }

    /**
     * Gets the status of a job.
     *
     * @param jobId ID of the job
     * @return The job, or null if not found
     */
    public TranscodeJob getJobStatus(String jobId) {
        return jobRepository.findById(jobId).orElse(null);
    }

    /**
     * Parses a comma-separated list of format names into VideoFormat objects.
     *
     * @param formatNames Comma-separated list of format names
     * @return List of VideoFormat objects
     */
    public List<VideoFormat> parseFormats(String formatNames) {
        if (formatNames == null || formatNames.isEmpty()) {
            formatNames = defaultFormats;
        }
        
        return Arrays.stream(formatNames.split(","))
                .map(String::trim)
                .map(VideoFormat::createStandard)
                .collect(Collectors.toList());
    }
}

