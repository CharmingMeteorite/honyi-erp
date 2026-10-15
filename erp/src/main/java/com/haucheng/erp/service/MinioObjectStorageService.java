package com.haucheng.erp.service;

import com.haucheng.erp.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MinioObjectStorageService {
    @Value("${erp.minio.endpoint:}") private String endpoint;
    @Value("${erp.minio.bucket:}") private String bucket;
    @Value("${erp.minio.access-key:}") private String accessKey;
    @Value("${erp.minio.secret-key:}") private String secretKey;

    private MinioClient client() {
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw BusinessException.conflict("对象存储未配置，请联系管理员");
        }
        if (!(endpoint.startsWith("https://") || endpoint.startsWith("http://")) || !bucket.matches("[a-z0-9][a-z0-9.-]{2,62}")) {
            throw BusinessException.conflict("对象存储配置不正确，请联系管理员");
        }
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey,secretKey).build();
    }

    private MinioClient ready() throws Exception {
        MinioClient client = client();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return client;
    }

    /** Read-only readiness probe; never creates a missing bucket. */
    public boolean isAvailable() {
        try {
            return client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception ex) {
            log.warn("对象存储健康检查失败，错误类型={}", ex.getClass().getSimpleName());
            return false;
        }
    }

    public void put(String key, byte[] bytes, String type) {
        try {
            ready().putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes),bytes.length,-1).contentType(type).build());
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { log.warn("对象上传失败，键={}，错误类型={}",key,ex.getClass().getSimpleName()); throw BusinessException.conflict("对象存储不可用，上传失败"); }
    }

    public byte[] get(String key, long expectedSize) {
        try (InputStream stream = ready().getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = stream.readNBytes((int)Math.min(expectedSize + 1, 20L * 1024 * 1024 + 1));
            if (bytes.length != expectedSize) throw BusinessException.conflict("附件对象大小不一致，请联系管理员");
            return bytes;
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { log.warn("对象下载失败，键={}，错误类型={}",key,ex.getClass().getSimpleName()); throw BusinessException.conflict("附件对象不存在或对象存储不可用"); }
    }

    public void remove(String key) {
        try { ready().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { log.warn("对象删除失败，键={}，错误类型={}，错误码={}",key,ex.getClass().getSimpleName(),
                ex instanceof ErrorResponseException error ? error.errorResponse().code() : "n/a"); throw BusinessException.conflict("对象存储不可用，删除待重试"); }
    }
}
