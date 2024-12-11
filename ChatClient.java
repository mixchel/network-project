import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// TODO: handle server error messages (make them more clear) (requires handling of state) 
// TODO: ttf fonts
// NICE TO HAVES: 
// -indicador de qual Chat estamos atualmente
// -quando o usuario manda mensagem trocar o nome dele por you.

public class ChatClient {

    // UI Vars
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Other
    String domain;
    int port;
    Socket clientSocket;

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
            stopClient(); //ISSUE - running this here just feels wrong.
            break;
        default:
            decodedMessage = "Undefined";
            break;
        }
        return decodedMessage;
    }

    public void receiveMessage() throws IOException{
        String response = inFromServer.readLine();
        chatArea.append(decodeMessage(response) + '\n');
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        outToServer.writeBytes(message + '\n');
    }

    public void stopClient(){
        try{
            clientSocket.close();
        } catch (IOException ie){
            System.out.println("Couldn't close socket properly, exception" + ie);
        }
        System.exit(0);  // Terminates the program
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
        if(args.length != 2){
            throw new IllegalArgumentException("USAGE: ChatClient HOSTNAME PORT");
        }
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
