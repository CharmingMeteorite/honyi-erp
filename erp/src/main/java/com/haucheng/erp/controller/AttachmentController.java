package com.haucheng.erp.controller;

import com.haucheng.erp.auth.RequirePermission;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.service.AttachmentService;
import com.haucheng.erp.vo.AttachmentView;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@RequirePermission("attachment.view")
public class AttachmentController {
    private final AttachmentService service;

    @GetMapping
    public ApiResponse<List<AttachmentView>> list(@RequestParam String businessType,@RequestParam Long businessId) {
        return ApiResponse.success(service.list(businessType,businessId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AttachmentView> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("attachment.upload")
    public ApiResponse<AttachmentView> upload(@RequestParam String businessType,@RequestParam Long businessId,
            @RequestParam String clientRequestId,@RequestPart MultipartFile file) {
        return ApiResponse.success(service.upload(businessType,businessId,clientRequestId,file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id,@RequestParam(defaultValue="false") boolean preview) {
        AttachmentService.Download download = service.download(id);
        AttachmentView metadata = download.metadata();
        boolean image = metadata.contentType().startsWith("image/");
        ContentDisposition disposition = (preview && image ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(metadata.displayName(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(download.bytes().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","default-src 'none'; sandbox")
                .body(download.bytes());
    }

    @DeleteMapping("/{id}")
    @RequirePermission("attachment.delete")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
}
