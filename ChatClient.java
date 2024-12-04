import java.io.*;
import java.net.*;
import java.util.List;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// TODO: handle server error messages (make them more clear) (requires handling of state)
// ISSUE: only prints penultimate message (related to threading)
// TODO: threading (I'm utterly clueless on this one)
// TODO: ttf fonts

public class ChatClient {

    // UI Vars
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Other
    String domain;
    int port;

    // Buffers/Streams
    DataOutputStream outToServer;
    BufferedReader inFromServer;

    
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

        // Connect to server and generate buffers
        System.out.println("DEBUG: Attempting to connect to " + domain + ":" + port);
        Socket clientSocket = new Socket(domain, port);
        System.out.println("DEBUG: Connected to " + domain + ":" + port);
        outToServer =
         new DataOutputStream(clientSocket.getOutputStream());
        inFromServer =
         new BufferedReader(new
               InputStreamReader(clientSocket.getInputStream()));

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
            decodedMessage = tokens.get(1) + ": " + String.join(" ", tokens.subList(2, tokens.size()));
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
            decodedMessage = "Bye!";
            break;
        default:
            decodedMessage = "Undefined";
            break;
        }
        return decodedMessage;
    }

    public void recieveMessage() throws IOException{
        String response = inFromServer.readLine();
        chatArea.append(decodeMessage(response) + '\n');
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        outToServer.writeBytes(message + '\n');
        recieveMessage();
    }

    
    // Método principal do objecto
    public void run() throws IOException {

    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR * ISSUE: Frik, I modified it...
    public static void main(String[] args) throws IOException {
        if(args.length != 2){
            throw new IllegalArgumentException("USAGE: ChatClient HOSTNAME PORT");
        }

        try{
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
        } catch(ConnectException e) {
            System.out.println("The server " + args[0] + ":" + args[1] + " is currently unreachable");
            System.exit(1);
        }
    }
}
