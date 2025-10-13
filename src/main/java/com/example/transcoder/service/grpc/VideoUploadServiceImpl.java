package com.example.transcoder.service.grpc;

import com.example.transcoder.proto.UploadResponse;
import com.example.transcoder.proto.UploadStatus;
import com.example.transcoder.proto.UploadStatusRequest;
import com.example.transcoder.proto.UploadStatusResponse;
import com.example.transcoder.proto.VideoChunk;
import com.example.transcoder.proto.VideoUploadServiceGrpc;
import com.example.transcoder.service.VideoStorageService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

/**
 * Implementation of the gRPC video upload service.
 */
@GRpcService
@Slf4j
public class VideoUploadServiceImpl extends VideoUploadServiceGrpc.VideoUploadServiceImplBase {

    @Autowired
    private VideoStorageService videoStorageService;

    /**
     * Handles streaming video upload requests.
     *
     * @param responseObserver Stream observer for sending the response
     * @return StreamObserver for receiving video chunks
     */
    @Override
    public StreamObserver<VideoChunk> uploadVideo(StreamObserver<UploadResponse> responseObserver) {
        return new StreamObserver<VideoChunk>() {
            private String uploadId;
            private String filename;
            private String contentType;
            private boolean initialized = false;

            @Override
            public void onNext(VideoChunk chunk) {
                try {
                    // Initialize on first chunk
                    if (!initialized) {
                        filename = chunk.getFilename();
                        contentType = chunk.getContentType();
                        
                        // If upload ID was provided, use it and create a session for it
                        // Otherwise, create a new session with a generated ID
                        if (chunk.getUploadId() != null && !chunk.getUploadId().isEmpty()) {
                            uploadId = chunk.getUploadId();
                            // Create session for the provided upload ID
                            videoStorageService.createUploadSessionWithId(uploadId, filename);
                        } else {
                            uploadId = videoStorageService.createUploadSession(filename);
                        }
                        
                        initialized = true;
                        log.info("Started upload session: {}, filename: {}, content-type: {}", 
                                uploadId, filename, contentType);
                    }

                    // Save the chunk
                    boolean saved = videoStorageService.saveChunk(
                            uploadId,
                            chunk.getContent().toByteArray(),
                            chunk.getSequenceNumber(),
                            chunk.getIsLastChunk());
                    
                    if (!saved) {
                        throw new RuntimeException("Failed to save chunk");
                    }
                    
                    // If this is the last chunk, assemble the file
                    if (chunk.getIsLastChunk()) {
                        log.info("Received last chunk for upload: {}", uploadId);
                    }
                } catch (Exception e) {
                    log.error("Error processing upload chunk: {}", e.getMessage(), e);
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in upload stream: {}", t.getMessage(), t);
                
                // Send error response
                UploadResponse response = UploadResponse.newBuilder()
                        .setStatus(UploadStatus.UPLOAD_FAILED)
                        .setErrorMessage("Upload failed: " + t.getMessage())
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                log.info("Upload stream completed for session: {}", uploadId);
                
                try {
                    // Check upload status
                    VideoStorageService.UploadStatus status = videoStorageService.getUploadStatus(uploadId);
                    
                    if (status == null) {
                        throw new RuntimeException("Upload session not found");
                    }
                    
                    if (!status.isComplete()) {
                        throw new RuntimeException("Upload incomplete");
                    }
                    
                    // Assemble the final file
                    Path assembledFile = videoStorageService.assembleFile(uploadId);
                    
                    if (assembledFile == null) {
                        throw new RuntimeException("Failed to assemble file");
                    }
                    
                    // Send success response
                    UploadResponse response = UploadResponse.newBuilder()
                            .setVideoId(status.getVideoId())
                            .setStatus(UploadStatus.UPLOAD_COMPLETED)
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    
                    log.info("Upload completed successfully, video ID: {}", status.getVideoId());
                } catch (Exception e) {
                    log.error("Error completing upload: {}", e.getMessage(), e);
                    
                    // Send error response
                    UploadResponse response = UploadResponse.newBuilder()
                            .setStatus(UploadStatus.UPLOAD_FAILED)
                            .setErrorMessage("Upload failed: " + e.getMessage())
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            }
        };
    }

    /**
     * Gets the status of an upload.
     *
     * @param request The request containing the upload ID
     * @param responseObserver Stream observer for sending the response
     */
    @Override
    public void getUploadStatus(UploadStatusRequest request, StreamObserver<UploadStatusResponse> responseObserver) {
        String uploadId = request.getUploadId();
        log.debug("Getting upload status for: {}", uploadId);
        
        VideoStorageService.UploadStatus status = videoStorageService.getUploadStatus(uploadId);
        
        UploadStatusResponse.Builder responseBuilder = UploadStatusResponse.newBuilder();
        
        if (status == null) {
            responseBuilder
                    .setStatus(UploadStatus.UPLOAD_UNKNOWN)
                    .setErrorMessage("Upload session not found");
        } else {
            // Map internal status to proto status
            UploadStatus protoStatus;
            if (status.getError() != null) {
                protoStatus = UploadStatus.UPLOAD_FAILED;
                responseBuilder.setErrorMessage(status.getError());
            } else if (status.isAssembled()) {
                protoStatus = UploadStatus.UPLOAD_COMPLETED;
                responseBuilder.setVideoId(status.getVideoId());
            } else {
                protoStatus = UploadStatus.UPLOAD_IN_PROGRESS;
            }
            
            responseBuilder
                    .setStatus(protoStatus)
                    .setPercentComplete(status.getPercentComplete());
        }
        
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
