package com.example.appbackend.config;

import com.example.appbackend.entity.MapPlace;
import com.example.appbackend.repository.MapPlaceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MapPlaceDataInitializer implements ApplicationRunner {

    private final MapPlaceRepository mapPlaceRepository;

    public MapPlaceDataInitializer(MapPlaceRepository mapPlaceRepository) {
        this.mapPlaceRepository = mapPlaceRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> existingOtherNames = mapPlaceRepository.findBySceneTypeOrderBySortOrderAscIdAsc("OTHER")
                .stream()
                .map(MapPlace::getName)
                .collect(Collectors.toSet());
        List<MapPlace> seeds = List.of(
                otherPlace("中心景观湖", "LANDSCAPE", "校园景观休闲区", "朝阳校区景观区", "114.8992100", "40.7559200", 10),
                otherPlace("明志行政楼", "ADMIN_BUILDING", "校园行政办公楼", "朝阳校区行政区", "114.8985200", "40.7566200", 20),
                otherPlace("朝阳校医院", "HOSPITAL", "校园医疗服务点", "朝阳校区生活服务区", "114.8979300", "40.7549600", 30)
        );
        seeds.stream()
                .filter(place -> !existingOtherNames.contains(place.getName()))
                .forEach(mapPlaceRepository::save);
    }

    private MapPlace otherPlace(
            String name,
            String placeType,
            String description,
            String locationDesc,
            String longitude,
            String latitude,
            int sortOrder
    ) {
        MapPlace place = new MapPlace();
        place.setSceneType("OTHER");
        place.setPlaceType(placeType);
        place.setName(name);
        place.setDescription(description);
        place.setLocationDesc(locationDesc);
        place.setLongitude(new BigDecimal(longitude));
        place.setLatitude(new BigDecimal(latitude));
        place.setStatus("ENABLED");
        place.setMapVisible(true);
        place.setSortOrder(sortOrder);
        return place;
    }
}
