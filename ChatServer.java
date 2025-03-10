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
    static private final ByteBuffer newline = ByteBuffer.allocate(1);

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static final CharsetDecoder decoder = charset.newDecoder();
    static final CharsetEncoder encoder = charset.newEncoder();

    // An array for currently occupied names
    static public Set<String> occupiedNames = new HashSet<String>();
    static public Map<String, SelectionKey> userMap = new HashMap<>();

    static public void main(String args[]) {
        newline.put((byte) 0x0A).flip();
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (ArrayIndexOutOfBoundsException a) {
            port = 1024;
            System.out.println(
                    "No port was provided as an argument so the default port 1024 will be used");
        } catch (NumberFormatException b) {
            port = 1024;
            System.out.println(
                    "First argument can't be interpreted as port so default port 1024 will be used");
        }

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
                            List<String> messages = processPacket((User) key.attachment());

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (messages == null) {
                                User user = (User) key.attachment();
                                Socket s = null;
                                deleteUser(user);
                            } else {
                                for (String message : messages) {
                                    processMessage(key, message);
                                }
                            }

                        } catch (IOException ie) {

                            // On exception, remove this channel from the selector
                            key.cancel();
                            socketChannel = (SocketChannel) key.channel();
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

    static boolean isNameAvailable(String name) {
        return !(occupiedNames.contains(name));
    }

    static List<String> processPacket(User sender) throws IOException {
        SocketChannel senderSocket = (SocketChannel) sender.getKey().channel();
        buffer.clear();
        senderSocket.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit() == 0) {
            return null;
        }
        String messageStart = sender.getUnfinishedMessage();
        String input = messageStart.concat(decoder.decode(buffer).toString());
        List<String> inputLines = new ArrayList<String>(Arrays.stream(input.split("\n", -1))
                .collect(Collectors.toCollection(ArrayList::new)));
        buffer.rewind();
        String last = inputLines.remove(inputLines.size() - 1); // remove last
        sender.setUnfinishedMessage(last); // if there is no unfinished message the string will be
                                           // ""
        return inputLines; // Complete messages
    }

    static void processMessage(SelectionKey senderKey, String message) throws IOException {
        User user = (User) senderKey.attachment();
        if (message.length() > 2 && message.charAt(0) == '/') {
            if (message.charAt(1) == '/'){
                message = message.substring(1, message.length()); //Removes escape character
            } else {
                processCommand(message, user);
                return;
            }
        }
        if (user.isInside()) {
            user.SendMessageFromUser("MESSAGE " + user.getName() + " " + message);
        } else {
            user.sendMessageToUser("ERROR");
        }


    }

    static public ByteBuffer encodeMessage(String message) throws IOException {
        return encoder.encode(CharBuffer.wrap(message.toCharArray()));
    }

    static public void sendMessage(ByteBuffer messageBuf, SelectionKey receiverKey)
            throws IOException {
        SocketChannel sc = (SocketChannel) receiverKey.channel();
        sc.write(messageBuf);
        messageBuf.rewind();
        sc.write(newline);
        newline.rewind();
    }

    static private void processCommand(String message, User user) throws IOException {
        String[] command = message.split(" ");
        switch (command[0]) {
            // Changing name, format of command /nick {newname}
            case "/nick":
                if (command.length != 2) {
                    user.sendMessageToUser("ERROR");
                    break;
                }
                if (isNameAvailable(command[1])) {
                    user.sendMessageToUser("OK");
                    if (user.isInside())
                        user.announceToChat("NEWNICK " + user.getName() + " " + command[1]);
                    occupiedNames.remove(user.getName());
                    userMap.remove(user.getName());
                    user.setName(command[1]);
                    userMap.put(user.getName(), user.getKey());
                } else {
                    user.sendMessageToUser("ERROR");
                }
                break;

            // Joining chat, format of command /join {chatname}    
            case "/join":
                if (command.length != 2) {
                    user.sendMessageToUser("ERROR");
                    break;
                }
                if (user.isInit()) {
                    user.sendMessageToUser("ERROR");
                } else {
                    user.sendMessageToUser("OK");
                    if (user.isInside()) {
                        user.announceToChat("LEFT " + user.getName());
                    }
                    Chat newChat = Chat.getChat(command[1]);
                    if (newChat == null) {
                        newChat = new Chat(command[1]);
                    }
                    user.setCurrrentChat(newChat);
                    user.announceToChat("JOINED " + user.getName());
                }
                break;

            // Leaving chat, format of command: /leave    
            case "/leave":
                if (user.isInside()) {
                    user.announceToChat("LEFT " + user.getName());
                    user.setCurrrentChat(null);
                    user.sendMessageToUser("OK");
                } else
                    user.sendMessageToUser("ERROR");
                break;
            case "/bye":
                user.sendMessageToUser("BYE");
                deleteUser(user);
                break;

            //Sending private message, format of command /priv {recient} {message}    
            case "/priv":
                if (user.isInit() ||command.length < 3 || !userMap.containsKey(command[1])){ //If sender has no name (isInit), command doesn't contain messages or there is no user with receiver name ERROR
                    user.sendMessageToUser("ERROR");
                } else {
                    SelectionKey receiverKey = userMap.get(command[1]);
                    String privateMessage = String.join(" ", Arrays.copyOfRange(command, 2, command.length));
                    user.sendMessageToUser("OK");
                    sendMessage(encodeMessage("PRIVATE " + user.getName() + " " + privateMessage), receiverKey);
                }
                break;

            default:
                user.sendMessageToUser("ERROR");
                break;
        }
    }
    static private void deleteUser(User user) throws IOException{
        SocketChannel sc = (SocketChannel) user.getKey().channel();
        Socket s = sc.socket();
        try {
            if (user.isInside()) {
                user.announceToChat("LEFT " + user.getName());
                user.setCurrrentChat(null);
            }
            occupiedNames.remove(user.getName());
            System.out.println("Closing connection to " + s);
            sc.close();
            System.out.println("Closed " + sc);
            user.getKey().cancel();
        } catch (IOException ie) {
            System.err.println("Error closing socket " + s + ": " + ie);
        }
    }
}


