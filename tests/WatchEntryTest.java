package ca.jeeth.switchboard;

import java.util.*;

public final class WatchEntryTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static WatchEntry item(String id,String pkg,long time,long position,long duration,int type,boolean browsable){return new WatchEntry(id,pkg,"Example Show","Episode title","2","3","example://episode/"+id,time,position,duration,type,browsable);}
    public static void main(String[] args){
        WatchEntry old=item("a","video.a",100,25,100,0,true),latest=item("a","video.a",300,50,100,0,true),other=item("a","video.b",200,0,100,1,true);
        List<WatchEntry> entries=WatchEntry.ordered(Arrays.asList(old,other,latest,item("private","video.a",600,1,100,0,false),item("watchlist","video.a",700,0,0,3,true),item("finished","video.a",800,100,100,0,true)),Collections.emptyMap());
        check(entries.size()==2&&entries.get(0)==latest&&entries.get(1)==other,"Sort, deduplication, owner isolation and provider flags must be respected");
        Map<String,Long> hidden=new HashMap<>();hidden.put(latest.key,300L);
        check(WatchEntry.ordered(entries,hidden).size()==1,"A dismissed publication must stay hidden");
        WatchEntry republished=item("a","video.a",301,10,100,0,true);
        check(WatchEntry.ordered(Arrays.asList(republished),hidden).size()==1,"A newly watched publication must reappear");
        check(latest.progress()==500,"Known duration must produce accurate progress");
        check(item("x","video.a",1,-10,100,0,true).progress()==0,"Negative position must clamp to zero");
        check(item("x","video.a",1,200,100,1,true).progress()==1000,"Progress must not exceed its range");
        check(item("x","video.a",1,20,0,1,true).progress()==0,"Unknown duration must not invent progress");
        check(latest.episodeLabel().equals("S2 E3 · Episode title"),"Episode label must preserve supplied metadata");
        System.out.println("Watch entries: ordering, duplicates, hidden/new items, shared flags and progress pass");
    }
}
