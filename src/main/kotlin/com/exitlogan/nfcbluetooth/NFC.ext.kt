package com.exitlogan.nfcbluetooth

import com.lightningkite.kotlin.lifecycle.LifecycleConnectable
import com.lightningkite.kotlin.lifecycle.LifecycleListener
import org.nfctools.NfcAdapter
import org.nfctools.api.NfcTagListener
import org.nfctools.api.Tag
import org.nfctools.api.TagScannerListener
import org.nfctools.mf.classic.MfClassicNfcTagListener
import org.nfctools.ndef.NdefOperations
import org.nfctools.ndef.NdefOperationsListener
import org.nfctools.ndef.mime.MimeRecord
import org.nfctools.scio.Terminal
import org.nfctools.scio.TerminalHandler
import org.nfctools.scio.TerminalMode
import org.nfctools.spi.acs.AcsTerminal
import org.nfctools.spi.scm.SclTerminal

/**
 * Handles operations with the NFC reader for NDEF operations.
 */
fun ndef(
        lifecycle: LifecycleConnectable,
        onFailure:(Throwable?)->Unit = {},
        onAnyTag:(Tag?)->Unit = {},
        onCard: (NdefOperations) -> Unit
) {
    try {
        var active = false
        var adapter: NfcAdapter? = null

        lifecycle.connect(object : LifecycleListener {
            override fun onStart() {
                println("Starting NFC")


                adapter = NfcAdapter(getAvailableTerminal(), TerminalMode.INITIATOR, object : TagScannerListener {
                    override fun onScanningEnded() {
                        println("onScanningEnded")
                    }

                    override fun onScanningFailed(throwable: Throwable?) {
                        println("onScanningFailed")
                        onFailure.invoke(throwable)
                        if (active) {
                            adapter?.startListening()
                        }
                    }

                    override fun onTagHandingFailed(throwable: Throwable?) {
                        println("onTagHandingFailed")
                        onFailure.invoke(throwable)
                    }
                }).apply {
                    registerTagListener(object : NfcTagListener {
                        override fun canHandle(tag: Tag?): Boolean {
                            println("canHandle: ${tag?.tagType?.name}")
                            onAnyTag.invoke(tag)
                            return false
                        }

                        override fun handleTag(tag: Tag?) {
                            println("handleTag: ${tag?.tagType?.name}")
                        }
                    })
                    registerTagListener(MfClassicNfcTagListener(NdefOperationsListener { ndefOperations ->
                        if (active) {
                            println("onNdefOperations")
                            onCard.invoke(ndefOperations)
                        }
                    }))
                    startListening()
                    active = true
                }
            }

            override fun onStop() {
                active = false
                println("Stopping NFC")
                adapter?.stopListening()
                adapter = null
            }
        })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getAvailableTerminal(preferredTerminalName: String? = null): Terminal? {
    val terminalHandler = TerminalHandler()
    terminalHandler.addTerminal(AcsTerminal())
    terminalHandler.addTerminal(SclTerminal())
    return terminalHandler.getAvailableTerminal(preferredTerminalName)
}

fun NdefOperations.getRecords(): List<NDEFMessage> = readNdefMessage().asSequence()
        .mapNotNull { it as? MimeRecord }
        .map {
            NDEFMessage(
                    mimeType = it.contentType,
                    data = it.contentAsBytes.toString(Charsets.UTF_8)
            )
        }
        .toList()

fun NdefOperations.setRecords(message: NDEFMessage){
    format(org.nfctools.ndef.mime.TextMimeRecord(message.mimeType, message.data))
}

