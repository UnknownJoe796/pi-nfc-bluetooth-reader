package com.exitlogan.nfcbluetooth

import com.lightningkite.kotlin.lifecycle.DisposeLifecycle
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.bluetooth.DiscoveryAgent
import javax.bluetooth.LocalDevice
import javax.bluetooth.UUID
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier

val uuid = UUID("00001101-0000-1000-8000-00805F9B34FB".filter { it.isLetterOrDigit() }, false)

public inline fun <R> StreamConnection.use(block: (StreamConnection) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        if (exception == null) close()
        else try {
            close()
        } catch (closeException: Throwable) {
            // cause.addSuppressed(closeException) // ignored here
        }
    }
}

inline fun StreamConnectionNotifier.acceptAndOpenData(
        use: (connection: StreamConnection, input: DataInputStream, output: DataOutputStream) -> Unit
) {
    acceptAndOpen().use { connection ->
        connection.openDataInputStream().use { input ->
            connection.openDataOutputStream().use { output ->
                use(
                        connection,
                        input,
                        output
                )
            }
        }
    }
}

fun main(vararg args: String) {
    var mode: NFCMode = NFCMode.READ
    var messageToWrite: NDEFMessage = NDEFMessage("", "")

    LocalDevice.getLocalDevice().discoverable = DiscoveryAgent.GIAC
    val notifier = Connector.open("btspp://localhost:" + uuid + ";name=RemoteBluetooth") as StreamConnectionNotifier
    while (true) {
        val lifecycle = DisposeLifecycle()
        try {
            println("Waiting for connection...")
            notifier.acceptAndOpenData { connection, input, output ->

                println("Connection made.")

                println("Starting NFC...")
                ndef(lifecycle, onCard = {
                    when (mode) {
                        NFCMode.READ -> {
                            println("Card detected.  Reading...")
                            if(it.isFormatted) {
                                it.getRecords().forEach {
                                    println(it)
                                    output.write(MessageType.READ_RECORD.ordinal)
                                    output.write(it)
                                    output.flush()
                                }
                            } else {
                                println("Non-formatted tag.")
                                output.write(MessageType.EMPTY_TAG_FOUND.ordinal)
                                output.flush()
                            }
                        }
                        NFCMode.WRITE -> {
                            println("Card detected.  Writing...")
                            it.setRecords(messageToWrite)
                            output.write(MessageType.WRITE_RECORD_SUCCESS.ordinal)
                            output.write(messageToWrite)
                            output.flush()
                        }
                    }
                })

                while (true) {
                    try {
                        val read = input.read()
                        if(read == -1) break
                        if(input.available() == 0) continue
                        try {
                            val type = MessageType.values()[read]
                            when (type) {
                                MessageType.SET_MODE_READ -> mode = NFCMode.READ
                                MessageType.SET_MODE_WRITE -> {
                                    mode = NFCMode.WRITE
                                    messageToWrite = input.readNDEFMessage()
                                }
                                else -> {
                                    println("Type $type not accepted")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            break
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }
                println("Closing...")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            lifecycle.dispose()
        }
    }
}