package com.example.appbackend.service;

import com.example.appbackend.dto.FacilityTypeItem;

import java.util.List;

public interface FacilityTypeService {

    List<FacilityTypeItem> listTypes();

    void saveTypes(List<FacilityTypeItem> types);

    String getLabel(Integer type);

    boolean isKnown(Integer type);
}
