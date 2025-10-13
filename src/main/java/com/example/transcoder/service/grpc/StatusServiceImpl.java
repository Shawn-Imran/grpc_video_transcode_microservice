package com.example.transcoder.service.grpc;

import com.example.transcoder.model.OutputFile;
import com.example.transcoder.model.TranscodeJob;
import com.example.transcoder.proto.JobStatus;
import com.example.transcoder.proto.JobStatusRequest;
import com.example.transcoder.proto.JobStatusResponse;
import com.example.transcoder.proto.ListJobsRequest;
import com.example.transcoder.proto.ListJobsResponse;
import com.example.transcoder.proto.StatusServiceGrpc;
import com.example.transcoder.repository.JobRepository;
import com.example.transcoder.service.TranscodeManager;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the gRPC status service.
 */
@GRpcService
@Slf4j
public class StatusServiceImpl extends StatusServiceGrpc.StatusServiceImplBase {

    @Autowired
    private TranscodeManager transcodeManager;

    @Autowired
    private JobRepository jobRepository;

    /**
     * Gets the status of a transcoding job.
     *
     * @param request The status request
     * @param responseObserver Stream observer for sending the response
     */
    @Override
    public void getJobStatus(JobStatusRequest request, StreamObserver<JobStatusResponse> responseObserver) {
        String jobId = request.getJobId();
        log.debug("Getting status for job: {}", jobId);
        
        TranscodeJob job = transcodeManager.getJobStatus(jobId);
        
        if (job == null) {
            responseObserver.onNext(JobStatusResponse.newBuilder()
                    .setJobId(jobId)
                    .setStatus(JobStatus.JOB_UNKNOWN)
                    .setErrorMessage("Job not found")
                    .build());
        } else {
            responseObserver.onNext(createJobStatusResponse(job));
        }
        
        responseObserver.onCompleted();
    }

    /**
     * Streams status updates for a job.
     *
     * @param request The status request
     * @param responseObserver Stream observer for sending the responses
     */
    @Override
    public void streamJobStatus(JobStatusRequest request, StreamObserver<JobStatusResponse> responseObserver) {
        String jobId = request.getJobId();
        log.info("Starting status stream for job: {}", jobId);
        
        // Check if job exists
        TranscodeJob job = transcodeManager.getJobStatus(jobId);
        
        if (job == null) {
            responseObserver.onNext(JobStatusResponse.newBuilder()
                    .setJobId(jobId)
                    .setStatus(JobStatus.JOB_UNKNOWN)
                    .setErrorMessage("Job not found")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        
        // Send initial status
        responseObserver.onNext(createJobStatusResponse(job));
        
        // In a real implementation, we would set up a listener for job updates
        // and push them to the client. For this example, we'll just complete.
        // In a real app, this would keep the connection open and push updates.
        
        // For demonstration purposes only:
        responseObserver.onCompleted();
    }

    /**
     * Lists jobs with filtering and pagination.
     *
     * @param request The list request
     * @param responseObserver Stream observer for sending the response
     */
    @Override
    public void listJobs(ListJobsRequest request, StreamObserver<ListJobsResponse> responseObserver) {
        int limit = request.getLimit() > 0 ? request.getLimit() : 100;
        String pageToken = request.getPageToken().isEmpty() ? null : request.getPageToken();
        
        // Convert proto status filter to internal status enum
        List<com.example.transcoder.model.JobStatus> statusFilter = null;
        if (request.getStatusFilterCount() > 0) {
            statusFilter = request.getStatusFilterList().stream()
                    .map(this::mapProtoStatusToInternal)
                    .collect(Collectors.toList());
        }
        
        // Get jobs from repository
        List<TranscodeJob> jobs = jobRepository.findAllWithFilter(limit, statusFilter, pageToken);
        
        // Convert to response objects
        List<JobStatusResponse> responseJobs = jobs.stream()
                .map(this::createJobStatusResponse)
                .collect(Collectors.toList());
        
        // Calculate next page token (if more results available)
        String nextPageToken = "";
        if (jobs.size() == limit) {
            nextPageToken = jobs.get(jobs.size() - 1).getJobId();
        }
        
        // Build and send response
        ListJobsResponse response = ListJobsResponse.newBuilder()
                .addAllJobs(responseJobs)
                .setNextPageToken(nextPageToken)
                .setTotalCount(jobRepository.count())
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
        log.debug("Listed {} jobs", responseJobs.size());
    }

    /**
     * Creates a job status response from a job object.
     *
     * @param job The job object
     * @return JobStatusResponse proto message
     */
    private JobStatusResponse createJobStatusResponse(TranscodeJob job) {
        JobStatusResponse.Builder builder = JobStatusResponse.newBuilder()
                .setJobId(job.getJobId())
                .setVideoId(job.getVideoId())
                .setStatus(mapStatus(job.getStatus()))
                .setProgress(job.getProgress().get())
                .setStartTime(job.getStartedAt() != null ? job.getStartedAt().toEpochMilli() : 0)
                .setEndTime(job.getCompletedAt() != null ? job.getCompletedAt().toEpochMilli() : 0)
                .setEstimatedTimeRemaining(job.getEstimatedTimeRemaining());
        
        if (job.getCurrentStage() != null) {
            builder.setCurrentStage(job.getCurrentStage());
        }
        
        if (job.getErrorMessage() != null) {
            builder.setErrorMessage(job.getErrorMessage());
        }
        
        // Add output files if available
        for (OutputFile file : job.getOutputFiles()) {
            builder.addOutputFiles(com.example.transcoder.proto.OutputFile.newBuilder()
                    .setFormat(file.getFormat())
                    .setLocation(file.getLocation())
                    .setSize(file.getSize())
                    .setDuration(file.getDuration())
                    .setBitrate(file.getBitrate())
                    .build());
        }
        
        return builder.build();
    }

    /**
     * Maps internal job status to proto job status.
     *
     * @param status Internal job status
     * @return Proto job status
     */
    private JobStatus mapStatus(com.example.transcoder.model.JobStatus status) {
        switch (status) {
            case QUEUED:
                return JobStatus.JOB_QUEUED;
            case IN_PROGRESS:
                return JobStatus.JOB_IN_PROGRESS;
            case COMPLETED:
                return JobStatus.JOB_COMPLETED;
            case FAILED:
                return JobStatus.JOB_FAILED;
            case CANCELLED:
                return JobStatus.JOB_CANCELLED;
            default:
                return JobStatus.JOB_UNKNOWN;
        }
    }

    /**
     * Maps proto job status to internal job status.
     *
     * @param status Proto job status
     * @return Internal job status
     */
    private com.example.transcoder.model.JobStatus mapProtoStatusToInternal(JobStatus status) {
        switch (status) {
            case JOB_QUEUED:
                return com.example.transcoder.model.JobStatus.QUEUED;
            case JOB_IN_PROGRESS:
                return com.example.transcoder.model.JobStatus.IN_PROGRESS;
            case JOB_COMPLETED:
                return com.example.transcoder.model.JobStatus.COMPLETED;
            case JOB_FAILED:
                return com.example.transcoder.model.JobStatus.FAILED;
            case JOB_CANCELLED:
                return com.example.transcoder.model.JobStatus.CANCELLED;
            default:
                return com.example.transcoder.model.JobStatus.UNKNOWN;
        }
    }
}

