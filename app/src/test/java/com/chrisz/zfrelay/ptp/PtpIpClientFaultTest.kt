package com.chrisz.zfrelay.ptp

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PtpIpClientFaultTest {
    @Test
    fun `mismatched response transaction closes connection before any retry`() {
        withFakeCamera(
            socketTimeoutMs = 2_000,
            serverScenario = { command ->
                val request = readOperation(command)
                respond(command, request.second + 1, ResponseCode.OK)
            },
            clientScenario = { client ->
                val error = assertThrows(PtpException::class.java) { client.listPhotos() }
                assertTrue(error.message.orEmpty().contains("事务编号不匹配"))
                val retry = assertThrows(IOException::class.java) { client.listPhotos() }
                assertTrue(retry.message.orEmpty().contains("重新连接"))
            },
        )
    }

    @Test
    fun `timeout closes connection so late response cannot contaminate next request`() {
        withFakeCamera(
            socketTimeoutMs = 100,
            serverScenario = { command ->
                val request = readOperation(command)
                Thread.sleep(300)
                val handles = LittleEndianWriter().apply { u32(0) }.toByteArray()
                runCatching { respondWithData(command, request.second, handles) }
            },
            clientScenario = { client ->
                assertThrows(SocketTimeoutException::class.java) { client.listPhotos() }
                val retry = assertThrows(IOException::class.java) { client.listPhotos() }
                assertTrue(retry.message.orEmpty().contains("重新连接"))
            },
        )
    }

    @Test
    fun `command initialization failure reports its connection stage`() {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val worker = thread(isDaemon = true) { server.accept().close() }
            val client = PtpIpClient(
                host = "127.0.0.1",
                port = server.localPort,
                settleDelayMs = 0,
                socketTimeoutMs = 2_000,
            )
            val error = assertThrows(PtpException::class.java) { client.connect() }
            assertTrue(error.message.orEmpty().contains("初始化命令通道失败"))
            client.close()
            worker.join(5_000)
            assertFalse(worker.isAlive)
        }
    }

    private fun withFakeCamera(
        socketTimeoutMs: Int,
        serverScenario: (Socket) -> Unit,
        clientScenario: (PtpIpClient) -> Unit,
    ) {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val serverError = AtomicReference<Throwable?>()
            val worker = thread(isDaemon = true) {
                runCatching {
                    completeHandshake(server).use(serverScenario)
                }.onFailure(serverError::set)
            }
            val client = PtpIpClient(
                host = "127.0.0.1",
                port = server.localPort,
                settleDelayMs = 0,
                socketTimeoutMs = socketTimeoutMs,
            )
            try {
                client.connect()
                clientScenario(client)
            } finally {
                client.close()
            }
            worker.join(6_000)
            assertFalse("假相机线程未结束", worker.isAlive)
            assertNull(serverError.get())
        }
    }

    private fun completeHandshake(server: ServerSocket): Socket {
        val command = server.accept()
        command.soTimeout = 5_000
        val init = readPacket(command.getInputStream())
        val ack = LittleEndianWriter()
        ack.u32(77)
        ack.bytes(ByteArray(16))
        "Fake Z f".forEach { ack.u16(it.code) }
        ack.u16(0)
        ack.u32(0x00010000)
        writePacket(command.getOutputStream(), PacketType.INIT_COMMAND_ACK, ack.toByteArray())

        val event = server.accept()
        readPacket(event.getInputStream())
        writePacket(event.getOutputStream(), PacketType.INIT_EVENT_ACK)

        val session = readOperation(command)
        respond(command, session.second, ResponseCode.OK)
        val device = readOperation(command)
        respondWithData(command, device.second, deviceInfoDataset())
        return command
    }

    private fun readOperation(socket: Socket): Triple<Int, Long, ByteArray> {
        val packet = readPacket(socket.getInputStream())
        val reader = LittleEndianReader(packet.payload)
        reader.u32()
        val opcode = reader.u16()
        val transaction = reader.u32()
        return Triple(opcode, transaction, packet.payload)
    }

    private fun respond(socket: Socket, transaction: Long, code: Int) {
        socket.getOutputStream().write(
            encodeOperationResponse(OperationResponse(code, transaction, emptyList())),
        )
        socket.getOutputStream().flush()
    }

    private fun respondWithData(socket: Socket, transaction: Long, data: ByteArray) {
        val start = LittleEndianWriter()
        start.u32(transaction)
        start.u64(data.size.toLong())
        writePacket(socket.getOutputStream(), PacketType.START_DATA, start.toByteArray())
        socket.getOutputStream().write(encodeDataPacket(PacketType.END_DATA, transaction, data))
        socket.getOutputStream().flush()
        respond(socket, transaction, ResponseCode.OK)
    }
}
