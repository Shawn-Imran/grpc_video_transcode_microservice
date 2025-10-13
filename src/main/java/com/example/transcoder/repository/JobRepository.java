package com.example.transcoder.repository;

import com.example.transcoder.model.JobStatus;
import com.example.transcoder.model.TranscodeJob;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for storing and retrieving transcoding job information.
 * 
 * Note: In a production environment, this would likely be backed by a database.
 * This in-memory implementation is for demonstration purposes.
 */
@Repository
public class JobRepository {

    private final Map<String, TranscodeJob> jobs = new ConcurrentHashMap<>();

    /**
     * Saves a job to the repository.
     *
     * @param job Job to save
     * @return The saved job
     */
    public TranscodeJob save(TranscodeJob job) {
        jobs.put(job.getJobId(), job);
        return job;
    }

    /**
     * Finds a job by its ID.
     *
     * @param jobId Job ID to find
     * @return Optional containing the job if found, empty otherwise
     */
    public Optional<TranscodeJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Finds all jobs for a specific video.
     *
     * @param videoId Video ID
     * @return List of jobs for the video
     */
    public List<TranscodeJob> findByVideoId(String videoId) {
        return jobs.values().stream()
                .filter(job -> job.getVideoId().equals(videoId))
                .collect(Collectors.toList());
    }

    /**
     * Finds all jobs with the specified status.
     *
     * @param status Job status to filter by
     * @return List of jobs with the status
     */
    public List<TranscodeJob> findByStatus(JobStatus status) {
        return jobs.values().stream()
                .filter(job -> job.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Lists all jobs in the repository.
     *
     * @return List of all jobs
     */
    public List<TranscodeJob> findAll() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * Lists jobs with pagination and filtering.
     *
     * @param limit Maximum number of jobs to return
     * @param statusFilter List of statuses to include
     * @param pageToken Pagination token (job ID to start from)
     * @return List of matching jobs
     */
    public List<TranscodeJob> findAllWithFilter(int limit, List<JobStatus> statusFilter, String pageToken) {
        return jobs.values().stream()
                .filter(job -> statusFilter == null || statusFilter.isEmpty() || statusFilter.contains(job.getStatus()))
                .filter(job -> pageToken == null || job.getJobId().compareTo(pageToken) > 0)
                .sorted((j1, j2) -> j1.getCreatedAt().compareTo(j2.getCreatedAt()))
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a job by its ID.
     *
     * @param jobId Job ID to delete
     */
    public void deleteById(String jobId) {
        jobs.remove(jobId);
    }

    /**
     * Gets the count of jobs.
     *
     * @return Number of jobs in the repository
     */
    public int count() {
        return jobs.size();
    }
}

