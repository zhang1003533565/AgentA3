package com.example.appbackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosConfig {

    @Value("${tencent.cos.secret-id}")
    private String secretId;

    @Value("${tencent.cos.secret-key}")
    private String secretKey;

    @Value("${tencent.cos.region}")
    private String region;

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region cosRegion = new Region(region);
        ClientConfig clientConfig = new ClientConfig(cosRegion);
        return new COSClient(cred, clientConfig);
    }
}
