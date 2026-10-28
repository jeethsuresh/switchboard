package ca.jeeth.switchboard;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;
import android.os.*;
import java.util.function.Consumer;
import java.net.*;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class BridgeClient {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static Context context;
    static void init(Context c){context=c.getApplicationContext();}
    private static byte[] sign(byte[] key,int role,byte[] challenge,byte[] nonce)throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));mac.update((byte)role);mac.update(challenge);return mac.doFinal(nonce);
    }
    static JSONObject request(JSONObject command)throws Exception{
        byte[] key=new byte[32];
        if(context==null)throw new IOException("App context unavailable");
        try(DataInputStream keyFile=new DataInputStream(new FileInputStream(new File(context.getFilesDir(),"bridge-key")))){keyFile.readFully(key);}
        catch(IOException e){throw new IOException("Start the ADB helper to initialize its private connection key");}
        try(Socket socket=new Socket()){
            try{socket.connect(new InetSocketAddress("127.0.0.1",38473),3000);}
            catch(ConnectException|SocketTimeoutException e){throw new IOException("The ADB helper is not listening on this TV (127.0.0.1:38473). Restart the helper after a TV restart or launcher update.",e);}
            socket.setSoTimeout(20000);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());DataInputStream in=new DataInputStream(socket.getInputStream());
            byte[] challenge=new byte[32],nonce=new byte[32],proof=new byte[32];in.readFully(challenge);new SecureRandom().nextBytes(nonce);
            out.write(nonce);out.write(sign(key,0,challenge,nonce));out.flush();in.readFully(proof);
            if(!MessageDigest.isEqual(proof,sign(key,1,challenge,nonce)))throw new SecurityException("Helper authentication failed");
            out.writeUTF(command.toString());out.flush();
            JSONObject response=new JSONObject(in.readUTF());
            if(!response.optBoolean("ok"))throw new IOException(response.optString("error","Helper refused request"));return response;
        }
    }
    static JSONObject command(String name){JSONObject r=new JSONObject();try{r.put("command",name);}catch(Exception ignored){}return r;}
    static void call(JSONObject request,Consumer<JSONObject> success,Consumer<String> error){
        worker.execute(()->{try{JSONObject result=request(request);main.post(()->success.accept(result));}
            catch(Exception e){main.post(()->error.accept(e.getMessage()==null?e.toString():e.getMessage()));}});
    }
}
