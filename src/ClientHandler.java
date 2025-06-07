import java.io.*;
import java.net.Socket;
import java.time.LocalTime;

public class ClientHandler {
    private static final int MAX_USERNAME_LENGTH = 24;

    private final Socket connection;
    private PrintWriter output;
    private BufferedReader reader;

    private Thread serverThread;
    private Thread serverStopper;

    private final long sessionID;
    private String clientUsername;

    private boolean connected;
    private int muteLevel;

    public ClientHandler(Socket connection) throws IOException {
        this.connection = connection;
        this.sessionID =  (connection.getInetAddress().hashCode()*100000L) + LocalTime.now().toSecondOfDay();
        this.reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        this.output = new PrintWriter(connection.getOutputStream(), true);
        this.clientUsername = "";

        this.connected = true;
        connection.setKeepAlive(true);

        serverThread = new Thread(this::serverLoop);
        serverThread.start();
        serverStopper = new Thread(this::serverStopperLoop);
        serverStopper.start();

        this.muteLevel = 0;
    }

    public String getClientUsername(){
        return this.clientUsername;
    }
    public boolean isConnected() {return connected;}
    public int getMuteLevel(){return this.muteLevel;}
    public long getSessionID(){return this.sessionID;}

    public String toString(){return Long.toString(this.sessionID) + " ("+this.clientUsername+")";}
    public boolean equals(ClientHandler a) {
        return (this.sessionID == a.getSessionID());
    }

    public static boolean isUsernameTaken(String username){
        for (ClientHandler client : Main.clients){
            if (client.getClientUsername().equals(username)){
                return true;
            }
        }
        return false;
    }

    public void sendToClient(String data){sendToClient(data, true);}
    public void sendToClient(String data, boolean autoLfCr) {
        output.print(data + (autoLfCr?"\n\r":""));
        output.flush();
    }

