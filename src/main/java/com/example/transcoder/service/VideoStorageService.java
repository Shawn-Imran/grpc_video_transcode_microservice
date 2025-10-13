package com.example.transcoder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling video file storage operations.
 */
@Service
@Slf4j
public class VideoStorageService {

    @Value("${storage.temp.directory}")
    private String tempDirectory;

    @Value("${storage.output.directory}")
    private String outputDirectory;

    private final ConcurrentHashMap<String, UploadStatus> uploadStatuses = new ConcurrentHashMap<>();

    /**
     * Initializes storage directories on startup.
     */
    public void init() {
        try {
            Files.createDirectories(Paths.get(tempDirectory));
            Files.createDirectories(Paths.get(outputDirectory));
            log.info("Storage directories initialized: temp={}, output={}", tempDirectory, outputDirectory);
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
            throw new RuntimeException("Could not initialize storage directories", e);
        }
    }

    /**
     * Creates a new upload session.
     *
     * @param filename Original filename
     * @return Upload ID for the new session
     */
    public String createUploadSession(String filename) {
        String uploadId = UUID.randomUUID().toString();
        uploadStatuses.put(uploadId, new UploadStatus(uploadId, filename));
        log.info("Created upload session: {}, filename: {}", uploadId, filename);
        return uploadId;
    }

    /**
     * Creates a new upload session with a specific upload ID.
     *
     * @param uploadId The upload ID to use
     * @param filename Original filename
     */
    public void createUploadSessionWithId(String uploadId, String filename) {
        uploadStatuses.put(uploadId, new UploadStatus(uploadId, filename));
        log.info("Created upload session with provided ID: {}, filename: {}", uploadId, filename);
    }

    /**
     * Saves a chunk of a video file.
     *
     * @param uploadId Upload session ID
     * @param chunk Byte array containing chunk data
     * @param sequenceNumber Chunk sequence number
     * @param isLastChunk Whether this is the last chunk
     * @return True if successful
     */
    public boolean saveChunk(String uploadId, byte[] chunk, int sequenceNumber, boolean isLastChunk) {
        UploadStatus status = uploadStatuses.get(uploadId);
        if (status == null) {
            log.warn("Upload session not found: {}", uploadId);
            return false;
        }

        try {
            Path chunkFile = Paths.get(tempDirectory, uploadId + "_" + sequenceNumber);
            Files.write(chunkFile, chunk);
            status.addChunk(sequenceNumber, chunkFile);
            status.setLastChunkReceived(isLastChunk);
            
            log.debug("Saved chunk {} for upload {}, size: {} bytes, last: {}", 
                     sequenceNumber, uploadId, chunk.length, isLastChunk);
            
            if (isLastChunk) {
                status.setTotalChunks(sequenceNumber + 1);
            }
            
            return true;
        } catch (IOException e) {
            log.error("Failed to save chunk for upload {}: {}", uploadId, e.getMessage(), e);
            status.setError("Failed to save chunk: " + e.getMessage());
            return false;
        }
    }

