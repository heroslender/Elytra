package io.elytra.api.events

import io.elytra.api.events.ElytraEvent
import io.reactivex.rxjava3.core.Observable

interface IEventBus {

	fun listen() : Observable<Any>

	fun post(event: ElytraEvent)
}