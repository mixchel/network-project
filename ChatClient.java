import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.Collectors;

// TODO: selecting a nickname outside a room just responds with a OK
// TODO: joining a room just responds with a OK

public class ChatClient {

    // UI Vars
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Other
    String domain;
    int port;
    Socket clientSocket;
    String lastMessage = "";
    String currentRoom = "";
    String currentNick = "";

    // Buffers/Streams
    DataOutputStream outToServer;
    BufferedReader inFromServer;

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static final CharsetDecoder decoder = charset.newDecoder();
    static final CharsetEncoder encoder = charset.newEncoder();

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // A buffer containing a newline character
    static private final ByteBuffer newline = ByteBuffer.allocate(1);
    
    // Send message to history
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Initialize UI
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);

        //Variable assignments
        domain = server;
        this.port = port;

        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Code to be executed when the window is closed
                System.out.println("Window is closing, trying to close socket gracefully...");
                stopClient();
            }
        });
        

    }

    // Pretty print messages recieved from server
    public String decodeMessage(String message){
        java.util.List<String> tokens = Arrays.asList(message.split(" "));
        String decodedMessage = "UNDEFINED DECODED MESSAGE";
        System.out.println("DEBUG: token[0]: " + tokens.get(0));
        switch(tokens.get(0)){
        case "ERROR":
            decodedMessage = "Error";
            break;
        case "OK":
            decodedMessage = "Ok";
            break;
        case "MESSAGE":
            if(tokens.get(1).equals(currentNick)){
                decodedMessage = "[" + currentRoom + "] " + "You" + ": " + String.join(" ", tokens.subList(2, tokens.size()));
            } else {
                decodedMessage = "[" + currentRoom + "] " + tokens.get(1) + ": " + String.join(" ", tokens.subList(2, tokens.size()));
            }
            break;
        case "JOINED":
            decodedMessage = "The user " + tokens.get(1) + " has joined the room";
            break;
        case "NEWNICK":
            decodedMessage = tokens.get(1) + " mudou de nome para " + tokens.get(2);
            break;
        case "LEFT":
            decodedMessage = "The user " + tokens.get(1) + " has left the room";
            break;
        case "BYE":
            stopClient(); //ISSUE - running this here just feels wrong.
            break;
        default:
            decodedMessage = "Undefined";
            break;
        }
        return decodedMessage;
    }

    public boolean isCommand(String message){
        if(message.length() > 1 && message.charAt(0) == '/' && message.charAt(1) != '/'){
            return true;
        }
        return false;
    }

    // TODO: complains you need to join a room to send a message, if you happen to send one while on the INIT state... I mean, it is right, but perhaps it should inform the user of the fact he hasn't chosen a nickname
    public void receiveMessage() throws IOException{
        String response = inFromServer.readLine();
        if(response.equals("ERROR")){
            if(lastMessage.split(" ")[0].equals("/nick")){
                printMessage("Error: Nick already in use.\n");
                return;
            }
            if(!isCommand(lastMessage)){
                printMessage("Error: You need to join a room to be send messages.\n");
                return;
            }
            printMessage("Error: Invalid command.\n");
            return;
        }
        if(response.equals("OK")){
            if(lastMessage.split(" ")[0].equals("/join")){
                setCurrentRoom(lastMessage.split(" ")[1]);
            }
            if(lastMessage.split(" ")[0].equals("/nick")){
                setCurrentNick(lastMessage.split(" ")[1]);
            }
        }
        chatArea.append(decodeMessage(response) + '\n');
    }

    public void setCurrentRoom(String room){
        this.currentRoom = room;
        System.out.println("Set currentRoom to:" + room);
    }

    public void setCurrentNick(String nick){
        this.currentNick = nick;
        System.out.println("Set currentNick to:" + nick);
    }

    public void setLastMessage(String message){
        this.lastMessage = message;
        System.out.println("Set lastMessage to:" + message);
    }

    static public ByteBuffer encodeMessage(String message) throws IOException {
        System.out.println("encoding!!!");
        return encoder.encode(CharBuffer.wrap(message.toCharArray()));
    }
 
    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        WritableByteChannel channel = Channels.newChannel(outToServer);
        channel.write(encodeMessage(message + '\n'));
        setLastMessage(message);
    }

    public void stopClient(){
        try{
            clientSocket.close();
        } catch (IOException ie){
            System.out.println("Couldn't close socket properly, exception" + ie);
        }
        System.exit(0);
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        System.out.println("catapimbas");
        System.out.println("DEBUG: Attempting to connect to " + domain + ":" + port);
        clientSocket = new Socket(domain, port);
        System.out.println("DEBUG: Connected to " + domain + ":" + port);
        outToServer =
          new DataOutputStream(clientSocket.getOutputStream());
        inFromServer =
         new BufferedReader(new
               InputStreamReader(clientSocket.getInputStream()));
        while (true) {
            try{
                receiveMessage();
            } catch (IOException e){
                System.out.println("Couldn't receive message");
            }
        }
    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR * ISSUE: Frik, I modified it...
    public static void main(String[] args) throws IOException {
        newline.put((byte) 0x0A).flip();
        if(args.length != 2){
            throw new IllegalArgumentException("USAGE: ChatClient HOSTNAME PORT");
        }
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
