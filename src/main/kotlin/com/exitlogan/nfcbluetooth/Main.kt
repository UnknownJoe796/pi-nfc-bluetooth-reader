package com.exitlogan.nfcbluetooth

import com.lightningkite.kotlin.invokeAll
import com.lightningkite.kotlin.lifecycle.DisposeLifecycle
import com.lightningkite.kotlin.lifecycle.listen
import org.nfctools.ndef.NdefOperations
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentLinkedQueue
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
    val logs = ConcurrentLinkedQueue<String>()
    fun log(line:String){
        logs.add(line)
        println(line)
        if(logs.size > 50)
            logs.poll()
    }


    var mode: NFCMode = NFCMode.READ
    var messageToWrite: NDEFMessage = NDEFMessage("", "")

    val outerLifecycle = DisposeLifecycle()

//    val onCardListeners = ArrayList<(NdefOperations)->Unit>()
//    ndef(outerLifecycle, onCard = {
//        onCardListeners.invokeAll(it)
//    })

    LocalDevice.getLocalDevice().discoverable = DiscoveryAgent.GIAC
    val notifier = Connector.open("btspp://localhost:" + uuid + ";name=RemoteBluetooth") as StreamConnectionNotifier
    while (true) {
        val lifecycle = DisposeLifecycle()
        try {
            log("Waiting for bluetooth connection...")
            notifier.acceptAndOpenData { connection, input, output ->

                log("Bluetooth connection made.")

                log("Starting NFC...")
                ndef(
                        lifecycle = lifecycle,
                        onFailure = {
                            val writer = StringWriter()
                            it?.printStackTrace(PrintWriter(writer, true) )
                            log("NFC Failure occurred.")
                            log(writer.toString())
                        },
                        onAnyTag = {
                            log("Tag noted: $it")
                        },
                        onCard = {
                            when (mode) {
                                NFCMode.READ -> {
                                    log("Card detected.  Reading...")
                                    if(it.isFormatted) {
                                        it.getRecords().forEach {
                                            log("Record found: " + it)
                                            output.write(MessageType.READ_RECORD.ordinal)
                                            output.write(it)
                                            output.flush()
                                            log("Send completed.")
                                        }
                                    }  else {
                                        log("Non-formatted tag.")
                                        output.write(MessageType.EMPTY_TAG_FOUND.ordinal)
                                        output.flush()
                                        log("Send completed.")
                                    }
                                }
                                NFCMode.WRITE -> {
                                    log("Card detected.  Writing...")
                                    it.setRecords(messageToWrite)
                                    output.write(MessageType.WRITE_RECORD_SUCCESS.ordinal)
                                    output.write(messageToWrite)
                                    output.flush()
                                    log("Send completed.")
                                }
                            }
                        }
                )

                while (true) {
                    try {
                        val read = input.read()
                        println("READ $read")
                        if(read == -1) break
//                        if(input.available() == 0) continue
                        try {
                            val type = MessageType.values()[read]
                            log("Message received of type $type.")
                            when (type) {
                                MessageType.SET_MODE_READ -> mode = NFCMode.READ
                                MessageType.SET_MODE_WRITE -> {
                                    mode = NFCMode.WRITE
                                    messageToWrite = input.readNDEFMessage()
                                }
                                MessageType.REQUEST_LOGS -> {
                                    output.write(MessageType.LOGS.ordinal)
                                    output.writeUTF(logs.joinToString("\n"))
                                }
                                else -> {
                                    log("Type $type not accepted")
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
                log("Closing bluetooth connection...")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e:InterruptedException){
            e.printStackTrace()
            break
        }finally {
            log("Stopping NFC...")
            lifecycle.dispose()
        }
    }
    outerLifecycle.dispose()
}