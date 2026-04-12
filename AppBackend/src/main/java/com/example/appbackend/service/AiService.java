package com.example.appbackend.service;

import com.example.appbackend.dto.AiWriteDTO;

public interface AiService {

    AiWriteDTO.WriteResponse write(AiWriteDTO.WriteRequest request);
}
