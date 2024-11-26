import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.Collectors;

public class ChatServer {

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // A buffer containing a newline character
    static private final ByteBuffer newline = ByteBuffer.allocate(1).put((byte) 0x0A).flip();

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static final CharsetDecoder decoder = charset.newDecoder();
    static final CharsetEncoder encoder = charset.newEncoder();

    // An array for currently occupied names
    static public Set<String> occupiedNames = new HashSet<String>();  

    static public void main(String args[]) {
        int port = Integer.parseInt(args[0]);
        new Chat("padrao");
        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            serverSocketChannel.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = serverSocketChannel.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

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
                for (SelectionKey key : keys) {
                    // Get a key representing one of bits of I/O activity

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        // It's an incoming connection. Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel socketChannel = s.getChannel();
                        socketChannel.configureBlocking(false);

                        // Register it with the selector, for reading
                        SelectionKey userKey = socketChannel.register(selector,
                                SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        userKey.attach(new User(userKey));

                    } else if (key.isReadable()) {

                        SocketChannel socketChannel = null;

                        try {

                            // It's incoming data on a connection -- process it
                            socketChannel = (SocketChannel) key.channel();
                            List<String> messages = processPacket(socketChannel);

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (messages == null) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = socketChannel.socket();
                                    System.out.println("Closing connection to " + s);
                                    s.close();
                                } catch (IOException ie) {
                                    System.err.println("Error closing socket " + s + ": " + ie);
                                }
                            } else {
                                for (String message : messages) {
                                    processMessage(key, message);
                                }
                            }

                        } catch (IOException ie) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                socketChannel.close();
                            } catch (IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + socketChannel);
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }

    static List<String> processPacket(SocketChannel senderSocket, String leadingMessage)
            throws IOException {
        buffer.clear();
        senderSocket.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit() == 0) {
            return null;
        }
        String input = leadingMessage.concat(decoder.decode(buffer).toString());
        List<String> inputLines = new ArrayList<String>(Arrays.stream(input.split("\n", -1))
                .collect(Collectors.toCollection(ArrayList::new)));
        buffer.rewind();
        String last = inputLines.remove(inputLines.size() - 1); // remove last
        if (last.isBlank()) {
            return inputLines;
        } else {
            inputLines.addAll(processPacket(senderSocket, last));
            return inputLines;
        }
    }

    static List<String> processPacket(SocketChannel senderSocket) throws IOException {
        return processPacket(senderSocket, "");
    }

    static void processMessage(SelectionKey senderKey, String message) throws IOException {
        User user = (User) senderKey.attachment();
        if (isCommand(message)) {
            processCommand(message, user);
        } else if (user.isInside()){
            user.sendMessageFromHim("MESSAGE " + user.getName() + " " + message);
        } else {
            user.sendMessageToHim("ERROR");
        }


    }

    static public void sendMessage(String message, SelectionKey receiverKey) throws IOException {
        ByteBuffer messageBuf = encoder.encode(CharBuffer.wrap(message.toCharArray())); // Turns
                                                                                        // message
                                                                                        // from
                                                                                        // string to
                                                                                        // byte
                                                                                        // buffer
        sendMessage(messageBuf, receiverKey);
    }

    static public void sendMessage(ByteBuffer messageBuf, SelectionKey receiverKey)
            throws IOException {
        SocketChannel sc = (SocketChannel) receiverKey.channel();
        sc.write(messageBuf);
        messageBuf.rewind();
        sc.write(newline);
        newline.rewind();
    }

    static private boolean isCommand(String input) {
        return (input.charAt(0) == '/' && input.charAt(1) != '/');
    }
    static private void processCommand(String message,User user)throws IOException{
        String[] command = message.split(" ");
        switch (command[0]) {
            case "/nick":
                if (command.length < 2) {
                    user.sendMessageToHim("ERROR");
                } else {
                    if (user.setName(command[1])){
                        if (user.isInside()){
                            user.getCurrrentChat().sendMessage("NEWNICK " + user.getName() + " " + command[1]);
                        }
                        user.sendMessageToHim("OK");
                        System.out.println("New name for User: " + command[1]);
                    } else {
                        user.sendMessageToHim("ERROR");
                    }
                }
                break;
            case "/join":
                if (user.isInside()){
                    user.sendMessageFromHim("LEFT " + user.getName());
                }
                Chat newChat = Chat.getChat(command[1]);
                if (newChat != null){
                    user.setCurrrentChat(newChat);
                    user.sendMessageFromHim("JOIN " + user.getName());
                    user.sendMessageToHim("OK");
                } else {
                    user.sendMessageToHim("ERROR");
                }
                break;
            case "/leave":
                //TODO
                break;
            case "/bye":
                user.sendMessageFromHim("LEFT " + user.getName());
                user.setCurrrentChat(null);
                user.sendMessageToHim("BYE");
                break;
            default:
                user.sendMessageToHim("ERROR");
                break;
        }
    }
}


class User {
    private String name;
    private Chat currrentChat;
    private SelectionKey key;

    User(SelectionKey key) {
        this.name = null;
        this.key = key;
        currrentChat = null;
    }
    public boolean isInit(){
        return this.name == null && this.currrentChat == null;
    }

    public boolean isOutside(){
        return this.name != null && currrentChat == null;
    }

    public boolean isInside(){
        return this.currrentChat != null;
    }

    public boolean setName(String newName) {
        if (ChatServer.occupiedNames.add(newName)){
            this.name = newName;
            return true;
        }
        return false;
    }

    public void setKey(SelectionKey key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public SelectionKey getKey() {
        return key;
    }

    public Chat getCurrrentChat() {
        return currrentChat;
    }

    public void setCurrrentChat(Chat newChat) {
        if (isInside()){
            currrentChat.removeUser(this);
        }
        this.currrentChat = newChat;
        if (currrentChat != null)
            currrentChat.addUser(this);
    }

    public void sendMessageFromHim(ByteBuffer messageBuf) throws IOException {
        for (User u : this.currrentChat.getUsers()) {
            if (u != this) {
                if (u.getKey().isWritable())
                    ChatServer.sendMessage(messageBuf, u.getKey());
                else
                    System.err.println("A user in chat can`t receive messages");
            }
        }
    }

    public void sendMessageFromHim(String message) throws IOException {
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        sendMessageFromHim(messageBuf);
    }
    public void sendMessageToHim(String message) throws IOException{
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        sendMessageToHim(messageBuf);
    }
    public void sendMessageToHim(ByteBuffer messageBuf) throws IOException{
        ChatServer.sendMessage(messageBuf, this.getKey());
    }
}


class Chat {
    private static Dictionary<String, Chat> chatDict = new Hashtable<>();
    private String name;
    private List<User> users;

    public static Chat getChat(String name) {
        return chatDict.get(name);
    }

    Chat(String name) {
        this.name = name;
        chatDict.put(name, this);
        users = new ArrayList<User>();
    }

    public List<User> getUsers() {
        return users;
    }


    public void addUser(User user) {
        this.users.add(user);
    }

    public void removeUser(User user) {
        this.users.remove(user);
    }

    void sendMessage(ByteBuffer buf) throws IOException {
        for (User u : users) {
            if (u.getKey().isWritable())
                ChatServer.sendMessage(buf, u.getKey());
            else
                System.err.println("A user in chat can`t receive messages");
        }
    }

    void sendMessage(String message) throws IOException {
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        sendMessage(messageBuf);
    }
}
