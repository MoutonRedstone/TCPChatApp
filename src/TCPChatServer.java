import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TCPChatServer {
    public static List<ClientHandler> clients = new ArrayList<>();

    static Thread connectionThread;

    static ServerSocket serverSocket;

    public static void main(String[] args) throws IOException {
        if(args.length==0){
            throw new RuntimeException("Please provide a port");
        }
        serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        connectionThread = new Thread(TCPChatServer::acceptConnections);
        connectionThread.start();
        System.out.println("Server started on port " + args[0]);
        System.out.println("Listening to connections");
    }

    public static void acceptConnections(){
        while(true){
            try {
                Socket connection = serverSocket.accept();
                System.out.println("New connection from " + connection.getInetAddress().hashCode() + ".....");
                long id = (connection.getInetAddress().hashCode()*100000L) + LocalTime.now().toSecondOfDay();
                boolean doesIdExist = false;
                for (ClientHandler client : clients){
                    if (client.getSessionID() == id){
                        doesIdExist = true;
                        break;
                    }
                }
                if(doesIdExist){
                    connection.close();
                }else{
                    clients.add(new ClientHandler(connection, id));
                    System.out.println("There is currently " + clients.size() + " running instances");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean canReceive(ClientHandler client){
        return client.isConnected() && !client.getClientUsername().isBlank() && !(client.getMuteLevel()>0);
    }

    public static void broadcastMessage(ClientHandler sender, String message){
        String username = sender.getClientUsername();
        for (ClientHandler client : clients){
            if (canReceive(client) && !client.equals(sender)){
                client.sendToClient("["+username+"] " + message);
            }
        }
    }

    public static void broadcastJoin(ClientHandler user){
        String username = user.getClientUsername();
        for (ClientHandler client : clients){
            if (canReceive(client)){
                client.sendToClient("["+username+" a rejoint la discussion]");
            }
        }
    }

    public static ClientHandler getClientByUsername(String username){
        for (ClientHandler client : clients){
            if (client.getClientUsername().equals(username)){
                return client;
            }
        }
        return null;
    }

    public static void broadcastLeave(ClientHandler user){
        String username = user.getClientUsername();
        for (ClientHandler client : clients){
            if (canReceive(client)){
                client.sendToClient("["+username+" a quitté la discussion]");
            }
        }
    }

    public static void broadcastRename(String oldName, String newName){
        for (ClientHandler client : clients){
            if (canReceive(client)){
                client.sendToClient("["+oldName+" a changé son pseudo en : "+newName+"]");
            }
        }
    }

}