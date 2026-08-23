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
        Set<String> existingNames = mapPlaceRepository.findAll()
                .stream()
                .map(MapPlace::getName)
                .collect(Collectors.toSet());

        List<MapPlace> seeds = List.of(
                // 食堂
                place("CANTEEN", "CANTEEN", "学一食堂", "河北建筑工程学院朝阳校区学生食堂。", "朝阳校区", "114.8997410", "40.7555380", 1),
                place("CANTEEN", "CANTEEN", "学二食堂", "河北建筑工程学院朝阳校区学生食堂。", "朝阳校区", "114.8998880", "40.7569850", 2),
                place("CANTEEN", "CANTEEN", "学三食堂", "河北建筑工程学院朝阳校区学生食堂。", "朝阳校区", "114.8999480", "40.7541340", 3),
                place("CANTEEN", "CANTEEN", "蜜雪冰城", "校园商业餐饮服务点。", "朝阳校区", "114.9000290", "40.7561210", 4),
                // 运动场
                place("SPORTS", "SPORTS_GROUND", "东区运动场", "东区综合运动场地。", "朝阳校区东区", "114.8995120", "40.7585850", 10),
                place("SPORTS", "SPORTS_GROUND", "篮球场", "室外篮球场。", "朝阳校区", "114.8964830", "40.7521780", 11),
                place("SPORTS", "SPORTS_GROUND", "田径场", "标准田径运动场。", "朝阳校区", "114.8976100", "40.7524130", 12),
                place("SPORTS", "SPORTS_GROUND", "排球场", "室外排球场。", "朝阳校区", "114.8975750", "40.7530420", 13),
                // 教学/服务
                place("TEACHING", "TEACHING_BUILDING", "明德楼", "教学楼。", "朝阳校区", "114.8989990", "40.7575830", 20),
                place("TEACHING", "TEACHING_BUILDING", "崇德楼", "教学楼。", "朝阳校区", "114.8963100", "40.7586740", 21),
                place("TEACHING", "TEACHING_BUILDING", "图书馆", "学校图书馆。", "朝阳校区", "114.8970210", "40.7558120", 22),
                place("TEACHING", "TEACHING_BUILDING", "综合服务楼", "校园综合服务楼。", "朝阳校区", "114.8989650", "40.7569360", 23),
                place("TEACHING", "TEACHING_BUILDING", "机械工程学院", "机械工程学院教学科研楼。", "朝阳校区", "114.8982000", "40.7562000", 24),
                place("TEACHING", "TEACHING_BUILDING", "经济管理学院", "经济管理学院教学科研楼。", "朝阳校区", "114.8974000", "40.7568000", 25),
                // 其他
                place("OTHER", "LANDSCAPE", "中心景观湖", "校园景观休闲区", "朝阳校区景观区", "114.8992100", "40.7559200", 30),
                place("OTHER", "ADMIN_BUILDING", "明志行政楼", "校园行政办公楼", "朝阳校区行政区", "114.8985200", "40.7566200", 31),
                place("OTHER", "HOSPITAL", "朝阳校医院", "校园医疗服务点", "朝阳校区生活服务区", "114.8979300", "40.7549600", 32)
        );

        seeds.stream()
                .filter(place -> !existingNames.contains(place.getName()))
                .forEach(mapPlaceRepository::save);
    }

    private MapPlace place(
            String sceneType,
            String placeType,
            String name,
            String description,
            String locationDesc,
            String longitude,
            String latitude,
            int sortOrder
    ) {
        MapPlace place = new MapPlace();
        place.setSceneType(sceneType);
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
