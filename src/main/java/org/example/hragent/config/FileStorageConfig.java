package org.example.hragent.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储客户端配置
 */
@Configuration
public class FileStorageConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(FileStorageProperties props) {
        COSCredentials cred = new BasicCOSCredentials(props.getAccessKey(), props.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(props.getRegion()));
        return new COSClient(cred, clientConfig);
    }
}
