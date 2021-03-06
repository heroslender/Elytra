package io.elytra.sdk.network.protocol.packets

import com.flowpowered.network.Codec
import com.flowpowered.network.Message
import com.flowpowered.network.MessageHandler
import com.flowpowered.network.exception.IllegalOpcodeException
import com.flowpowered.network.exception.UnknownPacketException
import com.flowpowered.network.protocol.AbstractProtocol
import com.flowpowered.network.service.CodecLookupService
import com.flowpowered.network.service.HandlerLookupService
import com.flowpowered.network.util.ByteBufUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.IOException
import java.lang.reflect.InvocationTargetException


abstract class BasicPacket(name: String, opcode: Int) : AbstractProtocol(name) {

	private var inboundCodecs: CodecLookupService? = null
	private var outboundCodecs: CodecLookupService? = null
	private var handlers: HandlerLookupService? = null

	init {
		inboundCodecs = CodecLookupService(opcode+1)
		outboundCodecs = CodecLookupService(opcode+1)
		handlers = HandlerLookupService()
	}

	protected open fun <M : Message, C : Codec<M>, H : MessageHandler<*, M>> inbound(
		opcode: Int,
		message: Class<M>,
		codec: Class<C>,
		handler: Class<H>
	) {
		try {
			inboundCodecs?.bind(message, codec, opcode)
			handlers?.bind(message, handler)
		} catch (e: InstantiationException) {
			logger.error("Error registering inbound $opcode in $name", e)
		} catch (e: IllegalAccessException) {
			logger.error("Error registering inbound $opcode in $name", e)
		} catch (e: InvocationTargetException) {
			logger.error("Error registering inbound $opcode in $name", e)
		}
	}


	protected open fun <M : Message, C : Codec<M>, H : MessageHandler<*, in M>> inbound(
		opcode: Int, message: Class<M>, codec: Class<C>, handler: H) {
		try {
			inboundCodecs!!.bind(message, codec, opcode)
			handlers?.bind(message, handler::class.java)
		} catch (e: InstantiationException) {
			logger.error("Error registering inbound $opcode in $name", e)
		} catch (e: IllegalAccessException) {
			logger.error("Error registering inbound $opcode in $name", e)
		} catch (e: InvocationTargetException) {
			logger.error("Error registering inbound $opcode in $name", e)
		}
	}

	protected open fun <M : Message?, C : Codec<in M>?> outbound(
		opcode: Int,
		message: Class<M>?,
		codec: Class<C>?
	) {
		try {
			outboundCodecs!!.bind(message, codec, opcode)
		} catch (e: InstantiationException) {
			logger.error("Error registering outbound $opcode in $name", e)
		} catch (e: IllegalAccessException) {
			logger.error("Error registering outbound $opcode in $name", e)
		} catch (e: InvocationTargetException) {
			logger.error("Error registering outbound $opcode in $name", e)
		}
	}

	override fun <M : Message?> getMessageHandle(clazz: Class<M>): MessageHandler<*, M>? {
		return handlers?.find(clazz);
	}

	override fun <M : Message> getCodecRegistration(clazz: Class<M>): Codec.CodecRegistration? {
		val reg = outboundCodecs?.find(clazz)

		if (reg == null) {
			println("No codec to write ${clazz.simpleName} in $name")
		}

		return reg
	}

	override fun writeHeader(out: ByteBuf, codec: Codec.CodecRegistration, data: ByteBuf): ByteBuf {
		val opcodeBuffer = Unpooled.buffer(5)
		ByteBufUtils.writeVarInt(opcodeBuffer, codec.opcode)
		ByteBufUtils.writeVarInt(out, opcodeBuffer.readableBytes() + data.readableBytes())
		opcodeBuffer.release()
		ByteBufUtils.writeVarInt(out, codec.opcode)
		return out
	}

	override fun readHeader(buf: ByteBuf): Codec<*> {
		var length = -1
		var opcode = -1

		return try {
			length = ByteBufUtils.readVarInt(buf)
			buf.markReaderIndex()
			opcode = ByteBufUtils.readVarInt(buf)
			println(opcode)
			inboundCodecs!!.find(opcode)
		} catch (e: IOException) {
			throw UnknownPacketException("Failed to read packet data (corrupt?)", opcode,
				length)
		} catch (e: IllegalOpcodeException) {
			buf.resetReaderIndex()
			throw UnknownPacketException(
				"Opcode received is not a registered codec on the server!", opcode, length)
		}
	}

	@Throws(IOException::class, IllegalOpcodeException::class)
	open fun newReadHeader(input: ByteBuf): Codec<*>? {
		val opcode = ByteBufUtils.readVarInt(input)
		println("Packet id: $opcode | ${input.capacity()}")
		return inboundCodecs?.find(opcode)
	}
}

