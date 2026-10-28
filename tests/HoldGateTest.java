package ca.jeeth.switchboard;

public final class HoldGateTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        HoldGate gate=new HoldGate();
        gate.down(100);check(!gate.tick(899,800),"short tap must not launch");check(gate.up(),"tap is reported");
        check(!gate.tick(1000,800),"release cancels pending launch");
        gate.down(2000);gate.down(2400);check(gate.tick(2800,800),"repeats do not restart hold");
        check(!gate.tick(3200,800),"hold launches once");check(!gate.up(),"held release is not a tap");
        gate.down(4000);gate.cancel();check(!gate.tick(5000,800),"disconnect cancels launch");
        gate.down(6000);check(gate.tick(6900,800),"delayed handler can fire at release");gate.up();
        gate.down(7000);check(!gate.tick(7599,600),"configured duration respected");check(gate.tick(7600,600),"exact boundary fires");
        System.out.println("HoldGate: all checks passed");
    }
}
