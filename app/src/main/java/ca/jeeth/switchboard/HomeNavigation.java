package ca.jeeth.switchboard;

/** Explicit focus graph: the center always has four useful destinations. */
final class HomeNavigation {
    static final int HUB=0,RECENT=1,SETTINGS=2,INPUT=10,APP=100,WATCH=200;
    static final int LEFT=0,RIGHT=1,UP=2,DOWN=3;
    static int next(int node,int direction){
        if(node==HUB)return new int[]{INPUT+1,APP,RECENT,SETTINGS}[direction];
        if(node==RECENT)return direction==LEFT?INPUT:direction==RIGHT?APP:direction==DOWN?HUB:WATCH;
        if(node==SETTINGS)return direction==LEFT?INPUT+2:direction==RIGHT?APP+12:direction==UP?HUB:SETTINGS;
        if(node>=INPUT&&node<INPUT+3){int row=node-INPUT;return direction==RIGHT?HUB:direction==UP?(row==0?RECENT:node-1):direction==DOWN?(row==2?SETTINGS:node+1):node;}
        if(node>=APP&&node<APP+16){int n=node-APP,row=n/4,col=n%4;
            return direction==LEFT?(col==0?HUB:node-1):direction==RIGHT?(col==3?node:node+1):direction==UP?(row==0?RECENT:node-4):(row==3?SETTINGS:node+4);
        }
        if(node>=WATCH&&node<WATCH+4)return direction==DOWN?RECENT:direction==LEFT?Math.max(WATCH,node-1):direction==RIGHT?Math.min(WATCH+3,node+1):node;
        return HUB;
    }
}
