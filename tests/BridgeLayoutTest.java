package ca.jeeth.switchboard;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class BridgeLayoutTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Class<?> type=Class.forName("ca.jeeth.switchboard.bridge.ShellBridge");
        Method matches=type.getDeclaredMethod("taskMatches",String.class,String.class,String.class);matches.setAccessible(true);
        require((Boolean)matches.invoke(null,"com.google.android.youtube.tv/.ShellActivity","com.google.android.youtube.tv/.MainActivity",null),"Redirected YouTube task was lost");
        require(!(Boolean)matches.invoke(null,"ca.jeeth.switchboard/.MultiViewActivity","ca.jeeth.switchboard/.MainActivity",null),"HDMI host matched the launcher task");
        require(!(Boolean)matches.invoke(null,"com.google.android.youtube.tv/.ShellActivity","com.netflix.ninja/.MainActivity",null),"Unrelated app matched");
        Method fit=type.getDeclaredMethod("fitInset",int.class,int.class,int.class,int.class,int.class,int.class);fit.setAccessible(true);
        int[] topRight=(int[])fit.invoke(null,576,576,440,1920,1080,0);
        require(Arrays.equals(topRight,new int[]{1112,24,1896,465}),"TV's minimum height did not produce a 16:9 frame");
        int[] bottomLeft=(int[])fit.invoke(null,576,576,440,1920,1080,3);
        require(Arrays.equals(bottomLeft,new int[]{24,615,808,1056}),"Corrected inset escaped its requested corner");
        int[] stable=(int[])fit.invoke(null,784,784,441,1920,1080,0);
        require(Arrays.equals(stable,topRight),"Repeated fitting kept growing the inset");
        System.out.println("Bridge layout: redirected tasks found; launcher isolated; minimum-size and corner bounds preserve 16:9");
    }
}
