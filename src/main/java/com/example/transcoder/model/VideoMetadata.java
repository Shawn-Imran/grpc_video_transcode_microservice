package com.example.transcoder.model;

import lombok.Data;

/**
 * Represents metadata information extracted from a video file.
 */
@Data
public class VideoMetadata {
    /**
     * Width in pixels.
     */
    private int width;
    
    /**
     * Height in pixels.
     */
    private int height;
    
    /**
     * Duration in seconds.
     */
    private float durationSeconds;
    
    /**
     * Bitrate in kbps.
     */
    private int bitrate;
    
    /**
     * Video codec name.
     */
    private String videoCodec;
    
    /**
     * Audio codec name.
     */
    private String audioCodec;
}

