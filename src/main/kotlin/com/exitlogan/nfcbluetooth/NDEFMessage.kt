package com.exitlogan.nfcbluetooth

import java.io.DataInputStream
import java.io.DataOutputStream

data class NDEFMessage(
        var mimeType: String,
        var data: String
)

fun DataOutputStream.write(message:NDEFMessage){
    writeUTF(message.mimeType)
    writeUTF(message.data)
}

fun DataInputStream.readNDEFMessage():NDEFMessage = NDEFMessage(
        mimeType = readUTF(),
        data = readUTF()
)