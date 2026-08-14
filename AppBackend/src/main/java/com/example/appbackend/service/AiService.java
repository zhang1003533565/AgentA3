package com.example.appbackend.service;

import com.example.appbackend.dto.AiWriteDTO;

import java.util.List;

public interface AiService {

    AiWriteDTO.WriteResponse write(AiWriteDTO.WriteRequest request);

    List<AiWriteDTO.ModelOption> listAvailableTextModels();
}
