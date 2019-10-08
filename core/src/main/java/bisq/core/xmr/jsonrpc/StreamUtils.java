package bisq.core.xmr.jsonrpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
/**
 * Collection of utilities for working with streams.
 * 
 * @author woodser
 */
public class StreamUtils {

  /**
   * Converts an input stream to a byte array.
   * 
   * @param is is the input stream
   * @return byte[] are the contents of the input stream as a byte array
   * @throws IOException 
   */
  public static byte[] streamToBytes(InputStream is) throws IOException {
    byte[] bytes = is.readAllBytes();
=======
import org.apache.commons.io.IOUtils;

=======
>>>>>>> cdb3333 Removed Commons IO dependency.
/**
 * Collection of utilities for working with streams.
 * 
 * @author woodser
 */
public class StreamUtils {

  /**
   * Converts an input stream to a byte array.
   * 
   * @param is is the input stream
   * @return byte[] are the contents of the input stream as a byte array
   * @throws IOException 
   */
  public static byte[] streamToBytes(InputStream is) throws IOException {
<<<<<<< Upstream, based on branch 'master' of https://github.com/bisq-network/incubator-bisq-xmr-integration.git
    byte[] bytes = IOUtils.toByteArray(is);
>>>>>>> cf956db Fully functional and basic Monero (XMR) wallet integrated to Monero RPC Wallet running on localhost with the following features:
=======
    byte[] bytes = is.readAllBytes();
>>>>>>> cdb3333 Removed Commons IO dependency.
    is.close();
    return bytes;
  }
  
  /**
   * Converts a byte array to an input stream.
   * 
   * @param bytes is the byte[] to convert to an input stream
   * @return InputStream is the input stream initialized from the byte array
   */
  public static InputStream bytesToStream(byte[] bytes) {
    return new ByteArrayInputStream(bytes);
  }
  
  /**
   * Converts an input stream to a string.
   * 
   * @param is is the input stream to convert to a string
   * @return String is the input stream converted to a string
   * @throws IOException 
   */
  public static String streamToString(InputStream is) throws IOException {
    return new String(streamToBytes(is));
  }
}
