package com.example.transcoder.test;

import com.example.transcoder.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test client for the YouTube Transcode Service.
 * This client connects to the gRPC server, uploads a video file,
 * requests transcoding, and monitors the progress.
 */
public class TranscodeClientTest {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 6565;
    private static final String TEST_VIDEO_PATH = "test-video.mp4"; // Place a test video in the project root

    public static void main(String[] args) {
        // Create a path for the test video
        Path testVideoPath = Paths.get(TEST_VIDEO_PATH);
        
        // Check if the test video exists
        if (!Files.exists(testVideoPath)) {
            System.err.println("Test video not found at: " + testVideoPath.toAbsolutePath());
            System.err.println("Please place a test video named 'test-video.mp4' in the project root directory.");
            return;
        }

        // Create a channel
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(SERVER_HOST, SERVER_PORT)
                .usePlaintext() // Not secure, but okay for testing
                .build();

        try {
            // Upload the video and get the video ID
            String videoId = uploadVideo(channel, testVideoPath);
            if (videoId == null) {
                System.err.println("Failed to upload video.");
                return;
            }
            
            System.out.println("Video uploaded successfully with ID: " + videoId);

            // Request transcoding
            String jobId = transcodeVideo(channel, videoId);
            if (jobId == null) {
                System.err.println("Failed to start transcoding job.");
                return;
            }
            
            System.out.println("Transcoding job started with ID: " + jobId);

            // Monitor transcoding progress
            monitorTranscodingProgress(channel, jobId);

        } catch (Exception e) {
            System.err.println("Error during transcoding test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown the channel
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("Error shutting down channel: " + e.getMessage());
            }
        }
    }

    /**
     * Uploads a video file to the server.
     * 
     * @param channel The gRPC channel
     * @param videoPath Path to the video file
     * @return The video ID if upload was successful, null otherwise
     */
    private static String uploadVideo(ManagedChannel channel, Path videoPath) {
        // Create a stub for the VideoUploadService
        VideoUploadServiceGrpc.VideoUploadServiceStub uploadStub = 
            VideoUploadServiceGrpc.newStub(channel);

        // Create a latch to wait for the upload to complete
        CountDownLatch uploadLatch = new CountDownLatch(1);
        AtomicReference<String> videoIdRef = new AtomicReference<>(null);

        // Create an observer for upload responses
        StreamObserver<UploadResponse> responseObserver = new StreamObserver<UploadResponse>() {
            @Override
            public void onNext(UploadResponse response) {
                System.out.println("Upload response - Video ID: " + response.getVideoId());
                System.out.println("Upload status: " + response.getStatus());
                videoIdRef.set(response.getVideoId());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Upload error: " + t.getMessage());
                uploadLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Upload completed");
                uploadLatch.countDown();
            }
        };

        // Start the upload
        StreamObserver<VideoChunk> requestObserver = uploadStub.uploadVideo(responseObserver);

        // Read the file in chunks and send
        try {
            String filename = videoPath.getFileName().toString();
            long fileSize = Files.size(videoPath);
            
            byte[] buffer = new byte[1024 * 1024]; // 1MB chunks
            int chunkIndex = 0;
            
            try (InputStream inputStream = new FileInputStream(videoPath.toFile())) {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    boolean isLastChunk = inputStream.available() == 0;
                    
                    VideoChunk chunk = VideoChunk.newBuilder()
                        .setContent(com.google.protobuf.ByteString.copyFrom(buffer, 0, bytesRead))
                        .setFilename(filename)
                        .setContentType("video/mp4")
                        .setSequenceNumber(chunkIndex++)
                        .setIsLastChunk(isLastChunk)
                        .build();
                    
                    requestObserver.onNext(chunk);
                    System.out.println("Sent chunk " + chunkIndex + " of size " + bytesRead + " bytes");
                    
                    if (isLastChunk) {
                        System.out.println("Sent last chunk");
                    }
                }
            }
            
            // Mark the upload as complete
            requestObserver.onCompleted();
            
            // Wait for the upload to complete (max 30 seconds)
            if (!uploadLatch.await(30, TimeUnit.SECONDS)) {
                System.err.println("Upload timed out");
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error uploading video: " + e.getMessage());
            e.printStackTrace();
            requestObserver.onError(e);
            return null;
        }

        return videoIdRef.get();
    }

    /**
     * Requests the server to transcode the uploaded video.
     * 
     * @param channel The gRPC channel
     * @param videoId The ID of the uploaded video
     * @return The job ID if the transcode request was successful, null otherwise
     */
    private static String transcodeVideo(ManagedChannel channel, String videoId) {
        // Create a blocking stub for the TranscodeService
        TranscodeServiceGrpc.TranscodeServiceBlockingStub transcodeStub = 
            TranscodeServiceGrpc.newBlockingStub(channel);
        
        try {
            // Create the transcode request
            TranscodeRequest request = TranscodeRequest.newBuilder()
                .setVideoId(videoId)
                .setOutputContainer("mp4")
                .addOutputFormats(
                    OutputFormat.newBuilder()
                        .setName("720p")
                        .setWidth(1280)
                        .setHeight(720)
                        .setVideoCodec("libx264")
                        .setBitrate(2500)
                        .build()
                )
                .setOptions(
                    TranscodeOptions.newBuilder()
                        .setAudioCodec("aac")
                        .setAudioBitrate(128)
                        .setCrf(23)
                        .build()
                )
                .build();
            
            // Send the request and get the response
            TranscodeResponse response = transcodeStub.transcodeVideo(request);
            return response.getJobId();
        } catch (Exception e) {
            System.err.println("Error requesting transcoding: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Monitors the progress of a transcoding job.
     * 
     * @param channel The gRPC channel
     * @param jobId The ID of the transcoding job
     */
    private static void monitorTranscodingProgress(ManagedChannel channel, String jobId) {
        // Create a stub for the StatusService
        StatusServiceGrpc.StatusServiceStub statusStub = 
            StatusServiceGrpc.newStub(channel);
        
        // Create a latch to wait for the monitoring to complete
        CountDownLatch monitorLatch = new CountDownLatch(1);
        
        // Create an observer for job status updates
        StreamObserver<JobStatusResponse> responseObserver = new StreamObserver<JobStatusResponse>() {
            @Override
            public void onNext(JobStatusResponse response) {
                System.out.println("Job ID: " + response.getJobId());
                System.out.println("Status: " + response.getStatus());
                System.out.println("Progress: " + response.getProgress() + "%");
                System.out.println("Current stage: " + response.getCurrentStage());
                
                // If the job is completed or failed, we're done monitoring
                if (response.getStatus().equals("COMPLETED") || 
                    response.getStatus().equals("FAILED")) {
                    monitorLatch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Monitoring error: " + t.getMessage());
                monitorLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Monitoring completed");
                monitorLatch.countDown();
            }
        };
        
        // Create the job status request
        JobStatusRequest request = JobStatusRequest.newBuilder()
            .setJobId(jobId)
            .build();
        
        // Start streaming job status updates
        statusStub.streamJobStatus(request, responseObserver);
        
        try {
            // Wait for up to 5 minutes for the job to complete
            if (!monitorLatch.await(5, TimeUnit.MINUTES)) {
                System.err.println("Monitoring timed out - job is taking too long");
            }
        } catch (InterruptedException e) {
            System.err.println("Monitoring interrupted: " + e.getMessage());
        }
    }
}

