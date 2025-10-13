# YouTube Transcode Service Python Client

A Python client library for the YouTube Transcode Service that enables video uploading, transcoding, and job status monitoring through a clean, Pythonic interface.

## Features

- **Video Upload**: Stream-based chunked video upload with automatic sequencing
- **Transcoding**: Request video transcoding with customizable formats and encoding options
- **Job Status Monitoring**: Check job status, stream real-time updates, and list jobs
- **Unified API**: Convenient interfaces for all service operations
- **Error Handling**: Robust error handling with specific exception types
- **Async-Ready**: Support for non-blocking operations
- **Type Hints**: Full type annotation support for modern Python development

## Installation

### Prerequisites

- Python 3.7 or later
- pip package manager

### Install from PyPI (Recommended)

```bash
pip install youtube-transcode-client
```

### Install from Source

```bash
git clone https://github.com/yourusername/youtube-transcode-client.git
cd youtube-transcode-client
pip install -e .
```

## Quick Start

This example shows how to upload a video and transcode it with default settings:

```python
from transcoder import TranscoderClient

# Create a client
with TranscoderClient(host="localhost", port=9090) as client:
    # Upload and transcode a video in one operation
    video_id, job_id, _ = client.upload_and_transcode(
        file_path="path/to/video.mp4"
    )
    
    print(f"Video uploaded with ID: {video_id}")
    print(f"Transcoding job started with ID: {job_id}")
    
    # Check the job status
    status = client.status.get_job_status(job_id)
    print(f"Job status: {status.status}, Progress: {status.progress}%")
```

## Usage Examples

### Upload a Video

```python
from transcoder import TranscoderClient

with TranscoderClient() as client:
    # Upload a video with 1MB chunks
    video_id = client.upload.upload_video(
        file_path="path/to/video.mp4",
        chunk_size=1024 * 1024,  # 1MB chunks
        content_type="video/mp4"  # Optional, auto-detected if not provided
    )
    
    print(f"Video uploaded with ID: {video_id}")
```

### Request Transcoding with Custom Settings

```python
from transcoder import TranscoderClient

with TranscoderClient() as client:
    # Define custom output formats
    output_formats = [
        {
            "name": "1080p",
            "width": 1920,
            "height": 1080,
            "video_codec": "libx264",
            "bitrate": 4500
        },
        {
            "name": "720p",
            "width": 1280,
            "height": 720,
            "video_codec": "libx264",
            "bitrate": 2500
        }
    ]
    
    # Define transcoding options
    options = {
        "audio_codec": "aac",
        "audio_bitrate": 192,
        "frame_rate": 30.0,
        "two_pass": True,
        "crf": 18  # Lower CRF = higher quality
    }
    
    # Request transcoding
    job_id = client.transcode.transcode_video(
        video_id="your-video-id",
        output_formats=output_formats,
        output_container="mp4",
        options=options
    )
    
    print(f"Transcoding job started with ID: {job_id}")
```

### Monitor Job Status

```python
from transcoder import TranscoderClient

with TranscoderClient() as client:
    # Get a one-time status update
    status = client.status.get_job_status("your-job-id")
    print(f"Status: {status.status}, Progress: {status.progress}%")
    
    # Stream status updates in real-time
    for status in client.status.stream_job_status("your-job-id"):
        print(f"Status: {status.status}, Progress: {status.progress}%")
        if not status.is_active:
            break
    
    # Wait for job completion with a callback
    def status_callback(status):
        print(f"Status update: {status.status}, Progress: {status.progress}%")
    
    final_status = client.status.wait_for_completion(
        job_id="your-job-id",
        callback=status_callback
    )
    
    if final_status.is_completed:
        print("Job completed successfully!")
        for file in final_status.output_files:
            print(f"Output file: {file['format']} at {file['location']}")
    elif final_status.is_failed:
        print(f"Job failed: {final_status.error_message}")
```

### Manage Jobs

