package actors;

import java.io.IOException;

import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;

/**
 * Can't (yet) figure out why Scala has a problem with
 * overloaded SpiDevice.write method, so falling back
 * to Java here
 */
public class SpiWrapper {
   private static final SpiDevice spi;

   static {
      try {
         spi = SpiFactory.getInstance(SpiChannel.CS0,
               SpiDevice.DEFAULT_SPI_SPEED, SpiDevice.DEFAULT_SPI_MODE);
      }
      catch (IOException e) {
         throw new ExceptionInInitializerError(e);
      }
   }

   public int readChannel(int channel) throws IOException {
      byte[] data = new byte[]{0x01, (byte) (0x80|((channel&7) << 4)), 0x00};
//      System.out.println(String.format("data: [0x%x, 0x%x, 0x%x]", data[0], data[1], data[2]));
      byte[] results = spi.write(data);
//      System.out.println("SPI call responded with " + results.length + " bytes");
//      System.out.println(String.format("results: [0x%x, 0x%x, 0x%x]", results[0], results[1], results[2]));
      int level = (results[1]<<8) & 0b1100000000;
//      System.out.println(String.format("level after 1st byte: 0x%x", level));
      level |=  (results[2] & 0xff);
//      System.out.println(String.format("level after 2nd byte: 0x%x (%d)", level, level));
      return level;
   }
}