    public String receiveNext() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            closeConnection();
            return "";
        }
        return line;
    }

    public void executeCommand(String command){
        String[] params = command.split("\\s+");
        if (params[0].equals("exit")){
            sendToClient(">> Deconnexion");
            closeConnection();
        }
        else if(params[0].equals("mute")){
            if (params.length==1){
                muteLevel = (muteLevel>0?0:1);
            }else{
                int level = Integer.parseInt(params[1]);
                if (level<=0)        {muteLevel = 0;}
                else if (level >= 2) {muteLevel = 2;}
                else                 {muteLevel = 1;}
            }
            sendToClient(">> Mute : " + (this.muteLevel>0) + " ("+this.muteLevel+")");
        }
        else if(params[0].equals("rename")){
            if(params.length==1){
                sendToClient(">> Nom d'utilisateur manquant : /rename <username>");
            }else{
                String potentialUsername = command.substring(7);
                if (potentialUsername.length()>MAX_USERNAME_LENGTH || potentialUsername.contains("\n") || potentialUsername.contains("@>") || potentialUsername.isBlank()){
                    sendToClient(">> Le nom d'utilisateur ne peut pas dépasser "+MAX_USERNAME_LENGTH+" caractères ni contenir de retour à la ligne");
                }else if (ClientHandler.isUsernameTaken(potentialUsername)){
                    sendToClient(">> Ce nom d'utilisateur est déjà utilisé");
                }
                else{
                    Main.broadcastRename(this.clientUsername, potentialUsername);
                    this.clientUsername = potentialUsername;
                    System.out.println("Client " + this + " renamed themselves");
                }
            }
        }
        else if(params[0].equals("connected")){
            String message = "";
            for (ClientHandler client : Main.clients){
                message += "'" + client.getClientUsername() + "'" + (client.getMuteLevel()>0?" (muted)":"") + "\n\r";
            }
            sendToClient(">> Voilà la liste des membres connectés :\n\r" + message, false);
        }
        else if(params[0].equals("whisper")){
            if (params.length<2){
                sendToClient(">> Il manque des arguments : /whisper <username> @> <message>");
            }
            String username = "";
            boolean goToMessage = false;
            String message = "";
            for (int i = 1; i< params.length; i ++){
                if (params[i].contains("@>")){
                    goToMessage = true;
                }else if (!goToMessage){
                    username += " "+params[i];
                }else{
                    message += " "+params[i];
                }
            }
            if (goToMessage){
                username = username.substring(1);
                message = message.substring(1);
                whisperTo(username, message);
            }else{
                sendToClient(">> Veuillez indiquer un message à envoyer : /whisper <username> @> <message>");
            }
        }
        else if(params[0].equals("help")){
            sendToClient(">> Voici une liste des commandes :\n\r" +
                    ">> /exit : ferme la connection avec le server\n\r" +
                    ">> /mute : active ou désactive l'affichage des messages généraux\n\r" +
                    ">> /mute (niveau) : change votre niveau de mute (1 que les généraux, 2 tout les messages même privés)\n\r" +
                    ">> /rename <username> : change votre pseudo par le pseudo entré\n\r" +
                    ">> /connected : affiche les pseudos des utilisateurs connectés\n\r" +
                    ">> /whisper <username> @> <message> : Envoie un message privé à quelqu'un, il faut mettre '@>' entre le pseudo et le message"
            );
        }
        else{
            sendToClient(">> Commande Inconnue, faites /help pour avoir une liste des commandes");
        }
    }

    public void whisperTo(String username, String data){
        ClientHandler target = Main.getClientByUsername(username);
        if (target==null){
            sendToClient(">> Cet utilisateur n'existe pas");
        }else if(target.muteLevel == 2){
            sendToClient(">> Cet utilisateur a muté tout les messages (y compris les privés)");
        }
        else{
            target.sendToClient("(" + this.clientUsername + ") " + data);
        }
    }

    public void serverLoop(){
        try {
            System.out.println("Client " + this + " redirected to login page");
            sendToClient("Salut, ", false);
            connection.setSoTimeout(60000); //Timeout de 60 secondes
            // Choix du username
            while (this.clientUsername.isBlank()){
                sendToClient("choisis un nom d'utilisateur : ", false);
                String potentialUsername = receiveNext();
                if (potentialUsername.length()>MAX_USERNAME_LENGTH || potentialUsername.contains("\n") || potentialUsername.contains("@>")){
                    sendToClient("Le nom d'utilisateur ne peut pas dépasser "+MAX_USERNAME_LENGTH+" caractères ni contenir de retour à la ligne");
                }else if (ClientHandler.isUsernameTaken(potentialUsername)){
                    sendToClient("Ce nom d'utilisateur est déjà utilisé");
                }
                else{
                    this.clientUsername = potentialUsername;
                    System.out.println("Client " + this + " registered");
                }
            }
            connection.setSoTimeout(600000); //Timeout de 10 minutes
            Main.broadcastJoin(this);
            // Boucle de communication avec le server
            while (this.connected){
                String message = receiveNext();
                if (message.charAt(0) == '/'){
                    executeCommand(message.substring(1));
                }else{
                    Main.broadcastMessage(this, message);
                }
            }
        } catch (Exception e){
            closeConnection();
            throw new RuntimeException(e);
        }
    }

    public void serverStopperLoop(){
        while (this.connected) {
            try {
                this.connection.getOutputStream().write(0);
                this.connection.getOutputStream().flush();
                Thread.sleep(5000);
            } catch (Exception e) {
                System.out.println("Client not responding");
                this.closeConnection();
            }
            boolean areStreamsShutdown = connection.isOutputShutdown() || connection.isInputShutdown();
            boolean isConnectionClosed = !connection.isConnected() || connection.isClosed() || !connection.isBound();
            if (isConnectionClosed || areStreamsShutdown) {
                this.closeConnection();
            }
        }
    }

    public synchronized void closeConnection(){
        serverStopper.interrupt();
        if (!this.connected){
            return;
        }
        System.out.println("Client " + this + " lost connection");
        try {
            this.connection.getInputStream().close();
            this.connection.getOutputStream().close();
            connection.close();
        } catch (IOException e) { }
        this.connected = false;
        Main.clients.remove(this);
        Main.broadcastLeave(this);
    }
}
