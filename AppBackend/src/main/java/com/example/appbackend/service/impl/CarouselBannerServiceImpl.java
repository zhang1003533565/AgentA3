package com.example.appbackend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.appbackend.entity.CarouselBanner;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.CarouselBannerRepository;
import com.example.appbackend.service.CarouselBannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class CarouselBannerServiceImpl implements CarouselBannerService {

    @Autowired
    private CarouselBannerRepository carouselBannerRepository;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.base-url}")
    private String ossBaseUrl;

    @Value("${file.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public CarouselBanner uploadBanner(MultipartFile file, String title, Integer sortOrder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "上传文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "只允许上传图片文件");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "图片大小不能超过5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String newFileName = UUID.randomUUID().toString() + suffix;
        String objectName = "carousel/" + newFileName;

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(bucketName, objectName, inputStream);
        } catch (IOException e) {
            ossClient.shutdown();
            throw new BusinessException(Result.ERROR_CODE, "文件上传失败: " + e.getMessage());
        }
        ossClient.shutdown();

        String imageUrl = ossBaseUrl + "/" + objectName;

        CarouselBanner banner = new CarouselBanner();
        banner.setImageUrl(imageUrl);
        banner.setTitle(title);
        banner.setSortOrder(sortOrder != null ? sortOrder : 0);
        banner.setEnabled(true);

        return carouselBannerRepository.save(banner);
    }

    @Override
    public List<CarouselBanner> getEnabledBanners() {
        return carouselBannerRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Override
    public List<CarouselBanner> getAllBanners() {
        return carouselBannerRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public CarouselBanner getBannerById(Long id) {
        return carouselBannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "轮播图不存在"));
    }

    @Override
    public CarouselBanner updateBanner(Long id, String title, Integer sortOrder, Boolean enabled) {
        CarouselBanner banner = getBannerById(id);

        if (title != null) {
            banner.setTitle(title);
        }
        if (sortOrder != null) {
            banner.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            banner.setEnabled(enabled);
        }

        return carouselBannerRepository.save(banner);
    }

    @Override
    public void deleteBanner(Long id) {
        CarouselBanner banner = getBannerById(id);

        String objectName = extractObjectName(banner.getImageUrl());
        if (objectName != null) {
            OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                ossClient.deleteObject(bucketName, objectName);
            } catch (Exception e) {
                // 文件删除失败不影响数据库删除
            } finally {
                ossClient.shutdown();
            }
        }

        carouselBannerRepository.delete(banner);
    }

    private String extractObjectName(String imageUrl) {
        if (imageUrl == null) return null;
        int idx = imageUrl.indexOf(".com/");
        return idx >= 0 ? imageUrl.substring(idx + 5) : null;
    }
}
