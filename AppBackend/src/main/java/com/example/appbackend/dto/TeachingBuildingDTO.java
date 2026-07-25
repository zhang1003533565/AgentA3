package com.example.appbackend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeachingBuildingDTO {
    private Long id;
    private String name;
    private String zone;
    private String image;
    private String description;
    private Integer status;
    private Integer floorCount;
    private Integer classroomCount;
    private Integer totalSeatCount;
    private Integer smartClassroomCount;
    private Integer activeClassroomCount;
    private Integer freeClassroomCount;
    private List<ClassroomDTO> classrooms = new ArrayList<>();
}
