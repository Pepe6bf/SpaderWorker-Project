package site.devtown.spadeworker.domain.file.service;

import org.springframework.web.multipart.MultipartFile;
import site.devtown.spadeworker.domain.file.constant.ImageFileType;

public interface ImageFileService {
    String uploadFile(
            ImageFileType imageFileType,
            MultipartFile fileData
    ) throws Exception;

    String getStoredFileName(
            ImageFileType imageFileType,
            String originalFileName
    );

    String getFileStoredFullPath(
            ImageFileType imageFileType,
            String storedFileName
    );

    void deleteFile(String fileStoredFullPath);
}
