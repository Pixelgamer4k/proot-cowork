package com.proot.cowork.data.vnc

import android.graphics.Bitmap
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
/**
 * Minimal RFB 3.8 client (Raw encoding) for localhost x11vnc.
 */
class RfbClient(
    private val host: String = VncConfig.HOST,
    private val port: Int = VncConfig.PORT,
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var redShift = 16
    private var greenShift = 8
    private var blueShift = 0
    private var bytesPerPixel = 4

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect() {
        disconnect()
        val sock = Socket()
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(host, port), VncConfig.CONNECT_TIMEOUT_MS)
        socket = sock
        input = DataInputStream(sock.getInputStream())
        output = DataOutputStream(sock.getOutputStream())
        handshake()
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        val out = output ?: return
        out.writeByte(5)
        out.writeByte(buttonMask)
        out.writeShort(x.coerceIn(0, maxOf(0, width - 1)))
        out.writeShort(y.coerceIn(0, maxOf(0, height - 1)))
        out.flush()
    }

    fun sendKeyEvent(key: Int, down: Boolean) {
        val out = output ?: return
        out.writeByte(4)
        out.writeByte(if (down) 1 else 0)
        out.writeByte(0)
        out.writeByte(0)
        out.writeInt(key)
        out.flush()
    }

    fun requestFramebufferUpdate(incremental: Boolean) {
        val out = output ?: return
        out.writeByte(3)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(width)
        out.writeShort(height)
        out.flush()
    }

    fun readFramebuffer(bitmap: Bitmap?): Bitmap {
        val inp = input ?: throw IllegalStateException("Not connected")
        val target = bitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        while (true) {
            val messageType = inp.readUnsignedByte()
            when (messageType) {
                0 -> {
                    inp.readByte() // padding
                    val count = inp.readUnsignedShort()
                    repeat(count) {
                        decodeRectangle(inp, target)
                    }
                    return target
                }
                1 -> skipColourMap(inp)
                2 -> { /* bell */ }
                3 -> skipServerCutText(inp)
                else -> throw EOFException("Unknown RFB message type $messageType")
            }
        }
    }

    private fun handshake() {
        val inp = input ?: error("no input")
        val out = output ?: error("no output")

        val serverVersion = readLine(inp)
        if (!serverVersion.startsWith("RFB ")) {
            error("Invalid RFB banner: $serverVersion")
        }
        out.writeBytes("RFB 003.008\n")
        out.flush()

        val securityCount = inp.readUnsignedByte()
        if (securityCount == 0) {
            val reasonLen = inp.readInt()
            val reason = ByteArray(reasonLen).also { inp.readFully(it) }
            error(String(reason, Charsets.UTF_8))
        }
        val types = IntArray(securityCount) { inp.readUnsignedByte() }
        val chosen = when {
            types.contains(1) -> 1 // None
            else -> types.first()
        }
        out.writeInt(chosen)
        out.flush()

        if (chosen == 1) {
            val result = inp.readInt()
            if (result != 0) error("VNC security handshake failed ($result)")
        }

        out.writeByte(1) // shared desktop
        out.flush()

        width = inp.readUnsignedShort()
        height = inp.readUnsignedShort()
        readPixelFormat(inp)
        val nameLen = inp.readInt()
        inp.skipBytes(nameLen)

        preferRawEncoding()
    }

    private fun preferRawEncoding() {
        val out = output ?: return
        out.writeByte(2) // SetEncodings
        out.writeByte(0)
        out.writeShort(1)
        out.writeInt(0) // Raw only
        out.flush()
    }

    private fun readPixelFormat(inp: DataInputStream) {
        bytesPerPixel = inp.readUnsignedByte()
        inp.readUnsignedByte() // depth
        inp.readUnsignedByte() // big endian
        inp.readUnsignedByte() // true color
        inp.readUnsignedShort() // red max
        inp.readUnsignedShort() // green max
        inp.readUnsignedShort() // blue max
        redShift = inp.readUnsignedByte()
        greenShift = inp.readUnsignedByte()
        blueShift = inp.readUnsignedByte()
        inp.skipBytes(3) // padding
    }

    private fun decodeRectangle(inp: DataInputStream, target: Bitmap) {
        val x = inp.readUnsignedShort()
        val y = inp.readUnsignedShort()
        val w = inp.readUnsignedShort()
        val h = inp.readUnsignedShort()
        val encoding = inp.readInt()
        when (encoding) {
            0 -> decodeRaw(inp, target, x, y, w, h)
            1 -> decodeCopyRect(inp, target, x, y, w, h)
            else -> throw EOFException("Unsupported VNC encoding $encoding")
        }
    }

    private fun decodeRaw(
        inp: DataInputStream,
        target: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val rowBytes = w * bytesPerPixel
        val row = ByteArray(rowBytes)
        val pixels = IntArray(w)
        for (rowIndex in 0 until h) {
            inp.readFully(row)
            for (col in 0 until w) {
                val offset = col * bytesPerPixel
                pixels[col] = when (bytesPerPixel) {
                    4 -> argbFrom32(readIntBE(row, offset))
                    3 -> argbFrom24(row[offset].toInt() and 0xFF, row[offset + 1].toInt() and 0xFF, row[offset + 2].toInt() and 0xFF)
                    else -> 0xFF000000.toInt()
                }
            }
            target.setPixels(pixels, 0, w, x, y + rowIndex, w, 1)
        }
    }

    private fun decodeCopyRect(
        inp: DataInputStream,
        target: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val srcX = inp.readUnsignedShort()
        val srcY = inp.readUnsignedShort()
        val copy = Bitmap.createBitmap(target, srcX, srcY, w, h)
        val canvas = android.graphics.Canvas(target)
        canvas.drawBitmap(copy, x.toFloat(), y.toFloat(), null)
        copy.recycle()
    }

    private fun argbFrom32(pixel: Int): Int {
        val r = (pixel shr redShift) and 0xFF
        val g = (pixel shr greenShift) and 0xFF
        val b = (pixel shr blueShift) and 0xFF
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun argbFrom24(r: Int, g: Int, b: Int): Int =
        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b

    private fun readIntBE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun skipColourMap(inp: DataInputStream) {
        inp.readUnsignedByte()
        val count = inp.readUnsignedShort()
        inp.skipBytes(count * 6)
    }

    private fun skipServerCutText(inp: DataInputStream) {
        inp.skipBytes(3)
        val len = inp.readInt()
        inp.skipBytes(len)
    }

    private fun readLine(inp: DataInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val ch = inp.readUnsignedByte()
            if (ch == '\n'.code) break
            if (ch != '\r'.code) sb.append(ch.toChar())
        }
        return sb.toString()
    }
}
