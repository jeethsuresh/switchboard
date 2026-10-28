package ca.jeeth.switchboard;

import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

public final class BridgeAuthTest {
    private static void roundTrip(boolean sameKey)throws Exception{
        Class<?> bridge=Class.forName("ca.jeeth.switchboard.bridge.ShellBridge");
        Method serverAuth=bridge.getDeclaredMethod("authenticateServer",DataInputStream.class,DataOutputStream.class,byte[].class);
        Method clientAuth=bridge.getDeclaredMethod("authenticateClient",DataInputStream.class,DataOutputStream.class,byte[].class);
        serverAuth.setAccessible(true);clientAuth.setAccessible(true);
        byte[] serverKey=new byte[32],clientKey=new byte[32];if(!sameKey)clientKey[0]=1;
        AtomicReference<Throwable> serverError=new AtomicReference<>();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            Thread thread=new Thread(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(1500);DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    serverAuth.invoke(null,in,out,serverKey);
                    if(!in.readUTF().equals("status"))throw new AssertionError("Payload changed");out.writeUTF("ok");out.flush();
                }catch(Throwable e){serverError.set(e);}
            });thread.start();
            boolean accepted=false;
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())){
                socket.setSoTimeout(1500);DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                clientAuth.invoke(null,in,out,clientKey);out.writeUTF("status");out.flush();accepted=in.readUTF().equals("ok");
            }catch(Exception e){if(sameKey)throw e;}
            thread.join(2000);
            if(thread.isAlive()||accepted!=sameKey||(sameKey&&serverError.get()!=null))throw new AssertionError("Authentication round trip failed");
        }
    }
    public static void main(String[] args)throws Exception{roundTrip(true);roundTrip(false);System.out.println("Bridge authentication: valid client accepted; wrong key rejected");}
}
