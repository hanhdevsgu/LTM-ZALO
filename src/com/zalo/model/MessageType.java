package com.zalo.model;

/**
 * Định nghĩa chuẩn các loại thông điệp trao đổi giữa Client và Server
 * Định nghĩa các loại gói tin theo Đặc tả Zalo PC Việt Nam 100%
 */
public enum MessageType {
    LOGIN("login"),
    LOGIN_RESULT("login_result"),
    REGISTER("register"),
    REGISTER_RESULT("register_result"),
    DIRECT_MESSAGE("direct_message"),
    GROUP_MESSAGE("group_message"),
    GROUP_CREATE("group_create"),
    GROUP_JOIN("group_join"),
    USER_LIST("user_list"),
    STATUS_UPDATE("status_update"),
    SYSTEM("system"),
    DISCONNECT("disconnect"),
    PING("ping"),
    PONG("pong"),
    
    // Các tính năng mở rộng chuẩn Zalo PC Việt Nam
    RECALL_MESSAGE("recall_message"),       // Thu hồi tin nhắn
    TYPING_INDICATOR("typing_indicator"),   // Đang soạn tin nhắn (...)
    FILE_TRANSFER("file_transfer"),         // Gửi ảnh hoặc tệp tin đính kèm
    MY_CLOUD("my_cloud"),                   // Cloud của tôi (lưu trữ cá nhân)
    PIN_CONVERSATION("pin_conversation"),   // Ghim hội thoại lên đầu
    MESSAGE_STATUS("message_status");       // Trạng thái đã gửi, đã nhận, đã xem

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromString(String text) {
        for (MessageType t : MessageType.values()) {
            if (t.value.equalsIgnoreCase(text)) {
                return t;
            }
        }
        return SYSTEM;
    }
}
