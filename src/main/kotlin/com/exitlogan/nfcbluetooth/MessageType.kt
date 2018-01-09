package com.exitlogan.nfcbluetooth

enum class MessageType {
    SET_MODE_READ,
    SET_MODE_WRITE,
    READ_RECORD,
    EMPTY_TAG_FOUND,
    WRITE_RECORD_SUCCESS,
    REQUEST_LOGS,
    LOGS,
}