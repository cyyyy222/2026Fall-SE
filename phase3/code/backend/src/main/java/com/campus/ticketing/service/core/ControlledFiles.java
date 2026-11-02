package com.campus.ticketing.service.core;

import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.entity.StoredFileEntity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.core.env.Environment;
import org.springframework.core.io.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ControlledFiles {
  private final BusinessSupport b;
  private final Path root;

  public ControlledFiles(BusinessSupport b, Environment env) {
    this.b = b;
    root = Path.of(env.getProperty("app.data-dir", "data")).toAbsolutePath().normalize();
  }

  private Path path(String key) {
    if (!key.matches("(poster|export)/[0-9a-f-]{36}"))
      throw new IllegalArgumentException("Invalid internal storage key");
    Path file = root.resolve(key).normalize();
    if (!file.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
    return file;
  }

  public String imageType(byte[] bytes) {
    if (bytes.length > 5_000_000) throw error(413, "FILE_TOO_LARGE", "海报不得超过5,000,000字节");
    boolean png =
        bytes.length >= 8
            && Arrays.equals(
                Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    boolean jpeg =
        bytes.length >= 3
            && (bytes[0] & 255) == 255
            && (bytes[1] & 255) == 216
            && (bytes[2] & 255) == 255;
    if (!png && !jpeg) throw error(415, "UNSUPPORTED_MEDIA_TYPE", "只允许真实JPEG/PNG图片");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw error(415, "UNSUPPORTED_MEDIA_TYPE", "图片无法解码");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 40_000_000)
          throw error(422, "VALIDATION_FAILED", "图片像素数超过40,000,000上限");
        if (reader.read(0) == null) throw error(415, "UNSUPPORTED_MEDIA_TYPE", "图片损坏");
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw error(415, "UNSUPPORTED_MEDIA_TYPE", "图片损坏或格式不支持");
    }
    return png ? "image/png" : "image/jpeg";
  }

  public StoredFileEntity store(UUID owner, String kind, byte[] bytes, String contentType) {
    UUID id = id();
    String key = kind.toLowerCase(Locale.ROOT) + "/" + id;
    Path destination = path(key), temporary = null;
    try {
      Files.createDirectories(destination.getParent());
      temporary = Files.createTempFile(destination.getParent(), ".pending-", ".tmp");
      Files.write(temporary, bytes);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, destination);
      }
      return new StoredFileEntity(
          id, owner, kind, key, contentType, (long) bytes.length, hash(bytes), b.now(), null);
    } catch (IOException e) {
      throw error(503, "SERVICE_UNAVAILABLE", "受控文件存储不可写");
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
        }
    }
  }

  public DownloadResult download(StoredFileEntity file, String name, boolean attachment) {
    if (file.deletedAtMs() != null) throw error(404, "RESOURCE_NOT_FOUND", "文件不存在");
    Path location = path(file.storageKey());
    if (!Files.isRegularFile(location, LinkOption.NOFOLLOW_LINKS))
      throw error(503, "SERVICE_UNAVAILABLE", "文件暂不可读取");
    return new DownloadResult(
        new FileSystemResource(location),
        MediaType.parseMediaType(file.contentType()),
        name,
        attachment);
  }

  public void delete(StoredFileEntity file) {
    try {
      Files.deleteIfExists(path(file.storageKey()));
    } catch (IOException e) {
      throw error(503, "SERVICE_UNAVAILABLE", "文件清理暂时失败");
    }
  }
}
