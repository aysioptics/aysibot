package uz.kuponbot.kupon.dto;

public record AdminStatsDto(
    long totalUsers,
    long totalProducts,
    long totalVouchers,
    long activeVouchers,
    long usedVouchers,
    long expiredVouchers
) {
}