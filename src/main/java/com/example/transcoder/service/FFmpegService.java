package com.example.transcoder.service;

import com.example.transcoder.model.TranscodeJob;
import com.example.transcoder.model.VideoFormat;
import com.example.transcoder.model.VideoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for interacting with FFmpeg for video processing.
 */
@Service
@Slf4j
public class FFmpegService {

    @Value("${transcode.ffmpeg.path}")
    private String ffmpegPath;

    @Value("${transcode.ffprobe.path}")
    private String ffprobePath;

    private static final Pattern DURATION_PATTERN = 
            Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");
    private static final Pattern PROGRESS_PATTERN = 
            Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    /**
     * Extracts metadata from a video file using ffprobe.
     *
     * @param videoPath Path to the video file
     * @return Video metadata or null if extraction failed
     */
    public VideoMetadata extractMetadata(Path videoPath) {
        List<String> command = new ArrayList<>();
        command.add(ffprobePath);
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("format=duration,bit_rate:stream=width,height,codec_name");
        command.add("-of");
        command.add("json");
        command.add(videoPath.toString());

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                boolean completed = process.waitFor(30, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    log.error("FFprobe timed out after 30 seconds");
                    return null;
                }
                
                if (process.exitValue() != 0) {
                    log.error("FFprobe failed with exit code: {}", process.exitValue());
                    return null;
                }
                
                // For simplicity, we're returning a basic metadata object
                // In a real implementation, parse the JSON output properly
                return parseMetadata(output.toString());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to extract metadata: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Transcodes a video to the specified format.
     *
     * @param job The transcoding job
     * @param format The output format
     * @param outputPath Path to the output file
     * @param progressCallback Callback for progress updates
     * @return True if transcoding was successful
     */
    public boolean transcodeVideo(TranscodeJob job, VideoFormat format, Path outputPath, 
                                 ProgressCallback progressCallback) {
        List<String> command = buildFFmpegCommand(job.getInputPath(), outputPath, format, job.getOptions());
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Extract total duration for progress calculation
            float totalDuration = job.getMetadata().getDurationSeconds();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Extract progress from FFmpeg output
                    Matcher matcher = PROGRESS_PATTERN.matcher(line);
                    if (matcher.find()) {
                        float currentTime = parseTime(matcher);
                        int progressPercent = (int) ((currentTime / totalDuration) * 100);
                        
                        if (progressCallback != null) {
                            progressCallback.onProgress(progressPercent, "Transcoding " + format.getName());
                        }
                    }
                    
                    log.debug("FFmpeg: {}", line);
                }
                
                int exitCode = process.waitFor();
                boolean success = (exitCode == 0);
                
                if (success) {
                    log.info("Transcoding completed successfully: {}", outputPath);
                    if (progressCallback != null) {
                        progressCallback.onProgress(100, "Completed " + format.getName());
                    }
                } else {
                    log.error("Transcoding failed with exit code: {}", exitCode);
                    if (progressCallback != null) {
                        progressCallback.onProgress(-1, "Failed: exit code " + exitCode);
                    }
                }
                
                return success;
            }
        } catch (IOException | InterruptedException e) {
            log.error("Transcoding failed: {}", e.getMessage(), e);
            if (progressCallback != null) {
                progressCallback.onProgress(-1, "Error: " + e.getMessage());
            }
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Builds the FFmpeg command for transcoding.
     *
     * @param inputPath Input file path
     * @param outputPath Output file path
     * @param format Output format
     * @param options Additional options
     * @return List of command arguments
     */
    private List<String> buildFFmpegCommand(Path inputPath, Path outputPath, 
                                           VideoFormat format, TranscodeJob.Options options) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath.toString());
        
        // Video codec and settings
        command.add("-c:v");
        command.add(format.getVideoCodec());
        command.add("-s");
        command.add(format.getWidth() + "x" + format.getHeight());
        
        if (format.getBitrate() > 0) {
            command.add("-b:v");
            command.add(format.getBitrate() + "k");
        }
        
        // Apply two-pass encoding if requested
        if (options != null && options.isTwoPass()) {
            command.add("-pass");
            command.add("1");
        }
        
        // Apply CRF if specified
        if (options != null && options.getCrf() > 0) {
            command.add("-crf");
            command.add(String.valueOf(options.getCrf()));
        }
        
        // Frame rate if specified
        if (options != null && options.getFrameRate() > 0) {
            command.add("-r");
            command.add(String.valueOf(options.getFrameRate()));
        }
        
        // Audio codec and settings
        if (options != null && options.getAudioCodec() != null) {
            command.add("-c:a");
            command.add(options.getAudioCodec());
            
            if (options.getAudioBitrate() > 0) {
                command.add("-b:a");
                command.add(options.getAudioBitrate() + "k");
            }
        } else {
            // Default audio settings
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
        }
        
        // Output file
        command.add("-y"); // Overwrite output files without asking
        command.add(outputPath.toString());
        
        log.info("FFmpeg command: {}", String.join(" ", command));
        return command;
    }

    /**
     * Parses metadata from ffprobe output.
     *
     * @param output FFprobe JSON output
     * @return VideoMetadata object
     */
    private VideoMetadata parseMetadata(String output) {
        // This is a simplified implementation
        // In a real application, use a JSON parser to extract values
        VideoMetadata metadata = new VideoMetadata();
        
        // Extract duration
        Matcher durationMatcher = DURATION_PATTERN.matcher(output);
        if (durationMatcher.find()) {
            float duration = parseTime(durationMatcher);
            metadata.setDurationSeconds(duration);
        }
        
        // For now, set some reasonable defaults
        metadata.setWidth(1920);
        metadata.setHeight(1080);
        metadata.setBitrate(5000);
        metadata.setVideoCodec("h264");
        metadata.setAudioCodec("aac");
        
        return metadata;
    }

    /**
     * Parses time from HH:MM:SS.MS format.
     *
     * @param matcher Matcher containing time groups
     * @return Time in seconds
     */
    private float parseTime(Matcher matcher) {
        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        int seconds = Integer.parseInt(matcher.group(3));
        int milliseconds = Integer.parseInt(matcher.group(4));
        
        return hours * 3600 + minutes * 60 + seconds + (milliseconds / 100.0f);
    }

    /**
     * Callback interface for progress updates.
     */
    public interface ProgressCallback {
        void onProgress(int percent, String message);
    }
}

