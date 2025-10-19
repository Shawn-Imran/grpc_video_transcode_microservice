# Video Transcode Microservice

A high-performance video transcoding microservice built with Java Spring Boot and gRPC. This service enables video uploading, transcoding to multiple formats, and real-time job status monitoring through efficient gRPC APIs.

## Features

- **gRPC-Based Communication**: High-performance binary protocol for efficient data transfer
- **Stream-Based Video Upload**: Chunked upload support for large video files
- **Multi-Format Transcoding**: Transcode videos to multiple resolutions and formats simultaneously
- **Real-Time Status Monitoring**: Stream job status updates with progress tracking
- **Asynchronous Processing**: Non-blocking transcoding with configurable worker thread pools
- **FFmpeg Integration**: Industry-standard video processing engine
- **RESTful APIs**: Optional REST endpoints alongside gRPC services
- **Job Management**: List, monitor, and cancel transcoding jobs
- **Dockerized Deployment**: Ready-to-deploy container configuration

## Architecture

This microservice exposes three main gRPC services:

1. **VideoUploadService**: Handle chunked video file uploads
2. **TranscodeService**: Request and manage video transcoding jobs
3. **StatusService**: Monitor job progress and retrieve job information

## Prerequisites

- Java 22 or later
- Maven 3.6+
- FFmpeg installed and accessible in system PATH
- Docker (optional, for containerized deployment)

## Installation & Setup

### Clone the Repository

```bash
git clone https://github.com/yourusername/grpc_video_transcode_microservice.git
cd grpc_video_transcode_microservice
```

### Configure Application

Edit `src/main/resources/application.properties` to customize:

```properties
# gRPC Server Configuration
grpc.server.port=9090
grpc.server.max-inbound-message-size=10485760  # 10MB

# File Storage Configuration
storage.temp.directory=temp-uploads
storage.output.directory=transcoded-videos
storage.max-file-size=5120MB  # 5GB max

# Transcoding Configuration
transcode.worker.thread-pool-size=5
transcode.default-formats=1080p,720p,480p,360p
transcode.ffmpeg.path=ffmpeg
transcode.ffprobe.path=ffprobe
```

### Build the Project

```bash
mvn clean install
```

### Run the Service

```bash
mvn spring-boot:run
```

The gRPC server will start on port 9090 (or your configured port).

### Docker Deployment

Build and run using Docker:

```bash
# Build the Docker image
docker build -t video-transcode-service .

# Run the container
docker run -p 9090:9090 -v $(pwd)/transcoded-videos:/app/transcoded-videos video-transcode-service
```

## Java Client Library

A dedicated Java client library is available to easily interact with this microservice:

