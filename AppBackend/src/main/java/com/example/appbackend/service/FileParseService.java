package com.example.appbackend.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface FileParseService {
    String parse(File file);

    String parse(MultipartFile file);
}
