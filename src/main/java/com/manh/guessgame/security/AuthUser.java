package com.manh.guessgame.security;

/** Thông tin user đã đăng nhập, lấy từ JWT (không cần query DB mỗi request). */
public record AuthUser(Long id, String username) {
}
