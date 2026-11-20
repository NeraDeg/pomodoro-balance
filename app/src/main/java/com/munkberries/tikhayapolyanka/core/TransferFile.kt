package com.munkberries.tikhayapolyanka.core

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Local, unencrypted transfer file. Digest detects damage, not an authenticated signature. */
object TransferFile {
    const val MAX_BODY=8*1024*1024
    const val MAX_FILE=MAX_BODY+44
    private val magic="PMBAL001".toByteArray(Charsets.US_ASCII)
    fun encode(text: String): ByteArray {
        val body=text.toByteArray(Charsets.UTF_8)
        require(body.size in 1..MAX_BODY)
        return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.write(magic);out.writeInt(body.size);out.write(body);out.write(MessageDigest.getInstance("SHA-256").digest(body))
        } }.toByteArray()
    }
    fun decode(file: ByteArray): String {
        require(file.size in 45..MAX_FILE)
        val input=DataInputStream(ByteArrayInputStream(file));val header=ByteArray(8);input.readFully(header)
        require(header.contentEquals(magic))
        val length=input.readInt();require(length in 1..MAX_BODY && file.size==length+44)
        val body=ByteArray(length);input.readFully(body);val digest=ByteArray(32);input.readFully(digest)
        require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(body)))
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
        var depth=0;var quoted=false;var escaped=false;var nodes=0
        for(c in text) {
            if(quoted) { if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false }
            else when(c) {'"'->quoted=true;'[','{'->{depth++;nodes++;require(depth<=32 && nodes<=200_000)};','->{nodes++;require(nodes<=200_000)};']','}'->{depth--;require(depth>=0)}}
        }
        require(depth==0 && !quoted)
        return text
    }
    fun read(input: InputStream): ByteArray {
        val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=MAX_FILE);output.write(buffer,0,count)}
        return output.toByteArray()
    }
}