```python
from transcoder import TranscoderClient

with TranscoderClient() as client:
    # List active jobs
    active_jobs = client.get_active_jobs()
    print(f"Active jobs: {len(active_jobs)}")
    
    # List completed jobs
    completed_jobs = client.get_completed_jobs()
    print(f"Completed jobs: {len(completed_jobs)}")
    
    # Cancel a job
    if active_jobs:
        job_id = active_jobs[0].job_id
        success = client.transcode.cancel_transcoding(job_id)
        print(f"Job {job_id} cancellation {'succeeded' if success else 'failed'}")
```

## API Reference

### Main Client

#### `TranscoderClient`

The main client that provides access to all service functionality.

```python
client = TranscoderClient(
    host="localhost",  # Service hostname or IP
    port=9090,         # Service port
    secure=False,      # Whether to use TLS
    timeout=60.0       # Default timeout in seconds
)
```

Key methods:
- `upload_and_transcode()`: Unified method for uploading and transcoding
- `get_active_jobs()`, `get_completed_jobs()`, `get_failed_jobs()`: Job listing helpers
- `cancel_all_active_jobs()`: Cancel all active transcoding jobs

### Video Upload

#### `VideoUploadClient`

Client for video upload operations.

Key methods:
- `upload_video()`: Upload a video file in chunks
- `get_upload_status()`: Check the status of an upload

### Transcoding

#### `TranscodeClient`

Client for video transcoding operations.

Key methods:
- `transcode_video()`: Request transcoding of a video
- `cancel_transcoding()`: Cancel an in-progress job
- `create_output_format()`: Helper to create output format specifications

### Status Monitoring

#### `StatusClient`

Client for job status monitoring.

Key methods:
- `get_job_status()`: Get current job status
- `stream_job_status()`: Stream real-time status updates
- `wait_for_completion()`: Wait for a job to complete
- `list_jobs()`: List jobs with filtering and pagination
- `list_all_jobs()`: List all jobs matching a filter

#### `JobStatus`

Class representing a job status with properties:
- `status`: String status ('queued', 'in_progress', 'completed', 'failed', 'cancelled')
- `progress`: Integer percentage of completion (0-100)
- `is_completed`, `is_failed`, `is_active`, `is_cancelled`: Status helper properties
- `output_files`: List of output file information (for completed jobs)
- `error_message`: Error details (for failed jobs)

## Error Handling

The client library provides specific exception types for different error scenarios:

```python
from transcoder.services.base import TranscoderError, ConnectionError, ServiceError
from transcoder.services.upload import UploadError
from transcoder.services.transcode import TranscodeError
from transcoder.services.status import StatusError

try:
    # Client operations...
except ConnectionError as e:
    # Handle connection issues
    print(f"Connection error: {e}")
except UploadError as e:
    # Handle upload-specific errors
    print(f"Upload error: {e}")
except TranscodeError as e:
    # Handle transcoding-specific errors
    print(f"Transcode error: {e}")
except StatusError as e:
    # Handle status-specific errors
    print(f"Status error: {e}")
except ServiceError as e:
    # Handle other service errors
    print(f"Service error: {e.code} - {e.details}")
except TranscoderError as e:
    # Handle any other client errors
    print(f"Client error: {e}")
```

Best practices:
1. Always handle specific exceptions before general ones
2. Always close the client when done (or use with context manager)
3. Use timeouts for operations that might take a long time
4. Check status codes and error messages for debugging

## Contributing

Contributions are welcome! Here's how you can help:

1. **Report bugs**: Create an issue describing the bug and steps to reproduce
2. **Suggest features**: Open an issue to suggest new functionality
3. **Submit pull requests**: Fork the repository, make changes, and submit a PR

Development setup:
```bash
# Clone the repository
git clone https://github.com/yourusername/youtube-transcode-client.git
cd youtube-transcode-client

# Create a virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install development dependencies
pip install -e ".[dev]"

# Run tests
pytest
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## Related Projects

- [YouTube Transcode Service](https://github.com/yourusername/youtube-transcode-service) - The Java service this client communicates with

