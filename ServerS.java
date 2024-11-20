import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ServerS
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
  static private final CharsetEncoder encoder = charset.newEncoder();
  static private final ByteBuffer newline = ByteBuffer.allocate(1).put((byte) 0x0A).flip();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc ,key, selector);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  static private boolean isCommand(String input){
    return (input.charAt(0) == '/' && input.charAt(1) != '/');
  }

  static private void sendMessage(String message, SelectionKey receiverKey) throws IOException{
    ByteBuffer messageBuf = encoder.encode(CharBuffer.wrap(message.toCharArray())); // Turns message from string to byte buffer
    sendMessage(messageBuf, receiverKey);
  }

  static private void sendMessage(ByteBuffer messageBuf, SelectionKey receiverKey) throws IOException{
    SocketChannel sc = (SocketChannel) receiverKey.channel();
    sc.write(messageBuf);
    messageBuf.rewind();
    sc.write(newline);
    newline.rewind();
  }




  // Reads the message and send it to all sockets
  static private boolean processInput( SocketChannel sc,SelectionKey senderKey, Selector selector) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }
    String input = decoder.decode(buffer).toString();
    buffer.rewind();
    if (isCommand(input)){
      String[] command = input.split("[ \n]");
      switch (command[0]){
        case "/nick":
          if(command.length < 2){
            sendMessage("ERROR", senderKey);
          }
          else {
          //todo check if name is available
          senderKey.attach(command[1]);
          //todo send nickname to users of a room
          sendMessage("OK", senderKey);
          }
          break;
        case "/join":
          //todo
          break;
        case "/leave":
          //todo
          break;
        case "/bye":
          //todo
          sendMessage("BYE", senderKey);
          break;
        default:
          sendMessage("ERROR", senderKey);
          break; 
      }
    }
    else {
    // Buffer is message so we rebroadcast it
    String nickname = senderKey.attachment().toString();
    String content = decoder.decode(buffer).toString();
    String message = "MESSAGE " + nickname + " " + content;

    ByteBuffer messageBuf = encoder.encode(CharBuffer.wrap(message.toCharArray())); // Turns message from string to byte buffer
    Set<SelectionKey> keys = selector.selectedKeys();
    Iterator<SelectionKey> it = keys.iterator();
    while (it.hasNext()) {
      // Get a key representing one of bits of I/O activity
      SelectionKey key = it.next();
      // Check if Selected channels is accepting writes
      if (key.isWritable()) {
        System.out.println("Sending message to "+ key.attachment());
        sendMessage(messageBuf, key);
      } else System.out.println("Socket not Writable");
    }
    System.out.println("fim");
  }
    return true;
  }
}
