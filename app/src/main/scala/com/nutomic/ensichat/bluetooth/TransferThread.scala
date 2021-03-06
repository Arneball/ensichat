package com.nutomic.ensichat.bluetooth

import java.io._

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.messages.Message._
import com.nutomic.ensichat.messages.{Crypto, DeviceInfoMessage, Message}
import org.msgpack.ScalaMessagePack

/**
 * Transfers data between connnected devices.
 *
 * Messages must not be longer than [[TransferThread#MaxMessageLength]] bytes.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param crypto Object used to handle signing and encryption of messages.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket, service: ChatService,
                     crypto: Crypto, onReceive: (Message) => Unit) extends Thread {

  private val Tag: String = "TransferThread"

  val InStream: InputStream =
    try {
      socket.getInputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  val OutStream: OutputStream =
    try {
      socket.getOutputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  override def run(): Unit = {
    Log.i(Tag, "Starting data transfer with " + device.toString)

    while (socket.isConnected) {
      try {
        val up = new ScalaMessagePack().createUnpacker(InStream)
        val isEncrypted = up.readBoolean()
        val plain =
          if (isEncrypted) {
            val encrypted = up.readByteArray()
            val key = up.readByteArray()
            crypto.decrypt(encrypted, key)
          } else {
            up.readByteArray()
          }
        val (message, signature) = Message.read(plain)
        var messageValid = true

        if (message.sender != device.Id) {
          Log.i(Tag, "Dropping message with invalid sender from " + device.Id)
          messageValid = false
        }

        if (message.receiver != service.localDeviceId) {
          Log.i(Tag, "Dropping message with different receiver from " + device.Id)
          messageValid = false
        }

        // Add public key for new, directly connected device.
        // Explicitly check that message was not forwarded or spoofed.
        if (message.isInstanceOf[DeviceInfoMessage] && !crypto.havePublicKey(message.sender) &&
            message.sender == device.Id) {
          val dim = message.asInstanceOf[DeviceInfoMessage]
          // Permanently store public key for new local devices (also check signature).
          if (crypto.isValidSignature(message, signature, dim.publicKey)) {
            crypto.addPublicKey(device.Id, dim.publicKey)
            Log.i(Tag, "Added public key for new device " + device.Name)
          }
        }

        if (!crypto.isValidSignature(message, signature)) {
          Log.i(Tag, "Dropping message with invalid signature from " + device.Id)
          messageValid = false
        }

        if (messageValid) {
          message match {
            case m: DeviceInfoMessage => crypto.addPublicKey(message.sender, m.publicKey)
            case _ => onReceive(message)
          }
        }
      } catch {
        case e: IOException =>
          Log.i(Tag, "Failed to read incoming message", e)
        case e: RuntimeException =>
          Log.i(Tag, "Received invalid message", e)
      }
    }
    service.onConnectionChanged(new Device(device.bluetoothDevice, false), null)
  }

  def send(message: Message): Unit = {
    try {
      val plain = message.write(crypto.calculateSignature(message))
      val packer = new ScalaMessagePack().createPacker(OutStream)

      message.messageType match {
        case Type.Text =>
          val (encrypted, key) = crypto.encrypt(message.receiver, plain)
          // Message is encrypted.
          packer.write(true)
            .write(encrypted)
            .write(key)
        case Type.DeviceInfo | Type.RequestAddContact | Type.ResultAddContact =>
          // Message is not encrypted.
          packer.write(false)
            .write(plain)
      }
    } catch {
      case e: IOException => Log.e(Tag, "Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => Log.e(Tag, "Failed to close socket", e);
    }
  }

}