**Repository**: [https://github.com/Shawn-Imran/video_transcode_microservice_client.git](https://github.com/Shawn-Imran/video_transcode_microservice_client.git)

### Quick Start with Java Client

Add the client dependency to your project (Maven):

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>video-transcode-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Example usage:

```java
// Create a client instance
TranscoderClient client = new TranscoderClient("localhost", 9090);

// Upload a video
String videoId = client.uploadVideo("path/to/video.mp4");

// Request transcoding
List<OutputFormat> formats = Arrays.asList(
    OutputFormat.newBuilder()
        .setName("1080p")
        .setWidth(1920)
        .setHeight(1080)
        .setVideoCodec("libx264")
        .setBitrate(4500)
        .build(),
    OutputFormat.newBuilder()
        .setName("720p")
        .setWidth(1280)
        .setHeight(720)
        .setVideoCodec("libx264")
        .setBitrate(2500)
        .build()
);

String jobId = client.transcodeVideo(videoId, formats);

// Monitor job status
client.streamJobStatus(jobId, status -> {
    System.out.println("Progress: " + status.getProgress() + "%");
    System.out.println("Status: " + status.getStatus());
});
```

For detailed documentation and examples, visit the [client repository](https://github.com/Shawn-Imran/video_transcode_microservice_client.git).

## API Reference

### gRPC Services

#### VideoUploadService

Upload video files using streaming chunks:

```protobuf
service VideoUploadService {
  rpc UploadVideo(stream VideoChunk) returns (UploadResponse);
  rpc GetUploadStatus(UploadStatusRequest) returns (UploadStatusResponse);
}
```

**VideoChunk** fields:
- `upload_id`: Unique identifier for the upload session
- `content`: Binary chunk data
- `filename`: Original filename
- `content_type`: MIME type (e.g., "video/mp4")
- `sequence_number`: Chunk ordering
- `is_last_chunk`: Flag for final chunk

#### TranscodeService

Request and manage transcoding jobs:

```protobuf
service TranscodeService {
  rpc TranscodeVideo(TranscodeRequest) returns (TranscodeResponse);
  rpc CancelTranscoding(CancelRequest) returns (CancelResponse);
}
```

**TranscodeRequest** fields:
- `video_id`: ID of uploaded video
- `output_formats`: List of desired output formats
- `output_container`: Container format (e.g., "mp4", "webm")
- `options`: Transcoding options (audio codec, bitrate, frame rate, two-pass encoding, CRF)

**OutputFormat** specification:
- `name`: Resolution identifier (e.g., "1080p", "720p")
- `width`: Video width in pixels
- `height`: Video height in pixels
- `video_codec`: Codec (e.g., "libx264", "libx265")
- `bitrate`: Target bitrate in kbps

#### StatusService

Monitor transcoding job progress:

```protobuf
service StatusService {
  rpc GetJobStatus(JobStatusRequest) returns (JobStatusResponse);
  rpc StreamJobStatus(JobStatusRequest) returns (stream JobStatusResponse);
  rpc ListJobs(ListJobsRequest) returns (ListJobsResponse);
}
```

**JobStatusResponse** includes:
- Job status (QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED)
- Progress percentage (0-100)
- Current processing stage
- Estimated time remaining
- Output file information (when completed)
- Error messages (if failed)

## Configuration

### Transcoding Options

The service supports various FFmpeg transcoding options:

- **Audio Codec**: AAC, MP3, Opus, etc.
- **Video Codec**: H.264 (libx264), H.265 (libx265), VP9, etc.
- **Quality Control**: CRF values (0-51, lower = higher quality)
- **Encoding Mode**: Single-pass or two-pass encoding
- **Frame Rate**: Custom frame rates (e.g., 24, 30, 60 fps)

### Storage Configuration

Configure storage paths for temporary uploads and transcoded outputs:

```properties
storage.temp.directory=temp-uploads
storage.output.directory=transcoded-videos
storage.max-file-size=5120MB
```

### Performance Tuning

Adjust worker thread pool size based on your hardware:

```properties
transcode.worker.thread-pool-size=5  # Number of concurrent transcoding jobs
```

## Use Cases

- **Video Streaming Platforms**: Prepare videos in multiple resolutions for adaptive bitrate streaming
- **Content Management Systems**: Automatic video optimization for web delivery
- **Media Processing Pipelines**: Integrate video transcoding into larger workflows
- **Cloud Storage Services**: On-demand video format conversion
- **Educational Platforms**: Optimize video content for various devices and bandwidths

## Project Structure

```
grpc_video_transcode_microservice/
├── src/
│   ├── main/
│   │   ├── java/com/example/transcoder/
│   │   │   ├── TranscoderApplication.java       # Main application
│   │   │   ├── config/                          # Configuration classes
│   │   │   ├── model/                           # Domain models
│   │   │   ├── repository/                      # Data repositories
│   │   │   ├── service/                         # Business logic
│   │   │   │   ├── grpc/                        # gRPC service implementations
│   │   │   │   ├── FFmpegService.java           # FFmpeg integration
│   │   │   │   ├── TranscodeManager.java        # Job management
│   │   │   │   └── VideoStorageService.java     # File storage
│   │   │   └── rest/                            # REST controllers (optional)
│   │   ├── proto/                               # Protocol Buffer definitions
│   │   │   ├── video_upload.proto
│   │   │   ├── transcode.proto
│   │   │   └── status.proto
│   │   └── resources/
│   │       └── application.properties           # Application configuration
│   └── test/                                    # Test classes
├── Dockerfile                                   # Docker configuration
├── pom.xml                                      # Maven dependencies
└── README.md                                    # This file
```

## Testing

Run the test suite:

```bash
mvn test
```

### Manual Testing with grpcurl

Install [grpcurl](https://github.com/fullstorydev/grpcurl) and test endpoints:

```bash
# List available services
grpcurl -plaintext localhost:9090 list

# Get job status
grpcurl -plaintext -d '{"job_id": "your-job-id"}' \
  localhost:9090 transcoder.StatusService/GetJobStatus
```

## Monitoring & Health Checks

The service exposes Spring Boot Actuator endpoints:

- **Health**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`
- **Info**: `http://localhost:8080/actuator/info`

## Error Handling

The service provides detailed error responses:

- **INVALID_ARGUMENT**: Invalid request parameters
- **NOT_FOUND**: Video or job not found
- **RESOURCE_EXHAUSTED**: Server capacity exceeded
- **INTERNAL**: Internal server error
- **CANCELLED**: Operation cancelled by client

## Performance Considerations

- **Chunk Size**: Recommended 1-5MB for optimal upload performance
- **Concurrent Jobs**: Adjust `transcode.worker.thread-pool-size` based on CPU cores
- **Storage**: Ensure sufficient disk space for temporary and output files
- **Memory**: FFmpeg processes can be memory-intensive; monitor heap usage

## Contributing

Contributions are welcome! To contribute:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Java coding conventions
- Write unit tests for new features
- Update documentation as needed
- Ensure all tests pass before submitting PR

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Related Projects

- [Video Transcode Microservice Client](https://github.com/Shawn-Imran/video_transcode_microservice_client.git) - Java client library for this service

## Troubleshooting

### FFmpeg Not Found

Ensure FFmpeg is installed and in your system PATH:

```bash
# Linux/Mac
which ffmpeg

# Windows
where ffmpeg
```

Or configure the full path in `application.properties`:

```properties
transcode.ffmpeg.path=/usr/local/bin/ffmpeg
transcode.ffprobe.path=/usr/local/bin/ffprobe
```

### Port Already in Use

Change the gRPC port in `application.properties`:

```properties
grpc.server.port=9091
```

### Out of Memory Errors

Increase JVM heap size:

```bash
java -Xmx4g -jar youtube-transcode-service.jar
```

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

---

**Built with** ❤️ **using Spring Boot, gRPC, and FFmpeg**
