package com.munkberries.tikhayapolyanka.core

import java.io.*
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class TransferFileTest {
    @Test fun long_russian_text_and_emoji_roundtrip_without_loss() {
        val text="{\"note\":\""+"Русский текст ёжик 🦊 ".repeat(8000)+"\"}"
        assertEquals(text,TransferFile.decode(TransferFile.encode(text)))
    }
    @Test fun modified_payload_is_rejected() {
        val bytes=TransferFile.encode("{\"note\":\"важное\"}");bytes[15]=(bytes[15].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java){TransferFile.decode(bytes)}
    }
    @Test fun truncated_or_appended_file_is_rejected() {
        val good=TransferFile.encode("{\"note\":123}")
        for(length in listOf(0,8,12,good.size-1))assertThrows(IllegalArgumentException::class.java){TransferFile.decode(good.copyOf(length))}
        assertThrows(IllegalArgumentException::class.java){TransferFile.decode(good+byteArrayOf(0))}
    }
    @Test fun foreign_format_and_excessive_json_nesting_are_rejected() {
        val other=TransferFile.encode("{}");other[0]='X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java){TransferFile.decode(other)}
        val deep="{\"x\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}"
        assertThrows(IllegalArgumentException::class.java){TransferFile.decode(TransferFile.encode(deep))}
    }
    @Test fun invalid_utf8_is_not_silently_replaced() {
        val body=byteArrayOf(123,34,120,34,58,34,0xC3.toByte(),34,125)
        val out=ByteArrayOutputStream();DataOutputStream(out).use{
            it.write("PMBAL001".toByteArray());it.writeInt(body.size);it.write(body);it.write(MessageDigest.getInstance("SHA-256").digest(body))
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java){TransferFile.decode(out.toByteArray())}
    }
    @Test fun input_larger_than_limit_is_rejected_before_decoding() {
        assertThrows(IllegalArgumentException::class.java){TransferFile.read(ByteArrayInputStream(ByteArray(TransferFile.MAX_FILE+1)))}
    }
}
