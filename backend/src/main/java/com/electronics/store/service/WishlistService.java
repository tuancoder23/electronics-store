package com.electronics.store.service;

import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.WishlistItemResponse;

public interface WishlistService {
    PagedResponse<WishlistItemResponse> getCurrentUserWishlist(int page, int size);
    WishlistItemResponse addProduct(Long productId);
    void removeProduct(Long productId);
}
