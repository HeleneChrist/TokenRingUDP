import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.LinkedList;


public class TokenRing {

    private static void loop(DatagramSocket socket, String ip, int port, boolean first){
        LinkedList<Token.Endpoint> candidates = new LinkedList<>();
        if (first) {
            candidates.add(new Token.Endpoint(ip, port));
            //System.out.println("Token erstellt");
        }
        while (true) {
            try {
                socket.setSoTimeout(0);
                Token rc = Token.receive(socket);
                if (!rc.getIsConfirmation()) {
                    System.out.printf("Token: seq=%d, #members=%d", rc.getSequence(), rc.length());
                    for (Token.Endpoint endpoint : rc.getRing()) {
                        System.out.printf(" (%s, %d)", endpoint.ip(), endpoint.port());
                    }
                    System.out.println();
                    if (rc.length() == 1) {
                        candidates.add(rc.poll());
                        if (!first) {
                            continue;
                        }
                    }
                    first = false;
                    for (Token.Endpoint candidate : candidates) {
                        rc.append(candidate);
                    }
                    if (rc.length()>2){
                        Token previous = new Token();
                        previous.setIsConfirmation(true);
                        previous.send(socket, rc.previous());
                    }
                    candidates.clear();
                    Token.Endpoint next = rc.poll();
                    rc.append(next);
                    rc.incrementSequence();
                    Thread.sleep(1000);
                    rc.send(socket, next);
                    socket.setSoTimeout(300);
                    try {
                        Token confirmation = Token.receive(socket);
                        if (confirmation.getIsConfirmation()) {
                            System.out.println("Token bestätigt");
                        }
                    }
                    catch (SocketTimeoutException e) {
                        System.out.println("Nächster Token wird gelöscht");
                       rc.remove(next);
                       next = rc.poll();
                       rc.append(next);
                       rc.send(socket, next);
                    }

                }

            }
            catch (IOException e) {
                System.out.println("Error receiving packet: " + e.getMessage());
            }
            catch (Exception e) {
                System.out.println("Error: " + e.getMessage() + " " + Arrays.toString(e.getStackTrace()));
            }
        }
    }

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            socket.disconnect();
            int port = socket.getLocalPort();
            System.out.printf("UDP endpoint is (%s, %d)\n", ip, port);
            if (args.length == 0) {
                loop(socket,ip,port,true);
            }
            else if (args.length == 2) {
                Token rc = new Token().append(ip,port);
                rc.send(socket,args[0],Integer.parseInt(args[1]));
                loop(socket,ip,port,false);
            }
            else {
                System.out.println("Usage: \"java TokenRing\" or \"java TokenRing <ip> <port>\"");
            }
        }
        catch (SocketException e) {
            System.out.println("Error creating socket: " + e.getMessage());
        }
        catch (UnknownHostException e) {
            System.out.println("Error while determining IP address: " + e.getMessage());
        }
        catch (IOException e) {
            System.out.println("IO error: " + e.getMessage());
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }
}