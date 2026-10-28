package ca.jeeth.switchboard;

public final class HomeNavigationTest {
    private static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(HomeNavigation.next(0,0)==11,"Startup Left must choose HDMI 2");
        check(HomeNavigation.next(0,1)==100,"Startup Right must choose first app");
        check(HomeNavigation.next(0,2)==1,"Startup Up must choose recent app");
        check(HomeNavigation.next(0,3)==2,"Startup Down must choose settings");
        check(HomeNavigation.next(11,2)==10&&HomeNavigation.next(11,3)==12,"HDMI 1/3 must flank HDMI 2");
        check(HomeNavigation.next(10,1)==0&&HomeNavigation.next(12,1)==0,"Inputs must return to center");
        for(int i=100;i<116;i++){
            if((i-100)%4>0)check(HomeNavigation.next(HomeNavigation.next(i,0),1)==i,"Horizontal grid navigation must be reversible");
            if(i<112)check(HomeNavigation.next(HomeNavigation.next(i,3),2)==i,"Vertical grid navigation must be reversible");
        }
        check(HomeNavigation.next(100,0)==0,"Left edge must reach center");
        check(HomeNavigation.next(100,2)==1,"Top edge must reach recent app");
        check(HomeNavigation.next(115,3)==2,"Bottom edge must reach settings");
        System.out.println("Home navigation: startup directions, input rail and all 16 grid cells pass");
    }
}
