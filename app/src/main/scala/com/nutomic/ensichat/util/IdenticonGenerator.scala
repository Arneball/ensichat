package com.nutomic.ensichat.util

import java.security.{MessageDigest, PublicKey}

import android.content.Context
import android.graphics.Bitmap.Config
import android.graphics.{Bitmap, Canvas, Color}

/**
 * Calculates a unique identicon for the given hash.
 *
 * Based on "Contact Identicons" by David Hamp-Gonsalves (converted from Java to Scala).
 * https://github.com/davidhampgonsalves/Contact-Identicons
 */
object IdenticonGenerator {

  val Height: Int = 5

  val Width: Int = 5

  /**
   * Generates an identicon for the key.
   *
   * The identicon size is fixed to [[Height]]x[[Width]].
   *
   * @param size The size of the bitmap returned.
   */
  def generate(key: PublicKey, size: (Int, Int), context: Context): Bitmap = {
    // Hash the key.
    val digest = MessageDigest.getInstance("SHA-1")
    val hash = digest.digest(key.getEncoded)

    // Create base image and colors.
    var identicon = Bitmap.createBitmap(Width, Height, Config.ARGB_8888)
    val background = Color.parseColor("#f0f0f0")
    val r = hash(0) & 255
    val g = hash(1) & 255
    val b = hash(2) & 255
    val foreground = Color.argb(255, r, g, b)

    // Color pixels.
    for (x <- 0 until Width) {
      val i = if (x < 3) x else 4 - x
      var pixelColor: Int = 0
      for (y <- 0 until Height) {
        pixelColor = if ((hash(i) >> y & 1) == 1) foreground else background
        identicon.setPixel(x, y, pixelColor)
      }
    }

    // Add border.
    val bmpWithBorder = Bitmap.createBitmap(12, 12, identicon.getConfig)
    val canvas = new Canvas(bmpWithBorder)
    canvas.drawColor(background)
    identicon = Bitmap.createScaledBitmap(identicon, 10, 10, false)
    canvas.drawBitmap(identicon, 1, 1, null)

    Bitmap.createScaledBitmap(identicon, size._1, size._2, false)
  }

}
