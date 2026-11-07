package com.tmplayer.movie

import androidx.compose.runtime.Immutable

/** Nguồn phim hỗ trợ */
enum class MovieSource(val displayName: String, val badge: String) {
    KKPhim("KKPhim (Khuyên dùng)", "KKPhim"),
    VSMov("VSMov (Đa chất lượng)", "VSMov");

    companion object {
        fun fromString(value: String?): MovieSource {
            return when (value?.lowercase()) {
                "vsmov" -> VSMov
                else -> KKPhim
            }
        }
    }
}

/** Thông tin cơ bản một bộ phim trong danh sách */
@Immutable
data class MovieItem(
    val name: String,
    val originName: String = "",
    val slug: String,
    val thumbUrl: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val quality: String = "HD",
    val lang: String = "Vietsub",
    val type: String = "",
    val episodeCurrent: String = "",
    val rating: String = "",
    val source: MovieSource = MovieSource.KKPhim
)

/** Chi tiết bộ phim */
@Immutable
data class MovieDetail(
    val movie: MovieItem,
    val content: String = "",
    val time: String = "",
    val episodeTotal: String = "",
    val director: List<String> = emptyList(),
    val actor: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val servers: List<MovieServer> = emptyList()
)

/** Server chứa các tập phim */
@Immutable
data class MovieServer(
    val serverName: String,
    val episodes: List<MovieEpisode>
)

/** Tập phim */
@Immutable
data class MovieEpisode(
    val name: String,
    val slug: String,
    val linkM3u8: String = "",
    val linkEmbed: String = ""
)

/** Thông tin phụ đề */
@Immutable
data class MovieSubtitleTrack(
    val label: String,
    val url: String,
    val language: String = "vi",
    val isDefault: Boolean = false
)

/** Thông tin luồng stream để phát trong Video Player */
@Immutable
data class MovieStream(
    val streamUrl: String,
    val referer: String,
    val movieTitle: String,
    val episodeName: String,
    val serverName: String = "",
    val source: MovieSource = MovieSource.KKPhim,
    val subtitleUrl: String = "",
    val subtitles: List<MovieSubtitleTrack> = emptyList()
)

/** Danh mục phim */
@Immutable
data class FilterOption(
    val slug: String,
    val label: String
)

object MovieFilterData {
    val categories = listOf(
        FilterOption("", "Tất cả"),
        FilterOption("continue", "Đang xem dở"),
        FilterOption("watchlist", "Theo dõi"),
        FilterOption("history", "Lịch sử"),
        FilterOption("phim-moi-cap-nhat", "Mới cập nhật"),
        FilterOption("phim-bo", "Phim bộ"),
        FilterOption("phim-le", "Phim lẻ"),
        FilterOption("phim-chieu-rap", "Chiếu rạp"),
        FilterOption("hoat-hinh", "Hoạt hình"),
        FilterOption("tv-shows", "TV Shows"),
        FilterOption("subteam", "Subteam")
    )

    val genres = listOf(
        FilterOption("", "Tất cả thể loại"),
        FilterOption("hanh-dong", "Hành động"),
        FilterOption("tinh-cam", "Tình cảm"),
        FilterOption("co-trang", "Cổ trang"),
        FilterOption("hai-huoc", "Hài hước"),
        FilterOption("tam-ly", "Tâm lý"),
        FilterOption("khoa-hoc-vien-tuong", "Viễn tưởng"),
        FilterOption("kinh-di", "Kinh dị"),
        FilterOption("vo-thuat", "Võ thuật"),
        FilterOption("phieu-luu", "Phiêu lưu"),
        FilterOption("hinh-su", "Hình sự"),
        FilterOption("chien-tranh", "Chiến tranh"),
        FilterOption("hoc-duong", "Học đường"),
        FilterOption("gia-dinh", "Gia đình"),
        FilterOption("chinh-kich", "Chính kịch"),
        FilterOption("than-thoai", "Thần thoại")
    )

    val countries = listOf(
        FilterOption("", "Tất cả quốc gia"),
        FilterOption("trung-quoc", "Trung Quốc"),
        FilterOption("han-quoc", "Hàn Quốc"),
        FilterOption("au-my", "Âu Mỹ"),
        FilterOption("nhat-ban", "Nhật Bản"),
        FilterOption("viet-nam", "Việt Nam"),
        FilterOption("thai-lan", "Thái Lan"),
        FilterOption("dai-loan", "Đài Loan"),
        FilterOption("hong-kong", "Hồng Kông"),
        FilterOption("an-do", "Ấn Độ")
    )

    val years = listOf(
        FilterOption("", "Tất cả năm"),
        FilterOption("2026", "2026"),
        FilterOption("2025", "2025"),
        FilterOption("2024", "2024"),
        FilterOption("2023", "2023"),
        FilterOption("2022", "2022"),
        FilterOption("2021", "2021"),
        FilterOption("2020", "2020")
    )
}
