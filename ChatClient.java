import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// TODO: report error if it can't connect to a server
// TODO: parse messages
// ISSUE: only prints penultimate message

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    String domain;
    int port;

    // buffers
    DataOutputStream outToServer;
    BufferedReader inFromServer;

    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
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
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        // conectar ao servidor
        BufferedReader inFromUser =
            new BufferedReader(new InputStreamReader(System.in)); //necessário?
        Socket clientSocket = new Socket(domain, port);
        outToServer =
         new DataOutputStream(clientSocket.getOutputStream());
        inFromServer =
         new BufferedReader(new
               InputStreamReader(clientSocket.getInputStream()));

    }

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
            decodedMessage = "Joined";
            break;
        case "NEWNICK":
            decodedMessage = "Newnick";
            break;
        case "LEFT":
            decodedMessage = "Left";
            break;
        case "BYE":
            decodedMessage = "Bye";
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
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