class User {
    private String name;
    private Chat currrentChat;
    private SelectionKey key;
    private String unfinishedMessage = "";

    public String getUnfinishedMessage() {
        return unfinishedMessage;
    }

    public void setUnfinishedMessage(String unfinishedMessage) {
        this.unfinishedMessage = unfinishedMessage;
    }

    User(SelectionKey key) {
        this.key = key;
    }

    public boolean isInit() {
        return this.name == null && this.currrentChat == null;
    }

    public boolean isOutside() {
        return this.name != null && currrentChat == null;
    }

    public boolean isInside() {
        return this.currrentChat != null;
    }

    public boolean setName(String newName) {
        if (ChatServer.occupiedNames.add(newName)) {
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
        if (isInside()) {
            currrentChat.removeUser(this);
        }
        this.currrentChat = newChat;
        if (currrentChat != null)
            currrentChat.addUser(this);
    }

    private void SendMessageFromUser(ByteBuffer messageBuf) throws IOException {
        for (User u : this.currrentChat.getUsers()) {
            if (u.getKey().isWritable())
                ChatServer.sendMessage(messageBuf, u.getKey());
        }
    }
    /**
     * Sends a message to all members of the user's current chat, including to the user himself,
     * supposed to be used when a message is being sent by the user
     * @param message
     * @throws IOException
     * 
     * 
     */
    public void SendMessageFromUser(String message) throws IOException {
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        SendMessageFromUser(messageBuf);
    }
    /**
     * Sends a message only to the user himself, used for OK ERROR and other confirmations
     * @param message
     * @throws IOException
     */
    public void sendMessageToUser(String message) throws IOException {
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        sendMessageToUser(messageBuf);
    }

    private void sendMessageToUser(ByteBuffer messageBuf) throws IOException {
        ChatServer.sendMessage(messageBuf, this.getKey());
    }
    /**
     * Send message to all members off a User's chat except the user himself, used for NEWNICK, LEFT and JOIN 
     * @param message
     * @throws IOException
     */
    public void announceToChat(String message) throws IOException {
        ByteBuffer messageBuf = ChatServer.encoder.encode(CharBuffer.wrap(message.toCharArray()));
        announceToChat(messageBuf);
    }
    private void announceToChat(ByteBuffer messageBuf)throws IOException{
        for (User u : this.currrentChat.getUsers()) {
            if (u != this) {
                if (u.getKey().isWritable())
                    ChatServer.sendMessage(messageBuf, u.getKey());
                else
                    System.err.println("A user in chat can`t receive messages");
            }
        }
    }

}


class Chat {
    private static Map<String, Chat> chatDict = new Hashtable<>();
    private String name;
    private List<User> userMap;

    public static Chat getChat(String name) {
        return chatDict.get(name);
    }

    Chat(String name) {
        this.name = name;
        chatDict.put(name, this);
        userMap = new ArrayList<User>();
    }

    public List<User> getUsers() {
        return userMap;
    }


    public void addUser(User user) {
        this.userMap.add(user);
    }

    public void removeUser(User user) {
        this.userMap.remove(user);
    }

    void sendMessage(ByteBuffer buf) throws IOException {
        for (User u : userMap) {
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