    /**
     * Assembles all chunks into a complete file once upload is complete.
     *
     * @param uploadId Upload session ID
     * @return Path to the assembled file, or null if assembly failed
     */
    public Path assembleFile(String uploadId) {
        UploadStatus status = uploadStatuses.get(uploadId);
        if (status == null || !status.isComplete()) {
            log.warn("Cannot assemble incomplete upload: {}", uploadId);
            return null;
        }

        String videoId = UUID.randomUUID().toString();
        String fileExtension = getFileExtension(status.getFilename());
        Path outputFile = Paths.get(tempDirectory, videoId + fileExtension);

        try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            for (int i = 0; i < status.getTotalChunks(); i++) {
                Path chunkPath = status.getChunkPath(i);
                if (chunkPath == null) {
                    throw new IOException("Missing chunk " + i);
                }
                
                Files.copy(chunkPath, fos);
                Files.delete(chunkPath); // Clean up chunk after using it
            }
            
            status.setAssembled(true);
            status.setVideoId(videoId);
            log.info("Assembled file for upload {}, video ID: {}, path: {}", 
                    uploadId, videoId, outputFile);
            
            return outputFile;
        } catch (IOException e) {
            log.error("Failed to assemble file for upload {}: {}", uploadId, e.getMessage(), e);
            status.setError("Failed to assemble file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the status of an upload.
     *
     * @param uploadId Upload ID
     * @return The upload status or null if not found
     */
    public UploadStatus getUploadStatus(String uploadId) {
        return uploadStatuses.get(uploadId);
    }

    /**
     * Creates the output directory for a transcoding job.
     *
     * @param jobId The job ID
     * @return Path to the job output directory
     */
    public Path createJobOutputDirectory(String jobId) {
        Path jobDir = Paths.get(outputDirectory, jobId);
        try {
            Files.createDirectories(jobDir);
            log.info("Created job output directory: {}", jobDir);
            return jobDir;
        } catch (IOException e) {
            log.error("Failed to create job output directory: {}", e.getMessage(), e);
            throw new RuntimeException("Could not create job output directory", e);
        }
    }

    /**
     * Gets the output file path for a specific format.
     *
     * @param jobId The job ID
     * @param videoId The video ID
     * @param formatName The format name (e.g., "720p")
     * @param container The container format (e.g., "mp4")
     * @return Path to the output file
     */
    public Path getOutputFilePath(String jobId, String videoId, String formatName, String container) {
        return Paths.get(outputDirectory, jobId, videoId + "_" + formatName + "." + container);
    }

    /**
     * Gets the temporary file path for a video.
     *
     * @param videoId The video ID
     * @return Path to the temporary file
     */
    public Path getVideoFile(String videoId) {
        File dir = new File(tempDirectory);
        File[] files = dir.listFiles((d, name) -> name.startsWith(videoId));
        
        if (files != null && files.length > 0) {
            return files[0].toPath();
        }
        
        log.warn("Video file not found for ID: {}", videoId);
        return null;
    }

    /**
     * Extracts file extension from filename.
     *
     * @param filename The filename
     * @return The file extension (with dot) or empty string if none
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0) ? filename.substring(dotIndex) : "";
    }

    /**
     * Inner class to track upload status.
     */
    public static class UploadStatus {
        private final String uploadId;
        private final String filename;
        private final ConcurrentHashMap<Integer, Path> chunks = new ConcurrentHashMap<>();
        private String videoId;
        private boolean lastChunkReceived = false;
        private boolean assembled = false;
        private int totalChunks = -1;
        private String error;
        private final long startTime = System.currentTimeMillis();

        public UploadStatus(String uploadId, String filename) {
            this.uploadId = uploadId;
            this.filename = filename;
        }

        public void addChunk(int sequenceNumber, Path chunkPath) {
            chunks.put(sequenceNumber, chunkPath);
        }

        public Path getChunkPath(int sequenceNumber) {
            return chunks.get(sequenceNumber);
        }

        public boolean isComplete() {
            return lastChunkReceived && totalChunks > 0 && chunks.size() == totalChunks;
        }

        public int getPercentComplete() {
            if (totalChunks <= 0) {
                return (int)(chunks.size() * 10); // Rough estimate if we don't know total
            }
            return (int)((chunks.size() * 100.0) / totalChunks);
        }

        // Getters and setters
        public String getUploadId() {
            return uploadId;
        }

        public String getFilename() {
            return filename;
        }

        public String getVideoId() {
            return videoId;
        }

        public void setVideoId(String videoId) {
            this.videoId = videoId;
        }

        public boolean isLastChunkReceived() {
            return lastChunkReceived;
        }

        public void setLastChunkReceived(boolean lastChunkReceived) {
            this.lastChunkReceived = lastChunkReceived;
        }

        public boolean isAssembled() {
            return assembled;
        }

        public void setAssembled(boolean assembled) {
            this.assembled = assembled;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public void setTotalChunks(int totalChunks) {
            this.totalChunks = totalChunks;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public long getStartTime() {
            return startTime;
        }
    }
}
