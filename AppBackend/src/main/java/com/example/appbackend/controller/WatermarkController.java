package com.example.appbackend.controller;

import com.example.appbackend.entity.WatermarkHistory;
import com.example.appbackend.entity.WatermarkHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/history")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class WatermarkController {

    @Autowired
    private WatermarkHistoryRepository repository;

    @GetMapping("/list")
    public Map<String, Object> getHistoryList() {
        List<WatermarkHistory> data = repository.findAllByOrderByIdDesc();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", data);
        return response;
    }

    @PostMapping("/add")
    public Map<String, Object> addHistory(@RequestBody WatermarkHistory newRecord) {
        repository.save(newRecord);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "添加成功");
        return response;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteHistory(@PathVariable("id") Long id) {
        repository.deleteById(id);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "删除成功");
        return response;
    }

    @PostMapping("/aiRemove")
    public Map<String, Object> aiRemoveWatermark(@RequestParam("image") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            byte[] fileBytes = file.getBytes();

            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (originalImage == null) {
                response.put("code", 400);
                response.put("msg", "无法解析图片格式");
                return response;
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            int maxSize = 1024;
            double scale = 1.0;
            if (width > maxSize || height > maxSize) {
                scale = Math.min((double) maxSize / width, (double) maxSize / height);
            }
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);

            Image scaledInstance = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resizedImage.createGraphics();
            g2d.drawImage(scaledInstance, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            // 【极重要修复 1】：必须是 data:image 前缀！
            String dataUrl = "data:image/png;base64," + base64Image;

            System.out.println("【调试1】去水印请求发起，Base64 长度: " + dataUrl.length());

            // 【你自己的百炼专属 Key】
            String apiKey = "sk-ws-H.EPYRPPL.1JmV.MEUCIQCKbGSoYRNDuijfOWZVyT67wkKEOoY5jb3LNDed9B5NcQIgAXA8SskzqdKN0Zrp4bntl40Cl9Xmr0UNSVzUEUTV9xw"; 

            // 【极重要修复 2】：用最标准的老版地址！
            String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation";

            Map<String, Object> input = new HashMap<>();
            input.put("image", dataUrl); // 用带前缀的
            input.put("prompt", "去除图片上的水印文字和LOGO，清理背景痕迹，智能补全被遮挡的图像细节，保持原图色彩");

            Map<String, Object> requestBody = new HashMap<>();
            // 【极重要修复 3】：必须是全称包含 -v1！
            requestBody.put("model", "wanx2.1-imageedit-v1"); 
            requestBody.put("input", input);

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(requestBody);
            System.out.println("【调试2】发送 JSON 长度: " + jsonBody.length());

            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // 如果成功
            if (httpResponse.statusCode() == 200) {
                Map body = mapper.readValue(httpResponse.body(), Map.class);
                Map output = (Map) body.get("output");
                String taskId = output != null ? (String) output.get("task_id") : null;

                if (taskId != null) {
                    String taskUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
                    Map resultMap = null;
                    int tryCount = 0;
                    
                    while (tryCount < 60) { 
                        tryCount++;
                        Thread.sleep(2000);
                        
                        HttpRequest getRequest = HttpRequest.newBuilder()
                                .uri(URI.create(taskUrl))
                                .header("Authorization", "Bearer " + apiKey)
                                .timeout(Duration.ofSeconds(30))
                                .GET()
                                .build();
                        
                        HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
                        if (getResponse.statusCode() == 200) {
                            Map taskBody = mapper.readValue(getResponse.body(), Map.class);
                            Map taskOutput = (Map) taskBody.get("output");
                            String status = taskOutput != null ? (String) taskOutput.get("task_status") : null;
                            
                            if ("SUCCEEDED".equals(status)) {
                                resultMap = taskBody;
                                break;
                            } else if ("FAILED".equals(status)) {
                                response.put("code", 500);
                                response.put("msg", "AI 去水印任务失败");
                                return response;
                            }
                        }
                    }

                    if (resultMap != null) {
                        Map resultOutput = (Map) resultMap.get("output");
                        Map resultsArr = (Map) ((List) resultOutput.get("results")).get(0);
                        String resultImgUrl = (String) resultsArr.get("url");
                        
                        response.put("code", 200);
                        response.put("data", Map.of("url", resultImgUrl));
                        return response;
                    } else {
                        response.put("code", 500);
                        response.put("msg", "AI 去水印任务处理超时");
                    }
                } else {
                    response.put("code", 500);
                    response.put("msg", "未获取到阿里云任务ID");
                }
            } else {
                // 打印具体错误，方便排查
                System.out.println("【错误日志】阿里云返回状态码: " + httpResponse.statusCode() + ", 内容: " + httpResponse.body());
                response.put("code", 500);
                response.put("msg", "调用阿里云接口失败，状态码: " + httpResponse.statusCode() + ", 响应: " + httpResponse.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.put("code", 500);
            response.put("msg", "服务异常: " + e.getMessage());
        }
        return response;
    }
}