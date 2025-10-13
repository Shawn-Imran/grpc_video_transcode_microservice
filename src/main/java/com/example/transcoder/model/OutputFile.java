package com.example.transcoder.model;

import lombok.Data;
import lombok.Builder;

/**
 * Represents a transcoded output file.
 */
@Data
@Builder
public class OutputFile {
    /**
     * Format name (e.g., "1080p", "720p").
     */
    private String format;
    
    /**
     * File path or URL.
     */
    private String location;
    
    /**
     * File size in bytes.
     */
    private long size;
    
    /**
     * Duration in seconds.
     */
    private float duration;
    
    /**
     * Video bitrate in kbps.
     */
    private int bitrate;
}

