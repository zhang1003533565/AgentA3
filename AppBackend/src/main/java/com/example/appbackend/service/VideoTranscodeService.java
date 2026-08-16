package com.example.appbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 视频编码兼容性服务。
 * 通过命令行调用 ffprobe / ffmpeg 检测并转码视频为 H.264，
 * 解决 H.265/HEVC 视频在浏览器中只有声音没有画面的问题。
 *
 * FFmpeg 未安装时优雅降级：仅记录警告，不阻塞上传流程。
 */
@Service
public class VideoTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeService.class);

    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "webm", "mkv", "flv");

    /** ffprobe 报告的 H.264 编码名称 */
    private static final Set<String> H264_CODECS = Set.of("h264", "avc1");

    private final boolean ffmpegAvailable;
    private final boolean ffprobeAvailable;

    public VideoTranscodeService() {
        this.ffmpegAvailable = checkCommand("ffmpeg", "-version");
        this.ffprobeAvailable = checkCommand("ffprobe", "-version");
        if (!ffmpegAvailable || !ffprobeAvailable) {
            log.warn("FFmpeg/FFprobe 未安装或不在 PATH 中。视频转码功能已禁用。"
                    + "上传的 H.265 视频可能在浏览器中只有声音没有画面。"
                    + "安装 FFmpeg: https://ffmpeg.org/download.html");
        }
    }

    /** 根据文件名扩展名判断是否为视频文件 */
    public boolean isVideoFile(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return VIDEO_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }

    /**
     * 确保视频为 H.264 编码。如果不是，转码并替换原文件。
     * FFmpeg 不可用时返回 false（不阻塞上传流程）。
     *
     * @param videoPath 视频文件路径
     * @return true 表示文件已是 H.264 或已成功转码
     */
    public boolean ensureH264(Path videoPath) {
        if (!ffprobeAvailable || !ffmpegAvailable) {
            return false;
        }
        if (!Files.isRegularFile(videoPath)) {
            log.warn("视频文件不存在，跳过转码: {}", videoPath);
            return false;
        }
        try {
            if (isH264(videoPath)) {
                log.debug("视频已是 H.264 编码，跳过转码: {}", videoPath.getFileName());
                return true;
            }
            log.info("检测到非 H.264 视频，开始转码: {}", videoPath.getFileName());
            return transcodeToH264(videoPath);
        } catch (Exception e) {
            log.error("视频转码失败 ({}): {}", videoPath.getFileName(), e.getMessage());
            return false;
        }
    }

    // ---- 内部方法 ----

    /**
     * 使用 ffprobe 检测第一个视频流的编码名称。
     * 命令: ffprobe -v error -select_streams v:0 -show_entries stream=codec_name
     *        -of default=noprint_wrappers=1:nokey=1 <file>
     */
    private boolean isH264(Path videoPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffprobe 超时");
        }
        String codec = output.toLowerCase();
        log.debug("视频编码: {} -> {}", videoPath.getFileName(), codec);
        return H264_CODECS.contains(codec);
    }

    /**
     * 使用 ffmpeg 将视频转码为 H.264 + AAC。
     * 先输出到临时文件，成功后原子替换原文件。
     *
     * 命令: ffmpeg -y -i <input>
     *       -c:v libx264 -preset fast -crf 23
     *       -c:a aac -b:a 128k
     *       -movflags +faststart -pix_fmt yuv420p <output>
     */
    private boolean transcodeToH264(Path videoPath) throws IOException, InterruptedException {
        Path tempFile = videoPath.resolveSibling(videoPath.getFileName() + ".transcoding.mp4");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    "-pix_fmt", "yuv420p",
                    tempFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String ffmpegOutput = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                Files.deleteIfExists(tempFile);
                throw new IOException("ffmpeg 转码超时（5分钟）");
            }

            if (process.exitValue() != 0) {
                Files.deleteIfExists(tempFile);
                String tail = ffmpegOutput.length() > 500
                        ? ffmpegOutput.substring(ffmpegOutput.length() - 500) : ffmpegOutput;
                log.error("ffmpeg 转码失败 (exit={}): {}", process.exitValue(), tail);
                return false;
            }

            // 原子替换：先备份原文件，转码成功后再删除备份
            Path backupFile = videoPath.resolveSibling(videoPath.getFileName() + ".original_backup");
            Files.move(videoPath, backupFile);
            try {
                Files.move(tempFile, videoPath);
                Files.deleteIfExists(backupFile);
            } catch (Exception e) {
                // 替换失败时恢复原文件
                Files.move(backupFile, videoPath);
                Files.deleteIfExists(tempFile);
                throw new IOException("转码文件替换失败", e);
            }

            log.info("视频转码完成 (H.264): {}", videoPath.getFileName());
            return true;
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    private boolean checkCommand(String command, String arg) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, arg);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
