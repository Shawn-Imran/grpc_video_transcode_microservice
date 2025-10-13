package com.example.transcoder.service.grpc;

import com.example.transcoder.model.JobStatus;
import com.example.transcoder.model.TranscodeJob;
import com.example.transcoder.model.VideoFormat;
import com.example.transcoder.proto.CancelRequest;
import com.example.transcoder.proto.CancelResponse;
import com.example.transcoder.proto.OutputFormat;
import com.example.transcoder.proto.TranscodeOptions;
import com.example.transcoder.proto.TranscodeRequest;
import com.example.transcoder.proto.TranscodeResponse;
import com.example.transcoder.proto.TranscodeServiceGrpc;
import com.example.transcoder.service.TranscodeManager;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the gRPC transcoding service.
 */
@GRpcService
@Slf4j
public class TranscodeServiceImpl extends TranscodeServiceGrpc.TranscodeServiceImplBase {

    @Autowired
    private TranscodeManager transcodeManager;

    /**
     * Handles video transcoding requests.
     *
     * @param request The transcoding request
     * @param responseObserver Stream observer for sending the response
     */
    @Override
    public void transcodeVideo(TranscodeRequest request, StreamObserver<TranscodeResponse> responseObserver) {
        log.info("Received transcode request for video: {}", request.getVideoId());
        
        try {
            // Convert proto formats to domain model formats
            List<VideoFormat> formats = new ArrayList<>();
            for (OutputFormat format : request.getOutputFormatsList()) {
                formats.add(VideoFormat.builder()
                        .name(format.getName())
                        .width(format.getWidth())
                        .height(format.getHeight())
                        .videoCodec(format.getVideoCodec())
                        .bitrate(format.getBitrate())
                        .build());
            }
            
            // If no formats specified, use defaults
            if (formats.isEmpty()) {
                formats = transcodeManager.parseFormats(null);
            }
            
            // Convert options
            TranscodeJob.Options options = null;
            if (request.hasOptions()) {
                TranscodeOptions protoOptions = request.getOptions();
                options = TranscodeJob.Options.builder()
                        .audioCodec(protoOptions.getAudioCodec())
                        .audioBitrate(protoOptions.getAudioBitrate())
                        .frameRate(protoOptions.getFrameRate())
                        .twoPass(protoOptions.getTwoPass())
                        .crf(protoOptions.getCrf())
                        .build();
            }
            
            // Create and start the job
            TranscodeJob job = transcodeManager.createJob(
                    request.getVideoId(),
                    formats,
                    request.getOutputContainer().isEmpty() ? "mp4" : request.getOutputContainer(),
                    options);
            
            if (job == null) {
                responseObserver.onNext(TranscodeResponse.newBuilder()
                        .setStatus(com.example.transcoder.proto.JobStatus.JOB_FAILED)
                        .setErrorMessage("Failed to create transcoding job")
                        .build());
                responseObserver.onCompleted();
                return;
            }
            
            // Start processing the job asynchronously
            transcodeManager.processJob(job.getJobId());
            
            // Send success response
            TranscodeResponse response = TranscodeResponse.newBuilder()
                    .setJobId(job.getJobId())
                    .setStatus(mapStatus(job.getStatus()))
                    .setEstimatedTime(job.getEstimatedTimeRemaining())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("Created and started transcoding job: {}", job.getJobId());
            
        } catch (Exception e) {
            log.error("Error processing transcode request: {}", e.getMessage(), e);
            
            responseObserver.onNext(TranscodeResponse.newBuilder()
                    .setStatus(com.example.transcoder.proto.JobStatus.JOB_FAILED)
                    .setErrorMessage("Error: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Handles job cancellation requests.
     *
     * @param request The cancellation request
     * @param responseObserver Stream observer for sending the response
     */
    @Override
    public void cancelTranscoding(CancelRequest request, StreamObserver<CancelResponse> responseObserver) {
        String jobId = request.getJobId();
        log.info("Received cancel request for job: {}", jobId);
        
        boolean success = transcodeManager.cancelJob(jobId);
        
        CancelResponse response = CancelResponse.newBuilder()
                .setSuccess(success)
                .setErrorMessage(success ? "" : "Could not cancel job. It may be completed, failed, or not found.")
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Maps internal job status to proto job status.
     *
     * @param status Internal job status
     * @return Proto job status
     */
    private com.example.transcoder.proto.JobStatus mapStatus(JobStatus status) {
        switch (status) {
            case QUEUED:
                return com.example.transcoder.proto.JobStatus.JOB_QUEUED;
            case IN_PROGRESS:
                return com.example.transcoder.proto.JobStatus.JOB_IN_PROGRESS;
            case COMPLETED:
                return com.example.transcoder.proto.JobStatus.JOB_COMPLETED;
            case FAILED:
                return com.example.transcoder.proto.JobStatus.JOB_FAILED;
            case CANCELLED:
                return com.example.transcoder.proto.JobStatus.JOB_CANCELLED;
            default:
                return com.example.transcoder.proto.JobStatus.JOB_UNKNOWN;
        }
    }
}

