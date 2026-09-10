package com.electronics.store.service;

import com.electronics.store.dto.response.VnPayCreatePaymentResponse;
import com.electronics.store.dto.response.VnPayIpnResponse;
import com.electronics.store.dto.response.VnPayReturnResponse;
import org.springframework.util.MultiValueMap;

public interface VnPayService {
    VnPayCreatePaymentResponse createPaymentUrl(Long orderId, String clientIp);
    VnPayReturnResponse inspectReturn(MultiValueMap<String, String> parameters);
    VnPayIpnResponse processIpn(MultiValueMap<String, String> parameters);
}
