import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// TODO: report error if it can't connect to a server and quit
// ISSUE: only prints penultimate message
// TODO: threading
// TODO: improve error messages
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
        BufferedReader inFromUser =
            new BufferedReader(new InputStreamReader(System.in)); //necessário?
        Socket clientSocket = new Socket(domain, port);
        outToServer =
         new DataOutputStream(clientSocket.getOutputStream());
        inFromServer =
         new BufferedReader(new
               InputStreamReader(clientSocket.getInputStream()));

    }

    // Pretty print messages recieved from server
    public String decodeMessage(String message){
        String tokens[] = message.split(" ");
        String decodedMessage = "UNDEFINED DECODED MESSAGE";
        System.out.println("token[0]: " + tokens[0]);
        switch(tokens[0]){
        case "ERROR":
            decodedMessage = "Error";
            break;
        case "OK":
            decodedMessage = "Ok";
            break;
        case "MESSAGE":
            decodedMessage = tokens[1] + ": " + String.join(" ", tokens[2]);
            break;
        case "JOINED":
            decodedMessage = "The user " + tokens[1] + " has joined the room";
            break;
        case "NEWNICK":
            decodedMessage = tokens[1] + " -> " + tokens[2];
            break;
        case "LEFT":
            decodedMessage = "The user " + tokens[1] + " has left the room";
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


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        outToServer.writeBytes(message + '\n');
        String response = inFromServer.readLine();
        chatArea.append(decodeMessage(response) + '\n');
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        


    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        if(args.length != 2){
            throw new IllegalArgumentException("USAGE: ChatClient HOSTNAME PORT");
        }
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
