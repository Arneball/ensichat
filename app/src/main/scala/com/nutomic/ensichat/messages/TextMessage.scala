package com.nutomic.ensichat.messages

import java.util.Date

import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.Message._
import org.msgpack.packer.Packer
import org.msgpack.unpacker.Unpacker

object TextMessage {

  def read(sender: Device.ID, receiver: Device.ID, date: Date, up: Unpacker): TextMessage =
    new TextMessage(sender, receiver, date, up.readString())

}

/**
 * Message that contains text.
 */
class TextMessage(override val sender: Device.ID, override val receiver: Device.ID,
                  override val date: Date, val text: String) extends Message(Type.Text) {

  override def doWrite(packer: Packer) = packer.write(text)

  override def equals(a: Any) = super.equals(a) && a.asInstanceOf[TextMessage].text == text

  override def hashCode = super.hashCode + text.hashCode

  override def toString = "TextMessage(" + sender.toString + ", " + receiver.toString +
    ", " + date.toString + ", " + text + ")"

}
