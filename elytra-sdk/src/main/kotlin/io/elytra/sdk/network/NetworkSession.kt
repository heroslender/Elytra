package io.elytra.sdk.network

import com.flowpowered.network.Message
import com.flowpowered.network.MessageHandler
import com.flowpowered.network.protocol.AbstractProtocol
import com.flowpowered.network.session.BasicSession
import io.elytra.sdk.network.pipeline.CodecsHandler
import io.elytra.sdk.network.protocol.PacketProvider
import io.elytra.sdk.network.protocol.message.DisconnectMessage
import io.elytra.sdk.network.protocol.packets.BasicPacket
import io.elytra.sdk.network.protocol.packets.HandshakePacket
import io.elytra.api.utils.Asyncable
import io.elytra.api.utils.Tickable
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.handler.codec.CodecException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque


class NetworkSession(
	channel: Channel,
	var protocol: BasicPacket? = null,
	@Volatile private var disconnected: Boolean = false,
	private var packetProvider: PacketProvider = PacketProvider(),
	private val messageQueue: BlockingQueue<Message> = LinkedBlockingDeque()
) : BasicSession(channel, HandshakePacket()), Tickable {

	override fun sendWithFuture(message: Message?): ChannelFuture? {
		if (!isActive) {
			return null
		}
		return super.sendWithFuture(message)
	}

	override fun disconnect() {
		disconnect("No reason specified.")
	}

	override fun tick() {
		var message: Message?
		while (messageQueue.poll().also { message = it } != null) {
			if (disconnected) break

			println(message)
			super.messageReceived(message)
		}

		/*if (disconnected && getProtocol() == packetProvider.playPacket) {
			player?.let { PlayerDisconnectEvent(it, "No reason specified") }?.let { EventBus.post(it) }
		}*/
	}

	public override fun setProtocol(protocol: AbstractProtocol?) {
		updatePipeline("codecs", CodecsHandler(protocol as BasicPacket))
		super.setProtocol(protocol)
	}

	override fun onDisconnect() {
		disconnected = true
	}

	override fun onInboundThrowable(throwable: Throwable?) {
		if (throwable is CodecException) {
			println("Error in inbound network: $throwable")
			return
		}

		disconnect("decode error: ${throwable?.message}")
	}

	override fun onHandlerThrowable(message: Message?, handle: MessageHandler<*, *>?, throwable: Throwable?) {
		println("Error while handling $message (${handle?.javaClass?.simpleName}) - $throwable")
	}

	override fun messageReceived(message: Message) {
		if (message is Asyncable) {
			super.messageReceived(message)
			return
		}

		messageQueue.add(message)
	}

	fun disconnect(reason: String) {
		/*if (player != null) {
			ElytraServer.logger.info("${player?.username} kicked due $reason")
		}*/

		if (disconnected && getProtocol() == packetProvider.playPacket) {
			//println("$sessionId : ${player?.username} disconnect")
			sendWithFuture(DisconnectMessage(reason))?.addListener(ChannelFutureListener.CLOSE)
		} else {
			channel.close()
		}
	}

	private fun updatePipeline(key: String, handler: ChannelHandler) {
		channel.pipeline().replace(key, key, handler);
	}
}
