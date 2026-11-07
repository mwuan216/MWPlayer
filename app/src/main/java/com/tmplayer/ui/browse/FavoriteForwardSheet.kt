package com.tmplayer.ui.browse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ChatKind
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.FavoriteForwarder
import com.tmplayer.data.FavoriteVideoRecord
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.Td
import com.tmplayer.data.valueOrNull
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSection
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.FocusOnOpen
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.components.showMessage
import com.tmplayer.ui.icons.TmIcons
import dev.g000sha256.tdl.dto.ForumTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PickerStage {
    Main,
    SelectChat,
    SelectTopic,
}

@Composable
internal fun FavoriteForwardSheet(
    settings: SettingsStore,
    chats: List<ChatSummary>,
    favorites: List<FavoriteVideoRecord>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    val forwardEnabled by settings.favoriteForwardEnabled.collectAsState(initial = false)
    val forwardChatId by settings.favoriteForwardChatId.collectAsState(initial = 0L)
    val forwardTopicId by settings.favoriteForwardTopicId.collectAsState(initial = 0)
    val forwardChatTitle by settings.favoriteForwardChatTitle.collectAsState(initial = "")
    val forwardTopicTitle by settings.favoriteForwardTopicTitle.collectAsState(initial = "")

    var stage by remember { mutableStateOf(PickerStage.Main) }
    var chatFilterQuery by remember { mutableStateOf("") }
    var selectedChatForTopic by remember { mutableStateOf<ChatSummary?>(null) }
    var availableTopics by remember { mutableStateOf<List<ForumTopic>>(emptyList()) }
    var loadingTopics by remember { mutableStateOf(false) }
    var forwardingCount by remember { mutableIntStateOf(0) }

    val title = when (stage) {
        PickerStage.Main -> "Cài đặt chuyển tiếp Yêu thích"
        PickerStage.SelectChat -> "Chọn kho tin nhắn / nhóm"
        PickerStage.SelectTopic -> "Chọn Topic trong ${selectedChatForTopic?.title.orEmpty()}"
    }

    val note = when (stage) {
        PickerStage.Main -> "Tự động chuyển tiếp (Forward) các video yêu thích về kho tin nhắn hoặc nhóm/topic bạn chọn (drop author = true)."
        PickerStage.SelectChat -> "Chọn Tin nhắn đã lưu (Saved Messages), nhóm hoặc kênh để nhận video."
        PickerStage.SelectTopic -> "Nhóm này có chia chủ đề (Forum Topic). Chọn một chủ đề nhận video."
    }

    ChoiceSheet(
        title = title,
        note = note,
        onDismiss = onDismiss,
        onClose = {
            if (stage != PickerStage.Main) {
                stage = if (stage == PickerStage.SelectTopic) PickerStage.SelectChat else PickerStage.Main
            } else {
                onDismiss()
            }
        },
        ignoreRelease = !isTouch(),
    ) {
        ChoiceList {
            when (stage) {
                PickerStage.Main -> {
                    ChoiceSection("Tự động chuyển tiếp")
                    ChoiceLine(
                        title = if (forwardEnabled) "Đang bật tự động chuyển tiếp" else "Đang tắt chuyển tiếp",
                        detail = if (forwardEnabled) "Bấm để tắt" else "Bấm để bật",
                        selected = forwardEnabled,
                        icon = if (forwardEnabled) TmIcons.CheckCircle else TmIcons.PlayCircle,
                        modifier = Modifier.focusRequester(first),
                        onClick = {
                            scope.launch {
                                settings.setFavoriteForward(
                                    enabled = !forwardEnabled,
                                    chatId = forwardChatId,
                                    topicId = forwardTopicId,
                                    chatTitle = forwardChatTitle,
                                    topicTitle = forwardTopicTitle,
                                )
                            }
                        },
                    )

                    ChoiceSection("Kho tin nhắn nhận video")
                    val destTitle = if (forwardChatId != 0L) {
                        if (forwardTopicId != 0 && forwardTopicTitle.isNotBlank()) {
                            "$forwardChatTitle  ›  $forwardTopicTitle"
                        } else {
                            forwardChatTitle
                        }
                    } else {
                        "Chưa thiết lập đích đến"
                    }
                    ChoiceLine(
                        title = destTitle,
                        detail = "Bấm để chọn kho tin nhắn, nhóm hoặc topic",
                        icon = TmIcons.ArrowForward,
                        onClick = {
                            stage = PickerStage.SelectChat
                        },
                    )

                    if (forwardChatId != 0L) {
                        ChoiceLine(
                            title = "Xóa cài đặt đích đến",
                            detail = "Hủy bỏ kho tin nhắn đã chọn",
                            icon = TmIcons.Delete,
                            onClick = {
                                scope.launch {
                                    settings.clearFavoriteForward()
                                    showMessage(context, "Đã xóa cài đặt chuyển tiếp")
                                }
                            },
                        )
                    }

                    ChoiceSection("Hành động")
                    val isForwarding = forwardingCount > 0
                    val forwardActionTitle = if (isForwarding) {
                        "Đang chuyển tiếp $forwardingCount/${favorites.size} video..."
                    } else {
                        "Chuyển tiếp tất cả ${favorites.size} video hiện tại"
                    }
                    val canForwardAll = forwardChatId != 0L && favorites.isNotEmpty() && !isForwarding
                    ChoiceLine(
                        title = forwardActionTitle,
                        detail = if (forwardChatId == 0L) "Cần chọn kho đích trước" else "Chuyển tiếp toàn bộ danh sách yêu thích sang kho này (bỏ người gửi)",
                        icon = TmIcons.Download,
                        enabled = canForwardAll,
                        onClick = {
                            if (canForwardAll) {
                                scope.launch {
                                    forwardingCount = 1
                                    val success = FavoriteForwarder.forwardAll(
                                        favorites = favorites,
                                        targetChatId = forwardChatId,
                                        topicId = forwardTopicId,
                                        onProgress = { current, _ ->
                                            forwardingCount = current
                                        },
                                    )
                                    forwardingCount = 0
                                    showMessage(context, "Đã chuyển tiếp $success/${favorites.size} video thành công!")
                                }
                            }
                        },
                    )
                }

                PickerStage.SelectChat -> {
                    ChoiceLine(
                        title = "Quay lại",
                        icon = TmIcons.ArrowBack,
                        onClick = { stage = PickerStage.Main },
                    )

                    ChoiceSection("Kho tin nhắn cá nhân")
                    ChoiceLine(
                        title = "Tin nhắn đã lưu (Saved Messages)",
                        detail = "Lưu trữ cá nhân của bạn trên Telegram",
                        icon = TmIcons.Bookmark,
                        selected = forwardChatId != 0L && forwardChatTitle == "Tin nhắn đã lưu",
                        onClick = {
                            scope.launch {
                                val savedChat = chats.firstOrNull { it.kind == ChatKind.Saved }
                                val id = savedChat?.id ?: runCatching { Td.myId() }.getOrDefault(0L)
                                if (id != 0L) {
                                    settings.setFavoriteForward(
                                        enabled = true,
                                        chatId = id,
                                        topicId = 0,
                                        chatTitle = "Tin nhắn đã lưu",
                                        topicTitle = "",
                                    )
                                    showMessage(context, "Đã chọn đích đến: Tin nhắn đã lưu")
                                    stage = PickerStage.Main
                                }
                            }
                        },
                    )

                    ChoiceSection("Nhóm & Kênh trò chuyện")
                    val filteredChats = remember(chats, chatFilterQuery) {
                        chats.filter { it.kind != ChatKind.Saved && (chatFilterQuery.isBlank() || it.title.contains(chatFilterQuery, ignoreCase = true)) }
                    }

                    filteredChats.take(30).forEach { chat ->
                        ChoiceLine(
                            title = chat.title,
                            detail = chat.kind.label,
                            icon = if (chat.kind == ChatKind.Channel) TmIcons.Trailer else TmIcons.Folder,
                            selected = chat.id == forwardChatId && forwardTopicId == 0,
                            onClick = {
                                scope.launch {
                                    selectedChatForTopic = chat
                                    loadingTopics = true
                                    val topics: List<ForumTopic> = withContext(Dispatchers.IO) {
                                        try {
                                            val td = Td.client
                                            val page = td.getForumTopics(chat.id, "", 0, 0L, 0, 100).valueOrNull
                                            page?.topics?.toList().orEmpty()
                                        } catch (_: Exception) {
                                            emptyList<ForumTopic>()
                                        }
                                    }
                                    loadingTopics = false
                                    if (topics.isNotEmpty()) {
                                        availableTopics = topics
                                        stage = PickerStage.SelectTopic
                                    } else {
                                        settings.setFavoriteForward(
                                            enabled = true,
                                            chatId = chat.id,
                                            topicId = 0,
                                            chatTitle = chat.title,
                                            topicTitle = "",
                                        )
                                        showMessage(context, "Đã chọn: ${chat.title}")
                                        stage = PickerStage.Main
                                    }
                                }
                            },
                        )
                    }
                }

                PickerStage.SelectTopic -> {
                    ChoiceLine(
                        title = "Quay lại danh sách chat",
                        icon = TmIcons.ArrowBack,
                        onClick = { stage = PickerStage.SelectChat },
                    )

                    ChoiceSection("Chọn chủ đề (Topic)")
                    val chat = selectedChatForTopic
                    if (chat != null) {
                        ChoiceLine(
                            title = "Chung (Không chọn topic riêng)",
                            detail = "Gửi vào luồng chung của nhóm",
                            selected = chat.id == forwardChatId && forwardTopicId == 0,
                            icon = TmIcons.Grid,
                            onClick = {
                                scope.launch {
                                    settings.setFavoriteForward(
                                        enabled = true,
                                        chatId = chat.id,
                                        topicId = 0,
                                        chatTitle = chat.title,
                                        topicTitle = "",
                                    )
                                    showMessage(context, "Đã chọn: ${chat.title}")
                                    stage = PickerStage.Main
                                }
                            },
                        )

                        availableTopics.forEach { topic ->
                            val topicName = topic.info.name.ifBlank { "Topic #${topic.info.forumTopicId}" }
                            ChoiceLine(
                                title = topicName,
                                detail = "Topic ID: ${topic.info.forumTopicId}",
                                selected = chat.id == forwardChatId && forwardTopicId == topic.info.forumTopicId,
                                icon = TmIcons.Episodes,
                                onClick = {
                                    scope.launch {
                                        settings.setFavoriteForward(
                                            enabled = true,
                                            chatId = chat.id,
                                            topicId = topic.info.forumTopicId,
                                            chatTitle = chat.title,
                                            topicTitle = topicName,
                                        )
                                        showMessage(context, "Đã chọn: ${chat.title} › $topicName")
                                        stage = PickerStage.Main
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        FocusOnOpen(first)
    }
}
