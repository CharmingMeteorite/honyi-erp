package com.haucheng.erp.service;
import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.config.RequestIdFilter;
import com.haucheng.erp.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
@Service @RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper mapper;
    public void record(String type, String target, String detail) {
        var user = CurrentUserContext.get();
        mapper.insert(user == null ? null : user.getUserId(), type, target, detail, currentRequestId());
    }
    public String currentRequestId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : (String) attributes.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }
}
